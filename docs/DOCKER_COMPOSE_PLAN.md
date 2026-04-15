# Docker Compose Implementation Plan
## Containerizing the E-Commerce Microservices Application

---

## Quick Start (After Implementation)

```bash
# 1. Build all Docker images
docker compose build

# 2. Start all services in detached mode
docker compose up -d

# 3. Watch Eureka register all services (~60s)
docker compose logs -f eureka-server

# 4. Verify API Gateway is routing correctly
curl http://localhost:8080/api/customers

# 5. Tear down (data is lost — H2 is in-memory)
docker compose down
```

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [Dockerfile Design](#2-dockerfile-design)
3. [docker-compose.yml Design](#3-docker-composeyml-design)
4. [Actuator Dependency](#4-actuator-dependency)
5. [Application Config Overrides](#5-application-config-overrides)
6. [Build & Run Workflow](#6-build--run-workflow)
7. [Environment Profiles](#7-environment-profiles)
8. [Networking Considerations](#8-networking-considerations)
9. [Known Caveats & Limitations](#9-known-caveats--limitations)
10. [Testing the Deployment](#10-testing-the-deployment)
11. [File List](#11-file-list)

---

## 1. Overview & Goals

### What This Accomplishes

This plan containerizes five Spring Boot microservices into a reproducible Docker Compose environment:

| Service | Port | Role |
|---|---|---|
| `eureka-server` | 8761 | Service registry (Netflix Eureka) |
| `api-gateway` | 8080 | Edge router (Spring Cloud Gateway) |
| `customer-service` | 8081 | Customer CRUD, H2 in-memory DB |
| `inventory-service` | 8082 | Product/stock management, H2 in-memory DB |
| `order-service` | 8083 | Order lifecycle, calls customer & inventory via OpenFeign |

### Why Docker Compose

- **Single command startup** — `docker compose up -d` replaces running 5 terminal windows
- **Deterministic networking** — services find each other by container name (not `localhost`)
- **Dependency ordering** — `depends_on` with health checks prevents race conditions at startup
- **Portable** — any machine with Docker can run the full stack without installing Java or Maven locally
- **Demo-ready** — the H2 in-memory databases are fine for demonstrations; no volume management required

---

## 2. Dockerfile Design

### Strategy: Per-Service Multi-Stage Builds

Each of the five services gets its own `Dockerfile` located in its module directory. All five use the same two-stage pattern:

- **Stage 1 (build):** `maven:3.9-eclipse-temurin-21` — resolves dependencies and compiles the JAR
- **Stage 2 (runtime):** `eclipse-temurin:21-jre-alpine` — minimal ~200 MB image that runs the JAR

The Docker build context is **always the project root** (set via `context: .` in `docker-compose.yml`). This lets each Dockerfile COPY files from any module, and lets Maven resolve the parent POM and sibling modules.

### The JDK 21 / Java 25 Build Constraint

The parent POM (`pom.xml`) declares `<release>25</release>` in the compiler plugin, which targets Java 25 language features and bytecode. JDK 21 (`maven:3.9-eclipse-temurin-21`) cannot compile with `--release 25`. To work around this in Docker builds, pass `-Dmaven.compiler.release=21` to Maven, which overrides the POM setting without modifying source files.

> This mirrors what the local `build.sh` does: it uses Temurin JDK 21's `javac` against the same source tree. The code does not use Java 25-specific language syntax; the version declaration is aspirational.

### Layer Caching Strategy

The Dockerfile copies all `pom.xml` files first and runs `dependency:go-offline` before copying source code. This means Docker layer cache is only invalidated for the dependency-download layer when `pom.xml` files change — not on every source edit.

### Template Dockerfile (shown for `customer-service`)

```dockerfile
# customer-service/Dockerfile
# Build context must be the project root: docker build -f customer-service/Dockerfile .

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy all POM files first for dependency caching
COPY pom.xml .
COPY eureka-server/pom.xml      eureka-server/
COPY api-gateway/pom.xml        api-gateway/
COPY customer-service/pom.xml   customer-service/
COPY inventory-service/pom.xml  inventory-service/
COPY order-service/pom.xml      order-service/

# Pre-fetch dependencies (cached layer — only invalidated when pom.xml changes)
RUN mvn dependency:go-offline -B -q -Dmaven.compiler.release=21

# Copy source for this module only
COPY customer-service/src customer-service/src

# Build: -pl targets this module, -am builds required upstream modules (parent)
RUN mvn clean package -DskipTests -B \
    -pl customer-service -am \
    -Dmaven.compiler.release=21

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /build/customer-service/target/customer-service-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Per-Service Dockerfile Variations

The only differences between each service's Dockerfile are:

| Service | `COPY src` path | `-pl` value | JAR name | `EXPOSE` port |
|---|---|---|---|---|
| `eureka-server` | `eureka-server/src` | `eureka-server` | `eureka-server-1.0.0-SNAPSHOT.jar` | `8761` |
| `api-gateway` | `api-gateway/src` | `api-gateway` | `api-gateway-1.0.0-SNAPSHOT.jar` | `8080` |
| `customer-service` | `customer-service/src` | `customer-service` | `customer-service-1.0.0-SNAPSHOT.jar` | `8081` |
| `inventory-service` | `inventory-service/src` | `inventory-service` | `inventory-service-1.0.0-SNAPSHOT.jar` | `8082` |
| `order-service` | `order-service/src` | `order-service` | `order-service-1.0.0-SNAPSHOT.jar` | `8083` |

> **Note for `order-service`:** This service calls `customer-service` and `inventory-service` via OpenFeign. No Dockerfile change is needed — service resolution happens at runtime through Eureka, not at build time.

---

## 3. docker-compose.yml Design

### Full `docker-compose.yml`

```yaml
# docker-compose.yml
# Root of project. Run: docker compose up -d

services:

  # ── Eureka Server (Service Registry) ─────────────────────────────────────
  eureka-server:
    build:
      context: .
      dockerfile: eureka-server/Dockerfile
    container_name: eureka-server
    ports:
      - "${EUREKA_SERVER_PORT:-8761}:8761"
    networks:
      - ecommerce-net
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-docker}
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8761/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  # ── Customer Service ──────────────────────────────────────────────────────
  customer-service:
    build:
      context: .
      dockerfile: customer-service/Dockerfile
    container_name: customer-service
    ports:
      - "${CUSTOMER_SERVICE_PORT:-8081}:8081"
    networks:
      - ecommerce-net
    depends_on:
      eureka-server:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-docker}
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
      - EUREKA_INSTANCE_PREFER_IP_ADDRESS=true
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  # ── Inventory Service ─────────────────────────────────────────────────────
  inventory-service:
    build:
      context: .
      dockerfile: inventory-service/Dockerfile
    container_name: inventory-service
    ports:
      - "${INVENTORY_SERVICE_PORT:-8082}:8082"
    networks:
      - ecommerce-net
    depends_on:
      eureka-server:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-docker}
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
      - EUREKA_INSTANCE_PREFER_IP_ADDRESS=true
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8082/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  # ── Order Service ─────────────────────────────────────────────────────────
  order-service:
    build:
      context: .
      dockerfile: order-service/Dockerfile
    container_name: order-service
    ports:
      - "${ORDER_SERVICE_PORT:-8083}:8083"
    networks:
      - ecommerce-net
    depends_on:
      eureka-server:
        condition: service_healthy
      customer-service:
        condition: service_healthy
      inventory-service:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-docker}
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
      - EUREKA_INSTANCE_PREFER_IP_ADDRESS=true
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8083/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  # ── API Gateway ───────────────────────────────────────────────────────────
  api-gateway:
    build:
      context: .
      dockerfile: api-gateway/Dockerfile
    container_name: api-gateway
    ports:
      - "${API_GATEWAY_PORT:-8080}:8080"
    networks:
      - ecommerce-net
    depends_on:
      eureka-server:
        condition: service_healthy
      customer-service:
        condition: service_healthy
      inventory-service:
        condition: service_healthy
      order-service:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-docker}
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
      - EUREKA_INSTANCE_PREFER_IP_ADDRESS=true
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

networks:
  ecommerce-net:
    driver: bridge
    name: ecommerce-net
```

### Key Design Decisions

#### `depends_on` with `condition: service_healthy`
Standard `depends_on` only waits for a container to *start*, not for the application to be ready. Using `condition: service_healthy` forces Docker Compose to wait until the health check passes before starting the dependent service. This eliminates the "Eureka not yet ready" startup failures.

#### Startup Chain
```
eureka-server (healthy)
    ├── customer-service (healthy)
    ├── inventory-service (healthy)
    │       └── order-service (healthy)  ← waits for eureka + customer + inventory
    │               └── api-gateway      ← waits for all four
    └── api-gateway
```

#### Health Check Command
`wget` from BusyBox (included in Alpine) is used instead of `curl` (not installed by default on alpine). The `grep -q '"status":"UP"'` check validates the Spring Boot Actuator response body, not just the HTTP 200 status.

#### Volume Strategy
No persistent volumes are defined. All five services use H2 in-memory databases (`jdbc:h2:mem:<dbname>`). Data is lost on container restart. **This is appropriate for demos.** See [Section 9](#9-known-caveats--limitations) for implications.

#### `.env` File
Port numbers and the Spring profile are read from a `.env` file at the project root (created in [Section 11](#11-file-list)). Docker Compose reads `.env` automatically. Override any variable at runtime:

```bash
API_GATEWAY_PORT=9090 docker compose up -d
```

---

## 4. Actuator Dependency

The health checks in `docker-compose.yml` call `/actuator/health` on each service. This endpoint is provided by `spring-boot-starter-actuator`, which is **not yet present** in any of the five `pom.xml` files.

### Required Change: Add to All Five `pom.xml` Files

Add the following dependency to the `<dependencies>` section of each module POM:

```xml
<!-- Required for Docker health checks and observability -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Affected Files

- `eureka-server/pom.xml`
- `api-gateway/pom.xml`
- `customer-service/pom.xml`
- `inventory-service/pom.xml`
- `order-service/pom.xml`

### Actuator Endpoint Exposure

By default, Spring Boot 3.x exposes only the `health` and `info` endpoints over HTTP. No additional configuration is needed for Docker health checks. If you want to also expose metrics, add this to the relevant `application-docker.properties` files:

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
```

---

## 5. Application Config Overrides

### The Core Problem: `localhost` vs. Container Hostnames

All five services currently point Eureka at `http://localhost:8761/eureka/`. Inside a Docker network, `localhost` resolves to the container itself — not the `eureka-server` container. This must be overridden.

### Solution: Spring Profile `docker`

Each service gets an `application-docker.properties` file (or `application-docker.yml`) that overrides only the values that differ in Docker. The `SPRING_PROFILES_ACTIVE=docker` environment variable in `docker-compose.yml` activates this profile.

Spring's profile-specific property files are additive — `application-docker.properties` merges with and overrides `application.properties`.

### `application-docker.properties` for `customer-service`, `inventory-service`, `order-service`

```properties
# Overrides for Docker Compose deployment
# eureka-server is the container name on ecommerce-net
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
eureka.instance.prefer-ip-address=true

# Disable H2 console in containerized environments (security)
spring.h2.console.enabled=false
```

### `application-docker.yml` for `eureka-server`

```yaml
eureka:
  instance:
    hostname: eureka-server   # Use container name, not 'localhost'
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

### `application-docker.yml` for `api-gateway`

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
  instance:
    prefer-ip-address: true
```

> **Note:** The `lb://customer-service` style URIs in `api-gateway/application.yml` already reference Eureka-registered names, not hostnames. No change is needed for the gateway routing rules themselves.

### Environment Variable Override (Alternative to Profile Files)

Docker Compose also injects `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` as an environment variable. Spring Boot's [Relaxed Binding](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables) maps this to `eureka.client.service-url.defaultZone` automatically. This acts as an additional safety net even if the `docker` profile is not active.

---

## 6. Build & Run Workflow

### Prerequisites

- Docker Desktop 4.x+ (or Docker Engine 24+ with Compose plugin)
- `docker compose version` ≥ 2.20 (for `condition: service_healthy` syntax)

### Step 1: Add Actuator and Profile Config Files

Follow Sections 4 and 5. Ensure all `pom.xml` files have `spring-boot-starter-actuator` and each module has an `application-docker.properties`.

### Step 2: Create Dockerfiles and Compose Files

Create all files listed in [Section 11](#11-file-list).

### Step 3: Build All Images

```bash
# From the project root. Builds all 5 images.
docker compose build

# To rebuild a single service after a code change:
docker compose build customer-service

# To see build progress in detail:
docker compose build --progress=plain
```

> First build takes ~5–10 minutes (downloading Maven dependencies). Subsequent builds use layer cache and take ~30–60 seconds.

### Step 4: Start All Services

```bash
# Start in detached mode (background)
docker compose up -d

# Or: build and start in one command
docker compose up -d --build
```

### Step 5: Monitor Startup

```bash
# Watch all services startup logs
docker compose logs -f

# Watch only eureka (to see when services register)
docker compose logs -f eureka-server

# Check health status of all containers
docker compose ps
```

Expected output from `docker compose ps` when all services are healthy:
```
NAME                 STATUS
eureka-server        Up (healthy)
customer-service     Up (healthy)
inventory-service    Up (healthy)
order-service        Up (healthy)
api-gateway          Up (healthy)
```

### Step 6: Run the Smoke Tests

See [Section 10](#10-testing-the-deployment) for `curl` commands.

### Step 7: Teardown

```bash
# Stop and remove containers (preserves images)
docker compose down

# Stop, remove containers AND images
docker compose down --rmi all

# Also remove the network
docker compose down --remove-orphans
```

### Rebuild After Source Changes

```bash
# Rebuild only the changed service and restart it
docker compose build order-service && docker compose up -d order-service
```

---

## 7. Environment Profiles

### `.env` File (Project Root)

Docker Compose automatically reads `.env` from the directory where you run `docker compose`. Define defaults here:

```dotenv
# .env
# Docker Compose environment defaults for ecommerce microservices

SPRING_PROFILES_ACTIVE=docker

EUREKA_SERVER_PORT=8761
API_GATEWAY_PORT=8080
CUSTOMER_SERVICE_PORT=8081
INVENTORY_SERVICE_PORT=8082
ORDER_SERVICE_PORT=8083
```

### Spring Profile: `docker`

Activate the Docker-specific configuration by setting `SPRING_PROFILES_ACTIVE=docker` (already in `.env`). Spring Boot merges `application.properties` with `application-docker.properties` at startup.

**Profile precedence (highest to lowest):**
1. Docker Compose `environment:` variables (e.g., `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`)
2. `application-docker.properties` (profile-specific)
3. `application.properties` (base config)

This means you can override any value at `docker compose up` time without touching files:

```bash
# Override Eureka URL for a custom network
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://my-eureka:8761/eureka/ docker compose up -d
```

### Local Development (Non-Docker)

The `application.properties` files are not modified by this plan. All existing `localhost:8761` references remain valid for local `./run.sh` or `mvn spring-boot:run` workflows. The `docker` profile is only activated inside containers.

---

## 8. Networking Considerations

### How Docker Bridge Networking Works Here

All five containers join a single user-defined bridge network: `ecommerce-net`. On this network:

- Each container is reachable by its **container name** as a hostname
- `eureka-server` resolves to the Eureka container's IP
- `customer-service`, `inventory-service`, `order-service`, `api-gateway` each resolve to their respective containers

Docker's embedded DNS server handles this automatically — no `/etc/hosts` editing required.

### Eureka + Docker: How Service Discovery Works

```
[order-service container]
  │
  │ 1. Registers with Eureka at startup:
  │    POST http://eureka-server:8761/eureka/apps/ORDER-SERVICE
  │    (IP: container's docker network IP, e.g., 172.18.0.5)
  │
  │ 2. OpenFeign calls customer-service:
  │    GET lb://customer-service/api/customers/42
  │
  │ 3. Spring Cloud LoadBalancer asks Eureka:
  │    GET http://eureka-server:8761/eureka/apps/CUSTOMER-SERVICE
  │    → returns {hostname: "172.18.0.3", port: 8081}
  │
  │ 4. Actual HTTP call:
  │    GET http://172.18.0.3:8081/api/customers/42
  └──────────────────────────────────────────────────────────
```

The key configuration that makes this work inside Docker is:
```properties
eureka.instance.prefer-ip-address=true
```

Without this, Eureka registers the container's hostname (a Docker-assigned hash like `a3f2b1c4d5e6`). Other services may fail DNS resolution on this hash. With `prefer-ip-address=true`, Eureka registers the container's IP address directly, which is always routable within `ecommerce-net`.

### API Gateway Routing

The `api-gateway` uses `lb://service-name` URIs (e.g., `lb://customer-service`). Spring Cloud Gateway resolves these through Eureka using the same IP-based mechanism above. No gateway configuration changes are needed beyond the Eureka URL override.

### Port Exposure

Ports are exposed to the Docker host (your laptop) via `ports:` in `docker-compose.yml`. Containers communicate with each other on internal ports — the host port mapping is only for external access:

| From | To | Port |
|---|---|---|
| Your browser / curl | `api-gateway` | `localhost:8080` |
| Your browser / curl | Eureka dashboard | `localhost:8761` |
| `order-service` → `customer-service` | Internal | `8081` (no host mapping needed) |

---

## 9. Known Caveats & Limitations

### H2 In-Memory Databases

**Impact:** All data (customers, products, orders) is lost every time a container restarts.

**Why acceptable:** This is a demo application. The `DataInitializer` beans seed test data on startup.

**Migration path to persistence:** Replace H2 with PostgreSQL. Add a `postgres` service to `docker-compose.yml`, add `spring-boot-starter-data-jpa` with PostgreSQL driver, and add a named volume. This is a separate, non-trivial effort.

### Java 25 Target vs. JDK 21 Build

**Impact:** The parent POM sets `<release>25</release>`. The Docker build overrides this with `-Dmaven.compiler.release=21`. The compiled bytecode targets Java 21, not 25.

**Why acceptable:** The source code does not use Java 25-exclusive language features. `spring.threads.virtual.enabled=true` (virtual threads) is a JVM runtime feature, not a compiler feature, and is fully supported on JDK 21+.

**Caveat:** If Java 25 language features (e.g., new syntax) are added to the source code later, Docker builds will break. The fix is to switch the build image to `maven:3.9-eclipse-temurin-25` when available and when Lombok compatibility is confirmed.

### Lombok Compatibility

**Impact:** Lombok 1.18.x (bundled with Spring Boot 3.5.0 starters) uses annotation processing that is incompatible with JDK 25's stricter internal API restrictions. Using `maven:3.9-eclipse-temurin-21` in the Docker build stage avoids this entirely.

**Resolution tracking:** Watch [Lombok changelog](https://projectlombok.org/changelog) for a release that adds JDK 25 support. Once available, update both `build.sh` and Dockerfiles.

### Startup Time

Cold-start (first `docker compose up -d`) takes approximately **90–120 seconds** for all services to reach `healthy` status. This is primarily:
- ~30s for Eureka to start and pass health checks
- ~15–30s per business service to register with Eureka
- ~20s for API Gateway to discover all services and start routing

This is normal for Spring Boot applications and Eureka. The `start_period: 30s` in health check definitions gives each service grace time before Docker counts failed checks.

### H2 Console Disabled in Docker

`spring.h2.console.enabled=false` is set in `application-docker.properties`. The H2 console is web-based and useful locally, but serving it from a container without authentication is a security risk. Leave it disabled.

### No TLS / Authentication

This setup has no TLS between services and no API authentication on the gateway. It is suitable for local demos only. Do not expose these ports publicly.

---

## 10. Testing the Deployment

Run these commands after `docker compose up -d` and all services show `(healthy)`.

### Health Check All Services

```bash
# Eureka
curl -s http://localhost:8761/actuator/health | python3 -m json.tool

# API Gateway
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# Customer Service (direct)
curl -s http://localhost:8081/actuator/health | python3 -m json.tool

# Inventory Service (direct)
curl -s http://localhost:8082/actuator/health | python3 -m json.tool

# Order Service (direct)
curl -s http://localhost:8083/actuator/health | python3 -m json.tool
```

Expected response for each:
```json
{
  "status": "UP"
}
```

### Verify Eureka Registry

Open in browser: **http://localhost:8761**

Or via API:
```bash
curl -s http://localhost:8761/eureka/apps | grep -o '<app>.*</app>' | grep -oP '(?<=<name>)[^<]+'
```

Expected output:
```
API-GATEWAY
CUSTOMER-SERVICE
INVENTORY-SERVICE
ORDER-SERVICE
```

### Test via API Gateway (Port 8080)

```bash
# List all customers (via gateway → customer-service)
curl -s http://localhost:8080/api/customers | python3 -m json.tool

# List all products (via gateway → inventory-service)
curl -s http://localhost:8080/api/products | python3 -m json.tool

# List all orders (via gateway → order-service)
curl -s http://localhost:8080/api/orders | python3 -m json.tool
```

### Create a Customer and Place an Order

```bash
# 1. Create a customer
curl -s -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Doe","email":"jane@example.com","phone":"555-1234"}' \
  | python3 -m json.tool

# 2. Check inventory (find a product ID from the seeded data)
curl -s http://localhost:8080/api/products | python3 -m json.tool

# 3. Place an order (adjust customerId and productId from above responses)
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"productId":1,"quantity":2}' \
  | python3 -m json.tool
```

### Verify OpenFeign Cross-Service Calls

The order creation above exercises the full inter-service path:
```
curl → api-gateway (8080)
        → order-service (8083)
            → customer-service (8081) via OpenFeign + Eureka
            → inventory-service (8082) via OpenFeign + Eureka
```

If the order returns successfully with a populated customer name and product details, all service-to-service communication through Eureka is working.

### Container Logs

```bash
# Tail logs for a specific service
docker compose logs -f order-service

# See last 50 lines from all services
docker compose logs --tail=50
```

---

## 11. File List

### Files to Create

| Path | Description |
|---|---|
| `docker-compose.yml` | Main Compose file (all 5 services, network, health checks) |
| `.env` | Default environment variables (ports, Spring profile) |
| `eureka-server/Dockerfile` | Multi-stage build for Eureka Server |
| `api-gateway/Dockerfile` | Multi-stage build for API Gateway |
| `customer-service/Dockerfile` | Multi-stage build for Customer Service |
| `inventory-service/Dockerfile` | Multi-stage build for Inventory Service |
| `order-service/Dockerfile` | Multi-stage build for Order Service |
| `eureka-server/src/main/resources/application-docker.yml` | Docker profile: override `hostname` to `eureka-server` |
| `api-gateway/src/main/resources/application-docker.yml` | Docker profile: override Eureka URL |
| `customer-service/src/main/resources/application-docker.properties` | Docker profile: override Eureka URL, disable H2 console |
| `inventory-service/src/main/resources/application-docker.properties` | Docker profile: override Eureka URL, disable H2 console |
| `order-service/src/main/resources/application-docker.properties` | Docker profile: override Eureka URL, disable H2 console |

### Files to Modify

| Path | Change |
|---|---|
| `eureka-server/pom.xml` | Add `spring-boot-starter-actuator` dependency |
| `api-gateway/pom.xml` | Add `spring-boot-starter-actuator` dependency |
| `customer-service/pom.xml` | Add `spring-boot-starter-actuator` dependency |
| `inventory-service/pom.xml` | Add `spring-boot-starter-actuator` dependency |
| `order-service/pom.xml` | Add `spring-boot-starter-actuator` dependency |

### Files NOT Modified

| Path | Reason |
|---|---|
| `*/src/main/resources/application.properties` | Local dev config preserved unchanged |
| `*/src/main/resources/application.yml` | Local dev config preserved unchanged |
| `pom.xml` (root) | Build config unchanged; Docker overrides via `-Dmaven.compiler.release=21` |
| `build.sh` | Local build script unchanged |
| `run.sh` | Local run script unchanged |
| All Java source files | No source changes required |

### `.dockerignore` (Recommended)

Create `.dockerignore` at the project root to speed up Docker builds by excluding unnecessary files from the build context:

```dockerignore
# .dockerignore
.git
.gitignore
*.md
docs/
target/
*/target/
*.log
.mvn/wrapper/maven-wrapper.jar
```

---

## Appendix: Complete Dockerfile for Each Service

### `eureka-server/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY eureka-server/pom.xml      eureka-server/
COPY api-gateway/pom.xml        api-gateway/
COPY customer-service/pom.xml   customer-service/
COPY inventory-service/pom.xml  inventory-service/
COPY order-service/pom.xml      order-service/
RUN mvn dependency:go-offline -B -q -Dmaven.compiler.release=21
COPY eureka-server/src eureka-server/src
RUN mvn clean package -DskipTests -B -pl eureka-server -am -Dmaven.compiler.release=21

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /build/eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `api-gateway/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY eureka-server/pom.xml      eureka-server/
COPY api-gateway/pom.xml        api-gateway/
COPY customer-service/pom.xml   customer-service/
COPY inventory-service/pom.xml  inventory-service/
COPY order-service/pom.xml      order-service/
RUN mvn dependency:go-offline -B -q -Dmaven.compiler.release=21
COPY api-gateway/src api-gateway/src
RUN mvn clean package -DskipTests -B -pl api-gateway -am -Dmaven.compiler.release=21

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /build/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `customer-service/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY eureka-server/pom.xml      eureka-server/
COPY api-gateway/pom.xml        api-gateway/
COPY customer-service/pom.xml   customer-service/
COPY inventory-service/pom.xml  inventory-service/
COPY order-service/pom.xml      order-service/
RUN mvn dependency:go-offline -B -q -Dmaven.compiler.release=21
COPY customer-service/src customer-service/src
RUN mvn clean package -DskipTests -B -pl customer-service -am -Dmaven.compiler.release=21

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /build/customer-service/target/customer-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `inventory-service/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY eureka-server/pom.xml      eureka-server/
COPY api-gateway/pom.xml        api-gateway/
COPY customer-service/pom.xml   customer-service/
COPY inventory-service/pom.xml  inventory-service/
COPY order-service/pom.xml      order-service/
RUN mvn dependency:go-offline -B -q -Dmaven.compiler.release=21
COPY inventory-service/src inventory-service/src
RUN mvn clean package -DskipTests -B -pl inventory-service -am -Dmaven.compiler.release=21

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /build/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `order-service/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY eureka-server/pom.xml      eureka-server/
COPY api-gateway/pom.xml        api-gateway/
COPY customer-service/pom.xml   customer-service/
COPY inventory-service/pom.xml  inventory-service/
COPY order-service/pom.xml      order-service/
RUN mvn dependency:go-offline -B -q -Dmaven.compiler.release=21
COPY order-service/src order-service/src
RUN mvn clean package -DskipTests -B -pl order-service -am -Dmaven.compiler.release=21

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /build/order-service/target/order-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```
