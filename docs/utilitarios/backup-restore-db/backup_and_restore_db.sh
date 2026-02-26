#!/usr/bin/env bash
set -euo pipefail

# === Logging de errores/avisos a .err en la misma carpeta del script ===
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
ERR_LOG="${SCRIPT_DIR}/$(basename "$0" .sh).err"

{
  echo "============================================================"
  echo "Run: $(date '+%F %T %z') - Script: $(basename "$0")"
  echo "============================================================"
} >>"$ERR_LOG"

# Redirige todo STDERR del script al .err y también lo muestra en pantalla
exec 2> >(tee -a "$ERR_LOG" >&2)

###############################################################################
# Configuración (EDITA SOLO ESTA SECCIÓN)
###############################################################################

# --- (1) DUMP REMOTO ---
# Modo del dump remoto: "ssh" (recomendado) o "direct"
REMOTE_DUMP_MODE="ssh"

# Datos de la DB remota a respaldar
REMOTE_DB_HOST="172.25.1.200"
REMOTE_DB_PORT="5551"
REMOTE_DB_USER="franco"
REMOTE_DB_NAME="bodega"
REMOTE_DB_PASSWORD="franco"

# Si usas modo "ssh"
SSH_USER="franco"          # usuario SSH en el host remoto
SSH_HOST="${REMOTE_DB_HOST}"
SSH_PORT="22"              # cambia si tu SSH no es 22

# Nombre base del archivo de dump remoto (se guardará en el mismo directorio del script)
REMOTE_DUMP_PREFIX="${REMOTE_DB_NAME}_full"

# Incluir CREATE DATABASE en el dump? (afecta el restore luego)
# Recomendado: false (más simple restaurar dentro de la DB ya creada)
REMOTE_DUMP_WITH_CREATE_DB="false"

# --- (2) RESTORE LOCAL ---
# Conexión PostgreSQL local (o donde vas a restaurar)
PGHOST="127.0.0.1"
PGPORT="5551"
PGUSER="franco"
PGPASSWORD_VALUE="franco"

# DB “de gestión” para ejecutar DROP/CREATE y pg_restore --create
PGDATABASE_FOR_MGMT="postgres"

# Base de datos objetivo a recrear (LOCAL)
DB_NAME="bodega_fact_test_2"
DB_OWNER="franco"         # owner de la DB creada (puede ser el mismo usuario)

# Opcional: Encoding/Locale al crear DB (dejar vacío para defaults del cluster)
CREATE_DB_OPTS=""         # ej: "ENCODING 'UTF8' LC_COLLATE 'es_PY.UTF-8' LC_CTYPE 'es_PY.UTF-8'"

# Política de stop en error para psql (1=detenerse en el primer error, recomendado)
ON_ERROR_STOP="0"

###############################################################################
# No editar desde aquí
###############################################################################

export PGPASSWORD="${PGPASSWORD_VALUE}"

timestamp() { date +%Y%m%d_%H%M%S; }

# Archivo final de dump (se define automáticamente en el directorio del script)
DUMP_FILE="${SCRIPT_DIR}/${REMOTE_DUMP_PREFIX}_$(timestamp).sql"

# Flags comunes de psql más verbosos (capturamos warnings/notices)
PSQL_FLAGS=(-v ON_ERROR_STOP=${ON_ERROR_STOP} -v VERBOSITY=verbose -v SHOW_CONTEXT=always)

psql_mgmt() {
  psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${PGDATABASE_FOR_MGMT}" \
       "${PSQL_FLAGS[@]}" -c "$1"
}

# --------------------------
# (0) BACKUP REMOTO -> .sql
# --------------------------

remote_dump_direct() {
  echo "==> Realizando dump REMOTO (direct) ${REMOTE_DB_HOST}:${REMOTE_DB_PORT}/${REMOTE_DB_NAME}"
  # Si quieres incluir CREATE DATABASE añade -C y marca REMOTE_DUMP_WITH_CREATE_DB="true"
  if [[ "${REMOTE_DUMP_WITH_CREATE_DB}" == "true" ]]; then
    PGPASSWORD="${REMOTE_DB_PASSWORD}" pg_dump \
      -h "${REMOTE_DB_HOST}" -p "${REMOTE_DB_PORT}" -U "${REMOTE_DB_USER}" \
      -C -F p "${REMOTE_DB_NAME}" > "${DUMP_FILE}"
  else
    PGPASSWORD="${REMOTE_DB_PASSWORD}" pg_dump \
      -h "${REMOTE_DB_HOST}" -p "${REMOTE_DB_PORT}" -U "${REMOTE_DB_USER}" \
      -F p "${REMOTE_DB_NAME}" > "${DUMP_FILE}"
  fi
  echo "   Dump guardado: ${DUMP_FILE}"
}

remote_dump_ssh() {
  echo "==> Realizando dump REMOTO (ssh) ${SSH_USER}@${SSH_HOST} -> ${DUMP_FILE}"
  # Ejecuta pg_dump en el host remoto y trae la salida a un .sql local
  if [[ "${REMOTE_DUMP_WITH_CREATE_DB}" == "true" ]]; then
    ssh -p "${SSH_PORT}" "${SSH_USER}@${SSH_HOST}" \
      "PGPASSWORD='${REMOTE_DB_PASSWORD}' pg_dump -h 127.0.0.1 -p ${REMOTE_DB_PORT} -U ${REMOTE_DB_USER} -C -F p ${REMOTE_DB_NAME}" \
      > "${DUMP_FILE}"
  else
    ssh -p "${SSH_PORT}" "${SSH_USER}@${SSH_HOST}" \
      "PGPASSWORD='${REMOTE_DB_PASSWORD}' pg_dump -h 127.0.0.1 -p ${REMOTE_DB_PORT} -U ${REMOTE_DB_USER} -F p ${REMOTE_DB_NAME}" \
      > "${DUMP_FILE}"
  fi
  echo "   Dump guardado: ${DUMP_FILE}"
}

