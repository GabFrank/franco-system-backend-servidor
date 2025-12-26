-- Agregar columna pedido_item_distribucion_id que falta en la tabla recepcion_mercaderia_item
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD COLUMN pedido_item_distribucion_id BIGINT;

-- Agregar foreign key constraint
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD CONSTRAINT fk_recepcion_mercaderia_item_pedido_item_distribucion 
FOREIGN KEY (pedido_item_distribucion_id) REFERENCES operaciones.pedido_item_distribucion(id);

-- Crear índice para mejorar performance
CREATE INDEX idx_recepcion_mercaderia_item_pedido_item_distribucion_id 
ON operaciones.recepcion_mercaderia_item(pedido_item_distribucion_id);

-- Comentario sobre la migración
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.pedido_item_distribucion_id IS 'Referencia a la distribución del pedido item asociada a esta recepción'; 