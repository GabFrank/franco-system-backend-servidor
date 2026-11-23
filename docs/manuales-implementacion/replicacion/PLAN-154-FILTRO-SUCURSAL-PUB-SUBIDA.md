# Plan #154 — filtro por sucursal en la publicación de subida de cada filial

Issue: central #154. Rama: `fix/replicacion-filtro-sucursal-subida` (sale de `develop` 60ff726f).
Pieza: **solo central**. Sin migración, sin GraphQL, sin variable de entorno.

## Problema

`<db>_filial<N>_pub` (en la base de la filial, la que sube a central) se crea sin `WHERE`:

- `createRemotePublication` arma `CREATE PUBLICATION … FOR TABLE t1, t2, …`.
- `agregarTablaAPublicacionFilial` (la sincronización de cada hora) hace `ADD TABLE t` sin filtro.

Una fila con el `sucursal_id` de otra sucursal sube igual y choca con la PK `(id, sucursal_id)`
de la fila legítima en central: `duplicate key` y la suscripción `filial<N>_sub` se corta. La
publicación de bajada (`central_<db>_filial<N>_pub`) ya lleva `WHERE (sucursal_id = N)`.

## Qué NO se hace: sacar `delete` de la publicación

La issue lo propone como riesgo 2. **No se toca**: el filial borra filas de tablas que suben en
flujos normales y central tiene que enterarse.

| Borrado en el filial | Tabla |
|---|---|
| `VentaItemService.deleteById` (quitar ítem) | `operaciones.venta_item` |
| `CobroDetalleService.deleteById` | `operaciones.cobro_detalle` |
| `PdvCajaService.deleteById` (caja huérfana si falla el conteo, `ConteoGraphQL:272`) | `financiero.pdv_caja` |
| `ConteoGraphQL.deleteConteo`, `VueltoGraphQL`, `VueltoItemGraphQL`, `VentaGraphQL` | `conteo`, `vuelto`, `vuelto_item`, `venta` |

Sin `delete`, central se quedaría con ítems y cajas que ya no existen y los reportes darían mal.
El riesgo de «un DELETE a mano en la filial borra central» se mantiene; ya está documentado en
`gotchas.md` («Preferir SKIP a borrar filas»). El filtro lo acota: un borrado de una fila ajena
ya no sube.

## Diseño

Un helper nuevo en `LogicalReplicationService`:

```java
/** " WHERE (sucursal_id = N)" si la filial lo soporta para esa tabla; "" si no. */
String filtroSucursal(JdbcTemplate filial, String tabla, long sucursalId)
```

Devuelve el filtro solo si se cumplen las cuatro condiciones, **en este orden** (cada una que
falle corta y deja una línea de log con el motivo, para listar con un grep qué quedó sin filtro):

1. PostgreSQL ≥ 15 (`server_version_num >= 150000`): antes no existe el filtro por fila ni la
   columna `pg_publication_tables.rowfilter`.
2. La tabla tiene columna `sucursal_id` (`movimiento_personas` no tiene: queda sin filtro).
3. La identidad de réplica **cubre** `sucursal_id`: `FULL`, o `DEFAULT` con PK que contenga
   `sucursal_id`, o `USING INDEX` (`indisreplident`) con `sucursal_id`. `NOTHING` o `DEFAULT` sin
   PK no cubren. **Si no la cubre, PostgreSQL rechaza todo UPDATE/DELETE sobre la tabla en la
   filial** («column used in the publication WHERE expression is not part of the replica
   identity», se chequea al ejecutar el UPDATE, no al hacer el ALTER): cortaría las ventas. No se
   sube la identidad a FULL para poder filtrar: sería un `ALTER TABLE` (lock exclusivo) sobre
   tablas calientes en horario de venta. La tabla queda sin filtro y logueada.
4. **No hay filas ajenas** (`sucursal_id IS DISTINCT FROM N`, incluye NULL): ver «Filas ajenas».

Ante cualquier error de la consulta, o un `null`, devuelve `""` (queda como hoy).

### Filas ajenas: el filtro no puede tragarse datos que hoy suben bien

