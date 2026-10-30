-- Vuelto de un egreso de la caja mayor: el ingreso con el que el responsable devuelve lo que sobro de un
-- egreso de efectivo, egreso vario, egreso de maletin o pago de gasto. Puede devolverse en cualquier moneda
-- y en varias veces, hasta completar el egreso.
--   vuelto_de_id       el egreso que se rinde (otro movimiento de la misma caja)
--   vuelto_equivalente lo devuelto expresado en la moneda del egreso (con la cotizacion del momento):
--                      es lo que descuenta del pendiente del egreso.
-- La tabla es solo del central (no se replica a las filiales).

ALTER TABLE financiero.movimiento_caja_virtual
    ADD COLUMN IF NOT EXISTS vuelto_de_id bigint REFERENCES financiero.movimiento_caja_virtual (id),
    ADD COLUMN IF NOT EXISTS vuelto_equivalente numeric(18, 4);

CREATE INDEX IF NOT EXISTS idx_movimiento_caja_virtual_vuelto_de
    ON financiero.movimiento_caja_virtual (vuelto_de_id)
    WHERE vuelto_de_id IS NOT NULL;
