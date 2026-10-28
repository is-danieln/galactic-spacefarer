#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
mailpit_pid=""
cleanup() {
  if [ -n "$mailpit_pid" ]; then
    kill "$mailpit_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if ! curl --silent --fail http://127.0.0.1:8025/api/v1/info >/dev/null; then
  mailpit_bin="$(command -v mailpit || true)"
  if [ -z "$mailpit_bin" ]; then
    mailpit_bin="$HOME/.cache/galactic-spacefarer-tools/mailpit/mailpit"
  fi
  if [ ! -x "$mailpit_bin" ]; then
    echo 'Előbb indítsd el a Mailpitet: docker compose up -d'
    echo 'Vagy telepítsd a Mailpitet, és add hozzá a PATH-hoz.'
    exit 1
  fi
  "$mailpit_bin" --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025 &
  mailpit_pid=$!
fi

echo 'Alkalmazás: http://localhost:8080/spacefarers/index.html'
echo 'Tesztpostaláda: http://localhost:8025'
"$project_dir/scripts/maven.sh" spring-boot:run
