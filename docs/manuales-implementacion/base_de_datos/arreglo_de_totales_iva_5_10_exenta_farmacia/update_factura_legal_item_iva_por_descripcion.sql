-- ============================================
-- Script: update_factura_legal_item_iva_por_descripcion.sql
-- ============================================
-- Objetivo:
--   Reparar el iva de factura_legal_item resolviendo el producto por DESCRIPCION
--   cuando no hay FK (producto_id, presentacion_id, venta_item_id) o cuando el iva
--   ya poblado en el item no coincide con el iva actual del producto matcheado.
--
-- Bug que mitiga:
--   El backend filial (FacturaLegalGraphQL, FacturaLegalApiService) defaultea
--   `iva = 10` cuando el itemInput.iva es null y no resuelve por FK.
--   Esto produce factura_legal_item.iva=NULL o =10 en items que en realidad
--   deberian ser 0 (exento). Caso GLUCONEX: producto.iva era 10 y se cambio a 0
--   en catalogo, pero los items historicos quedaron en 10.
--
-- Prerequisitos:
--   1. Backup: pg_dump financiero.factura_legal, financiero.factura_legal_item
--   2. Correr ANTES: update_factura_legal_item_iva.sql (resuelve via FK)
--   3. Correr DESPUES: update_factura_legal_totales.sql (recalcula parciales)
--
-- Convencion de matching:
--   UPPER(TRIM(fli.descripcion)) IN (
--     UPPER(TRIM(producto.descripcion)),
--     UPPER(TRIM(producto.descripcion_factura))
--   )
--   Tie-breaker: si hay multiples productos con misma descripcion y distinto iva,
--   se IGNORA el item (queda como estaba). Reporte en PASO 5.
--
-- Rango de fechas: parametrizar abajo
-- ============================================

\set fechaInicio '2026-03-01 00:00:00'
\set fechaFin    '2026-04-01 00:00:00'

-- ============================================
-- PASO 0: estadisticas del rango
-- ============================================
SELECT
    'PASO 0: stats del rango' AS info,
    COUNT(*) AS total_items,
    COUNT(*) FILTER (WHERE fli.iva IS NULL) AS items_iva_null,
    COUNT(*) FILTER (WHERE fli.iva = 0)     AS items_iva_0,
    COUNT(*) FILTER (WHERE fli.iva = 5)     AS items_iva_5,
    COUNT(*) FILTER (WHERE fli.iva = 10)    AS items_iva_10
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl
  ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
WHERE fl.fecha >= :'fechaInicio'::timestamp
  AND fl.fecha <  :'fechaFin'::timestamp;

-- ============================================
-- PASO 1: descripciones ambiguas (mismo nombre, distinto iva)
--         Estas NO se tocan; quedan para revision manual
-- ============================================
DROP TABLE IF EXISTS tmp_desc_ambigua;
CREATE TEMP TABLE tmp_desc_ambigua AS
SELECT UPPER(TRIM(descripcion)) AS dn
FROM productos.producto
GROUP BY UPPER(TRIM(descripcion))
HAVING COUNT(DISTINCT iva) > 1
UNION
SELECT UPPER(TRIM(descripcion_factura)) AS dn
FROM productos.producto
WHERE descripcion_factura IS NOT NULL AND TRIM(descripcion_factura) <> ''
GROUP BY UPPER(TRIM(descripcion_factura))
HAVING COUNT(DISTINCT iva) > 1;

SELECT 'PASO 1: descripciones ambiguas (se ignoran)' AS info,
       COUNT(*) AS cant FROM tmp_desc_ambigua;

-- ============================================
-- PASO 2: tabla resolutoria desc -> iva_producto
-- ============================================
DROP TABLE IF EXISTS tmp_desc_iva;
CREATE TEMP TABLE tmp_desc_iva AS
WITH unioned AS (
  SELECT UPPER(TRIM(descripcion)) AS dn, iva::integer AS iva FROM productos.producto
  WHERE descripcion IS NOT NULL AND TRIM(descripcion) <> ''
  UNION
  SELECT UPPER(TRIM(descripcion_factura)) AS dn, iva::integer AS iva FROM productos.producto
  WHERE descripcion_factura IS NOT NULL AND TRIM(descripcion_factura) <> ''
)
SELECT dn, MIN(iva) AS iva
FROM unioned
WHERE dn NOT IN (SELECT dn FROM tmp_desc_ambigua)
GROUP BY dn;
CREATE INDEX ON tmp_desc_iva(dn);
ANALYZE tmp_desc_iva;

