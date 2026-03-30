# Workflow: agregar una nueva tabla a la replicación lógica

Este documento describe el flujo recomendado cuando se crea una **nueva tabla** que debe participar en la replicación lógica (central ↔ filiales), considerando el sistema actual: `configuraciones.replication_table`, publicaciones en central y en cada filial, y Flyway en ambos servidores.

---

## 1. Cómo funciona hoy el sistema

- **`configuraciones.replication_table`** (solo en central) es la fuente de verdad: define qué tablas se replican y con qué dirección (`MAIN_TO_ALL`, `BRANCH_TO_MAIN`, `MAIN_TO_SPECIFIC`) y flags (`replicate_central_to_branch_with_filter`, `branch_ids`).
- Las **publicaciones** en PostgreSQL se crean con una **lista fija de tablas** en el momento del `CREATE PUBLICATION`. Si después agregas un registro en `replication_table`, las publicaciones **ya existentes** no se actualizan solas: hay que ejecutar `ALTER PUBLICATION ... ADD TABLE` en cada una.
- **Setup nuevo** (diálogo “Configurar replicación”): al crear publicaciones/suscripciones, el backend lee `replication_table` y arma la lista de tablas; las publicaciones nuevas ya quedan con todas las tablas actuales.
- **Publicaciones ya existentes**: para que una tabla nueva entre en ellas, hace falta un paso explícito (Flyway o manual) que haga `ALTER PUBLICATION ... ADD TABLE`.

Por tanto, al agregar una tabla a la replicación hay que:

1. Crear la tabla (y si aplica, `REPLICA IDENTITY FULL`).
2. Registrarla en `replication_table` (solo en central).
3. Añadirla a las publicaciones **existentes** en central y en filial mediante `ALTER PUBLICATION ... ADD TABLE`.

---

## 2. Workflow ideal: usar Flyway

La opción recomendada es **Flyway en central y en filial**, para que todo quede versionado, repetible y sin pasos manuales por entorno.

### 2.1 Migración en **central** (frc-central-server)

En una migración Flyway del central (ej. `VXXX__add_mi_tabla_to_replication.sql`):

1. **Crear la tabla** (si es nueva), con `REPLICA IDENTITY FULL` si la tabla tiene filas que se actualizan/borran y la replicación debe ver los cambios completos:
   ```sql
   CREATE TABLE mi_esquema.mi_tabla (...);
   ALTER TABLE mi_esquema.mi_tabla REPLICA IDENTITY FULL;
   ```

2. **Registrarla en `replication_table`**:
   - Para **central → filiales** (maestro): `direction = 'MAIN_TO_ALL'`.
   - Para **filial → central**: `direction = 'BRANCH_TO_MAIN'` y, si además debe ir en `central_filialX_pub` (central → esa filial con filtro), `replicate_central_to_branch_with_filter = true`.
   - Para **central → solo algunas filiales**: `direction = 'MAIN_TO_SPECIFIC'` y `branch_ids` con los IDs de sucursal.

   Ejemplo (BRANCH_TO_MAIN y con filtro central→filial):
   ```sql
   INSERT INTO configuraciones.replication_table
       (table_name, direction, description, enabled, replicate_central_to_branch_with_filter, creado_en)
   VALUES
       ('mi_esquema.mi_tabla', 'BRANCH_TO_MAIN', 'Descripción', true, true, NOW())
   ON CONFLICT (table_name) DO NOTHING;
   ```

3. **Añadir la tabla a las publicaciones ya existentes en central**:
   - **`central_pub`** (si la tabla es MAIN_TO_ALL):
     ```sql
     ALTER PUBLICATION central_pub ADD TABLE mi_esquema.mi_tabla;
     ```
   - **`central_filialX_pub`** (para cada sucursal que ya tenga publicación): si la tabla es BRANCH_TO_MAIN con filtro o MAIN_TO_SPECIFIC, hacer un bloque PL/pgSQL que recorra las publicaciones existentes y ejecute:
     ```sql
     ALTER PUBLICATION central_filialX_pub ADD TABLE mi_esquema.mi_tabla WHERE (sucursal_id = X);
     ```
     El patrón ya usado en el proyecto está en migraciones como **V75**, **V77**, **V82**, **V83**: se itera sobre `pg_publication` (por nombre `central_filial%_pub`) o sobre `empresarial.sucursal`, y se ejecuta `ALTER PUBLICATION ... ADD TABLE ... WHERE (sucursal_id = ...)`.

Así, cualquier setup **nuevo** que se haga después ya verá la tabla en `replication_table` y la incluirá al crear publicaciones; y las publicaciones **ya creadas** en central quedan actualizadas por la misma migración.

