# syntax=docker/dockerfile:1

# --- Build stage ---
FROM gradle:8.10-jdk17 AS builder
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src ./src

RUN gradle bootJar -x test --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
RUN mkdir -p /app/data/dem

COPY --from=builder /app/build/libs/*.jar /app/app.jar
COPY --chown=app:app python/data/raw/dem/nasadem/korea_dem.tif /app/data/dem/korea_dem.tif

RUN chown app:app /app/app.jar

ENV JAVA_OPTS="-Xmx400m -Xms200m -XX:+UseSerialGC"
ENV SPRING_PROFILES_ACTIVE=prod
ENV DEM_FILE_PATH=/app/data/dem/korea_dem.tif

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]