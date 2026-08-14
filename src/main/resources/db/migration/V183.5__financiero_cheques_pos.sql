-- =====================================================================
-- Tesorería — Fase 7: cheques + acreditación POS
-- =====================================================================
-- Cheque diferido reserva saldo de la cuenta; contado la debita y queda cobrado.
-- Acreditación POS: cada venta con tarjeta genera una acreditación pendiente que
-- un scheduler acredita a la cuenta bancaria al vencer los minutos configurados.
-- Aditivo e idempotente.
-- =====================================================================

-- 1. Cheque — estado + vínculos
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS estado             varchar(20);   -- EMITIDO | DIFERIDO | COBRADO | ANULADO
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS moneda_id          bigint REFERENCES financiero.moneda(id);
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS cuenta_bancaria_id bigint REFERENCES financiero.cuenta_bancaria(id);
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS fecha_cobro        timestamp;
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS motivo_anulacion   varchar(200);
ALTER TABLE financiero.cheque ADD COLUMN IF NOT EXISTS movimiento_bancario_id bigint;

-- 2. Chequera — numeración incremental + estado
ALTER TABLE financiero.chequera ADD COLUMN IF NOT EXISTS nombre           varchar(80);
ALTER TABLE financiero.chequera ADD COLUMN IF NOT EXISTS siguiente_numero bigint;
ALTER TABLE financiero.chequera ADD COLUMN IF NOT EXISTS estado           varchar(20) DEFAULT 'ACTIVA';   -- ACTIVA | AGOTADA | ANULADA
-- sembrar siguiente_numero con rango_desde donde falte
UPDATE financiero.chequera SET siguiente_numero = COALESCE(CAST(rango_desde AS bigint), 1)
    WHERE siguiente_numero IS NULL;

-- 3. Terminal POS (≈ MaquinaPos): comisión + minutos de acreditación
ALTER TABLE financiero.terminal_pos ADD COLUMN IF NOT EXISTS porcentaje_comision  numeric(6,3) DEFAULT 0;
ALTER TABLE financiero.terminal_pos ADD COLUMN IF NOT EXISTS minutos_acreditacion integer DEFAULT 0;

-- 4. Acreditación POS
CREATE TABLE IF NOT EXISTS financiero.acreditacion_pos (
    id                          bigserial PRIMARY KEY,
    terminal_pos_id             bigint REFERENCES financiero.terminal_pos(id),
    cuenta_bancaria_id          bigint REFERENCES financiero.cuenta_bancaria(id),
    monto_original              numeric(18,4) NOT NULL,
    monto_comision              numeric(18,4),
    monto_esperado              numeric(18,4),
    monto_acreditado            numeric(18,4),
    fecha_transaccion           timestamp DEFAULT now(),
    fecha_esperada_acreditacion timestamp,
    fecha_acreditacion_real     timestamp,
    estado                      varchar(20) DEFAULT 'PENDIENTE',   -- PENDIENTE | ACREDITADO_AUTO | VERIFICADO | CON_DIFERENCIA
    diferencia                  numeric(18,4),
    venta_id                    bigint,
    movimiento_bancario_id      bigint,
    usuario_id                  bigint,
    creado_en                   timestamp DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_acreditacion_pos_pendiente
    ON financiero.acreditacion_pos (fecha_esperada_acreditacion)
    WHERE estado = 'PENDIENTE';
