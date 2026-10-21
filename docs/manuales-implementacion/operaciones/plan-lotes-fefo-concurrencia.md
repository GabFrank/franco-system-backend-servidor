# Plan — dos asignaciones FEFO en paralelo no toman el mismo saldo de un lote (issue #463)

Rama: `fix/lotes-fefo-concurrencia` (desde `develop` b87c4bdf). Pieza: central.

## Qué pasa hoy

`LoteFefoService.asignar` y `asignarConPreferencia` leen el saldo por lote con
`movimientoStockLoteService.stockPorLote(productoId, sucursalId)` y devuelven el reparto; quien llama
lo escribe después en `movimiento_stock_lote`. Entre la lectura y la escritura no hay bloqueo, y
con read committed la segunda transacción no ve lo que la primera todavía no commiteó.

**Reproducido el 2026-10-20** en el central local (`develop` b87c4bdf, perfil dev, 8081), con el
producto 4680 (marcado con lote para la prueba) y 30 unidades atribuidas al lote `REPRO463` en la
sucursal 13. Las transferencias 19248 y 19251 (20 unidades cada una, origen 13) se avanzaron a
`PREPARACION_MERCADERIA`:

| Corrida | 19248 | 19251 | Saldo de `REPRO463` |
|---|---|---|---|
| En paralelo | 20 del lote | **20 del lote** | **−10** |
| Una después de la otra (control) | 20 del lote | 10 del lote | 0 |

En la corrida en paralelo, un `psql` retuvo `LOCK TABLE operaciones.movimiento_stock_lote IN SHARE
MODE` (deja leer y frena los `INSERT`). `pg_locks` mostró a los dos backends esperando en el
`insert into operaciones.movimiento_stock_lote`: los dos ya habían leído el saldo. Es determinista y
no cambia código. El primer desglose de un movimiento no borra nada antes de leer
(`deleteBy…` derivado no emite `DELETE` sin filas), así que la primera escritura llega después
de la lectura.

### Quién lee el saldo de un lote y escribe en base a él

| Camino | Lee | Escribe | Transacción |
|---|---|---|---|
| Transferencia: `createMovimientoFromTransferenciaItem` → `desglosarTransferenciaPorLote` | `asignarConPreferencia` | `reemplazarDesglose` | `@Transactional` del método; dentro de la de `avanzarEtapaTransferencia` / `saveTransferencia` / `verificarParaTransporte` cuando viene de ahí. Desde `saveTransferenciaItem`, la propia (entra por el proxy). |
| Devolución: `DevolucionLoteService.desglosarBaja` | `asignar` (sin lote elegido) | `reemplazarDesglose` | `@Transactional`; `DevolucionService` llega por `applicationContext.getBean` (proxy) |
| Ajuste por lote: `AjusteStockLoteService.ajustar` | `saldoDeLote` → `stockPorLote` | filas del ajuste | `@Transactional` |
| Inventario: `InventarioGraphQL.finalizarInventarioEnSucursal` | `saldosPorLote` | `escribirDesglose` | **ninguna**: cada `save` commitea solo (ver Fuera de alcance) |
| Filial: `VentaLoteService.registrarSalidaVenta` | `asignarConPreferencia` del filial | `reemplazarDesglose` del filial | otra base (ver Fuera de alcance) |

La transferencia re-asigna la salida **en todas las etapas**, no solo en preparación:
`createMovimientoFromTransferenciaItem` busca la salida al principio y `desglosarTransferenciaPorLote`
la vuelve a repartir siempre que esté activa y sea negativa (`MovimientoStockService:433-446`).

## Qué se hace

Un lock transaccional por **sucursal** sobre el saldo de lotes, con espera acotada. Clave:
`lote-saldo:<sucursalId>`. Se libera solo al commit o rollback.

1. `BloqueoTransaccionalService.tomar(String clave, String esperaMaxima)`: overload nuevo. Guarda
   `current_setting('lock_timeout')`, pone `set_config('lock_timeout', esperaMaxima, true)`, toma
   `pg_advisory_xact_lock(hashtextextended(clave, 0))` y restaura el valor anterior, así que las
   esperas de fila que vengan después en la misma transacción quedan como estaban. `tomar(String)`
   (tesorería) no cambia. Verificado en el Postgres local el 2026-10-20: `lock_timeout` corta la
   espera de un advisory lock con `55P03` y `set_config(…, true)` restaura bien.
