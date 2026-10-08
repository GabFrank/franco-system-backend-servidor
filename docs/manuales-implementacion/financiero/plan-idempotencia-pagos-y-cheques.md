# Plan: idempotencia de `pagarSolicitudesMixto` y `emitirCheque` (issue #376, punto 1)

Rama: `fix/financiero-idempotencia-pagos-y-cheques` (desde `origin/develop` c0fdaf0a).
Este archivo es registro de trabajo: se borra en el PR final (ciclo, paso 11).

## Alcance

Solo el punto 1 del «orden sugerido» de la issue #376. Quedan para ciclos propios, un PR cada uno:
guard + lock en `revertir` y en las dos anulaciones (2), `resolverRetiroCaso` atomico (3) y el resto (4).

Dentro del punto 1 entran las dos mutations que mueven plata y emiten documentos:

| Mutation | Servicio | Que duplica hoy un pedido repetido |
|---|---|---|
| `pagarSolicitudesMixto` | `PagoProveedorService.pagarLoteMixto` → `procesarEvento` | en un pago **parcial**: otro `Pago`, otro movimiento de caja/banco y cheques nuevos con el numero siguiente. El pago total ya se rechaza (`CONCLUIDO`) |
| `emitirCheque` | `ChequeGestionService.emitir` | otro cheque: al dia debita el banco otra vez, diferido reserva otra vez |

Fuera: `pagarValesMixto` y `pagarRrhhMixto` (pago parcial prohibido, la repeticion ya se rechaza),
`pagarSolicitud` y `pagarSolicitudesLoteCajaMayor` (sin llamadores en desktop ni PWA).

## Diseno

Clave de idempotencia opcional, generada por el cliente por cada intento del usuario y reenviada en el
reintento. El central guarda la clave **en la misma transaccion** que el pago o el cheque.

### Tabla nueva `financiero.operacion_idempotente` (solo central, no se publica)

| Columna | Tipo | Uso |
|---|---|---|
| `clave` | `varchar(64)` PK | la que manda el cliente (UUID) |
| `operacion` | `varchar(40)` not null | `PAGAR_SOLICITUDES_MIXTO` / `EMITIR_CHEQUE` |
| `usuario_id` | `bigint` | quien la uso |
| `huella` | `varchar(64)` not null | SHA-256 del pedido canonico |
| `resultado_id` | `bigint` | id del `Pago` / `Cheque` creado |
| `creado_en` | `timestamp` not null default `now()` | |

Migracion `V237.1__financiero_operacion_idempotente.sql`: `CREATE TABLE IF NOT EXISTS` con PK nombrada
(`pk_operacion_idempotente`) y un indice sobre `creado_en` para una purga futura. Aditiva. Sin espejo en
filial (tabla nueva, no entra en ninguna publicacion). La migracion **no** inserta en
`configuraciones.replication_table`, que es lo unico que mira `syncPublicationsWithReplicationTable`
para publicar. Control de solo lectura antes de desplegar a cada instancia:
`SELECT pubname, puballtables FROM pg_publication;` (ninguna debe ser `FOR ALL TABLES`).

### `IdempotenciaService` (nuevo, `service/financiero`)

Un solo punto de entrada, para que reclamar y registrar no puedan separarse (auditoria B, R3):

`<T> T ejecutar(clave, operacion, huella, usuario, Supplier<T> accion, Function<T, Long> idDe, Function<Long, T> cargar)`
— `@Transactional(propagation = MANDATORY)`.

1. Clave nula o vacia → `accion.get()` y nada mas: comportamiento actual (cliente viejo).
2. Clave con largo > 64 → rechazo.
3. `INSERT ... ON CONFLICT (clave) DO NOTHING`.
   - Inserto 1 fila → `accion.get()`, y `UPDATE ... SET resultado_id` verificando que toco **1** fila.
   - Inserto 0 → la clave ya existe y esta commiteada. Se lee la fila:
     - `operacion`, `usuario_id` o `huella` distintos → `GraphQLException` («La clave de idempotencia ya
       se uso para otro pedido»);
     - `resultado_id` nulo → `GraphQLException` (estado inconsistente; nunca se devuelve vacio — R2);
     - si no → `cargar.apply(resultado_id)`.

