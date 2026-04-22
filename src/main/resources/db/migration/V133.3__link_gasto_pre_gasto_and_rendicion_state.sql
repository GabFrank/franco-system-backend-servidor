ALTER TABLE financiero.gasto
    ADD COLUMN IF NOT EXISTS pre_gasto_id BIGINT,
    ADD COLUMN IF NOT EXISTS pre_gasto_sucursal_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_gasto_pre_gasto_link
    ON financiero.gasto(pre_gasto_id, pre_gasto_sucursal_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'financiero'
          AND table_name = 'pre_gasto'
    ) THEN
        ALTER TABLE financiero.pre_gasto
            ADD COLUMN IF NOT EXISTS estado_rendicion VARCHAR(20),
            ADD COLUMN IF NOT EXISTS fecha_rendicion TIMESTAMP WITH TIME ZONE;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'financiero'
          AND table_name = 'pre_gasto'
    ) THEN
        BEGIN
            ALTER TABLE financiero.gasto
                ADD CONSTRAINT fk_gasto_pre_gasto
                FOREIGN KEY (pre_gasto_id, pre_gasto_sucursal_id)
                REFERENCES financiero.pre_gasto(id, sucursal_id)
                ON DELETE SET NULL;
        EXCEPTION
            WHEN duplicate_object THEN NULL;
        END;
    END IF;
END $$;
