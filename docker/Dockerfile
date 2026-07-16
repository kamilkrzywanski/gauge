# ── Gauge Dockerfile ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the uber-jar
COPY target/gauge-*-runner.jar /app/app.jar

# Copy docker-specific properties
COPY docker/docker.application.properties /app/application.properties
ENV QUARKUS_CONFIG_LOCATIONS=/app/application.properties

# Create data directory for the H2 database
RUN mkdir -p /app/data

EXPOSE 8087

CMD ["java", "-jar", "/app/app.jar"]
