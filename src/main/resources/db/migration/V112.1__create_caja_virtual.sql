-- Enum tipo de caja virtual
CREATE TYPE financiero.caja_virtual_tipo AS ENUM ('CAJA_MAYOR', 'CAJA_CHICA');

-- Enum tipo de movimiento de caja virtual
CREATE TYPE financiero.caja_virtual_tipo_movimiento AS ENUM (
    'INGRESO',
    'EGRESO',
    'TRANSFERENCIA_ENTRADA',
    'TRANSFERENCIA_SALIDA',
    'PAGO_PROVEEDOR',
    'AJUSTE'
);

-- Tabla principal de cajas virtuales (Mayor / Chica)
CREATE TABLE financiero.caja_virtual (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    tipo            financiero.caja_virtual_tipo NOT NULL,
    sucursal_id     BIGINT REFERENCES empresarial.sucursal(id),
    responsable_id  BIGINT REFERENCES personas.funcionario(id),
    usuario_id      BIGINT REFERENCES personas.usuario(id),
    saldo_gs        DOUBLE PRECISION NOT NULL DEFAULT 0,
    saldo_rs        DOUBLE PRECISION NOT NULL DEFAULT 0,
    saldo_ds        DOUBLE PRECISION NOT NULL DEFAULT 0,
    limite_gs       DOUBLE PRECISION,
    descripcion     TEXT,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP NOT NULL DEFAULT now()
);

-- Tabla de movimientos de cajas virtuales
CREATE TABLE financiero.movimiento_caja_virtual (
    id              BIGSERIAL PRIMARY KEY,
    caja_virtual_id BIGINT NOT NULL REFERENCES financiero.caja_virtual(id),
    tipo_movimiento financiero.caja_virtual_tipo_movimiento NOT NULL,
    cantidad        DOUBLE PRECISION NOT NULL,
    moneda_id       BIGINT REFERENCES financiero.moneda(id),
    referencia_id   BIGINT,
    descripcion     TEXT,
    usuario_id      BIGINT REFERENCES personas.usuario(id),
    caja_origen_id  BIGINT REFERENCES financiero.caja_virtual(id),
    caja_destino_id BIGINT REFERENCES financiero.caja_virtual(id),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP NOT NULL DEFAULT now()
);

-- Índices para búsquedas frecuentes
CREATE INDEX idx_caja_virtual_sucursal ON financiero.caja_virtual(sucursal_id);
CREATE INDEX idx_caja_virtual_tipo ON financiero.caja_virtual(tipo);
CREATE INDEX idx_mov_caja_virtual_caja ON financiero.movimiento_caja_virtual(caja_virtual_id);
CREATE INDEX idx_mov_caja_virtual_fecha ON financiero.movimiento_caja_virtual(creado_en);
