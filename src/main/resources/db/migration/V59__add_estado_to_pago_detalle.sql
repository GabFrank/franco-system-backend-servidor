-- Add enum type for pago_detalle_estado if it doesn't exist
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'pago_detalle_estado') THEN
        CREATE TYPE operaciones.pago_detalle_estado AS ENUM (
            'ABIERTO',
            'PENDIENTE',
            'PAGO_PARCIAL',
            'CONCLUIDO',
            'CANCELADO'
        );
    END IF;
END $$;

-- Add estado column to pago_detalle table with default value 'ABIERTO'
ALTER TABLE operaciones.pago_detalle 
ADD COLUMN estado operaciones.pago_detalle_estado NOT NULL DEFAULT 'ABIERTO';

-- Update indices if needed
CREATE INDEX idx_pago_detalle_estado ON operaciones.pago_detalle(estado);

-- Add index on pago_id for faster lookup
CREATE INDEX IF NOT EXISTS idx_pago_detalle_pago_id ON operaciones.pago_detalle(pago_id); 