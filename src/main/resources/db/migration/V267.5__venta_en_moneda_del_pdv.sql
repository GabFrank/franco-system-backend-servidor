-- =====================================================================
-- Venta en la moneda del PDV (entrega 4 de la moneda por modulo) + modulo FACTURACION
-- =====================================================================
-- QUE PROBLEMA RESUELVE
--
-- El PDV puede operar en otra moneda que la de la empresa (modulo PDV, por sucursal). Las columnas
-- de siempre (venta.total_gs, venta_item.precio, cobro.total_gs) siguen en la moneda de la empresa
-- (la principal de financiero): son el equivalente, y con ellas lucro, caja mayor, reportes y el
-- bot no cambian. Las columnas nuevas guardan la venta en su propia moneda:
--
--   venta.moneda_id         moneda en que opero el PDV. NULL = la de la empresa (ventas viejas).
--   venta.cotizacion        unidades de la moneda de la empresa por unidad de venta.moneda_id.
--   venta.total_moneda      total de la venta en venta.moneda_id.
--   venta_item.precio_en_moneda precio unitario cobrado, en venta.moneda_id.
--   cobro.total_moneda      total del cobro en la moneda de la venta.
--   cobro_detalle.cotizacion   cotizacion precisa de la linea (moneda de la empresa por unidad de
--                              cobro_detalle.moneda_id). cobro_detalle.cambio es numeric(19,0) en
--                              produccion y pierde los decimales; sigue grabandose como siempre.
--
-- FACTURACION: la moneda en que sale la factura legal, por sucursal como el PDV. Sin fila, la
-- factura sale en la moneda de la venta.
--
-- ORDEN DE DESPLIEGUE --- ⚠️ ESTE CENTRAL VA ANTES QUE LA FILIAL (V111.5)
--
-- venta, venta_item, cobro y cobro_detalle suben de la filial al central (filial<N>_pub, sin lista
-- de columnas). Una filial con las columnas las publica, y un central sin ellas deja la suscripcion
-- en crash-loop ("missing replicated column"). Las bajadas al resto de las filiales
-- (central_filial<N>_pub) publican CON lista de columnas: las nuevas no viajan hasta ampliarla, y
-- eso no hace falta para operar (cada venta vive en su filial).
--
-- ESTE ES EL LADO SUBSCRIBER de las cuatro tablas de la venta: sin FK ni CHECK (viven donde se
-- escribe). Es el PUBLISHER de configuracion_moneda_modulo: su CHECK se amplia aca; el espejo del
-- filial no tiene CHECK e ignora un modulo que no conoce.
-- =====================================================================
ALTER TABLE operaciones.venta ADD COLUMN IF NOT EXISTS moneda_id BIGINT;
ALTER TABLE operaciones.venta ADD COLUMN IF NOT EXISTS cotizacion NUMERIC;
ALTER TABLE operaciones.venta ADD COLUMN IF NOT EXISTS total_moneda NUMERIC;
ALTER TABLE operaciones.venta_item ADD COLUMN IF NOT EXISTS precio_en_moneda NUMERIC;
ALTER TABLE operaciones.cobro ADD COLUMN IF NOT EXISTS total_moneda NUMERIC;
ALTER TABLE operaciones.cobro_detalle ADD COLUMN IF NOT EXISTS cotizacion NUMERIC;

COMMENT ON COLUMN operaciones.venta.moneda_id IS
    'Moneda en que opero el PDV. NULL = la moneda de la empresa. total_gs sigue en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.venta.cotizacion IS
    'Unidades de la moneda de la empresa por unidad de moneda_id al cerrar la venta.';
COMMENT ON COLUMN operaciones.venta.total_moneda IS
    'Total de la venta en moneda_id. total_gs es su equivalente en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.venta_item.precio_en_moneda IS
    'Precio unitario cobrado en la moneda de la venta. precio es su equivalente en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.cobro.total_moneda IS
    'Total del cobro en la moneda de la venta. total_gs es su equivalente en la moneda de la empresa.';
COMMENT ON COLUMN operaciones.cobro_detalle.cotizacion IS
    'Unidades de la moneda de la empresa por unidad de moneda_id, con decimales. cambio sigue como siempre (entero).';

-- ---------------------------------------------------------------------
-- Modulo FACTURACION. Mismo valor en el enum Java (ModuloMoneda) y en el .graphqls.
-- ---------------------------------------------------------------------
ALTER TABLE financiero.configuracion_moneda_modulo DROP CONSTRAINT IF EXISTS ck_configuracion_moneda_modulo_modulo;
ALTER TABLE financiero.configuracion_moneda_modulo ADD CONSTRAINT ck_configuracion_moneda_modulo_modulo
    CHECK (modulo IN ('COMPRAS', 'COSTOS', 'PRECIOS', 'PDV', 'FACTURACION'));

-- PDV y FACTURACION pueden variar por sucursal (decision de negocio); el resto es de toda la empresa.
ALTER TABLE financiero.configuracion_moneda_modulo DROP CONSTRAINT IF EXISTS ck_configuracion_moneda_modulo_sucursal;
ALTER TABLE financiero.configuracion_moneda_modulo ADD CONSTRAINT ck_configuracion_moneda_modulo_sucursal
    CHECK (sucursal_id IS NULL OR modulo IN ('PDV', 'FACTURACION'));
