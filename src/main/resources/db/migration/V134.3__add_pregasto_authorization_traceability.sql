ALTER TABLE financiero.pre_gasto
    ADD COLUMN IF NOT EXISTS autorizado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rechazado_por_id BIGINT,
    ADD COLUMN IF NOT EXISTS rechazado_en TIMESTAMP WITH TIME ZONE;

DO $$
BEGIN
    BEGIN
        ALTER TABLE financiero.pre_gasto
            ADD CONSTRAINT fk_pre_gasto_rechazado_por
            FOREIGN KEY (rechazado_por_id)
            REFERENCES personas.persona(id);
    EXCEPTION
        WHEN duplicate_object THEN NULL;
    END;
END $$;

CREATE INDEX IF NOT EXISTS idx_pre_gasto_autorizado_en
    ON financiero.pre_gasto(autorizado_en);

CREATE INDEX IF NOT EXISTS idx_pre_gasto_rechazado_en
    ON financiero.pre_gasto(rechazado_en);
