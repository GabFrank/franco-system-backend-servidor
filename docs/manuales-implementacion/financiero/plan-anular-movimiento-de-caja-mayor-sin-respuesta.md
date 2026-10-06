# Plan: anular un movimiento de caja mayor sin duplicar el contra-movimiento (PR 13a)

Parte de #390. Primer PR del bloque **financiero** (reintentos de operaciones de plata).

## Bloque financiero: qué salió del relevamiento

Los diálogos de tesorería dejan reintentar ante cualquier error, sin distinguir un rechazo de un «sin respuesta»
(la operación pudo haberse aplicado). Lo que decide el riesgo es qué hace el **central** con un reintento idéntico:

| Operación (desktop) | Reintento idéntico en el central | PR |
|---|---|---|
| **Anular movimiento manual / de maletín** (`caja-virtual-dashboard`) | **duplica**: postea otro contra-movimiento; no mira si ya estaba anulado | **13a (este)** |
| Pago de compras / gastos **parcial**; alta de gasto y de vale (`pagar-compras-dialog`) | duplica (el pago total, los vales y RRHH se rechazan por estado) | 13b |
| Ingreso / egreso / ajuste, transferencia entre cajas, ajuste por conteo, maletín | duplica; además los de varias monedas van en lote y un fallo parcial deja aplicadas las otras | 13c |
| Operación financiera, entrada / salida varia, ajuste de saldo bancario | duplica | 13d |
| Emitir cheque; alta / baja de chequera (hoy se cuelga ante un error de red); cobrar / anular cheque | emitir duplica; cobrar y anular se rechazan | 13e |
| Anular pago, operación financiera, entrada varia, verificación de retiro | se rechaza («ya está anulado»): alcanza con avisar y releer | 13f |

Cada uno con su plan. Este documento es el del 13a.

## 1. El problema del 13a [verificado leyendo]

**Central** — `TesoreriaService.anular(movimientoId, …)`: valida permiso, origen (MANUAL o MALETIN) y antigüedad, y
llama a `revertir`, que postea un AJUSTE con el efecto negado y marca el original `activo = false`. **No verifica
que el original siga activo** ni toma un lock sobre él. Anular dos veces el mismo movimiento postea **dos**
contra-movimientos: el saldo de la caja queda corrido en sentido contrario al original. El comentario de
`PagoProveedorService` lo dice («`revertir` no mira si el original ya está revertido»): los módulos dueños se cuidan
solos; la anulación manual, no. Las otras tres anulaciones del mismo botón (pago a proveedor, operación financiera,
verificación de retiro) sí rechazan la segunda vez.

No hace falta un error de red para que pase: alcanza con dos usuarios con la caja abierta, o una lista sin
refrescar.

**Desktop** — `caja-virtual-dashboard.onAnular(mov)`:
- tras confirmar, el pedido sale y **la fila sigue con «Anular» habilitado** mientras está en vuelo (no hay marca);
- ante un error **no recarga**: la fila sigue «activa» y anulable aunque la anulación se haya aplicado;
- no distingue rechazo de «sin respuesta»; con un resultado `null` sin error no avisa ni recarga.

## 2. Cambio en el central (PR propio, repo `franco-system-backend-servidor`)

En `TesoreriaService.anular`:
- leer el movimiento **con lock pesimista** (`lockById` nuevo en `MovimientoCajaVirtualRepository`), para que dos
  anulaciones simultáneas no pasen las dos la verificación;
- si `activo == false` → `GraphQLException("El movimiento #N ya está anulado.")`.

`revertir` no se toca: lo llaman los módulos dueños, que ya tienen su propio guard.

Test en `TesoreriaServiceTest`: anular dos veces → la segunda lanza y el saldo queda como tras la primera.

Sin migración, sin cambio de schema GraphQL, sin impacto en replicación (caja mayor es solo del central).
Rama `fix/financiero-no-anular-dos-veces-un-movimiento-de-caja` desde `origin/develop`.

## 3. Cambio en el desktop (PR propio)

`caja-virtual-dashboard`:
- **En vuelo**: los movimientos con una anulación pendiente se marcan (conjunto de ids) y su «Anular» queda
  deshabilitado (campo de la fila, sin funciones en el HTML). Vale para las cuatro ramas.
