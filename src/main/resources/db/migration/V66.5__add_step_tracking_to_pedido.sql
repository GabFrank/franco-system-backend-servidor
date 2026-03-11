-- Migration V66.5: Add step tracking columns to pedido table
-- Following the same pattern as pedido_item with step-specific columns
-- This migration comes from the compras branch

-- Step: Creacion (Datos del pedido)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'usuario_creacion_id') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN usuario_creacion_id int8 NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_inicio_creacion') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_creacion timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_fin_creacion') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_creacion timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'progreso_creacion') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN progreso_creacion int4 DEFAULT 0;
    END IF;
END $$;

-- Step: Recepcion Nota
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'usuario_recepcion_nota_id') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN usuario_recepcion_nota_id int8 NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_inicio_recepcion_nota') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_recepcion_nota timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_fin_recepcion_nota') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_recepcion_nota timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'progreso_recepcion_nota') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN progreso_recepcion_nota int4 DEFAULT 0;
    END IF;
END $$;

-- Step: Recepcion Mercaderia
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'usuario_recepcion_mercaderia_id') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN usuario_recepcion_mercaderia_id int8 NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_inicio_recepcion_mercaderia') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_recepcion_mercaderia timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_fin_recepcion_mercaderia') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_recepcion_mercaderia timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'progreso_recepcion_mercaderia') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN progreso_recepcion_mercaderia int4 DEFAULT 0;
    END IF;
END $$;

-- Step: Solicitud Pago
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'usuario_solicitud_pago_id') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN usuario_solicitud_pago_id int8 NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_inicio_solicitud_pago') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_inicio_solicitud_pago timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_fin_solicitud_pago') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN fecha_fin_solicitud_pago timestamp NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'progreso_solicitud_pago') THEN
        ALTER TABLE operaciones.pedido ADD COLUMN progreso_solicitud_pago int4 DEFAULT 0;
    END IF;
END $$;

-- Add foreign key constraints
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido' AND constraint_name = 'pedido_usuario_creacion_fk') THEN
        ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_creacion_fk 
            FOREIGN KEY (usuario_creacion_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido' AND constraint_name = 'pedido_usuario_recepcion_nota_fk') THEN
        ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_recepcion_nota_fk 
            FOREIGN KEY (usuario_recepcion_nota_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido' AND constraint_name = 'pedido_usuario_recepcion_mercaderia_fk') THEN
        ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_recepcion_mercaderia_fk 
            FOREIGN KEY (usuario_recepcion_mercaderia_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = 'operaciones' AND table_name = 'pedido' AND constraint_name = 'pedido_usuario_solicitud_pago_fk') THEN
        ALTER TABLE operaciones.pedido ADD CONSTRAINT pedido_usuario_solicitud_pago_fk 
            FOREIGN KEY (usuario_solicitud_pago_id) REFERENCES personas.usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;
END $$;

-- Add comments for documentation
COMMENT ON COLUMN operaciones.pedido.usuario_creacion_id IS 'Usuario responsible for datos del pedido step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_creacion IS 'When user started working on datos del pedido step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_creacion IS 'When datos del pedido step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_creacion IS 'Progress percentage (0-100) for datos del pedido step';

COMMENT ON COLUMN operaciones.pedido.usuario_recepcion_nota_id IS 'Usuario responsible for recepcion nota step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_recepcion_nota IS 'When user started working on recepcion nota step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_recepcion_nota IS 'When recepcion nota step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_recepcion_nota IS 'Progress percentage (0-100) for recepcion nota step';

COMMENT ON COLUMN operaciones.pedido.usuario_recepcion_mercaderia_id IS 'Usuario responsible for recepcion mercaderia step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_recepcion_mercaderia IS 'When user started working on recepcion mercaderia step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_recepcion_mercaderia IS 'When recepcion mercaderia step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_recepcion_mercaderia IS 'Progress percentage (0-100) for recepcion mercaderia step';

COMMENT ON COLUMN operaciones.pedido.usuario_solicitud_pago_id IS 'Usuario responsible for solicitud pago step';
COMMENT ON COLUMN operaciones.pedido.fecha_inicio_solicitud_pago IS 'When user started working on solicitud pago step';
COMMENT ON COLUMN operaciones.pedido.fecha_fin_solicitud_pago IS 'When solicitud pago step was completed';
COMMENT ON COLUMN operaciones.pedido.progreso_solicitud_pago IS 'Progress percentage (0-100) for solicitud pago step'; 