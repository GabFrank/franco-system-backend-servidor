-- =====================================================================
-- Tesorería / Caja Mayor — Fase 1: núcleo robusto del ledger
-- =====================================================================
-- Evoluciona el modelo de caja virtual al modelo cash-only por (caja, moneda):
--   - Tabla de saldo por (caja, moneda) como nueva fuente de verdad.
--   - Trazabilidad genérica origen_tipo/origen_id en el ledger (para el
--     bloqueo de anulación cross-módulo).
--   - Flag permite_saldo_negativo por caja (CN2).
--   - Backfill del saldo actual (columnas Gs/Rs/Ds, que quedan como shim
--     derivado por compatibilidad con RRHH y la UI actual) y del origen de
--     los movimientos ya posteados por RRHH.
--
-- Aditivo e idempotente. No elimina las columnas saldo_gs/rs/ds (siguen como
-- shim recalculado hasta que la UI se porte). No toca replicación (central-only).
-- =====================================================================

-- 1. Tabla de saldo por (caja, moneda) — fuente de verdad
CREATE TABLE IF NOT EXISTS financiero.caja_virtual_saldo (
    id                bigserial PRIMARY KEY,
    caja_virtual_id   bigint NOT NULL REFERENCES financiero.caja_virtual(id),
    moneda_id         bigint NOT NULL REFERENCES financiero.moneda(id),
    saldo             numeric(18,4) NOT NULL DEFAULT 0,
    creado_en         timestamp DEFAULT now(),
    CONSTRAINT uq_caja_virtual_saldo UNIQUE (caja_virtual_id, moneda_id)
);

-- 2. Trazabilidad de origen en el ledger (para anulación cross-módulo)
ALTER TABLE financiero.movimiento_caja_virtual ADD COLUMN IF NOT EXISTS origen_tipo varchar(40);
ALTER TABLE financiero.movimiento_caja_virtual ADD COLUMN IF NOT EXISTS origen_id   bigint;

-- 3. Control de descubierto por caja (CN2)
ALTER TABLE financiero.caja_virtual ADD COLUMN IF NOT EXISTS permite_saldo_negativo boolean DEFAULT false;

-- 4. Backfill del saldo actual (Gs/Rs/Ds) a la tabla nueva.
--    Se resuelve una moneda por denominación (la de menor id que matchee).
DO $$
DECLARE
    gs_id bigint := (SELECT id FROM financiero.moneda WHERE upper(denominacion) LIKE '%GUARAN%' ORDER BY id LIMIT 1);
    rs_id bigint := (SELECT id FROM financiero.moneda WHERE upper(denominacion) LIKE '%REAL%'   ORDER BY id LIMIT 1);
    ds_id bigint := (SELECT id FROM financiero.moneda WHERE upper(denominacion) LIKE '%DOLAR%' OR upper(denominacion) LIKE '%DÓLAR%' ORDER BY id LIMIT 1);
BEGIN
    IF gs_id IS NOT NULL THEN
        INSERT INTO financiero.caja_virtual_saldo (caja_virtual_id, moneda_id, saldo)
        SELECT cv.id, gs_id, COALESCE(cv.saldo_gs, 0) FROM financiero.caja_virtual cv
        ON CONFLICT (caja_virtual_id, moneda_id) DO NOTHING;
    END IF;
    IF rs_id IS NOT NULL THEN
        INSERT INTO financiero.caja_virtual_saldo (caja_virtual_id, moneda_id, saldo)
        SELECT cv.id, rs_id, COALESCE(cv.saldo_rs, 0) FROM financiero.caja_virtual cv
        ON CONFLICT (caja_virtual_id, moneda_id) DO NOTHING;
    END IF;
    IF ds_id IS NOT NULL THEN
        INSERT INTO financiero.caja_virtual_saldo (caja_virtual_id, moneda_id, saldo)
        SELECT cv.id, ds_id, COALESCE(cv.saldo_ds, 0) FROM financiero.caja_virtual cv
        ON CONFLICT (caja_virtual_id, moneda_id) DO NOTHING;
    END IF;
END $$;

-- 5. Backfill de origen_tipo/origen_id desde el FK inverso que las 5 entidades RRHH ya guardan.
UPDATE financiero.movimiento_caja_virtual m SET origen_tipo = 'RRHH_VALE', origen_id = v.id
    FROM rrhh.vale v WHERE v.movimiento_caja_virtual_id = m.id AND m.origen_tipo IS NULL;
UPDATE financiero.movimiento_caja_virtual m SET origen_tipo = 'RRHH_PRESTAMO', origen_id = p.id
    FROM rrhh.prestamo p WHERE p.movimiento_caja_virtual_id = m.id AND m.origen_tipo IS NULL;
UPDATE financiero.movimiento_caja_virtual m SET origen_tipo = 'RRHH_AGUINALDO', origen_id = a.id
    FROM rrhh.aguinaldo a WHERE a.movimiento_caja_virtual_id = m.id AND m.origen_tipo IS NULL;
UPDATE financiero.movimiento_caja_virtual m SET origen_tipo = 'RRHH_LIQUIDACION_SUELDO', origen_id = ls.id
    FROM rrhh.liquidacion_sueldo ls WHERE ls.movimiento_caja_virtual_id = m.id AND m.origen_tipo IS NULL;
UPDATE financiero.movimiento_caja_virtual m SET origen_tipo = 'RRHH_LIQUIDACION_FINAL', origen_id = lf.id
    FROM rrhh.liquidacion_final lf WHERE lf.movimiento_caja_virtual_id = m.id AND m.origen_tipo IS NULL;
