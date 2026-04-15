#!/bin/bash
# Kills and restarts a named service
SERVICE=${1:?Usage: restart-service.sh <service-name>}
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Restarting $SERVICE..."
PID=$(pgrep -f "spring-boot:run.*${SERVICE}" 2>/dev/null || echo "")
if [ -n "$PID" ]; then
    kill "$PID" && echo "Killed PID $PID"
    sleep 2
fi

BUILD_JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
export JAVA_HOME=$BUILD_JAVA_HOME
LOG="${ROOT}/${SERVICE}-restart.log"
(cd "$ROOT/$SERVICE" && JAVA_HOME=$BUILD_JAVA_HOME mvn spring-boot:run -q > "$LOG" 2>&1) &
echo "Started $SERVICE (PID $!), log: $LOG"
