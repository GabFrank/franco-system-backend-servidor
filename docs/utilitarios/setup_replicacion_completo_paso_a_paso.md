# Setup de replicación completo – Paso a paso y qué debe suceder

Este documento describe el flujo del **setup de replicación completo** (central + filial) implementado desde la central: qué hace el usuario, qué ejecuta el sistema en cada paso y qué se espera que ocurra.

---

## 1. Desde el punto de vista del usuario (Frontend)

| Paso | Acción del usuario | Qué ve en pantalla |
|------|-------------------|--------------------|
| 1 | Abre el módulo de **Replicación lógica** en la aplicación desktop (central). | Listado de publicaciones/suscripciones y opciones de configuración. |
| 2 | Hace clic en el botón/acción que abre el diálogo **Configurar replicación** (setup). | Diálogo "CONFIGURAR REPLICACIÓN LÓGICA". |
| 3 | Selecciona una **sucursal destino** en el combo (solo sucursales activas con IP y puerto configurados). | Se muestra IP, puerto y conteos de tablas por dirección (Principal a todas, Principal a específica, Sucursal a principal). Opción "CONFIGURAR" para editar datos de la sucursal si hace falta. |
| 4 | Opcional: revisa "Ver tablas" para ver las tablas habilitadas para replicación. | Listado de tablas y su dirección. |
| 5 | Pulsa **CONFIGURAR**. | El botón muestra un spinner; el formulario queda deshabilitado mientras se ejecuta el setup. |
| 6 | Espera a que termine el proceso (puede tardar varios segundos: creación de pub/sub + espera 3 s + test E2E). | Cuando termina, el formulario desaparece y se muestra la sección de **resultado** con: título "Configuración completada" o "Configuración fallida" y un **log en texto** con cada paso ejecutado. |
| 7 | Lee el log (paso 1 a paso 10 y, si aplica, mensajes de rollback o advertencias). | Log en formato paso a paso; en caso de error, también se listan las operaciones de rollback. |
| 8 | Si hubo error: puede pulsar **REINTENTAR** (vuelve al formulario) o **CERRAR**. Si hubo éxito: pulsa **CERRAR**. | El diálogo se cierra; en éxito el resultado se considera `true` para refrescar listas si corresponde. |

---

## 2. Qué hace el sistema (Backend) – Los 10 pasos

El servicio `LogicalReplicationService.setupFullReplication(sucursalId)` ejecuta en orden lo siguiente. Cualquier fallo antes de terminar el paso 10 puede disparar **rollback** (ver sección 4).

| Paso | Acción técnica | Qué debe suceder |
|------|----------------|------------------|
| **1** | Validar sucursal | La sucursal existe, está activa y tiene IP y puerto no nulos. Si no: se lanza error y se retorna mensaje en el log. |
| **2** | Verificar conexión a la base **central** | Se ejecuta `SELECT 1` en la base de datos donde corre la aplicación (central). Debe devolver 1. Si falla: error de conexión y mensaje en el log. |
| **3** | Verificar conexión a la base **filial** | Se crea conexión JDBC a la filial (IP/puerto/base de la filial) y se ejecuta `SELECT 1`. Debe devolver 1. Si falla: error de conexión y mensaje en el log. |
| **4** | Crear publicación en **central** | Se crea la publicación `central_filial{id}_pub` en la base central, con las tablas BRANCH_TO_MAIN que tengan `replicate_central_to_branch_with_filter = true` y filtro `WHERE sucursal_id = {id}`. Debe crearse sin error. |
| **5** | Crear publicación en **filial** (remoto) | Se crea en la base filial la publicación `filial{id}_pub` con la lista de tablas BRANCH_TO_MAIN (la misma que usa el servicio para branch). Debe crearse sin error. |
| **6** | Crear suscripción en **central** | En la base central se crea la suscripción `filial{id}_sub` que se conecta a la filial y se suscribe a `filial{id}_pub`. Así la central recibe datos que la filial publique. Debe crearse sin error. |
| **7** | Crear suscripción en **filial** (central → filial) | En la base filial (vía comando remoto) se crea la suscripción `central_filial{id}_sub` que se conecta a la central y se suscribe a `central_filial{id}_pub`. Así la filial recibe datos que la central publique para esa sucursal. Debe crearse sin error. |
| **8** | Crear suscripción en **filial** (central_pub) | En la base filial se crea la suscripción `filial{id}_central_sub` que se conecta a la central y se suscribe a `central_pub`. Así la filial recibe datos MAIN_TO_ALL de la central. Debe crearse sin error. |
| **9** | Esperar 3 segundos y verificar workers | Se duerme 3 s y se consulta `pg_stat_subscription`: en central que `filial{id}_sub` tenga `pid` no nulo; en filial que `central_filial{id}_sub` y `filial{id}_central_sub` tengan `pid` no nulo. Se escribe en el log "Workers: central OK, filial OK (2/2)" o un warning si algún worker no aparece. No se hace rollback por un warning aquí. |
| **10** | Test E2E con `configuraciones.replication_test` | Se asegura la tabla de test en la filial (CREATE TABLE IF NOT EXISTS). Se inserta en central un registro con un UUID único y `source_db = 'CENTRAL'`; se inserta en filial otro con el mismo UUID y `source_db = 'FILIAL'`. Se espera 3 s. Se verifica en filial que exista al menos un registro con `source_db = 'CENTRAL'` y en central que exista al menos uno con `source_db = 'FILIAL'`. Se borran los registros de prueba. Se escribe en el log "Test E2E: central->filial OK/FAIL, filial->central OK/FAIL". Si el test falla solo se reporta como warning; no se hace rollback de pub/sub. |

