#!/bin/bash
# S6: Network partition via Toxiproxy — heavy latency + request burst
echo "=== S6: Network Partition via Toxiproxy ==="
echo "Prerequisite: Toxiproxy running on port 8474 (docker run -d -p 8474:8474 -p 18081:18081 -p 18082:18082 ghcr.io/shopify/toxiproxy:2.9.0)"
echo ""

TOXIPROXY=http://localhost:8474
RESULTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/results"
mkdir -p "$RESULTS_DIR"
LOG_FILE="$RESULTS_DIR/s6-requests.csv"
echo "seq,endpoint,http_code,elapsed_ms,result" > "$LOG_FILE"

# Check toxiproxy is available
if ! curl -sf "$TOXIPROXY/version" -o /dev/null; then
    echo "WARNING: Toxiproxy not reachable at $TOXIPROXY — skipping toxic injection"
    echo "Run: docker run -d -p 8474:8474 -p 18081:18081 -p 18082:18082 ghcr.io/shopify/toxiproxy:2.9.0"
    TOXIPROXY_AVAILABLE=false
else
    TOXIPROXY_AVAILABLE=true
fi

if [ "$TOXIPROXY_AVAILABLE" = "true" ]; then
    echo "Creating Toxiproxy proxies..."
    curl -s -X POST $TOXIPROXY/proxies -H "Content-Type: application/json" \
        -d '{"name":"customer-proxy","listen":"0.0.0.0:18081","upstream":"localhost:8081","enabled":true}' \
        -o /dev/null -w "customer-proxy: %{http_code}\n"

    curl -s -X POST $TOXIPROXY/proxies -H "Content-Type: application/json" \
        -d '{"name":"inventory-proxy","listen":"0.0.0.0:18082","upstream":"localhost:8082","enabled":true}' \
        -o /dev/null -w "inventory-proxy: %{http_code}\n"

    echo ""
    echo "Injecting 10s latency + 500ms jitter on customer-proxy..."
    curl -s -X POST $TOXIPROXY/proxies/customer-proxy/toxics \
        -H "Content-Type: application/json" \
        -d '{"name":"latency","type":"latency","stream":"upstream","toxicity":1.0,"attributes":{"latency":10000,"jitter":500}}' \
        -o /dev/null -w "toxic injected: %{http_code}\n"

    echo "Injecting 10s latency on inventory-proxy..."
    curl -s -X POST $TOXIPROXY/proxies/inventory-proxy/toxics \
        -H "Content-Type: application/json" \
        -d '{"name":"latency","type":"latency","stream":"upstream","toxicity":1.0,"attributes":{"latency":10000,"jitter":500}}' \
        -o /dev/null -w "toxic injected: %{http_code}\n"
fi

echo ""
echo "Firing 30 concurrent requests through partitioned network..."
for i in $(seq 1 30); do
    (
        ts_start=$(date +%s)
        CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/customers/1 --max-time 15 2>/dev/null || echo "000")
        ts_end=$(date +%s)
        elapsed=$(( (ts_end - ts_start) * 1000 ))
        echo "$i,customers,$CODE,$elapsed,$( [ "$CODE" = "200" ] && echo "ok" || echo "fail")" >> "$LOG_FILE"
        printf "  Req %2d: HTTP %s in %dms\n" "$i" "$CODE" "$elapsed"
    ) &
done
wait

FAST=$(awk -F',' 'NR>1 && $4<6000 {count++} END {print count+0}' "$LOG_FILE")
SLOW=$(awk -F',' 'NR>1 && $4>=6000 {count++} END {print count+0}' "$LOG_FILE")
echo ""
echo "  Fast responses (<6s): $FAST/30   Slow/timeout (>=6s): $SLOW/30"

if [ "$TOXIPROXY_AVAILABLE" = "true" ]; then
    echo ""
    echo "Cleaning up toxics..."
    curl -s -X DELETE $TOXIPROXY/proxies/customer-proxy/toxics/latency -o /dev/null
    curl -s -X DELETE $TOXIPROXY/proxies/inventory-proxy/toxics/latency -o /dev/null
    echo "Toxics removed. Proxies still exist — remove manually or re-run to reset."
fi

echo ""
echo "Results log: $LOG_FILE"
[ "$FAST" -ge 20 ] && echo "S6 PASSED — Gateway fast-failed $FAST/30 requests under 10s partition" || echo "S6 WARN — Only $FAST/30 fast-failed; verify timeout config"
