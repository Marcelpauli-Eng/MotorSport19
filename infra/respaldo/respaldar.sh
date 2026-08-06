#!/bin/sh
#
# Copia de seguridad de MotorSport19.
#
# Hace tres cosas, y las tres importan:
#
#   1. Volcado completo de la base de datos (pg_dump).
#   2. Exportacion JSON del libro de facturas. Es redundante a proposito: ese
#      fichero incluye las cadenas de huellas, asi que permite demostrar la
#      integridad de la facturacion aunque el volcado se corrompiera o no
#      hubiera manera de restaurarlo.
#   3. Cifrado y subida fuera del taller. Una copia en el mismo local no
#      protege de un incendio ni de un robo.
#
# Cada paso se VERIFICA. Una copia de cero bytes que nadie mira es peor que no
# tener copia, porque da una sensacion de seguridad que no existe.

set -eu

DIRECTORIO="${DIRECTORIO_COPIAS:-/copias}"
DIAS_LOCALES="${DIAS_RETENCION_LOCAL:-7}"
FECHA="$(date +%Y-%m-%d_%H%M)"
BASE="motorsport19_${FECHA}"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }
fallo() { log "ERROR: $*"; exit 1; }

# --------------------------------------------------------------------------
# Comprobaciones previas
# --------------------------------------------------------------------------

[ -n "${PGPASSWORD:-}" ] || fallo "Falta PGPASSWORD."
[ -n "${CLAVE_CIFRADO:-}" ] || fallo "Falta CLAVE_CIFRADO: no se hacen copias sin cifrar."

mkdir -p "$DIRECTORIO"

log "=== Copia de seguridad $FECHA ==="

# --------------------------------------------------------------------------
# 1. Volcado de la base de datos
# --------------------------------------------------------------------------

VOLCADO="${DIRECTORIO}/${BASE}.sql.gz"

log "Volcando la base de datos ${PGDATABASE}@${PGHOST}..."
pg_dump \
  --host="$PGHOST" \
  --port="${PGPORT:-5432}" \
  --username="$PGUSER" \
  --dbname="$PGDATABASE" \
  --no-owner \
  --no-privileges \
  --clean \
  --if-exists \
  | gzip -9 > "$VOLCADO" \
  || fallo "pg_dump ha fallado."

# Verificacion 1: el gzip tiene que estar integro.
gzip -t "$VOLCADO" || fallo "El volcado esta corrupto (gzip -t)."

# Verificacion 2: tiene que contener las tablas que esperamos. Un volcado de
# una base vacia tambien es un gzip valido, y no serviria de nada.
for tabla in factura linea_factura orden_trabajo cliente movimiento_stock; do
  gunzip -c "$VOLCADO" | grep -q "CREATE TABLE public.${tabla}" \
    || fallo "El volcado no contiene la tabla ${tabla}."
done

TAMANO=$(du -h "$VOLCADO" | cut -f1)
log "Volcado correcto: ${TAMANO}"

# --------------------------------------------------------------------------
# 2. Libro de facturas en JSON (verificable por si solo)
# --------------------------------------------------------------------------

LIBRO="${DIRECTORIO}/${BASE}_libro-facturas.json"

# Se saca de la base de datos, no de la API. Desde que hay autenticacion, pedirlo
# a la API exigiria guardar aqui las credenciales de un administrador, y ademas
# la copia dejaria de hacerse si la API estuviera caida. Este script ya tiene
# acceso a la base para el pg_dump, asi que es de donde conviene leerlo.
log "Exportando el libro de facturas..."

psql \
  --host="$PGHOST" \
  --port="${PGPORT:-5432}" \
  --username="$PGUSER" \
  --dbname="$PGDATABASE" \
  --quiet --no-align --tuples-only \
  --file=/dev/stdin > "$LIBRO" <<'SQL' || { log "AVISO: no se ha podido exportar el libro de facturas."; rm -f "$LIBRO"; }
