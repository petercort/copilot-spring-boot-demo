#!/bin/bash

# Run script for E-Commerce Monolith Demo
# This script ensures Java 17 is used for running the application

echo "🚀 Starting E-Commerce Monolith..."
echo "=================================="

# Find Java 17
JAVA_17_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)

if [ -z "$JAVA_17_HOME" ]; then
    echo "❌ Java 17 not found!"
    echo "Please install Java 17 (e.g., using Homebrew: brew install --cask temurin@17)"
    exit 1
fi

echo "✓ Using Java 17: $JAVA_17_HOME"
echo ""
echo "Application will be available at:"
echo "  • REST API: http://localhost:8080/api"
echo "  • H2 Console: http://localhost:8080/h2-console"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

# Run with Java 17
JAVA_HOME=$JAVA_17_HOME mvn spring-boot:run
