# Chaos Scenario Conventions

For runbooks matching `scenarios/*.sh`:

1. **Hypothesis first** — single sentence stating expected behavior under fault.
   > **Hypothesis:** When inventory-service latency exceeds 8 s, order-service Feign calls fail fast (<6 s) via timeout.
2. **Steady-state check** — always start with `verify-steady-state.sh`.
3. **Injection** — document the exact curl/command that injects the fault.
4. **Observation** — what to monitor (HTTP codes, response times, circuit breaker state).
5. **Pass/fail criteria** — quantitative thresholds (e.g., "<5% error rate", "fast-fail in <7 s").
6. **Cleanup** — always disable chaos / restore system at the end.
