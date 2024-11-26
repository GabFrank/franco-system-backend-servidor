ALTER TABLE operaciones.pedido_item RENAME COLUMN precio_unitario TO precio_unitario_creacion;
ALTER TABLE operaciones.pedido_item RENAME COLUMN descuento_unitario TO descuento_unitario_creacion;
ALTER TABLE operaciones.pedido_item RENAME COLUMN vencimiento TO vencimiento_creacion;
ALTER TABLE operaciones.pedido_item RENAME COLUMN presentacion_id TO presentacion_id_creacion;
ALTER TABLE operaciones.pedido_item RENAME COLUMN cantidad TO cantidad_creacion;

ALTER TABLE operaciones.pedido_item ADD precio_unitario_recepcion_nota numeric NULL;
ALTER TABLE operaciones.pedido_item ADD descuento_unitario_recepcion_nota numeric NULL;
ALTER TABLE operaciones.pedido_item ADD vencimiento_recepcion_nota timestamp NULL;
ALTER TABLE operaciones.pedido_item ADD presentacion_id_recepcion_nota int8 NULL;
ALTER TABLE operaciones.pedido_item ADD cantidad_recepcion_nota numeric NULL;

ALTER TABLE operaciones.pedido_item ADD precio_unitario_recepcion_producto numeric NULL;
ALTER TABLE operaciones.pedido_item ADD descuento_unitario_recepcion_producto numeric NULL;
ALTER TABLE operaciones.pedido_item ADD vencimiento_recepcion_producto timestamp NULL;
ALTER TABLE operaciones.pedido_item ADD presentacion_id_recepcion_producto int8 NULL;
ALTER TABLE operaciones.pedido_item ADD cantidad_recepcion_producto numeric NULL;

ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_presentacion_recepcion_nota_fk FOREIGN KEY (presentacion_id_recepcion_nota) REFERENCES productos.presentacion(id);
ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_presentacion_recepcion_producto_fk FOREIGN KEY (presentacion_id_recepcion_producto) REFERENCES productos.presentacion(id);

ALTER TABLE operaciones.pedido_item RENAME COLUMN usuario_id TO usuario_creacion_id;
ALTER TABLE operaciones.pedido_item ADD obs_creacion varchar NULL;
ALTER TABLE operaciones.pedido_item ADD obs_recepcion_nota varchar NULL;
ALTER TABLE operaciones.pedido_item ADD obs_recepcion_producto varchar NULL;
ALTER TABLE operaciones.pedido_item ADD autorizacion_recepcion_nota boolean NULL;
ALTER TABLE operaciones.pedido_item ADD autorizacion_recepcion_producto boolean NULL;
ALTER TABLE operaciones.pedido_item ADD autorizado_por_recepcion_nota_id int8 NULL;
ALTER TABLE operaciones.pedido_item ADD autorizado_por_recepcion_producto_id int8 NULL;
ALTER TABLE operaciones.pedido_item ADD usuario_recepcion_nota_id int8 NULL;
ALTER TABLE operaciones.pedido_item ADD usuario_recepcion_producto_id int8 NULL;
ALTER TABLE operaciones.pedido_item ADD motivo_modificacion_recepcion_nota varchar NULL;
ALTER TABLE operaciones.pedido_item ADD motivo_modificacion_recepcion_producto varchar NULL;
ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_autorizado_por_recepcion_nota_fk FOREIGN KEY (autorizado_por_recepcion_nota_id) REFERENCES personas.usuario(id);
ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_autorizado_por_recepcion_producto_fk FOREIGN KEY (autorizado_por_recepcion_producto_id) REFERENCES personas.usuario(id);
ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_usuario_recepcion_nota_id_fk FOREIGN KEY (usuario_recepcion_nota_id) REFERENCES personas.usuario(id);
ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_usuario_recepcion_producto_id_recepcion_producto_fk FOREIGN KEY (usuario_recepcion_producto_id) REFERENCES personas.usuario(id);
