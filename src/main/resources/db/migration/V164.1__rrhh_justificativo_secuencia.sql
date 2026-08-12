-- =====================================================================
-- RRHH — Renombra la secuencia del id de justificativo.
-- =====================================================================
-- Al hacer ALTER TABLE ... RENAME en la V164.0, Postgres conserva el nombre
-- original de la secuencia (jornada_novedad_id_seq). El generador de ids del
-- proyecto (AssignedIdentityGenerator) la busca por la convencion
-- <tabla>_id_seq, por lo que fallaba al insertar:
--   ERROR: no existe la relacion "rrhh.justificativo_id_seq"
-- Idempotente.
-- =====================================================================

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.sequences
               WHERE sequence_schema = 'rrhh' AND sequence_name = 'jornada_novedad_id_seq')
       AND NOT EXISTS (SELECT 1 FROM information_schema.sequences
               WHERE sequence_schema = 'rrhh' AND sequence_name = 'justificativo_id_seq') THEN
        ALTER SEQUENCE rrhh.jornada_novedad_id_seq RENAME TO justificativo_id_seq;
    END IF;
END $$;
