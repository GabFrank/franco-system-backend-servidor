# Módulo Financiero / Tesorería — Arquitectura

> Documento de referencia del módulo tal como quedó implementado (F1–F8, backend).
> Complementa `ESTADO-Y-PLAN-MODULO-FINANCIERO.md` (estado/roadmap) y
> `PRUEBA-GUIADA-MODULO-FINANCIERO.md` (test manual). Rama: `feat/modulo-financiero`.

## 1. Modelo mental (cash-only + ledgers separados)

- **Caja mayor (`CajaVirtual`) = solo efectivo.** Saldo por `(caja, moneda)` en tabla
  `caja_virtual_saldo` (fuente de verdad). Las columnas `saldo_gs/rs/ds` quedan como
  **shim** derivado (compat RRHH + UI vieja), sincronizadas por `TesoreriaService`.
- **Cuenta bancaria (`CuentaBancaria`) = ledger de primer nivel, independiente.** Saldo
  propio (`saldo`, `saldo_reservado`), su ledger `MovimientoBancario`. No es "hija" de la caja.
- **Forma de pago = router.** La UI de operación elige el destino: efectivo → caja mayor;
  no-efectivo → cuenta bancaria (config en `FormaPago`).
- **Tesorería = vista consolidada de solo lectura** (`TesoreriaReporteService.saldoConsolidado`):
  suma efectivo (todas las cajas) + bancos por moneda. Presentación; cero mezcla de datos.
- **Terceros:** cuenta corriente de cliente (`MovimientoCliente` + `Cliente.saldoActual`) y de
  proveedor (`MovimientoProveedor` + `Proveedor.saldoActual`).

Modelo portado de **frc-gourmet** (app hermana), adaptado a JPA/Postgres multi-usuario.

## 2. Núcleo — `TesoreriaService` (el único que toca el saldo de caja)

- `registrar(MovimientoCajaVirtual)` — aplica el movimiento con signo (`signedDelta`), saldo por
  `(caja, moneda)`, **lock pesimista** (`ensureRow` upsert + `lockByCajaVirtualIdAndMonedaId`),
  control de descubierto (`permite_saldo_negativo`, CN2), sincroniza el shim.
- `transferir(...)` — 2 movimientos, **lock en orden canónico (id caja asc)** → sin deadlock.
- `anular(id)` — bloquea si el movimiento no es MANUAL (**anulación cross-módulo**: se anula desde
  el dominio dueño). `revertir(mov)` — hook para los módulos dueños.
- **Límite de antigüedad para anular (CN4)** — `LimiteAnulacionService` es el único dueño de la regla
  (`configuracion_general.dias_limite_anulacion`; sin fila, `null` o `0` = sin tope). Vale para **toda**
  anulación que postea un contra-movimiento, en dos capas:
  - *el movimiento*: `TesoreriaService.revertir` y `BancoLedgerService.revertir` miden `creadoEn` del
    movimiento. Por ahí pasan todas las anulaciones (también las de RRHH por el egreso directo), así que
    una anulación nueva queda cubierta sin llamar a nada.
  - *el documento*: `anularPagoCpp`, `OperacionFinancieraService.anular`, `EntradaVariaService.anular` y
    `RetiroVerificacionService.anular` miden su propia fecha al entrar, antes de tocar nada. Hace falta
    porque el débito de un cheque nace al cobrarlo: un pago viejo con un cheque cobrado hace poco tiene
    ese movimiento dentro del límite, y un diferido sin cobrar no postea ninguna reversa.
  - El rechazo nombra el documento y su fecha. **No hay vía de autorización**: quien autoriza es quien
    sube el límite. Hoy el límite **solo se carga por SQL** (no está en `ConfiguracionGeneralInput` ni en
    el desktop) y está en `NULL` en bodega y farmacia (2026-10-09).
  - Lo que el tope frena además de pagos y operaciones: anular una liquidación, un finiquito o un vale
    con pago viejo, y resolver un caso de retiro **anulando** una verificación vieja (el caso se resuelve
    con el mismo veredicto sin anularla). Anular un cheque suelto, sin pago, no postea contra-movimiento
    y queda fuera.
