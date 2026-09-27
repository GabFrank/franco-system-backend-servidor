# Plan — cargar el conteo de una caja desde el admin del desktop

Rama: `fix/financiero-conteo-caja-desde-admin` en **central**, **filial** y **desktop** (sale de
`origin/develop`). Mobile y mobile-pwa: sin cambios (ver §6).

El fix en sí (fases 1 y 2) no necesita desktop nuevo: arregla también las máquinas sin actualizar.
La fase 3 del desktop cierra un camino que la auditoría encontró y que mandaría el conteo a la
sucursal equivocada.

## 1. El bug

Una sucursal reportó que al cargar el conteo de apertura de una caja abierta el desktop mostró:

> The field at path /data was declared as a non null type, but the code involved in retrieving
> data has wrongly returned a null value. [...] The non-nullable type is 'Conteo' within parent
> type 'Mutation'

Causa, verificada en `origin/master` y `origin/develop` de los tres repos:

1. `adicionar-conteo-dialog.component.ts:405` (desktop) llama
   `conteoService.onSave(conteo, cajaId, apertura, !this.isVentaTouch)`.
   - Desde el **PDV** (Venta Touch → seleccionar caja / utilitarios), `isVentaTouch = true` y la
     mutación va a la **filial**, que la guarda.
   - Desde el **admin** (Financiero → Cajas, o Análisis de diferencia, que abren
     `AdicionarCajaDialogComponent` sin `isVentaTouch`), va al **central**
     (`clientName: "servidor"`).
2. En el central, `ConteoGraphQL.saveConteo` tiene **todo el cuerpo comentado y devuelve `null`**.
   El schema lo declara `Conteo!` y de ahí el error.

Alcance real: desde el admin **no se puede cargar ni la apertura ni el cierre** de ninguna caja.
El central no guarda nada, así que no quedan datos a medias.

Segundo defecto, en la filial: `saveConteo` también devuelve `null` en silencio si no encuentra la
caja (`ConteoGraphQL.java`, «No se encontro la PdvCaja»), y el usuario ve el mismo mensaje
críptico. Además, una **apertura repetida** sobre una caja que ya tiene `conteoApertura` persiste un
conteo nuevo que no queda enlazado a nada (con sus `conteo_moneda`), en vez de ser idempotente
como ya lo es el cierre.

## 2. Decisión: arreglar `saveConteo` del central, sin mutación nueva

El `ConteoInput` que manda el desktop ya trae `sucursalId`
(`conteo.sucursalId = this.sucursalId` ← `selectedCaja.sucursalId`). Con eso el central puede
reenviar la operación a la filial dueña de la caja con `FilialCajaProxyService`, el mismo patrón que
ya usan `pdvCaja`, `imprimirBalance` y `editarConteoCajaDesdeServidor`.

Por qué así y no con una mutación nueva más un cambio en el desktop:

- **Arregla los desktops ya instalados** sin esperar a que actualicen (el desktop puede quedar
  indefinidamente sin actualizar). Una mutación nueva exigiría release del desktop y seguiría
  rota en todas las máquinas viejas.
- **Contrato GraphQL intacto**: misma firma y mismo tipo de retorno (`Conteo!`). Nada que grepear
  en los clientes más allá de confirmar quién la usa (§6).

La escritura sigue ocurriendo **siempre en la filial**, que es la dueña del dato. El central la
recibe por replicación, como hoy.

## 3. Fases

### Fase 1 — central: `saveConteo` reenvía a la filial

Archivos:

- `service/financiero/FilialCajaProxyService.java` — método nuevo `guardarConteoEnFilial(...)`:
  arma la mutación `saveConteo(conteo, conteoMonedaInputList, cajaId, apertura, imprimirBalance:false)`
  contra `buildFilialUrl(sucursal)`, reenvía el `Authorization` y usa `executeGraphQLOrThrow` para
  que el motivo del rechazo de la filial llegue al usuario. Pide
  `id sucursalId observacion creadoEn` y devuelve un `Conteo` con esos campos.
