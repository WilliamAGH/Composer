# syntax=docker/dockerfile:1

ARG BASE_IMAGE=eclipse-temurin:17-jre
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17

FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM ${BASE_IMAGE} as runtime
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""
# Copy application JAR
COPY --from=builder /workspace/target/*.jar /app/app.jar
# Copy static assets for production (will be served from /api/*)
COPY Code /app/static
EXPOSE 8080
ENTRYPOINT ["/bin/sh","-c","java ${JAVA_OPTS} -jar /app/app.jar"]
