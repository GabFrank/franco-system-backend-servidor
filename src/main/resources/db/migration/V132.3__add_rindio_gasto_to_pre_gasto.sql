ALTER TABLE financiero.pre_gasto
    ADD COLUMN IF NOT EXISTS rindio_gasto BOOLEAN DEFAULT FALSE;