SELECT json_build_object(
    'generado', now(),
    'facturas_exportadas', (SELECT COUNT(*) FROM factura),
    'algoritmo_huella', 'SHA-256',
    'facturas', COALESCE((
        SELECT json_agg(f ORDER BY f.posicion_registro)
          FROM (
            SELECT fa.numero_registro AS posicion_registro,
                   fa.numero_completo AS numero,
                   fa.tipo,
                   fa.fecha_emision,
                   fa.fecha_operacion,
                   fa.timestamp_emision,
                   fa.emisor_razon_social,
                   fa.emisor_nif,
                   fa.receptor_nombre,
                   fa.receptor_nif,
                   fa.codigo_ot,
                   fa.matricula,
                   fa.base_imponible,
                   fa.total_iva,
                   fa.total,
                   fa.huella_anterior,
                   fa.huella,
                   fa.cadena_huella,
                   fa.algoritmo_huella,
                   (SELECT json_agg(l ORDER BY l.numero_linea)
                      FROM (SELECT numero_linea, tipo, descripcion, pieza_sku,
                                   cantidad, precio_unitario, descuento_pct,
                                   porcentaje_iva, base_imponible, cuota_iva, total
                              FROM linea_factura
                             WHERE factura_id = fa.id) l) AS lineas
              FROM factura fa
          ) f), '[]'::json)
);
SQL

if [ -s "$LIBRO" ]; then
  # Verificacion: tiene que traer las cadenas de huellas, que es lo que permite
  # comprobar el libro sin este programa.
  if grep -q '"cadena_huella"' "$LIBRO"; then
    log "Libro de facturas exportado: $(du -h "$LIBRO" | cut -f1)"
  else
    log "AVISO: el libro exportado no trae cadenas de huellas. Se sigue igualmente."
  fi
else
  rm -f "$LIBRO"
fi

# --------------------------------------------------------------------------
# 3. Cifrado
# --------------------------------------------------------------------------

PAQUETE="${DIRECTORIO}/${BASE}.tar"
CIFRADO="${PAQUETE}.enc"

tar -cf "$PAQUETE" -C "$DIRECTORIO" "$(basename "$VOLCADO")" \
  $([ -f "$LIBRO" ] && echo "$(basename "$LIBRO")")

# AES-256 con derivacion de clave PBKDF2. Se puede descifrar desde cualquier
# maquina con openssl, sin depender de este contenedor ni de este proyecto.
openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt \
  -in "$PAQUETE" \
  -out "$CIFRADO" \
  -pass env:CLAVE_CIFRADO \
  || fallo "El cifrado ha fallado."

# Verificacion: se descifra a la nada para comprobar que la clave funciona.
# Sin esto, un error en la clave se descubriria el dia que hiciera falta.
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
  -in "$CIFRADO" -pass env:CLAVE_CIFRADO -out /dev/null \
  || fallo "La copia cifrada no se puede descifrar con la clave actual."

rm -f "$PAQUETE" "$VOLCADO" "$LIBRO"
log "Cifrado y verificado: $(du -h "$CIFRADO" | cut -f1)"

# --------------------------------------------------------------------------
# 4. Subida fuera del taller
# --------------------------------------------------------------------------

if [ -n "${RCLONE_DESTINO:-}" ]; then
  log "Subiendo a ${RCLONE_DESTINO}..."
  if rclone copy "$CIFRADO" "$RCLONE_DESTINO" --stats-one-line; then
    # Verificacion: comprobar que esta de verdad al otro lado.
    if rclone lsf "$RCLONE_DESTINO" | grep -q "$(basename "$CIFRADO")"; then
      log "Subida confirmada."
    else
      fallo "La subida dice que fue bien pero el fichero no aparece en el destino."
    fi
  else
    fallo "No se ha podido subir la copia. Queda la copia local en $CIFRADO"
  fi
else
  log "AVISO: sin RCLONE_DESTINO configurado. La copia SOLO existe en el taller."
  log "       Un incendio o un robo se la llevaria junto con el servidor."
fi

# --------------------------------------------------------------------------
# 5. Limpieza de copias locales antiguas
# --------------------------------------------------------------------------

log "Borrando copias locales de mas de ${DIAS_LOCALES} dias..."
find "$DIRECTORIO" -name "motorsport19_*.tar.enc" -type f -mtime "+${DIAS_LOCALES}" -delete

QUEDAN=$(find "$DIRECTORIO" -name "motorsport19_*.tar.enc" -type f | wc -l)
log "=== Copia terminada. ${QUEDAN} copia(s) local(es). ==="
