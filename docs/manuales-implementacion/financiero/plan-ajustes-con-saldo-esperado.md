# Plan: ajustes de saldo que dicen contra que saldo se hicieron (issue #376, bloque 4)

Rama: `fix/financiero-ajustes-con-saldo-esperado` (central) + una rama del desktop.

El bloque 4 de la issue junta cuatro temas independientes. Este plan cubre el primero: **el ajuste por conteo
de caja y el ajuste de saldo bancario**. Quedan para planes propios: la marca de «ya ingresado» del cierre de
un maletin, las validaciones al emitir un cheque y el limite de caja chica, y el numero de comprobante.

## El problema

Los dos ajustes son **relativos** y se calculan en el desktop con el saldo que tenia en pantalla:

- **Conteo de caja** (`conteo-caja-dialog`): el usuario cuenta billetes, el desktop resta `contado − saldo que
  veia` y manda un `AJUSTE` por esa diferencia con `saveMovimientoCajaVirtual`.
- **Saldo bancario** (`ajustar-saldo-cuenta-dialog`): el usuario elige suma o resta y un monto, viendo el saldo
  de la cuenta; va por `ajustarSaldoCuentaBancaria(cuentaBancariaId, monto, positivo, motivo)`.

El central aplica la diferencia sobre el saldo que tenga **en ese momento**, sin saber contra cual se calculo:

1. **Saldo que cambio:** si entre que se abrio el dialogo y se confirmo entro otro movimiento (un retiro
   verificado, un pago), la diferencia ya no es la real y el ajuste deja la caja en un saldo que nadie conto.
   La lista de cuentas bancarias solo se relee al cerrar un dialogo: su saldo puede llevar horas en pantalla.
