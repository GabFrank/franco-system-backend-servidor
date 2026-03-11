-- V103.5: Add activo and motivo_desvinculacion fields to producto_proveedor
-- This migration adds fields to track product-supplier relationship status

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'productos' AND table_name = 'producto_proveedor') THEN
        -- Add activo field (default true for existing records)
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'productos' AND table_name = 'producto_proveedor' AND column_name = 'activo') THEN
            ALTER TABLE productos.producto_proveedor ADD COLUMN activo BOOLEAN DEFAULT TRUE;
        END IF;
        
        -- Add motivo_desvinculacion field
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'productos' AND table_name = 'producto_proveedor' AND column_name = 'motivo_desvinculacion') THEN
            ALTER TABLE productos.producto_proveedor ADD COLUMN motivo_desvinculacion VARCHAR(255);
        END IF;
        
        -- Set all existing records to activo = true
        UPDATE productos.producto_proveedor SET activo = TRUE WHERE activo IS NULL;
    END IF;
END $$;

-- Add comments to document the fields
COMMENT ON COLUMN productos.producto_proveedor.activo IS 'Indica si el producto está activo en el portfolio del proveedor';
COMMENT ON COLUMN productos.producto_proveedor.motivo_desvinculacion IS 'Motivo por el cual el producto fue desvinculado del proveedor';
