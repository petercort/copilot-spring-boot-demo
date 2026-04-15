#!/usr/bin/env bash
# Live health dashboard for chaos testing
# Usage: ./scripts/watch-chaos.sh
# Shows real-time health status of all services, refreshes every 2s

SERVICES=(
  "eureka-server:http://localhost:8761/actuator/health"
  "api-gateway:http://localhost:8080/actuator/health"
  "customer-service:http://localhost:8081/actuator/health"
  "inventory-service:http://localhost:8082/actuator/health"
  "order-service:http://localhost:8083/actuator/health"
)

while true; do
  clear
  echo "=== Chaos Monitor $(date '+%H:%M:%S') === (Ctrl+C to stop)"
  echo ""
  for entry in "${SERVICES[@]}"; do
    name="${entry%%:http*}"
    url="${entry#*:http}"
    url="http${url}"
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$url" 2>/dev/null || echo "000")
    if [ "$response" = "200" ]; then
      status="\033[32m UP ($response)\033[0m"
    elif [ "$response" = "000" ]; then
      status="\033[31m DOWN (timeout)\033[0m"
    else
      status="\033[33m DEGRADED ($response)\033[0m"
    fi
    printf "  %-20s %b\n" "$name" "$status"
  done
  echo ""
  echo "  Dashboards:"
  echo "    Logs:    http://localhost:9999  (Dozzle)"
  echo "    Uptime:  http://localhost:3001  (Uptime Kuma)"
  echo "    Metrics: http://localhost:3000  (Grafana)"
  echo "    Scraper: http://localhost:9090  (Prometheus)"
  sleep 2
done
