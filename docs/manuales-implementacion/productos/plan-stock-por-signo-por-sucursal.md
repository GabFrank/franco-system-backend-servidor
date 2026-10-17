# Plan — Búsqueda de productos: stock POSITIVO/NEGATIVO por sucursal

Rama: `fix/productos-stock-por-signo-sin-sucursal` (desde `develop`). Pieza: **central**. Un PR.
Acompaña al PR del desktop de la misma rama (tabla de la fila desplegada), que no depende de este.

## Problema

`ProductoRepository.searchWithFilters` y `searchWithFiltersByIds`, con `stockFiltro` = `positivo`
/ `negativo` y sin `sucursalId`, comparan la **suma de todas las sucursales** (`GROUP BY
ms.producto.id HAVING SUM(ms.cantidad) > 0`). COMPRAS suele tener un negativo enorme que tapa el
resto: «HEINEKEN» + POSITIVO trae 2 productos, aunque 4902 HEINEKEN LATA 269 ML tiene 18 sucursales
en positivo (+9.486 sin COMPRAS, −118.464 en COMPRAS).

## Cambio (opción 1, elegida por Franco el 17/10)

En las dos queries, el subselect agrupa por **producto y sucursal**: `GROUP BY ms.producto.id,
ms.sucursalId HAVING SUM(ms.cantidad) > 0` (o `< 0`). POSITIVO = al menos una sucursal con stock
> 0; NEGATIVO = al menos una con stock < 0. El cero no entra en ninguno.

- **Sin sucursal elegida, COMPRAS y SERVIDOR no cuentan** (opción a, elegida por Franco el 17/10).
  No venden, y COMPRAS está negativa en casi todo: con ella, NEGATIVO traería casi todos los
  productos. Quien quiera el signo de COMPRAS la elige en el select de sucursal.
- Con `sucursalId` elegido no cambia nada: el `WHERE` ya deja una sola sucursal (también COMPRAS).
- Es la misma regla que la tabla de la fila desplegada del desktop, que tampoco muestra SERVIDOR.

## Quién llama

| Llamador | `stockFiltro` | Efecto |
|---|---|---|
| `searchProductoWithFilters` ← desktop `list-producto` | sí | el que se arregla |
| `exportarReporteConFiltros` (PDF de la misma pantalla) | sí | entran los mismos productos que en la lista; la cantidad no cambia |
| `buscarConFiltrosLucene` (`ProductoService` ~275, búsqueda con texto) | sí | usa `searchWithFiltersByIds`: el camino de «HEINEKEN» |
| desktop `buscador-compras`, `gestion-productos-proveedor-dialog` | `null` | ninguno |
| `ProductoService` búsqueda con stock por sucursal (línea ~157) | `null` | ninguno |
| PWA, mobile, filial | no usan el parámetro | ninguno |

Un desktop **stable** sin el fix de la fila ve la lista POSITIVO más larga, pero al desplegar
muestra todas las sucursales: solo visual, se arregla con el PR del desktop.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Contando COMPRAS, NEGATIVO trae casi todo y un producto positivo solo en COMPRAS entra en POSITIVO | A | Aplicado: opción a, se excluye sin sucursal elegida |
| Faltaba `buscarConFiltrosLucene` en la tabla de llamadores | A | Agregado; el IT cubre las dos queries |
| La frase del PDF sobre la cantidad era inexacta | A | Corregida |
| Rendimiento sin medir en bodega | B | Medido en la copia local, al día al 15/10 (5,6 M filas): ~1,0 s → ~1,6 s el subselect; el índice `(producto_id, sucursal_id)` ya existe |
| El IT tiene que aislar el producto sembrado | B | Aplicado: por id (`ids` / `soloCodigo` + `idsCodigo`) |
| La count query de `searchWithFilters` | B | Sin acción: Spring Data la deriva con el mismo `WHERE` |

## Implementación y auditoría del diff (pasos 7 y 8)

**`EXISTS` correlacionado por producto, no `IN (subselect)`.** Con el agrupado por sucursal, el
`IN` dentro del `OR` de positivo/negativo se resuelve sin paralelismo sobre todo
`movimiento_stock`: «HEINEKEN» + POSITIVO tardaba ~15 s por pantalla (16,5 s en `EXPLAIN ANALYZE`).
Con `EXISTS`, que usa el índice `(producto_id, sucursal_id)`:

| Caso (copia local, 5,6 M movimientos) | Antes del fix | Con el fix |
|---|---|---|
| Con texto («HEINEKEN»), POSITIVO / NEGATIVO | ~1,7 s | 340 ms / 308 ms |
| Sin texto, POSITIVO / NEGATIVO (página + conteo) | ~1,2–1,6 s solo el subselect | 2,7 s / 4,4 s |

