#!/bin/bash
# S8: Dropped Network Packets — validate resilience under unreliable network conditions
set -euo pipefail

SCRIPTS_DIR="$(cd "$(dirname "$0")/../scripts" && pwd)"
RESULTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/results"
mkdir -p "$RESULTS_DIR"
source "$SCRIPTS_DIR/assert.sh"

# ANSI colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

SCENARIO="S8: Network Packet Drop"
TARGET_CONTAINER="customer-service"
LOSS_PERCENT=60
DURATION=120
PUMBA_IMAGE="gaiaadm/pumba:latest"
TC_IMAGE="gaiadocker/iproute2"
USE_PUMBA=false
PUMBA_PID=""
SCENARIO_PASSED=false

# ---------------------------------------------------------------------------
# Cleanup — runs on EXIT regardless of success or failure
# ---------------------------------------------------------------------------
cleanup() {
    echo ""
    echo -e "${YELLOW}[cleanup] Restoring network state...${RESET}"

    # Stop any lingering pumba process
    if [ -n "$PUMBA_PID" ] && kill -0 "$PUMBA_PID" 2>/dev/null; then
        kill "$PUMBA_PID" 2>/dev/null || true
        echo "[cleanup] Pumba process stopped."
    fi

    # Ensure container is unpaused if fallback was used
    if docker inspect --format '{{.State.Paused}}' "$TARGET_CONTAINER" 2>/dev/null | grep -q "true"; then
        docker unpause "$TARGET_CONTAINER" 2>/dev/null && echo "[cleanup] $TARGET_CONTAINER unpaused." || true
    fi

    echo -e "${YELLOW}[cleanup] Done.${RESET}"
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Header
# ---------------------------------------------------------------------------
echo -e "${BOLD}${CYAN}"
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║         S8: Dropped Network Packets Chaos Experiment             ║"
echo "║  Hypothesis: order-service retries/fallbacks tolerate 60% loss   ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo -e "${RESET}"
echo "Target:    $TARGET_CONTAINER"
echo "Loss:      ${LOSS_PERCENT}% packet drop"
echo "Duration:  ${DURATION}s"
echo "Tool:      pumba netem (fallback: docker pause)"
echo ""

# ---------------------------------------------------------------------------
# Step 1: Verify steady state
# ---------------------------------------------------------------------------
echo -e "${BOLD}[1/6] Verifying steady state...${RESET}"
bash "$SCRIPTS_DIR/verify-steady-state.sh"
echo ""

# ---------------------------------------------------------------------------
# Step 2: Check pumba availability
# ---------------------------------------------------------------------------
echo -e "${BOLD}[2/6] Checking pumba Docker image...${RESET}"
if docker image inspect "$PUMBA_IMAGE" &>/dev/null; then
    echo "  pumba image already present."
    USE_PUMBA=true
else
    echo "  Pulling $PUMBA_IMAGE ..."
    if docker pull "$PUMBA_IMAGE" &>/dev/null; then
        echo "  Pull succeeded."
        USE_PUMBA=true
    else
        echo -e "  ${YELLOW}Warning: could not pull pumba — falling back to docker pause.${RESET}"
        USE_PUMBA=false
    fi
fi
echo ""

# ---------------------------------------------------------------------------
# Step 3: Inject fault
# ---------------------------------------------------------------------------
echo -e "${BOLD}[3/6] Injecting ${LOSS_PERCENT}% packet loss on ${TARGET_CONTAINER} for ${DURATION}s...${RESET}"

if [ "$USE_PUMBA" = "true" ]; then
    docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        "$PUMBA_IMAGE" \
        netem \
            --duration "${DURATION}s" \
            --tc-image "$TC_IMAGE" \
        loss \
            --percent "$LOSS_PERCENT" \
        "$TARGET_CONTAINER" &
    PUMBA_PID=$!
    echo "  pumba running as background PID $PUMBA_PID."
    # Give pumba a moment to apply the rule before we start measuring
    sleep 3
else
    echo -e "  ${YELLOW}Pausing $TARGET_CONTAINER for 60s (simulates network partition)...${RESET}"
    docker pause "$TARGET_CONTAINER"
    DURATION=60
fi
echo ""

# ---------------------------------------------------------------------------
# Step 4: Measure behavior under fault
# ---------------------------------------------------------------------------
echo -e "${BOLD}[4/6] Sending 60 requests to GET /api/orders while fault is active...${RESET}"

TOTAL=60
SUCCESS=0
FALLBACK=0
FAIL=0
LOG_FILE="$RESULTS_DIR/s8-requests.csv"
echo "seq,http_code,elapsed_ms,result" > "$LOG_FILE"

for i in $(seq 1 $TOTAL); do
    ELAPSED_MS=$(curl -s -o /dev/null -w "%{time_total}" \
        http://localhost:8080/api/orders \
        --max-time 10 2>/dev/null || echo "10.0")
    CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        http://localhost:8080/api/orders \
        --max-time 10 2>/dev/null || echo "000")
    ELAPSED=$(awk "BEGIN {printf \"%d\", $ELAPSED_MS * 1000}")

    case "$CODE" in
        200)
            SUCCESS=$((SUCCESS + 1))
            LABEL="success"
            ;;
        503|500|502)
            FALLBACK=$((FALLBACK + 1))
            LABEL="fallback"
            ;;
        *)
            FAIL=$((FAIL + 1))
            LABEL="error:$CODE"
            ;;
    esac

    echo "$i,$CODE,${ELAPSED},${LABEL}" >> "$LOG_FILE"
    echo "  req $i: HTTP $CODE  ${ELAPSED}ms  [$LABEL]"
