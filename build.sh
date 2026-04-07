#!/bin/bash

# Build script for E-Commerce Microservices
# Builds all modules (eureka-server, api-gateway, customer-service,
# inventory-service, order-service) from the parent POM.
#
# IMPORTANT: Homebrew JDK 25 is incompatible with Lombok 1.18.x annotation
# processing. This script uses Temurin JDK 21 automatically.

echo "Building E-Commerce Microservices..."
echo "====================================="

# Prefer Temurin 21; fall back to Temurin 17
BUILD_JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
if [ -z "$BUILD_JAVA_HOME" ]; then
    BUILD_JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)
fi

if [ -z "$BUILD_JAVA_HOME" ]; then
    echo "ERROR: Neither Temurin 21 nor Temurin 17 found."
    echo "Install via: brew install --cask temurin@21"
    exit 1
fi

echo "Using Java: $BUILD_JAVA_HOME"
echo ""

JAVA_HOME=$BUILD_JAVA_HOME mvn clean install -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful!"
    echo ""
    echo "To run all services:"
    echo "  ./run.sh"
    echo ""
    echo "Or manually:"
    echo "  JAVA_HOME=$JAVA_17_HOME mvn spring-boot:run"
else
    echo ""
    echo "❌ Build failed!"
    exit 1
fi
