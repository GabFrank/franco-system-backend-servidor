ALTER TABLE administrativo.jornada 
ADD COLUMN IF NOT EXISTS hora_entrada_horario TIME,
ADD COLUMN IF NOT EXISTS hora_salida_horario TIME,
ADD COLUMN IF NOT EXISTS inicio_descanso_horario TIME,
ADD COLUMN IF NOT EXISTS fin_descanso_horario TIME,
ADD COLUMN IF NOT EXISTS tolerancia_minutos_horario INTEGER;
