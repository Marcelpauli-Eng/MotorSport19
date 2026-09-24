#!/usr/bin/env bash
# Deja la base de la demo recién cargada (solo el volumen ms19video_dbvideo; la base real no se toca).
set -euo pipefail
cd "$(dirname "$0")"
docker compose -f compose.yml down -v >/dev/null 2>&1
docker compose -f compose.yml up -d >/dev/null 2>&1
for _ in $(seq 1 90); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:4340/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin1234"}')" = 200 ] && echo "demo lista" && exit 0
  sleep 2
done
echo "la demo no arranca"; exit 1
