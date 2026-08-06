# Servicio de copias de seguridad del taller.
#
# Imagen minima con lo justo: cliente de PostgreSQL para el volcado, openssl
# para cifrarlo y rclone para subirlo. Se elige la imagen de postgres:17-alpine
# como base para que la version de pg_dump coincida EXACTAMENTE con la del
# servidor: un pg_dump mas antiguo que el servidor se niega a trabajar.
FROM postgres:17-alpine

RUN apk add --no-cache \
      openssl \
      rclone \
      curl \
      tzdata \
      coreutils

ENV TZ=Europe/Madrid

COPY respaldar.sh /usr/local/bin/respaldar
COPY restaurar.sh /usr/local/bin/restaurar
COPY planificador.sh /usr/local/bin/planificador

RUN chmod +x /usr/local/bin/respaldar \
             /usr/local/bin/restaurar \
             /usr/local/bin/planificador

# Aqui se acumulan las copias locales antes de subirlas.
VOLUME ["/copias"]

ENTRYPOINT ["/usr/local/bin/planificador"]