Fijo 2: `N/A para central porque el diff no trae migración ni .graphqls [ev: git diff --name-only
→ ProductoRepository.java + el IT + este plan]`.

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| El cambio no expone datos nuevos | Fijo 1 | Nada que cambiar |
| `NOT IN` descartaría movimientos con `sucursal_id` nulo | Fijo 3 | Descartado: la columna es `NOT NULL` (0 filas) |
| `Pageable.unpaged()` en el IT no ejercita la count query | Fijo 3 | Cubierto por pantalla: los totales 5631 y 5177 coinciden con SQL |

## Fase 2 — Cantidad del PDF (pedida por Franco el 17/10)

**Problema.** Con POSITIVO o NEGATIVO y sin sucursal, el PDF lista los productos de la regla nueva,
pero su columna Cantidad sigue siendo el total de todas las sucursales (con COMPRAS para quien
tiene el permiso): un reporte «STOCK: POSITIVO» muestra 4902 con −108.978.

**Cambio.** En `ProductoService.exportarReporteConFiltros`, con `stockFiltro` positivo/negativo y
sin `sucursalId`, la cantidad es la **suma de las sucursales de ese signo**, sin COMPRAS ni SERVIDOR,
para cualquier usuario (opción a extendida, Franco 17/10). El encabezado lo aclara:
`STOCK: POSITIVO (SUMA DE SUCURSALES POSITIVAS, SIN COMPRAS)`. El desktop alinea su fila desplegada
en el mismo PR de la rama: con el filtro de signo y sin sucursal, tampoco muestra COMPRAS. `MovimientoStockService` gana
`stockDelSignoExcluyendoNombresSucursal(proId, positivo, nombres)`, sobre la misma consulta
agrupada (`stockPorSucursales`) y el mismo redondeo a `Float` que `sumarStockDeSucursales`.
El costo total de la fila (cantidad × costo) la sigue.

- TODOS o con sucursal elegida: sin cambios.
- El «COSTO TOTAL» del encabezado es la suma de las filas (`costoTotalGeneral` en `productos.jrxml`), así que también cambia. «CANT. TOTAL» es la cantidad de productos: no cambia.

**Llamadores.** `stockByProductoId` y `stockByProductoIdExcluyendoNombresSucursal` no cambian (los
usan costo medio y otros); el método nuevo solo lo llama el PDF.

- El signo se decide sobre el `double` de cada sucursal, antes del redondeo a `Float`: un producto
  que la lista consideró positivo no puede dar 0 (1e-9 sigue siendo > 0 en `Float`). Cantidad nula:
  se salta, como en `sumarStockDeSucursales`, que no se toca (su redondeo entra en el costo medio).

**Auditoría (paso 5).**

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Para quien ve COMPRAS, la fila del desktop la sumaba y la lista no | A | Aplicado: sin COMPRAS en PDF y fila con filtro de signo |
| El encabezado no decía qué suma la cantidad | A | Aplicado: rótulo en `filtroStock` |
| COMPRAS elegida sin permiso cae en la rama sin sucursal | A | Correcto; queda cubierto por la regla general |
| Signo con floats / cantidad nula | B | Signo sobre `double`; nulas se saltan; test con 1e-9 |
| No tocar `sumarStockDeSucursales` | B | Método aparte |
| Dos consultas por producto en el PDF | B | Previo; el método nuevo no agrega ninguna |

**Tests.** `MovimientoStockServiceStockTest`: suma solo las sucursales del signo, excluye por nombre,
el cero no entra, una sola consulta. Por pantalla: el PDF de «HEINEKEN» + POSITIVO muestra para 4902
+9.486 (las 18 sucursales positivas) y NEGATIVO solo cantidades negativas.

## Datos nuevos

Ninguno: sin migración, sin cambio de `.graphqls`, sin campo nuevo.

## Fases

1. Las dos queries + IT. Build: `./mvnw clean verify -B -DskipFlyway=true`.

## Tests

`ProductoStockPorSignoQueriesIT` (patrón de `CantidadSugeridaQueriesIT`, transaccional, contra la
base local con `-Dit.stockPorSigno=true`): un producto con +10 en una sucursal y −20 en otra
(total −10) tiene que entrar en POSITIVO y en NEGATIVO; uno solo en cero no entra en ninguno; con
sucursal elegida, solo el signo de esa; un producto positivo solo en COMPRAS no entra en POSITIVO
sin sucursal, y sí con COMPRAS elegida. Para las dos queries. Con el código viejo fallan POSITIVO
mixto y COMPRAS; NEGATIVO y sucursal elegida pasan igual (era correcto).

Prueba por pantalla (desktop web contra el central local): «HEINEKEN» + POSITIVO trae los que
tienen alguna sucursal positiva; desplegar el 4902 muestra solo esas; con una sucursal, igual que
antes; el PDF trae los mismos productos.

## Sin verificar

- Tiempo en bodega de producción. En la copia local, el subselect solo (`EXPLAIN ANALYZE`) pasa de
  ~1,0 s a ~1,6 s: el mismo recorrido de `movimiento_stock`, con más grupos. Solo corre con
  POSITIVO/NEGATIVO elegido.