2. `LoteFefoService.bloquearSaldo(Long sucursalId)`: el único lugar que arma la clave y la espera
   (`15s`). Si vence, tira una `GraphQLException` con «Hay otra operación de lotes en curso en la
   sucursal N; reintentá en unos segundos» (la transacción ya está abortada, no queda nada a medias).
   El javadoc deja escrito el invariante del que depende la ausencia de deadlocks (ver abajo).
3. `LoteFefoService.asignar(…, lotesExcluidos)` y `asignarConPreferencia` (rama con preferencias) lo
   llaman **antes** de `stockPorLote`. Cubre transferencias y devoluciones, los dos únicos
   llamadores. Tomarlo dos veces en la misma transacción no traba: el advisory lock es reentrante
   por sesión (conexión).
4. `MovimientoStockService.createMovimientoFromTransferenciaItem` lo toma **al principio**, antes de
   tocar ningún movimiento, si el producto lleva lote, con la sucursal origen. Ver «Por qué tan temprano».
5. `AjusteStockLoteService.ajustar` lo toma **inmediatamente después de `validar`**, antes de
   `resolverLote` (que puede insertar o actualizar el lote) y de `saldoDeLote`.

### Por qué por sucursal y no por (producto, sucursal)

La issue propone `(producto, sucursal)`. Con esa clave, un avance toma un lock por cada producto
con lote, en el orden de `findByTransferenciaIdOrderByIdDesc` (id de ítem descendente). Dos
transferencias con los productos A y B cargados en orden inverso los piden cruzados → deadlock.
Postgres lo detecta y aborta una con error, pero el operador ve un fallo por apretar un botón.

Todas las transacciones de la tabla de arriba asignan lotes en **una sola sucursal**: la
transferencia solo asigna en el origen (la entrada copia la salida sin leer saldo), una devolución
usa un único `sucId` (`DevolucionService:464-495`) y el ajuste es de una sucursal. Con una clave
por sucursal cada transacción toma a lo sumo una, y entre estos caminos no puede haber deadlock
de lock contra lock, sea cual sea el orden de los ítems. El costo: dos asignaciones de productos
con lote de la **misma** sucursal se hacen en fila durante lo que dure la transacción de la
primera, también las de la pseudo-sucursal COMPRAS entre sí. Los productos sin lote nunca toman el lock.

### Orden de locks real y el invariante que lo sostiene

En `avanzarEtapaTransferencia` y `saveTransferencia` el orden es: fila de la transferencia
(`lockById`, `FOR UPDATE`) → `transferenciaItemService.save(ti)` → lock de lotes. Además, la consulta
nativa del lock vuelca (auto-flush) lo pendiente de la sesión, así que las filas de transferencia e
ítem ya están tomadas **antes** del lock de lotes. No hay ciclo porque **bajo el lock de lotes no se
escriben `transferencia` ni `transferencia_item`**: `createMovimientoFromTransferenciaItem`,
`desglosarTransferenciaPorLote`, `limpiarDesglose` y `reemplazarDesglose` solo escriben
`movimiento_stock` y `movimiento_stock_lote` (las FK piden `KEY SHARE`, que no choca con un `UPDATE`
de columnas no clave). Ese es el invariante: código futuro que escriba esas tablas bajo el lock abre
un ciclo fila → lock / lock → fila. Queda en el javadoc de `bloquearSaldo`.

La reentrancia es por **sesión**: un `REQUIRES_NEW` que llegara a `asignar*` mientras la
transacción de afuera tiene el lock usaría otra conexión y se trabaría contra sí mismo hasta el
`lock_timeout`. Hoy los `REQUIRES_NEW` del camino son auditoría y notificaciones, que no asignan.
También va en el javadoc.

### Por qué tan temprano en la transferencia

`saveTransferenciaItem` no corre en una transacción propia: guarda el ítem (commitea) y después
llama a `createMovimientoFromTransferenciaItem`, que sí es transaccional. Si el lock se tomara recién
en `asignarConPreferencia`, esa transacción ya tendría bloqueada la fila del `movimiento_stock` de
salida al pedirlo, y un avance de la misma transferencia que ya tuviera el lock y llegara a ese
movimiento quedaría cruzado con ella. Tomándolo antes de tocar ningún movimiento, la transacción de
`saveTransferenciaItem` espera sin nada bloqueado.