### 2.2 Migración en **filial** (frc-filial-server)

En la filial la tabla debe existir (misma DDL que en central, normalmente por una migración compartida o equivalente) y debe estar en la publicación **de la filial** `filial{id}_pub`.

En una migración Flyway de la filial (ej. `VXX.X__add_mi_tabla_to_filial_publication.sql`):

1. Si la tabla no existe en la filial, crearla (y `REPLICA IDENTITY FULL` si aplica).
2. Añadirla a la publicación de la filial. El patrón del proyecto (p. ej. **V50**) es detectar la publicación por nombre:
   ```sql
   SELECT pubname FROM pg_publication WHERE pubname LIKE 'filial%_pub' LIMIT 1;
   ```
   y luego:
   ```sql
   ALTER PUBLICATION filialX_pub ADD TABLE mi_esquema.mi_tabla;
   ```
   manejando `duplicate_object` si la tabla ya estaba en la publicación.

Con esto, cada filial que ejecute Flyway actualiza su propia `filialX_pub` sin intervención manual.

### 2.3 Después de desplegar

- En la **central**, Flyway corre al arrancar y aplica la migración (tabla, `replication_table`, ALTER a `central_pub` y `central_filialX_pub`).
- En cada **filial**, Flyway corre al arrancar y aplica la migración (tabla si falta, ALTER a `filialX_pub`).
- Si hace falta que las suscripciones tomen la nueva tabla de inmediato, en cada filial se puede ejecutar:
  ```sql
  ALTER SUBSCRIPTION central_filialX_sub REFRESH PUBLICATION;
  ALTER SUBSCRIPTION filialX_central_sub REFRESH PUBLICATION;
  ```
  Eso se puede documentar en la migración (como en V75) o, si se quiere automatizar, incluir en una migración de la filial que haga `REFRESH PUBLICATION` en las suscripciones (con cuidado de no romper si la suscripción no existe en ese servidor).

---

## 3. ¿Hacerlo manualmente?

Sí es posible, pero no recomendado para entornos controlados:

- **Central:** crear tabla, insertar en `replication_table`, y ejecutar a mano los `ALTER PUBLICATION central_pub ADD TABLE ...` y `ALTER PUBLICATION central_filialX_pub ADD TABLE ... WHERE (sucursal_id = X)` para cada publicación existente.
- **Cada filial:** crear tabla si no existe, `ALTER PUBLICATION filialX_pub ADD TABLE ...`, y opcionalmente `REFRESH PUBLICATION` en las suscripciones.

Ventaja: flexibilidad. Desventaja: no queda trazabilidad, es fácil olvidar una publicación o una filial y es difícil repetir el mismo proceso en otros ambientes.

---

## 4. Sincronización automática (recomendado)

Para no depender de que cada migración Flyway escriba correctamente los `ALTER PUBLICATION` (lo cual ha fallado en el pasado y es propenso a errores), el proyecto incluye:

- **Servicio** `LogicalReplicationService.syncPublicationsWithReplicationTable()`: compara `replication_table` con las tablas actuales de cada publicación en **central** (`central_pub`, `central_filialX_pub`) y en **cada filial** (vía JDBC remoto a `filialX_pub`), y ejecuta `ALTER PUBLICATION ... ADD TABLE` solo para las tablas que falten.
- **Scheduler** `ReplicationPublicationSyncScheduler`: ejecuta esa sincronización al **inicio del servicio** (tras un delay configurable) y **cada 1 hora**. Así, con solo crear la tabla y agregar el registro en `replication_table` vía Flyway, las publicaciones existentes se actualizan solas.
- **Mutation GraphQL** `syncPublicationsWithReplicationTable`: permite lanzar la sincronización manualmente desde el front (p. ej. botón "Sincronizar publicaciones").

**Ventajas:** el desarrollador solo escribe en Flyway: (1) CREATE TABLE + REPLICA IDENTITY, (2) INSERT en `replication_table`. No hay que escribir ningún `ALTER PUBLICATION` en migraciones. Las filiales no necesitan lógica ni scheduler propio: el central se conecta por JDBC y actualiza `filialX_pub` en cada una. Configuración: replication.sync.enabled=true, replication.sync.initial-delay=120000, replication.sync.fixed-delay=3600000 (ms).

### 4.1 Refresco periódico de suscripciones (red de seguridad)

Cuando se añade una tabla a una publicación, cada **suscriptor** debe ejecutar `REFRESH PUBLICATION` para ver la nueva tabla. El sync automático intenta hacer ese refresh al final (por cada filial y en central). Si en ese momento una filial está caída o no responde, esa filial no recibe el refresh y queda desactualizada hasta que se ejecute un refresh más adelante.

