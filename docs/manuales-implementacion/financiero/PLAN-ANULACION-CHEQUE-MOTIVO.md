# Plan — Anulación de cheques: motivo, observación y ticket (desktop #807)

Ramas: `feature/financiero-anulacion-cheque-motivo` en central y desktop, desde `develop` (central `51798e0a`,
desktop `b7241872`). Este archivo se borra en el PR final; lo que sobreviva va a
`ARQUITECTURA-MODULO-FINANCIERO.md`.

## Qué se pide (issue desktop #807)

1. Al anular un cheque, el motivo sale de un **select** (ya no es texto libre) y se suma una **observación**.
2. Después de anular, se imprime un **ticket térmico «CHEQUE ANULADO»** para presillarlo al cheque, y se puede
   reimprimir.
3. Vale para **todas** las puertas, también cuando el cheque se anula con su pago: el usuario elige el motivo.

Motivos: EFECTIVIZACION, FIRMA_NO_CONFORME, MALA_IMPRESION, DEVOLUCION, PROVEEDOR_INCORRECTO,
FECHA_INCORRECTA, MONTO_INCORRECTO, OTRO. OTRO exige observación.

## Puertas (relevadas en develop)

| # | Desktop | Central | Motivo hoy | Rol de la mutation |
|---|---|---|---|---|
| 1 | `cheques-dashboard.onAnular` | `anularCheque` → `ChequeGestionService.anular` | texto libre | `ChequePosGraphQL` |
| 2 | `caja-virtual-dashboard.onAnular` (fila de efectivo de un pago) | `anularPagoCpp` → `anularPorPago` | ninguno | `requirePagarCppCaja` |
| 3 | `movimiento-bancario-anulacion.service` (fila de banco, desde caja mayor y Movimientos bancarios) | `anularPagoCpp` → `anularPorPago` | texto libre | `requirePagarCppCaja` |
| 4 | `liquidacion-detalle-dialog` / `liquidacion-final-dialog` | `anularLiquidacion` / `anularLiquidacionFinal` → `AnulacionPagoRrhhService` → `anularPagoCpp` | ninguno (fijo) | RRHH (+ `requirePagarCpp` si hay pagos vivos) |
| 5 | `numeros-inutilizados-dialog`, acción «Anular cheque» | `inutilizarNumeroCheque` → `anularPorPago` | texto libre del número | `ChequeraGraphQL` |

El motivo del **pago** (puertas 2–4) y el de la **inutilización** (puerta 5) no cambian: el del cheque viaja aparte.

## Diseño

### Central

- **Enum** `MotivoAnulacionCheque` (Java + `cheque.graphqls` + CHECK de la migración, **mismo commit**), mapeado
  con `@Enumerated(EnumType.STRING)`. Javadoc: «un valor nuevo exige una migración que redefina el CHECK».
  Cada valor con su etiqueta para el ticket («Efectivización», «Firma no conforme», …).
- **Migración `V289.1__financiero_cheque_anulacion_motivo.sql`** (aditiva, todo nullable, sin tocar datos),
  sobre `financiero.cheque`: `motivo_anulacion_tipo varchar(30)` con CHECK de los 8 valores,
  `observacion_anulacion text`, `fecha_anulacion timestamp`, `usuario_anulacion_id bigint` con FK a
  `personas.usuario(id) ON DELETE SET NULL` (como `chequera_usuario_fk`, V56). Central-only (V278.5): **sin espejo
  en filial**. Se aplica de verdad en el central local antes del PR (el CI no corre Flyway).
- **Input único** `AnulacionChequeInput { motivo: MotivoAnulacionCheque!, observacion: String }`, argumento opcional
  **`anulacionCheque`** con el mismo nombre en las **5** mutations: `anularCheque`, `anularPagoCpp`,
  `anularLiquidacion`, `anularLiquidacionFinal`, `inutilizarNumeroCheque`. Un mismo input vale para todos los
  cheques del pago. Opcional porque el desktop se actualiza con consentimiento.
- **Validación al entrar** (antes de cualquier lock), en un helper `validarAnulacionCheque(input)`: OTRO sin
  observación rechaza; observación recortada. Se llama al inicio de las 5 entradas.
