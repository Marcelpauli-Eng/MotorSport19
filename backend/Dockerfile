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

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
