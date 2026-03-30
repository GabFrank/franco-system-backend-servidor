-- V97.5: Add activo field to sucursal
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'empresarial' AND table_name = 'sucursal') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'empresarial' AND table_name = 'sucursal' AND column_name = 'activo') THEN
            ALTER TABLE empresarial.sucursal ADD COLUMN activo BOOLEAN DEFAULT TRUE;
            
            -- Actualizar registros existentes para que estén activos por defecto
            UPDATE empresarial.sucursal SET activo = TRUE WHERE activo IS NULL;
            
            -- Hacer el campo NOT NULL después de la actualización
            ALTER TABLE empresarial.sucursal ALTER COLUMN activo SET NOT NULL;
        END IF;
    END IF;
END $$;

-- Agregar comentario al campo
COMMENT ON COLUMN empresarial.sucursal.activo IS 'Indica si la sucursal está activa o no'; 