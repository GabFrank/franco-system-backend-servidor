-- =====================================================================
-- Tesorería — MVP: permisos dedicados CPC (cobrar) / CPP (pagar)
-- =====================================================================
-- Aditivo e idempotente. Complementan TESORERIA VER/GESTIONAR (DA6).
-- =====================================================================
INSERT INTO personas.role (nombre, creado_en)
SELECT r.nombre, now()
FROM (VALUES
    ('TESORERIA CPC COBRAR'),
    ('TESORERIA CPP PAGAR')
) AS r(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM personas.role pr WHERE upper(pr.nombre) = r.nombre
);
