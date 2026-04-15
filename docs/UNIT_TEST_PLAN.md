# Plan: Add Unit Tests — >90% Coverage Across All Microservices

## TL;DR
Add JUnit 5 + Mockito unit tests for every testable class in customer-service, inventory-service, and order-service. Use `@ExtendWith(MockitoExtension.class)` for pure unit tests (service layer, adapters) and `@WebMvcTest` for controllers. Add JaCoCo to the parent POM to enforce >90% line/branch coverage at build time. api-gateway and eureka-server have no business logic and need only a smoke test each.

---

## Phase Dependency Overview

```
Phase 1 ── Test Infrastructure
    │
    ├── Phase 2 ── customer-service tests ──┐
    ├── Phase 3 ── inventory-service tests ──┤── Phase 6 ── Verification
    ├── Phase 4 ── order-service tests ──────┤
    └── Phase 5 ── Gateway + Eureka smoke ───┘
```

| Phase | Description | Depends On | Parallel With |
|-------|-------------|------------|---------------|
| **1** | Test Infrastructure — JaCoCo in parent POM | — *(start here)* | — |
| **2** | customer-service tests (service, controller, entity) | Phase 1 | Phases 3, 4, 5 |
| **3** | inventory-service tests (service, controller, entity) | Phase 1 | Phases 2, 4, 5 |
| **4** | order-service tests (service, controller, adapters, entities) | Phase 1 | Phases 2, 3, 5 |
| **5** | api-gateway + eureka-server smoke tests | Phase 1 | Phases 2, 3, 4 |
| **6** | Verification — `mvn verify` + JaCoCo coverage gate | Phases 2, 3, 4, 5 | — |

> Phases 2–5 are fully independent once Phase 1 is complete and can be implemented concurrently.

---

## Phase 1 — Test Infrastructure (blocks all other phases)

**Step 1.** Add JaCoCo to parent `pom.xml`:
- Add JaCoCo Maven plugin to `<build><plugins>` with:
  - `prepare-agent` goal bound to `initialize`
  - `report` goal bound to `verify`
  - `check` goal with `<minimum>0.90</minimum>` for LINE and BRANCH counters
  - Exclusions: `**/model/**`, `**/dto/**`, `**/*Application.java`, `**/config/DataInitializer.java`

**Relevant file:** `pom.xml` (root parent)

---

## Phase 2 — customer-service tests (parallel with Phase 3, 4, 5)

**Step 2.** `CustomerServiceTest` — `@ExtendWith(MockitoExtension.class)`, mock `CustomerRepository`
- `getAllCustomers_returnsList`
- `getCustomerById_found` / `_notFound`
- `getCustomerByEmail_found` / `_notFound`
- `createCustomer_success`
- `createCustomer_duplicateEmail_throwsIllegalArgument`
- `updateCustomer_success` / `_notFound`
- `deleteCustomer_success` / `_notFound`

**Step 3.** `CustomerControllerTest` — `@WebMvcTest(CustomerController.class)`, `@MockBean CustomerService`
- `GET /api/customers` → 200
- `GET /api/customers/{id}` found → 200; not found → 404
- `GET /api/customers/email/{email}` found → 200; not found → 404
- `POST /api/customers` valid → 201; duplicate (IllegalArgument) → 400
- `PUT /api/customers/{id}` → 200; not found → 404
- `DELETE /api/customers/{id}` → 204; not found → 404

**Step 4.** `CustomerTest` — plain JUnit 5
- `onCreate_setsTimestamps`, `onUpdate_updatesTimestamp`
- `equals_sameId_returnsTrue`, `equals_differentId_returnsFalse`, `equals_nullId_returnsFalse`
- `hashCode_consistentWithEquals`

New files:
- `customer-service/src/test/java/com/fisglobal/customer/service/CustomerServiceTest.java`
- `customer-service/src/test/java/com/fisglobal/customer/controller/CustomerControllerTest.java`
- `customer-service/src/test/java/com/fisglobal/customer/model/CustomerTest.java`

---

## Phase 3 — inventory-service tests (parallel with Phase 2, 4, 5)

**Step 5.** `ProductServiceTest` — `@ExtendWith(MockitoExtension.class)`, mock `ProductRepository`
- `getAllProducts`, `getActiveProducts`, `getProductById_found/_notFound`, `getProductBySku_found/_notFound`
- `getProductsByCategory`, `getLowStockProducts`
- `createProduct_success` / `_duplicateSku`
- `updateProduct_success` / `_notFound`
- `reserveStock_success_returnsTrue`, `reserveStock_insufficientStock_returnsFalse`, `reserveStock_productNotFound`
- `restoreStock_success` / `_notFound`
- `deleteProduct_success` / `_notFound`

**Step 6.** `ProductControllerTest` — `@WebMvcTest(ProductController.class)`, `@MockBean ProductService`
- All 10 endpoints: happy path + error cases (400/404 where applicable)
- `reserveStock`: success → 200; insufficient stock (false return) → 400; not found → 404

**Step 7.** `ProductTest` — plain JUnit 5
- `isInStock_true/_false`, `needsReorder_true/_false`, lifecycle hooks, equals/hashCode

