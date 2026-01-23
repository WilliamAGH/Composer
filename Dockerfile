ARG BASE_IMAGE=registry.access.redhat.com/ubi9/openjdk-21-runtime:latest@sha256:84a136ce036ebde3adc502c84c39f22bd4bf14ca387a3825cf3e1c7ae26c0942
ARG MAVEN_IMAGE=ghcr.io/carlossg/maven:3.9-eclipse-temurin-21@sha256:41d58c4b64bae45eaa1c1df58b3e97e066597d8270f8a3b9eefa0153e8db650a
ARG NODE_IMAGE=registry.access.redhat.com/ubi9/nodejs-20:latest@sha256:23aa2e84a94e5e11d2c716de12344bc6183b29f0fc0a440fde7b0f2ee3dc703c

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

# 2) Build backend (Spring Boot) with Maven, including built frontend assets
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests dependency:go-offline
COPY src src
# Overlay built FE assets into resources before packaging so they are bundled in the JAR
COPY --from=fe_builder /workspace/src/main/resources/static/app/email-client /workspace/src/main/resources/static/app/email-client
RUN --mount=type=cache,target=/root/.m2 \
    echo "Verifying frontend assets before Maven build..." && \
    ls -la /workspace/src/main/resources/static/app/email-client/ && \
    mvn -q -B -DskipTests package

# 3) Runtime image
FROM ${BASE_IMAGE} AS runtime
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""
# Copy application JAR
COPY --from=builder /workspace/target/*.jar /app/app.jar
# Copy static assets (also present inside the JAR) for optional external serving
COPY --from=builder /workspace/src/main/resources/static /app/static
# Copy bundled sample email data for the email client view
COPY data /app/data
EXPOSE 8080
ENTRYPOINT ["/bin/sh","-c","java ${JAVA_OPTS} -jar /app/app.jar"]
