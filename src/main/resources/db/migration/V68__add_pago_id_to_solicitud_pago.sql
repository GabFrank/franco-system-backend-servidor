-- Add pago_id to solicitud_pago table for simple many-to-one relationship
-- This allows multiple solicitudes to be grouped under one pago

-- Remove the old unique constraint from pago table (if exists)
ALTER TABLE operaciones.pago DROP CONSTRAINT IF EXISTS unique_solicitud_pago_id;

-- Add pago_id column to solicitud_pago table
ALTER TABLE operaciones.solicitud_pago ADD COLUMN IF NOT EXISTS pago_id int8 NULL;

-- Add foreign key constraint (PostgreSQL doesn't support IF NOT EXISTS for constraints)
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'solicitud_pago_pago_fk' 
        AND table_name = 'solicitud_pago' 
        AND table_schema = 'operaciones'
    ) THEN
        ALTER TABLE operaciones.solicitud_pago ADD CONSTRAINT solicitud_pago_pago_fk 
            FOREIGN KEY (pago_id) REFERENCES operaciones.pago(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_solicitud_pago_pago_id ON operaciones.solicitud_pago(pago_id); 