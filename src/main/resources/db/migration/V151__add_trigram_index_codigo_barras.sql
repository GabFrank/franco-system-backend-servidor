-- Búsqueda parcial de código de barras (tramo interno / terminación / sin ceros a la izquierda).
-- El índice btree existente (codigo_codigo_idx) no sirve para LIKE '%x%', que degrada a seq scan.
-- pg_trgm + GIN resuelve la coincidencia en cualquier posición.
--
-- CREATE EXTENSION requiere superusuario: si no está disponible la migración no falla,
-- la búsqueda sigue funcionando (más lenta) con el plan secuencial.

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm no disponible, se omite el indice trigram: %', SQLERRM;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        CREATE INDEX IF NOT EXISTS codigo_codigo_upper_trgm_idx
            ON productos.codigo USING gin (UPPER(codigo) gin_trgm_ops);
    END IF;
END $$;
