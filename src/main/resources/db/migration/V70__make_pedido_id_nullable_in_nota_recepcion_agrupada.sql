-- V70: Make pedido_id nullable in nota_recepcion_agrupada
-- This allows creating NotaRecepcionAgrupada records without immediately linking to a pedido
-- The relationship can be established later when needed

-- Remove the NOT NULL constraint from pedido_id column
ALTER TABLE operaciones.nota_recepcion_agrupada 
ALTER COLUMN pedido_id DROP NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN operaciones.nota_recepcion_agrupada.pedido_id IS 'Foreign key to pedido table. Nullable to allow creating groups without immediate pedido relationship'; 