2. **Dos personas ajustando lo mismo:** las dos aplican su diferencia.
3. **Reintento:** si la respuesta se pierde, hoy los dos dialogos se cierran y avisan «comparalo antes de
   repetirlo» (#390). Mitiga, pero deja la decision en el usuario.

Ademas el ajuste por conteo entra por `saveMovimientoCajaVirtual`, que toma el usuario y la descripcion que
mande el cliente.

Produccion (solo lectura, 2026-10-09): bodega tiene 1 ajuste por conteo y ningun ajuste bancario manual;
farmacia, 3 y 5. Ninguno repetido.

## Alcance

Central: una mutation nueva para el conteo, dos argumentos opcionales en la del banco y un servicio que las
ejecuta. Desktop: los dos dialogos. La PWA y el filial no usan ninguna de las dos (grep vacio).

Fuera: `saveMovimientoCajaVirtual` (sigue aceptando cualquier tipo y usuario) y el «Ajuste de Saldo» del
dialogo de ingreso / egreso, que es un monto que tipea el usuario, no una diferencia contra un saldo; pasa a
ir en un pedido con clave de idempotencia en el PR #393, todavia sin mergear.

## Diseno

Un servicio nuevo, `AjusteDeSaldoService`, con una transaccion por pedido. En los dos casos: tomar el saldo
con lock, **refrescar la entidad** (`entityManager.refresh`: el lock devuelve la instancia que ya estuviera
cargada, y `registrar` calcula con ella; con solo una proyeccion, la comprobacion y el registro podrian mirar
saldos distintos), comparar y registrar. El resolver no carga nada antes.

### Conteo de caja: el central calcula la diferencia

```graphql
ajustarCajaVirtualPorConteo(cajaVirtualId: ID!, monedaId: ID!, saldoEsperado: Float!, contado: Float!): MovimientoCajaVirtual!
```

El desktop manda lo que vio (`saldoEsperado`) y lo que conto (`contado`); la resta la hace el central:

1. `requireGestionar`, usuario de la sesion, permiso de escritura sobre la caja.
2. Validar: caja y moneda existentes; los dos montos finitos; `contado` no negativo. `contado` se **redondea** a
   4 decimales, no se rechaza: es una suma hecha en JavaScript y puede traer `12.350000000000001`.
3. `ensureRow` + lock del saldo `(caja, moneda)` + refresh. No se toca la fila de la caja antes.
4. Comparar:
   - saldo actual = `contado` → rechazo: «El saldo de la caja ya coincide con lo contado: no hace falta
     ajustar.» Es lo que recibe el reintento de un ajuste que si habia entrado.
   - saldo actual ≠ `saldoEsperado` → rechazo: «El saldo de la caja cambió desde que abriste el conteo (era X,
     ahora es Y). Volvé a abrirlo: lo contado no se pierde.»
   - si no → `AJUSTE` por `contado − saldo actual`, origen `MANUAL`, con la descripcion armada en el central:
     «AJUSTE POR CONTEO DE CAJA (SISTEMA X / CONTADO Y)».

**No necesita clave de idempotencia:** el conteo es absoluto. Repetido despues de aplicarse cae en uno de los
dos rechazos; y si por otros movimientos el saldo volviera justo al esperado, aplicarlo de nuevo deja otra vez
el saldo en lo contado, que es lo que se pidio.

`saldoEsperado` se compara como llego: coincide si es igual al saldo redondeado a 4 decimales **o** igual a su
valor en `Double` (es el camino por el que el desktop lo recibio; en saldos muy grandes el `Double` no guarda
los 4 decimales).

La diferencia ya no se redondea a los decimales de la moneda, como hace hoy el desktop: el saldo queda
exactamente en lo contado. El `AJUSTE` puede llevar decimales que la moneda no usa si el saldo los tenia.

### Saldo bancario: saldo esperado y clave

```graphql
ajustarSaldoCuentaBancaria(cuentaBancariaId: ID!, monto: Float!, positivo: Boolean!, motivo: String!,
    saldoEsperado: Float, claveIdempotencia: String): MovimientoBancario!
```

Este ajuste es **relativo** (suma o resta un monto), y ahi el saldo esperado solo no alcanza: si despues del
ajuste entra un movimiento opuesto por el mismo monto, el saldo vuelve al esperado y un reintento lo aplicaria
otra vez. Por eso lleva las dos cosas:

- `claveIdempotencia` (el `IdempotenciaService` de §7.1, tabla existente): el **reintento** del mismo pedido
  devuelve el movimiento original; si ese movimiento se anulo, se rechaza. Es lo primero que toma la
  transaccion. Huella: cuenta, monto, sentido, motivo y saldo esperado.
- `saldoEsperado`: cubre el saldo viejo en pantalla y a dos personas ajustando. Lock de la cuenta + refresh;
  si no coincide: «El saldo de la cuenta cambió (era X, ahora es Y). Revisá sus movimientos antes de ajustar:
  si venías de un ajuste sin confirmar, puede que ya haya entrado.»
- Sin los argumentos (desktop anterior): como hoy. Se conserva `requireGestionar` y se suma la validacion de
  monto finito.

Aca no se cambia a «saldo real del extracto» (que el central calcule la diferencia): el dialogo pide monto y
sentido, y cambiar eso es rediseñar la pantalla.

### Desktop (PR propio, despues de desplegar el central)

- `conteo-caja-dialog`: llama a la mutation nueva con `saldoEsperado = data.saldoSistema` y `contado = total`.
  Ya no arma la descripcion ni manda el usuario. La confirmacion muestra la diferencia sin redondear.
- `ajustar-saldo-cuenta-dialog`: manda `saldoEsperado = saldoActual` y una clave por intento. Si no hay
  respuesta queda en «No se pudo confirmar» con Reintentar / Cerrar, el patron de cheques y pagos (hoy cierra y
  pide comparar a mano).
- **Rechazo por saldo** («cambió» / «ya coincide»): los dos dialogos **cierran** avisando a quien los abrio,
  que relee. Hoy quedan abiertos con el saldo viejo, y con `saldoEsperado` fijo cada reintento volveria a
  fallar. Lo contado sigue en `localStorage`. Los demas rechazos (sin permiso, monto invalido) dejan el dialogo
  abierto, como hoy. Se distinguen por el texto: el error de GraphQL solo trae el mensaje.

Con un central anterior, el desktop nuevo falla la validacion del schema en los dos dialogos: el central va
primero en cada canal.

## Tabla de datos nuevos

| Dato | Donde | Nota |
|---|---|---|
| mutation `ajustarCajaVirtualPorConteo` | central | nueva |
| argumentos `saldoEsperado: Float`, `claveIdempotencia: String` | `ajustarSaldoCuentaBancaria` | opcionales |
| filas en `financiero.operacion_idempotente` | central | tabla existente (V237.1), operacion nueva |

Sin columnas, sin migracion, sin cambios de replicacion.

## Fases

Central (un PR):

1. **Conteo.** Tests: aplica `contado − saldo` y el saldo queda en lo contado; saldo = contado → rechazo;
   saldo ≠ esperado → rechazo con los dos valores; lock y refresh antes de comparar; `contado` con ruido de
   punto flotante se redondea; NaN, infinito y negativo se rechazan; saldo actual negativo se ajusta; usuario
   de la sesion, origen `MANUAL` y descripcion del central; sin permiso → rechazo antes del lock.
2. **Banco.** Tests: con saldo esperado correcto ajusta; incorrecto rechaza sin registrar; sin los argumentos
   ajusta como hoy; la clave se toma antes que la cuenta; el repetido devuelve el original y anulado se
   rechaza.
3. **Tests de integracion** (`-Dit.financiero=true`): seis ajustes por conteo iguales a la vez → uno entra;
   la entidad ya cargada con el saldo viejo no engaña a la comparacion; en el banco, el mismo pedido con su
   clave no se repite **aunque el saldo haya vuelto al esperado**, y sin clave el saldo esperado rechaza.

Desktop (otro PR):

4. Queries, servicios y los dos dialogos. `verificar:imports`, build de produccion y prueba en Chrome
   (incluida la respuesta perdida y el reintento).

Cada test de bug se corre con el fix neutralizado para ver que falla.

## Prueba de runtime (central local, perfil `dev`)

- Conteo con faltante y con sobrante: un `AJUSTE` por la diferencia, descripcion del central.
- El mismo pedido otra vez: «ya coincide», sin movimiento nuevo.
- Con un movimiento en el medio: «cambió desde que abriste el conteo», sin ajuste.
- Banco: ajuste con `saldoEsperado` correcto; repetido → rechazo; sin el argumento → como hoy.

## Despliegue y rollback

Sin migracion. Requiere reinicio del central. Rollback del JAR: inocuo para los datos; el desktop nuevo deja
de poder ajustar por conteo y ajustar saldo bancario contra esa instancia. El desktop no se promueve a beta /
stable hasta que farmacia y bodega tengan el central.

## Decisiones tomadas (Franco, 2026-10-09: las tres, como se recomiendan)

1. **El central calcula la diferencia del conteo** (mutation nueva) en vez de agregar `saldoEsperado` a
   `saveMovimientoCajaVirtual`. Recomendacion: mutation nueva; la otra acepta cualquier tipo y cualquier
   usuario y conviene no sumarle mas usos.
2. **El banco conserva monto + sentido, y suma saldo esperado y clave de idempotencia.** Recomendacion: si.
   El costo es el estado «sin confirmar» con Reintentar en ese dialogo.
3. **Ante un rechazo por saldo, los dos dialogos se cierran** para que se relea. Recomendacion: si.

## Queda sin verificar

- Con montos de mas de ~15 digitos significativos el `Double` del movimiento no guarda los 4 decimales (ya es
  asi en `registrar`). No es realista con los saldos actuales.
- `saveMovimientoCajaVirtual` sigue dejando postear un `AJUSTE` cualquiera sin pasar por aca.
- Un `AJUSTE` por conteo se puede anular por API (el desktop no ofrece anular ajustes): vuelve el saldo al de
  antes del conteo. Es como hoy.
- Un desktop sin actualizar sigue ajustando sin ninguna de estas protecciones.

## Auditoria del plan (paso 5, 2026-10-09)

| Hallazgo | Que se hizo |
|---|---|
| Banco: si el saldo vuelve al esperado, el reintento duplica el ajuste (es relativo) | clave de idempotencia ademas del saldo esperado |
| Comparar con una proyeccion y registrar con la entidad pueden mirar saldos distintos | refresh de la entidad tras el lock |
| Rechazar por mas de 4 decimales tumba conteos legitimos (suma en JavaScript) | se redondea |
| Los dialogos quedan abiertos con el saldo viejo tras el rechazo | cierran en los rechazos por saldo |
| El mensaje del banco no dice que el ajuste anterior pudo haber entrado | mensaje |
| `origenTipo` del ajuste por conteo | `MANUAL` |
| `saldoEsperado` en saldos enormes: el `Double` no guarda 4 decimales | doble comparacion |
| La motivacion sobrevendia el reintento; el #393 todavia no esta en develop | texto corregido |
| El dashboard no usa el shim `saldo_gs/rs/ds` para el conteo (usa `caja_virtual_saldo`) | verificado, sin cambio |
