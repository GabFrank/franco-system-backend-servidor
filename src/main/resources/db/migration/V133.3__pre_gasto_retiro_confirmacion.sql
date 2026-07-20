ALTER TABLE financiero.pre_gasto
    ADD COLUMN IF NOT EXISTS retiro_confirmado_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS retiro_confirmado_funcionario_id BIGINT;
