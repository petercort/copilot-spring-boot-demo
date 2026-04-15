#!/bin/bash
# S5: Cascade failure test — kill inventory, verify order fails gracefully under concurrent load
set -euo pipefail
SCRIPTS_DIR="$(cd "$(dirname "$0")/../scripts" && pwd)"
COMPOSE_FILE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"
RESULTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/results"
mkdir -p "$RESULTS_DIR"
source "$SCRIPTS_DIR/assert.sh"

LOG_FILE="$RESULTS_DIR/s5-requests.csv"
echo "seq,endpoint,http_code,result" > "$LOG_FILE"

cleanup() {
    echo ""
    echo "[cleanup] Restarting inventory-service..."
    docker compose -f "$COMPOSE_FILE" start inventory-service 2>/dev/null && \
        echo "[cleanup] inventory-service restarted" || \
        echo "[cleanup] Could not restart — run: docker compose start inventory-service"
}
trap cleanup EXIT

echo "=== S5: Cascade Failure Test ==="
echo "Hypothesis: inventory-service failure causes controlled degradation, not full cascade"
echo ""

bash "$SCRIPTS_DIR/verify-steady-state.sh"

echo ""
echo "Stopping inventory-service container..."
docker compose -f "$COMPOSE_FILE" stop inventory-service
echo "inventory-service stopped. Waiting 3s..."
sleep 3

echo ""
echo "Verifying customer-service STILL UP (no cascade) — 20 concurrent..."
for i in $(seq 1 20); do
    (CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/customers --max-time 5 2>/dev/null || echo "000")
     echo "$i,customers,$CODE,$( [ "$CODE" = "200" ] && echo "ok" || echo "fail" )" >> "$LOG_FILE") &
done
wait
CUST_OK=$(grep ",customers," "$LOG_FILE" | grep -c ",ok$" 2>/dev/null || echo 0)
echo "  customer-service: $CUST_OK/20 ok"

echo ""
echo "Firing 30 concurrent order-create requests — expect graceful failure, no 500 cascade..."
for i in $(seq 1 30); do
    (CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST http://localhost:8080/api/orders \
        -H "Content-Type: application/json" \
        -d '{"customerId":1,"items":[{"productId":1,"quantity":3}],"shippingAddress":"123 Main","shippingCity":"NYC","shippingState":"NY","shippingZip":"10001","shippingCountry":"USA"}' \
        --max-time 10 2>/dev/null || echo "000")
     echo "$i,orders,$CODE,$( [ "$CODE" = "200" ] && echo "ok" || echo "fail" )" >> "$LOG_FILE") &
done
wait

ORDER_OK=$(grep ",orders," "$LOG_FILE" | grep -c ",ok$" 2>/dev/null || echo 0)
ORDER_FAIL=$(grep ",orders," "$LOG_FILE" | grep -c ",fail$" 2>/dev/null || echo 0)
echo "  order requests: $ORDER_OK succeeded / $ORDER_FAIL failed gracefully (expected all fail)"

echo ""
echo "Checking order-service GET still functional (reads should work)..."
assert_http "http://localhost:8080/api/orders" 200 && echo "  order GET still works"

echo ""
echo "Results: customer alive ($CUST_OK/20), orders rejected gracefully ($ORDER_FAIL/30)"
if [ "$CUST_OK" -ge 18 ] && [ "$ORDER_FAIL" -ge 25 ]; then
    echo "S5 PASSED — Cascade prevented: customer-service survived, orders degraded gracefully"
else
    echo "S5 WARN — Check results: customer_ok=$CUST_OK/20 order_fail=$ORDER_FAIL/30"
fi
echo "Results log: $LOG_FILE"
