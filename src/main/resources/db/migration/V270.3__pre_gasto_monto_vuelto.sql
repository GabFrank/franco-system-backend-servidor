-- Vuelto que el funcionario devolvió en la caja (PDV o caja mayor), en la moneda de la solicitud.
-- NULL = todavía sin vuelto: la rendición sale de lo que cargó el teléfono.
-- Con valor, manda el vuelto: rendido = retirado - vuelto (lo que de verdad se devolvió).
-- pre_gasto es central-only (no se replica), así que no hace falta tocar las filiales.
ALTER TABLE financiero.pre_gasto ADD COLUMN IF NOT EXISTS monto_vuelto NUMERIC(19, 2);
