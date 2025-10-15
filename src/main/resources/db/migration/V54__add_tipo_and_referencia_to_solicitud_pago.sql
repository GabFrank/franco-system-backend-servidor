-- Create the enumerated type for 'tipo'
CREATE TYPE operaciones.tipo_solicitud_pago AS ENUM ('COMPRA', 'GASTO', 'RRHH');

-- Add the new columns to the 'solicitud_pago' table
ALTER TABLE operaciones.solicitud_pago ADD COLUMN tipo operaciones.tipo_solicitud_pago NULL;
ALTER TABLE operaciones.solicitud_pago ADD COLUMN referencia_id int8 NULL;

-- Update existing records to set a default tipo value (can be updated later)
UPDATE operaciones.solicitud_pago SET tipo = 'GASTO' WHERE tipo IS NULL;

-- Make tipo column NOT NULL after setting defaults
ALTER TABLE operaciones.solicitud_pago ALTER COLUMN tipo SET NOT NULL; 
