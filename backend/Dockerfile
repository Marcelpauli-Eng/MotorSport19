# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copiamos primero el pom para aprovechar la cache de capas de Docker:
# si no cambian las dependencias, no se vuelven a descargar.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- Etapa 2: ejecucion ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# wget lo usa el healthcheck de docker-compose
RUN apk add --no-cache wget tzdata \
    && addgroup -S taller && adduser -S taller -G taller

ENV TZ=Europe/Madrid
COPY --from=build /build/target/taller-*.jar app.jar
RUN chown taller:taller /app/app.jar

USER taller
EXPOSE 8080

# Ajustes pensados para contenedores pequenos (el plan gratuito de Render son
# 512 MB). Con el 75% por defecto, el heap se come 384 MB y entre metaspace,
# hilos y la generacion de PDF el contenedor se pasa de 512 MB y lo matan.
#
#   MaxRAMPercentage=55  deja sitio para todo lo que la JVM usa fuera del heap.
#   SerialGC             gasta bastante menos memoria que G1 en heaps pequenos,
#                        y con un solo usuario a la vez no se nota en pausas.
#   MaxMetaspaceSize     evita que Spring/Hibernate lo hagan crecer sin limite.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=55", \
  "-XX:+UseSerialGC", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "/app/app.jar"]
