#!/bin/bash

# Build script for E-Commerce Monolith Demo
# This script ensures Java 17 is used for building the project

echo "🔨 Building E-Commerce Monolith..."
echo "================================="

# Find Java 17
JAVA_17_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)

if [ -z "$JAVA_17_HOME" ]; then
    echo "❌ Java 17 not found!"
    echo "Please install Java 17 (e.g., using Homebrew: brew install --cask temurin@17)"
    exit 1
fi

echo "✓ Using Java 17: $JAVA_17_HOME"
echo ""

# Build with Java 17
JAVA_HOME=$JAVA_17_HOME mvn clean install

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "To run the application:"
    echo "  ./run.sh"
    echo ""
    echo "Or manually:"
    echo "  JAVA_HOME=$JAVA_17_HOME mvn spring-boot:run"
else
    echo ""
    echo "❌ Build failed!"
    exit 1
fi
