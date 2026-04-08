-- Add ENVIADO_A_TESORERIA to estado_pre_gasto enum
-- In Postgres, ALTER TYPE ADD VALUE cannot be executed in a transaction block prior to PG 12
-- Flyway might consider this non-transactional.
ALTER TYPE financiero.estado_pre_gasto ADD VALUE IF NOT EXISTS 'ENVIADO_A_TESORERIA';
