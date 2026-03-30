-- V79.5: Update foreign key constraints to use CASCADE
-- This migration comes from the compras branch

DO $$
BEGIN
    -- pedido_item_distribucion constraints
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'fk_distribucion_pedido_item') THEN
        ALTER TABLE operaciones.pedido_item_distribucion DROP CONSTRAINT fk_distribucion_pedido_item;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'fk_distribucion_pedido_item') THEN
        ALTER TABLE operaciones.pedido_item_distribucion ADD CONSTRAINT fk_distribucion_pedido_item FOREIGN KEY (pedido_item_id) REFERENCES operaciones.pedido_item(id) ON DELETE CASCADE;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'fk_distribucion_sucursal') THEN
        ALTER TABLE operaciones.pedido_item_distribucion DROP CONSTRAINT fk_distribucion_sucursal;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'fk_distribucion_sucursal') THEN
        ALTER TABLE operaciones.pedido_item_distribucion ADD CONSTRAINT fk_distribucion_sucursal FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'fk_distribucion_sucursal_influencia') THEN
        ALTER TABLE operaciones.pedido_item_distribucion DROP CONSTRAINT fk_distribucion_sucursal_influencia;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item_distribucion' AND constraint_name = 'fk_distribucion_sucursal_influencia') THEN
        ALTER TABLE operaciones.pedido_item_distribucion ADD CONSTRAINT fk_distribucion_sucursal_influencia FOREIGN KEY (sucursal_influencia_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;
    END IF;
    
    -- nota_recepcion_item_distribucion constraints
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND constraint_name = 'fk_nota_recepcion_item_distribucion_nota_recepcion_item') THEN
        ALTER TABLE operaciones.nota_recepcion_item_distribucion DROP CONSTRAINT fk_nota_recepcion_item_distribucion_nota_recepcion_item;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND constraint_name = 'fk_nota_recepcion_item_distribucion_nota_recepcion_item') THEN
        ALTER TABLE operaciones.nota_recepcion_item_distribucion ADD CONSTRAINT fk_nota_recepcion_item_distribucion_nota_recepcion_item FOREIGN KEY (nota_recepcion_item_id) REFERENCES operaciones.nota_recepcion_item(id) ON DELETE CASCADE;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND constraint_name = 'fk_nota_recepcion_item_distribucion_sucursal_entrega') THEN
        ALTER TABLE operaciones.nota_recepcion_item_distribucion DROP CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_entrega;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND constraint_name = 'fk_nota_recepcion_item_distribucion_sucursal_entrega') THEN
        ALTER TABLE operaciones.nota_recepcion_item_distribucion ADD CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_entrega FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND constraint_name = 'fk_nota_recepcion_item_distribucion_usuario') THEN
        ALTER TABLE operaciones.nota_recepcion_item_distribucion DROP CONSTRAINT fk_nota_recepcion_item_distribucion_usuario;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'nota_recepcion_item_distribucion' AND constraint_name = 'fk_nota_recepcion_item_distribucion_usuario') THEN
        ALTER TABLE operaciones.nota_recepcion_item_distribucion ADD CONSTRAINT fk_nota_recepcion_item_distribucion_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;
    END IF;
END $$;
