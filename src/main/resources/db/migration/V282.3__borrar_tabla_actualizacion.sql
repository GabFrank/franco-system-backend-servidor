-- #526: borra configuraciones.actualizacion, del modulo viejo de actualizacion. El codigo que la
-- usaba ya se quito (frc-sistemas-integrados-angular#632) y la tabla quedo sin lectores ni escritores.
-- Se verifico vacia (0 filas, secuencia sin usar) en produccion, alpha y las copias locales.
--
-- Orden: primero el central (esta migracion) y despues la filial (V113.3 del filial). Si la filial
-- borrara la tabla mientras central_pub todavia la publica, el REFRESH PUBLICATION de su suscripcion
-- fallaria por la tabla faltante.
--
-- La fila de replication_table se borra en la misma migracion: si quedara, el
-- ReplicationPublicationSyncScheduler volveria a agregar la tabla a central_pub.

DO $$
DECLARE
    r record;
BEGIN
    IF to_regclass('configuraciones.actualizacion') IS NULL THEN
        RETURN;
    END IF;

    -- Por las dudas: nunca borrar datos en silencio.
    IF EXISTS (SELECT 1 FROM configuraciones.actualizacion) THEN
        RAISE EXCEPTION 'configuraciones.actualizacion tiene datos; revisar antes de borrarla (#526)';
    END IF;

    -- Sacarla de toda publicacion que la incluya (en produccion: solo central_pub).
    FOR r IN
        SELECT pt.pubname
        FROM pg_publication_tables pt
        JOIN pg_publication p ON p.pubname = pt.pubname
        WHERE pt.schemaname = 'configuraciones'
          AND pt.tablename = 'actualizacion'
          AND NOT p.puballtables
    LOOP
        EXECUTE format('ALTER PUBLICATION %I DROP TABLE configuraciones.actualizacion', r.pubname);
    END LOOP;
END $$;

DELETE FROM configuraciones.replication_table
WHERE table_name = 'configuraciones.actualizacion';

-- La secuencia actualizacion_id_seq es OWNED BY la tabla y cae con ella; el IF EXISTS cubre bases
-- donde no lo fuera.
DROP TABLE IF EXISTS configuraciones.actualizacion;
DROP SEQUENCE IF EXISTS configuraciones.actualizacion_id_seq;
DROP TYPE IF EXISTS configuraciones.tipo_actualizacion;
DROP TYPE IF EXISTS configuraciones.nivel_actualizacion;
