-- =====================================================================
-- RRHH — Fase 3: Vales y prestamos
-- =====================================================================
-- Tablas nuevas en el schema rrhh. Todo aditivo. El egreso/ingreso real
-- se registra contra financiero.caja_virtual (Caja Mayor, fd-93) via
-- FK plana caja_virtual_id / movimiento_caja_virtual_id, y la cuenta
-- corriente del empleado via financiero.movimiento_personas.
-- Central-only (no replicar a filiales).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. rrhh.motivo_vale
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.motivo_vale (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255),
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    usuario_id  BIGINT REFERENCES personas.usuario(id),
    creado_en   TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 2. rrhh.vale
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.vale (
    id                         BIGSERIAL PRIMARY KEY,
    funcionario_id             BIGINT REFERENCES personas.funcionario(id),
    motivo_id                  BIGINT REFERENCES rrhh.motivo_vale(id),
    monto                      NUMERIC(18,2) NOT NULL DEFAULT 0,
    moneda_id                  BIGINT REFERENCES financiero.moneda(id),
    fecha                      DATE,
    estado                     VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO',
    es_adelanto                BOOLEAN NOT NULL DEFAULT FALSE,
    liquidacion_id             BIGINT,
    caja_virtual_id            BIGINT REFERENCES financiero.caja_virtual(id),
    movimiento_caja_virtual_id BIGINT,
    autorizado_por_id          BIGINT REFERENCES personas.usuario(id),
    observacion                TEXT,
    comprobante_url            VARCHAR(500),
    usuario_id                 BIGINT REFERENCES personas.usuario(id),
    creado_en                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vale_funcionario ON rrhh.vale(funcionario_id);
CREATE INDEX IF NOT EXISTS idx_vale_estado ON rrhh.vale(estado);

-- ---------------------------------------------------------------------
-- 3. rrhh.prestamo
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.prestamo (
    id                         BIGSERIAL PRIMARY KEY,
    funcionario_id             BIGINT REFERENCES personas.funcionario(id),
    descripcion                VARCHAR(255),
    monto_total                NUMERIC(18,2) NOT NULL DEFAULT 0,
    monto_pagado               NUMERIC(18,2) NOT NULL DEFAULT 0,
    moneda_id                  BIGINT REFERENCES financiero.moneda(id),
    fecha_inicio               DATE,
    cantidad_cuotas            INTEGER NOT NULL DEFAULT 1,
    estado                     VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    observacion                TEXT,
    caja_virtual_id            BIGINT REFERENCES financiero.caja_virtual(id),
    movimiento_caja_virtual_id BIGINT,
    usuario_id                 BIGINT REFERENCES personas.usuario(id),
    creado_en                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prestamo_funcionario ON rrhh.prestamo(funcionario_id);
CREATE INDEX IF NOT EXISTS idx_prestamo_estado ON rrhh.prestamo(estado);

-- ---------------------------------------------------------------------
-- 4. rrhh.prestamo_cuota
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rrhh.prestamo_cuota (
    id                BIGSERIAL PRIMARY KEY,
    prestamo_id       BIGINT REFERENCES rrhh.prestamo(id),
    numero            INTEGER NOT NULL,
    fecha_vencimiento DATE,
    monto             NUMERIC(18,2) NOT NULL DEFAULT 0,
    monto_pagado      NUMERIC(18,2) NOT NULL DEFAULT 0,
    estado            VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_pago        DATE,
    creado_en         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prestamo_cuota_prestamo ON rrhh.prestamo_cuota(prestamo_id);
CREATE INDEX IF NOT EXISTS idx_prestamo_cuota_estado_venc ON rrhh.prestamo_cuota(estado, fecha_vencimiento);

-- ---------------------------------------------------------------------
-- 5. Seed — motivos de vale
-- ---------------------------------------------------------------------
INSERT INTO rrhh.motivo_vale (nombre, descripcion, activo) VALUES
    ('ANTICIPO SUELDO', 'ADELANTO A CUENTA DEL SALARIO', TRUE),
    ('EMERGENCIA MEDICA', 'VALE POR EMERGENCIA MEDICA', TRUE),
    ('GASTO PERSONAL', 'VALE POR GASTO PERSONAL', TRUE),
    ('OTRO', 'OTRO MOTIVO', TRUE)
ON CONFLICT DO NOTHING;
