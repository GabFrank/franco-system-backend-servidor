-- =====================================================================
-- RRHH — Fase 5: Liquidacion de sueldo (motor)
-- =====================================================================
-- Cabecera + detalle. El egreso real del neto se registra contra
-- financiero.caja_virtual (Caja Mayor) via FK plana. Central-only.
-- =====================================================================

CREATE TABLE IF NOT EXISTS rrhh.liquidacion_sueldo (
    id                         BIGSERIAL PRIMARY KEY,
    funcionario_id             BIGINT REFERENCES personas.funcionario(id),
    periodo                    VARCHAR(7),
    fecha_inicio               DATE,
    fecha_fin                  DATE,
    salario_base               NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_haberes              NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_descuentos           NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_neto                 NUMERIC(18,2) NOT NULL DEFAULT 0,
    moneda_id                  BIGINT REFERENCES financiero.moneda(id),
    estado                     VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    aprobado_por_id            BIGINT REFERENCES personas.usuario(id),
    fecha_aprobacion           TIMESTAMP,
    fecha_pago                 TIMESTAMP,
    caja_virtual_id            BIGINT REFERENCES financiero.caja_virtual(id),
    movimiento_caja_virtual_id BIGINT,
    movimiento_persona_id      BIGINT,
    observacion                TEXT,
    usuario_id                 BIGINT REFERENCES personas.usuario(id),
    creado_en                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_liquidacion_sueldo_func_periodo
    ON rrhh.liquidacion_sueldo(funcionario_id, periodo);
CREATE INDEX IF NOT EXISTS idx_liquidacion_sueldo_periodo
    ON rrhh.liquidacion_sueldo(periodo);

CREATE TABLE IF NOT EXISTS rrhh.liquidacion_item (
    id              BIGSERIAL PRIMARY KEY,
    liquidacion_id  BIGINT REFERENCES rrhh.liquidacion_sueldo(id) ON DELETE CASCADE,
    codigo          VARCHAR(50),
    descripcion     VARCHAR(255),
    monto           NUMERIC(18,2) NOT NULL DEFAULT 0,
    tipo            VARCHAR(20),
    referencia_id   BIGINT,
    referencia_tipo VARCHAR(30),
    manual          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_liquidacion_item_liquidacion
    ON rrhh.liquidacion_item(liquidacion_id);
