-- Control de lotes - sincroniza las publicaciones del central con lo que ya esta registrado
-- en configuraciones.replication_table.
--
-- V154.3 registro operaciones.movimiento_stock_lote (BRANCH_TO_MAIN + bajada filtrada) y
-- V156.3 registro operaciones.lote (MAIN_TO_ALL). Pero registrar no mueve datos: quien traduce
-- replication_table a ALTER PUBLICATION es ReplicationPublicationSyncScheduler, que arranca
-- recien 2 minutos despues del boot y despues corre cada hora.
--
-- Verificado en alpha el 2026-08-07: las dos filas estaban en replication_table, y sin embargo
-- operaciones.lote NO estaba en central_pub y operaciones.movimiento_stock_lote NO estaba en
-- central_alpha_filial2_pub. O sea que el canal de lotes central -> filial nunca transporto
-- nada, aunque toda la configuracion "se veia" correcta.
--
-- Esta migracion cierra ese hueco de forma deterministica, sin depender de que el scheduler
-- haya corrido alguna vez. Es idempotente: si el scheduler ya lo hizo, no hace nada.
--
-- LO QUE ESTA MIGRACION NO HACE, a proposito:
-- el ALTER SUBSCRIPTION ... REFRESH PUBLICATION que cada suscriptor necesita para tomar las
-- tablas nuevas. No puede hacerlo por dos razones independientes:
--   1) REFRESH PUBLICATION no corre dentro de una transaccion, y Flyway envuelve cada migracion
--      en una.
--   2) Dos de los tres refresh se ejecutan en la base de la FILIAL (alpha_filial2_central_sub
--      contra central_pub, y central_alpha_filial2_sub contra central_alpha_filial2_pub), que
--      esta fuera del alcance de una migracion del central.
-- De eso se encarga ReplicationRefreshScheduler (copy_data = false) o la mutation
-- refreshSubscription. Ojo con copy_data = false: engancha lo que venga de ahi en adelante, asi
-- que los lotes ya cargados antes del refresh necesitan un backfill aparte.

-- 1) Re-asegurar el registro. En alpha ya estaban, pero la migracion tiene que ser
--    autosuficiente para beta y produccion, donde el estado no esta verificado.
INSERT INTO configuraciones.replication_table
    (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
VALUES
    ('operaciones.lote', 'MAIN_TO_ALL', 'Maestro de lotes de producto', true, false, NOW()),
    ('operaciones.movimiento_stock_lote', 'BRANCH_TO_MAIN', 'Movimiento stock por lote', true, true, NOW())
ON CONFLICT (table_name) DO NOTHING;


-- 2) operaciones.lote -> central_pub (bajada a todas las filiales).
--    Sin filtro: es un maestro global y la tabla no tiene columna sucursal_id.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'central_pub') THEN
        RAISE NOTICE 'No existe central_pub - se omite (replicacion no configurada todavia)';
    ELSIF EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'central_pub'
          AND schemaname = 'operaciones'
          AND tablename = 'lote'
    ) THEN
        RAISE NOTICE 'operaciones.lote ya esta en central_pub';
    ELSE
        ALTER PUBLICATION central_pub ADD TABLE operaciones.lote;
        RAISE NOTICE 'Agregada operaciones.lote a central_pub';
    END IF;
END $$;


-- 3) operaciones.movimiento_stock_lote -> cada central_%_filial%_pub, filtrado por sucursal.
--
--    Se recorren TODAS las publicaciones que matcheen, no una sola: el central tiene una por
--    filial (central_alpha_filial2_pub, central_beta_filial2_pub, ...). Es la diferencia con la
--    V84.3 de la filial, donde el LIMIT 1 alcanzaba porque una filial publica una sola vez.
--
--    El nombre lleva el entorno adentro, por eso se descubren en vez de nombrarlas literal: un
--    ALTER PUBLICATION con nombre fijo solo funcionaria en una de las bases.
--
--    El numero de sucursal sale del propio nombre. Si algun nombre no matchea el patron se avisa
--    y se sigue, en vez de abortar toda la migracion por una publicacion mal nombrada.
DO $$
DECLARE
    pub        RECORD;
    sucursal   BIGINT;
BEGIN
    FOR pub IN
        SELECT pubname
        FROM pg_publication
        WHERE pubname LIKE 'central\_%\_filial%\_pub'
        ORDER BY pubname
    LOOP
        sucursal := NULLIF(substring(pub.pubname FROM 'filial([0-9]+)'), '')::BIGINT;

        IF sucursal IS NULL THEN
            RAISE NOTICE 'No se pudo deducir la sucursal de %, se omite', pub.pubname;
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = pub.pubname
              AND schemaname = 'operaciones'
              AND tablename = 'movimiento_stock_lote'
        ) THEN
            RAISE NOTICE 'operaciones.movimiento_stock_lote ya esta en %', pub.pubname;
            CONTINUE;
        END IF;

        EXECUTE format(
            'ALTER PUBLICATION %I ADD TABLE operaciones.movimiento_stock_lote WHERE (sucursal_id = %L)',
            pub.pubname, sucursal
        );
        RAISE NOTICE 'Agregada operaciones.movimiento_stock_lote a % con filtro sucursal_id = %',
            pub.pubname, sucursal;
    END LOOP;
END $$;
