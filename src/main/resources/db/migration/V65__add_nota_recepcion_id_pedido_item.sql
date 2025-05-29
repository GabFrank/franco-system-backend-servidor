ALTER TABLE operaciones.pedido_item ADD nota_recepcion_id int8 NULL;
ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_nota_recepcion_fk FOREIGN KEY (nota_recepcion_id) REFERENCES operaciones.nota_recepcion(id) ON DELETE SET NULL ON UPDATE CASCADE;
