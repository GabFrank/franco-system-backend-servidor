# Plan: resolver, asignar y soltar un caso de retiro en una transaccion, con lock (issue #376, punto 3)

Rama: `fix/financiero-resolver-caso-de-retiro-atomico` (sobre `origin/develop` d5ecfc18, que ya trae #384).
Este archivo es registro de trabajo: se borra en el PR final.

## El problema

Las tres mutations de casos de retiro viven en el resolver (`RetiroVerificacionGraphQL`), que no es
transaccional: cada `casoRepository.save` commitea solo.

- **`resolverRetiroCaso`** guarda el caso `RESUELTO` y **despues** llama a `RetiroVerificacionService.anular`.
  Si la anulacion se rechaza (ya anulada, saldo insuficiente para devolver lo acreditado, sin permiso sobre
  la caja), el caso queda resuelto con el veredicto «conto mal tesoreria» y la verificacion **vigente**, con
  el monto errado en la caja mayor; el desktop recibe un error. Y resolver de nuevo ya no se puede: «El caso
  ya está resuelto».
- **`asignarRetiroCaso`** no valida el estado ni el dueno: asignar un caso `RESUELTO` lo devuelve a
  `EN_INVESTIGACION`, y se le puede sacar el caso a quien lo esta investigando.
- **`soltarRetiroCaso`** (no esta en la issue, es el mismo defecto): lee el caso sin lock y lo guarda
  entero. Si corre a la vez que una resolucion, puede pisar el veredicto recien guardado y dejar el caso
  `ABIERTO`.
- Ninguna de las tres lockea el caso: dos resoluciones simultaneas pasan las dos el chequeo de «ya esta
  resuelto» y la segunda pisa a la primera.
- **`RetiroVerificacionService.anular`** (lo encontro la auditoria) cierra el caso de la verificacion que
  anula: lo lee sin lock y lo guarda **entero**. Si corre a la vez que una resolucion no se traba: espera, y
  despues escribe encima con los datos que leyo antes. El caso queda «CERRADO POR ANULACION», sin veredicto
  ni responsable, a nombre de quien anulo.

## Alcance

Las tres mutations del caso, el cierre del caso dentro de `anular`, y el dialogo del desktop que hoy esta
escrito alrededor del defecto. Dos PR: central y desktop.

Fuera, con ciclo propio: el lote multi-moneda para movimientos y transferencias (mutation nueva + desktop) y
lo que queda del bloque 2 (pata suelta de una transferencia, `cancelarRetiro` / `cancelarGasto`).

No se agrega control de «quien puede soltar» (hoy cualquiera con el rol suelta el caso de otro; en el
desktop el boton solo aparece para el dueno o el admin) ni de «a quien se puede asignar» (por API se puede
asignar a un tercero; el desktop siempre manda al usuario logueado). La issue no los pide. Quedan anotados.

## Diseno: central

Servicio nuevo `RetiroCasoService` (`service/financiero`, `@RequiredArgsConstructor`), con los tres metodos
`@Transactional`. El resolver conserva `seg.requireGestionar()` como primera linea, le pasa al servicio el
usuario actual y si es superusuario, y delega. Sin cambios de schema GraphQL ni de base.

### Tomar el caso: lock y estado fresco

Los tres metodos arrancan igual:

1. `casoRepository.findById(casoId)` — para tener `retiroId` y `sucursalId`, que no cambian.
2. `entityManager.refresh(caso, PESSIMISTIC_WRITE)` — toma la fila con `FOR UPDATE` **y** recarga la
   instancia. Aca `refresh` y no `lockById` + proyeccion (el patron de `revertir`): hay que releer dos campos
   (`estado` y `asignadoA`), el servicio es nuevo (inyectar el `EntityManager` no rompe ningun constructor) y
   en ese punto el caso no tiene cambios sin flushear. **Nada puede modificar el caso entre el paso 1 y el
   2**: el `refresh` lo descartaria en silencio. Va como comentario en el codigo.

### `resolver`

- Si se pidio anular la verificacion, **antes** de tomar el caso se toma el retiro
  (`retiroRepository.lockByIdAndSucursalId`). `anular` arranca por el retiro: sin eso, una resolucion
  (caso → retiro) y una anulacion directa (retiro → … → caso) se traban una a la otra. Solo en este caso: el
  retiro llega por replicacion y un lock sobre su fila frena al apply worker mientras dure; `asignar`,
  `soltar` y `resolver` sin anulacion no lo toman.
- Validaciones de hoy, con sus textos: ya resuelto, «lo esta investigando X», falta veredicto, responsable,
  reintegro, signo, «solo se anula cuando el error fue de tesoreria».
- **Nuevo:** si se pidio anular y el caso no tiene verificacion → rechazo. Hoy la anulacion se omite en
  silencio y el caso se resuelve igual.
- Guardar el caso y, en la **misma transaccion**, `retiroVerificacionService.anular(...)`. Si lanza, se
  deshace tambien el caso: queda como estaba y se puede resolver de nuevo (sin anular, o despues de arreglar
  lo que la freno).
- Se conserva «primero guardar, despues anular»: `anular` deja intacto un caso ya `RESUELTO`.

### `asignar`

- Caso `RESUELTO` → rechazo («El caso ya está resuelto»).
- Sin asignar → se asigna. Asignado al **mismo** usuario destino → se devuelve tal cual (un reintento no
  falla). Asignado a **otro** → rechazo («El caso ya lo tomó X»), salvo superusuario, con el mismo criterio
  con que hoy el ADMIN resuelve un caso ajeno.
- La regla que ya existe (no se asigna a quien hizo la verificacion) queda igual.

### `soltar`

Misma logica de hoy, bajo lock y con el estado fresco.

### El cierre del caso dentro de `anular`

Deja de leer el caso y guardarlo entero. Pasa a un `UPDATE` dirigido en `RetiroCasoRepository`:

`update RetiroCaso c set estado = RESUELTO, resolucion, resueltoPor, resueltoEn where c.verificacion.id = :v and c.estado <> RESUELTO`

Es el patron de `PrestamoCuotaRepository.marcarVencidas` (#301): el `UPDATE` espera el lock de una
resolucion en curso y PostgreSQL vuelve a evaluar el `WHERE` al despertar, asi que un caso recien resuelto
queda afuera; y solo toca cuatro columnas, asi que tampoco pisa un `asignar` simultaneo.

### Orden de locks

- `anular`: retiro → movimientos → saldos → verificacion → retiro → caso (el `UPDATE`, al final).
- `resolver` con anulacion: retiro → caso → (anular) movimientos → saldos.
- `verificar`: retiro → saldos → `INSERT` del caso nuevo.
- `resolver` sin anulacion, `asignar`, `soltar`: solo el caso, y no esperan nada despues de tenerlo.

Todo lo que toma el retiro lo toma primero. Vale mientras ningun camino tome un caso sin pasar antes por el
retiro y **despues** quiera el retiro o un saldo.

## Diseno: desktop (`frc-sistemas-integrados-angular`, PR propio, despues del central)

`detalle-caso-dialog` esta escrito alrededor del defecto: ante un **rechazo** con la anulacion pedida se
cierra con «El caso pudo haber quedado resuelto aunque la anulación de la verificación falló». Con el central
corregido ese aviso es falso, y el usuario pierde el informe que escribio.

- Rechazo (con o sin anulacion pedida) → el dialogo **queda abierto** con el mensaje del central, para
  corregir o reintentar sin la anulacion. Es lo que ya hace cuando no se pidio anular.
- «Sin respuesta» (red, corte, respuesta vacia) → sigue cerrando con «No se pudo confirmar si el caso quedó
  resuelto»: ahi la incertidumbre es real.

Orden: central primero. Un desktop nuevo contra un central viejo dejaria el dialogo abierto tras un rechazo
con el caso ya resuelto; el reintento recibe «El caso ya está resuelto» y se corrige solo, pero confunde.

## Tabla de datos nuevos

Ninguno: sin columnas, campos ni argumentos nuevos.

## Fases

Central (un PR):

1. **`RetiroCasoService` + resolver que delega.** Tests (`RetiroCasoServiceTest`, Mockito; el `refresh` se
   simula con un `doAnswer` que muta la instancia, para que el test falle si se quita):
   - resolver: toma el retiro antes que el caso cuando anula (`InOrder`) y no lo toma cuando no; usa el
     estado refrescado (instancia `EN_INVESTIGACION`, el `refresh` la deja `RESUELTO` → rechazo) y el
     `asignadoA` refrescado; guarda antes de anular; si `anular` lanza, la excepcion sale del metodo; pedir
     anular sin verificacion → rechazo; cada validacion existente conserva su mensaje;
   - asignar: resuelto → rechazo; tomado por otro → rechazo; por otro siendo superusuario → reasigna; por el
     mismo → sin cambios y sin guardar; a quien verifico → rechazo;
   - soltar: resuelto segun el estado refrescado → rechazo; en investigacion por otro → se suelta
     (comportamiento de hoy).
2. **Cierre del caso en `anular` con `UPDATE` dirigido.** Ajustar `RetiroVerificacionAnularTest`.
3. **Tests de integracion** (`RetiroCasoIT`, `-Dit.financiero=true`; no corren en CI):
   - atomicidad: una verificacion real cuya anulacion no puede pasar (caja propia sin saldo negativo, de la
     que se saca lo acreditado) → `resolver(..., anular = true)` lanza y el caso **sigue sin resolver** en la
     base;
   - lo mismo con la anulacion posible → caso resuelto, verificacion anulada, retiro flotando;
   - **`resolver` y `anular` directo a la vez → el veredicto sobrevive** (se mira el contenido del caso, no
     solo que no haya deadlock);
   - dos `resolver` a la vez → uno pasa.

Desktop (otro PR, despues de que el central este desplegado en el canal):

4. **`detalle-caso-dialog`**: rechazo → dialogo abierto. `npm run check` y prueba en Chrome.

Cada test de bug se corre con el fix neutralizado **dentro del servicio nuevo** (quitar el `refresh`, el lock
del retiro, o anular antes de guardar) para ver que falla: en el codigo viejo el servicio no existe.

## Prueba de runtime (central local, perfil `dev`)

La base local no tiene casos ni verificaciones: se crean verificando un retiro con diferencia. La
replicacion del cluster local es solo local (`bodega` ↔ `general` por `localhost`; las suscripciones a
farmacia estan deshabilitadas), asi que escribir un retiro local no sale de la maquina.

- Resolver con «conto mal tesoreria» + anular, con la anulacion imposible: error, y el caso sigue en
  investigacion con la verificacion vigente.
- Lo mismo con la anulacion posible: caso resuelto, verificacion anulada, retiro flotando otra vez.
- Seis `resolverRetiroCaso` simultaneos: uno pasa, cinco «El caso ya está resuelto».
- `resolverRetiroCaso` + `anularVerificacionRetiro` simultaneos: sin deadlock **y** el caso conserva su
  veredicto.
- Asignar un caso resuelto: rechazo. Asignar uno tomado por otro: rechazo. Volver a tomar el propio: pasa.
- Desde el desktop (con su PR): rechazo con la anulacion pedida → el dialogo queda abierto con lo escrito.

### Resultado (2026-10-08)

**Tests de integracion** (`RetiroCasoIT`, base local): 5 en verde.

- Con la anulacion imposible, `resolver(..., anular)` lanza «Saldo insuficiente» y el caso sigue en
  investigacion, sin veredicto; con la plata de vuelta, la misma resolucion pasa y la verificacion queda
  anulada.
- Resolucion con su transaccion abierta + anulacion directa: la anulacion espera y el caso conserva veredicto,
  informe y quien resolvio. **Con el cierre viejo (leer y guardar) el mismo test falla: «la anulacion piso el
  veredicto: expected FALTANTE_PDV but was null».**
- Tres resoluciones a la vez: una pasa. Asignar un caso resuelto: rechazo. Anular con el caso todavia abierto:
  queda «CERRADO POR ANULACION», sin veredicto y sin tocar a quien estaba asignado.

La atomicidad no se pudo «neutralizar» dentro del servicio para ver el test en rojo (sin `@Transactional` el
`refresh` con lock ni siquiera corre); lo que la prueba es que el caso sigue sin resolver en la base.

**Runtime por GraphQL** (central local, dos usuarios de prueba con rol de gestionar tesoreria: uno verifica,
otro investiga):

| Caso | Resultado |
|---|---|
| Resolver + anular con la anulacion imposible | «Saldo insuficiente en la caja virtual»; el caso sigue `EN_INVESTIGACION`, sin veredicto, verificacion vigente |
| Seis resolver + anular simultaneos, ya posible | 1 exito, 5 «El caso ya está resuelto»; verificacion anulada; lo acreditado sale de la caja una vez |
| Asignar un caso resuelto | «El caso ya está resuelto» |
| Tomar un caso que tiene otro | «El caso ya lo tomó X.» |
| Resolver un caso ajeno | «El caso lo está investigando X…» |
| Soltar, tomar, volver a tomar | pasan los tres |
| `resolverRetiroCaso` y `anularVerificacionRetiro` a la vez, tres rondas | el caso conserva `FALTANTE_PDV`, su informe y quien resolvio |
| Pedir anular con otro veredicto | rechazo; el caso no cambia |
| El verificador intenta tomar su propio caso | «El caso no puede asignarse a quien hizo la verificación» |

Log sin `deadlock`.

**Desktop** (worktree propio, `ng serve` contra el central local, Chrome): resolver el caso con «Contó mal
tesorería» y la anulacion pedida, con la anulacion imposible → aviso «Saldo insuficiente en la caja virtual»
y el dialogo **queda abierto** con el veredicto y el informe; desmarcar la anulacion y resolver de nuevo → el
caso queda resuelto con ese informe. `npm run check` sin errores.

## Auditoria del diff (paso 8, 2026-10-08)

Dos auditores (autorizacion + esquema; contrato + correccion). Ningun hallazgo alto. La logica salio del
resolver sin perder validaciones ni mensajes; el control de rol sigue como primera linea; sin cambios de
schema; `retiro_caso` y `retiro_verificacion` no se replican.

| Hallazgo | Severidad | Que se hizo |
|---|---|---|
| Ningun IT ejercitaba el `UPDATE` de cierre sobre un caso abierto | media | test nuevo en `RetiroCasoIT` |
| `asignar` devolvia sin guardar un caso `ABIERTO` que ya figuraba a nombre del destino (dato incoherente) | baja | solo es no-op si esta en investigacion. Test |
| El test con mocks de «si la anulacion se rechaza» pasaria sin la transaccion | baja | test de que los tres metodos son `@Transactional` |
| `refresh` sobre un caso borrado entre la lectura y el lock saldria como error interno | baja | no aplicado: ningun codigo borra casos |
| El lock del retiro se toma antes de validar dueno y veredicto: un pedido que igual se rechaza lo retiene hasta el rollback | baja | se acepta; validar antes duplicaria logica |
| `limpiar` del IT captura un fallo de anulacion y solo lo imprime | baja | se acepta |
| `findByVerificacionId` quedo sin uso en `src/main` | baja | se deja |

## Despliegue y rollback

- Central: sin migracion ni cambio de schema; rollback del JAR inocuo. Requiere reinicio (workflow Deploy;
  mergear a `develop` no despliega).
- Desktop: despues del central de cada puerta. Rollback inocuo. Sin impacto en auto-update.
- Cliente viejo contra central nuevo: funciona; solo muestra un aviso que ya no es cierto.

## Queda sin verificar

- No corrige casos que ya hayan quedado resueltos con su verificacion vigente, ni casos «cerrados por
  anulacion» que hayan perdido su veredicto. Los primeros se ven como `RESUELTO` + `ERROR_DE_CONTEO_TESORERIA`
  con la verificacion sin anular (que es tambien el estado legitimo de quien resolvio **sin** pedir la
  anulacion). Produccion no se miro.
- El bloqueo real y el rollback solo se comprueban contra PostgreSQL (IT y runtime).
- Tras un rollback, la instancia del caso en memoria: la excepcion corta la respuesta, asi que no llega al
  cliente (visto en runtime: el caso se relee `EN_INVESTIGACION`). No se probo una request que encadene dos
  mutations.
- El retiro de reintegro solo se valida como no nulo; que exista no se verifica. Ya era asi.

## Auditoria del plan (paso 5, 2026-10-08)

Dos auditores. No se contradijeron.

| Hallazgo | Que se hizo |
|---|---|
| `anular` directo pisa el veredicto de una resolucion simultanea (lee el caso sin lock y lo guarda entero). La prueba prevista, «sin deadlock en el log», pasaba con el defecto adentro | entra: `UPDATE` dirigido en el cierre del caso; IT y runtime miran el **contenido** del caso |
| El dialogo del desktop asume el defecto y cerraria con un aviso falso | fase de desktop |
| `asignar`: contra que usuario se compara | definido contra el usuario destino; asignar a un tercero queda como esta, anotado |
| Pedir anular sobre un caso sin verificacion se omite en silencio | rechazo |
| El plan describia mal el orden de locks de `anular` (el caso va ultimo) | corregido |
| El lock del retiro frena al apply worker de la replicacion mientras dure | se toma solo cuando se anula, que `anular` ya lo hacia; no se extiende a asignar ni soltar |
| El IT necesita datos que la base local no tiene | caja propia sin saldo negativo; la anulacion se fuerza a fallar sacando de la caja lo acreditado |
| `refresh` descarta cambios sin flushear del caso | comentario; nada modifica el caso antes |
| #384 figuraba como abierto | ya mergeado; la rama se actualizo sobre el `develop` nuevo |