Hoy una fila con otra sucursal que **no** choca de PK sube y central la recibe. Con el filtro esa
fila se queda en la filial, sin error y sin aviso. Hay escrituras con `sucursal_id` que no sale del
`env` del filial: `factura_legal` toma la sucursal del **timbrado** (`FacturaLegalApiService:243`,
`FacturaLegalBuilder:180`), y `VentaCreditoGraphQL:145`, `PdvCajaGraphQL:130,165` y
`MarcacionGraphQL:119` aceptan la del input del cliente. Por eso:

- **Antes de aplicar** el filtro a una tabla que ya está en la pub (puerta 3), una consulta
  `SELECT EXISTS (… WHERE sucursal_id IS DISTINCT FROM N)`. Si hay alguna, **no se filtra** y se
  loguea en WARN con la tabla: alguien tiene que mirar de dónde salió. Corre una vez por tabla (ya
  filtrada no se vuelve a evaluar), no cada hora.
- **Gate antes del Deploy** (abajo): conteo por tabla en todas las filiales de cada instancia.

Tres puertas, las tres pasan por el helper:

| Puerta | Qué cambia |
|---|---|
| `createRemotePublication` (setupFull, setup por pasos, mutation `createRemotePublication` / `editRemotePublication`) | cada tabla del `CREATE PUBLICATION` lleva su filtro |
| `agregarTablaAPublicacionFilial` (sync, tabla nueva) | `ADD TABLE t WHERE (…)`; el `ensureReplicaIdentityFull` que ya corre antes deja la condición 3 cumplida |
| **nuevo** `aplicarFiltroSucursal` (sync, tabla que ya está) | si el filtro aplica y la tabla está **sin** `rowfilter`: `DROP TABLE t` + `ADD TABLE t WHERE (…)` en **una transacción** (ConnectionCallback, autocommit off, `SET LOCAL lock_timeout = '5s'`). Si ya tiene filtro, no toca la pub |

Además, en cada corrida, para las tablas que **ya tienen** filtro: si la identidad de réplica dejó
de cubrir `sucursal_id` (una migración futura del filial la bajó a `DEFAULT` o cambió la PK; V111.3
ya hizo `DEFAULT` una vez), `ensureReplicaIdentityFull` y WARN. Ahí los UPDATE ya están fallando,
así que el `ALTER TABLE` es el arreglo, no el riesgo.

El tercero es el que arregla las publicaciones que **ya existen** en las filiales: el sync corre
2 minutos después de arrancar el central y después cada hora (`ReplicationPublicationSyncScheduler`,
`replication.sync.enabled`). También corrige solas las tablas que agregue a futuro una migración
del filial sin filtro.

Por qué DROP+ADD y no `SET TABLE`: `SET TABLE` reemplaza la lista entera y obliga a reconstruir
las otras tablas con sus filtros. DROP+ADD en la misma transacción toca una tabla sola; el
walsender decodifica con la definición vigente al commit, y la suscripción de central no se entera
(sigue con la tabla en `pg_subscription_rel`, sin refresh ni copia). ADD/DROP toman
`ShareUpdateExclusiveLock`, que no choca con INSERT/UPDATE/DELETE; el `lock_timeout` cubre un
VACUUM o un `ALTER PUBLICATION` concurrente.

No se toca:
- `createBranchPublication` (`:204`): crea una pub en la base **del central**, no en la filial.
- Una suscripción ya trabada por un `duplicate key`: lo encolado se decodifica con la definición
  vigente al commit, así que el filtro no la destraba. Sigue haciendo falta el `SKIP (lsn)`.
- El `success=true` del sync aunque una filial falle: es preexistente; las líneas nuevas van en
  WARN para que se vean en el log.

## Tabla de datos nuevos

No nace ningún dato persistido (ni columna, ni clave, ni configuración). El único estado nuevo es
el `rowfilter` de cada tabla en `<db>_filial<N>_pub`: lo **escribe** `LogicalReplicationService`
(las tres puertas) y lo **lee** el walsender de la filial al decodificar, y el propio
`aplicarFiltroSucursal` para no repetir el cambio.

## Fases

**Fase 1 — código + tests** (`fix(replicacion): filtrar por sucursal la publicacion de subida de cada filial`)
- `filtroSucursal`, `aplicarFiltroSucursal`, la revalidación de identidad de las ya filtradas, y su
  uso en las tres puertas y en el paso 3 del sync.
