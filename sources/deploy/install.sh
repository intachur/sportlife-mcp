#!/usr/bin/env bash
set -euo pipefail

APP_NAME="sportlife-mcp"
APP_USER="chur"
APP_DIR="/opt/sportlife"
JAR_NAME="sportlife-mcp.jar"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVICE_SOURCE="${SCRIPT_DIR}/${APP_NAME}.service"
ENV_EXAMPLE="${SCRIPT_DIR}/sportlife.env.example"
ENV_SOURCE="${SCRIPT_DIR}/sportlife.env"
if [[ ! -f "${ENV_SOURCE}" ]]; then
    ENV_SOURCE="${ENV_EXAMPLE}"
fi

JAR_SOURCE="${PROJECT_DIR}/target/sportlife-mcp.jar"
if [[ ! -f "${JAR_SOURCE}" ]]; then
    echo "Jar not found: ${JAR_SOURCE}. Run 'mvn package' first." >&2
    exit 1
fi

if ! id -u "${APP_USER}" >/dev/null 2>&1; then
    sudo useradd --system --create-home --shell /usr/sbin/nologin "${APP_USER}"
fi

sudo install -d -o "${APP_USER}" -g "${APP_USER}" "${APP_DIR}" "${APP_DIR}/logs"
sudo install -m 0644 "${JAR_SOURCE}" "${APP_DIR}/${JAR_NAME}"

if [[ ! -f "${APP_DIR}/sportlife.env" ]]; then
    sudo install -m 0600 -o "${APP_USER}" -g "${APP_USER}" "${ENV_SOURCE}" "${APP_DIR}/sportlife.env"
    echo "Created ${APP_DIR}/sportlife.env. Set SPORTLIFE_AUTH_TOKEN there."
fi

sudo install -m 0644 "${SERVICE_SOURCE}" "/etc/systemd/system/${APP_NAME}.service"
sudo systemctl daemon-reload
sudo systemctl enable "${APP_NAME}"
sudo systemctl restart "${APP_NAME}"
sudo systemctl status "${APP_NAME}" --no-pager