- `graphql/financiero/ConteoGraphQL.java` — `saveConteo` deja de devolver `null`:
  1. **Autorización, primera línea**: `tesoreriaSecurityService.requireAnyRole("ANALISIS DE CAJA")`
     (bypass ADMIN incluido). Es el rol que gatea la pantalla de Cajas en el desktop
     (`side-mini-variant.component.ts:1023`, `ROLES.ANALISIS_DE_CAJA`). Se lee del token, no del
     input (issue #177).
  2. Valida `cajaId` y `input.sucursalId`; si falta la sucursal, `GraphQLException` con mensaje.
  3. Si `input.usuarioId` viene vacío (el diálogo de alta no lo manda), lo completa con
     `tesoreriaSecurityService.currentUsuario()`, para que el conteo quede con quién lo cargó.
  4. Llama a `guardarConteoEnFilial`. Errores:
     - `ResourceAccessException` (timeout / sin red): «No se pudo confirmar el conteo con la
       sucursal. Vuelva a abrir la caja para ver si quedó guardado.» — un timeout de lectura puede
       llegar con el conteo ya confirmado en la filial.
     - `RestClientException`: «Error de comunicación con la sucursal…».
     - Cualquier otra: el mensaje de la filial tal cual.
  5. Se borra el bloque comentado muerto del cuerpo actual.
- `conteo.graphqls`: **sin cambios**.

`conteoMonedaList` del `Conteo` devuelto lo resuelve `ConteoResolver` contra la base del central:
sale vacío hasta que replica. El desktop solo mira que la respuesta no sea `null`
(`adicionar-conteo-dialog.component.ts:407`), así que no afecta.

Tests (`src/test/.../graphql/financiero/ConteoGraphQLSaveConteoTest.java`, Mockito, mismo estilo que
`PdvCajaGraphQLEditarConteoTest`):

- **devuelve el conteo que crea la filial** (no `null`) — **falla con el código viejo**;
- sin rol → excepción y **no** sale a la red (`verify(proxy, never())`);
- sin `sucursalId` → excepción con mensaje;
- `usuarioId` vacío → se completa con el usuario autenticado antes de reenviar;
- la filial rechaza → el mensaje llega tal cual;
- `ResourceAccessException` → el mensaje de «no se pudo confirmar».

Gate: `./mvnw clean verify -B -DskipFlyway=true` leído del log.

### Fase 2 — filial: lock sobre la caja, errores con mensaje y apertura idempotente

Archivos: `graphql/financiero/ConteoGraphQL.java` (`saveConteo`),
`repository/financiero/PdvCajaRepository.java`, `service/financiero/PdvCajaService.java`.

- **Lock pesimista sobre la caja** (hallazgo alto del eje B). Hoy `saveConteo` lee la caja con
  `findById` sin lock, y el `SERIALIZABLE` de `ConteoService.saveAndSend` /
  `PdvCajaService.saveAndSend` **no aplica**: se unen a la transacción ya abierta por `saveConteo`
  (`@Transactional` con propagación REQUIRED), y Spring ignora el nivel de aislamiento de una
  transacción que se une. Dos pedidos a la vez (doble clic, reintento tras timeout, PDV + admin)
  leen los dos `conteoApertura == null` (o `conteoCierre == null`). Los dos insertan su conteo **y
  sus `movimiento_caja` activos**, y el que pierde queda huérfano pero sumando efectivo a la caja.
  Pasa en apertura y en cierre: el `return` temprano del cierre no cierra esa ventana.
  Arreglo: `PdvCajaRepository.findByIdForUpdate(id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)`
  (mismo patrón que `CapturaCuponRepository.java:30`), expuesto por `PdvCajaService`. `saveConteo`
  lo usa en lugar de `findById`. El segundo pedido espera al primero y, al leer, ya ve el conteo
  enlazado, así que cae en la rama idempotente.
- Caja no encontrada → `throw new GraphQLException("No se encontro la caja id=… en esta sucursal")`
  en vez de `return null`.
- `apertura = true` y la caja **ya tiene** `conteoApertura` → devolver ese conteo sin persistir
  nada (espejo exacto de lo que ya hace el cierre). Evita conteos huérfanos si el central reintenta
  tras un timeout o el usuario da doble clic.
- La rama «`saveAndSend` devolvió `null` → borrar la caja» queda como está: `repository.save`
  nunca devuelve `null`, es código inalcanzable y tocarlo no es parte de este fix.

Tests (`src/test/.../graphql/financiero/ConteoGraphQLSaveConteoTest.java`, Mockito):

- caja inexistente → excepción con mensaje (con el código viejo devuelve `null`: **falla**);
- apertura sobre caja con `conteoApertura` → devuelve el existente y `service.saveAndSend` no se
  llama (con el código viejo persiste uno nuevo: **falla**);
- apertura sobre caja sin conteo → persiste y enlaza (regresión del camino normal del PDV);
- `saveConteo` lee la caja con `findByIdForUpdate` y no con `findById` (con el código viejo:
  **falla**).

El Mockito no prueba la carrera en sí. La prueba real va en el paso 9: dos `saveConteo` de
apertura **en paralelo** (`curl … & curl … & wait`) contra la filial local 8082, sobre una caja
de prueba sin conteo. Tiene que quedar **un** conteo enlazado y **un** juego de
`movimiento_caja CAJA_INICIAL`, y el segundo pedido tiene que devolver el mismo id. Se repite con
el cierre.

Gate: `./mvnw clean verify -B` del filial leído del log.

### Fase 3 — desktop: no adivinar la sucursal en Análisis de diferencia

Archivo: `modules/financiero/analisis-diferencia/analisis-diferencia.component.ts`
(`openFallbackTab`).

Hallazgo alto del eje A. Cuando la caja no está en las filas cargadas, `openFallbackTab` abre el
diálogo con el `sucursalId` de **la primera fila de la página**, no el de la caja. El diálogo trae
la caja con ese par `(id, sucursalId)`. Si en esa otra sucursal existe una caja con el mismo id,
se muestra **esa**, y con la fase 1 el conteo se mandaría a esa filial y a esa caja.
(Con la fase 2, si esa caja ya tiene el conteo, la filial devuelve el existente sin escribir. El
riesgo queda en una caja ajena abierta y sin conteo, pero sigue siendo un conteo en la caja
equivocada.)

Arreglo: si la caja no está en las filas cargadas, no abrir el tab y avisar con
`notificacionBar.openWarn("La caja no está en la lista actual. Filtrá por su sucursal para abrirla.")`.
Sin datos inventados.

Tests: el desktop no tiene batería en ningún gate (se prueba en el browser, paso 9). Gate:
`npm run check` al final, leído del log.

## 4. Datos nuevos

Ninguno: no hay columnas, tablas, enums, campos GraphQL ni configuración nuevos. Sin migraciones
en ninguno de los dos repos.

## 5. Orden de despliegue

- Sin DDL y sin cambio de contrato: las fases son independientes en lo técnico.
- **Fase 2 (filial) antes que la 1 en cada canal.** La fase 1 funciona contra una filial vieja,
  pero sin el lock y la idempotencia de la 2, un reintento o un doble clic desde el admin dejan
  conteos duplicados. Nada lo impide técnicamente. Antes de desplegar el central a `bodega` o
  `farmacia`, hay que confirmar la versión de las filiales del canal con `.current-version`
  (runbook de `frc-cicd`). Si una filial está atrasada (offline), el admin sobre esa sucursal
  queda con el riesgo viejo de duplicado, que es el mismo que hoy existe desde el PDV.
- Riesgo residual: si una filial hace rollback automático a una versión anterior a la fase 2, esa
  sucursal vuelve a quedar expuesta. No crea estado nuevo inconsistente.
- **Fase 3 (desktop)** sale cuando quiera: no depende del backend.
- Filial: el merge a `develop` sale solo a las filiales alpha en ≤15 min, y a las 18 de bodega
  recién con la promoción a `master`. Central: el deploy es manual (`gh workflow run Deploy`).
- Tablas que escribe la operación (todas **en la filial**, igual que hoy desde el PDV): `conteo`,
  `conteo_moneda`, `pdv_caja`, `movimiento_caja`. La dirección de replicación no cambia porque el
  lugar donde se escribe no cambia.

## 6. Clientes (eje A)

`git grep` en `origin/develop`:

- **desktop**: `saveConteo` solo desde `conteo.service.ts` ← `adicionar-conteo-dialog`. Con
  `servidor=false` (PDV) no pasa por el central y no cambia.
- Entradas al diálogo desde el admin: `list-caja` (menú gateado por `ANALISIS DE CAJA`),
  `list-venta.abrirConteo()` y `analisis-diferencia` (`onGoToCaja` / `openFallbackTab`). Las dos
  últimas **no** exigen ese rol en el menú. Con el fix, quien entre por ahí sin el rol pasa de ver el
  error críptico a un «No autorizado: se requiere el rol ANALISIS DE CAJA». Decisión a confirmar con
  Franco: que cargar conteos desde el admin quede para `ANALISIS DE CAJA` (y ADMIN). **Confirmado por Franco el 2026-09-27.**
- **mobile-pwa**: no usa `saveConteo`. Abre y cierra con `abrirCajaDesdeServidor`, que no se toca.
- **mobile** (legacy): **sí** llama a `saveConteo` (`conteo.service.ts` ← `caja.component.ts:113`,
  `caja-info.component.ts:140`). Pero manda un argumento `sucId: Int!` que no existe en el schema
  del central ni de la filial, así que **ya falla hoy por validación** antes de llegar al resolver.
  Este fix no lo cambia.
- El `saveConteo` del central no tiene otros llamadores Java (`PdvCajaGraphQL` inyecta
  `ConteoGraphQL` pero no lo usa). `abrirCajaDesdeServidor` arma su propio HTTP contra la
  filial, así que no le afecta el rol nuevo.
- `abrirCajaDesdeServidor` y `editarConteo` ya reenvían el `Authorization` del usuario a la filial:
  la fase 1 no inventa un mecanismo nuevo. `abrirCajaDesdeServidor` le pega al `saveConteo` **de la filial**: la fase 2 le
  cambia dos casos borde (caja inexistente → error en vez de `null`, que ese método ya trata como
  `fail()` porque mira `errors`; apertura repetida → devuelve el existente).

## 7. Qué queda sin verificar

- **Prueba de punta a punta** (desktop admin → central local → filial local): se hace en la fase de
  cierre con central 8081 + filial 8082 perfil `dev` y el desktop en `ng serve -c web`, apuntando la
  sucursal de la caja de prueba a `localhost:8082`. **Ojo**: la `bodega` local tiene las IP reales
  de las filiales; la caja de prueba tiene que ser de una sucursal cuya IP se cambie en la base
  local, o el central local escribiría en una filial de producción.
- Qué pasa si el usuario del admin no existe en la base de la filial: `saveConteo` de la filial
  guarda el conteo con `usuario = null` (hoy igual desde el PDV). No se cambia.

## 8. Fuera de alcance (anotado, no se toca)

- `crearNuevaCaja` desde el admin llama a `savePdvCaja` del central, que crea la caja **solo en la
  base del central** y no en la filial. Es otro bug, con el mismo origen; se reporta aparte.
- El diálogo de alta no manda `usuarioId` en el camino del PDV (filial): el conteo queda sin
  usuario. Preexistente.
- Las validaciones previas al cierre (delivery abierto, ventas con tarjeta sin registrar,
  solicitudes de gasto) viven **solo en el desktop** (`adicionar-caja-dialog.component.ts`,
  compartido por admin y PDV). La filial cierra sin mirarlas si alguien llama la mutación
  directo. Es preexistente y no cambia con este fix.

## 9. Auditoría del plan (paso 5)

Dos auditores en paralelo, sin verse entre sí, con el plan, el código de los tres repos en
`origin/develop`, las skills y `gotchas.md`.

| Eje | Hallazgo | Sev. | Qué se hizo |
|---|---|---|---|
| B | El `SERIALIZABLE` de los servicios no aplica dentro de la transacción de `saveConteo`. Dos pedidos a la vez duplican conteo y movimientos, en apertura y en cierre | alta | Verificado. Fase 2: lock pesimista `findByIdForUpdate` + prueba en paralelo en el paso 9 |
| A | `openFallbackTab` adivina la sucursal con la primera fila de la página | alta | Verificado (`analisis-diferencia.component.ts:484`). Fase 3 nueva en el desktop |
| B | El orden filial → central no tiene gate técnico | media | §5: chequear `.current-version` de las filiales antes de desplegar el central |
| A | Entradas al diálogo sin el rol del menú (`list-venta`, `analisis-diferencia`) | media | §6: documentado. Rol confirmado por Franco (2026-09-27) |
| B | Un rollback de la filial vuelve a exponer el caso | baja | §5: riesgo residual anotado |
| A | El plan decía que `mobile` no usa `saveConteo`: sí lo usa, y ya está roto por `sucId` | baja | §6 corregido |
| B | Las validaciones de cierre son solo client-side | baja | §8: fuera de alcance, preexistente |

Confirmados sin cambios: no hay DDL. El contrato GraphQL queda intacto y el desktop viejo funciona
contra el backend nuevo. Los tests propuestos fallan con el código viejo. El `RestTemplate` del
central tiene connect 10 s / read 30 s (`FrancoSystemsApplication.java:65-67`), y el mensaje de
timeout es correcto porque la fase 1 fuerza `imprimirBalance:false`.
