# Microservice Alignment Options

Based on the component analysis in [explanation.md](explanation.md), here are three approaches to decomposing the monolith into microservices. Each option trades off migration complexity, team autonomy, and operational overhead differently.

---

## Current Coupling Summary

The coupling in this monolith is concentrated in one place:

- **OrderService** injects both `CustomerService` and `ProductService`
- `createOrder()` validates the customer, looks up products, and reserves stock — all synchronously
- `updateOrderStatus()` and `deleteOrder()` restore stock on cancellation
- **Customer and Product domains are fully independent of each other**
- Order already uses ID references (`customerId`, `productId` as `Long`) rather than JPA entity relationships — a head start for decomposition

---

## Option A: 3-Service Domain Split (Recommended)

Split along the three bounded contexts that already exist in the package structure.

| Service | Source Package | Database | Team |
|---------|---------------|----------|------|
| **Customer Service** | `customer/` | `customerdb` | Customer/Identity team |
| **Product & Inventory Service** | `inventory/` | `productdb` | Catalog/Warehouse team |
| **Order Service** | `order/` | `orderdb` | Order/Fulfillment team |

### Coupling Resolution

- `OrderService.createOrder()` calls **Customer Service REST API** to validate customer exists
- `OrderService.createOrder()` calls **Product Service REST API** for product lookup + `reserveStock()`
- Stock restore on cancellation via REST call or async event (`OrderCancelled` → Product Service listens)

### Pros

- Maps **1:1 to existing package structure** — minimal code reorganization
- Clean team ownership boundaries (3 teams, 3 services)
- Each service has an independent deploy/scale lifecycle
- Order domain already uses ID references — no JPA relationship refactoring needed

### Cons

- `createOrder()` becomes a distributed operation (customer validation + stock reservation across services)
- Loss of ACID transactions — need saga pattern for stock reservation failures
- 3 databases to manage

### Best For

Teams already organized around Customer, Catalog, and Order/Fulfillment functions.

---

## Option B: 2-Service Split (Commerce Core + Customer)

Merge the tightly-coupled Order + Product domains to avoid distributed transaction complexity.

| Service | Source Packages | Database | Team |
|---------|----------------|----------|------|
| **Customer Service** | `customer/` | `customerdb` | Customer/Identity team |
| **Commerce Service** | `inventory/` + `order/` | `commercedb` | Commerce team |

### Coupling Resolution

- Only **one cross-service boundary** — Commerce Service calls Customer Service via REST for customer validation
- `OrderService` ↔ `ProductService` coupling stays as in-process calls (no change needed)
- Single database for orders + products preserves ACID stock reservation

### Pros

- **Simplest migration path** — only one service boundary to introduce
- Preserves transactional integrity for the critical order → stock flow
- Customer Service is already fully independent — clean extraction
- Only 2 databases to manage

### Cons

- Commerce Service is still fairly large (2 domains in one service)
- Less team autonomy — catalog and order teams share a codebase
- Doesn't fully realize microservice benefits for the commerce domain

### Best For

Smaller teams where reducing operational overhead matters more than fine-grained autonomy. Also works well as an **interim step** before further decomposition (Option B → Option A later).

---

## Option C: 4-Service Split (Separate Catalog from Inventory)

Further decompose the Product domain into a read-heavy Catalog service and a write-heavy Inventory service.

| Service | Owns | Database | Team |
|---------|------|----------|------|
| **Customer Service** | Customer management | `customerdb` | Customer/Identity team |
| **Product Catalog Service** | Product info, categories, search | `catalogdb` | Merchandising team |
| **Inventory Service** | Stock levels, reservation, reorder alerts | `inventorydb` | Warehouse/Ops team |
| **Order Service** | Order lifecycle | `orderdb` | Order/Fulfillment team |

### Coupling Resolution

- Order Service → Customer Service: REST call to validate customer
- Order Service → Catalog Service: REST call to get product name/SKU/price (read-only, cacheable)
- Order Service → Inventory Service: REST or async call to reserve/restore stock
- Inventory Service → Catalog Service: event-driven sync if product is deactivated

### Pros

- Catalog Service can be independently scaled and heavily cached (CDN-friendly)
- Inventory Service can enforce strong consistency without impacting read performance
- Maximum team autonomy — 4 independent teams
- Catalog changes (descriptions, images) don't require inventory redeployment

### Cons

- **Most complex option** — 4 services, 4 databases, more inter-service communication
- Current `Product` entity must be **split into two models** (catalog fields vs. inventory fields)
- Need to decide which service owns which `Product` fields
- Highest operational overhead

### Best For

Large teams with distinct catalog/merchandising and warehouse/inventory management functions. High-traffic applications where catalog reads vastly outnumber inventory writes.

---

## Recommendation Matrix

| Factor | Option A (3-Service) | Option B (2-Service) | Option C (4-Service) |
|--------|:-------------------:|:-------------------:|:-------------------:|
| Migration complexity | Medium | **Low** | High |
| Team autonomy | High | Medium | **Highest** |
| Operational overhead | Medium | **Low** | High |
| Data consistency risk | Medium | **Low** | High |
| Scalability granularity | Good | Limited | **Best** |
| Maps to existing code | **1:1** | Partial merge | Requires entity split |

---

## Common Steps (All Options)

Regardless of which option is chosen, these steps apply:

1. **Database per service** — Replace the shared H2 database with independent datastores (Postgres/MySQL for production)
2. **Split configuration** — Each service gets its own `application.properties` with independent port and database config
3. **Split `DataInitializer`** — Current shared initializer must become per-service seed data
4. **Replace direct calls with REST APIs** — `OrderService` cross-domain method calls become HTTP calls via RestTemplate, WebClient, or Feign clients
5. **Add resilience** — Circuit breakers (Resilience4j) for inter-service calls to handle downstream failures gracefully
6. **API Gateway** — Introduce Spring Cloud Gateway to route `/api/products/**`, `/api/customers/**`, `/api/orders/**` to the correct service
7. **Service discovery** — Add Eureka/Consul or rely on Kubernetes DNS for service-to-service communication

---

## Key Files Driving the Refactoring

| File | Why It Matters |
|------|---------------|
| `OrderService.java` | Primary coupling point — contains all cross-domain calls |
| `Order.java` / `OrderItem.java` | Already use ID references (`customerId`, `productId`) — microservice-ready |
| `ProductService.java` | `reserveStock()` and `restoreStock()` become API endpoints |
| `CustomerService.java` | Already independent — clean extraction candidate |
| `DataInitializer.java` | Must be split per service |
| `application.properties` | Shared DB config must be split per service |