Al final, el backend devuelve un **ReplicationStatus** con `success = true` si todos los pasos 1–8 se completaron correctamente (y opcionalmente el 9 y 10 con warnings); `success = false` si en algún paso 1–8 hubo error. El campo `message` contiene el **log paso a paso** que el frontend muestra en el diálogo.

---

## 3. Resultado esperado cuando todo sale bien

- En **central**:
  - Existe la publicación `central_filial{id}_pub` con las tablas configuradas (BRANCH_TO_MAIN con filtro).
  - Existe la suscripción `filial{id}_sub` apuntando a la filial y a `filial{id}_pub`.
  - En `pg_stat_subscription`, la suscripción `filial{id}_sub` tiene un `pid` (worker activo).

- En **filial**:
  - Existe la publicación `filial{id}_pub` con las tablas BRANCH_TO_MAIN.
  - Existen las suscripciones `central_filial{id}_sub` (a `central_filial{id}_pub` en central) y `filial{id}_central_sub` (a `central_pub` en central).
  - En `pg_stat_subscription`, ambas suscripciones tienen `pid` (workers activos).

- En el **log** del diálogo:
  - Líneas de "Paso 1" a "Paso 10" indicando éxito.
  - Paso 9: mensaje de workers OK.
  - Paso 10: "Test E2E: central->filial OK, filial->central OK".
  - Última línea: "Setup de replicación completado correctamente."

- El usuario ve "Configuración completada" y puede cerrar el diálogo.

---

## 4. Qué sucede cuando hay error (rollback)

Si falla cualquier paso entre el **4** y el **8**, el servicio hace **rollback en orden inverso** de lo que llegó a crear:

| Orden de rollback | Si se había creado… | Acción de rollback |
|-------------------|----------------------|---------------------|
| 1 | Suscripción `filial{id}_central_sub` en filial | Se elimina en filial (DROP SUBSCRIPTION). |
| 2 | Suscripción `central_filial{id}_sub` en filial | Se elimina en filial. |
| 3 | Suscripción `filial{id}_sub` en central | Se elimina en central. |
| 4 | Publicación `filial{id}_pub` en filial | Se elimina en filial. |
| 5 | Publicación `central_filial{id}_pub` en central | Se elimina en central. |

Cada operación de rollback se intenta; si alguna falla, se registra un warning en el log pero se continúa con las siguientes. Al final el backend retorna `success = false` y el `message` incluye el error original más las líneas de rollback. El usuario ve "Configuración fallida" y el log completo, y puede REINTENTAR o CERRAR.

---

## 5. Tabla de test de replicación

La migración **V115** crea:

- Tabla `configuraciones.replication_test` (id, test_uuid, source_db, sucursal_id, created_at) con **REPLICA IDENTITY FULL**.
- Un registro en `configuraciones.replication_table` para esa tabla con direction `BRANCH_TO_MAIN` y `replicate_central_to_branch_with_filter = true`.

Así la tabla participa en:

- Publicación en filial `filial{id}_pub` (filial → central).
- Publicación en central `central_filial{id}_pub` con filtro por sucursal (central → filial).

El test E2E del paso 10 usa esta tabla; si en la filial no existe aún (p. ej. Flyway no ha corrido allí), el servicio intenta crearla al inicio del test con `CREATE TABLE IF NOT EXISTS` vía comando remoto.

---

## 6. Resumen rápido

| Fase | Qué pasa |
|------|----------|
| Usuario | Elige sucursal y pulsa CONFIGURAR; espera; lee el log de resultado y cierra o reintenta. |
| Backend | Valida sucursal, comprueba conexiones, crea 1 pub en central, 1 pub en filial, 1 sub en central, 2 subs en filial, espera 3 s, verifica workers, ejecuta test E2E y devuelve log. |
| Si algo falla (pasos 4–8) | Rollback en orden inverso (subs filial → sub central → pubs filial y central) y mensaje de error + log. |
| Frontend | Muestra el `message` del backend como log en `<pre>` y botones REINTENTAR / CERRAR. |

Este es el flujo completo del setup de replicación y lo que debería suceder en cada paso.
