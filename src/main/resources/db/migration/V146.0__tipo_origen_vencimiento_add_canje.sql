-- Modulo Devoluciones: nuevo origen de vencimiento para el reingreso por canje directo.
-- El producto que el proveedor canjea reingresa al stock con nuevo vencimiento,
-- registrado en operaciones.producto_vencimiento con tipo_origen = 'DEVOLUCION_CANJE'.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'tipo_origen_vencimiento' AND n.nspname = 'operaciones'
                   AND e.enumlabel = 'DEVOLUCION_CANJE') THEN
        ALTER TYPE operaciones.tipo_origen_vencimiento ADD VALUE 'DEVOLUCION_CANJE';
    END IF;
END $$;
