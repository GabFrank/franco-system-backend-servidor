-- Puente Liquidacion / Finiquito / Aguinaldo -> SolicitudPago(tipo=RRHH).
--
-- Extiende a los otros conceptos de RRHH el puente que V198.5 abrio para el vale: pagarlos
-- con el motor de pago de CPP (caja mayor / cuenta bancaria / cheque, multi-moneda) desde
-- el hub de egresos de la caja, en vez de con un egreso directo desde las pantallas de RRHH.
--
-- Aditiva: columnas nullable. Las filas existentes quedan con NULL, que significa "pagada
-- por el atajo viejo" (pagar(id, cajaVirtualId), egreso directo EGRESO/RRHH_*).

ALTER TABLE rrhh.liquidacion_sueldo
    ADD COLUMN IF NOT EXISTS solicitud_pago_id BIGINT;

ALTER TABLE rrhh.liquidacion_final
    ADD COLUMN IF NOT EXISTS solicitud_pago_id BIGINT;

ALTER TABLE rrhh.aguinaldo
    ADD COLUMN IF NOT EXISTS solicitud_pago_id BIGINT;

-- Cada obligacion pertenece a lo sumo a un documento de RRHH (mismo criterio que el vale).
CREATE UNIQUE INDEX IF NOT EXISTS ux_liquidacion_sueldo_solicitud_pago_id
    ON rrhh.liquidacion_sueldo (solicitud_pago_id)
    WHERE solicitud_pago_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_liquidacion_final_solicitud_pago_id
    ON rrhh.liquidacion_final (solicitud_pago_id)
    WHERE solicitud_pago_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_aguinaldo_solicitud_pago_id
    ON rrhh.aguinaldo (solicitud_pago_id)
    WHERE solicitud_pago_id IS NOT NULL;
