-- Agregar campo usuario_id a la tabla recepcion_mercaderia_item
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD COLUMN usuario_id BIGINT NOT NULL DEFAULT 1;

-- Agregar foreign key constraint
ALTER TABLE operaciones.recepcion_mercaderia_item 
ADD CONSTRAINT fk_recepcion_mercaderia_item_usuario 
FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);

-- Crear índice para mejorar performance
CREATE INDEX idx_recepcion_mercaderia_item_usuario_id 
ON operaciones.recepcion_mercaderia_item(usuario_id);

-- Comentario sobre la migración
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.usuario_id IS 'Usuario que realizó la recepción del ítem'; 