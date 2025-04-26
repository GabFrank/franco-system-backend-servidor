ALTER TABLE operaciones.nota_recepcion ADD CONSTRAINT nota_recepcion_unique UNIQUE (pedido_id,numero);
