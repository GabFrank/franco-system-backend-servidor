-- Operación financiera: diferencia (sobra/falta) imputable como ajuste etiquetado.
-- Aditiva, idempotente.

ALTER TABLE financiero.operacion_financiera ADD COLUMN IF NOT EXISTS diferencia numeric;
ALTER TABLE financiero.operacion_financiera ADD COLUMN IF NOT EXISTS diferencia_destino_tipo varchar(20);
ALTER TABLE financiero.operacion_financiera ADD COLUMN IF NOT EXISTS diferencia_observacion text;