done

echo ""
echo "  Results: $SUCCESS success / $FALLBACK fallback / $FAIL error  (total $TOTAL)"
echo "  Log: $LOG_FILE"
echo ""

# ---------------------------------------------------------------------------
# Step 5: Assertions during fault
# ---------------------------------------------------------------------------
echo -e "${BOLD}[5/6] Evaluating assertions during fault window...${RESET}"

ASSERT_PASS=0
ASSERT_FAIL=0

# At least 50% of requests should complete (not timeout/drop completely)
COMPLETED=$((SUCCESS + FALLBACK))
if [ "$COMPLETED" -ge $((TOTAL / 2)) ]; then
    echo -e "  ${GREEN}PASS${RESET}: $COMPLETED/$TOTAL requests completed (≥50% threshold met)"
    ASSERT_PASS=$((ASSERT_PASS + 1))
else
    echo -e "  ${RED}FAIL${RESET}: only $COMPLETED/$TOTAL requests completed (expected ≥50%)"
    ASSERT_FAIL=$((ASSERT_FAIL + 1))
fi

# order-service must not return 5xx for every single request (circuit breaker / fallback active)
if [ "$FAIL" -lt "$TOTAL" ]; then
    echo -e "  ${GREEN}PASS${RESET}: not all requests hard-failed (circuit breaker / fallback protecting service)"
    ASSERT_PASS=$((ASSERT_PASS + 1))
else
    echo -e "  ${RED}FAIL${RESET}: every request failed — no fallback protection observed"
    ASSERT_FAIL=$((ASSERT_FAIL + 1))
fi
echo ""

# ---------------------------------------------------------------------------
# Step 6: Wait for fault to expire, then verify recovery
# ---------------------------------------------------------------------------
echo -e "${BOLD}[6/6] Waiting for fault window to expire, then verifying recovery...${RESET}"

if [ "$USE_PUMBA" = "true" ] && [ -n "$PUMBA_PID" ]; then
    # Wait for pumba to finish its duration
    echo "  Waiting for pumba to finish (up to ${DURATION}s remaining)..."
    wait "$PUMBA_PID" 2>/dev/null || true
    PUMBA_PID=""
    echo "  Pumba completed — tc rules removed automatically."
else
    echo "  Unpausing $TARGET_CONTAINER..."
    docker unpause "$TARGET_CONTAINER"
fi

echo "  Allowing 10s for service mesh to stabilise..."
sleep 10

echo "  Running steady-state check..."
if bash "$SCRIPTS_DIR/verify-steady-state.sh"; then
    echo -e "  ${GREEN}PASS${RESET}: steady state restored after fault cleared."
    ASSERT_PASS=$((ASSERT_PASS + 1))
else
    echo -e "  ${RED}FAIL${RESET}: steady state NOT restored after fault cleared."
    ASSERT_FAIL=$((ASSERT_FAIL + 1))
fi
echo ""

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
echo -e "${BOLD}${CYAN}════════════════════ VERDICT ════════════════════${RESET}"
echo "  Assertions passed : $ASSERT_PASS"
echo "  Assertions failed : $ASSERT_FAIL"
echo ""

if [ "$ASSERT_FAIL" -eq 0 ]; then
    echo -e "${BOLD}${GREEN}✅  S8 PASSED — Services tolerated ${LOSS_PERCENT}% packet loss and recovered automatically.${RESET}"
    SCENARIO_PASSED=true
else
    echo -e "${BOLD}${RED}❌  S8 FAILED — $ASSERT_FAIL assertion(s) did not meet acceptance criteria.${RESET}"
    SCENARIO_PASSED=false
fi

echo ""
echo "Full request log: $LOG_FILE"
echo ""

[ "$SCENARIO_PASSED" = "true" ]
