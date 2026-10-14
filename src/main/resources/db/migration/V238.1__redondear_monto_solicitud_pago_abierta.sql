-- Una solicitud de pago no puede deber mas decimales que su moneda: en guaranies, una deuda de
-- 899854.5 no se salda con ningun pago (el motor rechaza medio guarani de exceso). El codigo ya
-- redondea al crear; esto normaliza las solicitudes de compra abiertas y sin pagos que nacieron antes.
-- No toca GASTO ni RRHH (su monto es el del documento de origen) ni las que ya tienen pagos imputados.

CREATE TEMP TABLE tmp_sp_redondeo ON COMMIT DROP AS
SELECT s.id,
       s.monto_total,
       CASE WHEN m.decimales IS NOT NULL THEN m.decimales
            WHEN upper(m.denominacion) = 'GUARANI' THEN 0
            ELSE 2 END AS decimales,
       x.suma,
       x.notas
FROM operaciones.solicitud_pago s
JOIN financiero.moneda m ON m.id = s.moneda_id
LEFT JOIN (SELECT solicitud_pago_id, SUM(monto_incluido) AS suma, COUNT(*) AS notas
           FROM operaciones.solicitud_pago_nota_recepcion
           GROUP BY solicitud_pago_id) x ON x.solicitud_pago_id = s.id
WHERE s.tipo = 'COMPRA'
  AND s.estado IN ('PENDIENTE', 'SOLICITADO', 'DEVUELTO')
  AND COALESCE(s.monto_pagado, 0) = 0;

-- Quedan solo las que tienen algo que redondear, en el total o en alguna nota.
DELETE FROM tmp_sp_redondeo t
WHERE t.monto_total = round(t.monto_total, t.decimales)
  AND NOT EXISTS (SELECT 1 FROM operaciones.solicitud_pago_nota_recepcion r
                  WHERE r.solicitud_pago_id = t.id
                    AND r.monto_incluido <> round(r.monto_incluido, t.decimales));

-- Si el total no es la suma de sus notas, la diferencia no es de redondeo: no se toca a ciegas.
DO $$
DECLARE
    v_ids text;
BEGIN
    SELECT string_agg(id::text, ', ' ORDER BY id) INTO v_ids
    FROM tmp_sp_redondeo
    WHERE notas IS NOT NULL AND abs(suma - monto_total) >= 1;
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'V238.1: solicitudes de pago con total distinto de la suma de sus notas: %', v_ids;
    END IF;
END $$;

UPDATE operaciones.solicitud_pago_nota_recepcion r
SET monto_incluido = round(r.monto_incluido, t.decimales)
FROM tmp_sp_redondeo t
WHERE r.solicitud_pago_id = t.id
  AND r.monto_incluido <> round(r.monto_incluido, t.decimales);

UPDATE operaciones.solicitud_pago s
SET monto_total = COALESCE((SELECT SUM(r.monto_incluido)
                            FROM operaciones.solicitud_pago_nota_recepcion r
                            WHERE r.solicitud_pago_id = s.id),
                           round(t.monto_total, t.decimales))
FROM tmp_sp_redondeo t
WHERE s.id = t.id;
