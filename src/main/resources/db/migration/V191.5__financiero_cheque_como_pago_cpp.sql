-- Cheque como forma de pago CPP: link detalle→cheque emitido + campos beneficiario/nominal.
-- Un cheque diferido reserva saldo del banco (saldo futuro comprometido); contado lo debita.
ALTER TABLE financiero.pago_solicitud_detalle ADD COLUMN IF NOT EXISTS cheque_id bigint;
CREATE INDEX IF NOT EXISTS ix_pago_solicitud_detalle_cheque ON financiero.pago_solicitud_detalle (cheque_id);

ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS beneficiario varchar(200);
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS nominal boolean DEFAULT true;
