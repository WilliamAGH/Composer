#!/bin/bash

# Simple deployment script for Composer
echo "Building Composer application..."

# Prefer the orchestrated Make build (Vite → Maven) so assets land in the JAR
if command -v make &> /dev/null; then
    echo "Running make build (frontend then backend)..."
    make build
elif command -v mvn &> /dev/null; then
    echo "Fallback: building with Maven (backend only)..."
    (cd frontend/email-client && npm install && npm run build)
    mvn clean package -DskipTests
elif command -v ./mvnw &> /dev/null; then
    echo "Fallback: building with Maven wrapper (backend only)..."
    (cd frontend/email-client && npm install && npm run build)
    ./mvnw clean package -DskipTests
else
    echo "No build tool found. Please install Make and Maven."
    exit 1
fi

echo "Build complete! Check target/ for the JAR file."
echo "To run locally: java -jar target/*.jar"
