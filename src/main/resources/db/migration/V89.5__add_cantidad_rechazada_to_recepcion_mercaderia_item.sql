-- V89.5: Add cantidad_rechazada to recepcion_mercaderia_item
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'recepcion_mercaderia_item' AND column_name = 'cantidad_rechazada') THEN
            ALTER TABLE operaciones.recepcion_mercaderia_item ADD COLUMN cantidad_rechazada NUMERIC DEFAULT 0;
        END IF;
    END IF;
END $$;
 
-- Comentario sobre la migración
COMMENT ON COLUMN operaciones.recepcion_mercaderia_item.cantidad_rechazada IS 'Cantidad rechazada del ítem durante la recepción física'; 