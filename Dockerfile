# syntax=docker/dockerfile:1

ARG BASE_IMAGE=eclipse-temurin:21-jre-alpine
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG NODE_IMAGE=node:20-alpine

# 1) Build frontend (Svelte) into Spring static path
FROM ${NODE_IMAGE} AS fe_builder
WORKDIR /workspace
# Copy entire repo so Vite outDir relative path resolves to src/main/resources/static/...
COPY . .
WORKDIR /workspace/frontend/email-client
RUN npm ci && npm run build

# 2) Build backend (Spring Boot) with Maven, including built frontend assets
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests dependency:go-offline
COPY src src
# Overlay built FE assets into resources before packaging so they are bundled in the JAR
COPY --from=fe_builder /workspace/src/main/resources/static/app/email-client /workspace/src/main/resources/static/app/email-client
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# 3) Runtime image
FROM ${BASE_IMAGE} as runtime
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
