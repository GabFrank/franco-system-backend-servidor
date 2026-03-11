ALTER TABLE administrativo.horario ADD COLUMN IF NOT EXISTS inicio_descanso TIME WITHOUT TIME ZONE;
ALTER TABLE administrativo.horario ADD COLUMN IF NOT EXISTS fin_descanso TIME WITHOUT TIME ZONE;
ALTER TABLE administrativo.horario ADD COLUMN IF NOT EXISTS tolerancia_minutos INTEGER;
