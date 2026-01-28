##
## Multi-stage build for ComposerAI (Svelte frontend + Spring Boot backend)
## Note: Requires DOCKER_BUILDKIT=1 for cache mount support
##
ARG BASE_IMAGE=public.ecr.aws/docker/library/eclipse-temurin:25-jre
ARG BUILD_IMAGE=public.ecr.aws/docker/library/eclipse-temurin:25-jdk
ARG NODE_IMAGE=public.ecr.aws/docker/library/node:22.17.0-alpine

# ================================
# 1) FRONTEND BUILD STAGE (Svelte)
# ================================
FROM ${NODE_IMAGE} AS fe_builder
USER root
WORKDIR /workspace

# Upgrade npm to v11 for lockfileVersion 3 compatibility (separate layer, no cache mount)
RUN npm install -g npm@11

# Copy package files for npm cache layer
COPY frontend/email-client/package*.json frontend/email-client/

# Install dependencies with cache mount (npm 11 is now fully available)
RUN --mount=type=cache,target=/root/.npm \
    cd frontend/email-client && npm ci

# Copy entire repo so Vite outDir relative path resolves to src/main/resources/static/...
COPY . .
WORKDIR /workspace/frontend/email-client

# Build frontend
RUN npm run build && \
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

# ================================
# 2) BACKEND BUILD STAGE (Spring Boot)
# ================================
FROM ${BUILD_IMAGE} AS builder
WORKDIR /workspace

# 1. Gradle wrapper (rarely changes)
COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew

# 2. Build configuration (changes occasionally)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# 3. Download dependencies with cache mount
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --quiet

# 4. Copy source and frontend assets
COPY src src
COPY --from=fe_builder /workspace/src/main/resources/static/app/email-client /workspace/src/main/resources/static/app/email-client

# 5. Build JAR with cache mount
RUN --mount=type=cache,target=/root/.gradle \
    echo "Verifying frontend assets before Gradle build..." && \
    ls -la /workspace/src/main/resources/static/app/email-client/ && \
    ./gradlew bootJar --no-daemon -x test

# ================================
# 3) RUNTIME STAGE
# ================================
FROM ${BASE_IMAGE} AS runtime

# 1. System packages (never changes) - FIRST for maximum cache reuse
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 2. Create non-root user (never changes)
RUN groupadd --system app \
    && useradd --uid 10001 --gid app --home-dir /app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app

# 3. Static data (rarely changes)
COPY data /app/data

# 4. Environment (rarely changes)
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8090
ENV JAVA_OPTS=""

# 5. Application JAR (changes every build) - LAST for optimal caching
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

# 6. Copy static assets (also present inside the JAR) for optional external serving
COPY --from=builder /workspace/src/main/resources/static /app/static

# 7. Finalize permissions
RUN chown -R app:app /app
USER app

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS --connect-timeout 2 --max-time 3 http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["/bin/sh","-c","java ${JAVA_OPTS} -jar /app/app.jar"]
