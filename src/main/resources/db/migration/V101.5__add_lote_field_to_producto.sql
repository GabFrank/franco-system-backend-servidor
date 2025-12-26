-- V101.5: Add lote field to producto
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'productos' AND table_name = 'producto') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'productos' AND table_name = 'producto' AND column_name = 'lote') THEN
            ALTER TABLE productos.producto ADD COLUMN lote BOOLEAN DEFAULT FALSE;
        END IF;
    END IF;
END $$;

-- Add comment to document the field
COMMENT ON COLUMN productos.producto.lote IS 'Indica si el producto requiere control de lotes';
