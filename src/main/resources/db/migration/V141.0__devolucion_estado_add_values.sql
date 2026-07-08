-- Modulo Devoluciones: realineacion del enum operaciones.devolucion_estado.
-- El tipo fue creado en V73.5 con ('PENDIENTE','CONFIRMADA','CANCELADA').
-- Agregamos los estados de la maquina de estados definitiva (aditivo, regla Flyway).
-- 'CONFIRMADA' queda como valor legacy sin uso (no se puede eliminar, regla aditiva).
--
-- Patron idempotente por valor (mismo estilo que V125.3). ALTER TYPE ADD VALUE
-- requiere PG 12+ para correr dentro de bloque transaccional (ya en uso en el repo).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'devolucion_estado' AND n.nspname = 'operaciones' AND e.enumlabel = 'SEPARADO') THEN
        ALTER TYPE operaciones.devolucion_estado ADD VALUE 'SEPARADO';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'devolucion_estado' AND n.nspname = 'operaciones' AND e.enumlabel = 'RETIRADO') THEN
        ALTER TYPE operaciones.devolucion_estado ADD VALUE 'RETIRADO';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'devolucion_estado' AND n.nspname = 'operaciones' AND e.enumlabel = 'CANJEADO') THEN
        ALTER TYPE operaciones.devolucion_estado ADD VALUE 'CANJEADO';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'devolucion_estado' AND n.nspname = 'operaciones' AND e.enumlabel = 'ACREDITADO') THEN
        ALTER TYPE operaciones.devolucion_estado ADD VALUE 'ACREDITADO';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid
                   JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'devolucion_estado' AND n.nspname = 'operaciones' AND e.enumlabel = 'DESCARTADO') THEN
        ALTER TYPE operaciones.devolucion_estado ADD VALUE 'DESCARTADO';
    END IF;
END $$;