- `ChequeGestionService.marcarAnulado(cheque, motivoTexto, input, usuario)`, solo escritor, llamado por `anular` y
  `anularPorPago` **después** del `if (ANULADO) return` (idempotencia: un cheque ya anulado conserva sus datos).
  Escribe estado, `motivo_anulacion`, tipo, observación, `fecha_anulacion = now()` y el usuario de la sesión.
  - `motivo_anulacion` (texto, el que muestra el desktop viejo): con input, `"<Etiqueta>"` + `" - <obs>"` si hay;
    sin input, lo de hoy (`razon` del pago, «Número inutilizado: …» o el texto libre).
  - Sin input (desktop viejo): tipo null y **WARN en el log** cuando se anula un cheque así.
- **Overloads** `anularPorPago(id, motivo, usuario)` y `anularPagoCpp(id, motivo, usuario)` que delegan con
  `null`: los 27 llamadores de tests no cambian. `AnulacionPagoRrhhService.anularLiquidacion/anularFiniquito`
  ganan el parámetro y lo pasan.
- **Query `chequesAAnular(pagoId: ID, liquidacionId: ID, liquidacionFinalId: ID): [Cheque]`**: los cheques que la
  anulación va a anular. Mismo filtro que el central al anular: detalles `anulado=false` de pagos no CANCELADO
  (`pagosVivos` para RRHH), cheque `estado != ANULADO` (incluye COBRADO). Para RRHH resuelve la solicitud en el
  central (las liquidaciones no exponen `solicitudPagoId`).
  - **Rol = el de la mutation que antecede**: `requirePagarCppCaja` para `pagoId` (al acotado TESORERIA CAJA le
    devuelve `[]`: no puede anular pagos con cheque, `PagoProveedorService` lo rechaza); el rol RRHH de
    `anularLiquidacion` para las liquidaciones. Se suma a `RolTesoreriaCajaMatrizTest.HABILITADOS`.
- **Query `imprimirChequeAnulado(chequeId: ID!, anchoMm: Int): String`** (base64 ESC/POS), `seg.requireVer()`
  (quien puede anular un cheque tiene un rol de `TODOS`). Rechaza un cheque no ANULADO.
- **Ticket** `ChequeAnuladoTicketEscPos` en `utilitarios/print/` (hermano de `ReciboTicketEscPos`, que tiene
  rótulos fijos de RRHH; sus helpers de texto pasan a package-private): empresa, «CHEQUE ANULADO», Nº, banco y
  cuenta, beneficiario, monto y moneda, fecha de emisión y de pago, motivo, observación, fecha/hora y usuario de la
  anulación, y «Movimiento bancario revertido» si `movimientoBancarioId != null` o «Reserva liberada» si era
  diferido. Campos nulos (cheques anulados antes del cambio) → «sin dato».

### Desktop

- **`AnularChequeDialogComponent`** (`financiero/cheque/`): select de motivo, observación (obligatoria con OTRO) y
  la lista de cheques afectados en solo lectura (Nº, beneficiario, monto). Cierra con el input o null.
  **Cancelar aborta toda la operación** (no se anula el pago).
- Puertas:
  1. dashboard de cheques: el diálogo nuevo reemplaza a `MotivoDialogComponent`.
  2. y 3. tras confirmar / dar el motivo del pago, `chequesAAnular(pagoId)`; si trae cheques, el diálogo nuevo; si
     viene vacío, sigue como hoy. **Si la consulta falla, se aborta con aviso** (nunca «asumir que no hay cheques»).
  4. igual con `liquidacionId` / `liquidacionFinalId`.
  5. diálogo de números inutilizados: con acción «Anular cheque», select + observación en el mismo formulario.
- **Impresión**: `ImpresionService.imprimirTicketDirecto(nombre, generar)` público (reusa el `imprimirTicket`
  privado, sin el diálogo PDF/Ticket), ancho de la impresora elegida (`anchoMm`/`perfilPapel`, 58 por defecto).
  Tras una anulación exitosa, un ticket por cada id que devolvió `chequesAAnular` **antes** (no se re-consulta).
  Si la impresión falla, el aviso dice que el cheque ya quedó anulado y se reimprime desde el dashboard.
- **Reimprimir**: ⋮ «Imprimir comprobante de anulación» en el dashboard para los ANULADOS. El detalle muestra
  motivo, observación y fecha. Los campos nuevos del fragmento del dashboard son escalares (tipo, observación,
  fecha): el usuario sale solo en el ticket, para no sumar cargas por fila en una lista de hasta 500.