### Por qué espera acotada

Cada request que espera el lock retiene una conexión. El central no fija `maximum-pool-size`
(`application.properties` solo tiene `connection-timeout=60000`), así que el pool es el de Hikari
por defecto: 10. Sin tope, una cola de esperas en una sucursal puede dejar sin conexiones al resto
del central. Y el ajuste, ya con el lock, pide **otra** conexión para la auditoría
(`ModificacionService`, `REQUIRES_NEW`): con el pool lleno de esperas, el dueño del lock no la
consigue. Además, el desktop corta las mutations a los 60 s (`timeout-link.ts`,
«la operación pudo haberse aplicado»), y el reintento de un avance que sí se aplicó choca con
`validarQueNoRepiteEtapa`. Con 15 s de tope la espera se corta antes que el cliente, la transacción
se revierte entera y el operador ve un mensaje claro.

## Fases

Una sola fase: el cambio es chico (≈60 líneas de código más tests) y los cinco puntos son una sola
regla; partirlo dejaría a medias la razón de la clave por sucursal.

**Fase 1 — `fix(lotes): serializar por sucursal la asignacion de saldo por lote`**

- `BloqueoTransaccionalService`: overload `tomar(clave, esperaMaxima)`.
- `LoteFefoService`: inyecta `BloqueoTransaccionalService`, agrega `bloquearSaldo` y lo llama en
  `asignar(4)` y en `asignarConPreferencia` antes de leer. Sin cambios para quien construye la
  clase: nadie hace `new LoteFefoService(` y los tests la mockean.
- `MovimientoStockService.createMovimientoFromTransferenciaItem`: `loteFefoService.bloquearSaldo(origen)`
  al principio si el producto de `presentacionPreTransferencia` lleva lote. Sin dependencia nueva,
  así que no cambia el constructor que arman a mano tres tests.
- `AjusteStockLoteService.ajustar`: inyecta `LoteFefoService` y llama a `bloquearSaldo` después de
  `validar`.
- Tests unitarios (Mockito, sin contexto Spring):
  - `LoteFefoServiceBloqueoTest`: `asignar` y `asignarConPreferencia` (con y sin preferencias) toman
    `lote-saldo:<sucursal>` **antes** de `stockPorLote` (`InOrder`); con datos inválidos no toman
    nada. `bloquearSaldo` traduce el `55P03` a la `GraphQLException` y deja pasar cualquier otro error.
  - `MovimientoStockService` (mismo armado a mano que `MovimientoStockServiceStockTest`, con
    `LoteFefoService` mockeado): `createMovimientoFromTransferenciaItem` llama a `bloquearSaldo(origen)`
    antes del primer `save`, y no lo llama si el producto no lleva lote.
  - `AjusteStockLoteServiceTest`: `ajustar` toma el lock antes de `resolverLote` y de leer el saldo.
    Los campos `@Autowired` se cablean con `@InjectMocks`.
  - Paso 7: con `bloquearSaldo` presente pero **sin las llamadas**, estos tests tienen que fallar
    (no vale el error de compilación por falta del método).

## Persistencia, clientes, filial

- Sin migración, sin columnas, sin cambios de `.graphqls` ni de enums. **No cambia qué se replica**:
  `movimiento_stock_lote` (V154.3) y `movimiento_stock` (V161.3) ya bajan del central a cada filial
  con filtro por sucursal, igual que hoy.
- Desktop y PWA: sin cambios. Lo visible: el segundo de dos avances concurrentes de la misma sucursal
  tarda lo que tarde el primero, y si eso pasa de 15 s recibe el mensaje de reintentar.
- Filial: sin cambios en este PR (ver Fuera de alcance).
- Rollback: es solo código. Volver al JAR anterior deja la base igual; un advisory transaccional
  muere con la transacción. No se agrega interruptor por configuración: el deploy es manual y el
  rollback es el JAR anterior. La espera acotada limita el daño mientras tanto.

## Riesgos

