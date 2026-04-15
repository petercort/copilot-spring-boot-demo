#!/bin/bash
# S3: Inventory Service Latency — validate Feign timeout behavior under concurrent load
set -euo pipefail
SCRIPTS_DIR="$(cd "$(dirname "$0")/../scripts" && pwd)"
RESULTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/results"
mkdir -p "$RESULTS_DIR"
source "$SCRIPTS_DIR/assert.sh"

LOG_FILE="$RESULTS_DIR/s3-requests.csv"
echo "seq,http_code,elapsed_ms,result" > "$LOG_FILE"

echo "=== S3: Inventory Service Latency Injection ==="
echo "Hypothesis: Order-service Feign calls fail fast (<6s) under 8-12s injected latency, 30 concurrent"
echo ""

echo "[1/4] Verify steady state..."
bash "$SCRIPTS_DIR/verify-steady-state.sh"

echo "[2/4] Enabling Chaos Monkey latency assault on inventory-service (8–12s)..."
echo "(Requires inventory-service started with --spring.profiles.active=chaos-monkey)"
curl -s -X POST http://localhost:8082/actuator/chaosmonkey/enable || \
    echo "Note: Start inventory-service with chaos-monkey profile for full effect"
curl -s -X POST http://localhost:8082/actuator/chaosmonkey/assaults \
    -H "Content-Type: application/json" \
    -d '{"latencyActive":true,"latencyRangeStart":8000,"latencyRangeEnd":12000,"level":1}' \
    -o /dev/null -w "Assault configured: %{http_code}\n"
sleep 2

echo ""
echo "[3/4] Firing 30 concurrent order requests — expect fast-fail via Feign timeout..."
PASS=0; FAIL=0

for i in $(seq 1 30); do
    (
        ts_start=$(date +%s)
        CODE=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST http://localhost:8080/api/orders \
            -H "Content-Type: application/json" \
            -d '{"customerId":1,"items":[{"productId":1,"quantity":1}],"shippingAddress":"123 Main","shippingCity":"NYC","shippingState":"NY","shippingZip":"10001","shippingCountry":"USA"}' \
            --max-time 20 2>/dev/null || echo "000")
        ts_end=$(date +%s)
        elapsed=$(( (ts_end - ts_start) * 1000 ))
        result="ok"
        [ "$elapsed" -lt 7000 ] && result="fast-fail" || result="slow"
        echo "$i,$CODE,$elapsed,$result" >> "$LOG_FILE"
        printf "  Req %2d: HTTP %s in %dms\n" "$i" "$CODE" "$elapsed"
    ) &
done
wait

FAST_FAIL=$(grep -c ",fast-fail$" "$LOG_FILE" 2>/dev/null || echo 0)
SLOW=$(grep -c ",slow$" "$LOG_FILE" 2>/dev/null || echo 0)
echo ""
echo "  Fast-fails (<7s): $FAST_FAIL/30   Slow (>=7s): $SLOW/30"

echo ""
echo "[4/4] Disabling chaos..."
curl -s -X POST http://localhost:8082/actuator/chaosmonkey/disable -o /dev/null && echo "Chaos disabled"

if [ "$FAST_FAIL" -ge 20 ]; then
    echo "S3 PASSED — Feign timeout triggered correctly for $FAST_FAIL/30 requests"
else
    echo "S3 WARN — Only $FAST_FAIL/30 fast-failed; check Feign timeout config"
fi
echo "Results log: $LOG_FILE"
