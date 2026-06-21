-- user_id es el titular del rol (UI, JWT, NotificationRoleService).
-- Corrige registros donde usuario_id apunta a otro usuario distinto.
UPDATE personas.usuario_role
SET usuario_id = user_id
WHERE user_id IS NOT NULL
  AND (usuario_id IS NULL OR usuario_id <> user_id);
