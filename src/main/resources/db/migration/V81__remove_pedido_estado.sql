-- ===============================================
-- MIGRACIÓN: ELIMINACIÓN COMPLETA DE PEDIDOESTADO
-- ===============================================
-- Esta migración elimina completamente el campo estado de la tabla pedido
-- ya que ahora usamos ProcesoEtapa como sistema principal de gestión de estados

-- Step 1: Drop the estado column from pedido table
ALTER TABLE operaciones.pedido DROP COLUMN IF EXISTS estado;

-- Step 2: Drop the pedido_estado enum type (no longer needed)
DROP TYPE IF EXISTS operaciones.pedido_estado;

-- Step 3: Remove any indexes that might reference the estado column
-- (if they exist, they will be dropped automatically with the column)

-- Comment explaining the removal:
-- The PedidoEstado enum and estado column have been completely removed from the pedido table.
-- All state management is now handled through the ProcesoEtapa system which provides
-- granular tracking of each workflow stage (CREACION, RECEPCION_NOTA, RECEPCION_MERCADERIA, SOLICITUD_PAGO).
-- This eliminates confusion and provides better audit trails for the pedido workflow. 