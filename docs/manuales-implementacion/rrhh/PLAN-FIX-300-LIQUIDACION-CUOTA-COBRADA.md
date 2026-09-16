# Plan — fix #300: cuota de préstamo cobrada por caja y descontada otra vez en la liquidación

Issue: GabFrank/franco-system-backend-servidor#300. Rama `fix/rrhh-liquidacion-cuota-cobrada` desde
`origin/develop` `9e489671` (ya trae #301: `lockById` de cuota y préstamo).

## Decisión (usuario, 2026-09-16)

**Se corta al pagar la liquidación o el finiquito**, no al cobrar la cuota. Al pagar (hub de tesorería o pago
directo) se toma lock sobre cada cuota descontada y se compara el ítem con lo pendiente real. Si la cuota cambió,
se rechaza con «vuelva a borrador y regenere». El cobro por caja no se bloquea.

## Análisis (paso 3)

| Hecho | Evidencia |
|---|---|
| El borrador congela el descuento: ítem con código `PRESTAMO_CUOTA` (sueldo) y `referenciaTipo = CPP_CUOTA` (sueldo y finiquito; es el que lee `aplicarEfectosCruzados`) por lo pendiente **al generar** | `LiquidacionSueldoService.java:301-315`; `LiquidacionFinalService.java:362-378` |
| Al pagar, `aplicarEfectosCruzados` suma el monto del ítem a `montoPagado` **sin mirar el estado ni lo pendiente**, sin lock | Sueldo `:654-662`; Final `:655-664` |
| **El neto ya trae el descuento**: si la cuota se cobró por caja en el medio, el funcionario cobra de menos aunque se arregle el contador | `totalNeto` / `totalLiquidado` son el monto de la solicitud (`PagoRrhhTesoreriaService.asegurarSolicitud:262,273`) y del EGRESO directo (`pagar`) |
| La liquidación **nunca** actualiza `prestamo.montoPagado` ni lo pasa a `PAGADO` | mismas líneas |
| Al revertir, la cuota vuelve **siempre** a `PENDIENTE` (pierde `PARCIAL` / `VENCIDA`) | Sueldo `:660`; Final `:662` |
| Caminos de pago: **hub** (`pagarRrhhMixto`, sueldo y finiquito; valida en `validarYSaldo` antes de que el motor postee) y **directo** (`LiquidacionSueldoService.pagar`, `LiquidacionFinalService.pagar`; el desktop todavía usa el directo del finiquito en `liquidacion-final-dialog`) | `PagoRrhhTesoreriaService.java:124-151,200-232`; desktop `origin/develop` |
| Revertir: `anular` (directo) y `sincronizarDesdeSolicitudPago` con solicitud no concluida (hub) | Sueldo `:564-581,604-637`; Final `:703-763` |
| Un borrador se regenera (conserva manuales); una `APROBADA` vuelve con `volverBorrador` | Sueldo `:127-160,503-511`; Final `:573-581` |
| Dependencias: `PagoRrhhTesoreriaService` solo repos + motor; los servicios de liquidación no dependen del hub | constructores de los tres servicios |
| `rrhh.*` no se publica a filiales | gotcha RRHH 21 |

## Cambios

### Fase 1 — central

1. **Bean nuevo `service/rrhh/PrestamoCuotaDescuentoService`** (depende solo de `PrestamoCuotaRepository` y
   `PrestamoRepository`: no puede cerrar un ciclo de beans). Es el único que escribe cuota/préstamo desde una
   liquidación:
   - `validarVigentes(List<CuotaDescontada> items, String documento)`: por cada cuota, en **orden de id
     ascendente**, `lockById`. Rechaza si está `PAGADA` / `CANCELADA` o si `|pendiente − monto del ítem| > 0.005`:
     «La cuota #n del préstamo #m cambió desde que se generó la {documento} (pendiente hoy X, descontado Y).
     Vuelva a borrador y regenere». Un solo mensaje con todas las cuotas que no coinciden.
   - `aplicar(cuotaId, monto)`: lock cuota → lock préstamo (orden de #299); suma a la cuota y al préstamo,
     cuota `PAGADA` (con `fechaPago`) o `PARCIAL`, préstamo `PAGADO` si cubre `montoTotal`. Defensivo: si la cuota ya
     está `PAGADA`, lanza (no debería pasar después de validar; hace rollback del pago entero).
   - `revertir(cuotaId, monto)`: lock cuota → lock préstamo; resta (piso 0) en los dos. Estado recalculado:
     `PAGADA` si cubre, `PARCIAL` si queda algo pagado, `VENCIDA` si `fechaVencimiento < hoy`, si no `PENDIENTE`;
     `fechaPago` a null si deja de estar pagada. Préstamo `PAGADO` → `ACTIVO` si deja de cubrir.
2. **Validar antes de mover plata**, en los cuatro puntos de entrada de pago:
   - `LiquidacionSueldoService.pagar` y `LiquidacionFinalService.pagar`: antes de `registrarMovimiento`;
   - `PagoRrhhTesoreriaService.validarYSaldo` para `LIQUIDACION` y `FINIQUITO`: antes de `asegurarSolicitud` y del
     motor. `pagarRrhhMixto` es `@Transactional` y el motor llama a `sincronizarDesdeSolicitudPago` dentro de la misma
     transacción, así que los locks de la validación se sostienen hasta el commit.
   - Los servicios de liquidación exponen `cuotasDescontadas(id)` (ítems `CPP_CUOTA`) para que el hub valide sin
     leer ítems por su cuenta.
3. **`aplicarEfectosCruzados` (sueldo y final)**, caso `CPP_CUOTA`: delega en `aplicar` / `revertir`.
4. **Orden de locks**: la validación toma las cuotas **antes** que el saldo de caja → cuota → saldo, igual que
   `cobrarCuota`. Cierra el deadlock B1 de #299 (antes: saldo → cuota en `pagar`).
5. **Solicitud de pago con monto viejo** (auditoría B-1): `PagoRrhhTesoreriaService.asegurarSolicitud`, antes de
   reusar la solicitud vigente, compara su `montoTotal` con el total actual del documento (`totalNeto` /
   `totalLiquidado`). Si difiere en más de `0.005`:
   - sin nada pagado → le actualiza el `montoTotal` al total actual (en la implementación: `cancelar` no sirve, porque
     `cambiarEstado` exige `exigirSolicitudDeCompra` para pasar a `CANCELADO` y rechaza una solicitud RRHH);
   - con pagos aplicados → rechaza: «la liquidación tiene pagos aplicados por un monto distinto; anule el pago antes de
     regenerar».
6. **Lote en orden canónico** (auditoría B-2): `pagarRrhhMixto` ordena `pagos` por (concepto, documento id) antes del
   loop, como ya hace `PagoProveedorService` con los grupos de movimiento.
7. **Préstamo cancelado** (auditoría B-4): `aplicar` pasa a `PAGADO` solo desde `ACTIVO`; `revertir` vuelve a `ACTIVO`
   solo desde `PAGADO`. Nada produce hoy `PrestamoEstado.CANCELADO`, pero no se resucita.

### Tests (Mockito, estilo `LiquidacionSueldoNetoNegativoTest`)

| Caso | Esperado | Con el código viejo |
|---|---|---|
| `pagar` sueldo con la cuota ya cobrada por caja | rechaza, **sin** `registrarMovimiento` | pasa y descuenta doble → **falla** |
| `pagar` sueldo con cuota vigente | cuota `PAGADA`, **préstamo suma** y pasa a `PAGADO` | el préstamo no cambia → **falla** |
| `anular` una liquidación pagada cuya cuota era `PARCIAL` antes | la cuota vuelve a `PARCIAL`, el préstamo resta | vuelve a `PENDIENTE` → **falla** |
| `pagar` finiquito con cuota ya cobrada | rechaza sin movimiento | **falla** |
| Hub `pagarRrhhMixto` con cuota cobrada | rechaza antes de `asegurarSolicitud` / motor | **falla** |
| `validarVigentes` lockea en orden de id ascendente | verify de orden | — |
| Misma cuota en dos liquidaciones del mismo lote: el segundo `aplicar` | lanza (rollback del lote) | suma doble → **falla** |
| `asegurarSolicitud` con solicitud vigente sin pagos y monto viejo | cancela la vieja y crea una con el total actual | reusa la vieja → **falla** |
| `asegurarSolicitud` con solicitud con pagos aplicados y monto distinto | rechaza | reusa → **falla** |
| `anular` después de un cobro parcial por caja posterior al pago de la liquidación | resta solo lo del ítem; estado `PARCIAL` | vuelve a `PENDIENTE` → **falla** |
| `aplicar` sobre préstamo `CANCELADO` | no lo pasa a `PAGADO` | — |
| `pagarRrhhMixto` con documentos desordenados | procesa en orden (concepto, id) | — |

Revertir el fix y comprobar que los marcados fallan. Build: `./mvnw clean verify -B -DskipFlyway=true` del log.

### Implementación (fase 1) — diferencias con lo planeado y resultado de los tests

- La validación vive **en el bean** (`validarLiquidacion(id)` / `validarFiniquito(id)`, que leen los ítems con sus
  repositorios): el hub no depende de los dos servicios de liquidación. El bean sigue dependiendo solo de repositorios
  y recorre las cuotas con un `TreeMap` (id ascendente).
- Cambio 5: se **actualiza el monto** de la obligación sin pagos, no se cancela (`cancelar` rechaza solicitudes RRHH).
- Estado recalculado: `PAGADA` si cubre; si no, **`VENCIDA` antes que `PARCIAL`** cuando ya venció (mismo criterio del
  scheduler, que pasa una `PARCIAL` vencida a `VENCIDA`).
- Tests: `PrestamoCuotaDescuentoServiceTest` (9), `PagoRrhhTesoreriaServiceCuotaTest` (4), y un caso nuevo en
  `LiquidacionSueldoNetoNegativoTest` y en `ContraAsientoRrhhTest` → 22/22 verdes.
- **Con el fix neutralizado** (validación no-op, `aplicar`/`revertir` con la lógica vieja, hub sin orden y reusando la
  obligación tal cual): **13 fallan** por la razón esperada. Siguen pasando solo «cuota vigente pasa» y «préstamo
  cancelado no revive», que no dependen del fix.

### Desktop

**N/A** en código: el rechazo llega como error de negocio de GraphQL y lo muestra `onSaveCustom` en
`pagar-compras-dialog` y `liquidacion-final-dialog`. Se verifica en runtime que el mensaje se vea.

## Prueba de runtime (paso 9) — gate antes del PR

Central local (`SPRING_DATASOURCE_URL` a `bodega`, perfil `dev`). Préstamo de prueba con una cuota que vence dentro del
período → `generarLiquidacion` del funcionario → aprobar → `cobrarCuota` por caja → pagar por el hub
(`pagarRrhhMixto`): **tiene que rechazar sin movimiento**. `volverBorrador` → regenerar (ya sin la cuota) → aprobar →
pagar: un EGRESO por el neto sin descuento, la cuota con **un** cobro. Y un caso vigente (sin cobro por caja) que
deje cuota y préstamo `PAGADO`, más `anular` con cuota previa `PARCIAL`.

## Datos nuevos

Ninguno persistido. `N/A` tabla de datos nuevos.

## Persistencia, replicación, filial

Sin migración. `N/A para filial porque rrhh.* no se publica`. `N/A mobile / mobile-pwa`: no pagan liquidaciones.

## Orden de despliegue

Un solo PR (central). **`deploy-auto.yml` no se dispara** (la release la publica `GITHUB_TOKEN`): hace falta el deploy
manual del workflow «Deploy» para la instancia. Sin cambio de schema GraphQL: compatible con cualquier desktop.

## Riesgos conocidos

- **Liquidaciones ya aprobadas con cuotas cobradas por caja** quedan impagables hasta regenerarlas: es el
  comportamiento buscado, pero el mensaje tiene que decir qué hacer.
- Datos históricos: cuotas con `monto_pagado > monto` o préstamos sin sumar lo descontado por liquidación. No se
  corrigen en este PR; se deja la consulta de auditoría para producción.
- Un pago del hub con varias liquidaciones: una cuota inválida rechaza el lote entero (el lote ya es todo o nada).
  **No es alcanzable desde el desktop**: los modos de RRHH de `pagar-compras-dialog` fuerzan selección simple
  (`pagar-compras-dialog.component.ts:189-190`, `:603-604`). Solo por API.
- Tercer escritor de cuotas, fuera de alcance: `PrestamoCuotaScheduler` (06:00) → `marcarVencidas`, un `UPDATE` masivo de
  estado (#299). Si coincide con un pago, espera el lock de la fila y vuelve a evaluar el `WHERE`: no pisa lo aplicado.
- La cobertura de pagar / anular / hub es **por diseño**: los tres caminos de cada servicio pasan por el mismo
  `aplicarEfectosCruzados` privado. La validación se agrega donde se mueve plata (dos `pagar` y `validarYSaldo`); la
  reversión no valida, solo resta.

## Qué queda sin verificar

- Estado de datos en producción (solo lectura, fuera del PR).
- Karma y e2e del desktop: no corren.

## Auditoría del plan (paso 5)

| # | Eje | Hallazgo | Verificación | Qué se hizo |
|---|---|---|---|---|
| A-1 | A · baja | La cobertura de pagar/anular/hub no es una lista de sitios: los tres caminos pasan por el mismo `aplicarEfectosCruzados` | grep: solo existe en los dos servicios | **Aplicado**: aclarado en riesgos |
| A-2 | A · baja | Tercer escritor de cuotas: `marcarVencidas` (scheduler) | `PrestamoService.marcarVencidas` (#299, `UPDATE` masivo) | **Aplicado**: en riesgos; espera el lock y reevalúa el `WHERE` |
| A-3 | A · baja | El rechazo del lote entero no es alcanzable desde el desktop | `pagar-compras-dialog.component.ts:189-190,603-604` (selección simple) | **Aplicado**: en riesgos |
| A-4 | A · baja | «`PRESTAMO_CUOTA` / `CPP_CUOTA`» confunde | **Parcialmente falso**: `PRESTAMO_CUOTA` es el código del ítem, `CPP_CUOTA` el `referenciaTipo` (`LiquidacionSueldoService.java:312-313`) | **Aplicado**: redacción aclarada |
| A-5 | A | Desktop muestra el mensaje real (`onSaveCustom`) y tiene volver a borrador + regenerar; aguinaldo/bono no tocan cuotas; sin enums, env ni tablas publicadas | verificado | Sin cambio |
| B-1 | B · alta | Solicitud de pago con monto viejo tras regenerar: la plata sale y la liquidación queda `APROBADA` | La cadena del auditor (pago fallido que deja la solicitud) **no se da**: `pagarRrhhMixto` es `@Transactional` y el rollback se la lleva. **Sí se da** por: pagar → anular el pago (`anularPagoCpp:618-620` la reabre `SOLICITADO` con `montoTotal` viejo) → regenerar → pagar; `solicitudVigente:296-300` la reusa y el motor calcula `restante` con `montoTotal` (`PagoProveedorService:362-365`) → `PARCIAL`. Preexistente, y el mensaje de este fix lleva a ese flujo | **Aplicado**: cambio 5 |
| B-2 | B · media | El lote del hub no toma locks en orden canónico entre documentos | `pagarRrhhMixto:128` itera en el orden del cliente | **Aplicado**: cambio 6 (solo alcanzable por API) |
| B-3 | B · media | Misma cuota en dos liquidaciones del mismo lote: las dos validan antes de aplicar | Correcto: la validación de ambas corre antes del `sincronizar` | **Aplicado**: `aplicar` lanza si ya está `PAGADA` + test |
| B-4 | B · baja | Préstamo `CANCELADO` resucitado; anular tras cobro posterior por caja sin test | Enum real `PrestamoEstado{ACTIVO,PAGADO,CANCELADO}`; nadie produce `CANCELADO` | **Aplicado**: cambio 7 + tests |
| B-5 | B | Transacción única de la cadena del hub (sin `REQUIRES_NEW`), OSIV sin lecturas previas de cuota, rollback de deploy sin migración | verificado | Sin cambio |
