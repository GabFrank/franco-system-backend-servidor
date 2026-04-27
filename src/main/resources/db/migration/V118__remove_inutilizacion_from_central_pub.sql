-- Remove evento_inutilizacion tables from central_pub
-- These tables are central-only and should not be replicated to filiales
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'central_pub'
      AND schemaname = 'financiero'
      AND tablename = 'evento_inutilizacion_de'
  ) THEN
    ALTER PUBLICATION central_pub DROP TABLE financiero.evento_inutilizacion_de;
  END IF;

  IF EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'central_pub'
      AND schemaname = 'financiero'
      AND tablename = 'evento_inutilizacion_de_documento_electronico'
  ) THEN
    ALTER PUBLICATION central_pub DROP TABLE financiero.evento_inutilizacion_de_documento_electronico;
  END IF;
END
$$;
