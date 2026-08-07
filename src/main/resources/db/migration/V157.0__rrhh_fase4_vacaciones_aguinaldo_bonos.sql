-- =====================================================================
-- RRHH — Fase 4: Vacaciones, aguinaldo y bonos
-- =====================================================================
-- Tablas nuevas en el schema rrhh. Todo aditivo. Central-only.
-- Los montos de venta de vacaciones, aguinaldo y bonos se cobran como
-- HABER en la liquidacion de sueldo (Fase 5) via la FK plana liquidacion_id.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. rrhh.vacacion
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.vacacion (
    id             BIGSERIAL PRIMARY KEY,
    funcionario_id BIGINT REFERENCES personas.funcionario(id),
    anio_servicio  INTEGER,
    dias_generados INTEGER NOT NULL DEFAULT 0,
    dias_gozados   INTEGER NOT NULL DEFAULT 0,
    fecha_corte    DATE,
    prescrita      BOOLEAN NOT NULL DEFAULT FALSE,
    observacion    TEXT,
    usuario_id     BIGINT REFERENCES personas.usuario(id),
    creado_en      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vacacion_funcionario_anio
    ON rrhh.vacacion(funcionario_id, anio_servicio);

-- ---------------------------------------------------------------------
-- 2. rrhh.vacacion_periodo
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.vacacion_periodo (
    id                  BIGSERIAL PRIMARY KEY,
    vacacion_id         BIGINT REFERENCES rrhh.vacacion(id),
    fecha_desde         DATE,
    fecha_hasta         DATE,
    dias_usados         INTEGER NOT NULL DEFAULT 0,
    estado              VARCHAR(20) NOT NULL DEFAULT 'SOLICITADA',
    autorizado_por_id   BIGINT REFERENCES personas.usuario(id),
    novedades_generadas BOOLEAN NOT NULL DEFAULT FALSE,
    observacion         TEXT,
    creado_en           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vacacion_periodo_vacacion
    ON rrhh.vacacion_periodo(vacacion_id);

-- ---------------------------------------------------------------------
-- 3. rrhh.vacacion_venta
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.vacacion_venta (
    id             BIGSERIAL PRIMARY KEY,
    vacacion_id    BIGINT REFERENCES rrhh.vacacion(id),
    dias           INTEGER NOT NULL DEFAULT 0,
    monto          NUMERIC(18,2) NOT NULL DEFAULT 0,
    fecha          DATE,
    estado         VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    liquidacion_id BIGINT,
    observacion    TEXT,
    creado_en      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vacacion_venta_vacacion
    ON rrhh.vacacion_venta(vacacion_id);

-- ---------------------------------------------------------------------
-- 4. rrhh.aguinaldo
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.aguinaldo (
    id               BIGSERIAL PRIMARY KEY,
    funcionario_id   BIGINT REFERENCES personas.funcionario(id),
    anio             INTEGER,
    monto_calculado  NUMERIC(18,2) NOT NULL DEFAULT 0,
    meses_trabajados INTEGER NOT NULL DEFAULT 0,
    estado           VARCHAR(20) NOT NULL DEFAULT 'CALCULADO',
    fecha_pago       DATE,
    liquidacion_id   BIGINT,
    usuario_id       BIGINT REFERENCES personas.usuario(id),
    creado_en        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_aguinaldo_funcionario_anio
    ON rrhh.aguinaldo(funcionario_id, anio);

-- ---------------------------------------------------------------------
-- 5. rrhh.bono
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.bono (
    id                BIGSERIAL PRIMARY KEY,
    funcionario_id    BIGINT REFERENCES personas.funcionario(id),
    tipo              VARCHAR(30),
    monto             NUMERIC(18,2) NOT NULL DEFAULT 0,
    fecha             DATE,
    motivo            TEXT,
    es_recurrente     BOOLEAN NOT NULL DEFAULT FALSE,
    frecuencia        VARCHAR(20),
    anulado           BOOLEAN NOT NULL DEFAULT FALSE,
    liquidacion_id    BIGINT,
    autorizado_por_id BIGINT REFERENCES personas.usuario(id),
    usuario_id        BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bono_funcionario ON rrhh.bono(funcionario_id);
