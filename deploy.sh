#!/bin/bash

# Simple deployment script for Composer
echo "Building Composer application..."

# Prefer the orchestrated Make build (Vite → Gradle) so assets land in the JAR
if command -v make &> /dev/null; then
    echo "Running make build (frontend then backend)..."
    make build
else
    echo "Fallback: building with Gradle..."
    (cd frontend/email-client && npm install && npm run build)
    ./gradlew bootJar -x test
fi

echo "Build complete! Check build/libs/ for the JAR file."
echo "To run locally: java -jar build/libs/*.jar"