Para corregir ese caso sin intervención manual existe un **scheduler de solo refresh**:

- **Servicio** `LogicalReplicationService.refreshAllSubscriptionsEverywhere()`: refresca todas las suscripciones remotas (por cada sucursal: `filialX_central_sub`, `central_filialX_sub`) y luego todas las suscripciones locales en central.
- **Scheduler** `ReplicationRefreshScheduler`: ejecuta ese refresh con una periodicidad configurable (por defecto cada 2 horas). Es liviano: solo sincroniza la lista de tablas de cada publicación con el publicador, no copia datos.

**Configuración** (solo en central):

| Propiedad | Descripción | Valor por defecto |
|-----------|-------------|-------------------|
| replication.refresh.enabled | Activar scheduler de refresh | false |
| replication.refresh.initial-delay | Espera (ms) antes del primer refresh | 300000 (5 min) |
| replication.refresh.fixed-delay | Intervalo (ms) entre refrescos | 7200000 (2 h) |

Puedes tener sync cada 1 h y refresh cada 2 h; si una filial estuvo caída durante el sync, el siguiente refresh la actualizará cuando vuelva a estar disponible.

---

## 5. Resumen recomendado (con sync automático)

| Paso | Dónde | Acción |
|------|--------|--------|
| 1 | Central (Flyway) | Crear tabla, `REPLICA IDENTITY FULL` si aplica, `INSERT` en `configuraciones.replication_table`. |
| 2 | Filial (Flyway) | Crear la misma tabla en la filial si no existe (DDL compartido o migración equivalente). |
| 3 | Central (scheduler o mutation) | El sync automático añade la tabla a `central_pub`, a cada `central_filialX_pub` y, vía JDBC remoto, a cada `filialX_pub` en las filiales. Opcional: botón "Sincronizar publicaciones" en la UI para ejecutarlo ya. |

No es necesario escribir `ALTER PUBLICATION` en Flyway ni en la filial. Basta con activar `replication.sync.enabled=true` en el central (o llamar a la mutation después de desplegar).

---

## 6. Cómo probar la sincronización

**Dónde se configura**

- **application.properties** (o **application-dev.properties** si usas profile `dev`):  
  - Sync de publicaciones:  
    `replication.sync.enabled=true`  
    `replication.sync.initial-delay=120000`  
    `replication.sync.fixed-delay=3600000`  
  - Refresh de suscripciones (opcional, red de seguridad):  
    `replication.refresh.enabled=true`  
    `replication.refresh.initial-delay=300000`  
    `replication.refresh.fixed-delay=7200000`
- En dev ambos están activados en `application-dev.properties`.

**Formas de probar**

1. **Scheduler (automático)**  
   - Arrancar el servidor central con `replication.sync.enabled=true`.  
   - Tras ~2 minutos (initial-delay) se ejecuta la primera sincronización.  
   - Luego se repite cada 1 hora (fixed-delay).  
   - Revisar logs: `ReplicationPublicationSyncScheduler: sincronización completada...`

2. **Botón en la UI (manual)**  
   - En la app desktop: **Configuración → Replicación lógica**.  
   - Dejar **Conexión = Local** (servidor central).  
   - En la columna **Publicaciones**, botón **"Sincronizar publicaciones"**.  
   - Al hacer clic se ejecuta el sync de inmediato; el mensaje de éxito o el log del backend muestran el resultado.

3. **Refresh scheduler (automático, si replication.refresh.enabled=true)**  
   - Tras el initial-delay (p. ej. 5 min) se ejecuta el primer refresh de todas las suscripciones; luego cada fixed-delay (p. ej. 2 h).  
   - Log: `ReplicationRefreshScheduler: refrescando todas las suscripciones (central + filiales)` y `ReplicationRefreshScheduler: refresh completado`.

4. **Comprobar que algo se sincronizó**  
   - En PostgreSQL (central) ver qué tablas tiene cada publicación:
     ```sql
     SELECT pubname, schemaname, tablename
     FROM pg_publication_tables
     WHERE pubname LIKE 'central%'
     ORDER BY pubname, schemaname, tablename;
     ```
   - Ver que las tablas de `configuraciones.replication_table` estén en las publicaciones correspondientes.
   - Para una tabla concreta (ej. `configuraciones.replication_test`):
     ```sql
     SELECT pubname, schemaname, tablename
     FROM pg_publication_tables
     WHERE schemaname = 'configuraciones' AND tablename = 'replication_test'
     ORDER BY pubname;
     ```
   - Para ver el **replica identity** de una tabla antes/después del sync:
     ```sql
     SELECT n.nspname AS schema, c.relname AS table_name,
            CASE c.relreplident
              WHEN 'd' THEN 'default (primary key)'
              WHEN 'n' THEN 'nothing'
              WHEN 'f' THEN 'full'
              WHEN 'i' THEN 'index'
            END AS replica_identity
     FROM pg_class c
     JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'configuraciones' AND c.relname = 'replication_test';
     ```

