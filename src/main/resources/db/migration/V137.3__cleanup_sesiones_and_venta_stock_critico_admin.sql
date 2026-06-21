-- Cerrar sesiones activas duplicadas: conservar solo la más reciente por usuario y dispositivo
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

-- Limpiar tokens en sesiones ya cerradas
UPDATE configuraciones.inicio_sesion
SET token = NULL
WHERE hora_fin IS NOT NULL
  AND token IS NOT NULL;

-- Por cada token FCM activo, conservar solo la sesión más reciente
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

-- VENTA_STOCK_CRITICO solo para rol ADMIN (preferencias y envío alineados)
DELETE FROM configuraciones.notificacion_tipo_role
WHERE tipo_notificacion = 'VENTA_STOCK_CRITICO';

INSERT INTO configuraciones.notificacion_tipo_role (role_id, tipo_notificacion, descripcion, es_obligatorio, creado_en)
SELECT r.id,
       'VENTA_STOCK_CRITICO',
       'Alerta de venta con stock resultante cero o negativo',
       false,
       NOW()
FROM personas.role r
WHERE r.nombre = 'ADMIN'
ON CONFLICT (role_id, tipo_notificacion) DO NOTHING;
