-- Responsable de un egreso de la caja mayor: el funcionario que retira o rinde la plata, elegido al
-- registrarlo (egreso de efectivo, egreso vario, egreso de maletin y pago de gasto). Opcional: sin él,
-- el movimiento se muestra con el usuario que lo cargo, como hasta ahora.
-- Las dos tablas son solo del central (no se replican a las filiales).

ALTER TABLE financiero.movimiento_caja_virtual
    ADD COLUMN IF NOT EXISTS responsable_id bigint REFERENCES personas.funcionario (id);

-- El gasto creado desde la caja guarda su responsable en la solicitud de pago; al pagarse pasa al
-- movimiento de la caja y al gasto materializado (financiero.gasto.responsable_id).
ALTER TABLE operaciones.solicitud_pago
    ADD COLUMN IF NOT EXISTS responsable_id bigint REFERENCES personas.funcionario (id);
