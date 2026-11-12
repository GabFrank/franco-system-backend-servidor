-- Cotizacion de los cambios de divisa sin ambiguedad (issue #603).
--
-- operacion_financiera.cotizacion guardaba a veces «destino por 1 origen» y a veces lo inverso (en bodega,
-- REAL->DOLAR: 0,1825 y 5,60). Desde ahora es siempre «unidades de la moneda menor por 1 de la mayor» y
-- cotizacion_moneda_id dice cual vale 1.
--
-- Backfill de los CAMBIO_DIVISA existentes, con la misma regla que CotizacionNormalizada.java:
--   * la moneda mayor es la de monto menor (los montos son los reales: la diferencia es un ajuste aparte);
--   * si la cotizacion guardada cierra con los montos (+-1 %) se conserva;
--   * si cierra invertida, se invierte;
--   * si no cierra de ninguna forma (o era NULL), se usa la de los montos.
-- El valor anterior queda en cotizacion_original solo si cambio. No toca montos ni saldos.
--
-- Aditiva, idempotente (solo filas sin cotizacion_moneda_id) y reversible:
--   UPDATE financiero.operacion_financiera SET cotizacion = cotizacion_original
--    WHERE cotizacion_original IS NOT NULL;
-- Central-only: operacion_financiera no esta en ninguna publicacion.

ALTER TABLE financiero.operacion_financiera
    ADD COLUMN IF NOT EXISTS cotizacion_moneda_id bigint REFERENCES financiero.moneda(id);
ALTER TABLE financiero.operacion_financiera
    ADD COLUMN IF NOT EXISTS cotizacion_original numeric(18,6);

COMMENT ON COLUMN financiero.operacion_financiera.cotizacion_moneda_id IS
    'Moneda que vale 1 en la cotizacion (la de mayor valor del par). Solo en CAMBIO_DIVISA (V276.5, issue #603).';
COMMENT ON COLUMN financiero.operacion_financiera.cotizacion_original IS
    'Cotizacion anterior a normalizarla a «menor por 1 mayor», si cambio (V276.5, issue #603). Solo auditoria.';

DO $$
DECLARE
    a_normalizar integer;
    normalizadas integer;
BEGIN
    SELECT count(*) INTO a_normalizar
      FROM financiero.operacion_financiera
     WHERE tipo_operacion = 'CAMBIO_DIVISA'
       AND cotizacion_moneda_id IS NULL
       AND monto_origen > 0 AND monto_destino > 0;

    WITH base AS (
        SELECT id,
               cotizacion AS c,
               monto_origen <= monto_destino AS mayor_es_origen,
               greatest(monto_origen, monto_destino) / least(monto_origen, monto_destino) AS r,
               moneda_origen_id, moneda_destino_id
          FROM financiero.operacion_financiera
         WHERE tipo_operacion = 'CAMBIO_DIVISA'
           AND cotizacion_moneda_id IS NULL
           AND monto_origen > 0 AND monto_destino > 0
    ), calc AS (
        SELECT id, c,
               CASE WHEN mayor_es_origen THEN moneda_origen_id ELSE moneda_destino_id END AS mayor_id,
               CASE
                   WHEN c > 0 AND abs(c - r) <= r * 0.01           THEN round(c, 6)
                   WHEN c > 0 AND abs(1 / c - r) <= r * 0.01       THEN round(1 / c, 6)
                   ELSE round(r, 6)
               END AS nueva
          FROM base
    )
    UPDATE financiero.operacion_financiera o
       SET cotizacion_original  = CASE WHEN calc.c IS DISTINCT FROM calc.nueva THEN calc.c END,
           cotizacion           = calc.nueva,
           cotizacion_moneda_id = calc.mayor_id
      FROM calc
     WHERE o.id = calc.id;
    GET DIAGNOSTICS normalizadas = ROW_COUNT;

    IF normalizadas <> a_normalizar THEN
        RAISE EXCEPTION 'V276.5: se esperaban % cambios de divisa y se normalizaron %', a_normalizar, normalizadas;
    END IF;

    RAISE NOTICE 'V276.5: % cambios de divisa normalizados (% con cotizacion cambiada)', normalizadas,
        (SELECT count(*) FROM financiero.operacion_financiera WHERE cotizacion_original IS NOT NULL);
END $$;
