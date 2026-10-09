# Plan: cancelar retiro y cancelar gasto dejan de ser interruptores (issue #376, bloque 2)

Rama: `fix/financiero-cancelar-retiro-y-gasto-sin-interruptor` (central) + una rama del desktop.

## El problema

`cancelarRetiro(id, sucId)` y `cancelarGasto(id, sucId)` invierten el estado cada vez que se llaman:

- `RetiroService.cancelarRetiro`: `CANCELADO` → `CONCLUIDO`; cualquier otro estado → `CANCELADO`.
- `GastoService.cancelarGasto`: `cancelado = !cancelado`.

El desktop usa la misma mutation para «Cancelar» y para «Habilitar» (menu de `list-retiro` y `list-gastos`,
visible solo con rol ADMIN). Si la respuesta se pierde y el usuario repite, o hace doble clic, o dos
administradores cancelan lo mismo, el segundo pedido **deshace** el primero: el retiro vuelve a descontar de la
caja y nadie lo pidio.

Mirando el codigo aparecieron tres defectos mas en el mismo camino:

1. **Sin lock.** El resolver lee la entidad y el servicio la guarda entera. Dos pedidos simultaneos leen el
   mismo estado y los dos «cancelan» (o uno pisa lo que escribio una verificacion en el medio: `verificar`
   guarda `estado`, `caja_virtual_id` y `movimiento_caja_virtual_id` del mismo retiro).
2. **Sin control de rol en el central.** `cancelarRetiro` y `cancelarGasto` no exigen nada: el `*ngIf="esAdmin"`
   del desktop es lo unico que hay. Cualquier usuario autenticado puede llamarlas por API.
3. **Se puede cancelar un retiro que ya entro a la caja mayor**, y verificar uno cancelado.
   - Cancelar un retiro verificado (o ingresado por el camino viejo) lo deja `CANCELADO` con lo acreditado en
     la caja mayor intacto: la plata «vuelve» a la caja del PDV (`PdvCajaService.generarBalance` ignora los
     retiros cancelados) y sigue en la caja mayor. Queda contada dos veces.
   - «Habilitar» despues lo deja `CONCLUIDO`, perdiendo el `VERIFICADO_*`.
   - `RetiroVerificacionService.verificar` no mira si el retiro esta cancelado.

Produccion (solo lectura, 2026-10-09): bodega 1 retiro cancelado, farmacia 1; el de farmacia (#5154/1, del
21/08) esta cancelado **con su ingreso de 10 activo en la caja mayor** (movimiento #114): el defecto 3 ya paso
una vez, con un monto de prueba. Gastos cancelados: 1 en bodega (de caja mayor, sincronizado por tesoreria), 0
en farmacia. La columna `retiro.estado` es `NULL` en casi todos los retiros (56.569 en bodega).

## Alcance

Central: `RetiroGraphQL`, `RetiroService`, `GastoGraphQL`, `GastoService`, `GastoRepository`,
`RetiroVerificacionService`, `RetiroIngresoService`, `RetiroTesoreriaProcesador`, `RetiroRepository` y los dos
`.graphqls`. Desktop: `list-retiro`, `list-gastos`, las dos queries y sus servicios. La PWA, el mobile y el
filial no llaman a estas mutations (grep vacio; el desktop siempre las manda al central); el filial recibe el
estado por replicacion, que no cambia.

Fuera: el efecto de cancelar un retiro o un gasto de una caja de PDV ya cerrada (el balance se calcula al
leer, asi que cambia lo que muestra; ya es asi), `deleteRetiro` / `deleteGasto`, y `saveRetiro`, que guarda la
entidad entera desde el input y puede pisar estado, caja y movimiento de un retiro existente.

## Diseno

### El pedido dice que quiere

Argumento nuevo y opcional en las dos mutations:

```graphql
cancelarRetiro(id: ID!, sucId: ID, cancelar: Boolean): Boolean!
cancelarGasto(id: ID!, sucId: ID, cancelar: Boolean): Boolean!
```

- `cancelar: true` → queda cancelado. Si ya lo estaba, no escribe nada y devuelve `true`.
- `cancelar: false` → queda habilitado. Si no estaba cancelado, no escribe nada y devuelve `true` (un gasto con
  `cancelado` nulo no pasa a `false`; un retiro verificado no baja a `CONCLUIDO`).
- **sin el argumento** (desktop anterior) → cancelar, nunca habilitar: si ya esta cancelado se rechaza
  (decision 1).

Repetir el pedido es inocuo: lleva el estado final, no hace falta clave de idempotencia, tabla ni migracion.

Habilitar un retiro lo deja en `CONCLUIDO`, como hoy.

### Lock y estado leido de la base

La logica pasa del resolver al servicio, que recibe los ids; el resolver ya no carga la entidad.

- `RetiroService.cancelarRetiro(id, sucId, Boolean cancelar)` y `GastoService.cancelarGasto(id, sucId, Boolean
  cancelar)`: buscan la entidad y la toman con `entityManager.refresh(x, PESSIMISTIC_WRITE)` —lock y relectura
  en un paso, el patron de `RetiroCasoService`—. Nada puede haberla modificado antes en la request.
- Se escribe solo si el estado cambia, con `repository.save` y no con `this.save` (no dispara la notificacion
  de «retiro / gasto realizado»; se conserva el comentario actual).
- Se saca el `try/catch (Exception)` que convierte cualquier error en «No se pudo cancelar…»: tapa los
  rechazos nuevos. Ningun llamador depende de ese mensaje.
- `validarNoEsDeCajaMayor` (gasto pagado desde la caja mayor) se muda al servicio, despues del lock.

Es el mismo lock que toman `verificar` y `anular` de la verificacion, y lo primero que toma cada uno: se
serializan y el segundo ve lo que dejo el primero.

Limite que queda: `Retiro` y `Gasto` no tienen `@DynamicUpdate`, el `save` reescribe la fila entera con lo
leido bajo lock. Eso es correcto frente a quien toma el lock; `saveRetiro` y un UPDATE que suba de la filial
por replicacion no lo toman (el filial no escribe `estado`: grep vacio).

### Guardas

- **Rol:** las dos mutations exigen superusuario (`TesoreriaSecurityService.requireSuperusuario`: rol ADMIN o
  usuario ADMIN). Es un superconjunto de lo que el desktop pide para mostrar el boton (`ROLES.ADMIN`).
- **Cancelar un retiro, lista blanca:** solo si `estado` es nulo o `CONCLUIDO`, no tiene
  `movimiento_caja_virtual_id` ni `caja_virtual_id`, y no tiene verificacion vigente. Si no:
  - verificado o con movimiento → «El retiro #N ya entró a la caja mayor: anulá primero su verificación.»;
  - caja mayor asignada sin movimiento (destino preasignado, pendiente de posteo) → «El retiro #N ya tiene una
    caja mayor asignada: no se puede cancelar.»;
  - `EN_PROCESO` u otro estado → «El retiro #N está en estado X: no se puede cancelar.» (habilitarlo despues
    lo dejaria `CONCLUIDO` y verificable a medio cargar).
