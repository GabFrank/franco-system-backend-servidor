# Plan — el ajuste de un inventario re-finalizado se calcula contra el stock del primer cierre

Rama: `fix/inventario-ajuste-stock-al-contar` (desde `develop`). Pieza: **central**.

## Qué pasa hoy

`InventarioGraphQL.finalizarInventarioEnSucursal` busca, por producto, el AJUSTE que el inventario
ya tenga (`findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId`). Si existe —porque el
inventario se reabrió con `reabrirInventario` y se vuelve a finalizar— lo reutiliza y recalcula:

```
diferencia = contado - stockByProductoIdExecptMovStockId(producto, ajuste, sucursal)
```

Ese stock es el de **ahora** menos el ajuste. Todo lo que entró o salió entre el primer cierre y
el segundo (transferencias, ventas, compras) queda absorbido por el ajuste.

Caso real, bodega, SUC. CALLE 10, inventario 7638 (2026-09-03), MUNICH ULTRA PREMIUM 269 ML:
contado 362, stock al primer cierre (10:25) 355 → ajuste +7. Entre las 11:52 y las 15:49 entraron
dos transferencias (+720, +375) y se vendieron 48. Re-finalizado a las 16:16: stock sin el ajuste
1402 → ajuste **−1040**. Los 16 ajustes de la toma coinciden con el recálculo de las 16:16; 8
quedaron mal. El inventario 7660 del 07/09 los compensó.

El camino con lote tiene el mismo defecto: `InventarioLoteService.saldosPorLote` toma el saldo
actual de cada lote y le resta el desglose del ajuste.

## Decisión

En una re-finalización el corte es **el `creadoEn` del ajuste existente**, que es el instante del
primer cierre (`MovimientoStockService.save` solo lo asigna al crear). Se toman los movimientos
con `creadoEn < corte`: el propio ajuste queda afuera sin excluirlo por id.

- Primer cierre (no hay ajuste): **sin cambios**, stock actual.
- Ajuste existente con `creadoEn` nulo (filas viejas): **sin cambios**, el cálculo de antes.
- Supuesto: reabrir sirve para corregir lo contado en la toma original, no para recontar días
  después. Si alguien recuenta físicamente otro día, lo correcto es un inventario nuevo.

## Fase 1 — fix + tests (un commit)

1. `MovimientoStockLoteRepository`: consulta `stockPorLoteAntesDe(productoId, sucursalId, corte)`,
   igual a `stockPorLote` pero con join al `MovimientoStock` padre
   (`m.id = e.movimientoStockId and m.sucursalId = e.sucursalId`) y `m.creadoEn < :corte`,
   **conservando `e.estado = true`**, el `GROUP BY`, el `HAVING` y el orden FEFO. Corta por
   la fecha del **movimiento padre**, no por el `creado_en` de la fila de lote, que se reescribe al
   regenerar un desglose.
2. `MovimientoStockLoteService.stockPorLoteAntesDe(...)` que la expone.
3. `InventarioLoteService.saldosPorLote(producto, sucursal, excluir)`: si `excluir` tiene
   `creadoEn`, saldos con `stockPorLoteAntesDe(excluir.creadoEn)` y sin restar nada; si no, igual
   que hoy.
4. `InventarioGraphQL.finalizarInventarioEnSucursal`: con ajuste existente y `creadoEn` no nulo,
   `stockByProductoIdAndSucursalIdAntesDeFecha(producto, sucursal, creadoEn)` (ya existe); con
   `creadoEn` nulo, `stockByProductoIdExecptMovStockId` como hoy.

Tests (`./mvnw clean verify -B -DskipFlyway=true`). Los mocks de stock se stubean explícitos:
Mockito devuelve `null` para `Double` y el unboxing revienta.

- `InventarioGraphQLFinalizarTest`
  - re-finalizar usa el stock del primer cierre: ajuste existente con `creadoEn`, stock actual 1402,
    stock antes del corte 355, contado 362 → `cantidad == 7`. **Con el código viejo da −1040**
    (se revierte el fix y se comprueba que falla).
  - ajuste existente sin `creadoEn` → sigue usando `stockByProductoIdExecptMovStockId`.
  - primer cierre → sigue usando `stockByProductoIdAndSucursalId` (lo cubren los tests actuales).
- `InventarioLoteServiceTest`
  - `saldosPorLote` con `excluir.creadoEn` usa `stockPorLoteAntesDe` y no `stockPorLote` ni resta el
    desglose.
  - sin `creadoEn`, el comportamiento de hoy.

## Datos nuevos

Ninguno: sin columnas, sin migración, sin campos GraphQL. Tabla de escritor/lector: N/A.

## Alcance que queda afuera

- **Corrección retroactiva de los ajustes del 7638**: no se toca; el 7660 ya compensó el stock.
- **El primer cierre** sigue usando el stock del momento de finalizar, no el de la hora de cada
  conteo (en el 7638: conteo 09:57, cierre 10:25). Es otra decisión y otro PR.
- **Productos agregados a la toma después de reabrir**: no tienen ajuste, así que usan el stock del
  momento del segundo cierre, igual que un primer cierre.
- **Quién reabrió**: `reabrirInventario` no registra usuario. Otro PR.

## Hallazgos de la auditoría del plan (paso 5)

- **Eje A — venta de filial replicada tarde** (`movimiento_stock` replica filial→central y trae el
  `creado_en` de la filial): una venta hecha antes del primer cierre pero llegada después no estuvo
  en el primer cálculo y sí entra en el corte nuevo. Se acepta: ocurrió antes del conteo, así que
  incluirla corrige el ajuste. Zona horaria: la columna es `timestamptz`, el corte es un instante.
- **Eje A — contrato**: sin cambios GraphQL, sin enum, sin env var; filial sin copia de la lógica.
  `saldosPorLote` tiene un único llamador (`InventarioGraphQL`).
- **Eje B — premisa**: `creadoEn` no lo tocan reabrir/cancelar ni `escribirDesglose` (la fila de
  lote copia el del padre). Sin migración; rollback = JAR anterior, que en la próxima
  re-finalización vuelve a la semántica vieja.
- **Eje B — `e.estado = true`** en la consulta nueva: aplicado en el punto 1.
- **Eje B — tests**: stubs explícitos, aplicado.
- **Eje B — reabrir y recontar días después**: ningún cliente limita la antigüedad al reabrir, y la
  PWA documenta que reabrir no deshace los ajustes. Si alguien recuenta físicamente otro día, el
  corte al primer cierre suma dos veces lo movido entre medio. Con el código de hoy ese caso da
  bien y el de corregir el conteo da mal. **Decidido por el usuario (2026-09-21): corte al primer
  cierre, sin aviso ni bloqueo en UI.** Para recontar otro día se hace un inventario nuevo.

## Qué queda sin verificar y cómo

- La JPQL nueva no la valida ningún test unitario (sin contexto Spring). Se valida arrancando el
  central local con perfil `dev` (Spring Data compila las `@Query` al levantar) y ejecutando la
  consulta equivalente, solo lectura, contra la base de bodega para la toma 7638: tiene que dar
  +7 para el producto 2993.
- N/A para filial: no tiene `finalizarInventarioEnSucursal` (solo expone
  `stockByProductoIdExecptMovStockId` en `MovimientoGraphQL`, que no cambia).
- N/A para desktop / mobile / mobile-pwa: el contrato GraphQL no cambia.

## Despliegue y rollback

Sin migración: rollback = volver al JAR anterior. Requiere `systemctl restart frc-<instancia>` vía
workflow Deploy. Solo cambia el resultado de re-finalizar una toma reabierta.
