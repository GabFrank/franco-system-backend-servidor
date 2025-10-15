ALTER TABLE operaciones.pedido_item_sucursal RENAME COLUMN cantidad TO cantidad_por_unidad;
ALTER TABLE operaciones.pedido_item_sucursal ADD cant_por_unidad_recibida numeric NULL;

