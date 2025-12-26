-- V90.5: Add pedido_item_distribucion_id to recepcion_mercaderia_item
-- This migration comes from the compras branch
-- NOTE: This column may be removed by V91.5, so this migration is idempotent

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'pedido_item_distribucion_id') THEN
            ALTER TABLE operaciones.recepcion_mercaderia_item ADD COLUMN pedido_item_distribucion_id BIGINT;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'pedido_item_distribucion_id') THEN
            IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND constraint_name = 'fk_recepcion_mercaderia_item_pedido_item_distribucion') THEN
                ALTER TABLE operaciones.recepcion_mercaderia_item 
                ADD CONSTRAINT fk_recepcion_mercaderia_item_pedido_item_distribucion 
                FOREIGN KEY (pedido_item_distribucion_id) REFERENCES operaciones.pedido_item_distribucion(id);
            END IF;
        END IF;
    END IF;
END $$;

-- Crear índice para mejorar performance
CREATE INDEX IF NOT EXISTS idx_recepcion_mercaderia_item_pedido_item_distribucion_id 
ON operaciones.recepcion_mercaderia_item(pedido_item_distribucion_id);

-- Comentario sobre la migración
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.pedido_item_distribucion_id IS 'Referencia a la distribución del pedido item asociada a esta recepción'; 