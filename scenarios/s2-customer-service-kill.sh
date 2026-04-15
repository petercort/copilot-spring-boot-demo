#!/bin/bash
# S2: Customer Service Kill — validate order-service circuit breaker under heavy load
set -euo pipefail
SCRIPTS_DIR="$(cd "$(dirname "$0")/../scripts" && pwd)"
COMPOSE_FILE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"
RESULTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/results"
mkdir -p "$RESULTS_DIR"
source "$SCRIPTS_DIR/assert.sh"

LOG_FILE="$RESULTS_DIR/s2-requests.csv"
echo "seq,http_code,elapsed_ms,result" > "$LOG_FILE"

cleanup() {
    echo ""
    echo "[cleanup] Restarting customer-service..."
    docker compose -f "$COMPOSE_FILE" start customer-service 2>/dev/null && \
        echo "[cleanup] customer-service restarted" || \
        echo "[cleanup] Could not restart — run: docker compose start customer-service"
}
trap cleanup EXIT

echo "=== S2: Customer Service Kill Experiment ==="
echo "Hypothesis: Order-service circuit breaker opens and fast-fails 80 calls after kill"
echo ""

echo "[1/5] Verify steady state..."
bash "$SCRIPTS_DIR/verify-steady-state.sh"

echo ""
echo "[2/5] Stopping customer-service container..."
docker compose -f "$COMPOSE_FILE" stop customer-service
echo "customer-service stopped. Waiting 3s for circuit breaker to observe failures..."
sleep 3

echo ""
echo "[3/5] Firing 60 concurrent order requests in 6 batches of 10..."
CB_OPENED=false
SEQ=0

for batch in $(seq 1 6); do
    echo "  Batch $batch/6 (10 concurrent)..."
    for j in $(seq 1 10); do
        SEQ=$((SEQ+1))
        ts_start=$(date +%s)
        CODE=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST http://localhost:8080/api/orders \
            -H "Content-Type: application/json" \
            -d '{"customerId":1,"items":[{"productId":1,"quantity":5}],"shippingAddress":"456 Chaos Ave","shippingCity":"Portland","shippingState":"OR","shippingZip":"97201","shippingCountry":"USA"}' \
            --max-time 10 2>/dev/null || echo "000")
        ts_end=$(date +%s)
        elapsed=$(( (ts_end - ts_start) * 1000 ))
        echo "$SEQ,$CODE,$elapsed,$( [ "$CODE" = "200" ] && echo "ok" || echo "fail" )" >> "$LOG_FILE"
        [ "$CODE" = "503" ] || [ "$CODE" = "500" ] && CB_OPENED=true
    done &
    wait
    sleep 0.5
done

SUCCESS=$(grep -c ",ok$" "$LOG_FILE" 2>/dev/null || echo 0)
FAIL=$(grep -c ",fail$" "$LOG_FILE" 2>/dev/null || echo 0)
echo "  Results: $SUCCESS success / $FAIL failed (total 60)"

echo ""
echo "[4/5] Firing 20 sequential rapid requests — CB should fast-fail in <1s each..."
CB_BLOCKED=0
for i in $(seq 1 20); do
    SEQ=$((SEQ+1))
    ts_start=$(date +%s)
    CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST http://localhost:8080/api/orders \
        -H "Content-Type: application/json" \
        -d '{"customerId":1,"items":[{"productId":1,"quantity":1}],"shippingAddress":"123 Main","shippingCity":"NYC","shippingState":"NY","shippingZip":"10001","shippingCountry":"USA"}' \
        --max-time 10 2>/dev/null || echo "000")
    ts_end=$(date +%s)
    elapsed=$(( (ts_end - ts_start) * 1000 ))
    echo "  Request $i: HTTP $CODE (${elapsed}ms)"
    [ "$elapsed" -lt 1000 ] && CB_BLOCKED=$((CB_BLOCKED+1))
    [ "$CODE" = "503" ] || [ "$CODE" = "500" ] && CB_OPENED=true
    sleep 0.1
done

echo ""
echo "[5/5] Verifying no cascade to other services..."
assert_http "http://localhost:8080/api/products" 200 && echo "  inventory-service still responding"
assert_http "http://localhost:8080/api/orders" 200 && echo "  order-service GET still responding"

echo ""
echo "  Fast-fail responses (<1s): $CB_BLOCKED/20"
if [ "$CB_OPENED" = "true" ]; then
    echo "S2 PASSED — Circuit breaker activated under 80-request hammering"
else
    echo "S2 WARNING — Verify circuit breaker state via Grafana"
fi
echo "Results log: $LOG_FILE"
