-- Flag para consolidar en una sola linea del recibo las cuotas de compras a credito.
--
-- Motivo del pedido: un funcionario con muchas compras genera una cuota por venta y la
-- hoja de liquidacion se vuelve ilegible, cuando el desglose ya se le entrega por otra via.
--
-- Solo afecta la IMPRESION. Los LiquidacionItem individuales se siguen guardando, porque
-- aplicarEfectosCruzados usa el referenciaId de cada uno para saldar su cuota al pagar la
-- liquidacion: consolidando en la base se descontaria la plata del sueldo y las cuotas
-- quedarian vivas.
--
-- Arranca en false: es un cambio de formato del recibo, que cada instancia decide.

INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion, creado_en)
SELECT v.clave, v.valor, v.tipo, v.descripcion, now()
FROM (VALUES
    ('LIQUIDACION_CONSOLIDAR_CUOTAS_CREDITO', 'false', 'BOOLEAN',
     'Agrupar las cuotas de compras a credito en un solo item del recibo de liquidacion')
) AS v(clave, valor, tipo, descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM rrhh.configuracion_rrhh c WHERE c.clave = v.clave
);
