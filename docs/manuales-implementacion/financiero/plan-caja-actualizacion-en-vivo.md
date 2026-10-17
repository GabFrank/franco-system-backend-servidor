# Plan — caja mayor actualizada en vivo

Rama `feature/caja-actualizacion-en-vivo` en **central** y **desktop**. Pedido de Franco del 2026-10-17.

## Qué resuelve

El tablero de la caja mayor (`caja-virtual-dashboard`) ya se relee después de cada operación hecha **desde
ese mismo tablero**. No se entera de lo que hacen otros:

- otro tesorero que opera la misma caja;
- pagos que mueven la caja desde otras pantallas (gastos, RRHH, pagos a proveedor, operaciones
  financieras, retiros verificados);
- el mismo tablero abierto en otra PC o en otra pestaña.

Además: el botón «Refrescar» del tablero relee solo la tabla, no las cards de saldo; y la lista de cajas
(`list-caja-virtual`, columnas Saldo Gs/R$/U$D) queda con los saldos de cuando se abrió.

## Diseño

**El aviso solo dice «cambió la caja X» o «cambió la cuenta Y», y cuándo.** No lleva montos, nombres ni
movimientos. El cliente que lo recibe relee con las consultas que ya existen, que aplican rol y ACL de
caja.

### Central

1. `TesoreriaCambio(cajaVirtualId, cuentaBancariaId)`: uno de los dos ids va lleno. El mismo POJO es el
   evento de Spring y el tipo GraphQL (no hace falta una clase de evento aparte).
2. Se publica desde los **dos únicos puntos que mutan saldos** (regla 2 de la skill financiera; la
   auditoría lo verificó: `AjusteDeSaldoService`, `MovimientosCajaEnLoteService`, el poller de retiros,
   maletín y verificación de retiros terminan todos en estos métodos):
   - `TesoreriaService.aplicarSaldo` (todo movimiento de caja) y `recalcularSaldos`;
   - `BancoLedgerService.registrar` (y por él `revertir`) y `ajustarReservado` (cheques diferidos).
