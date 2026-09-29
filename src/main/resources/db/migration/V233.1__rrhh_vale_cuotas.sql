-- =====================================================================
-- RRHH — Vale en cuotas y vale en especie
-- =====================================================================
-- Un vale con cantidad_cuotas > 1 se descuenta en varias liquidaciones: la
-- cuota k se descuenta en la liquidacion cuyo periodo contiene fecha_descuento
-- (fecha del vale + k-1 meses). Un vale de 1 cuota no tiene filas en
-- vale_cuota y sigue descontandose entero, como antes.
-- en_especie: el vale se entrega en bienes (ej. uniforme), sin egreso de caja.
-- Todo aditivo. Central-only (schema rrhh, no se replica a filiales).
-- =====================================================================

ALTER TABLE rrhh.vale ADD COLUMN IF NOT EXISTS cantidad_cuotas INTEGER NOT NULL DEFAULT 1;
ALTER TABLE rrhh.vale ADD COLUMN IF NOT EXISTS en_especie BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS rrhh.vale_cuota (
    id                   BIGSERIAL PRIMARY KEY,
    vale_id              BIGINT NOT NULL REFERENCES rrhh.vale(id),
    numero               INTEGER NOT NULL,
    monto                NUMERIC(18,2) NOT NULL DEFAULT 0,
    fecha_descuento      DATE NOT NULL,
    estado               VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    liquidacion_id       BIGINT,
    liquidacion_final_id BIGINT,
    creado_en            TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_vale_cuota_numero UNIQUE (vale_id, numero)
);

CREATE INDEX IF NOT EXISTS idx_vale_cuota_vale ON rrhh.vale_cuota(vale_id);
CREATE INDEX IF NOT EXISTS idx_vale_cuota_estado_fecha ON rrhh.vale_cuota(estado, fecha_descuento);
