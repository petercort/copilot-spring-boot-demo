#!/bin/bash
# Verifies all 5 services are healthy before/after chaos experiments
set -euo pipefail
source "$(dirname "$0")/assert.sh"

echo "=== Steady-State Verification ==="
echo ""

PASS=0
FAIL=0

check() {
    if "$@"; then
        PASS=$((PASS+1))
    else
        FAIL=$((FAIL+1))
    fi
}

check assert_service_up "eureka-server"    8761
check assert_service_up "api-gateway"      8080
check assert_service_up "customer-service" 8081
check assert_service_up "inventory-service" 8082
check assert_service_up "order-service"    8083

check assert_http "http://localhost:8080/api/customers" 200
check assert_http "http://localhost:8080/api/products" 200
check assert_http "http://localhost:8080/api/orders" 200

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] && echo "✅ Steady state CONFIRMED" || echo "❌ Steady state FAILED"
exit $FAIL
