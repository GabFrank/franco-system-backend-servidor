-- Add unique constraint to solicitud_pago_id in the pago table
-- This prevents having multiple pagos for a single solicitud_pago
ALTER TABLE operaciones.pago ADD CONSTRAINT unique_solicitud_pago_id UNIQUE (solicitud_pago_id); 