| Riesgo | Qué se hace |
|---|---|
| `tomar` es `Propagation.MANDATORY`: fuera de una transacción tira excepción | Los cinco puntos están dentro de una `@Transactional` (tabla de arriba, confirmado por las dos auditorías). Se verifica en runtime pasando por cada pantalla. |
| Un avance grande con lotes tarda más que el tope de 15 s y deja sin hacer otro de la misma sucursal | Se mide en runtime el tiempo de un avance con muchos ítems con lote (R6). Si se acerca al tope, se sube el tope, siempre por debajo de los 60 s del cliente. |
| Pool agotado por esperas | Tope de 15 s. Se prueba con más requests concurrentes que el pool (R5). |
| Deadlock contra locks de fila | Invariante de arriba, en el javadoc; R3 y R4 deterministas. |
| Colisión de hash con una clave de tesorería | Solo provoca una espera, nunca un error (lo documenta `BloqueoTransaccionalService`). |
| `BloqueoTransaccionalService` vive en `service.financiero` | Se usa tal cual. Moverlo a un paquete común es un refactor aparte. |
| Saldos por lote **ya negativos** en producción por esta carrera | El fix no los repara. Consulta de detección abajo, de solo lectura; los corrige un ajuste por lote (`CORREGIR` o `ATRIBUIR`). |

### Detección de saldos negativos (solo lectura)

Agrupa igual que `stockPorLote`. Sirve para saber si la carrera ya pasó en bodega o farmacia, y
después del deploy como control. Las filas negativas también pueden venir de lo que queda fuera de
alcance (inventario, ventas del filial). Sentencia corta a propósito: psql hacia bodega se cuelga
con sentencias de más de ~1200 bytes.

```sql
select producto_id, sucursal_id, lote_id, numero_lote, sum(cantidad) saldo
from operaciones.movimiento_stock_lote
where estado and lote_id is not null
group by 1, 2, 3, 4 having sum(cantidad) < -0.0001
order by 5;
```

### Para ver quién tiene el lock en vivo

`select l.pid, l.granted, a.query from pg_locks l join pg_stat_activity a using (pid) where l.locktype = 'advisory';`
(`classid`/`objid` son las dos mitades de `hashtextextended('lote-saldo:N', 0)`).

## Prueba de runtime (central local, perfil dev, 8081)

Scripts: `repro463.py` (`concurrente` / `secuencial`) y `restaurar.sql`. Después de cada corrida se
restaura y se compara con el snapshot CSV. El resultado de cada punto se anota en este plan.

1. **R1 — la carrera, con el fix**: `repro463.py concurrente`. Antes de soltar el `LOCK SHARE` el
   script **exige** ver en `pg_locks` las dos esperas: una en el `INSERT` (lock de tabla) y la otra en
   el advisory (`locktype = advisory`). Si no las ve, la corrida no cuenta. Esperado: 20 + 10, saldo 0.
2. **R2 — control secuencial**: sin cambios respecto de la corrida de antes del fix.
3. **R3 — productos cruzados**: dos transferencias del mismo origen con dos productos con lote
   cargados en orden inverso. Desde psql se retiene `pg_advisory_lock(hashtextextended('lote-saldo:13', 0))`,
   se lanzan los dos avances, se ve en `pg_locks` que esperan el advisory y se suelta. Esperado: las
   dos terminan, sin `deadlock detected`. Pasaría también sin lock: es la regresión del diseño
   (con una clave por producto esta prueba da deadlock), no la del bug.
4. **R4 — avance + `saveTransferenciaItem` en paralelo** sobre la misma transferencia, con el
   advisory retenido desde psql. Esperado por caso:
   - ítem existente: el `save` del ítem espera la **fila del ítem** que tiene el avance, no el advisory;
   - ítem nuevo: espera la fila de la transferencia (`FOR UPDATE` contra el `FOR KEY SHARE` de la FK).
   En los dos, al soltar terminan los dos y no hay deadlock.
5. **R5 — tope de espera**: con el advisory retenido desde psql más de 15 s, 12 avances en paralelo
   (más que el pool) a transferencias de la misma sucursal. Esperado: a los ~15 s cada uno devuelve el
   mensaje de reintentar, ninguno queda colgado, las transferencias siguen en su etapa anterior y el
   central sigue respondiendo a otra query mientras tanto.
6. **R6 — duración real**: tiempo de un avance a `PREPARACION_MERCADERIA` de la transferencia con más
   ítems de la base local, con sus productos marcados con lote.
7. **Por pantalla (desktop `ng serve -c web` contra el 8081)**, una vez cada uno, en el camino normal:
   - avanzar una transferencia con un producto con lote hasta `PREPARACION_MERCADERIA` y ver el
     desglose;
   - ajuste de stock por lote;
   - devolución de un producto con lote sin lote elegido (sale por FEFO).
