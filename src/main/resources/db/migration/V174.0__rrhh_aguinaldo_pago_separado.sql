-- Pago separado del aguinaldo: trazabilidad del egreso de Caja Mayor (igual que
-- rrhh.prestamo). Aditivo, nullable. El estado 'PAGADO' + fecha_pago ya existen.
ALTER TABLE rrhh.aguinaldo ADD COLUMN IF NOT EXISTS caja_virtual_id BIGINT;
ALTER TABLE rrhh.aguinaldo ADD COLUMN IF NOT EXISTS movimiento_caja_virtual_id BIGINT;
