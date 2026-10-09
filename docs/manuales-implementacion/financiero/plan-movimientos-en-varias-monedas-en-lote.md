# Plan: movimientos y transferencias de caja en varias monedas, en un solo pedido (issue #376, bloque 3)

Rama: `fix/financiero-movimientos-en-varias-monedas-en-lote` (central) + una rama del desktop.

## El problema

En la caja mayor, los dialogos «Ingreso / Egreso / Ajuste» y «Transferencia» dejan cargar Gs, Rs y Ds a la
vez. El central solo sabe registrar de a una moneda (`saveMovimientoCajaVirtual`,
`realizarTransferenciaCajaVirtual`), asi que el desktop manda **un pedido por moneda**:

- si la segunda moneda se rechaza (saldo insuficiente, sin permiso) la primera **ya entro**: la operacion queda
  a medias;
- si una respuesta se pierde no se sabe cual entro, y reintentar duplica.

El desktop ya lo mitiga (#390): manda en serie, corta en la primera que falla, avisa que quedo a medias y
cierra el dialogo. Mitiga, no resuelve: la operacion sigue pudiendo quedar por la mitad.

Mirando el codigo aparecieron dos cosas mas en el mismo camino:

1. `saveMovimientoCajaVirtual` y `realizarTransferenciaCajaVirtual` toman **el usuario del pedido**
   (`usuarioId` lo manda el cliente), no el de la sesion: el movimiento queda a nombre de quien diga el
   cliente.
2. `saveMovimientoCajaVirtual` acepta cualquier tipo de movimiento (tambien `TRANSFERENCIA_*` y
   `PAGO_PROVEEDOR`) y cualquier `referenciaId`; ni ella ni `transferir` validan que el monto sea positivo.

## Alcance

Central: dos mutations nuevas y un servicio que las ejecuta en una transaccion. Desktop: los dos dialogos
pasan a mandar un solo pedido.

Fuera:
- Las dos mutations de una moneda **quedan como estan**: las usan los desktops sin actualizar y el ajuste por
  conteo (una sola moneda; su validacion de saldo esperado es el bloque 4 de la issue). Sus dos defectos de
  arriba no se tocan aca: quedan anotados.
- El limite de caja chica (hoy un aviso del desktop): bloque 4.

## Diseno

### Dos mutations de lote

```graphql
input MontoCajaVirtualInput {
    monedaId: ID!
    cantidad: Float!
}

extend type Mutation {
    registrarMovimientosCajaVirtual(cajaVirtualId: ID!, tipoMovimiento: CajaVirtualTipoMovimiento!,
        montos: [MontoCajaVirtualInput!]!, descripcion: String, claveIdempotencia: String): Boolean!
    realizarTransferenciasCajaVirtual(origenId: ID!, destinoId: ID!,
        montos: [MontoCajaVirtualInput!]!, descripcion: String, claveIdempotencia: String): Boolean!
}
```

- **Todo o nada:** una transaccion por pedido (`@Transactional` en el servicio nuevo). Si una moneda se rechaza
  no queda ninguna. Una excepcion de `registrar` no se atrapa para seguir: marca la transaccion para rollback.
- `seg.requireGestionar()` en los dos resolvers, como las mutations actuales. El resolver no carga entidades:
  pasa ids, y todo se resuelve dentro de la transaccion.
- **Usuario de la sesion** (`seg.currentUsuario()`), no del pedido; sin usuario se rechaza.
- `registrarMovimientosCajaVirtual` acepta solo `INGRESO`, `EGRESO` y `AJUSTE` (lo que ofrece el dialogo). Las
  transferencias van por la otra mutation, que deja las patas vinculadas.
- **Validaciones del pedido, todas antes de registrar nada:** entre 1 y 10 montos; monedas sin repetir y
  **existentes** (una moneda que no existe se rechaza: hoy `resolverMoneda(null)` la convertiria en guaranies);
  monto finito, de a lo sumo 4 decimales y dentro del rango de `numeric(18,4)`; mayor que cero en ingreso,
  egreso y transferencia; distinto de cero en ajuste (lleva signo). Se comparan como `BigDecimal` (`signum`),
  no como `Double`.
- Cada movimiento pasa por `TesoreriaService.registrar` / `transferir`: mismos permisos por caja, mismo control
  de descubierto, mismo vinculo entre patas. No se duplica nada de eso.
- Devuelven `Boolean`: el dialogo no usa los movimientos, relee la caja.
- La clase del input va en `graphql/financiero/input/MontoCajaVirtualInput.java`.

### Orden de locks

El orden dominante del modulo es **caja ascendente**; la moneda no tiene orden en ningun flujo. Registrar de a
una moneda, como venia el borrador de este plan, abria dos cruces nuevos:

- una transferencia en lote A→B en Gs y Rs tomaria (A,Gs), (B,Gs), (A,Rs), (B,Rs); un pago mixto con la caja A
  en Rs y la B en Gs toma (A,Rs), (B,Gs): se traban;
- cada `registrar` en Gs / Rs / Ds actualiza ademas la fila `caja_virtual` (el shim), y esa escritura sale a la
  base antes de pedir el saldo de la moneda siguiente. El lote tendria la fila de la caja mientras espera el
  saldo en Rs, y un egreso suelto en Rs tiene ese saldo y espera la fila de la caja: se traban.

Por eso el servicio **toma primero todos los saldos del lote**, ordenados por (caja, moneda): `ensureRow` y
`lockByCajaVirtualIdAndMonedaId` de cada par. Recien despues llama a `registrar` / `transferir`, que vuelven a
pedir locks que la transaccion ya tiene. Con eso el orden es el del modulo, y la fila de la caja se escribe
siempre despues de tener todos los saldos, igual que en los flujos de una moneda. Va despues de la clave de
idempotencia y de las validaciones.

Lo que no cambia y ya existe: `CajaVirtual` no tiene `@DynamicUpdate`, asi que dos movimientos simultaneos de
monedas distintas sobre la misma caja pueden pisarse las columnas `saldo_gs/rs/ds` del shim (se corrige sola en
el siguiente movimiento de esa moneda; `caja_virtual_saldo` es la que manda). No se toca aca.

### Idempotencia

`claveIdempotencia` opcional, con el `IdempotenciaService` que ya usan `pagarSolicitudesMixto` y
`emitirCheque` (ARQUITECTURA §7.1): el pedido repetido con la misma clave no registra nada y devuelve `true`.

- La clave es **lo primero** que toma la transaccion, antes de cualquier lock de saldo.
- Huella: operacion, caja (o cajas), tipo, montos ordenados por moneda y descripcion. La misma clave con otro
  contenido se rechaza.
- Como resultado se guarda el id del primer movimiento creado (en una transferencia, la pata de salida de la
  primera moneda). Para eso `TesoreriaService.transferir` pasa a tener una variante que devuelve la pata de
  salida; la que devuelve `Boolean` queda para sus llamadores actuales.
- El repetido comprueba ese movimiento: si ya no esta activo se rechaza con «ya se registro y despues fue
  anulado», la regla de §7.1 para pagos y cheques. **Solo mira el primero:** los movimientos de un lote no
  comparten ninguna columna, y agruparlos pediria una migracion. Queda anotado.
- El repetido no vuelve a exigir el permiso sobre la caja (igual que pagos y cheques): la clave ya esta atada
  al usuario que hizo el original.
- Sin clave no hay idempotencia (pedido armado a mano); el desktop nuevo siempre la manda.

Con el lote atomico el resultado tiene tres formas, no nueve: entro todo, no entro nada (rechazo), o no se
sabe (sin respuesta). Para la tercera hace falta la clave: sin ella, reintentar duplicaria el lote entero.

### Desktop (PR propio, despues de desplegar el central)

- `add-movimiento-caja-virtual-dialog` y `transferencia-caja-virtual-dialog` mandan un solo pedido con todos
  los montos y una `claveIdempotencia` nueva por intento del usuario (`claveIdempotencia.ts`, la que ya usan
  pagos y cheques).
- **Rechazo:** no entro nada; el dialogo queda abierto para corregir (el motivo ya lo muestra `onSaveCustom`).
- **Sin respuesta:** el dialogo pasa a «No se pudo confirmar» con **Reintentar** (mismo pedido, misma clave: si
  ya habia entrado no se repite) y **Cerrar** (cierra avisando a quien lo abrio, que relee la caja). Mientras
  tanto quedan bloqueados los montos, la descripcion y la caja destino: cambiarlos con la misma clave seria
  otro pedido. Mismo patron que `emitir-cheque-dialog`.
- Un error de schema (central sin las mutations) es un **rechazo**, no un «sin respuesta»: reintentar no
  serviria.
- Se conservan el signo del ajuste de egreso (hoy lo niega el dialogo) y el aviso de limite de caja chica,
  que corre antes de enviar y no se repite al reintentar.
- `enviar-en-serie.ts` queda sin usos y se borra.

Con un central anterior el desktop nuevo falla la validacion del schema (mutation inexistente): el central va
primero, y el desktop no se promueve a beta / stable hasta que farmacia y bodega lo tengan.

## Tabla de datos nuevos

| Dato | Donde | Nota |
|---|---|---|
| input `MontoCajaVirtualInput` | schema | moneda + monto |
| mutation `registrarMovimientosCajaVirtual` | central | nueva |
| mutation `realizarTransferenciasCajaVirtual` | central | nueva |
| filas en `financiero.operacion_idempotente` | central | tabla existente (V237.1), dos operaciones nuevas |

Sin columnas, sin migracion, sin cambios de replicacion.

## Fases

Central (un PR):

1. **Servicio + resolvers + schema.** Tests unitarios:
   - tres monedas → tres movimientos, por moneda ascendente, con el usuario de la sesion;
   - la segunda moneda se rechaza → la excepcion sale y no se traga (la atomicidad la prueba el IT);
   - tipo no permitido, lista vacia, mas de 10, moneda repetida o inexistente, monto cero / negativo / no
     finito → rechazo antes de registrar nada;
   - ajuste negativo pasa con su signo;
   - transferencias: una por moneda, origen = destino se rechaza;
   - con clave: el repetido no registra; misma clave con otro contenido → rechazo; la clave se toma antes que
     cualquier registro.
2. **Tests de integracion** (`-Dit.financiero=true`): lote de dos monedas donde la segunda no tiene saldo → los
   saldos de las dos quedan intactos y no hay movimientos; el mismo lote con la misma clave dos veces → un solo
   juego de movimientos; repetido despues de anular el primero → rechazo; dos lotes cruzados A→B y B→A a la
   vez, y un lote Gs + Rs contra egresos sueltos en Rs sobre la misma caja → terminan todos, sin deadlock.

Desktop (otro PR):

3. Servicio, queries y los dos dialogos; borrar `enviar-en-serie.ts`. `verificar:imports`, build de produccion
   y prueba en Chrome (incluida la respuesta perdida, parchando `XMLHttpRequest`).

Cada test de bug se corre con el fix neutralizado para ver que falla.

## Prueba de runtime (central local, perfil `dev`)

- Ingreso en tres monedas: tres movimientos, un pedido.
- Egreso en dos monedas con saldo solo en la primera: rechazo, ningun movimiento, saldos intactos.
- Transferencia en dos monedas: cuatro patas vinculadas de a pares; con saldo solo en una: no se transfiere
  nada.
- El mismo pedido dos veces con la misma clave: no se repite. Misma clave, otro monto: rechazo.
- Usuario sin permiso de escritura en la caja destino: rechazo, nada registrado.

## Despliegue y rollback

Sin migracion. Requiere reinicio del central. Rollback del JAR: inocuo para los datos; el desktop nuevo deja
de poder registrar movimientos y transferencias contra esa instancia (mutation inexistente).

## Decisiones tomadas (Franco, 2026-10-09: las cinco, como se recomiendan)

1. **Idempotencia en este mismo cambio.** Recomendacion: si. Sin la clave, el lote atomico deja el caso «sin
   respuesta» peor que hoy (reintentar duplica todas las monedas). El costo es el estado «sin confirmar» en
   los dos dialogos.
2. **Usuario de la sesion** en las mutations nuevas, ignorando el del pedido. Recomendacion: si.
3. **Solo ingreso, egreso y ajuste** en `registrarMovimientosCajaVirtual`. Recomendacion: si.
4. **Las mutations de una moneda no se tocan** (ni se marcan obsoletas): las necesitan los desktops sin
   actualizar. Sus defectos (usuario del pedido, cualquier tipo, monto sin validar) quedan para otro cambio.
   Recomendacion: dejarlas.
5. **Anular sigue siendo por moneda.** El lote entra todo junto, pero cada moneda queda como un movimiento (o
   un par de patas) independiente y se anula por separado, como hoy. Agruparlos para anular «el lote» pediria
   una columna nueva en `movimiento_caja_virtual`. Recomendacion: dejarlo asi.

## Queda sin verificar

- Cuantas veces quedo una operacion a medias en produccion: no hay forma directa de saberlo (los movimientos de
  un mismo dialogo no comparten nada mas que caja, usuario, descripcion y segundos de diferencia).
- `saveMovimientoCajaVirtual` sigue aceptando el usuario del pedido, cualquier tipo y montos negativos.
- El repetido solo comprueba el primer movimiento del lote: si se anulo otro y no ese, responde que esta
  registrado.
- `operacion_idempotente` no tiene purga. El dia que la tenga no puede borrar claves mas nuevas que un dialogo
  que siga abierto.
- El shim `saldo_gs/rs/ds` puede quedar desfasado entre movimientos simultaneos de monedas distintas (ya pasa).
- `registrar` no mira si la caja esta activa.

## Auditoria del plan (paso 5, 2026-10-09)

| Hallazgo | Que se hizo |
|---|---|
| Orden (moneda, caja) se cruza con el pago mixto, que va por caja | saldos tomados antes, por (caja, moneda) |
| El shim se escribe entre los locks de dos monedas: deadlock con un pedido suelto | idem; test de integracion |
| `transferir` devuelve `Boolean`: no hay id que guardar para la clave | variante que devuelve la pata de salida |
| El repetido sobre un lote anulado responderia «registrado» | rechazo si el primero esta inactivo; limite anotado |
| Moneda inexistente cae a guaranies; NaN, rango y decimales | validaciones en `BigDecimal`, moneda obligatoria |
| `requireGestionar`, usuario nulo, resolver sin cargar entidades | diseno |
| Descripcion y destino bloqueados al reintentar; error de schema no es «sin respuesta» | desktop |
| Anular por moneda contrasta con registrar todo junto | decision 5 |