Los dos nativos van por `EntityManager.createNativeQuery`, que flushea la sesion antes de ejecutar y **no
limpia el contexto**: nada de `clearAutomatically`, que desprenderia las solicitudes y la chequera ya
lockeadas con cambios sin flushear (R1). El servicio asume READ COMMITTED (el default de la aplicacion):
no llamarlo desde una transaccion `SERIALIZABLE` / `REPEATABLE READ`, donde el `DO NOTHING` contra una
fila no visible falla con 40001 en vez de devolver 0. Queda en el javadoc.

Por que alcanza: dos pedidos simultaneos con la misma clave — el segundo `INSERT` espera en el indice
unico a que el primero termine. Si el primero commitea, el segundo inserta 0 y devuelve lo ya creado; si
el primero hace rollback (rechazo de negocio), su fila desaparece y el segundo ejecuta normalmente. La
clave es el primer lock que toman los dos, antes de solicitudes, cajas, cuentas y chequera: no agrega
orden de lock nuevo entre flujos distintos.

### Cambios en los servicios

- `PagoProveedorService.pagarLoteMixto(pagos, usuario, claveIdempotencia)`: `idempotencia.ejecutar(...)`
  con `accion` = `exigirSinObligacionesRrhh` + `procesarEvento`. Se conserva la firma de dos argumentos
  (la usan los tests y delega con clave nula).
- `ChequeGestionService.emitir(cheque, usuario, claveIdempotencia)`: idem, devolviendo el `Cheque`. La
  firma de dos argumentos queda para `PagoProveedorService.emitirCheque` (cheques de un pago: los cubre
  la clave del pago).
- **Repeticion de algo que despues se anulo** (R4): si el `Pago` esta `CANCELADO` o el `Cheque`
  `ANULADO`, la repeticion **se rechaza** con «El pago #N de este pedido ya se registro y despues fue
  anulado» (idem cheque). No se devuelve como exito: el dialogo del desktop muestra «Pago registrado
  correctamente» ante cualquier respuesta no nula, sin mirar `estado`. Tampoco se vuelve a ejecutar.
- Huella (R6): solo lo que define el pedido, en orden fijo, con una funcion unica sobre el input ya
  mapeado; `null` y `false` valen lo mismo; montos como `stripTrailingZeros().toPlainString()`.
  Pago: por solicitud, `solicitudId` + cada linea (fuente, caja, cuenta, moneda, monto, cotizacion,
  montoSolicitud, descuento, aumento, chequeRef, chequera, diferido, `fechaPago` **solo el dia**,
  beneficiario, nominal). **Sin `fechaEmision`**: el desktop la arma con `new Date()` al confirmar
  (`pagar-compras-dialog.component.ts:808`) y un reintento rearmado cambiaria la huella.
  Cheque: chequera, total, diferido, moneda, cuenta, `fechaPago` (dia), concepto.

### GraphQL

Argumento opcional `claveIdempotencia: String` al final de las dos mutations
(`pago-proveedor.graphqls`, `cheque-pos.graphqls`) y en los resolvers (`PagoProveedorGraphQL`,
`ChequePosGraphQL`). Los controles de rol no cambian (`requirePagarCpp` / `requireGestionar`) y corren
antes de reclamar la clave. Sin enums nuevos.

## Tabla de datos nuevos

