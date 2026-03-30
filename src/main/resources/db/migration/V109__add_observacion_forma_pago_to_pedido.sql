-- Add observacion_forma_pago to operaciones.pedido for payment terms details (e.g. "3 cheques 30, 60 y 90 días")
ALTER TABLE operaciones.pedido ADD COLUMN IF NOT EXISTS observacion_forma_pago TEXT;
