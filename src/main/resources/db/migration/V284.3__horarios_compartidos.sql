-- Horarios compartidos: un horario se asigna a varios funcionarios sin copiarlo.
--
-- Hasta ahora cada horario tenia un solo dueno (horario.usuario_id) y "Asignar a otros" creaba
-- una copia por persona: 162 horarios con solo 9 distintos. Desde aca la asignacion va en
-- horario_usuario (N a N) y los repetidos se unifican en uno solo.
--
-- Solo central: ni horario ni horario_usuario se replican. funcionario.horario_id si se
-- replica (MAIN_TO_ALL); la replicacion aplica sin chequear FK, igual que desde #523.
--
-- No se borra nada (regla de migraciones): los repetidos quedan con activo = false y sin
-- asignaciones; horario.usuario_id queda como estaba y el codigo deja de leerlo.

ALTER TABLE administrativo.horario ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS administrativo.horario_usuario (
    horario_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    creado_en  TIMESTAMP DEFAULT now(),
    CONSTRAINT pk_horario_usuario PRIMARY KEY (horario_id, usuario_id),
    CONSTRAINT fk_horario_usuario_horario FOREIGN KEY (horario_id) REFERENCES administrativo.horario (id),
    CONSTRAINT fk_horario_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario (id)
);

CREATE INDEX IF NOT EXISTS idx_horario_usuario_usuario ON administrativo.horario_usuario (usuario_id);

-- Cada horario con su "canonico": el de menor id entre los que tienen la misma entrada,
-- salida, turno, dias, tolerancia y descanso. Sin dias o con TODOS cuenta como TODOS.
CREATE TEMPORARY TABLE horario_canonico ON COMMIT DROP AS
WITH datos AS (
    SELECT h.id,
           h.usuario_id,
           h.hora_entrada,
           h.hora_salida,
           h.turno,
           h.tolerancia_minutos,
           h.inicio_descanso,
           h.fin_descanso,
           CASE
               WHEN NOT EXISTS (SELECT 1 FROM administrativo.horario_dias d WHERE d.horario_id = h.id)
                    OR EXISTS (SELECT 1 FROM administrativo.horario_dias d WHERE d.horario_id = h.id AND d.dia = 'TODOS')
                   THEN 'TODOS'
               ELSE (SELECT string_agg(d.dia, ',' ORDER BY d.dia) FROM administrativo.horario_dias d WHERE d.horario_id = h.id)
           END AS dias
    FROM administrativo.horario h
    WHERE h.hora_entrada IS NOT NULL
)
SELECT id,
       usuario_id,
       min(id) OVER (PARTITION BY hora_entrada, hora_salida, turno, dias,
                                  tolerancia_minutos, inicio_descanso, fin_descanso) AS canonico_id
FROM datos;

-- Quien tenia una copia queda asignado al canonico.
INSERT INTO administrativo.horario_usuario (horario_id, usuario_id)
SELECT DISTINCT c.canonico_id, c.usuario_id
FROM horario_canonico c
JOIN personas.usuario u ON u.id = c.usuario_id
ON CONFLICT DO NOTHING;

-- El horario principal de cada funcionario (lo leen pantallas viejas y es el respaldo de la
-- marcacion) pasa al canonico.
UPDATE personas.funcionario f
SET horario_id = c.canonico_id
FROM horario_canonico c
WHERE f.horario_id = c.id
  AND c.id <> c.canonico_id;

-- Los repetidos dejan de aparecer.
UPDATE administrativo.horario h
SET activo = FALSE
FROM horario_canonico c
WHERE h.id = c.id
  AND c.id <> c.canonico_id;