| Dato | Quien lo escribe | Quien lo lee |
|---|---|---|
| arg `claveIdempotencia` | **desktop**: `pagar-compras-dialog` (modo compras/gastos) y `emitir-cheque-dialog` — PR propio, fase 4 | `PagoProveedorGraphQL`, `ChequePosGraphQL` |
| `operacion_idempotente.{clave, operacion, usuario_id, huella, resultado_id}` | `IdempotenciaService.ejecutar` (`INSERT` + `UPDATE`) | `IdempotenciaService.ejecutar` (rama «ya registrado») |
| `operacion_idempotente.creado_en` + su indice | `DEFAULT now()` | **nadie todavia**: queda para la purga por antiguedad, que no entra en este PR. Excepcion consciente a la regla de las dos puntas; sirve tambien para diagnosticar a mano |

El escritor del argumento es el desktop: sin la fase 4 el central queda con el mecanismo y nadie que lo
use. Por eso el trabajo no se da por terminado con el PR del central solo.

## Fases

Central (un PR):

1. **Migracion + `IdempotenciaService` + `HuellaPedido`.** Sin entidad ni repositorio: las tres
   sentencias son nativas por `EntityManager` y una entidad no agregaba nada (cambio respecto del plan
   aprobado, que las nombraba).
   Tests unitarios de la huella: dos pedidos armados por rutas distintas (nulos vs `false`, distinta
   `fechaEmision`, distinta hora en `fechaPago`) dan la misma; otro monto da otra.
   `IdempotenciaIT` (Postgres real, `@EnabledIfSystemProperty(it.financiero)` como `FinancieroFixesIT`;
   no corre en CI): clave nueva ejecuta y registra; repetida devuelve lo cargado sin ejecutar; otra
   huella / operacion / usuario → rechazo; `resultado_id` nulo → rechazo; accion que falla → la clave no
   queda; **dos hilos con la misma clave → la accion corre una sola vez**. Commitea de verdad (no puede
   ser `@Transactional` con rollback), asi que borra sus propias claves al terminar. Es la unica prueba
   automatica del bloqueo en el indice unico (R7).
2. **Pago mixto**: servicio + resolver + `.graphqls`.
   Tests en `PagoProveedorServiceTest`: con clave ya usada devuelve el `Pago` existente y **no** llama a
   `tesoreriaService.registrar`, `bancoLedgerService.registrar` ni `chequeGestionService.emitir`; con
   clave nueva procesa y registra el resultado; sin clave se comporta como hoy. Verificar que el primero
   falla con el fix neutralizado. `PagoProveedorService` y `ChequeGestionService` usan
   `@AllArgsConstructor` y los tests los arman con `new`: sumar el mock de `IdempotenciaService` en
   `PagoProveedorServiceTest`, `ChequeGestionServiceTest` y cualquier otro test que los construya.
3. **Emision de cheque**: servicio + resolver + `.graphqls`.
   Tests en `ChequeGestionServiceTest`: mismos tres casos; con clave usada no avanza el correlativo ni
   toca el banco.

Desktop (otro PR, en `frc-sistemas-integrados-angular`, despues de que el central este desplegado en el canal):

4. **Enviar la clave y reintentar con la misma.** `crypto.randomUUID()` al confirmar un armado nuevo; en
   «sin respuesta» el dialogo conserva **el payload congelado junto con su clave** (no lo rearma) y
   ofrece reintentar ese mismo pedido, en
   vez de soltarlo y obligar a rearmarlo (que hoy genera un pedido nuevo). Solo en modo compras/gastos
   y en `emitir-cheque-dialog`. El detalle de UI va en el plan del desktop, con su propio ciclo.

## Prueba de runtime (central local, perfil `dev`, replicacion apagada)

- Pago parcial de una solicitud, dos veces con la misma clave: un solo `Pago`, un solo movimiento,
  `monto_pagado` sube una vez. Sin clave (control): dos pagos.
- Lo mismo con una linea de cheque: un solo cheque, `siguiente_numero` avanza una vez.
- `emitirCheque` al dia y diferido, dos veces con la misma clave: un cheque, un debito / una reserva.
- Cuatro pedidos **simultaneos** con la misma clave: un resultado, los cuatro devuelven el mismo id, sin
  deadlock en el log.
