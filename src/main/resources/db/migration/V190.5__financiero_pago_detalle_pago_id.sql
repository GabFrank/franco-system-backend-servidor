-- Consolidación del pago CPP: un evento de pago (operaciones.pago) agrupa todas las notas y
-- consolida los movimientos de caja/banco por (fuente, caja/cuenta, moneda). Cada detalle
-- referencia el evento vía pago_id para poder anular todo el evento de una sola vez.
ALTER TABLE financiero.pago_solicitud_detalle ADD COLUMN IF NOT EXISTS pago_id bigint;
CREATE INDEX IF NOT EXISTS ix_pago_solicitud_detalle_pago ON financiero.pago_solicitud_detalle (pago_id);