- `recalcularSaldos(caja)` — red de seguridad (reconstruye desde movimientos activos, con lock).
- **Ledger inmutable:** nunca se edita/borra un movimiento; se revierte con contra-movimiento `AJUSTE` firmado.
- **Trazabilidad:** `origen_tipo` (`OrigenMovimientoTipo`) + `origen_id` → habilita el bloqueo cross-módulo.

`BancoLedgerService` es el análogo para cuentas bancarias (mismo patrón: lock + descubierto contra
`saldo − reservado` + `ajustarReservado` para cheques diferidos).

## 3. Fuentes que alimentan la caja mayor (el circuito de dinero)

| Fuente | Servicio | Movimiento | Origen |
|---|---|---|---|
| RRHH (vale/préstamo/aguinaldo/liquidación/finiquito) | `service/rrhh/*` | EGRESO/INGRESO/AJUSTE | RRHH_* |
| Entrada/salida varia | `EntradaVariaService` | INGRESO/EGRESO | ENTRADA_VARIA |
| Retiro caja PDV → caja mayor | `RetiroTesoreriaProcesador` (poller `@Scheduled`) | INGRESO | RETIRO_CAJA |
| Devolución/merma | `DevolucionService` | EGRESO | DEVOLUCION |
| Operación financiera (5 tipos) | `OperacionFinancieraService` | según tipo | OPERACION_FINANCIERA |
| Cobro venta a crédito (banco) | `CobroCreditoService` | (banco) + MovimientoCliente | VENTA_CREDITO_COBRO |
| Pago proveedor (CPP) | `PagoProveedorService` | PAGO_PROVEEDOR / (banco) + MovimientoProveedor | PAGO_CPP |
| Cheque / acreditación POS | `ChequeGestionService` / `AcreditacionPosService` | (banco) | CHEQUE / ACREDITACION_POS |

**Replicación (clave):** `retiro`, `venta_credito`, `venta_credito_cuota`, `cobro` son **BRANCH_TO_MAIN**
(llegan a central por replicación lógica PG, NO por Spring). Por eso el puente Retiro→caja mayor es un

**poller `@Scheduled`** reconciliador (patrón SIFEN), no un evento. `RetiroTesoreriaProcesador` es un bean
separado del scheduler para que `@Transactional` aplique (no self-invocation). **DA8:** el cobro en efectivo
es exclusivo del POS filial; central cobra solo banco/cheque.

## 4. Operaciones financieras (5 tipos)
CAMBIO_DIVISA (egreso+ingreso caja), DEPOSITO_BANCARIO (egreso caja + entrada banco), RETIRO_BANCARIO
(salida banco + ingreso caja), TRANSFERENCIA_ENTRE_CAJAS (salida+entrada caja), TRANSFERENCIA_BANCARIA
(banco→banco, **no toca caja**). Los que tocan dos lados lockean en orden canónico.

## 5. CPC / CPP (doble ledger)
- **CPC** (`CobroCreditoService`): `VentaCredito`/`Cuota` como cuenta por cobrar; cobro por banco,
  parcial nativo (`monto_cobrado`/`estado_cobro`), tolerancia de redondeo, doble ledger
  (banco + `MovimientoCliente`), finaliza la venta si todas las cuotas quedan cobradas.
- **CPP** (`PagoProveedorService`): `SolicitudPago` como cuenta por pagar; pago **mixto**
  (varias líneas caja/banco), `PAGO_PROVEEDOR`, doble ledger (egreso + `MovimientoProveedor`),
  transición PENDIENTE→PARCIAL→CONCLUIDO.
- **El monto de una solicitud de compra nunca tiene mas decimales que su moneda.** El motor rechaza
  un exceso mayor a `0.005`, asi que una deuda de `899854.5` guaranies no se salda con ningun pago
  (SP-001338, bodega, 2026-10-08). `SolicitudPagoNotaRecepcionService.redondearAMoneda` (HALF_UP a
  `moneda.decimales`) se aplica al guardar el `monto_incluido` de cada nota y al recalcular el total;
  el total es la **suma de lo incluido ya redondeado**, no el redondeo de la suma cruda. `V238.1`
  normalizo las de compra abiertas y sin pagos. **No cubierto:** GASTO y RRHH (toman el monto del
  documento de origen) y el plan de formas de pago (`solicitud_pago_detalle`).

## 6. Cheques + POS
- **Cheque** (`ChequeGestionService`): diferido reserva saldo; contado debita + COBRADO; cobrar
  diferido libera la reserva **antes** de debitar; anular bloquea si cobrado. Chequera con
  numeración incremental + AGOTADA.
