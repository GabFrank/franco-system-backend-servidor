ALTER TABLE financiero.gasto ADD COLUMN IF NOT EXISTS pre_gasto_id bigint;
ALTER TABLE financiero.gasto ADD COLUMN IF NOT EXISTS pre_gasto_sucursal_id bigint;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_gasto_pre_gasto'
    ) THEN
        ALTER TABLE financiero.gasto
            ADD CONSTRAINT fk_gasto_pre_gasto FOREIGN KEY (pre_gasto_id, pre_gasto_sucursal_id) REFERENCES financiero.pre_gasto(id, sucursal_id) ON DELETE SET NULL;
    END IF;
END $$;