- **Habilitar** no tiene guarda de caja mayor. Un retiro cancelado que ya tiene su ingreso (el #5154/1 de
  farmacia) se puede habilitar, y es lo que lo deja consistente.
- **Un retiro cancelado no entra a la caja mayor, por ninguno de los dos caminos:**
  - `RetiroVerificacionService.verificar` lo rechaza: «El retiro #N está cancelado: habilitalo antes de
    verificarlo.»
  - `RetiroIngresoService.ingresarACajaMayor` (el ingreso directo de la pantalla de tesoreria) hoy lee sin
    lock y solo mira si ya tiene movimiento. Pasa a tomar el retiro con lock y a rechazar el cancelado.
  - `RetiroTesoreriaProcesador.procesar` (lo usan ese ingreso y el poller, apagado por default) relee bajo
    lock y no postea un cancelado.
  - `RetiroRepository.findFlotantes` deja de listar cancelados (`estado is null or estado not in (EN_PROCESO,
    CANCELADO)`; un `<>` a secas descartaria los `NULL`, que son casi todos).
- `RetiroVerificacionService.anular` deja el retiro en `CONCLUIDO` siempre; pasa a conservar `CANCELADO` si lo
  encuentra asi (solo puede pasar con datos anteriores; hoy no hay ninguno en produccion).

### Desktop (PR propio)

- Las dos queries mandan `cancelar`; `onCancelarRetiro` / `onCancelarGasto` reciben el valor.
- `list-retiro`: manda `!estabaCancelado`. `list-gastos`: manda `!gasto.cancelado` y agrega la confirmacion
  que hoy no tiene (cancela con un clic).
- El rechazo del central ya se ve: `onCustomMutation` muestra el mensaje en el snackbar. Los dos `subscribe`
  no tienen rama de error (queda un error sin manejar en consola y el `else` de `list-retiro` es codigo
  muerto): se agrega, sin repetir el aviso, y un rechazo relee la lista.

## Tabla de datos nuevos

| Dato | Donde | Nota |
|---|---|---|
| argumento `cancelar: Boolean` | las dos mutations | opcional |

Sin columnas, sin migracion, sin cambios de replicacion (mismas columnas; menos UPDATEs, porque ya no se
escribe cuando el estado no cambia).

## Fases

Central (un PR):

