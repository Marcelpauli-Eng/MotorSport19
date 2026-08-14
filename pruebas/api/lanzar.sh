#!/usr/bin/env bash
#
# Lanza la bateria de pruebas de extremo a extremo contra una base de datos
# recien creada, para que cada ejecucion empiece igual que una instalacion nueva.
#
#   ./pruebas/api/lanzar.sh
#
# No toca la base de desarrollo: crea y destruye `motorsport19_pruebas` aparte, y
# levanta el backend en el 8081 para no pelearse con el que ya este en el 8080.

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PUERTO=8081
BD=motorsport19_pruebas
CONTENEDOR=${MOTORSPORT19_DB_CONTAINER:-motorsport19-db}
REGISTRO=$(mktemp -t motorsport19-pruebas)

limpiar() {
    [ -n "${PID:-}" ] && kill "$PID" 2>/dev/null || true
    docker exec "$CONTENEDOR" psql -U taller -d postgres \
        -c "DROP DATABASE IF EXISTS $BD;" >/dev/null 2>&1 || true
}
trap limpiar EXIT

echo "==> Base de datos limpia"
docker exec "$CONTENEDOR" psql -U taller -d postgres \
    -c "DROP DATABASE IF EXISTS $BD;" \
    -c "CREATE DATABASE $BD OWNER taller;" >/dev/null

echo "==> Backend en el puerto $PUERTO (registro: $REGISTRO)"
cd "$RAIZ/backend"
SERVER_PORT=$PUERTO \
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/$BD" \
SPRING_DATASOURCE_USERNAME=taller \
SPRING_DATASOURCE_PASSWORD=taller \
MOTORSPORT19_SEGURIDAD_CLAVE_JWT=clave-solo-para-desarrollo-local-no-usar-fuera-1234 \
MOTORSPORT19_ADMIN_INICIAL_USERNAME=admin \
MOTORSPORT19_ADMIN_INICIAL_PASSWORD=admin1234 \
    ./mvnw -o spring-boot:run > "$REGISTRO" 2>&1 &
PID=$!

for _ in $(seq 1 60); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' \
            -X POST "http://localhost:$PUERTO/api/auth/login" \
            -H 'Content-Type: application/json' \
            -d '{"username":"admin","password":"admin1234"}' 2>/dev/null)" = "200" ]; then
        LISTO=1
        break
    fi
    sleep 3
done

if [ -z "${LISTO:-}" ]; then
    echo "El backend no ha arrancado. Ultimas lineas de $REGISTRO:"
    tail -30 "$REGISTRO"
    exit 1
fi

echo "==> Bateria"
cd "$RAIZ/pruebas/api"
python3 suite.py
