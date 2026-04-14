FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copier les fichiers de configuration
COPY pom.xml .
COPY src/ src/

# Compiler et packager l'application
RUN mvn clean package -DskipTests

# Image de runtime
FROM eclipse-temurin:21-jre-alpine

# Installer les dépendances système requises par RocksDB
RUN apk add --no-cache libstdc++

WORKDIR /app

# Copier l'application packagée (quarkus-app contient quarkus-run.jar + runtime dependencies)
COPY --from=builder /app/target/quarkus-app /app

# Metadata
LABEL maintainer="Tuuuur Team"
LABEL description="Tuuuur Consumer – Kafka Consumers & Streams Analytics"

# Port
EXPOSE 8081

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=10 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/q/health || exit 1

# Démarrer l'application
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