New files:
- `inventory-service/src/test/java/com/fisglobal/inventory/service/ProductServiceTest.java`
- `inventory-service/src/test/java/com/fisglobal/inventory/controller/ProductControllerTest.java`
- `inventory-service/src/test/java/com/fisglobal/inventory/model/ProductTest.java`

---

## Phase 4 — order-service tests (parallel with Phase 2, 3, 5)

**Step 8.** `OrderServiceTest` — `@ExtendWith(MockitoExtension.class)`
Mocks: `OrderRepository`, `CustomerServicePort`, `InventoryServicePort`
- Read-only queries: `getAllOrders`, `getOrderById_found/_notFound`, `getOrderByOrderNumber`, `getOrdersByCustomerId`, `getOrdersByStatus`
- `createOrder_success` — customer found, products in stock, reservations succeed, saved as CONFIRMED
- `createOrder_customerNotFound_throwsIllegalArgument`
- `createOrder_productNotFound_throwsIllegalArgument` — compensation for prior reserved items
- `createOrder_insufficientStock_throwsIllegalArgument` — compensation rollback
- `createOrder_partialReservation_compensatesAndThrows` — 1st reserved, 2nd fails, 1st restored
- `updateOrderStatus_success`
- `updateOrderStatus_toCancelled_restoresAllItemStocks`
- `updateOrderStatus_orderNotFound_throwsIllegalArgument`
- `deleteOrder_notCancelledStatus_compensatesInventory`
- `deleteOrder_cancelledOrder_skipsCompensation`
- `deleteOrder_notFound_throwsIllegalArgument`

**Step 9.** `OrderControllerTest` — `@WebMvcTest(OrderController.class)`, `@MockBean OrderService`
- All 8 endpoints: found/not found, valid/invalid request

**Step 10.** `CustomerServiceClientAdapterTest` — `@ExtendWith(MockitoExtension.class)`, mock `CustomerServiceClient`
- `findById_success_returnsOptional`
- `findById_feignNotFound_returnsEmptyOptional`

**Step 11.** `InventoryServiceClientAdapterTest` — `@ExtendWith(MockitoExtension.class)`, mock `InventoryServiceClient`
- `findProductById_success` / `_feignNotFound_returnsEmpty`
- `reserveStock_success_returnsTrue`
- `reserveStock_feignBadRequest_returnsFalse`
- `restoreStock_success`
- `restoreStock_feignException_doesNotThrow`

**Step 12.** Entity tests:
- `OrderTest` — `addItem`, `recalculateTotal`, `onCreate`, `onUpdate`, equals/hashCode
- `OrderItemTest` — `calculateSubtotal`, `getSubtotal`, lifecycle, equals/hashCode

**Step 13.** `ProductDtoTest` — `isInStock_true/_false` (only DTO with custom logic)

New files:
- `order-service/src/test/java/com/fisglobal/order/service/OrderServiceTest.java`
- `order-service/src/test/java/com/fisglobal/order/controller/OrderControllerTest.java`
- `order-service/src/test/java/com/fisglobal/order/client/CustomerServiceClientAdapterTest.java`
- `order-service/src/test/java/com/fisglobal/order/client/InventoryServiceClientAdapterTest.java`
- `order-service/src/test/java/com/fisglobal/order/model/OrderTest.java`
- `order-service/src/test/java/com/fisglobal/order/model/OrderItemTest.java`
- `order-service/src/test/java/com/fisglobal/order/dto/ProductDtoTest.java`

---

## Phase 5 — Smoke tests for gateway + eureka (parallel with Phases 2-4)

**Step 14.** Context-load tests only (no business logic to test):
- `ApiGatewayApplicationTest` — `@SpringBootTest` with `eureka.client.enabled=false`
- `EurekaServerApplicationTest` — `@SpringBootTest` with `eureka.client.enabled=false`

New files:
- `api-gateway/src/test/java/com/fisglobal/gateway/ApiGatewayApplicationTest.java`
- `eureka-server/src/test/java/com/fisglobal/eureka/EurekaServerApplicationTest.java`

---

## Phase 6 — Verification

**Step 15.** `mvn verify` from project root — runs all tests + JaCoCo coverage gate (fails build if <90%)
**Step 16.** Review per-module HTML reports at `{module}/target/site/jacoco/index.html` for gaps
**Step 17.** Tune JaCoCo exclusions if needed for any auto-generated methods that inflate miss counts

---

## Decisions
- **Unit tests only** — no integration or contract tests; MockMvc keeps controller tests fast and hermetic
- **JaCoCo exclusions**: model entities, DTOs (records), Application bootstrap classes, DataInitializer — boilerplate with no conditional logic
- **Feign interface classes excluded**: `CustomerServiceClient`, `InventoryServiceClient` are tested indirectly through adapter tests; they contain no executable code
- **Repository interfaces not tested**: Spring Data generated methods are framework code, not subject to unit test
- **Eureka/discovery disabled in tests**: All test configurations use `eureka.client.enabled=false` to prevent network calls
- **Test properties**: Each module gets `src/test/resources/application.properties` with H2 + discovery disabled