8. `./mvnw clean verify -B -DskipFlyway=true` en una copia fuera del workspace (jdtls), y después
   `gh pr checks`.

## Resultado de la prueba de runtime (2026-10-20)

Central local con la rama (perfil dev, 8081), corrido desde una copia fuera del workspace. Antes de
cada corrida se restauró la base y se comparó con un snapshot CSV de las 17 transferencias, sus ítems,
productos y movimientos previos: todas las comparaciones dieron igual.

| Prueba | Resultado |
|---|---|
| R1 — carrera con el fix | **Pasa.** En `pg_locks`, una espera en el `INSERT` y la otra en el advisory (la corrida cuenta). 19248/19251 → 20 + 10, saldo de `REPRO463` **0** (antes del fix: 20 + 20 y −10). |
| R2 — control secuencial | Igual que antes del fix: 20 + 10, saldo 0. |
| R3 — 51221 y 51251, productos 7078/6964 en orden inverso | **Pasa.** Los dos esperan el advisory, avanzan, 0 deadlocks. |
| R4a — avance + `saveTransferenciaItem` de un ítem existente | Sin deadlock. El save del ítem **falla con `40001`** (`could not serialize access`): `TransferenciaItemService.save` es `SERIALIZABLE` y, sin transacción de afuera, corre en la suya; espera la fila que el avance ya modificó. **Es previo al fix**: el control sin lote (sin advisory, avance frenado con `LOCK SHARE` sobre `movimiento_stock`) da el mismo error. El fix solo puede alargar esa ventana cuando el avance espera el lock. |
| R4b — avance + ítem nuevo | **Pasa.** El insert del ítem no esperó la fila de la transferencia (commiteó solo); su movimiento esperó el advisory junto al avance. Los dos terminan, sin deadlock. |
| R5 — 11 avances con el advisory retenido 40 s | **Pasa.** 10 esperan el lock (todo el pool) y se cortan a los **15 s** con «Hay otra operación con lotes en curso en la sucursal 13. Reintentá en unos segundos.»; el 11.º espera conexión y lock (**30 s**, debajo de los 60 del desktop). Ninguna transferencia cambió de etapa. Otra query durante la cola tardó **10 s** (esperó conexión) y respondió. |
| R6 — avance de 51163 (118 ítems, todos con lote) | `develop` sin fix: **4,8 s**. Primer commit: **10,3 s**. Con el segundo commit: **4,8 s**. Sin lote: 1,8 s. |

**R7 — por pantalla (2026-10-21)**, desktop `develop` 00f37c4a compilado `web-production` y servido en
`127.0.0.1:4201` contra el central de la rama (los `/graphql` salían a `localhost:8081`), usuario MR,
producto 4680 marcado con lote y lote `REPRO463` creado con 30 por API (el diálogo de ajuste solo
corrige lotes existentes):

| Pantalla | Qué se hizo | Resultado |
|---|---|---|
| Productos → Ajustar stock (diálogo «Ajustar Stock por lote») | Sucursal 13, lote `REPRO463`, cantidad real 30 → 28 | «Guardado con éxito»; saldo del lote **28** |
| Transferencias → 19248 → «Preparar para envío» | Avance a `PREPARACION_MERCADERIA` (20 unidades) | Etapa avanzada; FEFO tomó 20 de `REPRO463`: saldo **8** |
| Devoluciones → nueva (sin proveedor, origen 13), 4680 × 5 sin lote → «Marcar como separado» | Avance a `SEPARADO` | `DESCARTE` −5 desglosado por FEFO en `REPRO463`: saldo **3** |

Al terminar se borraron la devolución 8, su ítem y su baja, se restauró la base (comparación igual), se
dejó el `localStorage` de ese origen como estaba y se bajaron el central y el servidor del desktop.

**Lo que cambió por R6.** El primer commit pedía el lock dos veces por ítem, y cada pedido son cuatro
consultas nativas que vuelcan la sesión entera: unas 950 por avance. El segundo commit
(`fix(lotes): pedir el lock de saldo una sola vez por transaccion`) recuerda las claves tomadas en una
`TransactionSynchronization` de la propia transacción: Spring la descarta al terminar y un
`REQUIRES_NEW` arranca con la suya vacía. R1, R3, R4b y R5 se repitieron con ese commit con el mismo
resultado.

