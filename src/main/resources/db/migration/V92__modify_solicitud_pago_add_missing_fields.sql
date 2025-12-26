-- V92: Modify existing solicitud_pago table to add missing fields
-- Add missing fields for solicitud pago functionality

-- Add missing columns to the existing table
ALTER TABLE operaciones.solicitud_pago 
ADD COLUMN numero_solicitud VARCHAR(50),
ADD COLUMN fecha_solicitud TIMESTAMP,
ADD COLUMN fecha_pago_propuesta DATE,
ADD COLUMN observaciones TEXT;

-- Update existing records with default values
UPDATE operaciones.solicitud_pago 
SET numero_solicitud = 'SP-' || LPAD(id::text, 6, '0'),
    fecha_solicitud = COALESCE(creado_en, CURRENT_TIMESTAMP)
WHERE numero_solicitud IS NULL;

-- Make numero_solicitud and fecha_solicitud NOT NULL after updating existing records
ALTER TABLE operaciones.solicitud_pago 
ALTER COLUMN numero_solicitud SET NOT NULL,
ALTER COLUMN fecha_solicitud SET NOT NULL;

-- Create unique index for numero_solicitud
CREATE UNIQUE INDEX uk_solicitud_pago_numero ON operaciones.solicitud_pago(numero_solicitud);

-- Create additional indexes for performance
CREATE INDEX idx_solicitud_pago_fecha ON operaciones.solicitud_pago(fecha_solicitud);
CREATE INDEX idx_solicitud_pago_estado ON operaciones.solicitud_pago(estado);
CREATE INDEX idx_solicitud_pago_proveedor ON operaciones.solicitud_pago(proveedor_id);