-- ============================================================
-- Flyway Migration Script
-- Descripción: Agrega tablas de documento electrónico a las publicaciones de filiales
-- Fecha: 2025-10-16
-- Versión: V1
-- ============================================================
-- Este script:
-- 1. Consulta dinámicamente todas las sucursales activas (excepto id = 0)
-- 2. Agrega las tablas de documento electrónico a cada publicación de filial
-- 3. Aplica el filtro WHERE (sucursal_id = X) para cada tabla
-- ============================================================

DO $$
DECLARE
    v_sucursal_id INTEGER;
    v_publication_name TEXT;
    v_sql TEXT;
    v_count INTEGER := 0;
BEGIN
    RAISE NOTICE 'Iniciando actualización de publicaciones...';
    
    -- Iterar sobre todas las sucursales (excepto la id = 0 que es la central)
    FOR v_sucursal_id IN 
        SELECT id 
        FROM empresarial.sucursal 
        WHERE id != 0 
        ORDER BY id
    LOOP
        -- Construir el nombre de la publicación
        v_publication_name := 'central_filial' || v_sucursal_id || '_pub';
        
        -- Verificar si la publicación existe
        IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = v_publication_name) THEN
            
            RAISE NOTICE 'Procesando publicación: %', v_publication_name;
            
            -- Construir el comando ALTER PUBLICATION dinámicamente
            v_sql := format(
                'ALTER PUBLICATION %I ADD TABLE 
                    financiero.documento_electronico WHERE (sucursal_id = %L),
                    financiero.evento_cancelacion_de WHERE (sucursal_id = %L),
                    financiero.evento_nominacion_de WHERE (sucursal_id = %L),
                    financiero.lote_de WHERE (sucursal_id = %L)',
                v_publication_name,
                v_sucursal_id,
                v_sucursal_id,
                v_sucursal_id,
                v_sucursal_id
            );
            
            -- Ejecutar el comando
            BEGIN
                EXECUTE v_sql;
                v_count := v_count + 1;
                RAISE NOTICE '✓ Tablas agregadas exitosamente a: %', v_publication_name;
            EXCEPTION WHEN OTHERS THEN
                RAISE WARNING '✗ Error al actualizar %: %', v_publication_name, SQLERRM;
            END;
            
        ELSE
            RAISE WARNING '⚠ Publicación no existe: %', v_publication_name;
        END IF;
        
    END LOOP;
    
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'Proceso completado. Publicaciones actualizadas: %', v_count;
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'IMPORTANTE: Debes refrescar las suscripciones en cada filial';
    RAISE NOTICE 'Ejecuta en cada servidor de filial:';
    RAISE NOTICE 'ALTER SUBSCRIPTION <nombre_subscription> REFRESH PUBLICATION;';
    RAISE NOTICE '============================================================';
    
END $$;

-- ============================================================
-- VERIFICACIÓN
-- ============================================================
-- Para verificar que se agregaron correctamente las tablas:
SELECT 
    pubname AS publicacion,
    schemaname AS esquema,
    tablename AS tabla,
    COUNT(*) OVER (PARTITION BY pubname) AS total_tablas_publicacion
FROM pg_publication_tables 
WHERE pubname LIKE 'central_filial%_pub' 
  AND tablename IN ('documento_electronico', 'evento_cancelacion_de', 'evento_nominacion_de', 'lote_de')
ORDER BY pubname, tablename;

-- Ver resumen por publicación
SELECT 
    pubname AS publicacion,
    COUNT(*) AS nuevas_tablas
FROM pg_publication_tables 
WHERE pubname LIKE 'central_filial%_pub' 
  AND tablename IN ('documento_electronico', 'evento_cancelacion_de', 'evento_nominacion_de', 'lote_de')
GROUP BY pubname
ORDER BY pubname;