-- ============================================
-- PASO 2b: OVERRIDE manual para items huerfanos (descripciones sin match exacto
--          en catalogo pero confirmadas como typos / variantes por matching similar)
-- ============================================
DROP TABLE IF EXISTS tmp_override;
CREATE TEMP TABLE tmp_override(dn text PRIMARY KEY, iva int);
INSERT INTO tmp_override VALUES
  ('TIRZEC 15MG',                            0), -- familia TIRZEC exenta
  ('TG 15  MG NUEVA PRESENTACION',           0), -- TG (Tirzepatida) exenta
  ('RETRATUTIDA SYNEDICA VERDE',             0), -- typo: RETATRUTIDA (exenta)
  ('TIRZEDRAL 15 MG',                        0), -- familia TIRZEDRAL exenta
  ('GHK-CU AMPOLLA',                         0), -- familia GHK-CU exenta
  ('GLOW GHK-CU',                            0), -- familia GLOW + GHK-CU exenta
  ('TESTENAT DEPOT 250 MG 10ML GOLD',        5), -- TESTENAT ENANTATO catalogo iva=5
  ('OXANDROLANA 5 MG X 100 COM',             5), -- typo: OXANDROLONA iva=5
  ('ADROLIC MESTEROLONA 25 MG X 20 COMP',    5), -- typo: ANDROLIC iva=5
  ('PROTIUM-T X 30 CAPSULAS',                5), -- duplicado catalogo (5 vs 10), conservador
  ('MELATONIN 5MG',                         10); -- familia MELATONIN gravada

-- Aplicar override: quitar dn duplicado de tmp_desc_iva y de tmp_desc_ambigua, luego insertar override
DELETE FROM tmp_desc_iva     WHERE dn IN (SELECT dn FROM tmp_override);
DELETE FROM tmp_desc_ambigua WHERE dn IN (SELECT dn FROM tmp_override);
INSERT INTO tmp_desc_iva SELECT dn, iva FROM tmp_override;
ANALYZE tmp_desc_iva;

SELECT 'PASO 2: mapping desc->iva (sin ambiguas)' AS info, COUNT(*) AS cant FROM tmp_desc_iva;

-- ============================================
-- PASO 3: candidatos a UPDATE
--   Caso A: fli.iva IS NULL y hay mapping en tmp_desc_iva
--   Caso B: fli.iva IS NOT NULL pero != mapping (correccion de iva mal grabado)
-- ============================================
DROP TABLE IF EXISTS tmp_candidatos;
CREATE TEMP TABLE tmp_candidatos AS
SELECT fli.id AS item_id, fli.sucursal_id, fli.factura_legal_id,
       fli.descripcion AS desc_original,
       fli.iva AS iva_actual,
       di.iva AS iva_resuelto,
       fli.total,
       CASE
         WHEN fli.iva IS NULL THEN 'A: NULL -> ' || di.iva
         ELSE 'B: ' || fli.iva || ' -> ' || di.iva
       END AS caso
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl
  ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
JOIN tmp_desc_iva di ON di.dn = UPPER(TRIM(fli.descripcion))
WHERE fl.fecha >= :'fechaInicio'::timestamp
  AND fl.fecha <  :'fechaFin'::timestamp
  AND fli.iva IS DISTINCT FROM di.iva;

SELECT 'PASO 3: candidatos por caso' AS info,
       LEFT(caso,4) AS tipo, COUNT(*) AS items, SUM(total)::bigint AS suma_total
FROM tmp_candidatos GROUP BY LEFT(caso,4) ORDER BY 2;

-- ============================================
-- PASO 4: preview top descripciones a corregir
-- ============================================
SELECT 'PASO 4: top descripciones a corregir' AS info, '' AS desc_, NULL::bigint AS items, NULL::bigint AS total
UNION ALL
SELECT '', UPPER(TRIM(desc_original)) AS desc_,
       COUNT(*)::bigint AS items, SUM(total)::bigint AS total
FROM tmp_candidatos
GROUP BY UPPER(TRIM(desc_original))
ORDER BY total DESC NULLS LAST
LIMIT 21;

-- ============================================
-- PASO 5: UPDATE (descomenta para ejecutar)
-- ============================================
/*
BEGIN;

UPDATE financiero.factura_legal_item fli
SET iva = c.iva_resuelto
FROM tmp_candidatos c
WHERE fli.id = c.item_id AND fli.sucursal_id = c.sucursal_id;

-- Verify
SELECT 'POST-UPDATE: pendientes en rango' AS info,
  COUNT(*) FILTER (WHERE fli.iva IS NULL) AS items_aun_null,
  COUNT(*) AS total
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl
  ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
WHERE fl.fecha >= :'fechaInicio'::timestamp
  AND fl.fecha <  :'fechaFin'::timestamp;

COMMIT;
*/

-- ============================================
-- PASO 6: items que quedaron sin resolver (sin match desc, sin FK)
--         Requieren atencion manual o se asumen iva=0 por defecto
-- ============================================
SELECT 'PASO 6: items aun NULL (sin match)' AS info,
       COUNT(*) AS items, SUM(fli.total)::bigint AS suma_total
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl
  ON fl.id = fli.factura_legal_id AND fl.sucursal_id = fli.sucursal_id
WHERE fl.fecha >= :'fechaInicio'::timestamp
  AND fl.fecha <  :'fechaFin'::timestamp
  AND fli.iva IS NULL;
