#!/bin/sh
#
# Restauracion de una copia de seguridad.
#
# Una copia que nunca se ha restaurado no es una copia: es una carpeta con
# ficheros. Este script existe para poder PROBAR la restauracion cada pocos
# meses sobre una base de datos de usar y tirar, no solo para el dia malo.
#
#   Uso:  restaurar <fichero.tar.enc> [nombre_base_destino]
#
# Si no se indica base destino, restaura sobre `motorsport19_prueba`, que es lo
# que se quiere para un simulacro. Para restaurar de verdad hay que escribir el
# nombre real a mano, a proposito: asi no se sobreescribe la base buena por un
# despiste.

set -eu

ORIGEN="${1:-}"
DESTINO="${2:-motorsport19_prueba}"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
fallo() { echo "ERROR: $*" >&2; exit 1; }

[ -n "$ORIGEN" ] || fallo "Uso: restaurar <fichero.tar.enc> [base_destino]"
[ -f "$ORIGEN" ] || fallo "No existe el fichero $ORIGEN"
[ -n "${CLAVE_CIFRADO:-}" ] || fallo "Falta CLAVE_CIFRADO."
[ -n "${PGPASSWORD:-}" ] || fallo "Falta PGPASSWORD."

TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$TEMPORAL"' EXIT

log "Descifrando $ORIGEN..."
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
  -in "$ORIGEN" \
  -out "${TEMPORAL}/copia.tar" \
  -pass env:CLAVE_CIFRADO \
  || fallo "No se ha podido descifrar. ¿Es correcta la CLAVE_CIFRADO?"

tar -xf "${TEMPORAL}/copia.tar" -C "$TEMPORAL"

VOLCADO="$(find "$TEMPORAL" -name '*.sql.gz' | head -1)"
[ -n "$VOLCADO" ] || fallo "La copia no contiene ningun volcado .sql.gz"

gzip -t "$VOLCADO" || fallo "El volcado esta corrupto."
log "Volcado verificado: $(basename "$VOLCADO")"

# Aviso claro antes de tocar nada.
if [ "$DESTINO" != "motorsport19_prueba" ]; then
  log "ATENCION: se va a SOBREESCRIBIR la base de datos '$DESTINO'."
  log "Pulsa Ctrl+C en los proximos 10 segundos para abortar."
  sleep 10
fi

log "Creando la base de datos '$DESTINO' si no existe..."
psql --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" \
     --dbname=postgres -tc \
     "SELECT 1 FROM pg_database WHERE datname='${DESTINO}'" | grep -q 1 \
  || psql --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" \
          --dbname=postgres -c "CREATE DATABASE ${DESTINO}"

log "Restaurando sobre '$DESTINO'..."
gunzip -c "$VOLCADO" | psql \
  --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" \
  --dbname="$DESTINO" --quiet --set ON_ERROR_STOP=on \
  || fallo "La restauracion ha fallado."

# --------------------------------------------------------------------------
# Verificacion: no basta con que el psql no haya dado error.
# --------------------------------------------------------------------------

log "Comprobando la integridad de lo restaurado..."

CONSULTA="SELECT
    (SELECT count(*) FROM factura)            AS facturas,
    (SELECT count(*) FROM orden_trabajo)      AS ordenes,
    (SELECT count(*) FROM cliente)            AS clientes,
    (SELECT count(*) FROM fn_verificar_cadena_facturas())    AS anomalias_cadena,
    (SELECT count(*) FROM fn_verificar_integridad_stock())   AS descuadres_stock;"

psql --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" \
     --dbname="$DESTINO" -c "$CONSULTA"

ANOMALIAS=$(psql --host="$PGHOST" --port="${PGPORT:-5432}" --username="$PGUSER" \
  --dbname="$DESTINO" -tAc "SELECT count(*) FROM fn_verificar_cadena_facturas()")

if [ "$ANOMALIAS" -eq 0 ]; then
  log "OK: la cadena de huellas de la facturacion esta integra tras restaurar."
else
  fallo "La cadena de facturacion tiene ${ANOMALIAS} anomalia(s) tras restaurar."
fi

log "Restauracion terminada sobre '$DESTINO'."
