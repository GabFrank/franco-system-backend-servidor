-- =====================================================================
-- sorteos: alta de sorteo / sorteo_sucursal / sorteo_premio en central_pub
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- V260.3 creo las tablas y las registro en replication_table, pero dejo el ALTER PUBLICATION al
-- ReplicationPublicationSyncScheduler. Donde ese scheduler no corre (o un restore saca las tablas
-- de la publicacion) el sorteo se carga en el central y nunca llega a la filial: el punto de venta
-- no lo ve. Aca la publicacion se arma en la misma migracion, sin depender de nada mas.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ EL FILIAL (V109.3 + V115.3) VA ANTES, EN CADA FILIAL
--
-- Una filial sin las tablas de sorteo corta TODA su replicacion entrante apenas entran a
-- central_pub ("logical replication target relation does not exist"). Ver V260.3.
--
-- REFRESH DE LAS SUSCRIPCIONES (no va aca)
--
-- ALTER SUBSCRIPTION ... REFRESH PUBLICATION no se puede ejecutar dentro de una transaccion ni de
-- una funcion, y Flyway corre cada migracion en una. Lo hace el central al arrancar
-- (LogicalReplicationService.syncPublicationsWithReplicationTable, paso 4: refresca en cada filial
-- las suscripciones a central_pub y en el central las filialX_sub). copy_data=false: no cargar un
-- sorteo real hasta ver srsubstate='r' de las tres tablas en cada filial (checklist de V260.3).
--
-- IDEMPOTENTE: ADD TABLE sobre una tabla ya publicada es error, asi que se pregunta antes. Si la
-- base no tiene central_pub (replicacion sin configurar, CI) no hace nada.
-- =====================================================================
DO $$
DECLARE
    v_tabla TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'central_pub') THEN
        RAISE NOTICE 'No existe central_pub - se omite (replicacion no configurada en esta base)';
        RETURN;
    END IF;

    FOREACH v_tabla IN ARRAY ARRAY['sorteo', 'sorteo_sucursal', 'sorteo_premio'] LOOP
        IF EXISTS (
            SELECT 1 FROM pg_publication_tables
            WHERE pubname = 'central_pub' AND schemaname = 'operaciones' AND tablename = v_tabla
        ) THEN
            RAISE NOTICE 'operaciones.% ya esta en central_pub', v_tabla;
        ELSE
            EXECUTE format('ALTER PUBLICATION central_pub ADD TABLE operaciones.%I', v_tabla);
            RAISE NOTICE 'Agregada operaciones.% a central_pub', v_tabla;
        END IF;
    END LOOP;
END $$;
