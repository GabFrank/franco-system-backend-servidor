# PLAN — fix(graficos): el ingreso del gráfico Ingresos vs Gastos no descuenta el vuelto

Rama: `fix/graficos-ingresos-descontar-vuelto` (desde `develop` @ `2a07f9b9`). Pieza: **central** solamente.

## Problema

Reporte de producción: en el gráfico Ingresos vs Gastos los ingresos figuran ~500 M por encima de
lo vendido en cada mes.

`VentaRepository.ventasPorMes` y `ventasPorMesSinSucursal` calculan
`SUM(pago) − SUM(vuelto)`, pero el `WHERE` filtra `AND cd.pago = true`. Las filas de vuelto son
`pago=false, vuelto=true`, así que nunca llegan al `SUM`: el ingreso es lo que entregó el cliente,
no lo que se cobró.

Además, el vuelto se guarda **negativo** (416.731 filas negativas contra 1.984 positivas de
2022-2023 que suman ~1,5 M), así que quitar el filtro sin más lo terminaría **sumando**.

Medido en `bodega` local (misma consulta), 2026:

| Mes | develop | vuelto no descontado | neto |
|---|---|---|---|
| Ene | 6.717.049.478 | 569.474.538 | 6.147.574.940 |
| Mar | 6.821.880.066 | 721.688.972 | 6.100.191.094 |

El neto `pagos − |vuelto|` de marzo (6.100 M) coincide con lo cobrado: `total_gs` da 6.158 M y la
diferencia son los descuentos (en 192.202 de 205.552 ventas la igualdad es exacta).

Segundo defecto en la misma consulta: `COUNT(DISTINCT v.id)` en `ventasPorMesSinSucursal`
colapsa ventas de sucursales distintas con el mismo id (PK `(id, sucursal_id)`). Marzo:
165.612 contra 205.553 reales. `cantidad` se muestra en el Excel del gráfico.

## Fase 1 — corregir las dos consultas (un commit)

`src/main/java/com/franco/dev/repository/operaciones/VentaRepository.java`, en `ventasPorMes` y
`ventasPorMesSinSucursal`:

- `WHERE … AND (cd.pago = true OR cd.vuelto = true)` en lugar de `AND cd.pago = true`.
- Monto por fila: `CASE WHEN cd.pago = true THEN cd.valor * COALESCE(cd.cambio, 1) WHEN
  cd.vuelto = true THEN -ABS(cd.valor * COALESCE(cd.cambio, 1)) ELSE 0 END`, igual en `total`,
  `efvo`, `tarjeta`, `otros`. `ABS` hace que el signo con que se guardó el vuelto no importe.
- `COALESCE(cd.cambio, 1)`: hay filas con `cambio` NULL, casi todas en guaraníes (80.566 pagos y
  1.392 vueltos), y hoy `valor * NULL` las descarta del `SUM`. En 2026 son 313 M de pagos que no
  aparecen. El `NOT EXISTS` de outliers ya usaba el mismo `COALESCE`.
- `COUNT(DISTINCT (v.id, v.sucursal_id))` en las dos (en la de una sucursal es equivalente; se
  unifica para que no diverjan).

Sin cambios de firma, de schema GraphQL ni de DTO. El vuelto es siempre `EFECTIVO` (209.766
filas desde 2025, 1 `CONVENIO`), así que baja `efvo` y `tarjeta` no cambia.

**Test** `VentaRepositoryIngresosPorMesTest` (patrón de `NotificacionEnvioLogRepositoryOrdenTest`:
lee la `@Query` por reflexión), para las dos consultas:
- el `WHERE` admite filas de vuelto;
- el vuelto se resta con `ABS`;
- el conteo distingue sucursal.
Se revierte el fix y se comprueba que falla.

**Verificación real** (el CI no tiene base): correr el SQL exacto de la anotación contra `bodega`
local y comparar con la tabla de arriba.

## Datos nuevos

Ninguno: no hay columnas, claves, migraciones ni campos GraphQL nuevos.

## Alcance y consumidores

- `ventasPorMes` (GraphQL `ventasPorMes`) — ningún cliente lo consulta (grep en desktop y PWA).
- `GraficoAggregationService.ingresosGastosPorMesMulti` → gráfico del desktop
  (`ingreso-gasto.component.ts`) y `GraficoIngresoGastoExcelExporter`. Los dos se corrigen sin
  tocar el cliente.
- Filial: N/A, las consultas de gráficos solo viven en el central.
- Migraciones: N/A.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | Formas de Pago suma sin `cambio` y cuenta sin sucursal | Verificado y medido; fuera de alcance (abajo), fix aparte |
| A | Sin consumidores extra, sin cambio de contrato, filial N/A | Confirmado |
| B | `cambio` NULL anula el vuelto | Verificado: afecta también a los pagos (313 M en 2026). Se agrega `COALESCE(cd.cambio, 1)` |
| B | El test por reflexión puede pasar con SQL incorrecto | Se mantiene porque es lo que corre en CI; la corrección la prueba la verificación con el SQL exacto contra `bodega` local (paso 9) |
| B | Sin migración ni datos tocados; `COUNT(DISTINCT (…))` es `bigint`; `EXPLAIN` +0,005 % | Confirmado |

## Fuera de alcance (hallazgo de la auditoría, eje A)

La pestaña **Formas de Pago** del mismo dashboard (`CobroDetalleRepository.obtenerEstadisticasFormaPago*`,
líneas 26-110) sí incluye el vuelto, pero suma `cd.valor` **sin `* cambio`** y cuenta
`COUNT(DISTINCT cd.venta_id)` sin sucursal. Marzo 2026 en `bodega` local: 10.657 filas en moneda
extranjera figuran como 285.943 en vez de 346.359.902 Gs. Es otra consulta y otro gráfico: va en
un fix aparte, no en este PR.

## Sin verificar

- Cifras de producción: el mecanismo es el mismo, pero no se consultó la base de producción.
- El gráfico en el desktop contra el central local (paso 9, prueba de runtime).
