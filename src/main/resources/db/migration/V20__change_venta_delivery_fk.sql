ALTER TABLE operaciones.venta DROP CONSTRAINT fk_venta_delivery;
ALTER TABLE operaciones.venta ADD CONSTRAINT fk_venta_delivery FOREIGN KEY (delivery_id,sucursal_id) REFERENCES operaciones.delivery(id,sucursal_id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE operaciones.delivery DROP CONSTRAINT delivery_fk_venta;
ALTER TABLE operaciones.delivery DROP COLUMN venta_id;