- **Sin respuesta** (red, corte, respuesta vacía, o resultado `null` sin error): aviso «No se pudo confirmar la
  anulación: se vuelve a leer la caja» y **recarga**. La fila queda **bloqueada hasta que una lectura de
  movimientos termine bien**: si la recarga también falla, sigue sin poder anularse (con el cartel de lista no
  cargada que la pantalla ya tiene).
- **Rechazo**: el motivo ya lo mostró el genérico (o se muestra el mensaje del servidor en la rama de pago); se
  recarga igual —un «ya está anulado» significa que la lista estaba vieja— y la fila se desbloquea.
- Un solo aviso por flujo: en el corte del link no se agrega aviso propio.

`graphqlErrorUtils.esRechazoDelServidor`: hoy solo reconoce el arreglo de `onCustomMutation` / `onSave`. Se
extiende para reconocer también la forma de `onSaveCustom` (`{ graphQLErrors: […] }`), con la misma regla (no vacío
y ninguno es la respuesta vacía). Un error de red (sin `graphQLErrors`) sigue siendo «sin respuesta». Lo van a usar
todos los PRs del bloque.

Rama `fix/financiero-anular-movimiento-de-caja-sin-respuesta` desde `origin/develop`.

## 4. Orden y dependencia entre los dos PRs

Independientes: el desktop funciona con el central viejo (bloquea y recarga) y el central nuevo protege a un
desktop viejo (rechaza la segunda). El que cierra el agujero de verdad es el del central.

## Fases

| Repo | Fase | Commit |
|---|---|---|
| central | 1 | `fix(financiero): rechazar la anulacion de un movimiento de caja ya anulado` (guard + lock + test) |
| desktop | 1 | `fix(financiero): no dejar reanular un movimiento de caja cuya anulacion quedo sin respuesta` |

Central: `./mvnw -o test -Dtest=TesoreriaServiceTest` y el build de la pieza antes del push. Desktop: `npm run check`.

## Prueba de runtime

Central local :8081 **desde la rama del fix** (perfil sin replicación, schedulers verificados apagados), desktop en
el navegador contra él.
1. Crear un ingreso manual de prueba en una caja mayor local y anularlo → contra-movimiento, saldo vuelve.
2. Anular el mismo movimiento otra vez llamando a la mutation directamente → «ya está anulado», saldo sin cambio.
   Lo mismo contra el central **sin** el fix, para dejar registrado el duplicado (en la base local).
3. Desktop, anulación con cuerpo HTTP vacío real (la petición se desvía en el navegador): aviso, recarga, fila
   bloqueada si la recarga falla.
4. Desktop, central congelado al anular (`kill -STOP`): corte del link, sin aviso doble, recarga al volver.
5. Doble clic / segundo intento con la anulación en vuelo → un solo pedido.
6. Las otras tres ramas (pago, operación financiera, retiro): rechazo simulado → recarga.

## Riesgos y qué queda sin verificar

- El lock agrega una espera breve si dos usuarios anulan a la vez; el segundo recibe el rechazo.
- Movimientos ya anulados dos veces en producción (si los hay) no se corrigen acá: se pueden detectar buscando
  dos contra-movimientos con el mismo `referencia_id`. Se anota para revisar con Franco.
- El central local corre desde un worktree de pruebas: la rama del fix se levanta ahí.

## Ajustes por la auditoría del plan (mandan sobre lo de arriba)

### Central
- `lockById` **reemplaza** al `findById` de `anular` (tiene que ser la primera carga de la fila en la transacción
  para que el segundo pedido relea el `activo` ya confirmado). Mismo patrón que `ChequeRepository.lockById`.
- Orden: lock → permiso de la caja → **ya anulado** → origen → antigüedad. Así quien no tiene permiso no se entera
  del estado, y un movimiento viejo ya anulado dice «ya está anulado» y no «requiere autorización».
- `Boolean.FALSE.equals(orig.getActivo())` (un `null` cuenta como activo, igual que en `registrar`).
- **Los tres tests existentes de `anular` stubean `findById`**: con el cambio pasarían en verde por el motivo
  equivocado («Movimiento no encontrado»). Se migran a `lockById` y se les agrega la verificación del mensaje.
  Tests nuevos: anular dos veces (la segunda lanza y no se postea otro contra-movimiento) y `activo = null`.
