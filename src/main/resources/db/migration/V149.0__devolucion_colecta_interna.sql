-- Modulo Devoluciones: colecta interna. Estado COLECTADO (opcional entre
-- SEPARADO y RETIRADO) y sucursal de ubicacion fisica actual. Aditivo.
-- El nuevo valor de enum NO se usa en esta misma migracion (regla PG: no se
-- puede ADD VALUE y usarlo en la misma transaccion).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'devolucion_estado' AND n.nspname = 'operaciones' AND e.enumlabel = 'COLECTADO') THEN
        ALTER TYPE operaciones.devolucion_estado ADD VALUE 'COLECTADO';
    END IF;
END $$;

-- Ubicacion fisica actual de la mercaderia (por defecto la sucursal de origen).
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS sucursal_ubicacion_id BIGINT;
ALTER TABLE operaciones.devolucion ADD COLUMN IF NOT EXISTS colectado_en TIMESTAMP;

-- Backfill: las devoluciones existentes estan en su sucursal de origen.
UPDATE operaciones.devolucion
SET sucursal_ubicacion_id = sucursal_origen_id
WHERE sucursal_ubicacion_id IS NULL;

-- FK a sucursal (idempotente).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                   WHERE constraint_schema = 'operaciones' AND table_name = 'devolucion'
                   AND constraint_name = 'fk_devolucion_sucursal_ubicacion') THEN
        ALTER TABLE operaciones.devolucion
            ADD CONSTRAINT fk_devolucion_sucursal_ubicacion
            FOREIGN KEY (sucursal_ubicacion_id) REFERENCES empresarial.sucursal(id);
    END IF;
END $$;
