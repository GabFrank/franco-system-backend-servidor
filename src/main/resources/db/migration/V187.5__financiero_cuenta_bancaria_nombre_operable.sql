-- Cuenta bancaria: nombre de cuenta (distinto de titular) + flag de disponibilidad para
-- operaciones financieras / caja mayor (distingue cuentas propias de cuentas de terceros).
-- Aditiva, idempotente.

ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS nombre varchar(150);

-- Backfill: cuentas existentes sin nombre -> titular o "CUENTA #id" como placeholder.
UPDATE financiero.cuenta_bancaria SET nombre = COALESCE(titular, 'CUENTA #' || id) WHERE nombre IS NULL;

ALTER TABLE financiero.cuenta_bancaria
    ADD COLUMN IF NOT EXISTS disponible_operaciones_financieras boolean DEFAULT true;

-- Backfill de negocio: cuentas asociadas a una persona (proveedor/cliente) NO deben
-- aparecer por default en caja mayor / operaciones financieras.
UPDATE financiero.cuenta_bancaria
    SET disponible_operaciones_financieras = false
    WHERE persona_id IS NOT NULL AND disponible_operaciones_financieras IS NULL;

-- Asegurar valor no nulo para el resto (cuentas propias).
UPDATE financiero.cuenta_bancaria
    SET disponible_operaciones_financieras = true
    WHERE disponible_operaciones_financieras IS NULL;
