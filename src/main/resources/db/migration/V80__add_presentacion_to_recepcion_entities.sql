-- V80: Agregar campos de presentación a las entidades de recepción
-- Esto permite trazabilidad completa de la presentación a través del flujo de compras

-- Agregar presentacion_en_nota a nota_recepcion_item
ALTER TABLE operaciones.nota_recepcion_item 
ADD COLUMN presentacion_en_nota_id BIGINT;

-- Agregar foreign key constraint
ALTER TABLE operaciones.nota_recepcion_item 
ADD CONSTRAINT fk_nota_recepcion_item_presentacion_en_nota 
FOREIGN KEY (presentacion_en_nota_id) REFERENCES productos.presentacion(id);

-- Agregar presentacion_recibida a recepcion_mercaderia_item
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD COLUMN presentacion_recibida_id BIGINT;

-- Agregar foreign key constraint
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD CONSTRAINT fk_recepcion_mercaderia_item_presentacion_recibida 
FOREIGN KEY (presentacion_recibida_id) REFERENCES productos.presentacion(id);

-- Agregar comentarios para documentar el propósito de estos campos
COMMENT ON COLUMN operaciones.nota_recepcion_item.presentacion_en_nota_id IS 'Presentación que el proveedor especifica en su factura/nota de recepción';
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.presentacion_recibida_id IS 'Presentación que se recibe físicamente en la sucursal';

-- Crear índices para mejorar el rendimiento de las consultas
CREATE INDEX idx_nota_recepcion_item_presentacion_en_nota ON operaciones.nota_recepcion_item(presentacion_en_nota_id);
CREATE INDEX idx_recepcion_mercaderia_item_presentacion_recibida ON operaciones.recepcion_mercaderia_item(presentacion_recibida_id); 