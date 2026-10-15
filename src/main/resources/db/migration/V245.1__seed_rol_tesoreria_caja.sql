-- =====================================================================
-- Rol TESORERIA CAJA
-- =====================================================================
-- Operador de caja acotado: habilita ver y mover el efectivo de las cajas
-- a las que el usuario tiene acceso (financiero.caja_virtual_acceso), sin
-- las lecturas globales de tesoreria (saldo consolidado, bancos, cheques).
-- Lo lee TesoreriaSecurityService (requireVerCaja / requireOperarCaja).
--
-- Aditivo e idempotente por nombre, mismo patron que V237.3. El trim es
-- porque personas.role tiene nombres con espacio final. No lleva espejo
-- en filial: personas.role se replica desde central. No lo otorga a
-- nadie; asignarlo es por la pantalla de usuarios.
-- =====================================================================

INSERT INTO personas.role (nombre, creado_en)
SELECT 'TESORERIA CAJA', now()
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(trim(pr.nombre)) = 'TESORERIA CAJA'
);