**Tamaño real.** En los últimos 90 días de la base local, p50 = 5 ítems, p95 ≈ 144, máximo 170. Un
avance de ~170 ítems con lote tarda unos 7 s: entra en el tope de 15 s de quien espera.

**Batería**: `./mvnw clean verify -B -DskipFlyway=true` en copia aislada: 2183 tests (primer commit) y
2187 (segundo), 0 fallas. Tests nuevos revertidos (paso 7): sin las llamadas al lock fallan 6 de 10;
sin el corte por clave tomada falla el de `BloqueoTransaccionalServiceTest`.

## Auditoría del plan (paso 5)

Dos auditores en paralelo, sin verse (`critic`, sonnet). Cada hallazgo se verificó contra el código
antes de aplicarlo.

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A y B | Espera sin tope: pool de 10 y corte del desktop a los 60 s; el ajuste pide una segunda conexión con el lock tomado | **Aplicado**: verificado (`application.properties:21`, `timeout-link.ts:5`, `ModificacionService:52/67`); overload con `lock_timeout` de 15 s y mensaje claro; R5 y R6 |
| A y B | En `ajustar`, `resolverLote` puede insertar o actualizar el lote antes del lock | **Aplicado**: el lock va justo después de `validar` |
| A | «Nada viaja por replicación» es falso: `movimiento_stock_lote` y `movimiento_stock` bajan a las filiales | **Aplicado**: corregida la frase (no cambia *qué* se replica); la carrera central/filial queda como issue aparte |
| A | El lock temprano se toma de más en etapas que no leen saldo | **Descartado**: falso. La salida se re-asigna en todas las etapas (`MovimientoStockService:433-446`) |
| A | Cambio de origen: la salida vieja queda en la sucursal anterior | Sin cambio: el lock usa el origen actual y la búsqueda de la salida también; hoy coinciden, igual que la asignación |
| B | El orden real es fila → lock (auto-flush), no «lock antes de cualquier fila»; la ausencia de ciclo depende de un invariante | **Aplicado**: sección «Orden de locks real», invariante y reentrancia por sesión en el javadoc |
| B | Los tests no fallarían con el código viejo, solo no compilarían; falta el test del lock temprano; el del ajuste necesita cableado | **Aplicado**: revertir solo las llamadas; test de `createMovimientoFromTransferenciaItem`; `@InjectMocks` |
| B | R1 puede pasar sin probar nada; R3 pasa con el código viejo; R4 depende del timing y no dice dónde espera | **Aplicado**: R1 exige las dos esperas en `pg_locks`; R3/R4 deterministas con el advisory retenido desde psql y esperado por caso |
| B | No hay forma de detectar saldos ya negativos | **Aplicado**: consulta de detección de solo lectura |
| B | Interruptor por configuración para apagar el lock en vivo | **Descartado**: deploy manual y rollback por JAR; la espera acotada limita el daño. Se agrega cómo ver quién tiene el lock |

## Fuera de alcance (issues aparte)

- **Inventario** (#465): `finalizarInventarioEnSucursal` no corre en una transacción, así que un lock
  transaccional no lo cubre. Además, lee el saldo y escribe la diferencia igual que con el stock
  agregado de productos **sin** lote, que tiene la misma carrera hoy. Arreglarlo es darle
  transacción a la finalización entera, que es otro cambio.
- **Filial, dentro de su base** (filial #185): `VentaLoteService` asigna por FEFO en la base del filial. Dos cajas
  vendiendo el mismo producto con lote a la vez tienen la misma carrera, y es el mismo arreglo en
  ese repo.
- **Central contra filial**: el ledger de la sucursal N vive en las dos bases (baja por réplica, V161.3),
  y el filial lo lee para vender. Una transferencia del central desde N y una venta en el filial N
  consumen el mismo saldo sin lock compartido: no se arregla con un lock, porque son dos bases.
  Seguimiento en #466, con la consulta de detección.

## Sin verificar todavía

- La duración de un avance de ~170 ítems con lote (unos 7 s) es una estimación por proporción, no
  una medición.
- El tamaño del pool en producción (no está en el repo ni en `deploy.sh`; podría venir del `.env`
  del servidor).
- Si ya hay saldos negativos en bodega o farmacia: la consulta de detección es de solo lectura y se
  corre con el OK de Franco.
