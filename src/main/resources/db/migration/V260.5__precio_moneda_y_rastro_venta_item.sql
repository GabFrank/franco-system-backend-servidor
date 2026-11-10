-- =====================================================================
-- Moneda del precio de venta (entrega 3 de la moneda por modulo) y rastro en venta_item
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- precio_por_sucursal no tenia moneda: todo precio era de la moneda de la empresa (la principal de
-- financiero). moneda_id dice en que moneda esta cada precio; NULL = la principal, sin backfill.
-- precio_especial_sucursal y programar_precio heredan la de su precio base.
--
-- Cuando el precio esta en otra moneda que la que cobra el PDV, el escritorio convierte al agregar
-- el item. venta_item.precio sigue en la moneda en que se cobra (lucro y reportes no cambian) y deja
-- el rastro: la moneda del precio de lista, el precio de lista en esa moneda y la cotizacion usada
-- (unidades de la moneda de cobro por unidad de la moneda del precio). NULL = sin conversion.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTE CENTRAL VA ANTES QUE LA FILIAL (V110.5)
--
-- - venta_item sube de la filial al central (filial<N>_pub, sin lista de columnas): la filial que
--   tenga las columnas las publica, y un central sin ellas deja la suscripcion en crash-loop
--   ("missing replicated column"). Por eso aca primero.
-- - precio_por_sucursal baja por central_pub, que publica CON lista de columnas (verificado en
--   farmacia y bodega 2026-10-29): moneda_id no viaja hasta agregarla a la lista, y eso se hace
--   recien cuando toda la flota tenga el espejo (filial V110.5). Ver 03-plan-precios-y-pdv.md.
--
-- ESTE ES EL LADO PUBLISHER de precio_por_sucursal: la FK vive aca (NOT VALID: no recorre la tabla).
-- =====================================================================
ALTER TABLE productos.precio_por_sucursal ADD COLUMN IF NOT EXISTS moneda_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'precio_por_sucursal_moneda_fk') THEN
        ALTER TABLE productos.precio_por_sucursal
            ADD CONSTRAINT precio_por_sucursal_moneda_fk
            FOREIGN KEY (moneda_id) REFERENCES financiero.moneda (id) NOT VALID;
    END IF;
END $$;

COMMENT ON COLUMN productos.precio_por_sucursal.moneda_id IS
    'Moneda del precio. NULL = la moneda de la empresa (principal). El precio especial y el programado heredan esta.';

ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_moneda_id BIGINT;
ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_lista NUMERIC;
ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_cotizacion NUMERIC;

COMMENT ON COLUMN operaciones.venta_item.precio_moneda_id IS
    'Moneda del precio de lista cuando difiere de la que se cobro. NULL = sin conversion.';
COMMENT ON COLUMN operaciones.venta_item.precio_lista IS
    'Precio de lista en precio_moneda_id. precio sigue en la moneda en que se cobro.';
COMMENT ON COLUMN operaciones.venta_item.precio_cotizacion IS
    'Unidades de la moneda de cobro por unidad de precio_moneda_id con que se convirtio el precio.';
