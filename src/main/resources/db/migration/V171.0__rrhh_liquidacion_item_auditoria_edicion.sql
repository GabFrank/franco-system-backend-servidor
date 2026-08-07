-- Liquidación de sueldo mensual: mismos ítems editables + auditoría de edición
-- (espejo del finiquito). liquidacion_item ya tiene tipo/manual; se agregan solo
-- los campos de auditoría de edición y el monto original. Aditivo e idempotente.

ALTER TABLE rrhh.liquidacion_item ADD COLUMN IF NOT EXISTS editado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE rrhh.liquidacion_item ADD COLUMN IF NOT EXISTS editado_por_id BIGINT;
ALTER TABLE rrhh.liquidacion_item ADD COLUMN IF NOT EXISTS editado_en TIMESTAMP;
ALTER TABLE rrhh.liquidacion_item ADD COLUMN IF NOT EXISTS monto_original NUMERIC(18,2);