---

## 7. Significado del log de sincronización

Cuando se ejecuta el sync (scheduler o botón "Sincronizar publicaciones"), el backend escribe un log línea a línea. Cada línea indica **qué se hizo** en esa ejecución.

| Mensaje | Significado |
|--------|-------------|
| `Sincronizando publicaciones con replication_table...` | Inicio del proceso. Se lee la configuración de `configuraciones.replication_table`. |
| `central_pub: agregada tabla esquema.tabla` | La tabla estaba en `replication_table` como MAIN_TO_ALL y **no estaba** en la publicación `central_pub`; se ejecutó `ALTER PUBLICATION central_pub ADD TABLE esquema.tabla`. A partir de ahora esa tabla se replica del central a todas las filiales que consuman `central_pub`. |
| `central_pub: esquema.tabla ya estaba (OK)` | La tabla ya formaba parte de `central_pub`; no se hizo nada. |
| `central_pub: sin tablas faltantes` | Todas las tablas MAIN_TO_ALL ya estaban en `central_pub`. |
| `central_pub: no existe, omitido` | No existe la publicación `central_pub` en este servidor; se omite ese paso. |
| `central_pub: REPLICA IDENTITY FULL aplicado a esquema.tabla` | La tabla no tenía REPLICA IDENTITY FULL; antes de añadirla a la publicación se ejecutó `ALTER TABLE esquema.tabla REPLICA IDENTITY FULL`. |
| `central_filialX_pub: agregada tabla esquema.tabla` | La tabla corresponde a esa filial (BRANCH_TO_MAIN con filtro o MAIN_TO_SPECIFIC) y **no estaba** en la publicación `central_filialX_pub`; se ejecutó `ALTER PUBLICATION central_filialX_pub ADD TABLE esquema.tabla WHERE (sucursal_id = X)`. A partir de ahora la filial X recibe cambios de esa tabla (solo filas con sucursal_id = X). |
| `central_filialX_pub: esquema.tabla ya estaba (OK)` | La tabla ya estaba en esa publicación; no se hizo nada. |
| `Filial X filialX_pub: agregada tabla esquema.tabla` | En el servidor de la filial X, la tabla **no estaba** en la publicación `filialX_pub`; el central se conectó por JDBC a la filial y ejecutó `ALTER PUBLICATION filialX_pub ADD TABLE esquema.tabla`. Esa tabla pasa a replicarse de la filial al central. |
| `Filial X: esquema.tabla ya estaba (OK)` | En esa filial la tabla ya estaba en `filialX_pub`. |
| `Filial X: REPLICA IDENTITY FULL aplicado a esquema.tabla` | En la filial X la tabla no tenía REPLICA IDENTITY FULL; se ejecutó `ALTER TABLE ... REPLICA IDENTITY FULL` en la base remota antes de añadirla a `filialX_pub`. |
| `Filial X: no se pudo conectar o sincronizar: ...` | No se pudo conectar a la filial (IP/puerto/base) o falló el ALTER en la filial; ver el mensaje de error. |
| `Filiales: no hay tablas BRANCH_TO_MAIN en replication_table` | No hay tablas configuradas para filial→central; no se intenta actualizar ninguna `filialX_pub`. |
| `Sincronización finalizada` | El proceso terminó. Si antes hubo errores, aparecen en líneas anteriores; el resumen igual indica "completada" si no hubo fallo que detuviera todo el proceso. |

**Ejemplo de log interpretado**

```
Sincronizando publicaciones con replication_table...
  central_pub: agregada tabla financiero.documento_electronico.
  central_pub: agregada tabla financiero.evento_cancelacion_de.
  ...
  central_filial10_pub: agregada tabla configuraciones.replication_test.
```

- En **central**: las tablas de documento electrónico y la de test estaban en `replication_table` pero no en las publicaciones; el sync las añadió a `central_pub` y, en el caso de `replication_test`, a `central_filial10_pub` (solo para sucursal 10).
- Si en una próxima ejecución esas tablas ya están en las publicaciones, verás "ya estaba (OK)" o "sin tablas faltantes" y no se ejecutará ningún ALTER.