3. `TesoreriaCambioListener`, `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`:
   - pasa el aviso al publisher **solo si la transacción confirmó**: un pago rechazado o un lote revertido
     no avisan nada. Todos los llamadores actuales son `@Transactional`; `fallbackExecution` cubre a uno
     futuro que no lo sea, que si no se descartaría en silencio;
   - **envuelve todo en `try/catch (Throwable)` con `log.warn`**: una excepción en `afterCommit` le llega al
     llamador aunque la plata ya se haya registrado, y el usuario podría reintentar un pago que entró (#390).
4. `TesoreriaCambioPublisher`: mismo armado que `TransferenciaQrEscaneadoPublisher`, con tres diferencias:
   - emisor **serializado** (`emitter.serialize()`): avisan varios hilos a la vez y un `ObservableEmitter`
     no es thread-safe;
   - **entrega en otro hilo** (`observeOn(Schedulers.io())`): sin eso el envío por el websocket corre en el
     hilo que confirmó la transacción, y un cliente lento demora la respuesta del pago;
   - **buffer acotado por suscriptor** (`onBackpressureBuffer(1000, …, DROP_OLDEST)`): un desktop dormido con
     el socket a medio cerrar no acumula avisos sin límite. Perder avisos viejos de un cliente así no
     importa: al reconectar relee todo.
5. Schema, en `caja-virtual.graphqls`:
   ```graphql
   extend type Subscription { tesoreriaCambioSub: TesoreriaCambio }
   type TesoreriaCambio { cajaVirtualId: ID  cuentaBancariaId: ID }
   ```
   POJO `TesoreriaCambio` y resolver propio `TesoreriaCambioGraphQL implements GraphQLSubscriptionResolver`
   con el método `tesoreriaCambioSub()` (sin él kickstart no arranca y el deploy hace rollback solo).

Sin cambio de esquema, sin enum nuevo, sin variable de entorno. `cuenta_bancaria` se publica a las filiales,
pero no cambia su forma ni lo que se escribe en ella.

### Desktop

1. `TesoreriaCambioSubGQL` (`caja-virtual/graphql/`, con el alias `data:` como el resto) y
   `CajaVirtualService.onCambiosTesoreria()`:
   - **una sola suscripción** al central (`clientName: 'servidor'`) por quien escucha. No se reabre: con
     `reconnect: true`, `subscriptions-transport-ws` reenvía sola las operaciones vivas al reconectar
     (`client.js:402-411`), y reabrirla duplicaría los avisos;
   - además emite un aviso «sin id» cuando el websocket del central **pasa de caído a conectado**
     (`cloudConnectionStatusSub` con `distinctUntilChanged`, descartando el valor inicial del
     `BehaviorSubject`): lo que pasó durante el corte no llegó;
   - **un error del servidor no se avisa ni se reintenta**: es una mejora de comodidad. Con un central sin la
     suscripción (desktop actualizado antes que su central, o un canal atrasado), el tablero queda como hoy
     y sin «Ups! Algo salió mal». Por eso no usa `onCustomSub`, que avisa los errores.
2. `caja-virtual-dashboard`: escucha, se queda con los avisos de **su** caja, de una cuenta de sus
   `bancoCards` o «sin id», y relee con `auditTime(1000)`: a lo sumo una relectura por segundo mientras
   haya actividad (un pago genera varios avisos; con `debounce` un flujo continuo no releería nunca).
   La relectura de fondo:
   - usa los filtros **ya aplicados** (`filtrosAplicados`), no los controles: no aplica un filtro a medio
     escribir;
   - no muestra los avisos de lectura fallida (el usuario no hizo nada); igual marca los saldos como no
     disponibles, para que el conteo no ajuste contra un saldo viejo (#390).
3. «Refrescar» pasa a llamar a `recargar()` (saldos + bancos + movimientos).
4. `list-caja-virtual`: escucha, y si el aviso es de una caja de la página actual (o «sin id»), relee la
   página con el mismo `auditTime`, sin cambiar de página ni de filtros.

### Datos nuevos

| Dato | Quién lo escribe | Quién lo lee |
|---|---|---|
| `TesoreriaCambio.cajaVirtualId` | `TesoreriaService.aplicarSaldo` / `recalcularSaldos` → listener → publisher | `CajaVirtualDashboardComponent`, `ListCajaVirtualComponent` |
| `TesoreriaCambio.cuentaBancariaId` | `BancoLedgerService.registrar` / `ajustarReservado` → listener → publisher | `CajaVirtualDashboardComponent` (sus `bancoCards`) |

## Fases

| # | Repo | Commit | Tests |
|---|---|---|---|
| 0 | central y desktop | `docs(financiero): plan de caja mayor actualizada en vivo` (este archivo) | — |
| 1 | central | `feat(financiero): avisar por suscripcion los cambios de saldo de caja y banco` | ver abajo |
| 2 | desktop | `feat(caja): actualizar la caja mayor en vivo con los cambios de otros usuarios` | Sin batería en CI (§1 paso 9): gate `npm run check` + prueba por pantalla. |

Tests de la fase 1:

- `TesoreriaServiceTest`: registrar, transferir (avisa las dos cajas) y recalcular publican el evento.
- `BancoLedgerServiceTest`: registrar y ajustarReservado publican con la cuenta.
- `TesoreriaCambioListenerTest`, con un contexto Spring mínimo (`@EnableTransactionManagement`, sin base) y
  sincronización de transacción simulada: el evento **no** llega al publisher antes del commit ni con
  rollback, **sí** después del commit, y llega sin transacción activa (`fallbackExecution`). Si el publisher
  lanza, el listener no propaga.
- `TesoreriaCambioPublisherTest`: publicar sin suscriptores no lanza; un suscriptor recibe lo publicado.
- Revertir cada llamada que publica y comprobar que su test falla.

Los cuatro tests que construyen `TesoreriaService` / `BancoLedgerService` a mano (`TesoreriaServiceTest`,
`BancoLedgerServiceTest`, `ContraAsientoRrhhTest`, `LiquidacionSueldoNetoNegativoTest`) reciben el
`ApplicationEventPublisher` nuevo como mock.

## Orden de despliegue

Central primero, desktop después. Alpha, beta, farmacia y bodega son centrales distintos y un mismo desktop
puede hablar con uno que tiene la suscripción y otro que no. Las dos combinaciones funcionan:

- central nuevo + desktop viejo: nadie se suscribe, el aviso se descarta;
- desktop nuevo + central viejo: el central responde `FieldUndefined` (verificado contra el alpha
  `:8083`), el desktop lo descarta en silencio y el tablero queda como hoy.

El rollback de cualquiera de los dos deja la caja como está hoy. No toca la base.

## Prueba por pantalla (paso 9)

Central local en 8081 (perfil `dev`) y desktop con `ng serve -c web`. Sin filial: la caja mayor vive en
el central.

1. Abrir el tablero ya conectado: una sola carga inicial (log de red).
2. Dos pestañas con el tablero de la misma caja (A y B). En B, ingreso en efectivo: A muestra el
   movimiento y el saldo nuevo sin tocar nada.
3. En B, anular ese movimiento: A lo muestra tachado y el saldo vuelve.
4. Transferencia de la caja X a la caja Y desde B: el tablero de Y abierto en A se actualiza.
5. Operación financiera de depósito (caja → banco) desde B: en A cambian el saldo de caja y la card de
   la cuenta bancaria.
6. Pago de un gasto desde otra pantalla (no desde el tablero): A se actualiza.
7. Lista de cajas abierta en A, movimiento en B: la columna de saldo cambia, sin cambiar de página.
8. Un pago rechazado (saldo insuficiente): A no relee.
9. «Refrescar»: relee también las cards de saldo.
10. Filtro a medio escribir en A (sin «Aplicar») y movimiento en B: la tabla relee con el filtro anterior.
11. Anulación en vuelo y conteo abierto en A mientras B mueve la caja: la anulación no se desbloquea antes
    de tiempo; el conteo, al ajustar, lo rechaza el central si el saldo cambió.
12. Bajar y subir el central dos veces con el tablero abierto: al reconectar relee una vez, y un movimiento
    posterior genera **una** relectura, no dos.
13. Desktop nuevo contra un central sin la suscripción (el central de `develop`): el tablero abre sin
    aviso de error.

## Riesgos y lo que queda sin verificar

- **Relectura doble en la propia operación**: el diálogo ya llama a `recargar()` al cerrar y además llega
  el aviso. Son tres consultas más, una vez; los contadores de carga ya descartan la respuesta vieja.
- **Un aviso por movimiento**: un lote de N movimientos manda N avisos. No se deduplica en el central; el
  `auditTime` del desktop lo absorbe.
- **Cuántos reciben el aviso**: solo los desktops con un tablero o la lista de cajas abiertos. Los PDV no se
  suscriben.
- **Lo que no cambia saldos no avisa**: alta, edición o baja de una caja, cambios de acceso o de propietario,
  y correcciones por SQL. Para eso queda «Refrescar» (tablero) o volver a buscar (lista).
- **Cambiar la configuración del central con el tablero abierto**: `reconnectWebSockets()` crea un cliente
  nuevo pero el link de Apollo sigue atado al anterior (defecto previo, fuera de este alcance). La
  suscripción sigue en el servidor anterior hasta reabrir el tablero.
- **El conteo**: con el tablero en vivo, el saldo puede cambiar detrás del diálogo de conteo abierto. El
  central ya rechaza el ajuste si el saldo cambió; el rechazo va a ser más frecuente.
- **La PWA y la app mobile no tienen caja mayor** (grep sin resultados): no se tocan.
- No verificado todavía: si el envío de kickstart por el websocket bloquea el hilo (se evita igual con
  `observeOn`), y la entrega real con el desktop servido desde `[::1]:4200` (prueba 2).

## Registro de la ejecución (2026-10-17)

**Auditoría del diff (paso 8), 3 ejes fijos.** Sin bloqueantes. Aplicado: la relectura de fondo de la lista
de cajas no tenía guarda de secuencia y una respuesta lenta podía pisar una búsqueda nueva; ahora usa un
contador de carga como el tablero. El resto quedó como riesgo aceptado (un aviso por movimiento, absorbido
por el `auditTime`; carga proporcional a los desktops con la pantalla abierta).

**Prueba por pantalla (paso 9).** Central de la rama en `:8084` (perfil `dev`), desktop `ng serve -c web` en
`[::1]:4201`, usuario de prueba TESORERO1; los cambios «de otro» los hizo GERENTE12 por GraphQL. Todas pasaron:

| # | Resultado |
|---|---|
| 1 | Al abrir el tablero, una sola carga (4 consultas). |
| 2, 3 | Ingreso y anulación de otro usuario: fila nueva / tachada y saldo al instante. |
| 4, 6 | Transferencia 18 → 19 desde la pantalla de Operaciones Financieras: tablero y lista al instante. |
| 5 | Depósito de la caja 18 a la cuenta 1: la card bancaria del tablero de la 19 cambia; la caja 19 no. |
| 7 | Lista de cajas: el saldo cambia sin volver a buscar. |
| 8 | Egreso rechazado por saldo: ninguna consulta (no hubo aviso). |
| 9 | «Refrescar» relee también los saldos. |
| 10 | Filtro «Egreso» sin aplicar: la relectura de fondo usa el filtro anterior. |
| 11 | Anulación en vuelo (demorada 6 s): la fila sigue bloqueada durante la relectura; una sola contra-anulación en la base. Conteo abierto: el central rechaza el ajuste porque el saldo cambió. |
| 12 | Dos reinicios del central: una relectura al reconectar, el cliente reenvía las 2 suscripciones originales (sin duplicar) y un movimiento posterior da una sola relectura. |
| 13 | Contra un central sin la suscripción (simulado reescribiendo el campo en el websocket: `FieldUndefined`): un error por pantalla, sin reintento ni aviso; la pantalla funciona como hoy. |

Prueba directa del canal con un cliente websocket: ingreso → señal de la caja; egreso rechazado → nada;
transferencia → señal de las dos cajas; depósito → señal de la caja y de la cuenta.
