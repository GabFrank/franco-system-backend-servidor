-- =====================================================================
-- Rol RRHH SUCURSAL
-- =====================================================================
-- RRHH acotado para un gerente de sucursal: genera y edita liquidaciones
-- de sueldo y finiquitos de los funcionarios de las sucursales que cubren
-- sus cajas, y paga en efectivo desde su caja los ya aprobados. No aprueba
-- ni ve el resto de RRHH (nomina completa, dashboard, bonos, legajos).
-- Lo lee RrhhAlcanceService.
--
-- Aditivo e idempotente por nombre, mismo patron que V245.1. No lleva
-- espejo en filial: personas.role se replica desde central. No lo otorga
-- a nadie; asignarlo es por la pantalla de usuarios.
-- =====================================================================

INSERT INTO personas.role (nombre, creado_en)
SELECT 'RRHH SUCURSAL', now()
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(trim(pr.nombre)) = 'RRHH SUCURSAL'
);