- Tests unitarios (`LogicalReplicationServiceFiltroSucursalTest`, JdbcTemplate mockeado, estilo de
  `LogicalReplicationServiceAgregarTablaFilialTest`):
  1. `filtroSucursal`: `" WHERE (sucursal_id = 7)"` si la consulta da true; `""` si da false,
     `null` o tira excepción.
  2. `agregarTablaAPublicacionFilial`: con `WHERE` cuando aplica, sin él cuando no.
  3. `aplicarFiltroSucursal` con `Connection`/`Statement` mockeados (`doAnswer` sobre
     `execute(ConnectionCallback)`): autocommit en false antes del primer statement, orden
     `lock_timeout` → DROP → ADD → commit; ADD que falla → rollback, sin commit, logueado y sigue
     con la próxima tabla; tabla que ya tiene filtro, tabla con filas ajenas, filtro no aplicable
     y PG < 15 → no abre transacción.
  4. Tabla ya filtrada cuya identidad dejó de cubrir → `ALTER TABLE … REPLICA IDENTITY FULL`.
  - Con el código viejo, 2 (con `WHERE`), 3 y 4 fallan (revertir y comprobar, paso 7).
- Los tres tests de `LogicalReplicationServiceAgregarTablaFilialTest` siguen verdes (el `null` del
  mock tiene que dar «sin filtro»).

**Fase 2 — documentación** (`docs(replicacion): …`): nota en `docs/manuales-implementacion/replicacion/`
con lo que sobrevive del plan (por qué no se saca `delete`, las cuatro condiciones, que una fila
ajena ya no sube y cómo verlo en el log, el SQL de rollback). El plan se borra en el último commit
del PR. El gotcha «una fila ajena ya no sube» va a `frc-cicd` por PR aparte de Gabriel (el clon de
esta máquina es solo lectura): se lo paso a Franco como texto.

## Pruebas (paso 9), en local, sin tocar filiales reales

Entorno: central local con perfil `dev` (schedulers apagados) contra una **copia zz** de `bodega`
(receta de la memoria «Probar replicación con copia zz») donde **todas** las sucursales quedan
inactivas salvo la 24, con IP `127.0.0.1`, puerto 5551, base `general` (el filial local). La copia
es obligatoria: la `bodega` local tiene las IP reales de las 18 filiales.

1. **Estado de producción simulado**: en `general`, crear `<db>_filial24_pub` sin filtro con las
   tablas BRANCH_TO_MAIN y una suscripción de prueba en la copia zz (slot aparte,
   `create_slot=false`, `copy_data=false`; memoria «Réplica de prueba en el mismo cluster»).
2. Correr `syncPublicationsWithReplicationTable` desde la pantalla de replicación del desktop
   (o la mutation si la pantalla no la tiene): `rowfilter = (sucursal_id = 24)` en las tablas que
   cumplen; sin filtro y con motivo en el log `movimiento_personas` y las que tengan filas ajenas.
   La suscripción sigue `streaming`, sin errores en `pg_stat_subscription_stats`.
3. **Chequeo de identidad de toda la pub**: por cada tabla filtrada, en una transacción con
   rollback, `UPDATE t SET sucursal_id = sucursal_id WHERE false` y `DELETE FROM t WHERE false`:
   PostgreSQL valida la identidad al preparar la sentencia, así que si alguna no cubre, falla acá
   y no en una venta. Se deja escrito como chequeo posterior al Deploy.
4. Segunda corrida: no cambia nada (idempotente).
5. Venta completa en el PDV contra el filial local (sucursal 24): `venta`, `venta_item`, `cobro`,
   `cobro_detalle`, `movimiento_stock`; quitar un ítem (DELETE). Todo llega a la copia zz.
6. Fila con `sucursal_id = 99` insertada **después** del filtro: no llega y la suscripción no se
   corta. Fila ajena **antes** del filtro (otra tabla): esa tabla queda sin filtro y con WARN.
7. Matriz de identidad sobre tablas de prueba en `general`: FULL; DEFAULT con PK `(id,
   sucursal_id)`; DEFAULT con PK `(id)`; DEFAULT sin PK; NOTHING; USING INDEX que cubre y que no;
   sin columna. Solo las que cubren quedan filtradas; en todas, un UPDATE anda.
