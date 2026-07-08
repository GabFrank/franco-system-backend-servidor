-- =====================================================================
-- RRHH — Notificaciones: tipo RRHH_SOLICITUD -> roles aprobadores
-- =====================================================================
-- Mapea el tipo de notificacion de las solicitudes mobile (vale/vacacion)
-- a los roles que las aprueban, para que obtenerUsuariosPorTipoNotificacion
-- resuelva los destinatarios del push. Aditivo, idempotente.
-- =====================================================================

INSERT INTO configuraciones.notificacion_tipo_role (id, role_id, tipo_notificacion, descripcion, es_obligatorio, creado_en)
SELECT nextval('configuraciones.notificacion_tipo_role_id_seq'), r.id, 'RRHH_SOLICITUD',
       'Solicitudes de vale/vacacion desde el mobile (para aprobadores)', false, now()
FROM personas.role r
WHERE r.nombre IN ('RRHH APROBAR', 'RRHH GESTIONAR')
  AND NOT EXISTS (
      SELECT 1 FROM configuraciones.notificacion_tipo_role t
      WHERE t.tipo_notificacion = 'RRHH_SOLICITUD' AND t.role_id = r.id
  );

-- Alertas RRHH del job diario (cuotas vencidas, liquidaciones pendientes)
INSERT INTO configuraciones.notificacion_tipo_role (id, role_id, tipo_notificacion, descripcion, es_obligatorio, creado_en)
SELECT nextval('configuraciones.notificacion_tipo_role_id_seq'), r.id, 'RRHH_ALERTA',
       'Alertas diarias del modulo RRHH (cuotas vencidas, liquidaciones pendientes)', false, now()
FROM personas.role r
WHERE r.nombre IN ('RRHH GESTIONAR', 'RRHH VER')
  AND NOT EXISTS (
      SELECT 1 FROM configuraciones.notificacion_tipo_role t
      WHERE t.tipo_notificacion = 'RRHH_ALERTA' AND t.role_id = r.id
  );
