#!/bin/bash

# Run script for E-Commerce Microservices
# Starts all five services in separate background processes.
# Stop all with: kill $(cat /tmp/ecommerce-pids.txt) 2>/dev/null
#
# Service ports:
#   8761 - Eureka Server       (service registry)
#   8080 - API Gateway         (single client-facing entry point)
#   8081 - Customer Service
#   8082 - Inventory Service
#   8083 - Order Service

echo "Starting E-Commerce Microservices..."
echo "======================================"

BUILD_JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
if [ -z "$BUILD_JAVA_HOME" ]; then
    BUILD_JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null)
fi

if [ -z "$BUILD_JAVA_HOME" ]; then
    echo "ERROR: Neither Temurin 21 nor Temurin 17 found."
    echo "Install via: brew install --cask temurin@21"
    exit 1
fi

export JAVA_HOME=$BUILD_JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

ROOT="$(cd "$(dirname "$0")" && pwd)"
PID_FILE=/tmp/ecommerce-pids.txt
> "$PID_FILE"

start_service() {
    local name=$1
    local dir=$2
    local log="/tmp/${name}.log"
    echo "Starting ${name} -> log: ${log}"
    (cd "$dir" && JAVA_HOME=$BUILD_JAVA_HOME mvn spring-boot:run -q > "$log" 2>&1) &
    echo $! >> "$PID_FILE"
}

start_service eureka-server  "$ROOT/eureka-server"
echo "Waiting 15s for Eureka to be ready..."
sleep 15

start_service customer-service  "$ROOT/customer-service"
start_service inventory-service "$ROOT/inventory-service"
start_service order-service     "$ROOT/order-service"
echo "Waiting 20s for services to register..."
sleep 20

start_service api-gateway "$ROOT/api-gateway"

echo ""
echo "All services started."
echo ""
echo "Endpoints (via API Gateway on :8080):"
echo "  GET  http://localhost:8080/api/customers"
echo "  GET  http://localhost:8080/api/products"
echo "  GET  http://localhost:8080/api/orders"
echo "  POST http://localhost:8080/api/orders"
echo ""
echo "Eureka Dashboard: http://localhost:8761"
echo ""
echo "To stop all services:"
echo "  kill \$(cat $PID_FILE)"
