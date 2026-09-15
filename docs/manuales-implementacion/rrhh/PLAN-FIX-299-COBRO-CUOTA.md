# Plan — fix #299: `cobrarCuota` duplica el ingreso en Caja Mayor

Issue: GabFrank/franco-system-backend-servidor#299. Rama `fix/rrhh-cobro-cuota-idempotente` en central
y desktop, las dos desde `origin/develop` (central `a472b8ab`, desktop `a7d5ac8d`).

## Decisión (usuario, 2026-09-15)

- **Lock pesimista** sobre la cuota y el préstamo en `PrestamoService.cobrarCuota`.
- **Control de monto**: argumento opcional `montoPagadoEsperado`. Si la cuota cambió desde que el cliente
  la cargó, se rechaza sin tocar caja.
- **Desktop**: «Cobrar» deshabilitado mientras la mutation está en vuelo; manda `montoPagadoEsperado`.
- **Fuera de alcance**: el doble cobro cuota por caja + descuento en liquidación → issue **#300**.

## Análisis (paso 3)

| Hecho | Evidencia |
|---|---|
| La única guarda es `estado == PAGADA`; un cobro parcial deja `PARCIAL` y un reintento idéntico vuelve a entrar | `PrestamoService.java:133-135`, `:157-159` |
| Sin lock: dos llamadas concurrentes leen la misma cuota y registran dos `INGRESO` | `cuotaRepository.findById` (`:131`); regla de tesorería «todo servicio que muta un saldo toma lock pesimista» |
| `CANCELADA` no está en la guarda: el backend la cobra; solo el desktop esconde el botón. **Hoy ningún flujo produce una cuota `CANCELADA`** (valor del enum sin escritor): la guarda es defensiva, no corrige un caso alcanzable | `PrestamoCuotaEstado.java`; grep `CANCELADA` en `src/main/java`; `prestamo-cuotas-dialog.component.ts` `puedeCobrar` |
| El desktop **siempre** cobra el total pendiente: el reintento del parcial solo se da por API. El caso real del desktop es el **doble clic** (sin bandera de «cobrando») | `prestamo-cuotas-dialog.component.ts` `onCobrar` |
| El movimiento pasa por `TesoreriaService.registrar` (lock del saldo y ACL de caja): no hay que tocarlo | `MovimientoCajaVirtualService.java:74-76` |
| Único cliente: desktop. Ni `mobile` ni `mobile-pwa` usan `cobrarCuota` | grep en los tres repos |
| El resolver ya exige `RRHH GESTIONAR` o `RRHH PAGAR` | `PrestamoGraphQL.java` `cobrarCuota` |
| `rrhh.*` no se publica a filiales | skill `rrhh-expert` gotcha 21, `V155.0` |

## Cambios

### Fase 1 — central

1. `PrestamoCuotaRepository.lockById(id)` y `PrestamoRepository.lockById(id)`: `@Lock(PESSIMISTIC_WRITE)` +
   `@Query("select e from X e where e.id = :id")`, mismo patrón que `LiquidacionFinalRepository.lockById`.
2. `PrestamoService.cobrarCuota(cuotaId, cajaVirtualId, montoPago, montoPagadoEsperado)`:
   - toma la cuota con `lockById` (en vez de `findById`);
   - rechaza `PAGADA` **y `CANCELADA`**;
   - si `montoPagadoEsperado != null` y difiere del `montoPagado` actual en más de `0.005`, lanza
     «La cuota cambió desde que se cargó la pantalla (ya tiene pagado X). Recargá antes de cobrar.»
     **antes** de registrar el movimiento;
   - toma el préstamo con `lockById` antes de sumar `montoPagado`. Orden de locks fijo: cuota → préstamo
     (dos cuotas distintas del mismo préstamo no forman ciclo);
   - se conserva la sobrecarga de 3 argumentos, que delega con `null`.
3. `PrestamoGraphQL.cobrarCuota(..., BigDecimal montoPagadoEsperado)` y el `.graphqls`:
   `cobrarCuota(cuotaId: ID!, cajaVirtualId: ID!, montoPago: Float, montoPagadoEsperado: Float): PrestamoCuota`.
   Argumento **opcional**: el desktop viejo no lo manda y conserva el comportamiento de hoy (con lock).
