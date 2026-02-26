Guía rápida — Script de backup remoto + recreación local de PostgreSQL
¿Para qué sirve?

Este script automatiza dos tareas encadenadas:

Hace un backup (dump .sql) de una base remota de PostgreSQL (por SSH o conexión directa) y guarda el archivo en la misma carpeta donde está el script.

Con ese .sql, borra y recrea una base de datos local (DROP/CREATE) y restaura su contenido.

Incluye manejo de:

Conexiones activas (las cierra para poder dropear).

Suscripciones de replicación lógica (las deshabilita y elimina si existen).

Replication slots inactivos asociados a esa DB.

Logging de errores y avisos a un archivo .err en el mismo directorio del script.

Requisitos

psql y pg_dump instalados en tu máquina local.

Acceso al host remoto:

Modo SSH: acceso SSH al servidor donde corre PostgreSQL.

Modo directo: acceso de red al puerto PostgreSQL remoto.

Credenciales válidas (usuario/clave) tanto remotas como locales.

Permisos de ejecución: chmod +x tu_script.sh.

Cómo se usa (paso a paso)

Abre el script y edita solo la sección de Configuración:

Dump remoto:

REMOTE_DUMP_MODE: "ssh" (recomendado) o "direct".

REMOTE_DB_HOST, REMOTE_DB_PORT, REMOTE_DB_USER, REMOTE_DB_NAME, REMOTE_DB_PASSWORD.

Para SSH: SSH_USER, SSH_HOST (generalmente igual a REMOTE_DB_HOST), SSH_PORT.

REMOTE_DUMP_WITH_CREATE_DB:

false (recomendado): genera un .sql sin CREATE DATABASE.

true: incluye CREATE DATABASE en el dump (útil si quieres restaurar “tal cual”).

Restore local:

PGHOST, PGPORT, PGUSER, PGPASSWORD_VALUE.

PGDATABASE_FOR_MGMT (normalmente postgres).

DB_NAME (nombre local que vas a recrear) y DB_OWNER.

CREATE_DB_OPTS si quieres especificar encoding/locale (opcional).

ON_ERROR_STOP=1 para detenerse al primer error (recomendado).

Guarda y ejecuta:

./tu_script.sh


Resultado:

Se creará un archivo .sql con nombre tipo NOMBRE_full_YYYYMMDD_HHMMSS.sql junto al script.

Se recreará la base local indicada y se restaurará desde ese .sql.

Cualquier error/aviso quedará también en ./tu_script.err.

Qué hace internamente

Paso 0: Backup remoto → .sql

SSH: ejecuta pg_dump en el servidor remoto y trae la salida a tu máquina. No necesitas exponer el puerto de PG.

Directo: ejecuta pg_dump desde tu máquina, conectando a host:port remotos.

El archivo .sql se guarda en la misma carpeta del script.

Paso 1: Preparación local

Termina conexiones activas a DB_NAME.

Elimina suscripciones lógicas (si existen) y sus replication slots inactivos.

Paso 2: Drop/Create/Restore

Si el .sql tiene CREATE DATABASE (cuando REMOTE_DUMP_WITH_CREATE_DB=true), se restaura contra PGDATABASE_FOR_MGMT (e.g., postgres) para que el propio SQL cree la DB.

Si el .sql no tiene CREATE DATABASE (recomendado), el script crea primero la DB (DB_NAME) y luego restaura dentro.

Dónde quedan los archivos

Dump: ./<REMOTE_DB_NAME>_full_YYYYMMDD_HHMMSS.sql
(por ejemplo: ./bodega_full_20251014_150808.sql)

Log de errores/avisos: ./<nombre_del_script>.err

Puedes monitorear el log en vivo:

tail -f ./tu_script.err

Ajustes útiles

Mensajes y fallos:

ON_ERROR_STOP=1 hace que psql se detenga al primer error (ideal para evitar restauraciones a medias).

El script usa una verbosidad que captura NOTICE/WARNING/ERROR en el .err.

Locales/encoding:

Si quieres asegurar UTF8 y locales, usa CREATE_DB_OPTS, por ejemplo:
CREATE_DB_OPTS="ENCODING 'UTF8' LC_COLLATE 'es_PY.UTF-8' LC_CTYPE 'es_PY.UTF-8'"

Seguridad:

Considera ~/.pgpass para no dejar contraseñas en el script (formato: host:port:db:user:password).

En modo SSH, asegúrate de tener claves o password habilitado para el usuario de sistema.

Errores comunes y cómo resolver

“database is being used by logical replication subscriptions”
→ El script ya intenta DROP SUBSCRIPTION y limpiar slots. Si persiste, revisa si hay procesos externos conectados y vuelve a correr.

Permisos insuficientes
→ Usa un usuario con privilegios para DROP DATABASE, CREATE DATABASE y ALTER/DROP SUBSCRIPTION.

Conexión remota fallida
→ Verifica firewall/puertos y credenciales. En modo SSH, confirma acceso al servidor y que pg_dump esté instalado allí.

¿Cuándo conviene REMOTE_DUMP_WITH_CREATE_DB = true?

Si quieres un dump “completo” que incluya CREATE DATABASE y restaurarlo “tal cual” (por ejemplo, replicar ambiente con mismo nombre original de la DB).

Nota: En ese caso, la restauración se ejecuta contra PGDATABASE_FOR_MGMT (no dentro de DB_NAME) y el propio dump creará la base.