-- Puente Vale -> SolicitudPago(tipo=RRHH): permite pagar un vale con el motor de pago
-- de CPP (caja mayor / cuenta bancaria / cheque, multi-moneda), igual que un gasto.
--
-- Aditiva: columna nullable. Los vales existentes quedan con NULL, que significa
-- "vale del atajo viejo" (crearValeConfirmado, egreso directo EGRESO/RRHH_VALE).

ALTER TABLE rrhh.vale
    ADD COLUMN IF NOT EXISTS solicitud_pago_id BIGINT;

-- Un vale apunta a lo sumo a una solicitud, y una solicitud pertenece a lo sumo a un vale.
CREATE UNIQUE INDEX IF NOT EXISTS ux_vale_solicitud_pago_id
    ON rrhh.vale (solicitud_pago_id)
    WHERE solicitud_pago_id IS NOT NULL;
