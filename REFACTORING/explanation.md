# Application Component Analysis — E-Commerce Monolith

## Overview

This is a **Spring Boot 3.3 e-commerce monolith** built with Java 17 and Maven. It uses an in-memory H2 database, Spring Data JPA for persistence, Lombok for boilerplate reduction, and exposes REST APIs. The application follows a layered architecture with controllers, services, repositories, and JPA entities.

---

## Component Inventory

### 1. **Product Domain**

| Layer | Class | Responsibility |
|---|---|---|
| Entity | `Product` | JPA entity mapping to the `PRODUCTS` table. Fields: `id`, `name`, `description`, `price`, `stockQuantity`, `category`, `imageUrl`. |
| Repository | `ProductRepository` | Spring Data JPA repository with custom queries: `findByCategory`, `findByNameContainingIgnoreCase`, `findByPriceBetween`. |
| Service | `ProductService` | Business logic for CRUD operations, stock management (`updateStock`, `isInStock`), and search/filter. |
| Controller | `ProductController` | REST endpoints under `/api/products` — list, get by ID, create, update, delete, search by name, filter by category/price. |

### 2. **Customer Domain**

| Layer | Class | Responsibility |
|---|---|---|
| Entity | `Customer` | JPA entity mapping to the `CUSTOMERS` table. Fields: `id`, `firstName`, `lastName`, `email` (unique), `address`, `phone`. Has a `@OneToMany` relationship with `Order`. |
| Repository | `CustomerRepository` | Spring Data JPA repository with `findByEmail` and `findByLastNameContainingIgnoreCase`. |
| Service | `CustomerService` | CRUD, lookup by email, search by last name, duplicate email validation on create. |
| Controller | `CustomerController` | REST endpoints under `/api/customers`. |

### 3. **Order Domain**

| Layer | Class | Responsibility |
|---|---|---|
| Entity | `Order` | JPA entity mapping to the `ORDERS` table. Fields: `id`, `customer` (ManyToOne), `orderDate`, `status`, `totalAmount`. Has a `@OneToMany` cascade relationship with `OrderItem`. |
| Entity | `OrderItem` | JPA entity mapping to the `ORDER_ITEMS` table. Fields: `id`, `order` (ManyToOne), `product` (ManyToOne), `quantity`, `price`. |
| Repository | `OrderRepository` | Custom queries: `findByCustomerId`, `findByStatus`, `findByOrderDateBetween`. |
| Repository | `OrderItemRepository` | Custom query: `findByOrderId`. |
| Service | `OrderService` | The most complex service — orchestrates order creation by validating customers, validating/reserving product stock, calculating totals, and managing order status transitions. |
| Controller | `OrderController` | REST endpoints under `/api/orders` — create, get, list by customer/status/date-range, update status, cancel. |

### 4. **Cross-Cutting / Infrastructure**

| Component | Purpose |
|---|---|
| `EcommerceApplication` | Spring Boot main class. |
| `application.properties` | H2 datasource config, JPA/Hibernate settings, H2 console. |
| `data.sql` | Seed data (sample products, customers, orders, order items). |
| `pom.xml` | Maven build with Spring Boot Starter Web, Data JPA, H2, Lombok. |
| `build.sh` | Shell script to build with Java 17. |

---

## Domain Relationship Diagram

```
┌──────────────┐       ┌──────────────┐
│   Customer   │──1:N──│    Order     │
└──────────────┘       └──────┬───────┘
                              │ 1:N
                       ┌──────┴───────┐
                       │  OrderItem   │
                       └──────┬───────┘
                              │ N:1
                       ┌──────┴───────┐
                       │   Product    │
                       └──────────────┘
```

---

## Proposed Microservice Groupings

Based on domain boundaries and coupling analysis, the monolith maps naturally to **three microservices**:

### Microservice 1: **Product Service**

- **Entities:** `Product`
- **Components:** `ProductRepository`, `ProductService`, `ProductController`
- **Rationale:** Product catalog is self-contained — no inbound JPA relationships. It can be queried independently by other services via REST or messaging.
- **API surface:** CRUD, search, stock queries.

