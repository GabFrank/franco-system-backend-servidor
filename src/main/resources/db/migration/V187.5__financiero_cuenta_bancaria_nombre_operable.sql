-- Cuenta bancaria: nombre de cuenta (distinto de titular) + flag de disponibilidad para
-- operaciones financieras / caja mayor (distingue cuentas propias de cuentas de terceros).
-- Aditiva, idempotente.

ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS nombre varchar(150);

-- Backfill: cuentas existentes sin nombre -> titular o "CUENTA #id" como placeholder.
UPDATE financiero.cuenta_bancaria SET nombre = COALESCE(titular, 'CUENTA #' || id) WHERE nombre IS NULL;

-- Sin DEFAULT en el ADD COLUMN: con DEFAULT true, Postgres 11+ puebla TODAS las filas
-- existentes con true al instante (fast-default) y el backfill condicional siguiente no
-- matchearía ninguna fila (quedarían todas en true, incluidas las de terceros).
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS disponible_operaciones_financieras boolean;

-- Backfill de negocio: cuentas asociadas a una persona (proveedor/cliente) NO deben
-- aparecer por default en caja mayor / operaciones financieras.
UPDATE financiero.cuenta_bancaria
    SET disponible_operaciones_financieras = false
    WHERE persona_id IS NOT NULL AND disponible_operaciones_financieras IS NULL;

-- El resto (cuentas propias) quedan operables.
UPDATE financiero.cuenta_bancaria
    SET disponible_operaciones_financieras = true
    WHERE disponible_operaciones_financieras IS NULL;

-- DEFAULT para inserts futuros (después del backfill, nunca en el mismo ADD COLUMN).
ALTER TABLE financiero.cuenta_bancaria ALTER COLUMN disponible_operaciones_financieras SET DEFAULT true;
