-- Agregar cotizaciones de mercado a la tabla de cambio
-- valorEnGsVentaMercado: tasa real de venta en el mercado (ej: 7700 Gs por 1 USD)
-- valorEnGsCompraMercado: tasa real de compra en el mercado (ej: 7650 Gs por 1 USD)
-- Estas tasas se usan en operaciones de compra a proveedores, separadas de las tasas
-- conservadoras de venta al público (valorEnGs) y compra de divisa (valorEnGsCambio)

ALTER TABLE financiero.cambio ADD COLUMN IF NOT EXISTS valor_en_gs_venta_mercado DOUBLE PRECISION;
ALTER TABLE financiero.cambio ADD COLUMN IF NOT EXISTS valor_en_gs_compra_mercado DOUBLE PRECISION;

COMMENT ON COLUMN financiero.cambio.valor_en_gs_venta_mercado IS 'Cotización de venta real del mercado';
COMMENT ON COLUMN financiero.cambio.valor_en_gs_compra_mercado IS 'Cotización de compra real del mercado - usada en pedidos de compra a proveedores';
