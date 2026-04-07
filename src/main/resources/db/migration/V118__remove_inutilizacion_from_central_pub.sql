-- Remove evento_inutilizacion tables from central_pub
-- These tables are central-only and should not be replicated to filiales
ALTER PUBLICATION central_pub DROP TABLE IF EXISTS financiero.evento_inutilizacion_de;
ALTER PUBLICATION central_pub DROP TABLE IF EXISTS financiero.evento_inutilizacion_de_documento_electronico;
