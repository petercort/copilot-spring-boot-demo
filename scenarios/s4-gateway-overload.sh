#!/bin/bash
# S4: API Gateway Overload — high concurrency flood test (3 waves)
RESULTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/results"
mkdir -p "$RESULTS_DIR"
LOG_FILE="$RESULTS_DIR/s4-requests.csv"
echo "wave,req,endpoint,http_code,result" > "$LOG_FILE"

echo "=== S4: API Gateway Overload ==="
echo "Firing 3 waves of 100 concurrent requests each (300 total) across GET + POST endpoints"
echo ""

run_wave() {
    local wave="$1"
    local pass=0 fail=0
    echo "  Wave $wave: 100 concurrent mixed GET/POST requests..."

    for i in $(seq 1 50); do
        (CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/customers --max-time 5 2>/dev/null || echo "000")
         echo "$wave,$i,customers,$CODE,$( [ "$CODE" = "200" ] && echo "ok" || echo "fail" )" >> "$LOG_FILE") &
    done

    for i in $(seq 1 30); do
        (CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/products --max-time 5 2>/dev/null || echo "000")
         echo "$wave,$i,products,$CODE,$( [ "$CODE" = "200" ] && echo "ok" || echo "fail" )" >> "$LOG_FILE") &
    done

    for i in $(seq 1 20); do
        (CODE=$(curl -s -o /dev/null -w "%{http_code}" \
             -X POST http://localhost:8080/api/orders \
             -H "Content-Type: application/json" \
             -d '{"customerId":1,"items":[{"productId":1,"quantity":2}],"shippingAddress":"99 Load St","shippingCity":"Austin","shippingState":"TX","shippingZip":"78701","shippingCountry":"USA"}' \
             --max-time 10 2>/dev/null || echo "000")
         echo "$wave,$i,orders,$CODE,$( [ "$CODE" = "200" ] && echo "ok" || echo "fail" )" >> "$LOG_FILE") &
    done

    wait
    pass=$(grep "^$wave," "$LOG_FILE" | grep -c ",ok$" 2>/dev/null || echo 0)
    fail=$(grep "^$wave," "$LOG_FILE" | grep -c ",fail$" 2>/dev/null || echo 0)
    echo "    Wave $wave done: $pass ok / $fail fail"
}

run_wave 1
sleep 2
run_wave 2
sleep 2
run_wave 3

TOTAL_PASS=$(grep -c ",ok$" "$LOG_FILE" 2>/dev/null || echo 0)
TOTAL_FAIL=$(grep -c ",fail$" "$LOG_FILE" 2>/dev/null || echo 0)
TOTAL=$((TOTAL_PASS + TOTAL_FAIL))
PCT_FAIL=$(( TOTAL > 0 ? TOTAL_FAIL * 100 / TOTAL : 100 ))

echo ""
echo "=== Results: $TOTAL_PASS ok / $TOTAL_FAIL fail out of $TOTAL requests ==="
echo "Error rate: ${PCT_FAIL}%"

if [ "$TOTAL_FAIL" -lt $((TOTAL / 10)) ]; then
    echo "S4 PASSED — <10% error rate under 300-request flood"
else
    echo "S4 DEGRADED — ${PCT_FAIL}% error rate under load"
fi
echo "Full results: $LOG_FILE"
