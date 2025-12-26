-- V87.5: Create motivo_rechazo_fisico enum
-- This migration comes from the compras branch

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'motivo_rechazo_fisico') THEN
        CREATE TYPE operaciones.motivo_rechazo_fisico AS ENUM (
            'PRODUCTO_DANADO',
            'PRODUCTO_VENCIDO', 
            'CANTIDAD_INCORRECTA',
            'PRODUCTO_DIFERENTE',
            'EMBALAJE_DANADO',
            'OTRO'
        );
        COMMENT ON TYPE operaciones.motivo_rechazo_fisico IS 'Motivos de rechazo físico de mercadería durante la recepción';
    END IF;
END $$;

-- Agregar la columna motivo_rechazo a recepcion_mercaderia_item
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'motivo_rechazo') THEN
            ALTER TABLE operaciones.recepcion_mercaderia_item 
            ADD COLUMN motivo_rechazo operaciones.motivo_rechazo_fisico;
        END IF;
    END IF;
END $$;

-- Agregar comentario a la columna
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.motivo_rechazo IS 'Motivo del rechazo físico del ítem'; 