8. Bajar a DEFAULT (PK solo `id`) una tabla ya filtrada → la corrida siguiente la sube a FULL.
9. Publicación nueva: borrarla y crearla con la mutation `createRemotePublication` → nace con los
   filtros.
10. Limpieza: suscripción, slot, pub y tablas de prueba, copia zz; `general` restaurada.

## Antes del Deploy (paso 10) — gate

No hay migración, así que no hay dry-run de Flyway. Gate **solo lectura** sobre las cuatro
instancias que tienen el sync prendido (alpha, beta, farmacia, bodega) y todas sus filiales:

- **Filas ajenas** por tabla que sube: `count(*) where sucursal_id IS DISTINCT FROM N`. Si aparece
  alguna, entender de dónde sale **antes** del Deploy (el código igual deja esa tabla sin filtro).
  Primero, barato y desde el central: qué `sucursal_id` llegan que no son de una filial con
  suscripción (0, 500, NULL).
- **Versión de PostgreSQL** e **identidad de réplica** de las tablas de la pub de subida: cuáles
  van a quedar sin filtro.

## Rollback

Revertir el JAR. Los filtros ya aplicados **quedan**: el sync viejo los respeta (solo agrega
tablas que faltan), pero `editRemotePublication` del JAR viejo recrea la pub **sin** filtros. Las
filas ajenas que no subieron mientras estuvo el filtro no se recuperan revirtiendo. Para sacar los
filtros a mano en una filial, generar el SQL desde la propia filial:

```sql
SELECT format('BEGIN; ALTER PUBLICATION %I DROP TABLE %I.%I; ALTER PUBLICATION %I ADD TABLE %I.%I; COMMIT;',
              pubname, schemaname, tablename, pubname, schemaname, tablename)
FROM pg_publication_tables WHERE pubname LIKE '%\_filial%\_pub' AND rowfilter IS NOT NULL;
```

## Despliegue

Solo central; Deploy manual por instancia: alpha → beta → farmacia → bodega, mirando entre una y
otra el log del primer sync (WARN y motivos) y el chequeo de identidad del paso 3. El primer sync
corre **2 minutos** después de arrancar, no a la hora: desplegar fuera del horario de venta. No hay
que actualizar ningún filial ni cliente.

## Qué queda sin verificar

- El comportamiento con una filial real en Windows (bodega 4): lo cubre la condición de versión,
  pero no se prueba en local.
- Que el sync de producción tenga permisos para `ALTER PUBLICATION` en cada filial: hoy ya agrega
  tablas con el mismo usuario, así que debería; se confirma mirando el log de la primera corrida.

## Auditoría del plan (paso 5)

Dos auditores, eje A (propagación) y eje B (reversibilidad), sin verse. Aceptado:

| Hallazgo | Qué se hizo |
|---|---|
| A-H1 / B-H5: el filtro se traga en silencio filas ajenas que hoy suben bien (`factura_legal` con la sucursal del timbrado, inputs con `sucursalId` del cliente) | condición 4 (filas ajenas → sin filtro y WARN) y gate de conteo con `IS DISTINCT FROM` |
| A-H2: orden de chequeos y log del motivo; `rowfilter` no existe en PG < 15 | orden versión → columna → identidad → filas ajenas → `rowfilter`, una línea por tabla omitida |
| A-H3: falta beta | agregada al gate y al orden de Deploy |
| A-H4: las tablas ya publicadas con identidad que no cubre no se filtran nunca | decidido: quedan sin filtro y logueadas; no se hace `ALTER TABLE` para poder filtrar |
| A-H6: el filtro no destraba una suscripción ya trabada | anotado en «No se toca» |
| A-H7 / B-5: `null` del mock, test del ConnectionCallback, PG < 15 | tests 1 y 3 |
| B-H1: sin `lock_timeout` | `SET LOCAL lock_timeout = '5s'` en la transacción |
| B-H2: el sync corre 2 min después del arranque | Deploy fuera del horario de venta |
| B-H3 / A-H5: nada revalida la identidad de las tablas ya filtradas | revalidación en cada corrida → FULL + WARN; test 4; prueba 8 |
| B-2: matriz de identidad y chequeo autoritativo | pruebas 3 y 7 |
| B-H4: rollback incompleto | SQL generador, aviso sobre `editRemotePublication` viejo y filas no recuperables |

