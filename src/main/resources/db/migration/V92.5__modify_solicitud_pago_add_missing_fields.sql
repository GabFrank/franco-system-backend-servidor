-- V92: Modify existing solicitud_pago table to add missing fields
-- Add missing fields for solicitud pago functionality

-- V92.5: Modify solicitud_pago to add missing fields
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago') THEN
        -- Add missing columns to the existing table
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'numero_solicitud') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN numero_solicitud VARCHAR(50);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'fecha_solicitud') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN fecha_solicitud TIMESTAMP;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'fecha_pago_propuesta') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN fecha_pago_propuesta DATE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'observaciones') THEN
            ALTER TABLE operaciones.solicitud_pago ADD COLUMN observaciones TEXT;
        END IF;
        
        -- Update existing records with default values
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'numero_solicitud') THEN
            UPDATE operaciones.solicitud_pago 
            SET numero_solicitud = 'SP-' || LPAD(id::text, 6, '0'),
                fecha_solicitud = COALESCE(creado_en, CURRENT_TIMESTAMP)
            WHERE numero_solicitud IS NULL;
        END IF;
        
        -- Make numero_solicitud and fecha_solicitud NOT NULL after updating existing records
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'numero_solicitud' AND is_nullable = 'YES') THEN
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN numero_solicitud SET NOT NULL;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'fecha_solicitud' AND is_nullable = 'YES') THEN
            ALTER TABLE operaciones.solicitud_pago ALTER COLUMN fecha_solicitud SET NOT NULL;
        END IF;
    END IF;
END $$;

-- Create unique index for numero_solicitud (only if column exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'numero_solicitud') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'operaciones' AND tablename = 'solicitud_pago' AND indexname = 'uk_solicitud_pago_numero') THEN
            CREATE UNIQUE INDEX uk_solicitud_pago_numero ON operaciones.solicitud_pago(numero_solicitud);
        END IF;
    END IF;
END $$;

-- Create additional indexes for performance (only if columns exist)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'fecha_solicitud') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'operaciones' AND tablename = 'solicitud_pago' AND indexname = 'idx_solicitud_pago_fecha') THEN
            CREATE INDEX idx_solicitud_pago_fecha ON operaciones.solicitud_pago(fecha_solicitud);
        END IF;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'estado') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'operaciones' AND tablename = 'solicitud_pago' AND indexname = 'idx_solicitud_pago_estado') THEN
            CREATE INDEX idx_solicitud_pago_estado ON operaciones.solicitud_pago(estado);
        END IF;
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'solicitud_pago' AND column_name = 'proveedor_id') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'operaciones' AND tablename = 'solicitud_pago' AND indexname = 'idx_solicitud_pago_proveedor') THEN
            CREATE INDEX idx_solicitud_pago_proveedor ON operaciones.solicitud_pago(proveedor_id);
        END IF;
    END IF;
END $$;