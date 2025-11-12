ALTER TABLE operaciones.inventario_producto_item ADD COLUMN cantidad_anterior numeric NULL;
ALTER TABLE operaciones.inventario_producto_item ADD COLUMN fecha_verificado timestamp NULL;
ALTER TABLE operaciones.inventario_producto_item ADD COLUMN verificado bool DEFAULT false;
ALTER TABLE operaciones.inventario_producto_item ADD COLUMN revisado bool DEFAULT false;