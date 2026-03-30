-- ===============================================
-- V76: Agregar sucursal de influencia a pedido_item_distribucion
-- ===============================================

-- V76.5: Add sucursal_influencia_id to pedido_item_distribucion
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'pedido_item_distribucion') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND column_name = 'sucursal_influencia_id') THEN
            ALTER TABLE operaciones.pedido_item_distribucion ADD COLUMN sucursal_influencia_id BIGINT;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND column_name = 'sucursal_influencia_id') THEN
            IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'fk_distribucion_sucursal_influencia') THEN
                ALTER TABLE operaciones.pedido_item_distribucion
                ADD CONSTRAINT fk_distribucion_sucursal_influencia 
                FOREIGN KEY (sucursal_influencia_id) REFERENCES empresarial.sucursal(id);
            END IF;
            
            -- Actualizar registros existentes: por defecto, sucursal de influencia = sucursal de entrega
            UPDATE operaciones.pedido_item_distribucion 
            SET sucursal_influencia_id = sucursal_entrega_id 
            WHERE sucursal_influencia_id IS NULL;
            
            -- Hacer la columna NOT NULL después de llenar los datos (solo si es nullable)
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND column_name = 'sucursal_influencia_id' AND is_nullable = 'YES') THEN
                ALTER TABLE operaciones.pedido_item_distribucion ALTER COLUMN sucursal_influencia_id SET NOT NULL;
            END IF;
        END IF;
    END IF;
END $$;

-- Crear índice para la nueva columna
CREATE INDEX IF NOT EXISTS idx_distribucion_sucursal_influencia 
ON operaciones.pedido_item_distribucion(sucursal_influencia_id);

-- Actualizar constraint único para incluir sucursal de influencia
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'uk_distribucion_item_sucursal') THEN
        ALTER TABLE operaciones.pedido_item_distribucion DROP CONSTRAINT uk_distribucion_item_sucursal;
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'uk_distribucion_item_sucursales') THEN
        ALTER TABLE operaciones.pedido_item_distribucion
        ADD CONSTRAINT uk_distribucion_item_sucursales 
        UNIQUE (pedido_item_id, sucursal_influencia_id, sucursal_entrega_id);
    END IF;
END $$;

-- Comentarios para documentación
COMMENT ON COLUMN operaciones.pedido_item_distribucion.sucursal_influencia_id IS 
'Sucursal para la cual se está realizando la compra (quien realmente necesita el producto)';

COMMENT ON COLUMN operaciones.pedido_item_distribucion.sucursal_entrega_id IS 
'Sucursal donde físicamente se entregará el producto'; 