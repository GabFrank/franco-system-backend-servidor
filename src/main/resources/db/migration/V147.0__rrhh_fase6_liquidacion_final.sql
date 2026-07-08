-- =====================================================================
-- RRHH — Fase 6: Liquidación final (finiquito)
-- =====================================================================
-- Finiquito al egreso: indemnización + vacaciones no gozadas + aguinaldo
-- proporcional. Aditivo, schema rrhh, central-only.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. rrhh.liquidacion_final
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.liquidacion_final (
    id                          BIGSERIAL PRIMARY KEY,
    funcionario_id              BIGINT REFERENCES personas.funcionario(id),
    fecha_egreso                DATE,
    motivo_egreso               VARCHAR(30),
    antiguedad_dias             INTEGER,
    antiguedad_meses            INTEGER,
    antiguedad_anios            INTEGER,
    salario_promedio            NUMERIC(18,2),
    indemnizacion_aplica        BOOLEAN NOT NULL DEFAULT FALSE,
    indemnizacion_monto         NUMERIC(18,2),
    dias_vacaciones_no_gozadas  INTEGER,
    monto_vacaciones_no_gozadas NUMERIC(18,2),
    aguinaldo_proporcional      NUMERIC(18,2),
    total_liquidado             NUMERIC(18,2),
    moneda_id                   BIGINT REFERENCES financiero.moneda(id),
    estado                      VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    aprobado_por_id             BIGINT REFERENCES personas.usuario(id),
    fecha_aprobacion            TIMESTAMP,
    fecha_pago                  TIMESTAMP,
    caja_virtual_id             BIGINT,
    movimiento_caja_virtual_id  BIGINT,
    movimiento_persona_id       BIGINT,
    observacion                 TEXT,
    usuario_id                  BIGINT REFERENCES personas.usuario(id),
    creado_en                   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_liq_final_funcionario
    ON rrhh.liquidacion_final(funcionario_id);
CREATE INDEX IF NOT EXISTS idx_liq_final_estado
    ON rrhh.liquidacion_final(estado);

-- ---------------------------------------------------------------------
-- 2. rrhh.liquidacion_final_item
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.liquidacion_final_item (
    id                  BIGSERIAL PRIMARY KEY,
    liquidacion_final_id BIGINT REFERENCES rrhh.liquidacion_final(id),
    concepto            VARCHAR(40),
    descripcion         TEXT,
    monto               NUMERIC(18,2)
);

CREATE INDEX IF NOT EXISTS idx_liq_final_item_liq
    ON rrhh.liquidacion_final_item(liquidacion_final_id);

-- Nota: las config keys INDEMNIZACION_DIAS_POR_ANIO (15) e
-- INDEMNIZACION_ANTIGUEDAD_MIN_DIAS (90) ya se siembran en V141.0
-- (fundaciones); no se re-insertan aca.
