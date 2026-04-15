#!/bin/bash
# S1: Eureka Server Kill — validate registry cache survivability
set -euo pipefail
SCRIPTS_DIR="$(cd "$(dirname "$0")/../scripts" && pwd)"
RESULTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/results"
COMPOSE_FILE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"
mkdir -p "$RESULTS_DIR"
source "$SCRIPTS_DIR/assert.sh"

cleanup() {
    echo ""
    echo "[cleanup] Restarting Eureka server..."
    docker compose -f "$COMPOSE_FILE" start eureka-server 2>/dev/null && \
        echo "[cleanup] Eureka restarted" || \
        echo "[cleanup] Could not restart Eureka — run: docker compose start eureka-server"
    kill $MONITOR_PID 2>/dev/null || true
}
trap cleanup EXIT

PROBE_URLS=(
    "http://localhost:8080/api/customers"
    "http://localhost:8080/api/products"
    "http://localhost:8080/api/orders"
)

# Fire N concurrent requests to all endpoints; print pass/fail count
probe_burst() {
    local label="$1"
    local n="${2:-10}"
    local pass=0 fail=0

    for url in "${PROBE_URLS[@]}"; do
        for j in $(seq 1 "$n"); do
            CODE=$(curl -s -o /dev/null -w "%{http_code}" "$url" --max-time 5 2>/dev/null || echo "000")
            if [ "$CODE" = "200" ]; then
                pass=$((pass + 1))
            else
                fail=$((fail + 1))
            fi
        done
    done
    echo "  $label: $pass ok / $fail fail ($((pass + fail)) total)"
}

echo "=== S1: Eureka Server Kill Experiment ==="
echo "Hypothesis: Services continue serving traffic for ≥60s after Eureka kill under burst load"
echo ""

echo "[1/5] Verify steady state..."
bash "$SCRIPTS_DIR/verify-steady-state.sh"

echo ""
echo "[2/5] Start traffic monitor in background..."
bash "$SCRIPTS_DIR/traffic-monitor.sh" "$RESULTS_DIR/s1-traffic.csv" 2 &
MONITOR_PID=$!

echo "[3/5] Stopping Eureka server..."
if ! docker compose -f "$COMPOSE_FILE" stop eureka-server 2>/dev/null; then
    echo "ERROR: Could not stop eureka-server container"
    exit 1
fi
echo "Eureka container stopped"

echo "[4/5] Probing APIs with 15-req bursts at t+15s, t+30s, t+60s under Eureka outage..."
sleep 15
assert_http "http://localhost:8080/api/customers" 200 && probe_burst "t+15s" 15
sleep 15
assert_http "http://localhost:8080/api/customers" 200 && probe_burst "t+30s" 15
sleep 30
assert_http "http://localhost:8080/api/customers" 200 && probe_burst "t+60s (Eureka cache held)" 15

echo ""
echo "[5/5] Stopping monitor. Traffic log: $RESULTS_DIR/s1-traffic.csv"

echo ""
echo "=== S1 Complete. Review $RESULTS_DIR/s1-traffic.csv for full timeline. ==="
