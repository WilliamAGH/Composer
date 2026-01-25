ARG BASE_IMAGE=eclipse-temurin:25-jre
ARG BUILD_IMAGE=eclipse-temurin:25-jdk
ARG NODE_IMAGE=registry.access.redhat.com/ubi9/nodejs-20:latest

# 1) Build frontend (Svelte) into Spring static path
FROM ${NODE_IMAGE} AS fe_builder
USER root
WORKDIR /workspace
# Copy entire repo so Vite outDir relative path resolves to src/main/resources/static/...
COPY . .
WORKDIR /workspace/frontend/email-client
RUN npm ci && npm run build && \
    echo "Verifying frontend build output..." && \
    if [ ! -d ../../src/main/resources/static/app/email-client ]; then \
        echo "ERROR: Frontend build output directory missing - check npm build logs"; \
        exit 1; \
    elif [ ! -f ../../src/main/resources/static/app/email-client/email-client.js ]; then \
        echo "ERROR: Frontend build succeeded but output path mismatch - email-client.js not found in expected directory"; \
        exit 1; \
    else \
        ls -la ../../src/main/resources/static/app/email-client/; \
    fi

# 2) Build backend (Spring Boot) with Gradle, including built frontend assets
FROM ${BUILD_IMAGE} AS builder
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
# Grant execution rights to gradlew
RUN chmod +x gradlew
# Download dependencies (this layer will be cached if gradle files don't change)
RUN ./gradlew dependencies --no-daemon

COPY src src
# Overlay built FE assets into resources before packaging so they are bundled in the JAR
COPY --from=fe_builder /workspace/src/main/resources/static/app/email-client /workspace/src/main/resources/static/app/email-client
RUN echo "Verifying frontend assets before Gradle build..." && \
    ls -la /workspace/src/main/resources/static/app/email-client/ && \
    ./gradlew bootJar --no-daemon -x test

# 3) Runtime image
FROM ${BASE_IMAGE} AS runtime
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""
# Copy application JAR
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
# Copy static assets (also present inside the JAR) for optional external serving
COPY --from=builder /workspace/src/main/resources/static /app/static
# Copy bundled sample email data for the email client view
COPY data /app/data
EXPOSE 8080
ENTRYPOINT ["/bin/sh","-c","java ${JAVA_OPTS} -jar /app/app.jar"]
