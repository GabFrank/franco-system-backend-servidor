-- Refactor PedidoEstado enum to remove unused states and add EN_SOLICITUD_PAGO
-- This migration cleans up the enum to match the actual stepper workflow

-- V67.5: Add EN_SOLICITUD_PAGO to pedido_estado enum
-- This migration comes from the compras branch

-- Step 1: Check if EN_SOLICITUD_PAGO already exists in enum
DO $$
DECLARE
    enum_exists BOOLEAN;
    column_exists BOOLEAN;
    column_type TEXT;
BEGIN
    -- Check if estado column exists
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'operaciones' 
        AND table_name = 'pedido' 
        AND column_name = 'estado'
    ) INTO column_exists;
    
    IF column_exists THEN
        -- Get current column type
        SELECT data_type INTO column_type
        FROM information_schema.columns 
        WHERE table_schema = 'operaciones' 
        AND table_name = 'pedido' 
        AND column_name = 'estado';
        
        -- Check if EN_SOLICITUD_PAGO value exists in current enum
        IF column_type = 'USER-DEFINED' THEN
            SELECT EXISTS (
                SELECT 1 FROM pg_enum 
                WHERE enumtypid = (SELECT oid FROM pg_type WHERE typname = 'pedido_estado')
                AND enumlabel = 'EN_SOLICITUD_PAGO'
            ) INTO enum_exists;
        ELSE
            enum_exists := FALSE;
        END IF;
        
        IF NOT enum_exists THEN
            -- Create new enum type with only the values we actually use
            CREATE TYPE operaciones.pedido_estado_new AS ENUM (
                'ABIERTO',
                'ACTIVO', 
                'EN_RECEPCION_NOTA',
                'EN_RECEPCION_MERCADERIA',
                'EN_SOLICITUD_PAGO',
                'CONCLUIDO',
                'CANCELADO'
            );
            
            -- Step 2: Convert column to text temporarily to allow data manipulation
            ALTER TABLE operaciones.pedido ALTER COLUMN estado TYPE TEXT;
            
            -- Step 3: Update any existing pedidos that might be in CONCLUIDO state but should be in EN_SOLICITUD_PAGO
            -- based on whether they have completed the solicitud pago step
            -- Note: Check if columns exist before using them
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'pedido' AND column_name = 'fecha_fin_recepcion_mercaderia') THEN
                UPDATE operaciones.pedido 
                SET estado = 'EN_SOLICITUD_PAGO'
                WHERE estado = 'CONCLUIDO'
                  AND fecha_fin_recepcion_mercaderia IS NOT NULL;
            END IF;
            
            -- Step 4: Map any unused states to appropriate new states
            UPDATE operaciones.pedido 
            SET estado = CASE 
                WHEN estado = 'MODIFICADO' THEN 'ACTIVO'
                WHEN estado = 'REPROGRAMADO' THEN 'ACTIVO'
                WHEN estado = 'EN_VERIFICACION' THEN 'EN_RECEPCION_MERCADERIA'
                WHEN estado = 'EN_VERIFICACION_SOLICITUD_AUTORIZACION' THEN 'EN_RECEPCION_MERCADERIA'
                WHEN estado = 'VERFICADO_SIN_MODIFICACION' THEN 'EN_SOLICITUD_PAGO'
                WHEN estado = 'VERFICADO_CON_MODIFICACION' THEN 'EN_SOLICITUD_PAGO'
                ELSE estado
            END
            WHERE estado IN ('MODIFICADO', 'REPROGRAMADO', 'EN_VERIFICACION', 'EN_VERIFICACION_SOLICITUD_AUTORIZACION', 'VERFICADO_SIN_MODIFICACION', 'VERFICADO_CON_MODIFICACION');
            
            -- Step 5: Change column type to new enum
            ALTER TABLE operaciones.pedido 
            ALTER COLUMN estado TYPE operaciones.pedido_estado_new 
            USING estado::operaciones.pedido_estado_new;
            
            -- Step 6: Drop old enum and rename new one
            DROP TYPE IF EXISTS operaciones.pedido_estado;
            ALTER TYPE operaciones.pedido_estado_new RENAME TO pedido_estado;
        ELSE
            RAISE NOTICE 'EN_SOLICITUD_PAGO already exists in pedido_estado enum, skipping enum recreation';
        END IF;
    ELSE
        RAISE NOTICE 'estado column does not exist in pedido table, skipping enum modification';
    END IF;
END $$;

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
