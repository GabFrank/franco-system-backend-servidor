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

-- ---------------------------------------------------------------------
-- 3. Config: días de indemnización por año (default legal PY = 15)
-- ---------------------------------------------------------------------
INSERT INTO rrhh.configuracion_rrhh (clave, valor, tipo, descripcion, creado_en)
SELECT 'INDEMNIZACION_DIAS_POR_ANIO', '15', 'NUMBER', 'Dias de salario por anio de antiguedad para la indemnizacion por despido injustificado', now()
WHERE NOT EXISTS (SELECT 1 FROM rrhh.configuracion_rrhh WHERE clave = 'INDEMNIZACION_DIAS_POR_ANIO');
