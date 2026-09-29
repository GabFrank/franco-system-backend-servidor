# PLAN — Fix: anular una liquidación o finiquito pagado desde tesorería

**Pedido (Franco, 2026-09-29):** arreglar antes de la feature "ítem de liquidación para otro periodo"
el bug de anular una liquidación pagada por banco, que deja vales, cuotas y aguinaldo como
descontados para siempre. Decisión sobre el pago en lote: **rechazar**; el caso lote queda para después.

Repo: **central**, rama `fix/rrhh-anular-liquidacion-pago-bancario` (desde develop).
Desktop: `N/A para desktop porque el botón Anular ya existe y los errores de negocio los muestra
GenericCrudService.onSaveCustom [ev: liquidacion-detalle-dialog / liquidacion-final-dialog llaman
onAnular]`. Filial: `N/A, schema rrhh central-only`. Sin migración.

## Causa (verificada en código, develop 778d60eb)

`LiquidacionSueldoService.anular` (y el espejo `LiquidacionFinalService.anular`) solo revierte si
`PAGADA && cajaVirtualId != null`, y revierte **el movimiento de caja** directo:

- **Pago desde el hub por banco o cheque:** no hay fila de caja, `cajaVirtualId` queda nulo
  (`sincronizarDesdeSolicitudPago` solo linkea movimientos de caja) → `anular` marca ANULADA sin
  devolver la plata ni revertir vales / cuotas de préstamo / aguinaldo / crédito por convenio.
- **Pago desde el hub en efectivo:** `cajaVirtualId` apunta al movimiento **consolidado del evento
  de pago**. `anular` lo revierte, pero el `Pago` y su `SolicitudPago` siguen vivos (CONCLUIDO): si
  después alguien anula ese pago desde la caja, `anularPagoCpp` vuelve a revertir → **doble reversa**.
- El camino correcto ya existe: `PagoProveedorService.anularPagoCpp` revierte caja/banco/cheque por
  detalle, reabre la solicitud y llama `sincronizarDesdeSolicitudPago`, que devuelve la liquidación a
  APROBADA revirtiendo los efectos cruzados. Pero en el desktop solo se llega a ese botón desde una
  **fila de movimiento de caja** (`caja-virtual-dashboard`, `esPagoCpp`): un pago 100% bancario no
  tiene fila y hoy **no se puede anular desde ninguna pantalla**.
- El hub paga el lote como **un solo evento** (`pagarLoteMixtoObligacionesRrhh` → `procesarEvento`):
  anular el evento devuelve a APROBADA todas las obligaciones del lote.