## Fases

| Fase | Repo | Contenido | Tests |
|---|---|---|---|
| C1 | central | enum, migración, entidad, schema, input, validación, `marcarAnulado`, argumento en las 5 mutations, overloads | `ChequeGestionServiceTest`: anular con input guarda tipo/obs/fecha/usuario y el texto; OTRO sin obs rechaza antes de tocar el banco; sin input queda como hoy; cheque ya ANULADO conserva sus datos; `PagoProveedorServiceTest`: el input llega a `anularPorPago`; `AnulacionPagoRrhhServiceTest`: llega a `anularPagoCpp`; test que compara el CHECK de V289.1 con `MotivoAnulacionCheque.values()`; `SchemaEnumsSincronizadosTest` |
| C2 | central | `chequesAAnular`, `imprimirChequeAnulado`, `ChequeAnuladoTicketEscPos` | builder: decodifica el base64 y busca los textos en 58 y 80 mm, y con todos los campos nuevos nulos; `chequesAAnular` excluye cheques de un pago anulado antes; TESORERIA CAJA recibe `[]`; matriz de roles; rechazo de cheque no anulado |
| D1 | desktop | modelo + GQL, diálogo, puertas 1–5 | `npm run check`; prueba por pantalla de cada puerta |
| D2 | desktop | `imprimirTicketDirecto`, impresión tras anular, reimprimir, detalle | prueba por pantalla en Electron con la térmica `frc-ticket` |

## Orden de despliegue (por canal)

El desktop nuevo pide campos y queries que el central viejo no tiene: contra un central viejo **se rompe el
dashboard de cheques y toda anulación de pago** (también la de pagos sin cheque). Por eso:

1. PR central → `develop`, merge y **Deploy manual a alpha**.
2. Recién ahí PR desktop → `develop` (el desktop alpha y el web se actualizan solos).
3. Central `release/beta` + Deploy beta/farmacia **antes** de promover el desktop a `release/beta`.
4. Central `master` + Deploy bodega **antes** del desktop a `master`.

Desktop viejo contra central nuevo: sigue funcionando (argumentos opcionales; anula sin motivo de la lista).

## Datos nuevos: quién escribe y quién lee

| Dato | Escribe | Lee |
|---|---|---|
| `cheque.motivo_anulacion_tipo` | `ChequeGestionService.marcarAnulado` | ticket, detalle del dashboard |
| `cheque.observacion_anulacion` | idem | idem |
| `cheque.fecha_anulacion` | idem | idem |
| `cheque.usuario_anulacion_id` | idem (usuario de la sesión) | ticket |

## Riesgos y reversa

- Migración aditiva; revertir el JAR deja columnas sin uso (`ddl-auto=none`). Sin espejo en filial.
- Todo dentro de la transacción de la mutation: si algo rechaza, se revierte caja, banco y cheque juntos.
- Si falla la impresión, el cheque ya quedó anulado: se reimprime.
- Dos usuarios anulando el mismo pago: el lock del pago serializa; el segundo recibe «El pago ya está anulado».
- Pago por lote desde RRHH: se elige el motivo y después el central rechaza el lote (como hoy).

## Pruebas de runtime (local)

Central propio en 8081 (replicación apagada) + desktop `ng serve -c web` y Electron para la térmica. Por puerta:
anular con motivo; OTRO sin observación (rechazo); cancelar el diálogo en las puertas 2, 3 y 4 (no anula nada);
pago con cheque diferido y con cheque al contado; liquidación pagada con cheque; inutilizar con acción anular;
usuario TESORERIA CAJA anulando un pago en efectivo (sin diálogo de cheque); ticket impreso en `frc-ticket`;
reimpresión, también de un cheque anulado antes del cambio.

## Sin verificar / fuera de alcance

- Un pago pagado solo con cheques diferidos no tiene fila de caja ni de banco: hoy no hay pantalla para anularlo.
- Vales y aguinaldos pagados con cheque no pasan por `AnulacionPagoRrhhService` (ya es así hoy, sin verificar).
- Desktop servido como web: no imprime local; muestra el aviso de que el ticket sale solo en la app de escritorio.
