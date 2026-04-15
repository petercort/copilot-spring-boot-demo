#!/bin/bash
# Continuously sends requests and logs status + response time to CSV
OUTPUT=${1:-traffic-monitor.csv}
INTERVAL=${2:-1}
echo "timestamp,endpoint,http_code,response_time_ms" > "$OUTPUT"
echo "Monitoring traffic... Writing to $OUTPUT (Ctrl+C to stop)"
while true; do
    for endpoint in "http://localhost:8080/api/customers" "http://localhost:8080/api/orders"; do
        RESULT=$(curl -s -o /dev/null -w "%{http_code} %{time_total}" "$endpoint" --max-time 5 2>/dev/null || echo "000 0")
        CODE=$(echo "$RESULT" | cut -d' ' -f1)
        TIME_S=$(echo "$RESULT" | cut -d' ' -f2)
        ELAPSED=$(awk "BEGIN {printf \"%.0f\", $TIME_S * 1000}")
        echo "$(date -u +%Y-%m-%dT%H:%M:%SZ),$endpoint,$CODE,$ELAPSED" >> "$OUTPUT"
    done
    sleep "$INTERVAL"
done
