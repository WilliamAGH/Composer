#!/bin/bash

# Simple deployment script for ComposerAI
echo "Building ComposerAI application..."

# Check if we have Maven
if command -v mvn &> /dev/null; then
    echo "Building with Maven..."
    mvn clean package -DskipTests
elif command -v ./mvnw &> /dev/null; then
    echo "Building with Maven wrapper..."
    ./mvnw clean package -DskipTests
elif command -v gradle &> /dev/null; then
    echo "Building with Gradle..."
    gradle build
elif command -v ./gradlew &> /dev/null; then
    echo "Building with Gradle wrapper..."
    ./gradlew build
else
    echo "No build tool found. Please install Maven or Gradle."
    exit 1
fi

echo "Build complete! Check target/ or build/ directory for JAR file."
echo "To run locally: java -jar target/*.jar or java -jar build/libs/*.jar"