- Misma clave con otro monto: rechazo, nada registrado.
- Pago con clave, anularlo con `anularPagoCpp`, repetir el pedido con la misma clave: rechazo «ya se
  registro y despues fue anulado», sin movimientos nuevos.
- Pedido rechazado por negocio (excede saldo) y reintento corregido con la misma clave y otro monto:
  **anotar que comportamiento queda** (la fila del intento rechazado hizo rollback, asi que deberia
  pasar).
- Dry-run de `V237.1` contra la copia local de `bodega` (paso 10).

### Resultado (2026-10-08, central local `:8081`, perfil `dev`, base `bodega` local, usuario de prueba)

| # | Caso | Resultado |
|---|---|---|
| 1 | Control **sin clave**: parcial de 1.000 dos veces | dos pagos (#17 y #18), caja -2.000, solicitud en 2.000 — el bug, y lo que sigue viendo un cliente viejo |
| 2 | Parcial de 1.000 dos veces **con la misma clave** | el mismo pago (#19) las dos veces, caja -1.000, un movimiento |
| 3 | Cuatro pedidos **simultaneos** con la misma clave (1.500) | los cuatro devuelven el pago #20; caja -1.500, un movimiento |
| 4 | Misma clave con otro monto | rechazo «La clave de idempotencia ya se uso para otro pedido»; nada cambia |
| 5 | Rechazo de negocio (excede saldo) y reintento corregido con la misma clave | el rechazo no deja la clave; el reintento paga (#21) |
| 6 | Pago con linea de cheque diferido dos veces, misma clave y **distinta `fechaEmision`** | un pago (#22), un cheque, una reserva de 2.000, el correlativo avanza una vez |
| 7 | Anular el pago de 2 y repetir su pedido | rechazo «El pago #19 de este pedido ya se registro y despues fue anulado»; sin movimientos nuevos |
| 8 | `emitirCheque` al dia de 3.000 dos veces, misma clave | el mismo cheque (Nº 700002), un debito, un movimiento bancario |
| 9 | `emitirCheque` diferido de 4.000, cuatro simultaneos, misma clave | los cuatro devuelven el cheque #5; una reserva |
| 10 | Control `emitirCheque` **sin clave** dos veces | dos cheques (700004 y 700005) |
| 11 | `emitirCheque` con la clave de 8 y otro monto | rechazo |

Sin `deadlock` ni `ERROR` en el log del central (salvo Firebase, preexistente). `IdempotenciaIT` (7 tests,
incluido el de dos hilos) en verde contra la misma base.

`V237.1` aplico limpia sobre la copia local de `bodega` al levantar el contexto del IT (junto con
`V236.1` y `V236.3`, que esa base no tenia): es el dry-run del paso 10 para bodega. **No se probo contra
una copia de farmacia.**

## Auditoria del diff (paso 8, 2026-10-08)

Tres auditores: Fijo 1 (autorizacion), Fijo 2 + Condicional B (esquema y replicacion), Fijo 3 (contrato)
mas correccion del comportamiento. Ningun hallazgo alto.

| Hallazgo | Severidad | Que se hizo |
|---|---|---|
| `chequeRef` sale de un contador del desktop que no se reinicia: un pago rearmado igual daba otra huella y un falso «clave usada para otro pedido» | media | la huella cuenta el **agrupamiento** de las lineas en cheques (orden de aparicion), no el numero. Tests nuevos |
| `IdempotenciaIT` no corre en CI | media | corrido a mano contra la base local: 8 en verde. Va como evidencia en el PR |
| Clave con espacios: `" abc"` y `"abc"` eran claves distintas | baja | se recorta antes de guardar. Test en el IT |
| La tabla de datos nuevos nombraba `reclamar` / `registrarResultado` | baja | corregida; `creado_en` anotado como excepcion sin lector |
| La repeticion no revalida el ACL de caja del momento | baja | no aplicado: devuelve solo lo que ese usuario creo, y el rol si se revalida en el resolver |
| La clave es global: el rechazo deja saber que una clave existe | baja | no aplicado: no revela de quien ni que; el cliente usa UUID |
| Aceptar solo `CONCLUIDO` en la repeticion del pago | baja | no aplicado: el motor siempre deja `CONCLUIDO`, y el mensaje de «anulado» seria falso para otro estado |
| Los tests con el mock de idempotencia prometen «sin mover plata» y prueban el cableado | baja | no renombrados: lo real lo prueban el IT y la prueba de runtime de arriba |
| No hay mecanismo que obligue a clasificar una tabla nueva como replicada o central-only | baja, preexistente | fuera de este PR; la defensa es el control de `pg_publication` antes de desplegar |

Bateria tras los ajustes: `./mvnw clean verify -B -DskipFlyway=true` → 1396 tests, 0 fallas, 1 salteado;
`target/frc-central-server.jar` construido.

## Despliegue y rollback

- Orden: central primero; el desktop solo despues de que el central del canal tenga el argumento. Un
  desktop que declare `$claveIdempotencia` contra un central sin el argumento falla por validacion de
  schema: **se cae el pago mixto entero y el cheque manual entero**, no solo el reintento.
- Son tres centrales con deploy manual e independiente, cada uno con su base: alpha (mauro `:8083`),
  farmacia / beta (`:8082`) y bodega / stable (`:8081`). `V237.1` corre en cada base recien cuando
  arranca esa instancia. El desktop de cada canal espera a **su** central.
- **Criterio de entrada a la fase 4** (por canal, antes de mergear el desktop a la rama de ese canal):
  un POST de prueba a `/graphql` del central del canal con `claveIdempotencia` que no falle por schema,
  y `financiero.operacion_idempotente` presente en esa base. No alcanza con leer la version del release:
  mergear a `develop` no despliega (`deploy-auto.yml` nunca corre).
- Se eligio argumento GraphQL y no header HTTP: tiene precedente (`montoPagadoEsperado`, #301) y ningun
  resolver lee headers. El costo es el orden de despliegue de arriba.
- El central nuevo acepta al desktop viejo (argumento opcional).
- Rollback del central con el desktop nuevo ya instalado: **no** — se corrige hacia adelante.
- Rollback del JAR con la tabla creada: inocuo, la version anterior no la conoce.
- Requiere reinicio del central (lo hace el workflow Deploy). Sin variables de entorno ni carpetas nuevas.

## Queda sin verificar / decisiones abiertas

- Que `operaciones.pago` y `financiero.cheque` no esten publicadas se vio en la copia **local** de
  bodega; no cambia el plan (no se tocan), pero no se miro produccion.
- La tabla crece una fila por pago / cheque con clave. Sin purga en este PR (volumen bajo: 14 pagos y 2
  cheques en la copia local).
- El bloqueo del segundo `INSERT` sobre el indice unico solo se comprueba en runtime, no en los tests
  unitarios (el CI no levanta contexto).
- Los tests unitarios de los dos servicios mockean `IdempotenciaService`: prueban el cableado, no la
  semantica de PostgreSQL. De ellos solo «clave ya usada no mueve plata» falla con el fix neutralizado;
  «sin clave se comporta como hoy» es caracterizacion.
- **No aplicado (auditoria B, R8):** `lock_timeout` para el segundo pedido que espera al primero.
  `SET LOCAL` dura toda la transaccion y cambiaria tambien la espera de los `lockById` del motor. Hoy un
  reintento espera lo que tarde el original, que es lo buscado; si la fase 4 reintentara sola y en
  bucle habria que revisarlo.
- En la respuesta repetida `Pago.solicitudesPago` puede diferir de la original (`sp.setPago` se pisa en
  cada pago). El desktop solo pide `id` y `estado` en esta mutation.
- No cubre el caso en que el usuario cierra el dialogo y **rearma** el mismo pago a mano: es un pedido
  nuevo con clave nueva. Lo baja la fase 4 (reintentar en vez de rearmar), no lo elimina.
