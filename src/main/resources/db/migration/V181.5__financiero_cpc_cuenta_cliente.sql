-- =====================================================================
-- Tesorería — Fase 5: CPC (cobros a clientes) + cuenta corriente de cliente
-- =====================================================================
-- Doble ledger: al cobrar una cuota de venta a crédito se postea el ingreso
-- (banco, por DA8 el efectivo es exclusivo del POS filial) Y un movimiento en
-- el libro del cliente (MovimientoCliente), y baja Cliente.saldo_actual.
-- venta_credito_cuota gana monto_cobrado (cobro parcial nativo).
-- Aditivo e idempotente. Tablas venta_credito* son BRANCH_TO_MAIN (replicadas).
-- =====================================================================

-- 1. Cuenta corriente del cliente
ALTER TABLE personas.cliente ADD COLUMN IF NOT EXISTS saldo_actual numeric(18,4) DEFAULT 0;

-- 2. Cobro parcial en la cuota
ALTER TABLE financiero.venta_credito_cuota ADD COLUMN IF NOT EXISTS monto_cobrado numeric(18,4) DEFAULT 0;
ALTER TABLE financiero.venta_credito_cuota ADD COLUMN IF NOT EXISTS estado_cobro varchar(20);  -- PENDIENTE | PARCIAL | COBRADO | CANCELADO

-- 3. Libro de movimientos del cliente (ledger de tercero)
CREATE TABLE IF NOT EXISTS financiero.movimiento_cliente (
    id                        bigserial PRIMARY KEY,
    cliente_id                bigint NOT NULL REFERENCES personas.cliente(id),
    tipo                      varchar(20) NOT NULL,   -- CARGO | PAGO | AJUSTE_POSITIVO | AJUSTE_NEGATIVO
    monto                     numeric(18,4) NOT NULL,
    saldo_anterior            numeric(18,4),
    saldo_posterior           numeric(18,4),
    venta_credito_id          bigint,
    venta_credito_cuota_id    bigint,
    movimiento_caja_virtual_id bigint,
    movimiento_bancario_id    bigint,
    moneda_id                 bigint REFERENCES financiero.moneda(id),
    cotizacion                numeric(18,6),
    descripcion               varchar(300),
    usuario_id                bigint,
    anulado                   boolean DEFAULT false,
    creado_en                 timestamp DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_movimiento_cliente_cliente ON financiero.movimiento_cliente (cliente_id);
