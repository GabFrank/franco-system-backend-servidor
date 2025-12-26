-- V75.5: Add purchasing logic fields
-- This migration comes from the compras branch

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido_item' AND column_name = 'es_bonificacion') THEN
        ALTER TABLE operaciones.pedido_item ADD COLUMN es_bonificacion BOOLEAN DEFAULT FALSE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'nota_recepcion_item_estado') THEN
        CREATE TYPE operaciones.nota_recepcion_item_estado AS ENUM (
            'PENDIENTE_CONCILIACION',
            'CONCILIADO',
            'RECHAZADO',
            'DISCREPANCIA'
        );
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item' AND column_name = 'estado') THEN
            ALTER TABLE operaciones.nota_recepcion_item ADD COLUMN estado operaciones.nota_recepcion_item_estado DEFAULT 'PENDIENTE_CONCILIACION';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_item' AND column_name = 'motivo_rechazo') THEN
            ALTER TABLE operaciones.nota_recepcion_item ADD COLUMN motivo_rechazo TEXT;
        END IF;
    END IF;
END $$; 