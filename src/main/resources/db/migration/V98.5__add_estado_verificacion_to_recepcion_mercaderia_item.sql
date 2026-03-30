-- V98: Añadir campo estado_verificacion a recepcion_mercaderia_item
-- Este campo permitirá rastrear el estado de verificación de cada ítem de recepción

-- V98.5: Add estado_verificacion to recepcion_mercaderia_item
-- This migration comes from the compras branch

-- Crear el tipo enum para estado_verificacion
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_verificacion') THEN
        CREATE TYPE operaciones.estado_verificacion AS ENUM (
            'PENDIENTE',
            'VERIFICADO', 
            'VERIFICADO_CON_DIFERENCIA',
            'RECHAZADO'
        );
    END IF;
END $$;

-- Añadir la columna estado_verificacion a la tabla recepcion_mercaderia_item
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'estado_verificacion') THEN
            ALTER TABLE operaciones.recepcion_mercaderia_item 
            ADD COLUMN estado_verificacion operaciones.estado_verificacion NOT NULL DEFAULT 'PENDIENTE';
        END IF;
    END IF;
END $$;

-- Crear índice para mejorar el rendimiento de consultas por estado
CREATE INDEX IF NOT EXISTS idx_recepcion_mercaderia_item_estado_verificacion 
ON operaciones.recepcion_mercaderia_item(estado_verificacion);

-- Comentario en la columna
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.estado_verificacion IS 
'Estado de verificación del ítem: PENDIENTE, VERIFICADO, VERIFICADO_CON_DIFERENCIA, RECHAZADO';

-- Comentario en el tipo enum
COMMENT ON TYPE operaciones.estado_verificacion IS 
'Enum que representa el estado de verificación de un ítem de recepción de mercadería';
