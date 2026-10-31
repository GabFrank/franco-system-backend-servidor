-- Issue #208: una devolucion DESCARTADO quedaba con resolucion NULL. Se agrega el
-- valor DESCARTE al tipo. Va solo en esta migracion: Postgres no deja usar un valor
-- de enum en la misma transaccion que lo crea, el backfill esta en V264.3.
-- operaciones.devolucion es central-only (no esta en ninguna publicacion).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'tipo_resolucion_devolucion' AND n.nspname = 'operaciones'
                     AND e.enumlabel = 'DESCARTE') THEN
        ALTER TYPE operaciones.tipo_resolucion_devolucion ADD VALUE 'DESCARTE';
    END IF;
END $$;
