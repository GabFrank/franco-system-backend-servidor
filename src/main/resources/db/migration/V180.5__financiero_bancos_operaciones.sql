-- =====================================================================
-- Tesorería — Fase 4: cuentas bancarias (ledger propio) + operaciones financieras
-- =====================================================================
-- La cuenta bancaria es un ledger de primer nivel, independiente de la caja mayor
-- (efectivo). Forma de pago = router: efectivo → caja mayor, no-efectivo → banco.
-- Operación financiera: cambio de divisa, depósito/retiro banco, transferencia
-- entre cajas mayor, transferencia bancaria (banco→banco, no toca caja).
-- Aditivo e idempotente.
-- =====================================================================

-- 1. Cuenta bancaria — saldos y datos
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS saldo           numeric(18,4) DEFAULT 0;
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS saldo_reservado numeric(18,4) DEFAULT 0;
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS titular         varchar(150);
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS alias           varchar(100);
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS activo          boolean DEFAULT true;
ALTER TABLE financiero.cuenta_bancaria ADD COLUMN IF NOT EXISTS permite_saldo_negativo boolean DEFAULT false;

-- 2. Movimiento bancario (ledger de la cuenta)
CREATE TABLE IF NOT EXISTS financiero.movimiento_bancario (
    id                  bigserial PRIMARY KEY,
    cuenta_bancaria_id  bigint NOT NULL REFERENCES financiero.cuenta_bancaria(id),
    tipo_movimiento     varchar(30) NOT NULL,   -- ENTRADA_MANUAL | SALIDA_MANUAL | AJUSTE_POSITIVO | AJUSTE_NEGATIVO | ACREDITACION_POS
    monto               numeric(18,4) NOT NULL,
    saldo_anterior      numeric(18,4),
    saldo_posterior     numeric(18,4),
    descripcion         varchar(300),
    numero_comprobante  varchar(60),
    origen_tipo         varchar(40),
    origen_id           bigint,
    usuario_id          bigint,
    anulado             boolean DEFAULT false,
    creado_en           timestamp DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_movimiento_bancario_cuenta ON financiero.movimiento_bancario (cuenta_bancaria_id);

-- 3. Categorías de operación financiera (árbol)
CREATE TABLE IF NOT EXISTS financiero.operacion_financiera_categoria (
    id         bigserial PRIMARY KEY,
    nombre     varchar(80) NOT NULL,
    padre_id   bigint REFERENCES financiero.operacion_financiera_categoria(id),
    icono      varchar(60),
    activo     boolean DEFAULT true,
    creado_en  timestamp DEFAULT now()
);

-- 4. Operación financiera (cambio divisa, depósito/retiro banco, transferencias)
CREATE TABLE IF NOT EXISTS financiero.operacion_financiera (
    id                        bigserial PRIMARY KEY,
    tipo_operacion            varchar(40) NOT NULL,   -- CAMBIO_DIVISA | DEPOSITO_BANCARIO | RETIRO_BANCARIO | TRANSFERENCIA_ENTRE_CAJAS | TRANSFERENCIA_BANCARIA
    categoria_id              bigint REFERENCES financiero.operacion_financiera_categoria(id),
    descripcion               varchar(300),
    -- Origen
    caja_mayor_origen_id      bigint REFERENCES financiero.caja_virtual(id),
    cuenta_bancaria_origen_id bigint REFERENCES financiero.cuenta_bancaria(id),
    moneda_origen_id          bigint REFERENCES financiero.moneda(id),
    monto_origen              numeric(18,4),
    -- Destino
    caja_mayor_destino_id      bigint REFERENCES financiero.caja_virtual(id),
    cuenta_bancaria_destino_id bigint REFERENCES financiero.cuenta_bancaria(id),
    moneda_destino_id          bigint REFERENCES financiero.moneda(id),
    monto_destino              numeric(18,4),
    cotizacion                 numeric(18,6),
    numero_comprobante         varchar(60),
    usuario_id                 bigint,
    anulado                    boolean DEFAULT false,
    creado_en                  timestamp DEFAULT now()
);