- El lock no se puede probar con mocks: en runtime se mandan **dos anulaciones simultáneas** del mismo movimiento y
  se espera un solo contra-movimiento y un rechazo.
- Sin deadlock nuevo: ningún otro flujo lockea `movimiento_caja_virtual`; el orden es movimiento → saldo.

### Desktop
- **No existe** hoy un indicador de «la lectura de movimientos falló» ni un contador de cargas (sí para saldos): la
  lista conserva las filas viejas con un aviso pasajero, y una respuesta lenta puede pisar a una nueva. Se agregan
  `movimientosCargaId` (solo cuenta la última) y un cartel «No se pudieron leer los movimientos» + Reintentar.
- El bloqueo se guarda en el **componente** (las filas se reconstruyen en cada carga) y `_anulable` se deriva al
  armar la fila. La clave es la de la **operación**, no la del movimiento: `referenciaId` para pago y operación
  financiera, retiro + sucursal para la verificación, id del movimiento para el manual (todas las patas de una
  misma operación quedan bloqueadas juntas).
- Una clave se libera solo cuando termina bien una lectura de **caja** que **empezó después** de terminar la
  anulación (una lectura que ya estaba en vuelo puede traer el estado viejo).
- **Helper nuevo** `erroresDeRechazo(error)` en `graphqlErrorUtils` (normaliza el arreglo y `{ graphQLErrors }`,
  devuelve `null` si no es un rechazo); `esRechazoDelServidor` **no cambia** (dos llamadores hacen `error.some(…)`
  y romperían con un objeto).
- **Rama de pago a proveedor**: `mutar` emite un `Error` plano ante un rechazo. Hasta el 13b (que lo tipa), en esta
  rama se toma como rechazo un error sin `networkError` cuyo mensaje no es la respuesta vacía.
- **Un aviso por flujo**: en las tres ramas por `onSaveCustom` el genérico ya avisó el rechazo, el error de red y
  la respuesta vacía (y el link, el corte): ahí solo se recarga. El aviso propio «No se pudo confirmar la
  anulación…» queda para lo que nadie avisó: resultado `null` sin error y la rama de pago sin respuesta.
- La recarga se hace en un único punto para las cuatro ramas, con `recargar()` (movimientos y saldos).
- Fuera de este PR: la anulación desde la fila de **banco** (va con el 13f).

### Anotado para después
- `revertir` tampoco verifica el estado; lo usan vales y liquidaciones vía `revertirMovimiento`. Agregar el guard
  ahí exige auditar a cada módulo dueño: PR propio en el central.
- Una pata de una transferencia entre cajas es de origen manual y se puede anular suelta (queda la otra). Previo.

## Fases (reemplaza la tabla de arriba)

| Repo | Fase | Commit |
|---|---|---|
| central | 1 | `fix(financiero): rechazar la anulacion de un movimiento de caja ya anulado` (lock + guard + tests) |
| desktop | 1 | `fix(financiero): avisar cuando no se pudieron leer los movimientos de la caja` (contador + cartel) |
| desktop | 2 | `fix(financiero): no dejar reanular un movimiento cuya anulacion quedo sin respuesta` (helper + bloqueo + recarga) |

Despliegue: central primero (cierra el agujero con cualquier desktop); el desktop es independiente.

## Auditoría del plan (paso 5, 2026-10-06)

| Eje | Hallazgo | Sev. | Qué se hizo |
|---|---|---|---|
| A | Los tests existentes de `anular` quedarían en verde por el motivo equivocado | alta | se migran a `lockById` con verificación del mensaje |
| A | El desktop no tiene indicador ni contador de la lectura de movimientos (el plan lo daba por existente) | alta | fase propia |
| A | Bloqueo por fila: se pierde al recargar y no cubre las otras patas de la operación | alta | conjunto en el componente, clave por operación |
| A | La rama de pago emite un `Error` plano: el helper no lo reconoce | alta | regla explícita para esa rama hasta el 13b |
| A | Doble aviso en red y respuesta vacía | media | aviso propio solo donde nadie avisó |
| B | Extender `esRechazoDelServidor` rompe a dos llamadores | media | helper nuevo |
| B | Liberar el bloqueo con una lectura que ya estaba en vuelo | media | solo lecturas que empezaron después |
| A | Orden del guard respecto del permiso y la antigüedad; `activo` nulo | baja | definido |
| B | El lock no se prueba con mocks | media | dos anulaciones simultáneas en runtime |
| A | `revertir` sin guard (vales, liquidaciones); pata suelta de transferencia; fila de banco | media | anotado para PRs propios |
| A | `anular` no verifica `activo` ni lockea; solo lo llama la mutation; sin deadlock | — | verificado |