### Microservice 2: **Customer Service**

- **Entities:** `Customer`
- **Components:** `CustomerRepository`, `CustomerService`, `CustomerController`
- **Rationale:** Customer data is owned independently. The current `@OneToMany` to `Order` is a convenience join that can be replaced by a REST call or event.
- **API surface:** CRUD, email lookup, search.

### Microservice 3: **Order Service**

- **Entities:** `Order`, `OrderItem`
- **Components:** `OrderRepository`, `OrderItemRepository`, `OrderService`, `OrderController`
- **Rationale:** Orders are the most coupled domain — they reference both customers and products. In a microservices architecture, these become **remote references** (store `customerId` and `productId` as plain fields, not JPA relationships). The Order Service calls the Customer and Product services to validate and retrieve data.
- **API surface:** Create order, status management, queries by customer/status/date.

---

## Key Refactoring Considerations

### 1. Breaking JPA Relationships Across Services
The current `@ManyToOne` from `Order` → `Customer` and `OrderItem` → `Product` must become simple ID fields. The Order Service will call the Customer and Product APIs to validate existence and fetch details.

### 2. Stock Management
`OrderService.createOrder()` currently calls `productService.updateStock()` directly. In a microservices world this needs to become:
- A **synchronous REST call** (simpler but tightly coupled), or
- An **event-driven saga** (e.g., `OrderCreated` → Product Service reserves stock → `StockReserved` / `StockInsufficient`).

### 3. Data Consistency
The monolith benefits from a single database transaction when creating an order. With microservices, you need a strategy for distributed consistency:
- **Saga pattern** (choreography or orchestration)
- **Eventual consistency** with compensating transactions (e.g., cancel order if stock reservation fails)

### 4. Shared Contracts
Define shared DTOs or API contracts (e.g., OpenAPI specs) so each service can evolve independently. Avoid sharing JPA entities across service boundaries.

### 5. Database per Service
Each microservice should own its database schema. The current single H2 database would be split into three independent datastores.

### 6. API Gateway
Introduce an API gateway (e.g., Spring Cloud Gateway) to route `/api/products/**`, `/api/customers/**`, and `/api/orders/**` to the appropriate service.

---

## Suggested Refactoring Order

| Step | Action | Risk |
|---|---|---|
| 1 | Extract **Product Service** (no inbound JPA deps) | Low |
| 2 | Extract **Customer Service** (remove `@OneToMany` to Order) | Low |
| 3 | Extract **Order Service** (replace JPA joins with REST calls) | Medium |
| 4 | Add API Gateway & service discovery | Low |
| 5 | Introduce async messaging for stock/order events | Medium |
| 6 | Add distributed tracing, circuit breakers, config server | Low |

---

## Current Package Structure (Monolith)

```
com.example.ecommerce
├── EcommerceApplication.java
├── controller/
│   ├── CustomerController.java
│   ├── OrderController.java
│   └── ProductController.java
├── model/
│   ├── Customer.java
│   ├── Order.java
│   ├── OrderItem.java
│   └── Product.java
├── repository/
│   ├── CustomerRepository.java
│   ├── OrderItemRepository.java
│   ├── OrderRepository.java
│   └── ProductRepository.java
└── service/
    ├── CustomerService.java
    ├── OrderService.java
    └── ProductService.java
```

## Target Multi-Module / Multi-Repo Structure

```
ecommerce-platform/
├── product-service/          # Independent Spring Boot app
│   └── src/main/java/com/example/product/
│       ├── ProductServiceApplication.java
│       ├── controller/
│       ├── model/
│       ├── repository/
│       └── service/
├── customer-service/         # Independent Spring Boot app
│   └── src/main/java/com/example/customer/
│       ├── CustomerServiceApplication.java
│       ├── controller/
│       ├── model/
│       ├── repository/
│       └── service/
├── order-service/            # Independent Spring Boot app
│   └── src/main/java/com/example/order/
│       ├── OrderServiceApplication.java
│       ├── controller/
│       ├── model/
│       ├── repository/
│       └── service/
└── api-gateway/              # Spring Cloud Gateway
```