No aceptado:
- B-H4, interruptor `replication.sync.filtro-sucursal.enabled`: el Deploy por instancia con
  observación entre una y otra y la condición 4 cubren el piloto; un interruptor en
  `application.properties` igual pide redeploy para cambiarlo. Si Franco lo quiere, es chico.
- B-H1, `socketTimeout` en el JDBC remoto: es preexistente y afecta a todo el sync; va aparte.
- B-4, que el sync devuelva falla si una tabla falla: preexistente; las líneas nuevas van en WARN.

## Auditoría del diff (paso 8)

Fijo 1, 2 y 3: N/A para central porque el diff no toca resolvers, `.graphqls`, enums ni migraciones
(`git diff --name-only origin/develop...HEAD`: solo `LogicalReplicationService.java`, su test y docs).
Condicional B (replicación), un auditor. Aceptado y arreglado en `fix(replicacion): acotar locks…`:

| Hallazgo | Arreglo |
|---|---|
| Si fallaba el paso a FULL de una tabla filtrada, quedaba filtrada sin cobertura | se vuelve a evaluar y se le saca el filtro |
| `ALTER TABLE … REPLICA IDENTITY FULL` sin `lock_timeout` sobre tablas calientes | misma transacción con `lock_timeout` |
| `DriverManagerDataSource`: una conexión por consulta, varias por tabla y filial cada hora | `SingleConnectionDataSource` por filial en el sync (medido: 5 sesiones por corrida en lugar de 2+ por tabla) |
| `EXISTS` de filas ajenas sin tope | `setQueryTimeout(30)`; si vence, sin filtro y reintento |
| `catch (SQLException)` solo | también `RuntimeException` |

No aceptado: misma tabla filtrable o no según cuándo entró a la pub, y filas ajenas posteriores
sin rastro (decisiones ya tomadas en el plan, documentadas en la nota de dominio).

## Resultado de las pruebas (paso 9)

Todo en local, central de la rama contra la copia `zz154` (solo la 24 activa) y `general` como
filial, con suscripción real `zz154_sub`:

1. Línea base sin filtro: una fila con `sucursal_id = 99` subía. ✔
2. Sync desde la pantalla de replicación: 30 de 33 tablas con `(sucursal_id = 24)`; sin filtro y
   con motivo `movimiento_personas` (sin columna), `replication_test` (tenía la fila 99) y
   `sorteo_participacion` (en el filial su PK es solo `id`). La tabla nueva `pdv_caja_reapertura`
   entró con filtro. Suscripción sin errores. ✔
3. `UPDATE … WHERE false` / `DELETE … WHERE false` en las 31 filtradas: todas OK. Control: con un
   filtro forzado sobre `sorteo_participacion` PostgreSQL rechaza el UPDATE. ✔
4. Segunda corrida: no cambia nada. ✔
5. Venta en el PDV (filial 8082, sucursal 24): `venta`, `venta_item`, `cobro`, `cobro_detalle`,
   `movimiento_stock` llegaron a `zz154`. ✔
6. Tras filtrar `replication_test` en vivo (DROP+ADD con la suscripción activa): insert, update y
   delete de la 24 suben; insert y update de la 99 no. ✔
7. Matriz de identidad del SQL real: FULL, DEFAULT con PK `(id, sucursal_id)` e índice que cubre →
   OK; DEFAULT con PK `(id)`, DEFAULT sin PK, NOTHING, índice que no cubre → no cubre; sin columna y
   columna borrada → sin columna. ✔
8. Tabla filtrada bajada a DEFAULT → el sync la sube a FULL. Con una transacción abierta leyendo la
   tabla, el ALTER se rinde a los 5 s y el sync le saca el filtro; los UPDATE siguen andando. ✔
9. `createRemotePublication` (la mutation del diálogo) con las tablas BRANCH_TO_MAIN: nace con 31
   filtros. ✔
10. SQL de rollback de la nota: sacó los 30 filtros y dejó la pub de `general` como estaba. ✔

No probado en local: PostgreSQL < 15 (solo test unitario) y una filial Windows.
