-- =====================================================================
-- Rol CAMBIAR COTIZACION
-- =====================================================================
-- saveCambio pasa a exigir este rol en el central (issue #326,
-- TesoreriaSecurityService.requireCambiarCotizacion). El rol ya existe en
-- las bases en uso y es el que gatea la pantalla en el desktop
-- (ROLES.CAMBIAR_COTIZACION); esta migracion solo lo garantiza en una
-- base que no lo tenga, para que el control no deje a todos afuera.
--
-- Aditivo e idempotente por nombre, mismo patron que V236.1. El trim es
-- porque personas.role tiene nombres con espacio final. No lleva espejo
-- en filial: personas.role se replica desde central. No lo otorga a
-- nadie; asignarlo es por la pantalla de usuarios.
-- =====================================================================

INSERT INTO personas.role (nombre, creado_en)
SELECT 'CAMBIAR COTIZACION', now()
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(trim(pr.nombre)) = 'CAMBIAR COTIZACION'
);
