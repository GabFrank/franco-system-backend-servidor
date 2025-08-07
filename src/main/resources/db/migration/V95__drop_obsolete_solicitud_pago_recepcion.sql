-- V95: Clean up obsolete fields and tables
-- Since functionality is not in production, we can safely remove obsolete structure

-- Drop the existing table that links to recepcion_mercaderia
DROP TABLE IF EXISTS operaciones.solicitud_pago_recepcion CASCADE;

-- Remove obsolete columns from solicitud_pago table
ALTER TABLE operaciones.solicitud_pago 
DROP COLUMN IF EXISTS tipo,
DROP COLUMN IF EXISTS referencia_id;

-- Make required fields NOT NULL now that we don't need backward compatibility
ALTER TABLE operaciones.solicitud_pago 
ALTER COLUMN proveedor_id SET NOT NULL,
ALTER COLUMN moneda_id SET NOT NULL,
ALTER COLUMN monto_total SET NOT NULL;

-- Drop the obsolete enum type
DROP TYPE IF EXISTS tipo_solicitud_pago CASCADE;

-- Add comment for audit trail
-- Cleaned up old approach that linked solicitud_pago with recepcion_mercaderia (physical reception)
-- New approach links solicitud_pago with nota_recepcion (documental reception)
-- This aligns with the business requirement to create payment requests based on 
-- completed documentation rather than physical reception