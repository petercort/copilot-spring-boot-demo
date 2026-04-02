# Phase 1 Deliverable: Service Boundaries and Contracts (Option A)

## Scope
This document finalizes Phase 1 for the selected Option A target architecture:
1. Customer Service
2. Product/Inventory Service
3. Order Service

## Service ownership matrix
| Service | Owns (In Scope) | Out of Scope | Primary Team | Data Owned | SLA Class |
|---|---|---|---|---|---|
| Customer Service | Customer profile lifecycle, lookup by ID/email, customer validation status | Product catalog, stock, order lifecycle | Customer/Identity | `customers` | Tier-1 read dependency for order placement |
| Product/Inventory Service | Product metadata, pricing snapshot source, stock quantity, reserve/restore operations | Customer lifecycle, order status lifecycle | Catalog/Warehouse | `products` | Tier-1 write dependency for order placement |
| Order Service | Order lifecycle, order items, order status transitions, orchestration of customer/product checks | Master customer profile updates, direct stock persistence | Order/Fulfillment | `orders`, `order_items` | Tier-1 user-facing transactional flow |

## Boundary rules
1. No service may read/write another service database directly.
2. Cross-service interactions must use HTTP APIs (Phase 1 baseline).
3. Order Service stores only `customerId` and `productId` references; it does not own customer/product master records.
4. Product/Inventory is system of record for stock reserve/restore outcomes.
5. Customer Service is system of record for customer existence and customer profile state.

## API contract summary
Detailed endpoint contracts are defined in:
- `REFACTORING/contracts/customer-service.openapi.yaml`
- `REFACTORING/contracts/product-inventory-service.openapi.yaml`
- `REFACTORING/contracts/order-service.openapi.yaml`

## Cross-service orchestration contract
### Create order (synchronous baseline)
1. Order Service validates customer via Customer Service (`GET /api/customers/{id}`).
2. For each item, Order Service reads product and reserves stock via Product/Inventory Service (`GET /api/products/{id}`, `POST /api/products/{id}/reserve`).
3. On full reservation success, Order Service persists order and returns created response.
4. On partial failure after any reservation, Order Service invokes compensation (`POST /api/products/{id}/restore`) for already-reserved items.

### Cancel order
1. Order Service updates status to `CANCELLED`.
2. Order Service restores stock for all items via Product/Inventory Service restore endpoint.

## Error and compatibility standards
1. Error response shape: `{ timestamp, status, error, message, path }`.
2. Contract versioning: additive-first changes in v1; breaking changes require `/v2` path namespace.
3. Idempotency requirement:
   - `reserve` and `restore` endpoints should be idempotent by operation key in later phases.
   - For Phase 1, behavior is defined as best-effort synchronous with explicit compensation.

## Non-functional baseline (Phase 1)
| Area | Baseline |
|---|---|
| Availability target | 99.9% for Customer, Product/Inventory, and Order APIs in production environments |
| Create-order latency target | p95 < 500ms for nominal synchronous flow (excluding downstream outage conditions) |
| Timeout policy | 2s client timeout for cross-service synchronous calls from Order Service |
| Retry policy | No automatic retries for non-idempotent operations in Phase 1; retries limited to safe reads |
| Observability | Correlation ID propagation across service calls; structured logs include `service`, `operation`, `correlationId` |
| Error budget signal | 5xx rate and timeout rate tracked per endpoint, with per-service dashboards |
| Deployment minimum | Independent deployment pipeline per service with health endpoint checks before traffic cutover |

## Phase 1 completion criteria
1. Ownership boundaries are defined and accepted.
2. Public service APIs are documented for customer, product/inventory, and order.
3. Cross-service call sequence and compensation flow are defined for order creation and cancellation.
4. No unresolved boundary ambiguity remains about data ownership for customer/product/order entities.
