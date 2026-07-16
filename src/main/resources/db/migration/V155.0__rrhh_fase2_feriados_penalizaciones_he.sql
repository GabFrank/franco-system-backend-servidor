-- =====================================================================
-- RRHH — Fase 2: Feriados, novedades de jornada, penalizaciones y HE
-- =====================================================================
-- Tablas nuevas en el schema rrhh. Todo aditivo. Las FK a la jornada
-- (jornada_id + sucursal_id) son planas porque administrativo.jornada
-- tiene PK compuesta (no se crea constraint FK sobre ellas).
-- Ninguna tabla se replica a filiales (gestion central-only).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. rrhh.feriado
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.feriado (
    id                 BIGSERIAL PRIMARY KEY,
    fecha              DATE NOT NULL UNIQUE,
    descripcion        VARCHAR(255),
    es_nacional        BOOLEAN NOT NULL DEFAULT TRUE,
    recargo_porcentaje NUMERIC(5,2) NOT NULL DEFAULT 100,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id         BIGINT REFERENCES personas.usuario(id),
    creado_en          TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 2. rrhh.jornada_novedad
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.jornada_novedad (
    id                BIGSERIAL PRIMARY KEY,
    funcionario_id    BIGINT REFERENCES personas.funcionario(id),
    fecha             DATE,
    tipo              VARCHAR(30),
    jornada_id        BIGINT,
    sucursal_id       BIGINT,
    observacion       TEXT,
    registrado_por_id BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_jornada_novedad_funcionario_fecha
    ON rrhh.jornada_novedad(funcionario_id, fecha);
CREATE INDEX IF NOT EXISTS idx_jornada_novedad_jornada
    ON rrhh.jornada_novedad(jornada_id, sucursal_id);

-- ---------------------------------------------------------------------
-- 3. rrhh.penalizacion
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.penalizacion (
    id                BIGSERIAL PRIMARY KEY,
    funcionario_id    BIGINT REFERENCES personas.funcionario(id),
    jornada_id        BIGINT,
    sucursal_id       BIGINT,
    tipo              VARCHAR(30),
    descripcion       TEXT,
    monto             NUMERIC(18,2) NOT NULL DEFAULT 0,
    fecha             DATE,
    auto_generada     BOOLEAN NOT NULL DEFAULT FALSE,
    anulada           BOOLEAN NOT NULL DEFAULT FALSE,
    registrado_por_id BIGINT REFERENCES personas.usuario(id),
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_penalizacion_funcionario_fecha
    ON rrhh.penalizacion(funcionario_id, fecha);
CREATE INDEX IF NOT EXISTS idx_penalizacion_jornada
    ON rrhh.penalizacion(jornada_id, sucursal_id);

-- ---------------------------------------------------------------------
-- 4. rrhh.hora_extra
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.hora_extra (
    id                 BIGSERIAL PRIMARY KEY,
    funcionario_id     BIGINT REFERENCES personas.funcionario(id),
    fecha              DATE,
    jornada_id         BIGINT,
    sucursal_id        BIGINT,
    minutos            NUMERIC(9,2) NOT NULL DEFAULT 0,
    tipo               VARCHAR(30),
    recargo_porcentaje NUMERIC(5,2),
    monto_calculado    NUMERIC(18,2) NOT NULL DEFAULT 0,
    origen             VARCHAR(30),
    anulada            BOOLEAN NOT NULL DEFAULT FALSE,
    autorizado_por_id  BIGINT REFERENCES personas.usuario(id),
    observacion        TEXT,
    creado_en          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_hora_extra_funcionario_fecha
    ON rrhh.hora_extra(funcionario_id, fecha);
CREATE INDEX IF NOT EXISTS idx_hora_extra_jornada
    ON rrhh.hora_extra(jornada_id, sucursal_id);

-- ---------------------------------------------------------------------
-- 5. Seed — feriados nacionales fijos de Paraguay (2026)
--    (los movibles como Semana Santa se cargan manualmente por anio)
-- ---------------------------------------------------------------------
INSERT INTO rrhh.feriado (fecha, descripcion, es_nacional, recargo_porcentaje, activo) VALUES
    (DATE '2026-01-01', 'ANIO NUEVO',                       TRUE, 100, TRUE),
    (DATE '2026-03-01', 'DIA DE LOS HEROES',                TRUE, 100, TRUE),
    (DATE '2026-05-01', 'DIA DEL TRABAJADOR',               TRUE, 100, TRUE),
    (DATE '2026-05-14', 'DIA DE LA INDEPENDENCIA',          TRUE, 100, TRUE),
    (DATE '2026-05-15', 'DIA DE LA INDEPENDENCIA',          TRUE, 100, TRUE),
    (DATE '2026-06-12', 'PAZ DEL CHACO',                    TRUE, 100, TRUE),
    (DATE '2026-08-15', 'FUNDACION DE ASUNCION',            TRUE, 100, TRUE),
    (DATE '2026-09-29', 'VICTORIA DE BOQUERON',             TRUE, 100, TRUE),
    (DATE '2026-12-08', 'DIA DE LA VIRGEN DE CAACUPE',      TRUE, 100, TRUE),
    (DATE '2026-12-25', 'NAVIDAD',                          TRUE, 100, TRUE)
ON CONFLICT (fecha) DO NOTHING;
