-- V94: Modify pagado field in nota_recepcion
-- Change default value from false to NULL for better payment tracking

-- Change default value of pagado field
ALTER TABLE operaciones.nota_recepcion 
ALTER COLUMN pagado SET DEFAULT NULL;

-- Update existing records: change false to NULL (keep true values)
UPDATE operaciones.nota_recepcion 
SET pagado = NULL 
WHERE pagado = false;

-- Add comment to clarify the field usage
COMMENT ON COLUMN operaciones.nota_recepcion.pagado IS 'NULL = not processed for payment, TRUE = paid, FALSE = not applicable for payment';