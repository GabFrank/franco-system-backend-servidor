-- V70: Make pedido_id nullable in nota_recepcion_agrupada
-- This allows creating NotaRecepcionAgrupada records without immediately linking to a pedido
-- The relationship can be established later when needed

-- Remove the NOT NULL constraint from pedido_id column
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_agrupada') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion_agrupada' AND column_name = 'pedido_id' AND is_nullable = 'NO') THEN
            ALTER TABLE operaciones.nota_recepcion_agrupada 
            ALTER COLUMN pedido_id DROP NOT NULL;
        END IF;
    ELSE
        RAISE NOTICE 'Tabla nota_recepcion_agrupada no existe, omitiendo migración';
    END IF;
END $$;

-- Add comment for documentation
COMMENT ON COLUMN operaciones.nota_recepcion_agrupada.pedido_id IS 'Foreign key to pedido table. Nullable to allow creating groups without immediate pedido relationship'; 