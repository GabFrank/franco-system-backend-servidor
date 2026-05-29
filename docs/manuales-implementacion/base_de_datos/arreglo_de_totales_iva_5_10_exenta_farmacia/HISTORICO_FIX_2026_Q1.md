# Histórico Fix IVA Farmacia — Q1 2026

Registro del fix masivo aplicado el **2026-05-28** sobre la base `farmacia@172.25.1.200:5551`.

## Resumen

| Lote | Items update | Facturas recalc | Δ iva_p10 (Gs) | Δ total_final (Gs) |
|---|---:|---:|---:|---:|
| Marzo 2026 | 12.647 | 9.775 | −96.208.708 | −34.728.530 |
| Abril+Mayo 2026 | 30.745 | 22.273 | −76.384.381 | −266.995.500 |
| Mini-huérfanos (RETATRUTIDA typos / NIVEA / BABELITO) | 133 | 130 | (incluido) | (incluido) |
| **Total** | **43.525** | **32.178** | **−172.593.089** | **−301.724.030** |

## Bug raíz

Backend filial defaulteaba `factura_legal_item.iva = 10` cuando el item venía sin `productoId` resoluble:

- `filial/src/main/java/com/franco/dev/graphql/financiero/FacturaLegalGraphQL.java:344` y `:454`
- `filial/src/main/java/com/franco/dev/service/financiero/FacturaLegalApiService.java:281`
- `filial/src/main/java/com/franco/dev/graphql/financiero/FacturaLegalGraphQL.java:477` — descuento global se resta de `total_final` sin distribuir a `total_parcial_*` (causó 375 facturas marzo con `total_final > items - descuento`)

Commit `3ded136` (2026-02-07 "arreglo error default iva 10") mejoró parcialmente la primera entry pero el patrón de default 10 persiste.

## Casos especiales del fix

### GLUCONEX 15MG 4 FRASCOS VIALES (prod_id 13101)

Catálogo cambió `iva` de `'10'` a `'0'` después de facturar. 85 items históricos marzo (Gs 144.372.000) tenían `fli.iva=10` y fueron forzados a 0 por el script (caso B: NOT NULL pero ≠ mapping).

### Items huérfanos (sin link a producto, descripción tipeada libre)

Override hardcoded en scripts:

| Descripción | iva | Motivo |
|---|---:|---|
| TIRZEC 15MG | 0 | familia TIRZEC exenta |
| TG 15  MG NUEVA PRESENTACION | 0 | TG = Tirzepatida exenta |
| RETRATUTIDA SYNEDICA VERDE | 0 | typo RETATRUTIDA |
| TIRZEDRAL 15 MG | 0 | familia TIRZEDRAL exenta |
| GHK-CU AMPOLLA | 0 | familia GHK-CU exenta |
| GLOW GHK-CU | 0 | combinación exenta |
| RETRATUTIDA VELTRANE 90 MG | 0 | typo, exenta |
| RETATUTIDE VELTRANE 60 MG | 0 | typo, exenta |
| RETRATUTIDA VELTRANE 120 MG | 0 | typo, exenta |
| TESTENAT DEPOT 250 MG 10ML GOLD | 5 | typo de ENANTATO (catálogo id 12481) |
| OXANDROLANA 5 MG X 100 COM | 5 | typo OXANDROLONA |
| ADROLIC MESTEROLONA 25 MG X 20 COMP | 5 | typo ANDROLIC |
| PROTIUM-T X 30 CAPSULAS | 5 | duplicado catálogo (5 vs 10), conservador |
| MELATONIN 5MG | 10 | familia gravada |
| NIVEA FACIAL 5 EN UNO ANTI-ARRUGAS | 10 | cosmética gravada |
| BABELITO ROSA SET 3 EN 1 ANTIDERRAPANTE | 10 | juguete gravado |

## Scripts ejecutados (en orden)

1. `fix_iva_marzo_2026_completo.sql` — backup + UPDATE iva + recalc parciales marzo
2. `fix_iva_abril_mayo_2026.sql` — mismo flujo abril+mayo
3. `fix_huerfanos_pendientes_2026.sql` — 133 items remanentes (descripciones nuevas no en override inicial)

## Backups conservados en DB

Tablas en schema `public`:
- `backup_fl_marzo_20260528`
- `backup_fli_marzo_20260528`
- `backup_fl_abrilmayo_20260528`
- `backup_fli_abrilmayo_20260528`

Restore (si necesario):
```sql
BEGIN;
UPDATE financiero.factura_legal fl
SET total_parcial_0=b.total_parcial_0, total_parcial_5=b.total_parcial_5, total_parcial_10=b.total_parcial_10,
    iva_parcial_5=b.iva_parcial_5, iva_parcial_10=b.iva_parcial_10, total_final=b.total_final
FROM public.backup_fl_marzo_20260528 b
WHERE fl.id=b.id AND fl.sucursal_id=b.sucursal_id;

UPDATE financiero.factura_legal_item fli
SET iva = b.iva
FROM public.backup_fli_marzo_20260528 b
WHERE fli.id=b.id AND fli.sucursal_id=b.sucursal_id;
COMMIT;
```

Análogo para abril+mayo.

## Verificación post-fix

```sql
-- 0 items NULL en rango fix
SELECT COUNT(*) FILTER (WHERE fli.iva IS NULL) AS items_null
FROM financiero.factura_legal_item fli
JOIN financiero.factura_legal fl ON fl.id=fli.factura_legal_id AND fl.sucursal_id=fli.sucursal_id
WHERE fl.fecha >= '2026-03-01' AND fl.fecha < '2026-06-01';
-- Esperado: 0

-- 0 inconsistencias parciales vs total_final
SELECT COUNT(*) FILTER (WHERE ABS(total_final - (total_parcial_0+total_parcial_5+total_parcial_10)) >= 1) AS inconsistentes
FROM financiero.factura_legal
WHERE fecha >= '2026-03-01' AND fecha < '2026-06-01';
-- Esperado: 0
```

## Próximos pasos

1. **Fix código backend filial** — eliminar default 10, hacer lookup producto o default 0. Distribuir descuento proporcional al guardar.
2. **Re-correr scripts mensualmente** hasta que el fix de código llegue a producción.
3. **Limpiar catálogo** — fusionar duplicados (`DESNASAL X 30 COMPRIMIDOS`, `KUKA MAMADERA CELESTE X 70ML`, `PROTIUM-T X 30 CAPSULAS`, `TIRZEDRAL 15 MG`/MD) y crear productos faltantes para evitar items huérfanos.

## Documentación relacionada

- [audit-marzo-2026.md](./audit-marzo-2026.md) — Auditoría inicial top 100 / huérfanos
- [README.md](./README.md) — Manual scripts pre-existentes (`update_factura_legal_item_iva.sql`, `update_factura_legal_totales.sql`)
- [update_factura_legal_item_iva_por_descripcion.sql](./update_factura_legal_item_iva_por_descripcion.sql) — Script reusable (parametrizable por rango fechas)
