#!/usr/bin/env bash
# Deja la base de la demo recién cargada (solo el volumen ms19video_dbvideo; la base real no se toca).
#   ./reiniciar.sh [semilla.sql ...]   las semillas se aplican encima de los datos de demostración
set -euo pipefail
cd "$(dirname "$0")"
docker compose -f compose.yml down -v >/dev/null 2>&1
docker compose -f compose.yml up -d >/dev/null 2>&1
for _ in $(seq 1 90); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:4340/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin1234"}')" = 200 ]; then
    for s in "$@"; do docker compose -f compose.yml exec -T db psql -q -v ON_ERROR_STOP=1 -U taller -d demo < "$s"; done
    echo "demo lista"; exit 0
  fi
  sleep 2
done
echo "la demo no arranca"; exit 1
