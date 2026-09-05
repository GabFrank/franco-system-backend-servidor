-- =====================================================================
-- Venta con tarjeta — rol para completar un cobro pendiente desde el PDV
-- =====================================================================
-- Cuando el cajero pospone la lectura del cupón, la venta_tarjeta queda en
-- PENDIENTE y el cierre de caja se la reclama con el mensaje "Registralas
-- escaneando el QR del cupón desde el PDV" (desktop:
-- adicionar-caja-dialog.component.ts). Esa pantalla es
-- ListVentaTarjetaComponent, y hasta ahora colgaba de un menú gateado con
-- ROLES.ADMIN: el cajero al que el mensaje le hablaba no tenía forma de
-- llegar. Este rol es el que le abre ese camino.
--
-- Alcance deliberadamente chico: da acceso a la lista de ventas con tarjeta
-- y a completar un PENDIENTE de la propia sucursal. NO habilita crear
-- terminales, editar proveedores de servicio ni la configuración del módulo
-- — esos botones siguen exigiendo ADMIN
-- (terminal-pos-dashboard.component.html).
--
-- Aditivo e idempotente: INSERT ... WHERE NOT EXISTS por nombre. No altera
-- ni elimina nada. Nombre con espacios para respetar la convención de
-- personas.role (ej: 'TESORERIA VER'), así el desktop lo matchea contra
-- ROLES.VENTA_TARJETA_COMPLETAR.
--
-- No lleva espejo en filial: personas.role se replica desde el central, y
-- el seed de roles de tesorería (V176.5) sentó ese precedente.
--
-- Asignarlo a los cajeros es un paso aparte, por la pantalla de usuarios:
-- esta migración solo crea el rol, no lo otorga a nadie.
-- =====================================================================
INSERT INTO personas.role (nombre, creado_en)
SELECT r.nombre, now()
FROM (VALUES
    ('VENTA TARJETA COMPLETAR')
) AS r(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(pr.nombre) = r.nombre
);
