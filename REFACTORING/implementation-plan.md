# Microservices Refactoring Plan (Option A)

## Problem and approach
The monolith is package-aligned by domain (`customer`, `inventory`, `order`), but `OrderService` is tightly coupled to `CustomerService` and `ProductService` via direct calls and shared transaction scope.  
This plan targets an incremental extraction to three services while preserving behavior and adding explicit inter-service contracts.

## Selected alignment
1. **Customer Service** (from `customer` package)
2. **Product/Inventory Service** (from `inventory` package)
3. **Order Service** (from `order` package)

## Current-state summary
- Spring Boot 3.2, Java 17, Maven, single runtime.
- Shared in-memory H2 database.
- Coupling hotspot in `order/service/OrderService.java`:
  - customer existence checks
  - product lookups
  - stock reserve/restore calls

## Phased implementation plan
| Phase | Scope | Effort Estimate | Blocking? | Blocks Which Phases |
|---|---|---|---|---|
| 1 | Define boundaries and contracts | **M** | **Yes** | 2, 3, 4, 5 |
| 2 | Split data ownership (database-per-service) | **M** | **Yes** | 3, 4, 5 |
| 3 | Extract Customer Service | **M** | No | 5 |
| 4 | Extract Product/Inventory Service | **L** | No | 5 |
| 5 | Refactor and extract Order Service | **L** | **Yes** | 6, 7 |
| 6 | Add platform capabilities (gateway/resilience/observability) | **M** | No | 7 |
| 7 | Harden integration and flow validation | **M** | No | — |

### Phase details
1. **Define boundaries and contracts (M) — Blocking**
   - Lock service responsibilities and public APIs for customer lookup, product read, stock reserve/restore, and order lifecycle.
2. **Split data ownership (M) — Blocking**
   - Move from one shared schema to database-per-service.
3. **Extract Customer Service (M)**
   - Package/service extraction with independent config and persistence.
4. **Extract Product/Inventory Service (L)**
   - Isolate catalog + stock behavior and expose reserve/restore endpoints.
5. **Refactor and extract Order Service (L) — Blocking**
   - Replace direct service calls with API clients.
   - Add compensating action flow for stock restore on failure/cancel paths.
6. **Add platform capabilities (M)**
   - API routing/gateway, retry/timeout/circuit-breaker behavior, telemetry and tracing.
7. **Harden integration (M)**
   - Contract and end-to-end tests for create/cancel/delete order flows.

## Blocking phases and critical path
Critical path: **Phase 1 -> Phase 2 -> (Phase 3 + Phase 4 in parallel) -> Phase 5 -> Phase 6 -> Phase 7**

- **Phase 1 is blocking** because all extraction work depends on fixed contracts and ownership.
- **Phase 2 is blocking** because independent persistence is required before true service autonomy.
- **Phase 5 is blocking** because order orchestration is the main cross-domain dependency and must stabilize before platform hardening and final integration validation.

## TODOs
1. Define ownership matrix (team, API, data, SLA) for each of the three services.
2. Draft and review service API contracts.
3. Choose consistency model for order/stock (sync + compensation, with evolution path to saga/events).
4. Create migration slices and cutover checkpoints per service.
5. Define non-functional baselines (latency/error budgets, observability, deployment model).

## Phase 1 execution artifacts
- `REFACTORING/phase-1-boundaries-and-contracts.md`
- `REFACTORING/contracts/customer-service.openapi.yaml`
- `REFACTORING/contracts/product-inventory-service.openapi.yaml`
- `REFACTORING/contracts/order-service.openapi.yaml`

## Notes
- This sequence minimizes risk by extracting independent domains first and handling cross-domain orchestration last.
- Order flow behavior should remain functionally equivalent during transition.
- Effort estimates are relative sizing (**S/M/L**) for planning complexity and should be converted to team-specific delivery forecasts separately.