## Implementación: desvíos (2026-10-06)

- **Desktop**: no se agregó el helper `erroresDeRechazo`. No hizo falta: las tres ramas por `onSaveCustom` ya llegan
  avisadas y la caja se relee con cualquier resultado, así que no hay que distinguir el rechazo. Queda para el 13b.
- **Desktop**: los avisos pasajeros de «no se pudieron cargar los movimientos» se reemplazan por el cartel.
- **Central**: se agregó un test del orden permiso → «ya anulado».
- Sin tocar: la anulación desde la fila de banco no entra en el bloqueo (13f); un rechazo sin mensaje al anular un
  pago dice «Error al registrar el pago» (previo).

## Prueba de runtime (2026-10-06)

Central local :8081 con la replicación apagada (schedulers verificados), caja mayor local «RRHH» (id 1).

**Central sin el fix** (rama anterior): ingreso de 1.000, anulado dos veces por la mutation → **dos**
contra-movimientos de −1.000; el saldo pasó de 8.300.000 a 8.299.000. Bug reproducido.

**Central con el fix**:
| Caso | Resultado |
|---|---|
| Tercera anulación del movimiento anterior | «El movimiento #45 ya está anulado.» |
| Ingreso de 2.000, anulado dos veces seguidas | la primera lo revierte; la segunda, «ya está anulado»; saldo sin cambio |
| Egreso de 3.000 con **cuatro anulaciones simultáneas** | un solo contra-movimiento y tres rechazos; saldo correcto |

**Desktop** (contra el central con el fix):
| Caso | Cómo | Resultado |
|---|---|---|
| Anular con cuerpo HTTP vacío real | petición desviada en el navegador | aviso del genérico; se relee; la fila vuelve anulable (no se había aplicado) |
| Anulación en vuelo | servicio reemplazado (lento) | «Anular» deshabilitado; un segundo intento no abre la confirmación ni llama |
| Sin respuesta y la relectura también falla | servicio reemplazado | la fila sigue bloqueada y aparece el cartel; con Reintentar se libera |
| Resultado vacío sin error | servicio reemplazado | «No se pudo confirmar la anulación…» y relectura |
| Anulación normal | real | «Movimiento anulado», fila tachada, saldo actualizado |
| Lista vieja (anulado por fuera) | real | el central rechaza «ya está anulado», se relee y la fila queda tachada |
| Central congelado al anular | real (`kill -STOP`) | corte a los 60 s con el aviso del link, sin aviso doble; la relectura falla → cartel y fila bloqueada; al volver el central se libera |
| Pago a proveedor sin respuesta / rechazado | filas y servicio simulados | las dos patas del pago bloqueadas juntas; «No se pudo confirmar…» / mensaje del servidor |

Los movimientos de prueba se anularon y la caja local quedó en su saldo original (8.300.000).

Sin probar: las ramas de operación financiera y verificación de retiro con datos reales (comparten el camino de la
manual); la anulación desde la fila de banco (no cambia).

## Auditoría de los diffs (paso 8, 2026-10-06)

Sin hallazgos altos ni medios en ninguno de los dos.

| Lado | Hallazgo | Sev. | Qué se hizo |
|---|---|---|---|
| central | Sin test del orden permiso → «ya anulado» | baja | test agregado |
| central | Deadlock teórico si un módulo dueño revirtiera un movimiento manual | baja | anotado para el PR de `revertir` |
| desktop | La rama «sin cuenta» de banco no contaba como lectura | baja | cuenta |
| desktop | La rama de pago nunca recibe «respuesta vacía» como error (llega como resultado nulo) | baja | comentario corregido |
| desktop | Si falla la consulta previa del retiro se bloquea y relee sin necesidad | baja | se deja: es pasajero |
| ambos | Transacción, lock, liberación del bloqueo, avisos por rama, reglas del repo | — | verificado |