- **POS** (`AcreditacionPosService` + `AcreditacionPosScheduler`): crea PENDIENTE (comisión + minutos),
  scheduler acredita las vencidas (idempotente por estado + lock), verificación con ajuste diferencial.

## 7. Concurrencia (regla del módulo)

**Todo servicio que muta un saldo toma lock pesimista** (`lockById`/`lockByCajaVirtualIdAndMonedaId`)
antes de leer-modificar-escribir: `TesoreriaService`, `BancoLedgerService`, `ClienteCuentaService`,
`ProveedorCuentaService`, `ChequeGestionService`, `AcreditacionPosService`, `CobroCreditoService`,
`PagoProveedorService`. Transferencias/operaciones de dos lados lockean en **orden canónico (id asc)**.

**Una reversa, una sola vez (issue #376).** El contra-movimiento se postea en `TesoreriaService.revertir`
(caja) y en `BancoLedgerService.revertir` (banco), y ahí vive el control: exigen el permiso sobre la caja,
toman el movimiento original con lock, leen su estado **de la base** y rechazan si ya está revertido («El
movimiento #N ya está anulado.»). Cubre a todos los módulos dueños —vale, liquidación, finiquito, entrada
varia, operación financiera, pago CPP, cheque, verificación de retiro— sin que cada uno tenga que acordarse.
Para quien llama, un rechazo es el rollback completo de su anulación.

- **Lock y después relectura, siempre en ese orden y con una proyección** (`findActivoById`,
  `findAnuladoById`, `PagoRepository.findEstadoById`): `lockById` espera el lock, pero si la entidad ya
  estaba cargada en la request devuelve esa misma instancia sin refrescar. Leer el estado de la entidad
  después de un `lockById` es leer el de antes de esperar.
- Los documentos que se anulan (`EntradaVaria`, `OperacionFinanciera`, `Pago`, el `Retiro` de una
  verificación) se toman con lock antes de decidir, para que la segunda anulación reciba el mensaje del
  documento. `ValeService.anular` y las liquidaciones no lockean su documento: dos anulaciones simultáneas
  las corta `revertir`, con el mensaje del movimiento.
- Orden: documento → movimiento → saldo / cuenta. Las patas de una operación financiera se revierten por
  caja ascendente y después por cuenta ascendente, igual que se postean.
- **Consecuencia:** un documento vivo cuyo movimiento ya está inactivo (dato inconsistente) no se puede
  anular por el camino normal; hay que corregir el dato. Antes se «anulaba» devolviendo la plata otra vez.
- Inversiones de orden que siguen existiendo (PostgreSQL aborta una de las dos transacciones; no corrompen
  datos): `anularPagoCpp` toma los saldos antes que las solicitudes y `procesarEvento` al revés;
  `anularPagoCpp` revierte los detalles en el orden del cliente, no caja → cuenta; y cada `registrar` en
  Gs/Rs/Ds actualiza además la fila `caja_virtual` (el shim).
- `ReversasIT` prueba lo que los mocks no ven (relectura real y reversas simultáneas). **No corre en CI**:
  `./mvnw -Dit.financiero=true -Dtest=ReversasIT test`.

**Casos de diferencia de un retiro (issue #376).** Tomar, soltar y resolver un caso viven en
`RetiroCasoService`: cada operación es una transacción y toma el caso con lock y con su estado recién leído
(`entityManager.refresh(caso, PESSIMISTIC_WRITE)`; nada puede modificar el caso antes, porque el `refresh`
descarta lo que no se haya escrito).

- **Resolver y anular la verificación van juntos.** Si la anulación se rechaza —la caja ya no tiene lo
  acreditado, sin permiso sobre la caja, ya anulada— el caso tampoco queda resuelto y se puede resolver de
  nuevo. Pedir anular sobre un caso sin verificación se rechaza.
- Cuando se va a anular, el **retiro se toma antes que el caso**: `RetiroVerificacionService.anular` arranca
  por el retiro, y al revés se trabarían. Solo en ese caso: el retiro llega por replicación y un lock sobre su
  fila frena al apply worker mientras dure.
- **Asignar** no reabre un caso resuelto ni se lo saca a quien lo investiga (salvo el superusuario); volver a
  tomar el propio no hace nada.
- `anular` cierra el caso de su verificación con un **UPDATE dirigido** (`cerrarPorAnulacion`), no leyéndolo y
  guardándolo: guardar la entidad escribe todas sus columnas con lo leído antes, y le pisaba el veredicto a
  quien estuviera resolviendo el caso en ese momento.
- Sigue sin control: quién puede soltar un caso ajeno, y a quién se le puede asignar uno por API.
- `RetiroCasoIT` prueba la atomicidad y la carrera con una anulación directa. **No corre en CI**:
  `./mvnw -Dit.financiero=true -Dtest=RetiroCasoIT test`.

**Una transferencia entre cajas se anula completa (issue #376).** `TesoreriaService.transferir` deja las dos
patas apuntándose entre sí por `referencia_id`, y `anular` sobre cualquiera de las dos —`TRANSFERENCIA_SALIDA`
o `TRANSFERENCIA_ENTRADA` con origen `MANUAL` o vacío— anula las dos en la misma transacción. Antes revertía
solo la pata pedida: la plata volvía al origen sin salir del destino.

- Orden: permiso de escritura sobre **las dos cajas** → la otra pata, por su vínculo → lock de los dos
  movimientos por **id ascendente** (dos anulaciones que entran cada una por una pata se cruzarían si cada
  una tomara primero la suya) → estado de las dos, de la base → reversas por caja ascendente, como
  `transferir` toma los saldos.
- El vínculo solo no alcanza (`saveMovimientoCajaVirtual` acepta el `referenciaId` del cliente): la otra pata
  tiene que ser del tipo opuesto, con las mismas cajas, moneda y monto, estar en su caja y apuntar de vuelta
  (`MovimientoCajaVirtualVinculo.esContraparteDe`).
- Si la otra pata ya estaba anulada **y tiene su contra-movimiento**, se anula solo la pedida: completa una
  transferencia que había quedado a medias.
- **Sin vínculo válido se rechaza** («No se pudo identificar la otra pata…»); no se empareja por cajas, monto
  y fecha. Vale para las transferencias anteriores a este cambio: se resuelven a mano. En bodega no había
  ninguna al 2026-10-09.
- Si la caja destino ya gastó lo recibido, no se anula nada y el rechazo nombra la caja. El límite de días
  (CN4) se mide sobre la pata más vieja.
- Las patas de una **operación financiera** llevan su propio origen y se siguen anulando desde la operación.
- En un movimiento con origen `MANUAL` el `referencia_id` es el id de la otra pata; con origen
  `OPERACION_FINANCIERA`, el de la operación. Quien lo lea tiene que mirar el origen.

**Movimientos y transferencias de caja en varias monedas (issue #376).** Los diálogos de la caja mayor dejan
cargar Gs, Rs y Ds a la vez. `registrarMovimientosCajaVirtual` (ingreso, egreso o ajuste) y
`realizarTransferenciasCajaVirtual` reciben todos los montos y los registran en **una transacción**
(`MovimientosCajaEnLoteService`): entra todo o no entra nada. Antes el desktop mandaba un pedido por moneda y
un rechazo de la segunda dejaba la primera adentro.

- Orden: validar el pedido → clave de idempotencia (§7.1) → permiso sobre las cajas → **todos los saldos del
  lote con lock, por (caja, moneda) ascendente** → registrar, moneda por moneda, con `TesoreriaService`.
- **Los saldos se toman antes y juntos.** El orden del módulo es por caja; registrar de a una moneda toma
  (A,Gs), (B,Gs), (A,Rs)… y se cruza con un pago mixto. Además cada `registrar` en Gs/Rs/Ds escribe la fila
  `caja_virtual` (el shim) antes de pedir el saldo de la moneda siguiente, y se cruza con cualquier movimiento
  suelto de esa moneda. `MovimientosCajaEnLoteIT` lo reproduce si se saca ese paso.
- Después de tomar los saldos relee la fila de cada caja: el chequeo de permiso ya la había cargado, y el
  shim se guarda con la fila entera (`CajaVirtual` no tiene `@DynamicUpdate`).
- Inversión que queda: `RetiroVerificacionService.acreditar` y los grupos de un pago mixto recorren las
  monedas de una misma caja en el orden en que vienen, no ascendente. Contra un lote de varias monedas sobre
  esa caja pueden cruzarse; PostgreSQL aborta una de las dos y no corrompe nada.
- Validaciones, todas antes de tocar nada: de 1 a 10 montos, monedas existentes (una inexistente no cae a
  guaraníes) y sin repetir, monto finito de hasta 4 decimales, mayor que cero —o distinto de cero y con signo
  en el ajuste—. El movimiento queda a nombre del **usuario de la sesión**.
- Cada moneda queda como un movimiento, o un par de patas vinculadas, independiente: **se anulan por
  separado**. Los movimientos de un lote no comparten ninguna columna.
- El pedido repetido sobre un lote cuyo primer movimiento se anuló se rechaza; solo mira el primero.
- `saveMovimientoCajaVirtual` y `realizarTransferenciaCajaVirtual` (una moneda) siguen existiendo para los
  desktops anteriores y el ajuste por conteo. Siguen tomando el usuario que manda el cliente, y la primera
  acepta cualquier tipo de movimiento y montos sin validar.
- `MovimientosCajaEnLoteIT` prueba la atomicidad, el repetido y la concurrencia. **No corre en CI**:
  `./mvnw -Dit.financiero=true -Dtest=MovimientosCajaEnLoteIT test`.

**Cancelar un retiro o un gasto no es un interruptor (issue #376).** `cancelarRetiro` y `cancelarGasto`
reciben `cancelar: Boolean` —`true` cancela, `false` habilita— y dejan el estado pedido: repetirlos no cambia
nada. Antes invertían el estado en cada llamada y un reintento o un doble clic deshacía la cancelación.

- **Sin el argumento** (un desktop anterior al cambio) significa cancelar y **nunca habilita**: sobre uno ya
  cancelado se rechaza. «Habilitar» necesita el desktop que manda el argumento.
- Las dos exigen superusuario en el central (`requireSuperusuario`); el desktop ya escondía el botón.
- Lock (`lockByIdAndSucursalId`) → estado por proyección (`RetiroSituacion`, `findCanceladoYSolicitud`) →
  **UPDATE dirigido** de la columna (`marcarCancelado`, `marcarConcluido`), que además limpia el contexto de
  persistencia. No se guarda la entidad: `Retiro` y `Gasto` no tienen `@DynamicUpdate` y un `save` reescribe
  la fila entera con lo que hubiera cargado. Tampoco se pasa por el `save` del servicio, que publica la
  notificación de «retiro / gasto realizado».
- **Un retiro que ya entró a la caja mayor no se cancela** (lista blanca: estado nulo o `CONCLUIDO`, sin caja
  mayor, sin movimiento, sin verificación vigente). Cancelado deja de descontar de la caja del PDV; si además
  está acreditado, la plata queda contada dos veces. Primero se anula la verificación.
- **Un retiro cancelado no entra a la caja mayor:** `verificar` e `ingresarACajaMayor` lo rechazan,
  `RetiroTesoreriaProcesador.procesar` no lo postea y `findFlotantes` no lo lista. Los cuatro —y cancelar—
  toman el mismo lock del retiro, lo primero. Anular una verificación no habilita un retiro cancelado.
- Habilitar deja el retiro en `CONCLUIDO` y no tiene guarda: un cancelado que quedó con su ingreso en la caja
  mayor (dato anterior a este cambio) se corrige habilitándolo.
- Un gasto pagado desde la caja mayor (`solicitud_pago_id`) no se cancela ni se habilita acá: se anula su pago.
- **Rollback del JAR:** el automático del deploy (falla el health check) no tiene riesgo, porque el JAR nuevo
  no llegó a numerar. **Desplegar a mano una versión anterior después de haber operado es de una sola vía
  mientras haya huecos:** el JAR anterior vuelve a contar; mientras `count() + 1` caiga en un hueco entra
  (reusa ese número), y en cuanto cae sobre uno existente se traba: ninguna alta de solicitud entra, de
  ningún tipo, y no se destraba sola. No queda nada a medias. La salida es volver a este JAR: al arrancar se
  realinea sin pasos manuales. **Antes de un rollback manual** mirar si hay huecos: `select count(*),
  max(numero_solicitud) from operaciones.solicitud_pago` (los hay si la cantidad es menor que el número más
  alto).
- El verificador no frena el arranque. Si falló, queda en el log como `ERROR` con el nombre de la secuencia:
  mirarlo después de desplegar.
- Si alguien inserta a mano un número mayor que la secuencia, las altas fallan contra el índice, una por
  número, hasta pasarlo; reiniciar el central la realinea de una vez.
- `SolicitudPagoNumeroIT` (no corre en CI): solicitud borrada del medio y secuencia atrasada.
  `./mvnw -Dit.financiero=true -Dtest=SolicitudPagoNumeroIT test`.

**Saldo de la cuenta bancaria (`BancoLedgerService`).** `registrar` y `ajustarReservado` toman la cuenta con
lock y **la releen de la base** (`cuentaConLock`). Sin el `refresh`, la instancia que el resolver ya había
cargado (una operación financiera busca sus cuentas antes de entrar a la transacción) traía el saldo de antes
de esperar el lock: de dos movimientos simultáneos sobre la misma cuenta, el segundo pisaba al primero. Es la
regla del módulo —`lockById` no refresca lo ya cargado— aplicada al único punto que escribe ese saldo.
`BancoLedgerSaldoIT` lo reproduce (no corre en CI).

## 8. Seguridad por rol
`TesoreriaSecurityService` (patrón self-contained, issue #177): resuelve el usuario por el nickname del
SecurityContext, lee roles de DB, bypass ADMIN. Roles `TESORERIA VER`/`TESORERIA GESTIONAR` (migración
`V176.5`). **Todos** los resolvers financieros llaman `seg.requireVer()` (queries) / `seg.requireGestionar()`
(mutations). `cajaVirtualesActivas` es lectura compartida tesorería **o** RRHH.

**`Pago` es un evento del motor (issue #304).** Lo crean y lo cambian solo `PagoProveedorService` (pago →
`CONCLUIDO`, `anularPagoCpp` → `CANCELADO`, con la reversión de caja/banco y la reapertura de solicitudes), por
`pagoService.save` en Java. La mutation vieja `savePago` (pantalla «Pagos» del desktop, hoy inalcanzable) pasa por
`PagoService.guardarManual`: exige `GESTIONAR`, solo asigna `ABIERTO`/`PENDIENTE`, rechaza editar un pago
`CONCLUIDO`/`PARCIAL`/`CANCELADO` y conserva `usuario`/`creadoEn`. Las mutations de `PagoDetalle`/`PagoDetalleCuota`
exigen `GESTIONAR` y sus queries `VER`.

**Solicitudes de pago por el resolver de compras (issue #306).** Compras no tiene rol propio, así que
`SolicitudPagoGraphQL` decide por el **tipo** de la solicitud (`findTipoById`, proyección) y no por rol:
- `COMPRA`: lectura y mutations sin rol. `GASTO`: lectura e impresión sin rol (el módulo de gastos tampoco tiene rol).
- `RRHH`: `solicitudPago`, `notasAsociadasASolicitud` e `imprimirSolicitudPago*` exigen rol de tesorería **o** de RRHH
  (mismo idioma que `cajaVirtualesActivas`).
- Las mutations de compras (actualizar, borrar, cambiar estado, notas, detalles) rechazan todo lo que no sea `COMPRA`.
  **La guarda va en el resolver, no en `SolicitudPagoService`**: el motor concluye las obligaciones con
  `solicitudPagoService.actualizarEstado`.
- Los rechazos no nombran el tipo («No autorizado para ver la solicitud #n.», «no es de compras»), para no revelar qué id
  es una obligación de RRHH.
- `Pago.solicitudesPago` (`PagoResolver`) omite las `RRHH` sin rol: desde una compra se llega al pago, y un pago previo a
  #302 pudo mezclar tipos. Consulta roles solo si el pago tiene alguna `RRHH`; si un listado llegara a pedir ese campo,
  cachear los roles por request.

`ChequeGraphQL` (CRUD plano, sin consumidores) y `ChequeraGraphQL` exigen `requireVer` en queries y `requireGestionar` en
mutations; emitir/cobrar/anular siguen por `ChequePosGraphQL`. `SolicitudPagoRecepcionGraphQL` es código muerto (tabla
dropeada en `V95.5`).

## 9. Migraciones (todas aditivas, sufijo `.5`)
`V176.5` roles · `V177.5` núcleo (saldo por moneda, origen, backfill) · `V178.5` config base ·
`V179.5` puente retiro · `V180.5` bancos + operaciones · `V181.5` CPC/cuenta cliente ·
`V182.5` CPP/cuenta proveedor · `V183.5` cheques + POS · `V184.5` config (CN4/CN10).

## 10. Schedulers (off por default, `@ConditionalOnProperty`)
- `tesoreria.retiro-poller.enabled` → `RetiroTesoreriaScheduler` (puente PDV→caja mayor).
- `tesoreria.acreditacion-pos.enabled` → `AcreditacionPosScheduler` (acreditación POS automática).

## 11. Tests
Suite en `src/test/.../service/financiero/` (38 tests): `TesoreriaServiceTest`, `ComprobanteSerieServiceTest`,
`RetiroTesoreriaProcesadorTest`, `OperacionFinancieraServiceTest`, `CobroCreditoServiceTest`,
`PagoProveedorServiceTest`, `ChequeGestionServiceTest`, `TesoreriaReporteServiceTest`.

## 12. Pendiente (follow-up, no bloqueante del backend MVP)
UI desktop de tesorería completa; notificaciones (reusar `PushNotificationService` + canal saliente, AJ-4);
reportes cierre-mes (aging CPC/CPP, flujo de caja); `pago_solicitud_detalle` persistencia línea-a-línea;
"toda compra → CPP" wiring en Compras (DA2); migrar `VentaTarjeta.estado` a enum; wiring venta-tarjeta →
`crearAcreditacionPos`; cobro consolidado por convenio; permisos `CPP_*` dedicados (DA6); limpieza dead-code.

## 13. Cancelación de venta (la cadena de `VentaService.cancelarVenta`)

Una venta se cancela **siempre** por `VentaService.cancelarVenta`. Es el único camino que deja todo
coherente: estado de la venta, `movimiento_caja` (activo), `movimiento_stock` (estado),
`venta_tarjeta`, `delivery`, `venta_credito`, evento de cancelación del DE en SIFEN y
`factura_legal.activo = false`. Nada más cambia el estado de una venta con un `save` directo
(issue #339: `cancelarVentaCredito` lo hacía y dejaba caja, stock y factura vivos).

Puertas de entrada, las dos con `seg.requireCancelarVenta()` (#340):

| Mutation | Qué hace |
|---|---|
| `cancelarVenta(id, sucId)` | **Alterna**: cancela una venta CONCLUIDA y reactiva una CANCELADA |
| `cancelarVentaCredito(id, sucId)` | **Solo cancela**, por la misma cadena; sobre una venta ya cancelada da error. Ningún cliente la llama desde el desktop 3.1.0 |

`VentaCreditoService.cancelarVentaCredito(id, sucId, venta)` es un paso de la cadena (sincroniza la
venta crédito con el estado de la venta), no una entrada: exige la venta.

Lo que hay que saber antes de tocar la cadena:

- **Reactivar no es el inverso de cancelar.** El bloque de la factura no mira hacia dónde va la
  venta: al reactivar también pide la cancelación del DE y deja la factura inactiva. Y la venta
  crédito vuelve a ABIERTO aunque antes estuviera FINALIZADO.
- **SIFEN no se revierte.** `SifenEventoService.cancelarDE` corre en `REQUIRES_NEW`: el evento queda
  enviado aunque después falle el resto y la transacción de la venta haga rollback. Si SIFEN falla,
  el error se traga y la factura queda inactiva con el DE vivo.
- **No hay lock.** Dos llamadas simultáneas leen el mismo estado.
- **Tres tablas de la cadena no bajan a la filial.** Todas son `BRANCH_TO_MAIN`; solo vuelven a la
  filial dueña las que tienen `replicate_central_to_branch_with_filter` (V113, V150.1, V161.3):

  | El cambio del central llega a la filial | No llega |
  |---|---|
  | `operaciones.venta`, `financiero.factura_legal`, `operaciones.movimiento_stock`, `financiero.venta_tarjeta` | `financiero.movimiento_caja`, `operaciones.delivery`, `financiero.venta_credito` |

  Una venta cancelada desde el central sigue con su movimiento de caja activo en la filial.

Tests: `CancelarVentaYFacturaRolTest`, `VentaCreditoServiceCancelarTest`,
`VentaServiceTarjetaCancelacionTest`.
