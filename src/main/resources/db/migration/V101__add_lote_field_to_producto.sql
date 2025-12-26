-- Add lote field to producto table
ALTER TABLE productos.producto ADD COLUMN lote BOOLEAN DEFAULT FALSE;

-- Add comment to document the field
COMMENT ON COLUMN productos.producto.lote IS 'Indica si el producto requiere control de lotes';
