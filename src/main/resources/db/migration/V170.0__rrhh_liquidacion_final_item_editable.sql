-- Finiquito con ítems totalmente editables ("todo es negociable"):
-- - tipo (HABER/DESCUENTO) para que el total sea Σ haberes − Σ descuentos.
-- - manual: distingue ítems agregados a mano de los automáticos.
-- - auditoría de edición: editado / editado_por_id / editado_en.
-- - monto_original: valor auto antes de la primera edición (delta negociado).
-- Todo aditivo e idempotente.

ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'HABER';
ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS manual BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS editado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS editado_por_id BIGINT;
ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS editado_en TIMESTAMP;
ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS monto_original NUMERIC(18,2);
