ALTER TABLE financiero.pre_gasto
    ADD COLUMN IF NOT EXISTS caja_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_pre_gasto_caja_id
    ON financiero.pre_gasto(caja_id);
