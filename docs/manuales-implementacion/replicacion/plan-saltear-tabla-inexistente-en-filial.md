# Plan — saltear tablas que no existen en la filial al sincronizar publicaciones

Issue: `GabFrank/franco-system-backend-filial#81` (el arreglo va en el central, ver comentario del 03/11).
Rama: `fix/replicacion-saltear-tabla-inexistente` (central, desde `develop`).

## Problema

`LogicalReplicationService.syncPublicationsWithReplicationTable`, paso 3, recorre cada filial activa y
hace `ALTER PUBLICATION <central>_filialN_pub ADD TABLE <tabla>` por cada tabla `BRANCH_TO_MAIN` de
`configuraciones.replication_table` que no esté en la publicación. Si la tabla **no existe en la base de
la filial** (migración del filial todavía no desplegada en esa sucursal, o tabla que nunca se creó), el
`ALTER` falla en cada corrida del `ReplicationPublicationSyncScheduler` (cada hora en prod) y deja un
`ERROR` en el log de PostgreSQL de la filial, más un «error agregando» en el log del central.

El caso original (`financiero.venta_tarjeta`) ya no falla: la creó `V78.1` del filial. Queda el defecto
general: cualquier tabla nueva `BRANCH_TO_MAIN` vuelve a producir el mismo ruido en las filiales que van
atrasadas de versión.

## Cambio (una fase)

En `LogicalReplicationService.java`:

1. Sacar a un método `agregarTablaAPublicacionFilial(JdbcTemplate remoto, Long sid, String pub,
   String tabla, StringBuilder log)` (package-private) el bloque `try/catch` que hoy está dentro del
   `for (String table : toAdd)` del paso 3. Mismo comportamiento y mismos mensajes.
2. Primera línea dentro del `try`: `SELECT to_regclass(?) IS NOT NULL` contra la filial. Si da falso:
   `logger.warn("Replicacion: tabla no existe en la filial, omitida: sucursal={} pub={} tabla={}")`
   (renglón fijo, grep-eable) + línea `"Filial N: <tabla> no existe en la filial, omitida."` en el log
   devuelto, y
   no se llama a `ensureReplicaIdentityFull` ni al `ALTER`. `to_regclass` devuelve NULL sin error para
   una relación inexistente, así que no deja nada en el log de PostgreSQL. Si la consulta falla
   (conexión), cae en el `catch` existente como hoy.
3. Cuando la filial se actualiza y la tabla aparece, la corrida siguiente la agrega sola (no se guarda
   ningún estado del «omitida»).

Fuera de alcance (no cambian): pasos 1 y 2 (publicaciones del propio central: sus tablas las crea el
mismo Flyway que las registra en `replication_table`) y el paso 4 (refresh).

Sin migración, sin GraphQL, sin enum, sin variable de entorno, sin cambio en filial/desktop/PWA.

## Tabla de datos nuevos

Ninguno. No nace columna, campo ni clave.

## Tests (paso 9)

`LogicalReplicationServiceAgregarTablaFilialTest` (Mockito, sin Spring, como
`AlineacionIdsFilialServiceTest`):

- tabla inexistente (`to_regclass` → false): no se ejecuta ningún `ALTER`, el log dice «omitida»;
- tabla existente: se ejecuta `ALTER PUBLICATION <pub> ADD TABLE <tabla>` y el log dice «agregada»;
- `ALTER` que falla con «already in»: log «ya estaba (OK)» (comportamiento previo preservado).

El primero se corre también con el fix revertido (solo el chequeo `to_regclass` dentro del método
extraído: el método no existe en el código viejo) y tiene que fallar. Detalles: stubear la sobrecarga
exacta `queryForObject(String, Class, Object...)` del `to_regclass`; el `never()` va sobre
`startsWith("ALTER PUBLICATION")`, porque `ensureReplicaIdentityFull` también hace `execute`.

