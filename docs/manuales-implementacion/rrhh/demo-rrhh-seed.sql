-- =============================================================================
-- SEMILLA DE DEMO RRHH — "prepara el terreno" para la presentación al encargado.
-- Crea 2 funcionarios en estados conocidos. Todo lo demás (vales, horas extra,
-- penalizaciones, préstamos, liquidaciones) se hace EN VIVO durante la demo.
--
-- Idempotente: sólo inserta si no existe (marcador por documento DEMO-*).
-- Cleanup al final del archivo (comentado).
--
-- Sujetos:
--   1. MARÍA FERNANDA GIMÉNEZ LÓPEZ (doc DEMO-0001) — ESTRELLA, completa,
--      VENDEDOR, ingreso 2021-04-01 (~5 años → preaviso 60d), nacida en julio
--      (aparece en "Cumpleaños del mes"). Sobre ella corremos liquidación
--      mensual y liquidación final.
--   2. JORGE DANIEL BENÍTEZ (doc DEMO-0002) — INCOMPLETA (sólo nombre + doc):
--      sin cargo, sin salario, sin datos personales. Aparece arriba en
--      "Legajos por completar"; la completamos EN VIVO desde el dashboard.
-- =============================================================================

-- 1) ESTRELLA — completa -------------------------------------------------------
WITH p AS (
  INSERT INTO personas.persona
    (nombre, documento, nacimiento, sexo, direccion, ciudad_id, telefono, email, activo, creado_en)
  SELECT 'MARIA FERNANDA GIMENEZ LOPEZ', 'DEMO-0001', DATE '1992-07-10', 'F',
         'BARRIO SAN BLAS', 1, '0985111222', 'mariafernanda.demo@frc.com', true, now()
  WHERE NOT EXISTS (SELECT 1 FROM personas.persona WHERE documento = 'DEMO-0001')
  RETURNING id
)
INSERT INTO personas.funcionario
  (persona_id, cargo_id, sueldo, fecha_ingreso, sucursal_id, activo, ips_activo,
   numero_ips, fecha_ingreso_ips, moneda_id, fase_prueba, diarista, creado_en,
   contacto_emergencia_nombre, contacto_emergencia_telefono)
SELECT p.id, 2, 3200000, TIMESTAMP '2021-04-01 00:00:00', 1, true, true,
       '1234567', TIMESTAMP '2021-04-01 00:00:00', 1, false, false, now(),
       'PEDRO GIMENEZ', '0981333444'
FROM p;

-- 2) INCOMPLETA — sólo nombre + documento --------------------------------------
-- OJO: funcionario tiene defaults (sueldo=0, fecha_ingreso=now()); hay que forzar
-- NULL explícito para que quede realmente incompleta y ranquee al fondo (score 1).
WITH p AS (
  INSERT INTO personas.persona (nombre, documento, activo, creado_en)
  SELECT 'JORGE DANIEL BENITEZ', 'DEMO-0002', true, now()
  WHERE NOT EXISTS (SELECT 1 FROM personas.persona WHERE documento = 'DEMO-0002')
  RETURNING id
)
INSERT INTO personas.funcionario (persona_id, cargo_id, sueldo, fecha_ingreso, activo, creado_en)
SELECT p.id, NULL, NULL, NULL, true, now() FROM p;

-- Verificación ----------------------------------------------------------------
SELECT f.id AS funcionario_id, p.nombre, p.documento, c.nombre AS cargo,
       f.sueldo, f.fecha_ingreso, f.activo
FROM personas.funcionario f
JOIN personas.persona p ON p.id = f.persona_id
LEFT JOIN empresarial.cargo c ON c.id = f.cargo_id
WHERE p.documento IN ('DEMO-0001', 'DEMO-0002')
ORDER BY p.documento;

-- =============================================================================
-- CLEANUP (ejecutar tras la demo para dejar la DB como estaba). Descomentar.
-- Borra primero los eventos RRHH creados en vivo, luego funcionario y persona.
-- =============================================================================
-- WITH fids AS (
--   SELECT f.id FROM personas.funcionario f JOIN personas.persona p ON p.id=f.persona_id
--   WHERE p.documento IN ('DEMO-0001','DEMO-0002')
-- )
-- DELETE FROM rrhh.liquidacion_final_item WHERE liquidacion_final_id IN (SELECT id FROM rrhh.liquidacion_final WHERE funcionario_id IN (SELECT id FROM fids));
-- (repetir el patrón para liquidacion_sueldo/vale/prestamo/etc. según lo creado en vivo)
-- DELETE FROM personas.funcionario WHERE persona_id IN (SELECT id FROM personas.persona WHERE documento IN ('DEMO-0001','DEMO-0002'));
-- DELETE FROM personas.persona WHERE documento IN ('DEMO-0001','DEMO-0002');
