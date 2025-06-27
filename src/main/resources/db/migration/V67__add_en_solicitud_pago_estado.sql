-- Refactor PedidoEstado enum to remove unused states and add EN_SOLICITUD_PAGO
-- This migration cleans up the enum to match the actual stepper workflow

-- Step 1: Create new enum type with only the values we actually use
CREATE TYPE operaciones.pedido_estado_new AS ENUM (
    'ABIERTO',
    'ACTIVO', 
    'EN_RECEPCION_NOTA',
    'EN_RECEPCION_MERCADERIA',
    'EN_SOLICITUD_PAGO',
    'CONCLUIDO',
    'CANCELADO'
);

-- Step 2: Update any existing pedidos that might be in CONCLUIDO state but should be in EN_SOLICITUD_PAGO
-- based on whether they have completed the solicitud pago step
UPDATE operaciones.pedido 
SET estado = 'EN_SOLICITUD_PAGO'::operaciones.pedido_estado
WHERE estado = 'CONCLUIDO'::operaciones.pedido_estado
  AND fecha_fin_recepcion_mercaderia IS NOT NULL 
  AND fecha_inicio_solicitud_pago IS NULL;

-- Step 3: Convert existing estados to new enum (this will fail if any invalid states exist)
-- Map any unused states to appropriate new states
UPDATE operaciones.pedido 
SET estado = CASE 
    WHEN estado = 'MODIFICADO'::operaciones.pedido_estado THEN 'ACTIVO'::operaciones.pedido_estado
    WHEN estado = 'REPROGRAMADO'::operaciones.pedido_estado THEN 'ACTIVO'::operaciones.pedido_estado
    WHEN estado = 'EN_VERIFICACION'::operaciones.pedido_estado THEN 'EN_RECEPCION_MERCADERIA'::operaciones.pedido_estado
    WHEN estado = 'EN_VERIFICACION_SOLICITUD_AUTORIZACION'::operaciones.pedido_estado THEN 'EN_RECEPCION_MERCADERIA'::operaciones.pedido_estado
    WHEN estado = 'VERFICADO_SIN_MODIFICACION'::operaciones.pedido_estado THEN 'EN_SOLICITUD_PAGO'::operaciones.pedido_estado
    WHEN estado = 'VERFICADO_CON_MODIFICACION'::operaciones.pedido_estado THEN 'EN_SOLICITUD_PAGO'::operaciones.pedido_estado
    ELSE estado
END
WHERE estado::text IN ('MODIFICADO', 'REPROGRAMADO', 'EN_VERIFICACION', 'EN_VERIFICACION_SOLICITUD_AUTORIZACION', 'VERFICADO_SIN_MODIFICACION', 'VERFICADO_CON_MODIFICACION');

-- Step 4: Change column type to new enum
ALTER TABLE operaciones.pedido 
ALTER COLUMN estado TYPE operaciones.pedido_estado_new 
USING estado::text::operaciones.pedido_estado_new;

-- Step 5: Drop old enum and rename new one
DROP TYPE operaciones.pedido_estado;
ALTER TYPE operaciones.pedido_estado_new RENAME TO pedido_estado;

-- Comment explaining the enum refactoring:
-- The PedidoEstado enum has been cleaned up to match the actual stepper workflow:
-- 1. ABIERTO - Initial state when pedido is first created (no items yet)
-- 2. ACTIVO - Creation phase with items added (ready to proceed to nota reception)  
-- 3. EN_RECEPCION_NOTA - Step 2: Assigning items to nota recepcion entities
-- 4. EN_RECEPCION_MERCADERIA - Step 3: Verifying received merchandise
-- 5. EN_SOLICITUD_PAGO - Step 4: Creating payment request groups (NEW)
-- 6. CONCLUIDO - All steps completed successfully
-- 7. CANCELADO - Pedido was cancelled at any stage

-- Removed unused states: MODIFICADO, REPROGRAMADO, EN_VERIFICACION, 
-- EN_VERIFICACION_SOLICITUD_AUTORIZACION, VERFICADO_SIN_MODIFICACION, VERFICADO_CON_MODIFICACION

