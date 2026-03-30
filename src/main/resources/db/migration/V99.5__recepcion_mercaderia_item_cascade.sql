-- V99.5: Update recepcion_mercaderia_item cascade constraint
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND constraint_name = 'fk_recepcion_item_recepcion') THEN
        ALTER TABLE operaciones.recepcion_mercaderia_item DROP CONSTRAINT fk_recepcion_item_recepcion;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND constraint_name = 'fk_recepcion_item_recepcion') THEN
            ALTER TABLE operaciones.recepcion_mercaderia_item 
            ADD CONSTRAINT fk_recepcion_item_recepcion 
            FOREIGN KEY (recepcion_mercaderia_id) 
            REFERENCES operaciones.recepcion_mercaderia(id) 
            ON DELETE CASCADE;
        END IF;
    END IF;
END $$;