Batería: `./mvnw clean verify -B -DskipFlyway=true`; veredicto final `gh pr checks`.

## Prueba de runtime (sin tocar filiales reales)

El método se conecta a toda sucursal activa con IP de `empresarial.sucursal`, y la base local tiene las
IP reales. Por eso:

- copia local de la base del central (`zz_central81`, `pg_dump --no-subscriptions`), con **todas** las
  sucursales en `activo=false` salvo una apuntando a `127.0.0.1:5551`, y `configuraciones.local`
  apuntando la base filial a `zz_filial81`;
- `zz_filial81`: base local nueva con un par de tablas y la publicación `<central>_filialN_pub`, sin
  suscripciones;
- en la copia del central, una fila `BRANCH_TO_MAIN` de una tabla que no existe en `zz_filial81`;
- `configuraciones.local` en la `bodega` local está **vacía** (verificado 19/11): sin una fila,
  `getBranchDbName()` cae a `general` real. En la copia se inserta/ajusta la fila con
  `nombre_base_datos_filial='zz_filial81'` y `nombre_base_datos_master` igual al prefijo de la
  publicación de `zz_filial81`; se verifica por SQL **antes** de levantar;
- `application-user-dev.properties` fija `bodega@5551` y el puerto 8081 y pisa los argumentos: el
  worktree lleva su propia copia con la URL a `zz_central81` y un puerto libre (mirar con
  `readlink /proc/<pid>/cwd` que el puerto no sea de otra sesión);
- `pg_dump --no-subscriptions` sí trae las publicaciones de la copia: quedan inertes (sin slots);
- central levantado contra `zz_central81` con `replication.sync.enabled=false` y
  `replication.refresh.enabled=false`; la sincronización se dispara por la mutation
  `syncPublicationsWithReplicationTable` (es la misma vía que usa el botón del desktop);
- verificar: respuesta con «omitida», cero `ERROR ... does not exist` nuevos en el log de PostgreSQL,
  y que una tabla existente sí se agrega. Después crear la tabla faltante y volver a correr: se agrega.
- al final: borrar `zz_central81` y `zz_filial81`, y confirmar que no quedan slots ni suscripciones `zz_%`.

## Despliegue

Solo central; se despliega con el workflow Deploy por instancia
(`systemctl restart frc-<instancia>.service`). Rollback = volver el jar: no hay estado.

Chequeo operativo después de cada deploy del filial: `journalctl -u frc-<instancia> | grep "no existe
en la filial, omitida"` en el central. Un renglón que se repite días después de que la filial se
actualizó significa una migración que no llegó: antes lo delataba el `ERROR` del log de PostgreSQL.

## Auditoría del plan (paso 5)

- Eje A: sin contratos tocados (GraphQL, enum, firma pública; el desktop solo muestra el texto del log,
  `logical-replication.service.ts:578`); los `ADD TABLE` de los pasos 1 y 2 son locales al central;
  `to_regclass(text)` existe en todo PG con réplica lógica (≥10) y resuelve el nombre igual que
  `ADD TABLE`. Riesgo: el «omitida» queda en `info` en el resumen del scheduler → **aplicado**: warn
  propio grep-eable y chequeo operativo.
- Eje B: (1) cambiar ruido por silencio → **aplicado** (ídem). Nombres con mayúsculas: las 109 filas de
  `replication_table` local están en minúsculas, y la semántica es la del `ADD TABLE` → sin cambio.
  (2) la receta de runtime tocaba `general` y `bodega` reales → **aplicado** (`configuraciones.local`,
  user-dev propio, puerto). (3) publicaciones y `REPLICA IDENTITY` no se revierten con el jar: ya era
  así, no es regresión. (4) detalles del test → **aplicados**. (5) el catch «duplicate» es solo del
  paso 3 → la extracción se usa solo ahí.

## Sin verificar

- El ruido del log de PostgreSQL en una filial real después del deploy (se mira en el próximo ciclo
  del scheduler en bodega).
