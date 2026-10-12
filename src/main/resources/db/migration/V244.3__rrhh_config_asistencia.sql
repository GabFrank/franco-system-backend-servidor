-- =====================================================================
-- RRHH — Pesos de la puntuacion de asistencia del legajo.
-- puntaje = 10 x (1 - (peso_falta x ausencias injustificadas
--                      + peso_tardanza x llegadas tardias) / dias esperados)
-- Aditivo e idempotente.
-- =====================================================================

INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion)
SELECT v.clave, v.valor, v.tipo, v.descripcion
FROM (VALUES
    ('ASISTENCIA_PESO_FALTA',    '3', 'NUMBER', 'Puntuacion de asistencia: peso de cada ausencia injustificada'),
    ('ASISTENCIA_PESO_TARDANZA', '1', 'NUMBER', 'Puntuacion de asistencia: peso de cada llegada tardia')
) AS v(clave, valor, tipo, descripcion)
WHERE NOT EXISTS (SELECT 1 FROM rrhh.configuracion_rrhh c WHERE c.clave = v.clave);
