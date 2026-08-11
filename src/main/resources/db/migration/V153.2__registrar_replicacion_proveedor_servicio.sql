-- Replicacion de personas.proveedor_servicio: central -> todas las filiales.
-- La fuente de verdad es el central (el alta/edicion solo existe alli); la filial
-- solo necesita la copia para poder resolver terminal_pos.proveedor_servicio_id.
--
-- financiero.terminal_pos NO se re-registra: ya esta replicada (V142.1). Agregarle una
-- columna no requiere tocar la publicacion (una publicacion sin lista de columnas publica
-- todas), pero SI requiere que la columna exista en cada filial -> migracion espejo V81.2
-- del repo filial, que se despliega ANTES que esta.

-- 1. Registro declarativo: alimenta la CREACION de publicaciones nuevas.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('personas.proveedor_servicio', 'MAIN_TO_ALL', 'Proveedor de Servicio', true, false, NOW())
ON CONFLICT (table_name) DO NOTHING;

-- 2. Publicaciones YA existentes: el INSERT de arriba no las toca, hay que alterarlas.
--    MAIN_TO_ALL sin filtro va a central_pub (misma logica que V119).
DO $$
DECLARE
    pub RECORD;
BEGIN
    FOR pub IN
        SELECT pubname FROM pg_publication
        WHERE (pubname = 'central_pub' OR pubname LIKE 'central\_%\_pub')
          AND pubname NOT LIKE 'central%filial%pub'
        ORDER BY pubname
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = pub.pubname
              AND schemaname = 'personas'
              AND tablename = 'proveedor_servicio'
        ) THEN
            BEGIN
                EXECUTE format(
                    'ALTER PUBLICATION %I ADD TABLE personas.proveedor_servicio',
                    pub.pubname
                );
                RAISE NOTICE 'Added personas.proveedor_servicio to %', pub.pubname;
            EXCEPTION WHEN OTHERS THEN
                RAISE WARNING 'No se pudo agregar personas.proveedor_servicio a %: %',
                    pub.pubname, SQLERRM;
            END;
        END IF;
    END LOOP;
END $$;

-- IMPORTANTE (post-deploy): cada filial necesita
--   ALTER SUBSCRIPTION <sub> REFRESH PUBLICATION;
-- para empezar a recibir la tabla nueva. Se hace desde el dialogo de replicacion del
-- desktop o a mano. Sin el REFRESH la tabla queda vacia en la filial, sin error visible.
