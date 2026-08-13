-- Opción 3: la diferencia de redondeo/cambio de un pago CPP multi-moneda se registra
-- como una línea de ajuste (fuente AJUSTE) en pago_solicitud_detalle, marcada descuento
-- (pagó de menos, a favor) o aumento (pagó de más, en contra). Espejo de ventas
-- (operaciones.cobro_detalle.descuento/aumento). No mueve caja ni banco.
ALTER TABLE financiero.pago_solicitud_detalle ADD COLUMN IF NOT EXISTS descuento boolean DEFAULT false;
ALTER TABLE financiero.pago_solicitud_detalle ADD COLUMN IF NOT EXISTS aumento boolean DEFAULT false;
