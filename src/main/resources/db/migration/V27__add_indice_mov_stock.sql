DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'movimiento_stock_sucursal_id_idx' -- Replace with your index name
        AND n.nspname = 'operaciones'  -- Replace with the schema name, like 'public'
    ) THEN
        CREATE INDEX movimiento_stock_sucursal_id_idx
        ON operaciones.movimiento_stock (sucursal_id, id);
    END IF;
END $$;