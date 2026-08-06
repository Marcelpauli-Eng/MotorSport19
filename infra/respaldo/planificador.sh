#!/bin/sh
#
# Planificador de copias.
#
# No se usa cron a proposito: dentro de un contenedor, cron se traga las
# variables de entorno y escribe en su propio fichero de log, con lo que cuando
# algo falla no te enteras. Con un bucle en el arranque, todo sale por la salida
# estandar y se ve con `docker compose logs respaldo`.

set -eu

HORA="${HORA_COPIA:-03}"
MINUTO="${MINUTO_COPIA:-30}"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

log "Planificador de copias en marcha. Copia diaria a las ${HORA}:${MINUTO} (${TZ:-UTC})."

# Copia al arrancar si se pide: sirve para comprobar la configuracion el dia
# del montaje, sin esperar a la madrugada.
if [ "${COPIA_AL_ARRANCAR:-false}" = "true" ]; then
  log "Copia inicial de comprobacion..."
  /usr/local/bin/respaldar || log "AVISO: la copia inicial ha fallado. Revisa la configuracion."
fi

while true; do
  AHORA=$(date +%s)
  # Proxima ejecucion: hoy a la hora indicada, o manana si ya ha pasado.
  OBJETIVO=$(date -d "today ${HORA}:${MINUTO}:00" +%s 2>/dev/null \
             || date -j -f "%H:%M:%S" "${HORA}:${MINUTO}:00" +%s)

  if [ "$OBJETIVO" -le "$AHORA" ]; then
    OBJETIVO=$((OBJETIVO + 86400))
  fi

  ESPERA=$((OBJETIVO - AHORA))
  log "Proxima copia en $((ESPERA / 3600))h $(((ESPERA % 3600) / 60))min."
  sleep "$ESPERA"

  # Si la copia falla, se registra y se sigue: un fallo puntual (la API caida,
  # internet cortado) no puede dejar el taller sin copias para siempre.
  /usr/local/bin/respaldar || log "AVISO: la copia de hoy ha fallado. Se reintentara manana."
done
