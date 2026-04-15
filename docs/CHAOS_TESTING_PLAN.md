# Chaos Engineering & Resilience Testing Plan
## Spring Boot Microservices E-Commerce Application

> **Version:** 1.0 | **Last Updated:** 2025 | **Stack:** Java 25, Spring Boot 3.5.0, Spring Cloud 2024.0.1

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [Architecture Summary](#2-architecture-summary)
3. [Resilience Gaps Analysis](#3-resilience-gaps-analysis)
4. [Tooling Selection](#4-tooling-selection)
5. [Phase 0 – Add Resilience Patterns First](#5-phase-0--add-resilience-patterns-first)
6. [Chaos Monkey for Spring Boot Setup](#6-chaos-monkey-for-spring-boot-setup)
7. [Toxiproxy Setup](#7-toxiproxy-setup)
8. [Steady-State Hypothesis Definitions](#8-steady-state-hypothesis-definitions)
9. [Test Scenarios & Runbooks](#9-test-scenarios--runbooks)
10. [Metrics & Observability](#10-metrics--observability)
11. [Shell Script Runbooks](#11-shell-script-runbooks)
12. [Automated Chaos Test Suite](#12-automated-chaos-test-suite)
13. [Recovery Procedures](#13-recovery-procedures)
14. [CI/CD Integration](#14-cicd-integration)
15. [File List – Changes & New Files](#15-file-list--changes--new-files)

---

## Quick Start – First Chaos Experiment

Before reading the full plan, here is a self-contained 10-minute experiment you can run after completing Phase 0:

```bash
# 1. Start all services (see run.sh)
./run.sh

# 2. Verify steady state
curl -s http://localhost:8080/api/orders | jq .   # expect 200

# 3. Enable Chaos Monkey on order-service (latency assault)
curl -X POST http://localhost:8083/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{"latencyActive": true, "latencyRangeStart": 1000, "latencyRangeEnd": 3000}'

# 4. Send traffic and observe circuit breaker opening
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" \
    http://localhost:8080/api/orders/place?customerId=1&productId=1&qty=1
done

# 5. Check circuit breaker state
curl -s http://localhost:8083/actuator/health | jq '.components.circuitBreakers'

# 6. Disable chaos
curl -X POST http://localhost:8083/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{"latencyActive": false}'
```

Expected outcome: After ~5 slow requests the circuit breaker opens, subsequent calls receive fast fallback responses, `circuitBreakers.status` transitions to `OPEN`.

---

## 1. Overview & Goals

### What Is Chaos Engineering?

Chaos engineering is the discipline of intentionally injecting controlled failures into a production-like system to uncover hidden weaknesses before they manifest as unplanned outages. The process follows a scientific method:

1. Define a **steady-state hypothesis** — what does "healthy" look like?
2. **Introduce a variable** — simulate a real-world failure condition.
3. **Observe** — does the system maintain the steady state?
4. **Learn & harden** — fix weaknesses revealed by the experiment.

### Why It Matters for Microservices

A monolith fails as a single unit. A microservices system can fail **partially** in ways that are far harder to predict:

- A slow downstream service causes thread/connection pool exhaustion upstream.
- A missing circuit breaker turns a single service crash into a cascading failure across the entire mesh.
- Eureka's cached registry keeps routing to dead instances for up to 90 seconds after a crash.
- OpenFeign's default timeout is effectively infinite, so one hanging downstream can wedge all order processing.

### Goals for This Project

| Goal | Success Criteria |
|------|-----------------|
| Validate circuit breaker behaviour | Order-service opens CB within 5 failures of customer/inventory calls |
| Confirm Eureka cache survivability | Services continue serving traffic for ≥ 60 s after Eureka kill |
| Expose timeout gaps | All Feign calls fail fast (< 3 s) under latency injection |
| Measure cascade blast radius | Single service failure affects ≤ 1 downstream service |
| Establish runbooks | Each failure scenario has a documented recovery procedure |

---

## 2. Architecture Summary

```
                          ┌─────────────────────┐
                          │    eureka-server     │
                          │      :8761           │
                          └──────────┬──────────┘
                                     │ registers/discovers
          ┌──────────────────────────┼─────────────────────────┐
          │                          │                          │
          ▼                          ▼                          ▼
  ┌──────────────┐         ┌──────────────────┐      ┌──────────────────┐
  │ api-gateway  │         │ customer-service  │      │inventory-service │
  │    :8080     │         │     :8081        │      │     :8082        │
  └──────┬───────┘         └────────▲─────────┘      └────────▲─────────┘
         │                          │ Feign (lb://)            │ Feign (lb://)
         │ routes to                └──────────┬───────────────┘
         ▼                                     │
  ┌──────────────┐                    ┌────────┴────────┐
  │order-service │ ◄──────────────── │  order-service  │
  │    :8083     │                    │     :8083       │
  └──────────────┘                    └─────────────────┘
```

**Inter-service call paths (failure blast radius targets):**
- `order-service` → `customer-service` via `lb://customer-service`
- `order-service` → `inventory-service` via `lb://inventory-service`
- All external traffic enters via `api-gateway` → routed by Eureka discovery

---

## 3. Resilience Gaps Analysis

The following gaps make the system susceptible to cascading failures. Each must be addressed in Phase 0 before chaos testing yields meaningful signal.

### 3.1 No Circuit Breakers on Feign Clients

**Risk:** CRITICAL  
**Impact:** If `customer-service` or `inventory-service` hangs, `order-service` Feign calls queue indefinitely. With virtual threads (JDK 25) this is less likely to cause thread exhaustion than with platform threads, but connection pool exhaustion in the HTTP client is still a real risk.

**Services affected:** `order-service`

### 3.2 No Retry Configuration

**Risk:** MEDIUM  
**Impact:** Transient network errors result in immediate failures rather than being retried against healthy instances. Particularly relevant with Eureka's eventual consistency — a newly registered instance may fail once before becoming fully warm.

**Services affected:** All services with Feign clients

### 3.3 No Timeout Configuration

**Risk:** HIGH  
**Impact:** OpenFeign's default `connectTimeout` is 10 s, `readTimeout` is 60 s. A slow `inventory-service` (e.g., GC pause, lock contention) will hold order-service connections for up to 60 seconds. Spring Cloud Gateway also has no custom timeout, meaning upstream clients experience the full delay.

**Services affected:** `order-service` (Feign), `api-gateway`

### 3.4 Single Eureka Instance (SPOF)

**Risk:** MEDIUM  
**Impact:** If `eureka-server` crashes, services cannot discover new instances. Existing registrations are cached in each client's local registry for `eureka.client.registryFetchIntervalSeconds` (default 30 s) × `eureka.client.cacheRefreshExecutorExponentialBackOffBound`. In practice, cached routes survive for 60–90 seconds, but no new registrations occur.

**Production mitigation (out of scope for this demo):** Run 2–3 Eureka peers.

### 3.5 H2 In-Memory Databases

**Risk:** LOW (for testing) / HIGH (for production)  
**Impact:** Service restart = data loss. This is acceptable for local dev but must be acknowledged in chaos runbooks — after a kill/restart cycle, the database starts empty. Chaos tests must account for this by re-seeding data on recovery.

### 3.6 No Bulkhead / Rate Limiting

**Risk:** MEDIUM  
**Impact:** No bulkhead means a flood of requests to one endpoint can starve other endpoints in the same service. No rate limiting on the gateway means a single misbehaving client can exhaust gateway threads.

---

## 4. Tooling Selection

### Primary Tools

| Tool | Purpose | Integration Level |
|------|---------|-------------------|
| **Chaos Monkey for Spring Boot** | In-process fault injection (latency, exceptions, kill) | Native Spring Boot — zero external deps |
| **Toxiproxy** | Network-level fault injection (latency, bandwidth, resets) | Docker container, language-agnostic |
| **Bash scripts** | Process kill/restart, traffic generation, assertions | No dependencies |

### Chaos Monkey for Spring Boot
- Library: `de.codecentric:chaos-monkey-spring-boot`
- Activates via Spring profile `chaos-monkey`
- Injects faults at Spring AOP level: `@Service`, `@Repository`, `@RestController`, `@Component`
- Exposes `/actuator/chaosmonkey` REST API for dynamic control
- **Best for:** Service-level faults, exception injection, latency simulation at the method level

### Toxiproxy
- GitHub: [shopify/toxiproxy](https://github.com/Shopify/toxiproxy)
- Runs as a TCP proxy between services
- **Best for:** Network partition simulation, bandwidth limiting, connection resets
- Requires reconfiguring service URLs to route through the proxy

### Shell Scripts
- **Best for:** Process kill/restart scenarios, automated test orchestration, CI integration
- No additional dependencies beyond `curl` and `jq`

### Alternatives Considered

| Tool | Why Not Primary |
|------|----------------|
| **AWS Fault Injection Service (FIS)** | Requires AWS; adds cost; over-engineered for local dev |
| **Chaos Toolkit** | Python-based, flexible but adds complexity for simple scenarios |
| **Gremlin** | Commercial; excellent for production but requires paid subscription |
| **Litmus Chaos** | Kubernetes-focused; not applicable without K8s |

---

## 5. Phase 0 – Add Resilience Patterns First

> ⚠️ **Do not run chaos tests before completing Phase 0.** Without resilience patterns, every test will simply fail catastrophically, yielding no useful signal about whether your mitigations work correctly.

### 5.1 Add Resilience4j to order-service

Add to `order-service/pom.xml`:

```xml
<!-- Resilience4j via Spring Cloud Circuit Breaker -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>

<!-- Actuator for circuit breaker metrics -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- AOP required for Resilience4j annotations -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 5.2 Configure Circuit Breakers, Retries & Timeouts

Create `order-service/src/main/resources/application.yml` additions:

```yaml
# ── Feign Timeouts ─────────────────────────────────────────────────────────
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 2000    # 2 s connect timeout
            readTimeout: 5000       # 5 s read timeout
          customer-service:
            connectTimeout: 2000
            readTimeout: 5000
          inventory-service:
            connectTimeout: 2000
            readTimeout: 5000

# ── Resilience4j Circuit Breakers ──────────────────────────────────────────
resilience4j:
  circuitbreaker:
    instances:
      customerService:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 10s
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 4s
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - feign.FeignException
      inventoryService:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 10s
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 4s

  # ── Retry Configuration ─────────────────────────────────────────────────
  retry:
    instances:
      customerService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - feign.RetryableException
        ignoreExceptions:
          - feign.FeignException.NotFound
      inventoryService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2

  # ── Bulkhead (thread pool isolation) ───────────────────────────────────
  bulkhead:
    instances:
      customerService:
        maxConcurrentCalls: 10
        maxWaitDuration: 1s
      inventoryService:
        maxConcurrentCalls: 10
        maxWaitDuration: 1s

# ── Actuator ───────────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,circuitbreakers,circuitbreakerevents,chaosmonkey
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
```

### 5.3 Annotate Feign Clients with Circuit Breakers

In `order-service/src/main/java/.../client/CustomerClient.java`:

```java
@FeignClient(name = "customer-service", fallback = CustomerClientFallback.class)
public interface CustomerClient {

    @GetMapping("/api/customers/{id}")
    CustomerDto getCustomer(@PathVariable Long id);
}
```

Create `CustomerClientFallback.java`:

```java
@Component
public class CustomerClientFallback implements CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClientFallback.class);

    @Override
    public CustomerDto getCustomer(Long id) {
        log.warn("CustomerClient fallback triggered for customerId={}", id);
        return null; // or throw a domain-specific exception
    }
}
```

In `order-service/src/main/java/.../service/OrderService.java`:

```java
@CircuitBreaker(name = "customerService", fallbackMethod = "customerFallback")
@Retry(name = "customerService")
@Bulkhead(name = "customerService")
public CustomerDto fetchCustomer(Long customerId) {
    return customerClient.getCustomer(customerId);
}

public CustomerDto customerFallback(Long customerId, Exception ex) {
    log.error("Circuit breaker open for customerId={}: {}", customerId, ex.getMessage());
    throw new ServiceUnavailableException("Customer service is currently unavailable");
}
```

### 5.4 API Gateway Timeout Configuration

In `api-gateway/src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 2000    # 2 s
        response-timeout: 10s    # 10 s total response
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCB
                fallbackUri: forward:/fallback/orders
            - name: Retry
              args:
                retries: 2
                statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE
                methods: GET
                backoff:
                  firstBackoff: 500ms
                  maxBackoff: 2s
                  factor: 2
        - id: customer-service
          uri: lb://customer-service
          predicates:
            - Path=/api/customers/**
          filters:
            - name: CircuitBreaker
              args:
                name: customerServiceCB
                fallbackUri: forward:/fallback/customers
        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
          filters:
            - name: CircuitBreaker
              args:
                name: inventoryServiceCB
                fallbackUri: forward:/fallback/inventory
```

---

## 6. Chaos Monkey for Spring Boot Setup

### 6.1 Maven Dependency

Add to **each service's** `pom.xml` (customer-service, inventory-service, order-service, api-gateway):

```xml
<dependency>
    <groupId>de.codecentric</groupId>
    <artifactId>chaos-monkey-spring-boot</artifactId>
    <version>3.1.0</version>
</dependency>
```

> **Note:** Version 3.1.0 targets Spring Boot 3.x. Verify compatibility at [codecentric/chaos-monkey-spring-boot releases](https://github.com/codecentric/chaos-monkey-spring-boot/releases).

### 6.2 Configuration Properties

Create `src/main/resources/application-chaos-monkey.yml` in each service:

```yaml
# ── Chaos Monkey Configuration ─────────────────────────────────────────────
chaos:
  monkey:
    enabled: true
    
    # Watchers — which Spring beans to attack
    watcher:
      controller: true          # @RestController methods
      restController: true      # @RestController methods (alias)
      service: true             # @Service methods
      repository: false         # @Repository methods (careful — H2 side effects)
      component: false          # @Component methods
      actuatorHealth: false
      
    # Assaults — what to inject
    assaults:
      level: 5                  # Attack 1 in every 5 calls (20%)
      deterministic: false      # Randomize which calls are attacked
      
      # Latency assault
      latencyActive: false      # Enable at runtime via REST API
      latencyRangeStart: 1000   # ms
      latencyRangeEnd: 3000     # ms
      
      # Exception assault
      exceptionsActive: false
      exception:
        type: java.lang.RuntimeException
        arguments:
          - type: java.lang.String
            value: "Chaos Monkey - injected exception"
      
      # Kill application assault
      killApplicationActive: false
      
      # Memory assault
      memoryActive: false
      memoryMillisecondsHoldFilledMemory: 90000
      memoryMillisecondsWaitNextIncrease: 1000
      memoryFillIncrementFraction: 0.15
      memoryFillTargetFraction: 0.80

# Expose chaos monkey actuator endpoint
management:
  endpoint:
    chaosmonkey:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,chaosmonkey,metrics
```

### 6.3 Activating Chaos Monkey via Spring Profile

Start a service with the chaos-monkey profile:

```bash
java -jar order-service.jar --spring.profiles.active=chaos-monkey
# or
SPRING_PROFILES_ACTIVE=chaos-monkey java -jar order-service.jar
```

### 6.4 Chaos Monkey REST API Reference

Base URL: `http://localhost:{port}/actuator/chaosmonkey`

```bash
# Check current status
curl http://localhost:8083/actuator/chaosmonkey

# Enable/disable Chaos Monkey
curl -X POST http://localhost:8083/actuator/chaosmonkey/enable
curl -X POST http://localhost:8083/actuator/chaosmonkey/disable

# View current assault config
curl http://localhost:8083/actuator/chaosmonkey/assaults

# Enable latency assault dynamically
curl -X POST http://localhost:8083/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{
    "latencyActive": true,
    "latencyRangeStart": 2000,
    "latencyRangeEnd": 5000,
    "level": 5
  }'

# Enable exception assault
curl -X POST http://localhost:8083/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{
    "exceptionsActive": true,
    "exception": {
      "type": "java.lang.RuntimeException",
      "arguments": [{"type": "java.lang.String", "value": "Chaos injected"}]
    },
    "level": 3
  }'

# Enable memory assault
curl -X POST http://localhost:8083/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{
    "memoryActive": true,
    "memoryFillTargetFraction": 0.80,
    "memoryMillisecondsHoldFilledMemory": 30000
  }'

# View watcher config
curl http://localhost:8083/actuator/chaosmonkey/watchers

# Update watchers
curl -X POST http://localhost:8083/actuator/chaosmonkey/watchers \
  -H "Content-Type: application/json" \
  -d '{"controller": true, "service": true, "repository": false}'
```

### 6.5 Assault Type Decision Matrix

| Assault | Simulates | Services to Target | Circuit Breaker? |
|---------|-----------|-------------------|-----------------|
| Latency | GC pauses, slow DB queries, network lag | customer-service, inventory-service | Yes — tests slow call threshold |
| Exception | Bug in downstream service, HTTP 500 | customer-service, inventory-service | Yes — tests failure rate threshold |
| KillApplication | Service crash, OOM | Any downstream service | Yes — tests OPEN transition |
| MemoryAssault | Memory leak, heap pressure | Any service | Indirect — test degraded response |

---

## 7. Toxiproxy Setup

Toxiproxy is used for network-level fault injection that Chaos Monkey cannot simulate (packet loss, bandwidth caps, TCP resets).

### 7.1 Docker Setup

```bash
# Start Toxiproxy
docker run -d \
  --name toxiproxy \
  -p 8474:8474 \
  -p 18081:18081 \
  -p 18082:18082 \
  -p 18083:18083 \
  ghcr.io/shopify/toxiproxy:2.9.0
```

### 7.2 Install Toxiproxy CLI (macOS)

```bash
brew install toxiproxy
# or download binary:
curl -L https://github.com/Shopify/toxiproxy/releases/download/v2.9.0/toxiproxy-cli-darwin-amd64 \
  -o toxiproxy-cli && chmod +x toxiproxy-cli
```

### 7.3 Create Proxies for Each Service Path

```bash
TOXIPROXY_API=http://localhost:8474

# Proxy: order-service → customer-service
curl -X POST $TOXIPROXY_API/proxies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "customer-service",
    "listen": "0.0.0.0:18081",
    "upstream": "localhost:8081",
    "enabled": true
  }'

# Proxy: order-service → inventory-service
curl -X POST $TOXIPROXY_API/proxies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "inventory-service",
    "listen": "0.0.0.0:18082",
    "upstream": "localhost:8082",
    "enabled": true
  }'

# Proxy: external → order-service
curl -X POST $TOXIPROXY_API/proxies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "order-service",
    "listen": "0.0.0.0:18083",
    "upstream": "localhost:8083",
    "enabled": true
  }'
```

### 7.4 Toxic Types Reference

```bash
TOXIPROXY_API=http://localhost:8474

# ── LATENCY toxic ──────────────────────────────────────────────────────────
# Add 2000ms ± 500ms latency to customer-service upstream
curl -X POST $TOXIPROXY_API/proxies/customer-service/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "latency-upstream",
    "type": "latency",
    "stream": "upstream",
    "toxicity": 1.0,
    "attributes": {"latency": 2000, "jitter": 500}
  }'

# ── BANDWIDTH toxic ────────────────────────────────────────────────────────
# Limit bandwidth to 100 KB/s
curl -X POST $TOXIPROXY_API/proxies/inventory-service/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "bandwidth-limit",
    "type": "bandwidth",
    "stream": "upstream",
    "toxicity": 1.0,
    "attributes": {"rate": 100}
  }'

# ── SLOW_CLOSE toxic ───────────────────────────────────────────────────────
# Delay TCP close by 5000ms (simulates hung connection)
curl -X POST $TOXIPROXY_API/proxies/customer-service/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "slow-close",
    "type": "slow_close",
    "stream": "downstream",
    "toxicity": 1.0,
    "attributes": {"delay": 5000}
  }'

# ── TIMEOUT toxic ──────────────────────────────────────────────────────────
# Drop connection after 3000ms (simulates unresponsive service)
curl -X POST $TOXIPROXY_API/proxies/inventory-service/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "timeout",
    "type": "timeout",
    "stream": "upstream",
    "toxicity": 1.0,
    "attributes": {"timeout": 3000}
  }'

# ── RESET_PEER toxic ───────────────────────────────────────────────────────
# Reset TCP connections after 1000ms (simulates network partition)
curl -X POST $TOXIPROXY_API/proxies/customer-service/toxics \
  -H "Content-Type: application/json" \
  -d '{
    "name": "reset-peer",
    "type": "reset_peer",
    "stream": "upstream",
    "toxicity": 1.0,
    "attributes": {"timeout": 1000}
  }'

# ── Remove a toxic ─────────────────────────────────────────────────────────
curl -X DELETE $TOXIPROXY_API/proxies/customer-service/toxics/latency-upstream

# ── Disable a proxy entirely (hard partition) ──────────────────────────────
curl -X POST $TOXIPROXY_API/proxies/customer-service \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'

# ── Re-enable proxy ────────────────────────────────────────────────────────
curl -X POST $TOXIPROXY_API/proxies/customer-service \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
```

### 7.5 Reconfiguring Services to Use Toxiproxy

When running Toxiproxy experiments, override the Eureka registration URL or Feign target URL to point through the proxy. The cleanest approach for local dev is to use hardcoded URLs in a `toxiproxy` Spring profile:

```yaml
# application-toxiproxy.yml in order-service
spring:
  cloud:
    openfeign:
      client:
        config:
          customer-service:
            url: http://localhost:18081   # routes through Toxiproxy
          inventory-service:
            url: http://localhost:18082   # routes through Toxiproxy
```

Start order-service with: `--spring.profiles.active=toxiproxy`

---

## 8. Steady-State Hypothesis Definitions

Each experiment must define what "healthy" means **before** and **after** the chaos phase. If the system cannot restore to steady state, the experiment has revealed a real weakness.

### Global Steady State

| Metric | Healthy Threshold |
|--------|------------------|
| `GET /api/orders` via gateway | HTTP 200, p99 < 500 ms |
| `GET /api/customers/{id}` | HTTP 200, p99 < 200 ms |
| `GET /api/inventory/{id}` | HTTP 200, p99 < 200 ms |
| Eureka registered instances | All 4 services (gateway, customer, inventory, order) showing UP |
| Circuit breaker state | All CLOSED |
| JVM heap usage | < 70% |
| Error rate (5xx) | < 1% over any 30 s window |

### Steady-State Verification Script

```bash
#!/bin/bash
# scripts/verify-steady-state.sh

set -euo pipefail

GATEWAY=http://localhost:8080
EUREKA=http://localhost:8761
PASS=true

check_http() {
  local url=$1 expected=$2 label=$3
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$url")
  if [ "$code" == "$expected" ]; then
    echo "  ✅ $label — HTTP $code"
  else
    echo "  ❌ $label — expected HTTP $expected, got HTTP $code"
    PASS=false
  fi
}

check_response_time() {
  local url=$1 threshold=$2 label=$3
  local time
  time=$(curl -s -o /dev/null -w "%{time_total}" --max-time 5 "$url")
  if (( $(echo "$time < $threshold" | bc -l) )); then
    echo "  ✅ $label — ${time}s (< ${threshold}s)"
  else
    echo "  ❌ $label — ${time}s (threshold: ${threshold}s)"
    PASS=false
  fi
}

echo "── Steady-State Verification ──────────────────────────────────"
check_http "$GATEWAY/api/orders" "200" "GET /api/orders"
check_http "$GATEWAY/api/customers/1" "200" "GET /api/customers/1"
check_http "$GATEWAY/api/inventory/1" "200" "GET /api/inventory/1"
check_http "$EUREKA/eureka/apps" "200" "Eureka registry"

echo ""
echo "── Response Time Checks ───────────────────────────────────────"
check_response_time "$GATEWAY/api/orders" "0.5" "Orders p99 threshold"
check_response_time "$GATEWAY/api/customers/1" "0.2" "Customers p99 threshold"

echo ""
echo "── Circuit Breaker State ──────────────────────────────────────"
CB_STATE=$(curl -s http://localhost:8083/actuator/health | \
  jq -r '.components.circuitBreakers.details | to_entries[] | "\(.key): \(.value.details.state)"' 2>/dev/null || echo "unavailable")
echo "  $CB_STATE"

echo ""
if [ "$PASS" = true ]; then
  echo "✅ STEADY STATE CONFIRMED"
  exit 0
else
  echo "❌ STEADY STATE VIOLATED — do not proceed with chaos"
  exit 1
fi
```

---

## 9. Test Scenarios & Runbooks

---

### S1: Eureka Server Kill

**Hypothesis:** When Eureka server is killed, all running services continue to route requests for at least 60 seconds using their locally cached registry.

**Blast Radius:** All services (read path); no new instance registrations possible  
**Risk Level:** Medium — controlled kill of a known component  
**Duration:** ~5 minutes  
**Prerequisites:** All services running, steady state confirmed

#### Runbook

```bash
#!/bin/bash
# scenarios/s1-eureka-kill.sh

set -uo pipefail

EUREKA_PID_FILE="eureka-server.pid"   # adjust to your startup mechanism
LOG="s1-eureka-kill.log"

echo "[$(date)] S1: Eureka Kill Test" | tee $LOG

# Step 1: Verify steady state
echo "[$(date)] Verifying steady state..." | tee -a $LOG
bash scripts/verify-steady-state.sh || { echo "Aborting — not in steady state"; exit 1; }

# Step 2: Start background traffic monitor
echo "[$(date)] Starting traffic monitor..." | tee -a $LOG
bash scripts/traffic-monitor.sh > traffic-s1.log &
MONITOR_PID=$!

# Step 3: Record Eureka PID
EUREKA_PID=$(pgrep -f "eureka-server" | head -1)
echo "[$(date)] Eureka PID: $EUREKA_PID" | tee -a $LOG

# Step 4: CHAOS — Kill Eureka
echo "[$(date)] ⚡ CHAOS: Killing Eureka server (PID $EUREKA_PID)" | tee -a $LOG
kill -9 $EUREKA_PID

# Step 5: Observe — send requests every 5 seconds for 90 seconds
echo "[$(date)] Observing service routing for 90s..." | tee -a $LOG
for i in $(seq 1 18); do
  ORDERS_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/api/orders)
  CUSTOMERS_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/api/customers/1)
  echo "[$(date)] t+${i}0s — Orders: $ORDERS_CODE, Customers: $CUSTOMERS_CODE" | tee -a $LOG
  sleep 5
done

# Step 6: Kill monitor
kill $MONITOR_PID 2>/dev/null || true

# Step 7: Restart Eureka
echo "[$(date)] Restarting Eureka..." | tee -a $LOG
cd eureka-server && java -jar target/eureka-server-*.jar &
sleep 20

# Step 8: Verify steady state restored
echo "[$(date)] Verifying recovery..." | tee -a $LOG
bash scripts/verify-steady-state.sh

echo "[$(date)] S1 Complete. Results in $LOG and traffic-s1.log"
```

**Expected Results:**
- HTTP 200s continue for ≥ 60 s after Eureka kill
- After ~90 s, services may start failing new discoveries but existing routes hold
- After Eureka restart + 30 s, all services re-register and routing normalizes

**Failure Indicators:**
- Immediate 503s after Eureka kill → registry not cached (check `eureka.client.fetchRegistry=true`)
- 5xx after restart that don't recover → Eureka cache not refreshing

---

### S2: Customer Service Failure

**Hypothesis:** When `customer-service` is killed, `order-service` circuit breaker opens within 5 failures, returning fast fallback responses within 1 second.

**Blast Radius:** `order-service` order placement; customer data lookup  
**Risk Level:** Low — downstream service kill  
**Duration:** ~10 minutes

#### Runbook

```bash
#!/bin/bash
# scenarios/s2-customer-service-kill.sh

set -uo pipefail

echo "[$(date)] S2: Customer Service Kill"

# Step 1: Steady state
bash scripts/verify-steady-state.sh || exit 1

# Step 2: Record circuit breaker initial state
echo "[$(date)] Initial CB state:"
curl -s http://localhost:8083/actuator/health | \
  jq '.components.circuitBreakers.details.customerService'

# Step 3: CHAOS — Kill customer-service
CUSTOMER_PID=$(pgrep -f "customer-service" | head -1)
echo "[$(date)] ⚡ CHAOS: Killing customer-service (PID $CUSTOMER_PID)"
kill -9 $CUSTOMER_PID

# Step 4: Send 20 requests, track CB transition
echo "[$(date)] Sending 20 order placement requests..."
for i in $(seq 1 20); do
  START=$(date +%s%3N)
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    "http://localhost:8083/api/orders/place?customerId=1&productId=1&qty=1")
  END=$(date +%s%3N)
  ELAPSED=$((END - START))
  
  CB_STATE=$(curl -s http://localhost:8083/actuator/health | \
    jq -r '.components.circuitBreakers.details.customerService.details.state' 2>/dev/null)
  
  echo "  Request $i: HTTP $CODE in ${ELAPSED}ms | CB: $CB_STATE"
  sleep 0.5
done

# Step 5: Assert CB is OPEN
CB_FINAL=$(curl -s http://localhost:8083/actuator/health | \
  jq -r '.components.circuitBreakers.details.customerService.details.state')
echo "[$(date)] Final CB state: $CB_FINAL"
if [ "$CB_FINAL" == "OPEN" ]; then
  echo "✅ Circuit breaker opened as expected"
else
  echo "❌ Circuit breaker did NOT open — check Resilience4j config"
fi

# Step 6: Restart customer-service
echo "[$(date)] Restarting customer-service..."
cd customer-service && java -jar target/customer-service-*.jar --server.port=8081 &
sleep 15

# Step 7: Wait for CB to transition to HALF_OPEN then CLOSED
echo "[$(date)] Waiting for circuit breaker to recover (up to 60s)..."
for i in $(seq 1 12); do
  CB_STATE=$(curl -s http://localhost:8083/actuator/health | \
    jq -r '.components.circuitBreakers.details.customerService.details.state' 2>/dev/null)
  echo "  t+${i}5s: CB=$CB_STATE"
  [ "$CB_STATE" == "CLOSED" ] && break
  sleep 5
done

bash scripts/verify-steady-state.sh
```

**Expected Results:**
- Requests 1–5: failures (customer-service unreachable), response time approaches Feign readTimeout
- Requests 6–20: CB OPEN, fast fallback responses < 100 ms
- After restart: CB transitions OPEN → HALF_OPEN → CLOSED within 30 s

**Failure Indicators:**
- CB never opens → `@CircuitBreaker` annotation not applied, or `minimumNumberOfCalls` not reached
- Fallback responses take > 5 s → Feign timeout not configured
- CB doesn't recover → `automaticTransitionFromOpenToHalfOpenEnabled: false`

---

### S3: Inventory Service Latency Injection

**Hypothesis:** When `inventory-service` experiences 4-second latency, `order-service` times out within 5 seconds and the circuit breaker opens due to slow call rate threshold.

**Blast Radius:** Order placement flow  
**Risk Level:** Low — non-destructive latency injection  
**Duration:** ~10 minutes

#### Runbook

```bash
#!/bin/bash
# scenarios/s3-inventory-latency.sh

set -uo pipefail

echo "[$(date)] S3: Inventory Service Latency Injection"

# Step 1: Steady state
bash scripts/verify-steady-state.sh || exit 1

# Step 2: Enable Chaos Monkey latency on inventory-service (via Chaos Monkey)
echo "[$(date)] ⚡ CHAOS: Injecting 4000ms latency on inventory-service"
curl -s -X POST http://localhost:8082/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{"latencyActive": true, "latencyRangeStart": 4000, "latencyRangeEnd": 4500, "level": 1}'

# Alternative: Use Toxiproxy
# curl -X POST http://localhost:8474/proxies/inventory-service/toxics \
#   -H "Content-Type: application/json" \
#   -d '{"name":"latency","type":"latency","attributes":{"latency":4000}}'

# Step 3: Send traffic for 60 seconds
echo "[$(date)] Sending requests for 60s..."
END_TIME=$(($(date +%s) + 60))
REQUEST_COUNT=0
TIMEOUT_COUNT=0
FALLBACK_COUNT=0

while [ $(date +%s) -lt $END_TIME ]; do
  START=$(date +%s%3N)
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    "http://localhost:8080/api/orders/place?customerId=1&productId=1&qty=1")
  END=$(date +%s%3N)
  ELAPSED=$((END - START))
  REQUEST_COUNT=$((REQUEST_COUNT + 1))
  
  case $CODE in
    200) ;;
    503) FALLBACK_COUNT=$((FALLBACK_COUNT + 1)) ;;
    000) TIMEOUT_COUNT=$((TIMEOUT_COUNT + 1)) ;;
  esac
  
  echo "  Request $REQUEST_COUNT: HTTP $CODE in ${ELAPSED}ms"
  sleep 1
done

# Step 4: Report
echo ""
echo "── S3 Results ─────────────────────────────────────────────────"
echo "  Total requests:   $REQUEST_COUNT"
echo "  Timeout (000):    $TIMEOUT_COUNT"
echo "  Fallbacks (503):  $FALLBACK_COUNT"
CB_STATE=$(curl -s http://localhost:8083/actuator/health | \
  jq -r '.components.circuitBreakers.details.inventoryService.details.state' 2>/dev/null)
echo "  Final CB state:   $CB_STATE"

# Step 5: Disable chaos
echo "[$(date)] Removing latency injection..."
curl -s -X POST http://localhost:8082/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{"latencyActive": false}'

# Step 6: Recovery
sleep 15
bash scripts/verify-steady-state.sh
```

**Expected Results:**
- First ~5 requests: slow (4–5 s), CB counting slow calls
- After threshold: CB opens, remaining requests return fast fallback (< 500 ms)
- `slowCallRateThreshold: 50` means after 50% of calls exceed 4 s, CB opens

---

### S4: API Gateway Overload

**Hypothesis:** Under high load (100+ concurrent requests), the API Gateway degrades gracefully with rate limiting; it does not crash, and p99 response time stays under 10 seconds.

**Blast Radius:** All traffic (entire gateway is target)  
**Risk Level:** Medium — may affect all services  
**Duration:** ~5 minutes

#### Runbook

```bash
#!/bin/bash
# scenarios/s4-gateway-overload.sh

set -uo pipefail

echo "[$(date)] S4: Gateway Overload Test"

# Step 1: Steady state
bash scripts/verify-steady-state.sh || exit 1

# Step 2: Install hey (HTTP load tool) if not present
which hey || brew install hey

# Step 3: Baseline load test (10 req/s for 30s)
echo "[$(date)] Baseline: 10 req/s for 30s"
hey -n 300 -c 10 -q 10 http://localhost:8080/api/orders 2>&1 | tail -20

# Step 4: CHAOS — High load (100 concurrent for 60s)
echo "[$(date)] ⚡ CHAOS: 100 concurrent requests for 60s"
hey -n 6000 -c 100 http://localhost:8080/api/orders 2>&1 | tee s4-load-results.txt

# Step 5: Check gateway still alive
echo "[$(date)] Checking gateway health post-load..."
curl -s http://localhost:8080/actuator/health | jq '.status'

# Step 6: Check for 5xx responses in results
ERRORS=$(grep -E "^\s+\[5" s4-load-results.txt | awk '{print $2}' | head -1 || echo "0")
echo "5xx error count: ${ERRORS:-0}"

# Step 7: Recovery check
bash scripts/verify-steady-state.sh
```

**Expected Results:**
- Gateway remains accessible throughout load test
- p99 < 10 s under 100 concurrent requests
- No OOM or process crash
- After load ends, response times return to baseline within 10 s

**Improvements Revealed:**
- If gateway crashes: add JVM heap tuning (`-Xmx512m`)
- If 429 responses: rate limiting is working (expected with Gateway rate limit filter)
- If all requests succeed with < 1 s p99: consider adding rate limiting for production

---

### S5: Order Service Cascade Failure

**Hypothesis:** When order-service is overwhelmed with exceptions from both customer-service AND inventory-service simultaneously, individual circuit breakers isolate the blast radius; order-service itself remains accessible (returns 503 with fallback, not 500 or OOM crash).

**Blast Radius:** Order placement only; customer and inventory read paths unaffected  
**Risk Level:** Medium  
**Duration:** ~10 minutes

#### Runbook

```bash
#!/bin/bash
# scenarios/s5-cascade-failure.sh

set -uo pipefail

echo "[$(date)] S5: Cascade Failure Test"

# Step 1: Steady state
bash scripts/verify-steady-state.sh || exit 1

# Step 2: CHAOS — Enable exception assault on BOTH downstream services
echo "[$(date)] ⚡ CHAOS: Enabling exception assault on customer-service and inventory-service"

curl -s -X POST http://localhost:8081/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{"exceptionsActive": true, "level": 1}'  # 100% attack rate

curl -s -X POST http://localhost:8082/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d '{"exceptionsActive": true, "level": 1}'

# Step 3: Send requests for 60s
echo "[$(date)] Sending order requests for 60s..."
END_TIME=$(($(date +%s) + 60))
TOTAL=0; FALLBACK=0; CRASH=0

while [ $(date +%s) -lt $END_TIME ]; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "http://localhost:8083/api/orders/place?customerId=1&productId=1&qty=1")
  TOTAL=$((TOTAL+1))
  case $CODE in
    503|422) FALLBACK=$((FALLBACK+1)) ;;
    500) CRASH=$((CRASH+1)) ;;
  esac
  sleep 0.5
done

# Step 4: Verify order-service still responding
GATEWAY_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/actuator/health)
echo ""
echo "── S5 Results ─────────────────────────────────────────────────"
echo "  Total requests:   $TOTAL"
echo "  Graceful (503):   $FALLBACK"
echo "  Server errors:    $CRASH"
echo "  Gateway health:   HTTP $GATEWAY_CODE"

# Verify direct services still work (blast radius isolation)
CUSTOMER_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8081/api/customers/1)
INVENTORY_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8082/api/inventory/1)
echo "  Customer direct:  HTTP $CUSTOMER_CODE  (should still be 200)"
echo "  Inventory direct: HTTP $INVENTORY_CODE (should still be 200)"

# Step 5: Disable chaos
curl -s -X POST http://localhost:8081/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" -d '{"exceptionsActive": false}'
curl -s -X POST http://localhost:8082/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" -d '{"exceptionsActive": false}'

sleep 30
bash scripts/verify-steady-state.sh
```

---

### S6: Network Partition via Toxiproxy

**Hypothesis:** When TCP connections between order-service and both downstream services are reset, the order-service circuit breakers detect the partition and serve fallbacks within the configured timeout window.

**Blast Radius:** Order placement; requires Toxiproxy running and services configured with `--spring.profiles.active=toxiproxy`  
**Risk Level:** Low (non-destructive, purely network-level)  
**Duration:** ~10 minutes

#### Runbook

```bash
#!/bin/bash
# scenarios/s6-network-partition.sh

set -uo pipefail

TOXI=http://localhost:8474

echo "[$(date)] S6: Network Partition via Toxiproxy"

# Prerequisite: Toxiproxy running and services configured for it
curl -s $TOXI/version || { echo "Toxiproxy not running. Start with docker run..."; exit 1; }

# Step 1: Steady state (via Toxiproxy routes)
bash scripts/verify-steady-state.sh || exit 1

# Step 2: CHAOS — reset all connections (immediate TCP RST)
echo "[$(date)] ⚡ CHAOS: Injecting TCP reset on customer-service and inventory-service proxies"

curl -X POST $TOXI/proxies/customer-service/toxics \
  -H "Content-Type: application/json" \
  -d '{"name":"reset-peer","type":"reset_peer","attributes":{"timeout":0}}'

curl -X POST $TOXI/proxies/inventory-service/toxics \
  -H "Content-Type: application/json" \
  -d '{"name":"reset-peer","type":"reset_peer","attributes":{"timeout":0}}'

# Step 3: Observe for 60s
echo "[$(date)] Observing partition behaviour for 60s..."
END_TIME=$(($(date +%s) + 60))
TOTAL=0; FAST_FAIL=0; SLOW_FAIL=0

while [ $(date +%s) -lt $END_TIME ]; do
  START=$(date +%s%3N)
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    "http://localhost:18083/api/orders/place?customerId=1&productId=1&qty=1")
  END=$(date +%s%3N)
  ELAPSED=$((END - START))
  TOTAL=$((TOTAL+1))
  
  [ $ELAPSED -lt 2000 ] && FAST_FAIL=$((FAST_FAIL+1)) || SLOW_FAIL=$((SLOW_FAIL+1))
  echo "  Request $TOTAL: HTTP $CODE in ${ELAPSED}ms"
  sleep 1
done

echo ""
echo "── S6 Results ─────────────────────────────────────────────────"
echo "  Total:      $TOTAL"
echo "  Fast (<2s): $FAST_FAIL  ← expected after CB opens"
echo "  Slow (>2s): $SLOW_FAIL  ← should be only first few"

# Step 4: Remove toxics
curl -X DELETE $TOXI/proxies/customer-service/toxics/reset-peer
curl -X DELETE $TOXI/proxies/inventory-service/toxics/reset-peer

sleep 15
bash scripts/verify-steady-state.sh
```

---

### S8: Network Packet Drop (pumba netem)

**Hypothesis:** When 30% of network packets between the Docker containers are randomly dropped, the order-service Feign client retries and/or circuit breaker ensure that ≥50% of requests still complete (either with real data or a fallback response), and the system returns to full steady state automatically once the fault is removed.

**Blast Radius:** Calls from order-service (and api-gateway) to customer-service degrade; other inter-service paths unaffected  
**Risk Level:** Low (no data is destroyed; pumba auto-removes `tc` rules after duration expires)  
**Duration:** ~3 minutes (60s fault window + stabilisation)

#### Prerequisites

- Docker daemon accessible on the host (`/var/run/docker.sock`)
- `pumba` pulled automatically by the script, or pre-pulled:
  ```bash
  docker pull gaiaadm/pumba:latest
  docker pull gaiadocker/iproute2
  ```
- All 5 services running and healthy (verified by script)

#### Steady State

| Indicator | Expected Value |
|-----------|---------------|
| `GET /api/orders` HTTP status | `200` |
| `GET /api/customers` HTTP status | `200` |
| All `/actuator/health` endpoints | `UP` |

#### Fault Injection Method

pumba injects a `netem` packet-loss rule into the `customer-service` container's network interface via a short-lived sidecar that has `iproute2` (`tc`) installed:

```bash
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba:latest \
  netem --duration 60s --tc-image "gaiadocker/iproute2" \
  loss --percent 30 \
  customer-service
```

If pumba is unavailable the script falls back to `docker pause customer-service` for 30 seconds, which simulates a hard network partition (100% loss).

#### Runbook

```bash
# Run the full automated scenario
bash scenarios/s8-network-packet-drop.sh

# Or manually step through:
bash scripts/verify-steady-state.sh

docker run --rm -d \
  -v /var/run/docker.sock:/var/run/docker.sock \
  gaiaadm/pumba:latest \
  netem --duration 60s --tc-image "gaiadocker/iproute2" \
  loss --percent 30 customer-service

# While pumba runs, send test traffic
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "req $i: %{http_code}\n" \
    http://localhost:8080/api/orders --max-time 10
done

# After 60s pumba exits automatically; verify recovery
sleep 10
bash scripts/verify-steady-state.sh
```

#### Expected Results

| Observation | Expected | Pass Criteria |
|-------------|----------|---------------|
| Requests completing during fault | ≥50% of sent requests return any HTTP response | Circuit breaker / retry absorbs packet loss |
| Hard failures (timeout/no response) | <50% | Retry policy compensates for dropped packets |
| Service recovery after fault | Full steady state within 10s of pumba exit | `tc` rules removed automatically by pumba |
| order-service circuit breaker | May open then half-open then close | `GET /actuator/health/circuitBreakers` on port 8083 |

#### Weaknesses This Scenario Exposes

- **No retry configuration on Feign clients** — every dropped packet causes a hard error rather than a transparent retry
- **Circuit breaker threshold too sensitive** — opens after only a few packet-loss errors, causing unnecessary fallback responses
- **Long Feign connect timeout** — combined with packet loss, requests can stall near the timeout boundary, increasing p99 latency significantly

---

### S7: JVM Heap Exhaustion

**Hypothesis:** When `order-service` runs with a severely constrained JVM heap (`-Xmx64m`), it becomes unhealthy or is OOM-killed under moderate request load, while `customer-service`, `inventory-service`, and `api-gateway` remain fully available — proving that a memory-starved JVM causes a contained, self-isolated failure with no cascade.

**Steady State:**  
All five services healthy (`/actuator/health` → `UP`), `/api/customers`, `/api/products`, and `/api/orders` returning HTTP 200 through the gateway.

**Method:**
1. Confirm steady state via `verify-steady-state.sh`.
2. Stop the normal `order-service` Docker container (`docker compose stop order-service`).
3. Start a replacement container from the same image with `-Xmx64m -XX:+ExitOnOutOfMemoryError` injected via `JAVA_TOOL_OPTIONS`.
4. Send 30 POST `/api/orders` requests (0.3 s apart) to induce GC pressure and potential OOM.
5. Inspect the container state, `OOMKilled` flag, and logs for `OutOfMemoryError` evidence.
6. Assert that `api-gateway`, `customer-service`, and `inventory-service` are still returning 200.
7. Restore via `docker compose up -d order-service` (runs automatically on EXIT via trap).

**Expected Results:**

| Assertion | Expected |
|-----------|----------|
| `order-service` container status | `exited` or `unhealthy` (OOM during startup or under load) |
| `OOMKilled` Docker flag | `true` (if kernel OOM killer triggered) |
| `OutOfMemoryError` in logs | Present |
| `api-gateway` health | UP — no cascade |
| `customer-service` health | UP — no cascade |
| `inventory-service` health | UP — no cascade |
| Scenario verdict | PASS if no cascade regardless of whether order-service OOMs |

**Blast Radius:** Order placement only; all other endpoints unaffected.  
**Risk Level:** Low — Docker-isolated, automatic cleanup on exit.  
**Duration:** ~3 minutes.  

**Notes:**
- Spring Boot 3 typically requires ≥ 256 MB heap to start cleanly; 64 MB will cause OOM during startup or during the first GC cycle under load.
- If the service unexpectedly survives (no OOM), the scenario still passes the no-cascade assertion and emits a warning to try a smaller heap (`-Xmx32m`).
- The `--network-alias order-service` flag allows Eureka and other containers to still resolve the constrained container by its service name.

#### Runbook

```bash
# scenarios/s7-jvm-heap-exhaustion.sh

set -euo pipefail

TARGET_IMAGE="copilot-spring-boot-demo-order-service:latest"
NETWORK="copilot-spring-boot-demo_ecommerce-net"

# [1] Steady state
bash scripts/verify-steady-state.sh

# [2] Stop normal container
docker compose stop order-service

# [3] CHAOS: start with 64 MB heap + exit-on-OOM
docker run -d \
  --name order-service-heap-test \
  --network "$NETWORK" \
  --network-alias order-service \
  -p 8083:8083 \
  -e JAVA_TOOL_OPTIONS="-Xmx64m -XX:+ExitOnOutOfMemoryError" \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/ \
  "$TARGET_IMAGE"

sleep 30   # allow startup attempt

# [4] Burst requests
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "Request $i: %{http_code}\n" \
    -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d '{"customerId":1,"items":[{"productId":1,"quantity":1}],...}' \
    --max-time 10
  sleep 0.3
done

# [5] Inspect
docker inspect --format '{{.State.Status}} OOMKilled={{.State.OOMKilled}}' order-service-heap-test
docker logs order-service-heap-test 2>&1 | grep -i "OutOfMemoryError" || true

# [6] Assert peers still up
curl -sf http://localhost:8080/actuator/health
curl -sf http://localhost:8081/actuator/health
curl -sf http://localhost:8082/actuator/health

# [CLEANUP]
docker rm -f order-service-heap-test
docker compose up -d order-service
```

---

## 10. Metrics & Observability

### Actuator Endpoints Quick Reference

| Endpoint | Port | What to Watch |
|----------|------|---------------|
| `/actuator/health` | 8081–8083 | Overall UP/DOWN + CB state |
| `/actuator/health/circuitBreakers` | 8083 | CB states: CLOSED/OPEN/HALF_OPEN |
| `/actuator/metrics/resilience4j.circuitbreaker.calls` | 8083 | Call counts by state |
| `/actuator/metrics/resilience4j.circuitbreaker.state` | 8083 | Current state (0=CLOSED, 1=OPEN, etc.) |
| `/actuator/circuitbreakers` | 8083 | Summary of all CBs |
| `/actuator/circuitbreakerevents` | 8083 | Recent state transition events |
| `/actuator/chaosmonkey` | any | Chaos Monkey status |
| `/eureka/apps` | 8761 | All registered service instances |
| `/actuator/metrics/http.server.requests` | any | HTTP request metrics |

### Key Metrics During Chaos

```bash
# Monitor circuit breaker state continuously
watch -n 2 "curl -s http://localhost:8083/actuator/health | \
  jq '.components.circuitBreakers.details'"

# Stream circuit breaker events
curl http://localhost:8083/actuator/circuitbreakerevents

# Monitor response times
curl "http://localhost:8083/actuator/metrics/http.server.requests?tag=uri:/api/orders" | \
  jq '.measurements[] | select(.statistic=="COUNT" or .statistic=="TOTAL_TIME")'

# Check Eureka registry
curl -s http://localhost:8761/eureka/apps | \
  python3 -c "import sys,json; [print(f\"{a['name']}: {a['instance'][0]['status']}\") \
  for a in json.load(sys.stdin)['applications']['application']]" 2>/dev/null || \
  curl -s http://localhost:8761/eureka/apps | grep -E '<status>|<app>'

# Feign call metrics (if micrometer-feign enabled)
curl "http://localhost:8083/actuator/metrics/feign.Client.execute"
```

### Recommended Monitoring Dashboard (Prometheus + Grafana)

For persistent observability, add to each service:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

Access: `http://localhost:{port}/actuator/prometheus`

Key Grafana panels:
- `resilience4j_circuitbreaker_state` — CB state timeline
- `resilience4j_circuitbreaker_calls_total` — call success/failure counts
- `http_server_requests_seconds_bucket` — response time histograms
- JVM metrics: `jvm_memory_used_bytes`, `jvm_threads_live_threads`

---

## 11. Shell Script Runbooks

### Traffic Monitor Script

```bash
#!/bin/bash
# scripts/traffic-monitor.sh
# Run in background during chaos experiments

GATEWAY=http://localhost:8080
INTERVAL=2

while true; do
  TIMESTAMP=$(date +%s)
  ORDERS_CODE=$(curl -s -o /dev/null -w "%{http_code}|%{time_total}" --max-time 3 "$GATEWAY/api/orders")
  ORDERS_HTTP=$(echo $ORDERS_CODE | cut -d'|' -f1)
  ORDERS_TIME=$(echo $ORDERS_CODE | cut -d'|' -f2)
  
  echo "$TIMESTAMP,orders,$ORDERS_HTTP,$ORDERS_TIME"
  sleep $INTERVAL
done
```

### Kill & Restart Service Script

```bash
#!/bin/bash
# scripts/restart-service.sh <service-name> <jar-path> <port>

SERVICE=$1
JAR=$2
PORT=$3

PID=$(pgrep -f "$SERVICE" | head -1)
if [ -n "$PID" ]; then
  echo "Killing $SERVICE (PID $PID)"
  kill -9 $PID
  sleep 2
fi

echo "Starting $SERVICE on port $PORT"
java -jar "$JAR" --server.port=$PORT &
echo $! > "${SERVICE}.pid"

# Wait for service to register with Eureka
echo "Waiting for $SERVICE to start..."
for i in $(seq 1 30); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://localhost:$PORT/actuator/health")
  [ "$CODE" == "200" ] && { echo "$SERVICE started (${i}s)"; exit 0; }
  sleep 1
done
echo "WARNING: $SERVICE did not start within 30s"
```

### Chaos Experiment Orchestrator

```bash
#!/bin/bash
# scripts/run-experiment.sh <scenario>

SCENARIO=$1

log() { echo "[$(date '+%H:%M:%S')] $*"; }

run_experiment() {
  log "Starting experiment: $SCENARIO"
  
  # Pre-check
  log "Verifying steady state..."
  bash scripts/verify-steady-state.sh || { log "ABORT: Not in steady state"; exit 1; }
  
  # Start monitoring
  bash scripts/traffic-monitor.sh > "results/${SCENARIO}-traffic.csv" &
  MONITOR_PID=$!
  
  # Run scenario
  log "Executing scenario..."
  bash "scenarios/${SCENARIO}.sh" 2>&1 | tee "results/${SCENARIO}.log"
  EXIT_CODE=$?
  
  # Stop monitoring
  kill $MONITOR_PID 2>/dev/null || true
  
  # Post-check
  log "Verifying recovery..."
  bash scripts/verify-steady-state.sh
  RECOVERY_CODE=$?
  
  # Summary
  if [ $EXIT_CODE -eq 0 ] && [ $RECOVERY_CODE -eq 0 ]; then
    log "✅ EXPERIMENT PASSED: $SCENARIO"
  else
    log "❌ EXPERIMENT FAILED: $SCENARIO (exit=$EXIT_CODE, recovery=$RECOVERY_CODE)"
  fi
}

mkdir -p results
run_experiment
```

---

## 12. Automated Chaos Test Suite

### Using REST Assured (Java Integration Tests)

Add to `order-service/pom.xml` test scope:

```xml
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>json-path</artifactId>
    <scope>test</scope>
</dependency>
```

Create `order-service/src/test/java/chaos/ChaosResilienceIT.java`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"chaos-monkey"})
class ChaosResilienceIT {

    private static final int ORDER_SERVICE_PORT = 8083;
    private static final int CHAOS_MONKEY_TIMEOUT_MS = 30_000;

    @BeforeEach
    void resetChaosMonkey() {
        // Disable all assaults before each test
        given()
            .contentType("application/json")
            .body("{\"latencyActive\": false, \"exceptionsActive\": false}")
            .post("http://localhost:{port}/actuator/chaosmonkey/assaults", ORDER_SERVICE_PORT)
            .then().statusCode(200);
    }

    @Test
    @DisplayName("Circuit breaker opens after 5 exceptions from downstream service")
    void circuitBreakerOpensAfterExceptions() {
        // Enable exception assault on inventory-service calls (level=1 = 100%)
        given()
            .contentType("application/json")
            .body("{\"exceptionsActive\": true, \"level\": 1}")
            .post("http://localhost:8082/actuator/chaosmonkey/assaults")
            .then().statusCode(200);

        // Send 10 requests to order placement
        List<Integer> statusCodes = IntStream.range(0, 10)
            .mapToObj(i -> given()
                .queryParam("customerId", 1)
                .queryParam("productId", 1)
                .queryParam("qty", 1)
                .get("http://localhost:{port}/api/orders/place", ORDER_SERVICE_PORT)
                .then().extract().statusCode())
            .collect(Collectors.toList());

        // Verify CB opened — last requests should be fast fallbacks (503)
        long fallbacks = statusCodes.stream()
            .filter(code -> code == 503 || code == 422)
            .count();
        assertThat(fallbacks).isGreaterThan(3);

        // Verify CB state is OPEN
        String cbState = given()
            .get("http://localhost:{port}/actuator/health", ORDER_SERVICE_PORT)
            .then().statusCode(200)
            .extract()
            .jsonPath()
            .getString("components.circuitBreakers.details.inventoryService.details.state");
        assertThat(cbState).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Latency assault triggers slow call circuit breaker")
    void slowCallCircuitBreakerTriggered() throws InterruptedException {
        // Inject 5-second latency on order-service itself
        given()
            .contentType("application/json")
            .body("{\"latencyActive\": true, \"latencyRangeStart\": 5000, \"latencyRangeEnd\": 5000, \"level\": 1}")
            .post("http://localhost:{port}/actuator/chaosmonkey/assaults", ORDER_SERVICE_PORT)
            .then().statusCode(200);

        // Send requests and track response times
        List<Long> responseTimes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            given()
                .get("http://localhost:{port}/api/orders", ORDER_SERVICE_PORT)
                .then().extract().statusCode();
            responseTimes.add(System.currentTimeMillis() - start);
            Thread.sleep(500);
        }

        // After CB opens, response times should drop dramatically
        long slowResponses = responseTimes.stream().filter(t -> t > 4000).count();
        long fastResponses = responseTimes.stream().filter(t -> t < 1000).count();
        
        // There should be a transition from slow to fast
        assertThat(fastResponses).isGreaterThan(0);
        System.out.printf("Slow: %d, Fast: %d%n", slowResponses, fastResponses);
    }

    @Test
    @DisplayName("Service remains available after downstream kill and CB opens")
    void serviceAvailableAfterDownstreamKill() {
        // This test requires a running customer-service process to kill
        // Use ProcessHandle API (Java 9+)
        ProcessHandle.allProcesses()
            .filter(p -> p.info().commandLine()
                .map(cmd -> cmd.contains("customer-service"))
                .orElse(false))
            .findFirst()
            .ifPresent(ProcessHandle::destroyForcibly);

        // Wait for CB to detect failure
        await()
            .atMost(CHAOS_MONKEY_TIMEOUT_MS, MILLISECONDS)
            .pollInterval(1, SECONDS)
            .until(() -> {
                String state = given()
                    .get("http://localhost:{port}/actuator/health", ORDER_SERVICE_PORT)
                    .then().extract()
                    .jsonPath()
                    .getString("components.circuitBreakers.details.customerService.details.state");
                return "OPEN".equals(state);
            });

        // Gateway must still be reachable
        given()
            .get("http://localhost:8080/actuator/health")
            .then().statusCode(200);
    }
}
```

### Shell-Based Automated Assertions

```bash
#!/bin/bash
# scripts/assert.sh — assertion helpers for shell-based chaos tests

# Assert HTTP status
assert_http() {
  local url=$1 expected=$2 label=${3:-$url}
  local actual
  actual=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url")
  if [ "$actual" == "$expected" ]; then
    echo "  ✅ PASS: $label returned HTTP $actual"
    return 0
  else
    echo "  ❌ FAIL: $label returned HTTP $actual (expected $expected)"
    return 1
  fi
}

# Assert circuit breaker state
assert_cb_state() {
  local service=$1 expected_state=$2 port=${3:-8083}
  local actual
  actual=$(curl -s "http://localhost:$port/actuator/health" | \
    jq -r ".components.circuitBreakers.details.${service}.details.state" 2>/dev/null)
  if [ "$actual" == "$expected_state" ]; then
    echo "  ✅ PASS: CB $service is $actual"
    return 0
  else
    echo "  ❌ FAIL: CB $service is $actual (expected $expected_state)"
    return 1
  fi
}

# Assert response time under threshold (seconds)
assert_response_time() {
  local url=$1 threshold=$2 label=${3:-$url}
  local time
  time=$(curl -s -o /dev/null -w "%{time_total}" --max-time 10 "$url")
  if (( $(echo "$time < $threshold" | bc -l) )); then
    echo "  ✅ PASS: $label responded in ${time}s (< ${threshold}s)"
    return 0
  else
    echo "  ❌ FAIL: $label responded in ${time}s (threshold: ${threshold}s)"
    return 1
  fi
}

# Wait for condition with timeout
wait_for() {
  local condition_cmd=$1 timeout=${2:-30} label=${3:-condition}
  local elapsed=0
  while [ $elapsed -lt $timeout ]; do
    eval "$condition_cmd" && { echo "  ✅ $label satisfied in ${elapsed}s"; return 0; }
    sleep 2; elapsed=$((elapsed+2))
  done
  echo "  ❌ TIMEOUT: $label not satisfied within ${timeout}s"
  return 1
}
```

---

## 13. Recovery Procedures

### Standard Recovery Checklist

After every chaos experiment:

1. **Disable all chaos assaults:**
   ```bash
   for PORT in 8081 8082 8083; do
     curl -s -X POST "http://localhost:$PORT/actuator/chaosmonkey/assaults" \
       -H "Content-Type: application/json" \
       -d '{"latencyActive":false,"exceptionsActive":false,"killApplicationActive":false,"memoryActive":false}'
   done
   ```

2. **Remove all Toxiproxy toxics:**
   ```bash
   for PROXY in customer-service inventory-service order-service; do
     # List and delete all toxics for this proxy
     curl -s http://localhost:8474/proxies/$PROXY/toxics | \
       jq -r '.[].name' | \
       xargs -I{} curl -s -X DELETE "http://localhost:8474/proxies/$PROXY/toxics/{}"
   done
   ```

3. **Restart any killed services:**
   ```bash
   bash scripts/restart-service.sh customer-service \
     customer-service/target/customer-service-*.jar 8081
   bash scripts/restart-service.sh inventory-service \
     inventory-service/target/inventory-service-*.jar 8082
   ```

4. **Wait for Eureka re-registration (~30s):**
   ```bash
   sleep 30
   curl -s http://localhost:8761/eureka/apps | grep -c "<status>UP</status>"
   # Expect: 4
   ```

5. **Verify circuit breakers return to CLOSED:**
   ```bash
   curl -s http://localhost:8083/actuator/health | \
     jq '.components.circuitBreakers.details'
   ```

6. **Re-seed H2 databases if needed** (services restart with empty DBs):
   ```bash
   # customer-service
   curl -X POST http://localhost:8081/api/customers \
     -H "Content-Type: application/json" \
     -d '{"name":"Test Customer","email":"test@example.com"}'
   
   # inventory-service
   curl -X POST http://localhost:8082/api/inventory \
     -H "Content-Type: application/json" \
     -d '{"productId":1,"quantity":100}'
   ```

7. **Confirm steady state:**
   ```bash
   bash scripts/verify-steady-state.sh
   ```

### Circuit Breaker Force-Reset

If a circuit breaker is stuck in OPEN state after recovery:

```bash
# Resilience4j does not expose a direct "reset" endpoint out of the box.
# Option 1: Restart the service (CB resets on startup)
bash scripts/restart-service.sh order-service order-service/target/*.jar 8083

# Option 2: Wait for automaticTransitionFromOpenToHalfOpenEnabled to kick in
# (fires after waitDurationInOpenState = 10s)
sleep 15
curl -s http://localhost:8083/actuator/health | jq '.components.circuitBreakers'
```

---

## 14. CI/CD Integration

### GitHub Actions Scheduled Chaos Suite

Create `.github/workflows/chaos-tests.yml`:

```yaml
name: Chaos Engineering Tests

on:
  schedule:
    - cron: '0 2 * * 1'   # Every Monday at 2 AM UTC (off-hours gameday)
  workflow_dispatch:        # Manual trigger for gameday sessions
    inputs:
      scenario:
        description: 'Scenario to run (all, s1, s2, s3, s4, s5, s6)'
        required: true
        default: 'all'

jobs:
  chaos-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 60

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Build all services
        run: mvn -q package -DskipTests

      - name: Start Toxiproxy
        run: |
          docker run -d --name toxiproxy \
            -p 8474:8474 -p 18081:18081 -p 18082:18082 \
            ghcr.io/shopify/toxiproxy:2.9.0

      - name: Start all services
        run: |
          bash run.sh
          sleep 40  # Wait for Eureka registration

      - name: Verify steady state
        run: bash scripts/verify-steady-state.sh

      - name: Run chaos scenarios
        run: |
          SCENARIO="${{ github.event.inputs.scenario || 'all' }}"
          mkdir -p results
          
          if [ "$SCENARIO" == "all" ] || [ "$SCENARIO" == "s2" ]; then
            bash scripts/run-experiment.sh s2-customer-service-kill
          fi
          if [ "$SCENARIO" == "all" ] || [ "$SCENARIO" == "s3" ]; then
            bash scripts/run-experiment.sh s3-inventory-latency
          fi
          if [ "$SCENARIO" == "all" ] || [ "$SCENARIO" == "s5" ]; then
            bash scripts/run-experiment.sh s5-cascade-failure
          fi

      - name: Upload results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: chaos-results-${{ github.run_number }}
          path: results/

      - name: Final steady state verification
        run: bash scripts/verify-steady-state.sh

      - name: Notify on failure
        if: failure()
        run: |
          echo "::error::Chaos tests failed — check uploaded results artifacts"
```

### Pre-Merge Chaos Smoke Test

For faster feedback on PRs touching resilience code:

```yaml
name: Resilience Smoke Test

on:
  pull_request:
    paths:
      - 'order-service/src/**'
      - '**/application*.yml'
      - '**/pom.xml'

jobs:
  resilience-smoke:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {java-version: '25', distribution: 'temurin'}
      - run: mvn -q package -DskipTests
      - run: bash run.sh && sleep 40
      - run: bash scripts/verify-steady-state.sh
      - name: Quick circuit breaker smoke test
        run: bash scenarios/s2-customer-service-kill.sh
      - run: bash scripts/verify-steady-state.sh
```

---

## 15. File List – Changes & New Files

### New Files to Create

```
copilot-spring-boot-demo/
├── scripts/
│   ├── verify-steady-state.sh          # Steady-state assertion script
│   ├── traffic-monitor.sh              # Background CSV traffic logger
│   ├── restart-service.sh              # Kill + restart a named service
│   ├── run-experiment.sh               # Experiment orchestrator with pre/post checks
│   └── assert.sh                       # Assertion helper functions
├── scenarios/
│   ├── s1-eureka-kill.sh               # Scenario 1: Eureka kill
│   ├── s2-customer-service-kill.sh     # Scenario 2: Customer service kill
│   ├── s3-inventory-latency.sh         # Scenario 3: Inventory latency injection
│   ├── s4-gateway-overload.sh          # Scenario 4: Gateway overload
│   ├── s5-cascade-failure.sh           # Scenario 5: Cascade failure
│   ├── s6-network-partition.sh         # Scenario 6: Toxiproxy network partition
│   ├── s7-jvm-heap-exhaustion.sh       # Scenario 7: JVM heap exhaustion / OOM simulation
│   └── s8-network-packet-drop.sh      # Scenario 8: pumba netem packet loss
├── .github/
│   └── workflows/
│       ├── chaos-tests.yml             # Scheduled weekly chaos suite
│       └── resilience-smoke.yml        # PR-triggered smoke test
└── docs/
    └── CHAOS_TESTING_PLAN.md           # This document
```

### Files to Modify

```
copilot-spring-boot-demo/
├── order-service/
│   ├── pom.xml                         # Add: resilience4j, actuator, aop, rest-assured
│   ├── src/main/resources/
│   │   ├── application.yml             # Add: Feign timeouts, Resilience4j config, Actuator
│   │   └── application-chaos-monkey.yml # NEW: Chaos Monkey profile config
│   └── src/main/java/.../
│       ├── client/
│       │   ├── CustomerClient.java     # Add: fallback = CustomerClientFallback.class
│       │   ├── CustomerClientFallback.java # NEW: Feign fallback
│       │   ├── InventoryClient.java    # Add: fallback = InventoryClientFallback.class
│       │   └── InventoryClientFallback.java # NEW: Feign fallback
│       └── service/
│           └── OrderService.java       # Add: @CircuitBreaker, @Retry, @Bulkhead annotations
├── customer-service/
│   ├── pom.xml                         # Add: chaos-monkey-spring-boot, actuator
│   ├── src/main/resources/
│   │   ├── application.yml             # Add: Actuator endpoints
│   │   └── application-chaos-monkey.yml # NEW: Chaos Monkey profile config
├── inventory-service/
│   ├── pom.xml                         # Add: chaos-monkey-spring-boot, actuator
│   ├── src/main/resources/
│   │   ├── application.yml             # Add: Actuator endpoints
│   │   └── application-chaos-monkey.yml # NEW: Chaos Monkey profile config
└── api-gateway/
    ├── pom.xml                         # Add: chaos-monkey-spring-boot (optional)
    └── src/main/resources/
        └── application.yml             # Add: Gateway timeouts, route CB filters, response-timeout
```

---

## Appendix A: Chaos Experiment Log Template

Use this template to record each experiment run:

```markdown
## Experiment: [Scenario ID & Name]
**Date:** YYYY-MM-DD  
**Operator:**  
**Duration:**  

### Pre-Experiment State
- [ ] Steady state verified
- All services: UP / DOWN / DEGRADED

### Chaos Injected
- Tool used: Chaos Monkey / Toxiproxy / Shell
- Target: 
- Fault type: 
- Parameters: 

### Observations
| Time | Event | HTTP Status | Response Time | CB State |
|------|-------|------------|---------------|----------|
| t+0  | Chaos started | | | |
| t+Xs | CB opened | | | OPEN |
| t+Ys | Chaos stopped | | | |
| t+Zs | CB closed | | | CLOSED |

### Results
- Hypothesis confirmed? YES / NO
- Fallback behaviour correct? YES / NO
- Recovery time: Xs

### Weaknesses Found
- 

### Action Items
- [ ] 
```

---

## Appendix B: Virtual Threads (JDK 25) Considerations

This application enables virtual threads via `spring.threads.virtual.enabled=true` (Spring Boot 3.2+). Key chaos implications:

1. **Thread exhaustion scenarios differ:** Virtual threads are cheap (millions possible). Classical thread pool exhaustion via high concurrency is much harder to trigger. Focus chaos on **connection pool exhaustion** (HTTP client, datasource) instead.

2. **Pinning:** Synchronized blocks inside virtual threads pin the carrier thread. If `customer-service` uses synchronized methods and is slow, this can still exhaust carrier threads. Check for `synchronized` in H2 JDBC driver internals.

3. **Bulkhead configuration:** Resilience4j's `ThreadPoolBulkhead` uses platform threads. Consider using `SemaphoreBulkhead` instead, which works better with virtual threads (counts concurrent semaphore acquisitions, not threads).

4. **MemoryAssault:** With virtual threads, memory usage profiles differ. The Chaos Monkey memory assault targets heap — this behaviour is the same regardless of thread model.

---

*This plan is a living document. Update it after each experiment with findings, action items completed, and newly discovered failure modes.*
