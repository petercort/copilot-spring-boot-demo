#!/bin/bash
# Assertion helpers for chaos experiments
set -euo pipefail

assert_http() {
    local url=$1
    local expected_code=${2:-200}
    local actual_code
    actual_code=$(curl -s -o /dev/null -w "%{http_code}" "$url" --max-time 10 2>/dev/null || echo "000")
    if [ "$actual_code" != "$expected_code" ]; then
        echo "FAIL: $url returned $actual_code (expected $expected_code)"
        return 1
    fi
    echo "PASS: $url → $actual_code"
}

assert_response_contains() {
    local url=$1
    local pattern=$2
    local response
    response=$(curl -s "$url" --max-time 10 2>/dev/null || echo "")
    if ! echo "$response" | grep -q "$pattern"; then
        echo "FAIL: $url response does not contain '$pattern'"
        return 1
    fi
    echo "PASS: $url contains '$pattern'"
}

assert_service_up() {
    local name=$1
    local port=$2
    assert_http "http://localhost:${port}/actuator/health" 200
    echo "Service $name on port $port is UP"
}
