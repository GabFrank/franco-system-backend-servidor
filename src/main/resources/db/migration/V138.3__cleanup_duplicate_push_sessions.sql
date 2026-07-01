-- Limpieza única al actualizar el sistema (Flyway). No hay limpieza programada en runtime.
-- Un token FCM solo puede existir en una sesión (global, todos los usuarios)
WITH tokens_duplicados AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY token
               ORDER BY id DESC
           ) AS orden
    FROM configuraciones.inicio_sesion
    WHERE hora_fin IS NULL
      AND token IS NOT NULL
      AND token <> ''
)
UPDATE configuraciones.inicio_sesion s
SET token = NULL
FROM tokens_duplicados t
WHERE s.id = t.id
  AND t.orden > 1;

-- Cerrar sesiones activas duplicadas por usuario y dispositivo (conservar la más reciente)
WITH sesiones_duplicadas AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY usuario_id, id_dispositivo
               ORDER BY id DESC
           ) AS orden
    FROM configuraciones.inicio_sesion
    WHERE hora_fin IS NULL
      AND usuario_id IS NOT NULL
      AND id_dispositivo IS NOT NULL
)
UPDATE configuraciones.inicio_sesion s
SET hora_fin = NOW(),
    token = NULL
FROM sesiones_duplicadas d
WHERE s.id = d.id
  AND d.orden > 1;

-- Cerrar todas las sesiones abiertas sin token push (huérfanas)
UPDATE configuraciones.inicio_sesion
SET hora_fin = NOW()
WHERE hora_fin IS NULL
  AND (token IS NULL OR token = '');

-- Conservar solo una sesión abierta por usuario (prioriza la que tiene token push)
WITH sesion_activa_por_usuario AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY usuario_id
               ORDER BY CASE WHEN token IS NOT NULL AND token <> '' THEN 0 ELSE 1 END,
                        id DESC
           ) AS orden
    FROM configuraciones.inicio_sesion
    WHERE hora_fin IS NULL
      AND usuario_id IS NOT NULL
)
UPDATE configuraciones.inicio_sesion s
SET hora_fin = NOW(),
    token = NULL
FROM sesion_activa_por_usuario a
WHERE s.id = a.id
  AND a.orden > 1;

-- Cerrar sesiones abiertas antiguas sin token (residual)
UPDATE configuraciones.inicio_sesion
SET hora_fin = NOW()
WHERE hora_fin IS NULL
  AND (token IS NULL OR token = '')
  AND hora_inicio < NOW() - INTERVAL '7 days';
