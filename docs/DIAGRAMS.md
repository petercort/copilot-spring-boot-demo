# Architecture Diagrams

This document contains visual representations of the monolith-to-microservices transformation.

## Table of Contents
- [Current Monolithic Architecture](#current-monolithic-architecture)
- [Target Microservices Architecture](#target-microservices-architecture)
- [Service Communication Flow](#service-communication-flow)
- [Order Creation Sequence](#order-creation-sequence)

---

## Current Monolithic Architecture

### ASCII Diagram

```
┌────────────────────────────────────────────────────────┐
│                  E-Commerce Monolith                   │
│                    (Port 8080)                         │
│                                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  |
│  │   Customer   │  │  Inventory   │  │    Order     │  │
│  │    Domain    │  │    Domain    │  │    Domain    │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│         │                 │                  │         │
│         └─────────────────┴──────────────────┘         │
│                           │                            │
│                  ┌────────▼────────┐                   │
│                  │  Shared H2 DB   │                   │
│                  │ (ecommercedb)   │                   │
│                  └─────────────────┘                   │
└────────────────────────────────────────────────────────┘
```

### Mermaid Diagram

```mermaid
graph TB
    subgraph Monolith["E-Commerce Monolith :8080"]
        Customer[Customer Domain]
        Inventory[Inventory Domain]
        Order[Order Domain]
        
        Order -->|Direct Call| Customer
        Order -->|Direct Call| Inventory
        
        Customer --> DB[(Shared Database)]
        Inventory --> DB
        Order --> DB
    end
    
    Client[Client/Browser] -->|HTTP| Monolith
    
    style Monolith fill:#f9f,stroke:#333,stroke-width:4px
    style DB fill:#bbf,stroke:#333,stroke-width:2px
```

---

## Target Microservices Architecture

### ASCII Diagram

```
                        ┌─────────────────┐
                        │  Client/Browser │
                        └────────┬────────┘
                                 │
                    ┌────────────┼────────────┐
                    │            │            │
                    ▼            ▼            ▼
         ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
         │   Customer   │  │  Inventory   │  │    Order     │
         │   Service    │  │   Service    │  │   Service    │
         │  (Port 8081) │  │ (Port 8082)  │  │ (Port 8083)  │
         └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
                │                 │                  │
                │                 │                  │
         ┌──────▼───────┐  ┌──────▼───────┐  ┌──────▼───────┐
         │ Customer DB  │  │ Inventory DB │  │  Order DB    │
         │   (H2/SQL)   │  │   (H2/SQL)   │  │   (H2/SQL)   │
         └──────────────┘  └──────────────┘  └──────────────┘
```

### Mermaid Diagram

```mermaid
graph TB
    Client[Client/Browser]
    
    subgraph CustomerMS["Customer Service :8081"]
        CustomerAPI[Customer API]
        CustomerDB[(Customer Database)]
        CustomerAPI --> CustomerDB
    end
    
    subgraph InventoryMS["Inventory Service :8082"]
        InventoryAPI[Inventory API]
        InventoryDB[(Inventory Database)]
        InventoryAPI --> InventoryDB
    end
    
    subgraph OrderMS["Order Service :8083"]
        OrderAPI[Order API]
        OrderDB[(Order Database)]
        OrderAPI --> OrderDB
    end
    
    Client -->|GET /customers| CustomerAPI
    Client -->|GET /products| InventoryAPI
    Client -->|POST /orders| OrderAPI
    
    style CustomerMS fill:#e1f5e1,stroke:#333,stroke-width:2px
    style InventoryMS fill:#e1e5f5,stroke:#333,stroke-width:2px
    style OrderMS fill:#f5e1e1,stroke:#333,stroke-width:2px
```

---

## Service Communication Flow

### ASCII Diagram - Order Processing

```
                             Order Creation Flow
                             
    Client                Order Service         Customer Service    Inventory Service
      │                      (8083)                  (8081)              (8082)
      │                        │                       │                   │
      │   POST /orders         │                       │                   │
      ├───────────────────────>│                       │                   │
      │                        │                       │                   │
      │                        │  GET /customers/{id}  │                   │
      │                        ├──────────────────────>│                   │
      │                        │                       │                   │
      │                        │   Customer Data       │                   │
      │                        │<──────────────────────┤                   │
      │                        │                       │                   │
      │                        │          GET /products/{id}               │
      │                        ├───────────────────────────────────────────>│
      │                        │                       │                   │
      │                        │              Product Data                 │
      │                        │<───────────────────────────────────────────┤
      │                        │                       │                   │
      │                        │      POST /products/{id}/reserve          │
      │                        ├───────────────────────────────────────────>│
      │                        │                       │                   │
      │                        │           Stock Reserved                  │
      │                        │<───────────────────────────────────────────┤
      │                        │                       │                   │
      │                        │  Save Order           │                   │
      │                        │  to Order DB          │                   │
      │                        │                       │                   │
      │   Order Created        │                       │                   │
      │<───────────────────────┤                       │                   │
      │                        │                       │                   │
```

### Mermaid Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant OrderService as Order Service<br/>(8083)
    participant CustomerService as Customer Service<br/>(8081)
    participant InventoryService as Inventory Service<br/>(8082)
    participant OrderDB as Order DB
    
    Client->>+OrderService: POST /api/orders
    Note over OrderService: Validate request
    
    OrderService->>+CustomerService: GET /api/customers/{id}
    CustomerService-->>-OrderService: Customer data
    Note over OrderService: Validate customer exists
    
    loop For each order item
        OrderService->>+InventoryService: GET /api/products/{id}
        InventoryService-->>-OrderService: Product data
        Note over OrderService: Check product exists
        
        OrderService->>+InventoryService: POST /api/products/{id}/reserve
        InventoryService-->>-OrderService: Stock reserved
    end
    
    OrderService->>OrderDB: Save order
    OrderDB-->>OrderService: Order saved
    
    OrderService-->>-Client: Order created (201)
```

---

## Service Dependency Map

### ASCII Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Service Dependencies                      │
└─────────────────────────────────────────────────────────────┘

    Customer Service               Inventory Service
    ┌──────────────┐              ┌──────────────┐
    │   Port 8081  │              │  Port 8082   │
    │              │              │              │
    │ - GET /      │              │ - GET /      │
    │   customers  │              │   products   │
    │ - POST /     │              │ - POST /     │
    │   customers  │              │   reserve    │
    │ - PUT /      │              │ - POST /     │
    │   customers  │              │   restore    │
    └──────▲───────┘              └──────▲───────┘
           │                             │
           │                             │
           └──────────┬──────────────────┘
                      │
                ┌─────▼──────┐
                │   Order    │
                │  Service   │
                │ Port 8083  │
                │            │
                │ Depends on │
                │   both     │
                └────────────┘
```

### Mermaid Component Diagram

```mermaid
graph LR
    subgraph Independent["Independent Services"]
        CS[Customer Service<br/>Port 8081<br/><br/>✓ No Dependencies]
        IS[Inventory Service<br/>Port 8082<br/><br/>✓ No Dependencies]
    end
    
    subgraph Dependent["Dependent Service"]
        OS[Order Service<br/>Port 8083<br/><br/>⚠ Depends on<br/>Customer & Inventory]
    end
    
    OS -.->|REST API| CS
    OS -.->|REST API| IS
    
    style CS fill:#90EE90,stroke:#333,stroke-width:2px
    style IS fill:#87CEEB,stroke:#333,stroke-width:2px
    style OS fill:#FFB6C1,stroke:#333,stroke-width:2px
```

---

## Data Flow - Complete Order Lifecycle

### Mermaid State Diagram

```mermaid
stateDiagram-v2
    [*] --> OrderRequested: Client submits order
    
    OrderRequested --> ValidatingCustomer: Order Service calls<br/>Customer Service
    ValidatingCustomer --> ValidatingInventory: Customer valid
    ValidatingCustomer --> OrderFailed: Customer not found
    
    ValidatingInventory --> ReservingStock: Inventory Service checks<br/>stock availability
    ReservingStock --> OrderConfirmed: Stock reserved
    ReservingStock --> OrderFailed: Insufficient stock
    
    OrderConfirmed --> OrderProcessing: Save to Order DB
    OrderProcessing --> OrderShipped: Process fulfillment
    OrderShipped --> OrderDelivered: Delivery confirmed
    
    OrderDelivered --> [*]
    OrderFailed --> [*]: Return error to client
```

---

## Deployment Architecture

### Mermaid Deployment Diagram

```mermaid
graph TB
    subgraph Cloud["Cloud Environment (AWS/Azure/GCP)"]
        subgraph LB["Load Balancer"]
            ALB[Application Load Balancer]
        end
        
        subgraph K8S["Kubernetes Cluster"]
            subgraph NS1["customer-service namespace"]
                CS1[Customer Pod 1]
                CS2[Customer Pod 2]
                CSDB[(Customer DB)]
                CS1 --> CSDB
                CS2 --> CSDB
            end
            
            subgraph NS2["inventory-service namespace"]
                IS1[Inventory Pod 1]
                IS2[Inventory Pod 2]
                ISDB[(Inventory DB)]
                IS1 --> ISDB
                IS2 --> ISDB
            end
            
            subgraph NS3["order-service namespace"]
                OS1[Order Pod 1]
                OS2[Order Pod 2]
                OSDB[(Order DB)]
                OS1 --> OSDB
                OS2 --> OSDB
            end
        end
        
        ALB --> CS1
        ALB --> CS2
        ALB --> IS1
        ALB --> IS2
        ALB --> OS1
        ALB --> OS2
        
        OS1 -.->|REST| CS1
        OS1 -.->|REST| IS1
        OS2 -.->|REST| CS2
        OS2 -.->|REST| IS2
    end
    
    Client[Clients] -->|HTTPS| ALB
```

---

## Migration Phases

### ASCII Timeline

```
Phase 1: Preparation          Phase 2: Extraction         Phase 3: Migration
─────────────────────────────────────────────────────────────────────────────

┌──────────────┐              ┌──────────────┐            ┌──────────────┐
│   Monolith   │              │  Monolith +  │            │ Pure Micro-  │
│              │    ──────>   │ Microservices│  ──────>   │  services    │
│   All-in-One │              │   (Hybrid)   │            │ Architecture │
└──────────────┘              └──────────────┘            └──────────────┘

• Analyze domains             • Extract Customer           • Decomission
• Define boundaries           • Extract Inventory            monolith
• Setup infrastructure        • Extract Order              • Full cloud
• Create repositories         • Parallel run                 deployment
                              • Data migration             • Service mesh
```

### Mermaid Gantt Chart

```mermaid
gantt
    title Microservices Migration Timeline
    dateFormat  YYYY-MM-DD
    section Phase 1: Prep
    Analyze Domains           :a1, 2025-11-01, 3d
    Define Boundaries         :a2, after a1, 2d
    Setup Infrastructure      :a3, after a2, 3d
    
    section Phase 2: Extract
    Extract Customer Service  :b1, after a3, 5d
    Extract Inventory Service :b2, after b1, 5d
    Extract Order Service     :b3, after b2, 7d
    
    section Phase 3: Deploy
    Deploy to Staging        :c1, after b3, 3d
    Testing & Validation     :c2, after c1, 5d
    Production Deployment    :c3, after c2, 2d
    Monitor & Optimize       :c4, after c3, 7d
```

---

## Technology Stack

```
┌─────────────────────────────────────────────────────────────┐
│                     Technology Stack                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Application Layer                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Spring Boot 3.2.0 | Java 17 | Maven                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  API Layer                                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ REST APIs | Spring Web MVC | JSON                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  Data Layer                                                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Spring Data JPA | Hibernate | H2 Database            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  Development Tools                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Lombok | Spring DevTools | GitHub Copilot            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Reference

### Service Ports Summary

| Service | Port | Database | Dependencies |
|---------|------|----------|--------------|
| **Customer Service** | 8081 | customer_db | None |
| **Inventory Service** | 8082 | inventory_db | None |
| **Order Service** | 8083 | order_db | Customer, Inventory |
| **Monolith (Current)** | 8080 | ecommercedb | N/A |

### API Endpoints Summary

```
Customer Service (8081)
├── GET    /api/customers
├── GET    /api/customers/{id}
├── POST   /api/customers
├── PUT    /api/customers/{id}
└── DELETE /api/customers/{id}

Inventory Service (8082)
├── GET    /api/products
├── GET    /api/products/{id}
├── POST   /api/products
├── PUT    /api/products/{id}
├── POST   /api/products/{id}/reserve
└── POST   /api/products/{id}/restore

Order Service (8083)
├── GET    /api/orders
├── GET    /api/orders/{id}
├── POST   /api/orders
├── PUT    /api/orders/{id}/cancel
└── PUT    /api/orders/{id}/status
```

---

## Notes for Presenters

### Diagram Usage Tips

1. **Start with ASCII diagrams** for quick terminal/console demonstrations
2. **Use Mermaid diagrams** in markdown viewers, GitHub, and documentation
3. **Print this document** as a reference during live coding sessions
4. **Keep the Service Dependency Map** visible to explain coupling issues

### Key Discussion Points

- **Current State**: Highlight the tight coupling in the monolith
- **Target State**: Emphasize service independence and scalability
- **Communication**: Show how REST APIs replace direct method calls
- **Data**: Explain database-per-service pattern
- **Trade-offs**: Discuss complexity vs. scalability

### Demo Flow with Diagrams

1. Show **Current Monolithic Architecture** (slide 1)
2. Explain problems with tight coupling
3. Present **Target Microservices Architecture** (slide 2)
4. Walk through **Service Communication Flow** (slide 3)
5. Detail **Order Creation Sequence** for deep dive
6. Review **Migration Phases** timeline

---

## Additional Resources

- **DEMO_SCRIPT.md** - Complete presentation script with timing
- **ARCHITECTURE.md** - Detailed architectural decisions
- **COPILOT_PROMPTS.md** - Prompts to generate these services
- **API_EXAMPLES.md** - Test the current monolith APIs

---

*Generated for the E-Commerce Monolith to Microservices Demo*  
*Use GitHub Copilot to transform the monolith into these microservices!*
