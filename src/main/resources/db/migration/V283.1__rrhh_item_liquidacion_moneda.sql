-- Moneda de cada item de liquidacion y de finiquito (docs/manuales-implementacion/rrhh/PLAN-MONEDA-ITEMS-LIQUIDACION.md).
--
-- monto        sigue en la moneda de la liquidacion (totales, caja, solicitud de pago).
-- moneda_id    moneda del origen del item (vale, prestamo, sueldo del funcionario...).
-- monto_moneda el monto en esa moneda: lo que se descuenta de la cuota / vale de origen.
-- cotizacion   factor origen -> moneda de la liquidacion, sin redondear (monto = monto_moneda * cotizacion).
--
-- Aditiva y sin backfill: null = item anterior a esto, en la moneda de la liquidacion
-- (quien lee usa coalesce(monto_moneda, monto)). El schema rrhh es solo del central: no va a las filiales.

ALTER TABLE rrhh.liquidacion_item
    ADD COLUMN IF NOT EXISTS moneda_id    BIGINT REFERENCES financiero.moneda(id),
    ADD COLUMN IF NOT EXISTS monto_moneda NUMERIC,
    ADD COLUMN IF NOT EXISTS cotizacion   NUMERIC;

ALTER TABLE rrhh.liquidacion_final_item
    ADD COLUMN IF NOT EXISTS moneda_id    BIGINT REFERENCES financiero.moneda(id),
    ADD COLUMN IF NOT EXISTS monto_moneda NUMERIC,
    ADD COLUMN IF NOT EXISTS cotizacion   NUMERIC;
