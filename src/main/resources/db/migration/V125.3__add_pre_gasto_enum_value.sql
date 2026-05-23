-- Asegurar que el valor 'PRE_GASTO' exista en el ENUM de forma segura
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type t 
        JOIN pg_enum e ON t.oid = e.enumtypid 
        JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        WHERE t.typname = 'tipo_autorizacion' AND n.nspname = 'administrativo' AND e.enumlabel = 'PRE_GASTO'
    ) THEN
        ALTER TYPE administrativo.tipo_autorizacion ADD VALUE 'PRE_GASTO';
    END IF;
END $$;
