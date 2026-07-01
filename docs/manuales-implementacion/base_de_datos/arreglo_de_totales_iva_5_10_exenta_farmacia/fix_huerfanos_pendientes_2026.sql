\timing on
\set ON_ERROR_STOP on
BEGIN;

DROP TABLE IF EXISTS tmp_override2;
CREATE TEMP TABLE tmp_override2(dn text PRIMARY KEY, iva int);
INSERT INTO tmp_override2 VALUES
  ('RETRATUTIDA VELTRANE 90 MG',                0),
  ('RETATUTIDE VELTRANE 60 MG',                 0),
  ('RETRATUTIDA VELTRANE 120 MG',               0),
  ('NIVEA FACIAL 5 EN UNO ANTI-ARRUGAS',       10),
  ('BABELITO ROSA SET 3 EN 1 ANTIDERRAPANTE',  10);

-- Update items NULL abril+mayo que matchean
DROP TABLE IF EXISTS tmp_cand2;
CREATE TEMP TABLE tmp_cand2 AS
SELECT fli.id AS item_id, fli.sucursal_id, fli.factura_legal_id, ov.iva AS iva_resuelto
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
JOIN tmp_override2 ov ON ov.dn = UPPER(TRIM(fli.descripcion))
WHERE fli.iva IS NULL
  AND fl.fecha >= '2026-04-01' AND fl.fecha < '2026-06-01';

SELECT 'candidatos' AS k, COUNT(*) FROM tmp_cand2;

UPDATE financiero.factura_legal_item fli
SET iva = c.iva_resuelto
FROM tmp_cand2 c
WHERE fli.id = c.item_id AND fli.sucursal_id = c.sucursal_id;

-- Recalcular parciales solo para facturas afectadas
WITH afectadas AS (
  SELECT DISTINCT factura_legal_id AS fl_id, sucursal_id AS suc FROM tmp_cand2
),
items_por_iva AS (
    SELECT fli.factura_legal_id AS fl_id, fli.sucursal_id AS suc,
        SUM(CASE WHEN COALESCE(fli.iva,0)=0  THEN fli.total ELSE 0 END) AS s0,
        SUM(CASE WHEN COALESCE(fli.iva,0)=5  THEN fli.total ELSE 0 END) AS s5,
        SUM(CASE WHEN COALESCE(fli.iva,0)=10 THEN fli.total ELSE 0 END) AS s10,
        SUM(fli.total) AS stot
    FROM financiero.factura_legal_item fli
    JOIN afectadas a ON a.fl_id=fli.factura_legal_id AND a.suc=fli.sucursal_id
    GROUP BY fli.factura_legal_id, fli.sucursal_id
),
calc AS (
    SELECT fl.id, fl.sucursal_id,
        CASE WHEN COALESCE(i.stot,0)>0 AND COALESCE(fl.descuento,0)>0
             THEN ROUND(COALESCE(i.s0,0)  * (1 - (fl.descuento / i.stot)), 2)
             ELSE COALESCE(i.s0,0) END AS np0,
        CASE WHEN COALESCE(i.stot,0)>0 AND COALESCE(fl.descuento,0)>0
             THEN ROUND(COALESCE(i.s5,0)  * (1 - (fl.descuento / i.stot)), 2)
             ELSE COALESCE(i.s5,0) END AS np5,
        CASE WHEN COALESCE(i.stot,0)>0 AND COALESCE(fl.descuento,0)>0
             THEN ROUND(COALESCE(i.s10,0) * (1 - (fl.descuento / i.stot)), 2)
             ELSE COALESCE(i.s10,0) END AS np10
    FROM financiero.factura_legal fl
    JOIN afectadas a ON a.fl_id=fl.id AND a.suc=fl.sucursal_id
    LEFT JOIN items_por_iva i ON i.fl_id=fl.id AND i.suc=fl.sucursal_id
)
UPDATE financiero.factura_legal fl
SET total_parcial_0  = c.np0,
    total_parcial_5  = c.np5,
    total_parcial_10 = c.np10,
    iva_parcial_5    = ROUND(c.np5  / 21.0, 2),
    iva_parcial_10   = ROUND(c.np10 / 11.0, 2),
    total_final      = ROUND(c.np0 + c.np5 + c.np10, 2)
FROM calc c
WHERE fl.id = c.id AND fl.sucursal_id = c.sucursal_id;

-- Verify
SELECT 'items NULL post abril+mayo' AS k,
       COUNT(*) FILTER (WHERE fli.iva IS NULL) AS aun_null
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
WHERE fl.fecha >= '2026-04-01' AND fl.fecha < '2026-06-01';

COMMIT;
