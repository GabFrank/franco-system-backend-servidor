-- ===============================================
-- V76: Agregar sucursal de influencia a pedido_item_distribucion
-- ===============================================

-- Agregar nueva columna sucursal_influencia_id
ALTER TABLE operaciones.pedido_item_distribucion
ADD COLUMN sucursal_influencia_id BIGINT;

-- Agregar foreign key constraint
ALTER TABLE operaciones.pedido_item_distribucion
ADD CONSTRAINT fk_distribucion_sucursal_influencia 
FOREIGN KEY (sucursal_influencia_id) REFERENCES empresarial.sucursal(id);

-- Crear índice para la nueva columna
CREATE INDEX idx_distribucion_sucursal_influencia 
ON operaciones.pedido_item_distribucion(sucursal_influencia_id);

-- Actualizar registros existentes: por defecto, sucursal de influencia = sucursal de entrega
-- Solo actualizar registros que no tengan valor null
UPDATE operaciones.pedido_item_distribucion 
SET sucursal_influencia_id = sucursal_entrega_id 
WHERE sucursal_influencia_id IS NULL;

-- Hacer la columna NOT NULL después de llenar los datos
ALTER TABLE operaciones.pedido_item_distribucion
ALTER COLUMN sucursal_influencia_id SET NOT NULL;

-- Actualizar constraint único para incluir sucursal de influencia
-- Primero eliminar el constraint existente
ALTER TABLE operaciones.pedido_item_distribucion
DROP CONSTRAINT IF EXISTS uk_distribucion_item_sucursal;

-- Agregar nuevo constraint único que incluya sucursal de influencia
ALTER TABLE operaciones.pedido_item_distribucion
ADD CONSTRAINT uk_distribucion_item_sucursales 
UNIQUE (pedido_item_id, sucursal_influencia_id, sucursal_entrega_id);

-- Comentarios para documentación
COMMENT ON COLUMN operaciones.pedido_item_distribucion.sucursal_influencia_id IS 
'Sucursal para la cual se está realizando la compra (quien realmente necesita el producto)';

COMMENT ON COLUMN operaciones.pedido_item_distribucion.sucursal_entrega_id IS 
'Sucursal donde físicamente se entregará el producto'; 