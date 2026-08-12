-- =====================================================================
-- Tesorería — Fase 6: CPP (pagos a proveedores) + cuenta corriente proveedor
-- =====================================================================
-- SolicitudPago actúa como CPP (central-only, no replicada). El pago posta el
-- egreso (caja mayor o banco; cheque llega en F7) + un movimiento en el libro
-- del proveedor, y transiciona la solicitud PENDIENTE→PARCIAL→CONCLUIDO.
-- Soporta pago mixto (varias líneas moneda/fuente por pago).
-- Aditivo e idempotente.
-- =====================================================================

-- 1. Cuenta corriente del proveedor
ALTER TABLE personas.proveedor ADD COLUMN IF NOT EXISTS saldo_actual numeric(18,4) DEFAULT 0;

-- 2. Monto pagado acumulado en la solicitud (pago parcial)
ALTER TABLE operaciones.solicitud_pago ADD COLUMN IF NOT EXISTS monto_pagado numeric(18,4) DEFAULT 0;

-- 3. Libro de movimientos del proveedor
CREATE TABLE IF NOT EXISTS financiero.movimiento_proveedor (
    id                        bigserial PRIMARY KEY,
    proveedor_id              bigint NOT NULL REFERENCES personas.proveedor(id),
    tipo                      varchar(20) NOT NULL,   -- CARGO | PAGO | AJUSTE_POSITIVO | AJUSTE_NEGATIVO
    monto                     numeric(18,4) NOT NULL,
    saldo_anterior            numeric(18,4),
    saldo_posterior           numeric(18,4),
    solicitud_pago_id         bigint,
    movimiento_caja_virtual_id bigint,
    movimiento_bancario_id    bigint,
    moneda_id                 bigint REFERENCES financiero.moneda(id),
    cotizacion                numeric(18,6),
    descripcion               varchar(300),
    usuario_id                bigint,
    anulado                   boolean DEFAULT false,
    creado_en                 timestamp DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_movimiento_proveedor_proveedor ON financiero.movimiento_proveedor (proveedor_id);

-- 4. Detalle de pago (para pago mixto: varias líneas por solicitud)
CREATE TABLE IF NOT EXISTS financiero.pago_solicitud_detalle (
    id                        bigserial PRIMARY KEY,
    solicitud_pago_id         bigint NOT NULL REFERENCES operaciones.solicitud_pago(id),
    fuente                    varchar(20) NOT NULL,   -- CAJA_MAYOR | CUENTA_BANCARIA | CHEQUE
    caja_virtual_id           bigint REFERENCES financiero.caja_virtual(id),
    cuenta_bancaria_id        bigint REFERENCES financiero.cuenta_bancaria(id),
    moneda_id                 bigint REFERENCES financiero.moneda(id),
    monto                     numeric(18,4) NOT NULL,       -- monto de la línea en su moneda
    cotizacion                numeric(18,6),
    monto_solicitud           numeric(18,4),                -- monto convertido a la moneda de la solicitud
    movimiento_caja_virtual_id bigint,
    movimiento_bancario_id    bigint,
    usuario_id                bigint,
    anulado                   boolean DEFAULT false,
    creado_en                 timestamp DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_pago_solicitud_detalle_solicitud ON financiero.pago_solicitud_detalle (solicitud_pago_id);