4. **(Incluido por el usuario al aprobar)** `marcarVencidas` pasa a un `UPDATE` masivo que toca solo el estado:
   `update PrestamoCuota c set c.estado = VENCIDA where c.estado in (PENDIENTE, PARCIAL) and c.fechaVencimiento < :fecha`
   (`@Modifying` en `PrestamoCuotaRepository`). No toca `montoPagado`, y PostgreSQL vuelve a evaluar el `WHERE`
   sobre la fila después de esperar el lock del cobro: una cuota recién `PAGADA` queda afuera. Devuelve la
   cantidad de filas, igual que hoy.

Tests (`PrestamoServiceCobroCuotaTest`, Mockito como `ValeServiceSincronizacionTest`):

| Caso | Esperado |
|---|---|
| Reintento del mismo parcial con el `montoPagadoEsperado` de antes del primer cobro | rechaza, **sin** `registrarMovimiento` (el caso del issue) |
| `montoPagadoEsperado` coincide | cobra: 1 `INGRESO`, cuota `PARCIAL`/`PAGADA`, préstamo suma |
| `montoPagadoEsperado` null | cobra como hoy |
| Cuota `PAGADA` / `CANCELADA` | rechaza sin movimiento |
| Usa `lockById` de cuota y de préstamo, no `findById` | verify |

Con el fix revertido, el primer caso y el de `CANCELADA` **tienen que fallar**.

Build: `./mvnw clean verify -B -DskipFlyway=true` leído del log; veredicto `gh pr checks`.

### Fase 2 — desktop

1. `cobrarCuotaMutation` y `PrestamoService.onCobrarCuota(cuotaId, cajaVirtualId, montoPago, montoPagadoEsperado, servidor = true)`.
2. `prestamo-cuotas-dialog`:
   - bandera `cobrando`: se enciende al confirmar y se apaga en `next` y en `error`; mientras está encendida
     `onCobrar` no hace nada y los botones van `[disabled]`;
   - manda `cuota.montoPagado ?? 0` como esperado;
   - `puedeCobrar(row)` se llama desde el HTML (prohibido por el repo): se precalcula en la fila al cargar,
     ya que se toca ese botón.

Tests: esbuild + node sobre la clase (Karma no corre). Doble `onCobrar` → una sola llamada; el esperado viaja;
`error` apaga la bandera. Gate: `npm run check`.

## Orden de PRs y despliegue

1. **Central primero.** Al mergear a `develop`, `semantic-release` publica la alpha y `deploy-auto.yml`
   (`on: release: published`) la despliega sola.
2. **Gate antes de mergear el desktop**: la corrida de `deploy-auto.yml` de **esa** release del central en
   `success` para la instancia del canal (`gh run list --repo GabFrank/franco-system-backend-servidor
   --workflow deploy-auto.yml`), y la mutation con `montoPagadoEsperado` respondiendo en esa instancia.
   «Mergeado» no alcanza: el deploy puede fallar o hacer rollback, y el desktop de alpha se autoactualiza igual.
3. **Desktop después.** Un desktop nuevo contra un central viejo manda un argumento que el schema no conoce y
   el cobro falla con error de validación de GraphQL. Al revés sí anda: desktop viejo contra central nuevo.
   Lo mismo al promover a beta y a stable: central de ese canal primero.
4. **Rollback: nunca revertir solo el central** una vez que salió el desktop nuevo. Recrea «desktop nuevo + central
   viejo» y el cobro de cuotas queda bloqueado para todo el canal hasta volver a desplegar. Si hay que revertir,
   se revierte hacia adelante con un fix del central.

## Datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| Argumento `montoPagadoEsperado` (no persiste) | desktop `PrestamoCuotasDialogComponent.onCobrar` (valor que muestra la pantalla) | `PrestamoService.cobrarCuota` |

## Persistencia, replicación, filial

Sin migración y sin columnas. `N/A para filial porque rrhh.* no se publica [ev: V155.0]`.
`N/A para mobile y mobile-pwa porque no usan cobrarCuota [ev: grep]`.

## Riesgos conocidos

- La liquidación marca cuotas sin lock ni control de estado → #300. Este fix no lo empeora.
- **Deadlock posible con el pago de liquidación** (B1): `pagar` bloquea saldo → cuota y `cobrarCuota` cuota → saldo.
  Solo si la misma cuota se cobra por caja en el mismo instante en que se paga la liquidación que la descuenta,
  contra la misma caja y moneda. PostgreSQL aborta una: el usuario ve un error y reintenta, y no hay doble cobro.
  Hoy las dos pasan. El orden canónico de locks para cuotas se fija en #300.
