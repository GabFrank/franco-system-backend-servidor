# PLAN — fix(graficos): Formas de Pago suma monedas extranjeras sin convertir (#316)

Rama: `fix/graficos-forma-pago-moneda` (desde `develop` @ `2a07f9b9`). Pieza: **central** solamente.
Relacionado: #315 (Ingresos vs Gastos), que corrige el mismo criterio en `VentaRepository`.

## Problema

`CobroDetalleRepository` tiene 8 consultas nativas que alimentan la pestaña **Formas de Pago**:

- 4 de **total por forma de pago** (`obtenerEstadisticasFormaPago`, `…PorSucursal`, `…PorFecha`,
  `…PorFechaYSucursal`): `SUM(cd.valor)` sin `* cambio`, así que reales y dólares se suman como
  guaraníes; el `porcentaje` de `CobroDetalleService` se calcula sobre ese total.
- 4 de **desglose por moneda** (`obtenerDesgloseMonedaFormaPago*`): se muestran en la moneda
  original con `simbolo` (desktop `forma-pago.component.ts:299-309`), así que ahí **no** va `cambio`.

Defectos comunes a las 8:

1. `COUNT(DISTINCT cd.venta_id)`: la PK de venta es `(id, sucursal_id)`; con todas las
   sucursales junta ventas distintas. EFECTIVO 2026: 356.615 contra 578.415 reales.
2. `SUM(cd.valor)` con signo: el vuelto se guarda negativo, pero 1.984 filas de 2022-2023 están en
   positivo y lo suman.
3. No excluyen los cobros atípicos (`≥ 2.000.000.000`) que sí excluye Ingresos vs Gastos: los dos
   gráficos pueden no cerrar entre sí.

Medido en `bodega` local, 2026, todas las sucursales:

| Forma de pago | Hoy | En Gs | Cantidad hoy | Real |
|---|---|---|---|---|
| EFECTIVO | 14.635.539.025 | 15.617.616.265 | 356.615 | 578.415 |
| TARJETA | 6.552.589.184 | 6.805.404.424 | 144.693 | 165.675 |
| CONVENIO | 309.288.340 | 309.288.340 | 9.157 | 9.208 |

La suma corregida (22.732 M) es la misma que la de Ingresos vs Gastos con #315.

## Fase 1 — corregir las 8 consultas (un commit)

`src/main/java/com/franco/dev/repository/operaciones/CobroDetalleRepository.java`:

- El subquery `cd` expone `sucursal_id` y el **monto por fila** en todas las variantes:
  - total: `CASE WHEN cd2.pago = true THEN cd2.valor * COALESCE(cd2.cambio, 1)
    WHEN cd2.vuelto = true THEN -ABS(cd2.valor * COALESCE(cd2.cambio, 1)) ELSE 0 END`
  - desglose: igual, **sin** `cambio` (moneda original).
- Filtro de atípicos dentro del subquery, idéntico al de `VentaRepository.ventasPorMes`:
  `NOT EXISTS (… ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000)`.
- Conteo: `COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL)`.
  El `FILTER` hace falta: con el `LEFT JOIN`, una forma de pago sin cobros trae `venta_id` NULL, y
  PostgreSQL cuenta `(NULL, NULL)` como 1 (verificado: `count(distinct (a,b))` sobre dos filas
  nulas da 1). El desktop filtra `cantidadTransacciones > 0`: aparecería una forma de pago vacía.

Sin cambios de firma, `.graphqls`, DTO ni servicio. Sin migración.

**Test** `CobroDetalleRepositoryFormaPagoTest` (reflexión sobre la `@Query`, como
`VentaRepositoryIngresosPorMesTest`), parametrizado sobre las 8:
- los totales convierten con `COALESCE(cd2.cambio, 1)`; el desglose no multiplica por `cambio`;
- el vuelto se resta con `-ABS`;
- el conteo distingue sucursal y filtra `venta_id` nulo;
- excluyen atípicos.
Rojo con el código viejo.

**Verificación real**: el SQL exacto de las anotaciones contra `bodega` local; totales contra la
tabla de arriba y contra la suma mensual de Ingresos vs Gastos; una forma de pago sin cobros tiene
que dar cantidad 0.

