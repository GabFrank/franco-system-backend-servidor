-- V72.5: Update foreign key constraints on pedido_item
-- This migration comes from the compras branch

DO $$
BEGIN
    -- pedido_item_pedido_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_pedido_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_pedido_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_pedido_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_pedido_fk FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON DELETE CASCADE ON UPDATE CASCADE;
    END IF;
    
    -- pedido_item_nota_recepcion_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_nota_recepcion_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_nota_recepcion_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_nota_recepcion_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_nota_recepcion_fk FOREIGN KEY (nota_recepcion_id) REFERENCES operaciones.nota_recepcion(id) ON DELETE SET NULL ON UPDATE SET NULL;
    END IF;
    
    -- pedido_item_autorizado_por_recepcion_nota_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_autorizado_por_recepcion_nota_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_autorizado_por_recepcion_nota_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_autorizado_por_recepcion_nota_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_autorizado_por_recepcion_nota_fk FOREIGN KEY (autorizado_por_recepcion_nota_id) REFERENCES personas.usuario(id) ON UPDATE SET NULL;
    END IF;
    
    -- pedido_item_autorizado_por_recepcion_producto_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_autorizado_por_recepcion_producto_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_autorizado_por_recepcion_producto_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_autorizado_por_recepcion_producto_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_autorizado_por_recepcion_producto_fk FOREIGN KEY (autorizado_por_recepcion_producto_id) REFERENCES personas.usuario(id) ON UPDATE SET NULL;
    END IF;
    
    -- pedido_item_presentacion_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_presentacion_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_presentacion_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_presentacion_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_presentacion_fk FOREIGN KEY (presentacion_creacion_id) REFERENCES productos.presentacion(id) ON UPDATE RESTRICT;
    END IF;
    
    -- pedido_item_presentacion_recepcion_nota_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_presentacion_recepcion_nota_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_presentacion_recepcion_nota_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_presentacion_recepcion_nota_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_presentacion_recepcion_nota_fk FOREIGN KEY (presentacion_recepcion_nota_id) REFERENCES productos.presentacion(id) ON UPDATE RESTRICT;
    END IF;
    
    -- pedido_item_presentacion_recepcion_producto_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_presentacion_recepcion_producto_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_presentacion_recepcion_producto_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_presentacion_recepcion_producto_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_presentacion_recepcion_producto_fk FOREIGN KEY (presentacion_recepcion_producto_id) REFERENCES productos.presentacion(id) ON UPDATE RESTRICT;
    END IF;
    
    -- pedido_item_producto_id_fkey
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_producto_id_fkey') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_producto_id_fkey;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_producto_id_fkey') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id) ON UPDATE RESTRICT;
    END IF;
    
    -- pedido_item_usuario_recepcion_nota_id_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_usuario_recepcion_nota_id_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_usuario_recepcion_nota_id_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_usuario_recepcion_nota_id_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_usuario_recepcion_nota_id_fk FOREIGN KEY (usuario_recepcion_nota_id) REFERENCES personas.usuario(id) ON UPDATE SET NULL;
    END IF;
    
    -- pedido_item_usuario_recepcion_producto_id_recepcion_producto_fk
    IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_usuario_recepcion_producto_id_recepcion_producto_fk') THEN
        ALTER TABLE operaciones.pedido_item DROP CONSTRAINT pedido_item_usuario_recepcion_producto_id_recepcion_producto_fk;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido_item' AND constraint_name = 'pedido_item_usuario_recepcion_producto_id_recepcion_producto_fk') THEN
        ALTER TABLE operaciones.pedido_item ADD CONSTRAINT pedido_item_usuario_recepcion_producto_id_recepcion_producto_fk FOREIGN KEY (usuario_recepcion_producto_id) REFERENCES personas.usuario(id) ON UPDATE SET NULL;
    END IF;
END $$;
