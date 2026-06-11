#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# FRC Central Server — Deploy Script
# Runs on the server via SSH from GitHub Actions
# Usage: ./deploy.sh <version> <instance>
# Example: ./deploy.sh 3.1.0-alpha.1 alpha
# ============================================================

VERSION="${1:?Usage: deploy.sh <version> <instance> [firebase_json_path] [firebase_target_path]}"
INSTANCE="${2:?Usage: deploy.sh <version> <instance> [firebase_json_path] [firebase_target_path]}"
FIREBASE_JSON_SOURCE="${3:-}"
FIREBASE_TARGET_OVERRIDE="${4:-}"

BASE_DIR="/opt/frc-backend-central"
INSTANCE_DIR="${BASE_DIR}/${INSTANCE}"
RELEASES_DIR="${BASE_DIR}/releases"
RELEASE_DIR="${RELEASES_DIR}/${VERSION}"
JAR_NAME="frc-central-server.jar"
SERVICE_NAME="frc-${INSTANCE}.service"
HEALTH_URL="http://localhost:$(grep SERVER_PORT "${INSTANCE_DIR}/.env" | cut -d= -f2)/actuator/health"
HEALTH_TIMEOUT=500
HEALTH_INTERVAL=5
DEFAULT_FIREBASE_FILE="bodega-franco-frc-18e8c6ef35cf.json"
FIREBASE_DIR="${INSTANCE_DIR}/secrets"
FIREBASE_TARGET="${FIREBASE_DIR}/${DEFAULT_FIREBASE_FILE}"

if [[ -n "${FIREBASE_TARGET_OVERRIDE}" ]]; then
  FIREBASE_TARGET="${FIREBASE_TARGET_OVERRIDE}"
elif [[ -n "${FIREBASE_JSON_SOURCE}" ]]; then
  SOURCE_BASENAME=$(basename "${FIREBASE_JSON_SOURCE}")
  FIREBASE_TARGET="${FIREBASE_DIR}/${SOURCE_BASENAME}"
fi

# --- Validations ---
if [[ ! -d "${INSTANCE_DIR}" ]]; then
  echo "ERROR: Instance directory ${INSTANCE_DIR} does not exist"
  exit 1
fi

if [[ ! -f "${RELEASE_DIR}/${JAR_NAME}" ]]; then
  echo "ERROR: JAR not found at ${RELEASE_DIR}/${JAR_NAME}"
  exit 1
fi

if [[ -n "${FIREBASE_JSON_SOURCE}" && -f "${FIREBASE_JSON_SOURCE}" ]]; then
  echo "Installing Firebase credentials for ${INSTANCE}..."
  mkdir -p "$(dirname "${FIREBASE_TARGET}")"
  cp "${FIREBASE_JSON_SOURCE}" "${FIREBASE_TARGET}"
  chmod 600 "${FIREBASE_TARGET}"
  rm -f "${FIREBASE_JSON_SOURCE}"
  echo "Firebase credentials installed at ${FIREBASE_TARGET}"
else
  echo "WARNING: Firebase credentials source file not provided. Reusing existing credentials if present."
fi

if [[ -f "${FIREBASE_TARGET}" ]]; then
  if grep -q '^APP_FIREBASE_CONFIGURATION_FILE=' "${INSTANCE_DIR}/.env"; then
    sed -i "s|^APP_FIREBASE_CONFIGURATION_FILE=.*|APP_FIREBASE_CONFIGURATION_FILE=${FIREBASE_TARGET}|" "${INSTANCE_DIR}/.env"
  else
    echo "APP_FIREBASE_CONFIGURATION_FILE=${FIREBASE_TARGET}" >> "${INSTANCE_DIR}/.env"
  fi
  echo "Firebase config path set in ${INSTANCE_DIR}/.env"
else
  echo "WARNING: Firebase credentials not found at ${FIREBASE_TARGET}. Push notifications will fail."
fi

# --- Save current version for rollback ---
PREVIOUS_VERSION=""
if [[ -f "${INSTANCE_DIR}/.current-version" ]]; then
  PREVIOUS_VERSION=$(cat "${INSTANCE_DIR}/.current-version")
fi
echo "Current version: ${PREVIOUS_VERSION:-none}"
echo "Deploying version: ${VERSION}"

# --- Update symlink ---
ln -sfn "${RELEASE_DIR}" "${INSTANCE_DIR}/current"
echo "${VERSION}" > "${INSTANCE_DIR}/.current-version"
echo "Symlink updated: current -> ${RELEASE_DIR}"

# --- Restart service ---
echo "Restarting ${SERVICE_NAME}..."
sudo systemctl restart "${SERVICE_NAME}"

# --- Health check ---
echo "Waiting for health check at ${HEALTH_URL} (timeout: ${HEALTH_TIMEOUT}s)..."
ELAPSED=0
while [[ ${ELAPSED} -lt ${HEALTH_TIMEOUT} ]]; do
  sleep "${HEALTH_INTERVAL}"
  ELAPSED=$((ELAPSED + HEALTH_INTERVAL))

  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${HEALTH_URL}" 2>/dev/null || echo "000")
  echo "  [${ELAPSED}s] HTTP ${HTTP_STATUS}"

  if [[ "${HTTP_STATUS}" == "200" || "${HTTP_STATUS}" == "503" ]]; then
    echo "Health check PASSED (HTTP ${HTTP_STATUS}) — ${INSTANCE} running version ${VERSION}"
    exit 0
  fi
done

# --- Health check failed — Rollback ---
echo "ERROR: Health check failed after ${HEALTH_TIMEOUT}s"

if [[ -n "${PREVIOUS_VERSION}" && -d "${RELEASES_DIR}/${PREVIOUS_VERSION}" ]]; then
  echo "Rolling back to ${PREVIOUS_VERSION}..."
  ln -sfn "${RELEASES_DIR}/${PREVIOUS_VERSION}" "${INSTANCE_DIR}/current"
  echo "${PREVIOUS_VERSION}" > "${INSTANCE_DIR}/.current-version"
  sudo systemctl restart "${SERVICE_NAME}"
  echo "Rollback complete. Service restarted with version ${PREVIOUS_VERSION}"
else
  echo "WARNING: No previous version to rollback to. Manual intervention required."
fi

exit 1
