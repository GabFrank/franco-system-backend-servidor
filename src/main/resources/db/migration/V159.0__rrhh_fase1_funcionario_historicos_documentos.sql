-- =====================================================================
-- RRHH — Fase 1: Núcleo funcionario ampliado
-- =====================================================================
-- Trazabilidad de cambios de cargo/salario y legajo digital del funcionario.
-- Todo aditivo, schema rrhh, central-only. Las columnas de egreso
-- (fecha_egreso, motivo_egreso) ya existen en personas.funcionario (Fase 0).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. rrhh.funcionario_cargo_historico
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.funcionario_cargo_historico (
    id                BIGSERIAL PRIMARY KEY,
    funcionario_id    BIGINT REFERENCES personas.funcionario(id),
    cargo_id          BIGINT REFERENCES empresarial.cargo(id),
    fecha_desde       DATE,
    fecha_hasta       DATE,
    motivo            TEXT,
    autorizado_por_id BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_func_cargo_hist_funcionario
    ON rrhh.funcionario_cargo_historico(funcionario_id);

-- ---------------------------------------------------------------------
-- 2. rrhh.funcionario_salario_historico
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.funcionario_salario_historico (
    id                BIGSERIAL PRIMARY KEY,
    funcionario_id    BIGINT REFERENCES personas.funcionario(id),
    salario_anterior  NUMERIC(18,2),
    salario_nuevo     NUMERIC(18,2),
    moneda_id         BIGINT REFERENCES financiero.moneda(id),
    fecha_vigencia    DATE,
    motivo            TEXT,
    autorizado_por_id BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_func_salario_hist_funcionario
    ON rrhh.funcionario_salario_historico(funcionario_id);

-- ---------------------------------------------------------------------
-- 3. rrhh.funcionario_documento
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.funcionario_documento (
    id             BIGSERIAL PRIMARY KEY,
    funcionario_id BIGINT REFERENCES personas.funcionario(id),
    tipo           VARCHAR(30) NOT NULL DEFAULT 'OTRO',
    nombre_archivo TEXT,
    ruta_relativa  TEXT,
    mime_type      VARCHAR(120),
    tamano_bytes   BIGINT,
    fecha_subida   TIMESTAMP,
    vencimiento    DATE,
    observacion    TEXT,
    anulado        BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_func_documento_funcionario
    ON rrhh.funcionario_documento(funcionario_id);
