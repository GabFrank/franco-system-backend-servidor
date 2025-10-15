ALTER TABLE productos.producto_proveedor DROP CONSTRAINT producto_proveedor_pedido_id_fkey;
ALTER TABLE productos.producto_proveedor ADD CONSTRAINT producto_proveedor_pedido_id_fkey FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE productos.producto_proveedor DROP CONSTRAINT producto_proveedor_producto_id_fkey;
ALTER TABLE productos.producto_proveedor ADD CONSTRAINT producto_proveedor_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE productos.producto_proveedor DROP CONSTRAINT producto_proveedor_proveedor_id_fkey;
ALTER TABLE productos.producto_proveedor ADD CONSTRAINT producto_proveedor_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id) ON DELETE CASCADE ON UPDATE CASCADE;