1. **Cancelar / habilitar.** Servicios, resolvers, schema. Tests (`RetiroServiceTest` nuevo, `GastoServiceTest`
   reescrito para la firma nueva):
   - `cancelar=true` dos veces → cancelado, un solo `save`; `cancelar=false` dos veces → habilitado;
     `cancelar=false` sobre un verificado o sobre un gasto con `cancelado` nulo → no escribe;
   - sin argumento → cancela; sobre uno ya cancelado → rechazo, sin `save`;
   - lista blanca: con movimiento, con caja asignada, con verificacion vigente, `EN_PROCESO`, `VERIFICADO_*` →
     rechazo con su mensaje, sin `save`; habilitar un cancelado con movimiento → pasa;
   - gasto de caja mayor → rechazo; sin rol → rechazo (resolver).
2. **Un cancelado no entra a la caja mayor.** `verificar`, `ingresarACajaMayor`, `procesar`, `findFlotantes`,
   `anular`. Tests de cada rechazo y de que `anular` conserva `CANCELADO`.
3. **Tests de integracion** (`-Dit.financiero=true`): seis `cancelar=true` a la vez → cancelado, una sola
   escritura efectiva; cancelar × verificar y cancelar × ingresar a la vez → gana uno, nunca cancelado con
   plata acreditada; `findFlotantes` contra la base real (nulos incluidos, cancelados no).

Desktop (otro PR):

4. Queries, servicios y los dos handlers. `verificar:imports`, build de produccion y prueba en Chrome.

Cada test de bug se corre con el fix neutralizado para ver que falla.

## Prueba de runtime (central local, perfil `dev`)

- Cancelar un retiro dos veces con `cancelar: true`: sigue cancelado. Habilitar dos veces: sigue habilitado.
- Sin argumento: cancela; repetido, rechaza y sigue cancelado.
- Usuario sin rol ADMIN: rechazo.
- Retiro verificado: cancelar se rechaza; anular la verificacion y cancelar pasa.
- Retiro cancelado: verificar e ingresar a caja mayor se rechazan, y no aparece entre los flotantes.
- Gasto: cancelar dos veces, habilitar dos veces, y el de caja mayor se rechaza como hoy.

## Despliegue y rollback

Sin migracion. Requiere reinicio del central.

**Orden, por canal:** el desktop nuevo contra un central sin el cambio falla al cancelar y al habilitar
(«Unknown argument cancelar»). El desktop se actualiza solo por canal y los centrales se despliegan a mano y
por instancia (alpha, farmacia, bodega): el PR del desktop se mergea cuando el central alpha ya lo tiene, y no
se promueve a beta / stable hasta que farmacia y bodega esten desplegados.

Rollback del JAR: no deja datos mal (los valores escritos son los de siempre); reabre los defectos, y rompe
cancelar / habilitar del desktop nuevo contra esa instancia.

## Decisiones tomadas (Franco, 2026-10-09)

1. **Sin argumento significa «cancelar» y nunca habilita.** Sobre uno ya cancelado se rechaza con «Ya está
   cancelado. Para habilitarlo actualizá el sistema.» «Habilitar» no anda desde un desktop sin actualizar.
2. **Rol:** las dos mutations exigen superusuario en el central.
3. **Guardas de caja mayor:** incluidas (lista blanca para cancelar; un cancelado no se verifica, no se
   ingresa y no se lista como flotante).
4. **Confirmacion al cancelar un gasto** en el desktop: se agrega.

## Queda sin verificar

- El retiro #5154/1 de farmacia sigue cancelado con su ingreso de 10 activo. Con este cambio se arregla
  habilitandolo desde la pantalla; no se toca solo.
- Si alguna filial actualiza la fila de un retiro o un gasto despues de crearla: ese UPDATE sube al central
  con su `estado` / `cancelado` y desharia una cancelacion. No encontre codigo del filial que lo haga.
- `verificar` lee sus guardas de la instancia que devuelve el lock; vale porque es su primera carga (el
  resolver no carga el retiro). Se deja escrito en el codigo.

## Auditoria del plan (paso 5, 2026-10-09)

| Hallazgo | Que se hizo |
|---|---|
| El ingreso directo a caja mayor acepta un cancelado, sin lock, y los flotantes lo listan (bloqueante, los dos auditores) | fase 2 |
| Cancelar deberia ser lista blanca de estados, no solo «sin caja mayor»; `EN_PROCESO` | guardas |
| Mensaje falso cuando hay caja asignada sin movimiento | mensaje propio |
| `anular` pisa `CANCELADO` | lo conserva |
| El modo sin argumento deja vivo el defecto en los desktops viejos | decision 1 |
| `validarNoEsDeCajaMayor` queda en el resolver, antes del lock | al servicio |
| `refresh` con lock en un paso, como `RetiroCasoService` | adoptado |
| UPDATE dirigido con la guarda en el WHERE | no: con el lock compartido alcanza y la verificacion vigente igual pide otra consulta |
| Despliegue por instancia y por canal | seccion de despliegue |
| El rechazo si se ve en el desktop; falta la rama de error | desktop |
| `saveRetiro` puede pisar estado, caja y movimiento | fuera de alcance, anotado |
