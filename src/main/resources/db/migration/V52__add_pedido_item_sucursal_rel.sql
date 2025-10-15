ALTER TABLE operaciones.pedido_item_sucursal ADD CONSTRAINT pedido_item_sucursal_pedido_item_fk FOREIGN KEY (pedido_item_id) REFERENCES operaciones.pedido_item(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE operaciones.pedido_item_sucursal ADD CONSTRAINT pedido_item_sucursal_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE operaciones.pedido_item_sucursal ADD CONSTRAINT pedido_item_sucursal_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE operaciones.pedido_item_sucursal ADD CONSTRAINT pedido_item_sucursal_sucursal_entrega_fk FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE ON UPDATE CASCADE;

