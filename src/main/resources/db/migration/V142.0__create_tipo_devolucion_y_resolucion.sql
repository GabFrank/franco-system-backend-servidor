-- Modulo Devoluciones: enums para tipo de documento y forma de resolucion.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'tipo_devolucion' AND n.nspname = 'operaciones') THEN
        CREATE TYPE operaciones.tipo_devolucion AS ENUM ('SIN_PROVEEDOR', 'CON_PROVEEDOR');
        COMMENT ON TYPE operaciones.tipo_devolucion IS 'Tipo de devolucion: averia sin proveedor (genera gasto) o con devolucion a proveedor';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                   WHERE t.typname = 'tipo_resolucion_devolucion' AND n.nspname = 'operaciones') THEN
        CREATE TYPE operaciones.tipo_resolucion_devolucion AS ENUM ('NOTA_CREDITO', 'CANJE');
        COMMENT ON TYPE operaciones.tipo_resolucion_devolucion IS 'Resolucion de devolucion a proveedor: nota de credito o canje directo';
    END IF;
END $$;
