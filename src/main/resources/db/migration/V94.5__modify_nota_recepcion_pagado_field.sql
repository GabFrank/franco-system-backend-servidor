-- V94.5: Modify pagado field in nota_recepcion
-- Change default value from false to NULL for better payment tracking
-- This migration comes from the compras branch

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion' AND column_name = 'pagado') THEN
        -- Change default value of pagado field
        ALTER TABLE operaciones.nota_recepcion ALTER COLUMN pagado SET DEFAULT NULL;
        
        -- Update existing records: change false to NULL (keep true values)
        UPDATE operaciones.nota_recepcion 
        SET pagado = NULL 
        WHERE pagado = false;
    ELSE
        RAISE NOTICE 'Columna pagado no existe en nota_recepcion, omitiendo migración';
    END IF;
END $$;

-- Add comment to clarify the field usage
COMMENT ON COLUMN operaciones.nota_recepcion.pagado IS 'NULL = not processed for payment, TRUE = paid, FALSE = not applicable for payment';