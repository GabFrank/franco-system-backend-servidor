-- V95.5: Clean up obsolete fields and tables
-- Since functionality is not in production, we can safely remove obsolete structure
-- This migration comes from the compras branch

-- Drop the existing table that links to recepcion_mercaderia
DROP TABLE IF EXISTS operaciones.solicitud_pago_recepcion CASCADE;

-- Remove obsolete columns from solicitud_pago table (only if table exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago') THEN
        -- Remove obsolete columns
        ALTER TABLE operaciones.solicitud_pago DROP COLUMN IF EXISTS tipo;
        ALTER TABLE operaciones.solicitud_pago DROP COLUMN IF EXISTS referencia_id;
        
        -- Make required fields NOT NULL now that we don't need backward compatibility
        -- Only if columns exist and are currently nullable
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'proveedor_id' AND is_nullable = 'YES') THEN
            -- First, set default values for any NULL records if needed
            UPDATE operaciones.solicitud_pago SET proveedor_id = (SELECT id FROM personas.proveedor LIMIT 1) WHERE proveedor_id IS NULL;
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN proveedor_id SET NOT NULL;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'moneda_id' AND is_nullable = 'YES') THEN
            -- First, set default values for any NULL records if needed
            UPDATE operaciones.solicitud_pago SET moneda_id = (SELECT id FROM financiero.moneda LIMIT 1) WHERE moneda_id IS NULL;
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN moneda_id SET NOT NULL;
        END IF;
        
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'monto_total' AND is_nullable = 'YES') THEN
            -- First, set default values for any NULL records if needed
            UPDATE operaciones.solicitud_pago SET monto_total = 0 WHERE monto_total IS NULL;
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN monto_total SET NOT NULL;
        END IF;
    ELSE
        RAISE NOTICE 'Tabla solicitud_pago no existe, omitiendo modificaciones';
    END IF;
END $$;

-- Drop the obsolete enum type
DROP TYPE IF EXISTS tipo_solicitud_pago CASCADE;

-- Add comment for audit trail
-- Cleaned up old approach that linked solicitud_pago with recepcion_mercaderia (physical reception)
-- New approach links solicitud_pago with nota_recepcion (documental reception)
-- This aligns with the business requirement to create payment requests based on 
-- completed documentation rather than physical reception