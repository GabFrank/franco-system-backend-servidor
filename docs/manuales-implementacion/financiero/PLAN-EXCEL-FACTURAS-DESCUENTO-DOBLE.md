# Plan: el Excel de facturas resta el descuento dos veces

Rama: `fix/venta-descuento-en-ticket-y-factura-silenciosa` (sale de `origin/develop`). Pieza: central.
Un PR. Sin migraciones. Es la mitad central de un cambio en dos repos. El plan principal, auditado y
aprobado, vive en el filial:
`franco-system-backend-filial/docs/manuales-implementacion/PLAN-DESCUENTO-TICKET-Y-FACTURA-SILENCIOSA.md`
(hallazgo A-1 de su auditoría, decisión de Franco del 2026-09-24).

## Problema

`FacturaLegalService.convertToDto` arma las filas de `generarExcelFacturas` / `generarExcelFacturasZip`
(el libro de ventas que baja el desktop desde la lista de facturas legales). Calcula
`porcentajeDesc = descuento / totalFinal` y se lo resta a `totalParcial5` y `totalParcial10` antes de
sacar gravadas e IVA.

Pero los parciales ya vienen netos: el filial los calcula con `ParcialesCalculator.calcular(tuples,
descuento)`. En la copia local de `bodega`, de las 1.376 facturas con `descuento > 0`, **1.374 tienen
parciales netos** (`total_final = p0 + p5 + p10`), 1 los tiene brutos y 1 no cuadra con ninguno. El
Excel exporta gravadas e IVA descontados dos veces, que no cierran con `venTotfac` (= `total_final`).
Encima, el porcentaje se divide por el total neto y no por el bruto.

El fix del filial le carga el descuento también a las facturas silenciosas, así que sin esto el
defecto crecería.

## Fase 1 — escalar los parciales al total de la factura

- Nuevo método estático package-private `factorAlTotal(FacturaLegal)`: `totalFinal / (p0 + p5 + p10)`,
  con null → 0. Devuelve 1 si la suma es 0 o `totalFinal` es null (no hay nada que escalar).
  - Parciales netos → factor 1: el Excel queda con lo que dice la factura.
  - Parciales brutos (bug histórico del filial, `FacturaLegalGraphQL:477`) → factor < 1: los lleva al
    neto, que es lo que el código viejo quería hacer.
- `convertToDto` usa `parcial × factor` en los cuatro bloques (gravada e IVA de 5 y de 10) en lugar de
  `porcentajeDesc`. Se va la variable `porcentrajeDesc`.
- Tests (`FacturaLegalServiceExcelTest`, JUnit 5 puro, sobre `factorAlTotal`):
  - parciales netos con descuento → 1;
  - parciales brutos (suma = total + descuento) → total / suma;
  - sin descuento → 1;
  - parciales null o en 0 → 1, sin división por cero;
  - `totalFinal` null → 1.

  Y uno sobre la cuenta de gravada/IVA de una factura neta, que **falla con el código viejo**
  (revertir y comprobarlo).
- Commit: `fix(factura): el excel de facturas no resta el descuento dos veces`.

## Datos nuevos

Ninguno. No se escribe nada: es un reporte de solo lectura.

## Verificación

- `./mvnw -o clean verify -B`, leído del log.
- Manual: central local (perfil `dev`, 8081) y desktop `ng serve -c web`, lista de facturas legales
  → «Excel» de la sucursal 24 el 2026-06-13, que incluye la factura 32487 (total 100.000, descuento
  1.000, parcial 10 = 100.000). Esperado: gravada 10 = 90.909, IVA 10 = 9.091. Hoy salen 90.000 y 9.000.

## Fuera de alcance

- `venExenta` va fijo en `0.0`: el Excel nunca exporta `total_parcial_0`. Es otro defecto previo del
  mismo reporte y se reporta aparte.
- Rectificar Excels ya entregados a la contadora.

## Registro del ciclo

- Paso 1: rama desde `origin/develop`, upstream desvinculado.
- Paso 2: `frc-central`, `frc-factura-iva-fix-expert`.
- Paso 5: cubierto por la auditoría del plan del filial (eje A lo detectó). Eje B aplicado acá: sin
  migración, sin escritura, sin contrato GraphQL; un rollback de JAR vuelve al cálculo viejo sin dejar
  estado.
- Paso 6: aprobado junto con el plan del filial (2026-09-24).
- Orden (§3): los dos PRs son independientes. Ninguno cambia contrato ni esquema, así que no hay orden
  de despliegue forzado.
