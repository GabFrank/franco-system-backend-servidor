-- FIX IVA abril + mayo 2026 farmacia
\timing on
\set ON_ERROR_STOP on

BEGIN;

-- BACKUP por mes
DROP TABLE IF EXISTS public.backup_fl_abrilmayo_20260528;
DROP TABLE IF EXISTS public.backup_fli_abrilmayo_20260528;

CREATE TABLE public.backup_fl_abrilmayo_20260528 AS
SELECT * FROM financiero.factura_legal
WHERE fecha >= '2026-04-01 00:00:00' AND fecha < '2026-06-01 00:00:00';

CREATE TABLE public.backup_fli_abrilmayo_20260528 AS
SELECT fli.* FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
WHERE fl.fecha >= '2026-04-01 00:00:00' AND fl.fecha < '2026-06-01 00:00:00';

SELECT 'BACKUP fl' AS k, COUNT(*) FROM public.backup_fl_abrilmayo_20260528
UNION ALL SELECT 'BACKUP fli', COUNT(*) FROM public.backup_fli_abrilmayo_20260528;

-- Mapping desc->iva con override
DROP TABLE IF EXISTS tmp_da;
CREATE TEMP TABLE tmp_da AS
SELECT UPPER(TRIM(descripcion)) AS dn FROM productos.producto GROUP BY 1 HAVING COUNT(DISTINCT iva)>1
UNION SELECT UPPER(TRIM(descripcion_factura)) FROM productos.producto WHERE descripcion_factura IS NOT NULL AND TRIM(descripcion_factura)<>'' GROUP BY 1 HAVING COUNT(DISTINCT iva)>1;

DROP TABLE IF EXISTS tmp_di;
CREATE TEMP TABLE tmp_di AS
WITH u AS (SELECT UPPER(TRIM(descripcion)) AS dn, iva::int FROM productos.producto WHERE descripcion IS NOT NULL AND TRIM(descripcion)<>''
           UNION SELECT UPPER(TRIM(descripcion_factura)), iva::int FROM productos.producto WHERE descripcion_factura IS NOT NULL AND TRIM(descripcion_factura)<>'')
SELECT dn, MIN(iva) iva FROM u WHERE dn NOT IN (SELECT dn FROM tmp_da) GROUP BY dn;

DROP TABLE IF EXISTS tmp_override;
CREATE TEMP TABLE tmp_override(dn text PRIMARY KEY, iva int);
INSERT INTO tmp_override VALUES
  ('TIRZEC 15MG',0),('TG 15  MG NUEVA PRESENTACION',0),('RETRATUTIDA SYNEDICA VERDE',0),
  ('TIRZEDRAL 15 MG',0),('GHK-CU AMPOLLA',0),('GLOW GHK-CU',0),
  ('TESTENAT DEPOT 250 MG 10ML GOLD',5),('OXANDROLANA 5 MG X 100 COM',5),
  ('ADROLIC MESTEROLONA 25 MG X 20 COMP',5),('PROTIUM-T X 30 CAPSULAS',5),
  ('MELATONIN 5MG',10);
DELETE FROM tmp_di WHERE dn IN (SELECT dn FROM tmp_override);
INSERT INTO tmp_di SELECT dn, iva FROM tmp_override;
CREATE INDEX ON tmp_di(dn);
ANALYZE tmp_di;

-- PASO 1: UPDATE factura_legal_item.iva
DROP TABLE IF EXISTS tmp_candidatos;
CREATE TEMP TABLE tmp_candidatos AS
SELECT fli.id AS item_id, fli.sucursal_id, di.iva AS iva_resuelto
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
JOIN tmp_di di ON di.dn = UPPER(TRIM(fli.descripcion))
WHERE fl.fecha >= '2026-04-01 00:00:00' AND fl.fecha < '2026-06-01 00:00:00'
  AND fli.iva IS DISTINCT FROM di.iva;

SELECT 'PASO 1 candidatos' AS k, COUNT(*) FROM tmp_candidatos;

UPDATE financiero.factura_legal_item fli
SET iva = c.iva_resuelto
FROM tmp_candidatos c
WHERE fli.id = c.item_id AND fli.sucursal_id = c.sucursal_id;

SELECT 'PASO 1 items NULL post' AS k,
       COUNT(*) FILTER (WHERE fli.iva IS NULL) AS aun_null,
       COUNT(*) AS total
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
WHERE fl.fecha >= '2026-04-01 00:00:00' AND fl.fecha < '2026-06-01 00:00:00';

-- PASO 2: Recalcular parciales con descuento proporcional
WITH items_por_iva AS (
    SELECT fli.factura_legal_id AS fl_id, fli.sucursal_id AS suc,
        SUM(CASE WHEN COALESCE(fli.iva,0)=0  THEN fli.total ELSE 0 END) AS s0,
        SUM(CASE WHEN COALESCE(fli.iva,0)=5  THEN fli.total ELSE 0 END) AS s5,
        SUM(CASE WHEN COALESCE(fli.iva,0)=10 THEN fli.total ELSE 0 END) AS s10,
        SUM(fli.total) AS stot
    FROM financiero.factura_legal_item fli
    JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
    WHERE fl.fecha >= '2026-04-01 00:00:00' AND fl.fecha < '2026-06-01 00:00:00'
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
    LEFT JOIN items_por_iva i ON i.fl_id=fl.id AND i.suc=fl.sucursal_id
    WHERE fl.fecha >= '2026-04-01 00:00:00' AND fl.fecha < '2026-06-01 00:00:00'
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

-- VERIFY
SELECT to_char(fl.fecha,'YYYY-MM') AS mes,
   COUNT(*) AS facturas,
   COUNT(*) FILTER (WHERE ABS(fl.total_final - (fl.total_parcial_0 + fl.total_parcial_5 + fl.total_parcial_10)) >= 1) AS inconsistentes,
   SUM(fl.total_parcial_0)::bigint  AS sum_p0,
   SUM(fl.total_parcial_5)::bigint  AS sum_p5,
   SUM(fl.total_parcial_10)::bigint AS sum_p10,
   SUM(fl.iva_parcial_5)::bigint    AS sum_iva5,
   SUM(fl.iva_parcial_10)::bigint   AS sum_iva10,
   SUM(fl.total_final)::bigint      AS sum_final
FROM financiero.factura_legal fl
WHERE fl.fecha >= '2026-04-01' AND fl.fecha < '2026-06-01'
GROUP BY 1 ORDER BY 1;

-- Items aun NULL post-fix por mes
SELECT to_char(fl.fecha,'YYYY-MM') AS mes,
       COUNT(*) FILTER (WHERE fli.iva IS NULL) AS items_null_post
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
WHERE fl.fecha >= '2026-04-01' AND fl.fecha < '2026-06-01'
GROUP BY 1 ORDER BY 1;

COMMIT;