## Datos nuevos

Ninguno.

## Alcance y consumidores

- `formaPagoEstadisticasMulti` (grafico.graphqls:80) → desktop `grafico/forma-pago` — único
  cliente (grep en desktop, PWA y mobile).
- `formaPagoEstadisticas`, `…PorSucursal`, `…ConFiltros` (forma-pago.graphqls:45-47): sin
  clientes; se corrigen igual porque comparten las consultas.
- Filial: N/A (no tiene estas consultas). Migraciones: N/A.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | Contrato GraphQL y DTO sin cambios; único cliente el desktop; el Excel (`GraficoDesgloseFila.desdeFormaPago`) usa solo el total en Gs y hereda el fix; filial sin estas consultas | Confirmado |
| A | El desglose (moneda original) no suma el total (Gs): puede confundir | Preexistente, sin aritmética cruzada en el componente. Nota en el PR |
| B | Sin migración ni estado; test rojo con el código viejo; `DISTINCT … FILTER` da 0 sin cobros | Confirmado (verificado: TRANSFERENCIA y CHEQUE dan 0) |
| B | «`PorFecha` 2026: 2,3 s → 7,1 s por el `NOT EXISTS`» | **Corregido con medición propia**: 2,5 s → 4,3 s; un mes 1,1 s → 2,0 s. De ese extra, el `NOT EXISTS` es ~0,25 s, el conteo compuesto ~0,5 s y la conversión ~0,2 s. Agregar primero por venta dio 3,1-4,0 s (sin mejora clara) y complica el SQL. Aceptado |
| B | 27 % de los cobros con vuelto lo dan en otra moneda (paga en Gs, vuelto en R$) | Es negocio real. En el desglose por moneda el bucket R$ refleja el neto de reales que entraron y salieron, que es lo correcto para esa moneda. Nota en el PR |
| B | 1 fila sin moneda y 1 con forma de pago inactiva | Insignificante; el total las incluye y el desglose no, como hoy |

## Verificación (paso 9)

- Test: rojo 24/24 con el SQL viejo, verde con el nuevo. `clean verify`: 858 tests, 0 fallos.
- SQL exacto de las 8 anotaciones contra `bodega` local: los totales 2026 dan la tabla de arriba;
  TRANSFERENCIA y CHEQUE, sin cobros, dan cantidad 0. Sucursal 1, 2026: 2.949 M, igual a la suma
  de sus meses en Ingresos vs Gastos con #315.
- Tiempos: con fecha (lo que usa el desktop), año 2,5 s → 4,3 s y mes 1,1 s → 2,0 s. **Sin fecha,
  todas las sucursales** (solo por `formaPagoEstadisticas` / `…ConFiltros` sin fechas, sin
  clientes): total 15 s → 39 s, desglose 9 s → 40 s. Agregar primero por venta lo baja a 27 s
  con el mismo resultado; no se adopta porque solo mejora endpoints sin uso y complica las 8.

## Auditoría del diff (paso 8)

Fijos 1, 2 y 3; ningún condicional (sin globs de release; solo lectura de `cobro_detalle`).

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| 1 | Filtro por sucursal equivalente (`v2.sucursal_id = cd2.sucursal_id` por el JOIN); el `NOT EXISTS` no cruza sucursales. `…PorSucursal` acepta cualquier `sucursalId` con solo sesión | Preexistente, fuera de alcance |
| 2 | Las 8 ejecutan; columnas, tipos y orden de parámetros coinciden con `CobroDetalleService`; filial sin estas consultas | Confirmado |
| 2 | Un cobro atípico excluye la venta entera | Es el mismo criterio de `ventasPorMes`, a propósito |
| 3 | Contrato GraphQL igual, `fix:` sin breaking; Excel y torta filtran por cantidad, no por monto | Confirmado |
| 3 | El desktop oculta buckets de moneda con `totalMonto <= 0` (hay uno de −22 R$) | **Preexistente, no lo agrava**: 2 de 3.579 buckets forma×moneda×sucursal×mes quedan ≤ 0, los mismos 2 con la fórmula vieja; ninguno nuevo. Sin cambio en el cliente |

## Sin verificar

- Cifras de producción.
- El gráfico en el desktop contra el central local (paso 9).
