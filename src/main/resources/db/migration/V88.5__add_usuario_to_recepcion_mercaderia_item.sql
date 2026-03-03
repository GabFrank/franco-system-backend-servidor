-- V88.5: Add usuario_id to recepcion_mercaderia_item
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'usuario_id') THEN
            ALTER TABLE operaciones.recepcion_mercaderia_item ADD COLUMN usuario_id BIGINT NOT NULL DEFAULT 1;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'usuario_id') THEN
            IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND constraint_name = 'fk_recepcion_mercaderia_item_usuario') THEN
                ALTER TABLE operaciones.recepcion_mercaderia_item 
                ADD CONSTRAINT fk_recepcion_mercaderia_item_usuario 
                FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);
            END IF;
        END IF;
    END IF;
END $$;

-- Crear índice para mejorar performance
CREATE INDEX IF NOT EXISTS idx_recepcion_mercaderia_item_usuario_id 
ON operaciones.recepcion_mercaderia_item(usuario_id);

-- Comentario sobre la migración
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.usuario_id IS 'Usuario que realizó la recepción del ítem'; 