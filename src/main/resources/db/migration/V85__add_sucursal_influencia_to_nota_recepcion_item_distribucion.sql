-- Migración V85: Agregar campo sucursal_influencia_id a nota_recepcion_item_distribucion
-- Fecha: 2024-12-19
-- Descripción: Agregar soporte para sucursal de influencia en distribuciones de nota de recepción
-- para mantener trazabilidad completa desde el pedido original

-- Agregar columna sucursal_influencia_id
ALTER TABLE operaciones.nota_recepcion_item_distribucion
ADD COLUMN sucursal_influencia_id BIGINT;

-- Agregar comentario a la columna
COMMENT ON COLUMN operaciones.nota_recepcion_item_distribucion.sucursal_influencia_id IS 
'ID de la sucursal de influencia (quién necesita el producto) para mantener trazabilidad con el pedido original';

-- Agregar foreign key constraint
ALTER TABLE operaciones.nota_recepcion_item_distribucion
ADD CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_influencia
FOREIGN KEY (sucursal_influencia_id)
REFERENCES empresarial.sucursal(id);

-- Agregar comentario a la constraint
COMMENT ON CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_influencia 
ON operaciones.nota_recepcion_item_distribucion IS 
'Foreign key que relaciona la distribución con la sucursal de influencia del pedido original';

-- Crear índice para optimizar consultas por sucursal de influencia
CREATE INDEX idx_nota_recepcion_item_distribucion_sucursal_influencia 
ON operaciones.nota_recepcion_item_distribucion(sucursal_influencia_id);

-- Agregar comentario al índice
COMMENT ON INDEX operaciones.idx_nota_recepcion_item_distribucion_sucursal_influencia IS 
'Índice para optimizar consultas de distribuciones por sucursal de influencia';

-- Crear índice compuesto para optimizar consultas por sucursal de influencia y entrega
CREATE INDEX idx_nota_recepcion_item_distribucion_sucursal_influencia_entrega 
ON operaciones.nota_recepcion_item_distribucion(sucursal_influencia_id, sucursal_entrega_id);

-- Agregar comentario al índice compuesto
COMMENT ON INDEX operaciones.idx_nota_recepcion_item_distribucion_sucursal_influencia_entrega IS 
'Índice compuesto para optimizar consultas de distribuciones por sucursal de influencia y entrega'; 