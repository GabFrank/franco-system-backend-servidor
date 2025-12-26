ALTER TABLE operaciones.pedido
  ADD COLUMN IF NOT EXISTS tipo_boleta varchar NULL;
