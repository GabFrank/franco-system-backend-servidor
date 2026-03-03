-- V108: Create solicitud_pago_detalle table for multiple payment methods per solicitud
-- Refactor: one row per payment form (moneda, forma pago, valor, fecha, etc.)

CREATE TABLE IF NOT EXISTS operaciones.solicitud_pago_detalle (
    id BIGSERIAL PRIMARY KEY,
    solicitud_pago_id BIGINT NOT NULL,
    moneda_id BIGINT NOT NULL,
    forma_pago_id BIGINT NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    fecha_pago DATE,
    observacion TEXT,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    orden INTEGER,
    cotizacion DECIMAL(15,4),
    fecha_emision_cheque DATE,
    portador VARCHAR(255),
    nominal BOOLEAN DEFAULT true,
    diferido BOOLEAN DEFAULT true,

    CONSTRAINT fk_solicitud_pago_detalle_solicitud FOREIGN KEY (solicitud_pago_id)
        REFERENCES operaciones.solicitud_pago(id) ON DELETE CASCADE,
    CONSTRAINT fk_solicitud_pago_detalle_moneda FOREIGN KEY (moneda_id)
        REFERENCES financiero.moneda(id) ON DELETE RESTRICT,
    CONSTRAINT fk_solicitud_pago_detalle_forma_pago FOREIGN KEY (forma_pago_id)
        REFERENCES financiero.forma_pago(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_solicitud_pago_detalle_solicitud ON operaciones.solicitud_pago_detalle(solicitud_pago_id);
CREATE INDEX IF NOT EXISTS idx_solicitud_pago_detalle_forma_pago ON operaciones.solicitud_pago_detalle(forma_pago_id);
CREATE INDEX IF NOT EXISTS idx_solicitud_pago_detalle_moneda ON operaciones.solicitud_pago_detalle(moneda_id);

-- Migrate existing data: one detalle per solicitud_pago (moneda_id, monto_total, forma_pago or default EFECTIVO, fecha_pago_propuesta)
INSERT INTO operaciones.solicitud_pago_detalle (
    solicitud_pago_id,
    moneda_id,
    forma_pago_id,
    valor,
    fecha_pago,
    creado_en,
    orden
)
SELECT
    sp.id,
    sp.moneda_id,
    COALESCE(sp.forma_pago_id, (SELECT id FROM financiero.forma_pago ORDER BY id LIMIT 1)),
    sp.monto_total,
    sp.fecha_pago_propuesta::DATE,
    COALESCE(sp.creado_en, CURRENT_TIMESTAMP),
    0
FROM operaciones.solicitud_pago sp
WHERE sp.moneda_id IS NOT NULL AND sp.monto_total IS NOT NULL;