- El vale ya resuelve su variante con un guard (`ValeService.anular`: "se pagó desde tesorería:
  anulá el pago").

## Diseño

1. **Servicio nuevo `AnulacionPagoRrhhService`** (`service/financiero`, depende de
   `PagoProveedorService`, `LiquidacionSueldoService`, `LiquidacionFinalService`,
   `PagoSolicitudDetalleRepository`; ninguno de ellos depende de él → sin ciclo de beans):
   - `anularLiquidacion(id, usuario)` / `anularFiniquito(id, usuario)`, **`@Transactional` explícito**,
     sin `entityManager.clear()` ni `REQUIRES_NEW` entre los pasos: `sincronizarDesdeSolicitudPago`
     muta la **misma instancia** gestionada (OSIV, gotcha rrhh #14) y `anular(id)` tiene que verla ya
     APROBADA (B-1).
   - **Primero toma la liquidación con `lockById`** (ya existe en los dos repos) y relee el estado: dos
     usuarios que anulan a la vez se serializan y el segundo sale por "ya está ANULADA" (B-3).
   - Si el documento está `PAGADA` y tiene `solicitudPagoId`:
     - toma los `pagoId` distintos de los detalles **no anulados** de esa solicitud (puede haber más
       de uno: pago parcial en dos eventos), descartando pagos `CANCELADO` (A-3);
     - **exclusividad por pago**: para cada `pagoId`, lee **todos** sus detalles no anulados
       (`findByPagoIdOrderByCreadoEnAsc`) — no solo los de la solicitud — y si alguno es de **otra**
       solicitud (otra liquidación, un vale, un gasto, un proveedor) → **rechaza sin tocar nada**: "El
       pago #X es un lote que también pagó la liquidación #a (NOMBRE), el vale #b… No se puede anular
       una sola; anulá el pago completo desde tesorería" (B-4);
     - si todos son exclusivos → `anularPagoCpp(pagoId, "ANULACION LIQUIDACION #id", usuarioActual)`
       por cada uno (devuelve la plata por el medio usado y, vía `sincronizarDesdeSolicitudPago`, deja
       la liquidación APROBADA con los efectos revertidos — el segundo pago es idempotente), y después
       `anular(id)` la pasa a ANULADA (desde APROBADA solo cambia el estado) (A-2);
     - **sin pagos vivos** (PAGADA con solicitud pero todos los detalles anulados: datos
       inconsistentes) → método nuevo en el servicio de liquidación que revierte los efectos cruzados
       y marca ANULADA, sin tocar tesorería. Sin esto quedaba trabada: el guard nuevo rechaza y no hay
       pago que anular (B-3, bloqueante).
   - Si no tiene `solicitudPagoId` → `anular(id)` como hoy (pago directo contra caja).
   - Todo en una transacción: si falla la anulación del pago (cheque ya cobrado, etc.), nada cambia.
2. **Guard en `LiquidacionSueldoService.anular` y `LiquidacionFinalService.anular`**: `PAGADA` con
   `solicitudPagoId` → excepción ("se pagó desde tesorería: se anula por AnulacionPagoRrhhService").
   Cierra la doble reversa para cualquier otro llamador.
3. **Resolvers** `anularLiquidacion` / `anularLiquidacionFinal`: llaman al servicio nuevo. Rol: el de
   hoy (`seg.PAGAR`) **y**, cuando hay que anular un pago de tesorería, además `TESORERIA CPP PAGAR`
   o `TESORERIA GESTIONAR` (lo mismo que exige `anularPagoCpp` desde la caja: anular desde RRHH no
   puede ser un atajo para revertir pagos sin el rol de tesorería).
4. Aguinaldo: `N/A, no tiene anular` (se anula su pago desde tesorería y vuelve a APROBADO).

## Tabla de datos nuevos

Ninguno: no hay columnas ni claves nuevas.

## Tests (paso 7: revertir el fix y ver que fallan)

`AnulacionPagoRrhhServiceTest` (mocks):
- liquidación PAGADA por un pago exclusivo (bancario, sin caja) → llama `anularPagoCpp` una vez y
  queda ANULADA; con el código viejo quedaba ANULADA **sin** anular el pago.
- pago parcial en dos eventos exclusivos → anula los dos.
- pago de lote con otra solicitud → rechaza y no llama `anularPagoCpp` ni `anular`.
- sin `solicitudPagoId` → `anular` directo, sin tocar tesorería.
- sin rol de tesorería con pago de hub → rechaza.
`LiquidacionSueldoService.anular` / `LiquidacionFinalService.anular` con `solicitudPagoId` → excepción
(con el código viejo: ANULADA silenciosa).
Además (B-7): el mock de `anularPagoCpp` usa `doAnswer` para poner la **misma instancia** en APROBADA,
y se verifica que `anular` no lanza y el estado final es ANULADA; lista de pagos vacía → revierte
efectos y anula; un pago con un detalle de otra solicitud mezclado con detalles anulados → rechaza;
pago `CANCELADO` → se descarta.

## Fases

| # | Qué | Tests |
|---|---|---|
| 1 | Servicio, guards, resolvers, tests | los de arriba + batería completa |
| 2 | Docs: `ESTADO-IMPLEMENTACION-RRHH.md` (liquidación y finiquito), gotcha en `rrhh-expert` local; borrar este plan | — |

## Prueba manual (central local :8081 + desktop)

1. Liquidación APROBADA con un vale descontado → pagarla desde el hub de RRHH **por banco** (sola).
   Anular desde la liquidación → pide/usa el rol de tesorería, el movimiento bancario queda revertido,
   el vale vuelve a CONFIRMADO, la liquidación ANULADA.
2. Igual pero en efectivo → un solo contra-asiento en la caja (no dos). Intentar anular el pago desde
   la caja después → "El pago ya está anulado".
3. Dos liquidaciones pagadas en el mismo lote → anular una → rechazo que nombra a la otra; nada cambia.
4. Liquidación pagada con «Pagar» directo contra caja → se anula como hoy.

## Riesgo y rollback

Sin migración: volver al JAR anterior restaura el comportamiento viejo sin romper datos. El servicio
nuevo solo reutiliza `anularPagoCpp`, ya usado en producción desde la caja.

## Qué queda sin verificar

- **Datos ya rotos en producción: verificado, no hay.** Consulta de solo lectura con el ok de Franco
  (2026-09-29) en el central de producción: bodega (5552) y farmacia (5551), 0 liquidaciones y 0
  finiquitos `ANULADA` con `solicitud_pago_id`. En bodega 142 de las 144 liquidaciones PAGADA pasaron
  por el hub: es el camino normal. `sincronizarDesdeSolicitudPago` ignora las ANULADAS, así que si
  alguna apareciera antes del deploy, el arreglo sería un script aparte (B-6).
- Anular una sola liquidación de un pago en lote: pendiente (anulación parcial en el motor de tesorería).

## Auditoría del plan (paso 5)

Dos auditores sin verse, hallazgos verificados contra el código. Sin contradicciones.

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| B-3 | B | PAGADA sin pagos vivos quedaba trabada (el guard rechaza y no hay pago que anular) | Método que revierte efectos y anula sin tocar tesorería |
| B-1 / A-1 | A, B | `anular` tiene que ver la misma instancia ya APROBADA | `@Transactional`, sin `clear()`; test con `doAnswer` |
| B-3 | B | Dos anulaciones simultáneas | `lockById` al inicio y relectura |
| B-4 | B | Exclusividad calculada desde la solicitud deja pasar lotes | Se miran todos los detalles de cada pago |
| A-3 | A | Pago ya CANCELADO hace fallar `anularPagoCpp` | Se descartan |
| B-4 (usuario) | B | Pasar el usuario actual, no el de la liquidación | Aplicado |
| A-2 | A | Desde APROBADA `anular` solo cambia el estado | Documentado + test de estado final |
| A-1 (llamadores) | A | Solo los 2 resolvers llaman `anular`; el desktop no anula en lote | Sin cambios |
| B-6 | B | ANULADAS históricas con pago vivo | Verificado en producción: 0 |
