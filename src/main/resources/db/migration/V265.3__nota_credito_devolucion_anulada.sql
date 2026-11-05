-- Issue #207: revertir el credito borraba la nota de credito del retiro. Ahora
-- queda ANULADA, con quien y cuando, y el retiro se puede volver a acreditar.
-- Central-only: nota_credito_devolucion no se replica.

ALTER TABLE operaciones.nota_credito_devolucion
    ADD COLUMN IF NOT EXISTS anulado_por_id BIGINT;
ALTER TABLE operaciones.nota_credito_devolucion
    ADD COLUMN IF NOT EXISTS anulado_en TIMESTAMP;

-- Un retiro puede tener varias notas ANULADAS pero una sola vigente: la unicidad
-- total por retiro (V153.0) pasa a un indice unico parcial.
ALTER TABLE operaciones.nota_credito_devolucion
    DROP CONSTRAINT IF EXISTS uq_nota_credito_retiro;
CREATE UNIQUE INDEX IF NOT EXISTS uq_nota_credito_retiro_vigente
    ON operaciones.nota_credito_devolucion (retiro_id)
    WHERE estado <> 'ANULADA';
