-- Referencia al registro origen de cada ítem del finiquito (vale, cuota de préstamo,
-- etc.), para poder saldar esos registros al pagar y revertir al anular — igual que
-- la liquidación de sueldo mensual (aplicarEfectosCruzados). Aditivo e idempotente.

ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS referencia_id BIGINT;
ALTER TABLE rrhh.liquidacion_final_item ADD COLUMN IF NOT EXISTS referencia_tipo VARCHAR(40);
