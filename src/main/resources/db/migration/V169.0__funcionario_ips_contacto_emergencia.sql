-- =====================================================================
-- Funcionario — fecha de ingreso a IPS y contacto de emergencia.
-- =====================================================================
-- ips_activo, numero_ips, cuenta_bancaria y codigo_interno ya existen en la
-- tabla; lo que faltaba para completar la ficha del legajo:
--   - fecha de ingreso a IPS (para el legajo laboral)
--   - contacto de emergencia (nombre + telefono), estandar en un legajo
--
-- Aditiva: columnas nullable. Idempotente.
-- =====================================================================

ALTER TABLE personas.funcionario
    ADD COLUMN IF NOT EXISTS fecha_ingreso_ips DATE;

ALTER TABLE personas.funcionario
    ADD COLUMN IF NOT EXISTS contacto_emergencia_nombre VARCHAR(150);

ALTER TABLE personas.funcionario
    ADD COLUMN IF NOT EXISTS contacto_emergencia_telefono VARCHAR(50);