- `marcarVencidas` (`PrestamoService.java:172-180`, `PrestamoCuotaScheduler`, 06:00 diario) **carga** las cuotas
  `PENDIENTE`/`PARCIAL` y las **guarda enteras**. `PrestamoCuota` no tiene `@DynamicUpdate` ni `@Version`, así
  que el `UPDATE` escribe todas las columnas. Si un cobro commitea entre la carga y el `save`, el scheduler **pisa
  `montoPagado` con el valor viejo** y deja la cuota `VENCIDA`: queda el `INGRESO` en caja y la cuota como si no se
  hubiera cobrado. Preexistente, ventana chica (un job diario contra un clic). Con el lock nuevo el `save` espera,
  pero pisa igual. Ver el punto 4 propuesto de la fase 1.
- OSIV (gotcha RRHH 14): el persistence context vive toda la request. `cobrarCuota` es la primera lectura de la
  cuota en esa request, así que el `SELECT ... FOR UPDATE` materializa la fila fresca después de esperar el lock.
- `Float` del cliente: comparación con tolerancia `0.005` (la misma de tesorería).

## Qué queda sin verificar

- **Gate antes de abrir el PR del central** (B4): dos mutations `cobrarCuota` simultáneas contra el central local,
  sobre una cuota de un préstamo de prueba. La segunda tiene que esperar y salir rechazada (por `PAGADA` o por
  monto), con **un solo** `INGRESO` en la caja. Mockito no puede probar la carrera.
- Auditoría de datos existentes (B3), solo lectura, sobre la copia local `bodega`: cuotas con `monto_pagado > monto`
  y préstamos cuya suma de `INGRESO` con `origen_tipo = 'RRHH_PRESTAMO'` supera lo pagado. El resultado se informa;
  corregir datos de producción queda fuera de este PR.
- Karma y e2e del desktop: no corren.

## Auditoría del plan (paso 5)

| # | Eje | Hallazgo | Verificación | Qué se hizo |
|---|---|---|---|---|
| A1 | A · alta | «Mergeado» no garantiza desplegado: el desktop de alpha se autoactualiza y puede llegar antes que el central | El central sí despliega solo (`deploy-auto.yml`, `on: release: published`); el auditor decía que era manual. El riesgo de carrera sigue | **Aplicado**: gate con la corrida de `deploy-auto.yml` de esa release en `success` antes de mergear el desktop |
| A2 | A · baja | Único cliente, patrón `BigDecimal` opcional ya probado con `montoPago`, `rrhh.*` no replicado | grep y `vales-prestamos.graphqls` | Sin cambio |
| A3 | A · baja | Ningún flujo produce `CANCELADA`: la guarda es defensiva | grep `CANCELADA` en `src/main/java` | **Aplicado**: aclarado en el análisis |
| A4 | A · baja | `marcarVencidas` guarda cuotas sin lock | Leído; además **pisa `montoPagado`** (sin `@DynamicUpdate` ni `@Version`) | **Aplicado**: riesgo reescrito y punto 4 propuesto de la fase 1 |
| B1 | B · alta | Deadlock: `LiquidacionSueldoService.pagar` bloquea saldo de caja → cuota; `cobrarCuota` nuevo bloquea cuota → saldo | `LiquidacionSueldoService.java:545` y `:552` → `:654-662`. Solo con la misma cuota, misma caja y moneda, en el mismo instante: es el doble cobro de #300 | **Aceptado y documentado**: PostgreSQL aborta una de las dos (hoy pasan las dos y se cobra doble). El orden canónico lo fija #300 |
| B2 | B · alta | Revertir **solo** el central con el desktop nuevo instalado bloquea el cobro de cuotas (argumento desconocido) | Por construcción | **Aplicado**: «nunca revertir solo el central» en la nota de despliegue |
| B3 | B · media | Datos ya corruptos: `INGRESO` duplicados de reintentos parciales anteriores | La parte de «cuotas `CANCELADA` cobradas» es falsa (nadie produce `CANCELADA`) | **Aplicado**: consulta de auditoría de solo lectura en la prueba de runtime |
| B4 | B · media | Mockito solo prueba que se llama `lockById`, no la carrera | Correcto | **Aplicado**: la prueba con dos mutations simultáneas pasa a ser gate antes del PR del central |
| B5 | B · media | Sin timeout HTTP, la bandera `cobrando` puede quedar encendida si la request cuelga | Preexistente (desktop #304). La bandera vive en el diálogo: cerrarlo y reabrirlo la reinicia | Anotado, fuera de alcance |
| B6 | B · baja | Desktop viejo con doble clic: el lock ya cierra el duplicado; precisión `Float` sin riesgo en guaraníes enteros | Leído | Sin cambio |
