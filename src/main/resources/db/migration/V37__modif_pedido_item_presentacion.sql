ALTER TABLE operaciones.pedido_item RENAME COLUMN presentacion_id_creacion TO presentacion_creacion_id;
ALTER TABLE operaciones.pedido_item RENAME COLUMN presentacion_id_recepcion_nota TO presentacion_recepcion_nota_id;
ALTER TABLE operaciones.pedido_item RENAME COLUMN presentacion_id_recepcion_producto TO presentacion_recepcion_producto_id;