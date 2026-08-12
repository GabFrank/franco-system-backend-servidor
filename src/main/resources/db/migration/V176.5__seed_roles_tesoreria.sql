-- =====================================================================
-- Tesorería (caja virtual / caja mayor) — roles de acceso
-- =====================================================================
-- Siembra los roles de control de acceso del módulo de tesorería, usados
-- por `service.financiero.TesoreriaSecurityService` para gatear los
-- resolvers de caja virtual y sus movimientos.
--
-- Aditivo e idempotente: INSERT ... WHERE NOT EXISTS por nombre. No altera
-- ni elimina nada. Nombres con espacios para respetar la convención de
-- personas.role (ej: 'RRHH VER'), así el desktop los matchea.
--
--   TESORERIA VER        -> ver cajas, saldos, movimientos e historial
--   TESORERIA GESTIONAR  -> crear/editar/borrar caja, cargar movimiento
--                           manual y transferir entre cajas
-- =====================================================================
INSERT INTO personas.role (nombre, creado_en)
SELECT r.nombre, now()
FROM (VALUES
    ('TESORERIA VER'),
    ('TESORERIA GESTIONAR')
) AS r(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(pr.nombre) = r.nombre
);