# --------------------------
# (1) UTILIDADES RESTORE
# --------------------------

# Cierra conexiones contra DB_NAME (sin usar :'var' ni $$)
terminate_connections() {
  psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${PGDATABASE_FOR_MGMT}" "${PSQL_FLAGS[@]}" <<SQL
SET client_min_messages TO NOTICE;
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${DB_NAME}'
  AND pid <> pg_backend_pid();
SQL
}

# Elimina suscripciones lógicas de la DB (evita bloqueo del DROP)
drop_all_subscriptions() {
  local subs
  subs=$(psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${DB_NAME}" -At -c "SELECT subname FROM pg_subscription;" || true)
  [[ -z "${subs}" ]] && { echo "  - No hay suscripciones lógicas en '${DB_NAME}'."; return; }

  echo "  - Eliminando suscripciones en '${DB_NAME}':"
  while IFS= read -r sub; do
    [[ -z "$sub" ]] && continue
    echo "    * $sub -> DISABLE, SET slot_name=NONE, DROP"
    psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${DB_NAME}" "${PSQL_FLAGS[@]}" \
         -c "ALTER SUBSCRIPTION \"${sub}\" DISABLE;" || true
    psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${DB_NAME}" "${PSQL_FLAGS[@]}" \
         -c "ALTER SUBSCRIPTION \"${sub}\" SET (slot_name = NONE);" || true
    psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${DB_NAME}" "${PSQL_FLAGS[@]}" \
         -c "DROP SUBSCRIPTION \"${sub}\";" || true
  done <<< "${subs}"
}

# Elimina replication slots que apunten a esta DB (por si quedó alguno)
drop_replication_slots() {
  psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${DB_NAME}" "${PSQL_FLAGS[@]}" <<SQL
SET client_min_messages TO NOTICE;
SELECT pg_drop_replication_slot(slot_name)
FROM pg_replication_slots
WHERE database = current_database()
  AND active IS FALSE;
SQL
}

drop_db_if_exists() {
  psql_mgmt "DROP DATABASE IF EXISTS \"${DB_NAME}\";"
}

create_db() {
  if [[ -n "${CREATE_DB_OPTS}" ]]; then
    psql_mgmt "CREATE DATABASE \"${DB_NAME}\" WITH OWNER \"${DB_OWNER}\" TEMPLATE template1 ${CREATE_DB_OPTS};"
  else
    psql_mgmt "CREATE DATABASE \"${DB_NAME}\" WITH OWNER \"${DB_OWNER}\" TEMPLATE template1;"
  fi
}

restore_sql_into() {
  psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${DB_NAME}" "${PSQL_FLAGS[@]}" -f "${DUMP_FILE}"
}

# --------------------------
# (2) FLUJO PRINCIPAL
# --------------------------

echo "==> Paso 0: Backup remoto -> ${DUMP_FILE}"
case "${REMOTE_DUMP_MODE}" in
  ssh)    remote_dump_ssh ;;
  direct) remote_dump_direct ;;
  *) echo "ERROR: REMOTE_DUMP_MODE debe ser 'ssh' o 'direct'"; exit 1 ;;
esac

echo "==> Verificaciones iniciales del dump"
if [[ ! -f "${DUMP_FILE}" ]]; then
  echo "ERROR: No se generó el archivo de dump: ${DUMP_FILE}"
  exit 1
fi

# A partir de aquí, usamos el dump recién generado
# Determina si el dump trae CREATE DATABASE (según configuración usada)
if [[ "${REMOTE_DUMP_WITH_CREATE_DB}" == "true" ]]; then
  SQL_HAS_CREATE_DB="true"
else
  SQL_HAS_CREATE_DB="false"
fi

echo "==> Procesando base LOCAL: ${DB_NAME}"

# 1) Cerrar conexiones y limpiar replicación lógica/slots
echo "  - Terminando conexiones (si existe) ..."
terminate_connections || true

echo "  - Eliminando suscripciones lógicas (si existen) ..."
drop_all_subscriptions || true

echo "  - Eliminando replication slots (si existen) ..."
drop_replication_slots || true

# 2) DROP/CREATE según el tipo de dump (siempre .sql plano en este flujo)
if [[ "${SQL_HAS_CREATE_DB}" == "true" ]]; then
  echo "  - Eliminando DB (si existe) ..."
  drop_db_if_exists

  echo "  - Restaurando .sql con CREATE DATABASE contra '${PGDATABASE_FOR_MGMT}' ..."
  psql "host=${PGHOST} port=${PGPORT} user=${PGUSER} dbname=${PGDATABASE_FOR_MGMT}" "${PSQL_FLAGS[@]}" -f "${DUMP_FILE}"
else
  echo "  - Eliminando DB (si existe) ..."
  drop_db_if_exists

  echo "  - Creando DB ..."
  create_db

  echo "  - Restaurando .sql dentro de '${DB_NAME}' ..."
  restore_sql_into
fi

echo "✓ Listo: dump remoto en ${DUMP_FILE} y restauración local en DB '${DB_NAME}' completados."
