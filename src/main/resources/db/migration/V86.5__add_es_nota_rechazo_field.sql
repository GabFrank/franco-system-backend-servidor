-- ===============================================
-- MIGRACIÓN: AGREGAR CAMPO ES_NOTA_RECHAZO
-- ===============================================
-- Esta migración agrega el campo es_nota_rechazo para identificar
-- notas de recepción que son específicamente para rechazos

-- Step 1: Add the es_nota_rechazo field to nota_recepcion table
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion' AND column_name = 'es_nota_rechazo') THEN
        ALTER TABLE operaciones.nota_recepcion ADD COLUMN es_nota_rechazo BOOLEAN DEFAULT FALSE;
    END IF;
END $$;

-- Step 2: Create index for better performance on filtering
CREATE INDEX IF NOT EXISTS idx_nota_recepcion_es_nota_rechazo ON operaciones.nota_recepcion(es_nota_rechazo);

-- Step 3: Update existing records that have tipo_boleta = 'RECHAZO_NO_ENTREGADO'
-- to set es_nota_rechazo = true (mantener compatibilidad con datos existentes)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion' AND column_name = 'es_nota_rechazo') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'operaciones' AND table_name = 'nota_recepcion' AND column_name = 'tipo_boleta') THEN
            UPDATE operaciones.nota_recepcion 
            SET es_nota_rechazo = TRUE 
            WHERE tipo_boleta = 'RECHAZO_NO_ENTREGADO' AND es_nota_rechazo IS NOT TRUE;
        END IF;
    END IF;
END $$;

-- Step 4: Add comment for documentation
COMMENT ON COLUMN operaciones.nota_recepcion.es_nota_rechazo IS 'Indica si la nota de recepción es específicamente para rechazos de productos no entregados';

-- ===============================================
-- EXPLICACIÓN DE LOS CAMBIOS
-- ===============================================
-- 
-- Campo es_nota_rechazo:
--    - Tipo: BOOLEAN (true/false)
--    - Default: FALSE (la mayoría de notas son normales)
--    - Propósito: Identificar fácilmente las notas que son para rechazos
--    - Ventajas: 
--      * Más simple que crear un nuevo estado
--      * Permite que las notas de rechazo mantengan cualquier estado normal
--      * Facilita filtrado y reportes específicos
--      * Es más explícito y claro en su propósito
--      * Elimina la necesidad de usar tipo_boleta = 'RECHAZO_NO_ENTREGADO'
--
-- Índice:
--    - Mejora el rendimiento de consultas que filtran por es_nota_rechazo
--    - Útil para reportes y listados de rechazos
--
-- Actualización de registros existentes:
--    - Las notas existentes con tipo_boleta = 'RECHAZO_NO_ENTREGADO' 
--      se marcan automáticamente como es_nota_rechazo = true
--    - Mantiene la consistencia de datos existentes
--    - A partir de ahora, el código usará solo es_nota_rechazo para identificar rechazos 