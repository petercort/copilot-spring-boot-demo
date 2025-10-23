# E-Commerce Monolith to Microservices Demo

This repository contains a demonstration project for refactoring a monolithic Spring Boot application into domain-specific microservices using GitHub Copilot.

## Overview

This project starts as a **monolithic e-commerce application** with three tightly coupled business domains:
- **Customer Management** - Customer data and operations
- **Inventory Management** - Product catalog and stock management
- **Order Management** - Order processing and fulfillment

The goal is to demonstrate how to use GitHub Copilot to split this monolith into three separate microservices, each owning its domain and communicating via REST APIs.

## Project Structure

```
copilot-spring-boot-demo/
├── src/main/java/com/fisglobal/demo/
│   ├── EcommerceApplication.java          # Main application class
│   ├── config/
│   │   └── DataInitializer.java           # Sample data loader
│   ├── customer/                          # Customer domain (future microservice)
│   │   ├── model/Customer.java
│   │   ├── repository/CustomerRepository.java
│   │   ├── service/CustomerService.java
│   │   └── controller/CustomerController.java
│   ├── inventory/                         # Inventory domain (future microservice)
│   │   ├── model/Product.java
│   │   ├── repository/ProductRepository.java
│   │   ├── service/ProductService.java
│   │   └── controller/ProductController.java
│   └── order/                             # Order domain (future microservice)
│       ├── model/
│       │   ├── Order.java
│       │   └── OrderItem.java
│       ├── dto/
│       │   ├── CreateOrderRequest.java
│       │   └── OrderItemRequest.java
│       ├── repository/OrderRepository.java
│       ├── service/OrderService.java
│       └── controller/OrderController.java
├── src/main/resources/
│   └── application.properties             # Application configuration
├── docs/                                  # Documentation
│   ├── QUICKSTART.md                      # Quick start guide
│   ├── DEMO_SCRIPT.md                     # Step-by-step demo guide
│   ├── COPILOT_PROMPTS.md                 # Sample Copilot prompts for refactoring
│   ├── ARCHITECTURE.md                    # Architecture documentation
│   ├── API_EXAMPLES.md                    # API testing examples
│   ├── PROJECT_SUMMARY.md                 # Technical summary
│   ├── PRESENTER_CHECKLIST.md             # Demo preparation checklist
│   └── BUILD_NOTES.md                     # Build configuration notes
├── pom.xml                                # Maven dependencies
├── build.sh                               # Build script (uses Java 17)
├── run.sh                                 # Run script (uses Java 17)
└── README.md                              # This file
```

## Prerequisites

- **Java 17** or higher
- **Maven 3.6+**
- **GitHub Copilot** enabled in your IDE (VS Code, IntelliJ, etc.)
- **Git** for version control

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd copilot-spring-boot-demo
```

### 2. Build the Application

```bash
mvn clean install
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

### 4. Access the H2 Database Console

Navigate to `http://localhost:8080/h2-console` with the following credentials:
- **JDBC URL**: `jdbc:h2:mem:ecommercedb`
- **Username**: `sa`
- **Password**: (leave empty)

## API Endpoints

### Customer API
- `GET /api/customers` - Get all customers
- `GET /api/customers/{id}` - Get customer by ID
- `GET /api/customers/email/{email}` - Get customer by email
- `POST /api/customers` - Create new customer
- `PUT /api/customers/{id}` - Update customer
- `DELETE /api/customers/{id}` - Delete customer

### Product API
- `GET /api/products` - Get all products
- `GET /api/products/{id}` - Get product by ID
- `GET /api/products/sku/{sku}` - Get product by SKU
- `GET /api/products/category/{category}` - Get products by category
- `GET /api/products/low-stock?threshold=10` - Get low stock products
- `POST /api/products` - Create new product
- `PUT /api/products/{id}` - Update product
- `POST /api/products/{id}/reserve?quantity=X` - Reserve stock
- `POST /api/products/{id}/restore?quantity=X` - Restore stock
- `DELETE /api/products/{id}` - Delete product

### Order API
- `GET /api/orders` - Get all orders
- `GET /api/orders/{id}` - Get order by ID
- `GET /api/orders/order-number/{orderNumber}` - Get order by order number
- `GET /api/orders/customer/{customerId}` - Get orders by customer
- `GET /api/orders/status/{status}` - Get orders by status
- `POST /api/orders` - Create new order
- `PATCH /api/orders/{id}/status?status=CONFIRMED` - Update order status
- `DELETE /api/orders/{id}` - Delete order

## Sample Data

The application initializes with sample data:
- **3 Customers**: John Doe, Jane Smith, Bob Johnson
- **6 Products**: Laptop, Mouse, Keyboard, Office Chair, Standing Desk, Webcam

## Testing the Application

### Create an Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [
      {"productId": 1, "quantity": 1},
      {"productId": 2, "quantity": 2}
    ],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingState": "NY",
    "shippingZip": "10001",
    "shippingCountry": "USA"
  }'
```

## Key Architectural Patterns to Observe

### Current Monolithic Issues

1. **Tight Coupling**: The `OrderService` directly depends on `CustomerService` and `ProductService`
2. **Shared Database**: All domains use the same database schema
3. **Single Deployment Unit**: Changes to any domain require redeploying the entire application
4. **Cross-Domain Transactions**: Order creation involves transactions across multiple domains

### Future Microservices Architecture

Each domain will become a separate service:
1. **Customer Service** - Port 8081
2. **Inventory Service** - Port 8082
3. **Order Service** - Port 8083

Communication patterns to implement:
- **Synchronous**: REST API calls between services
- **Asynchronous**: Event-driven communication (optional)
- **Data Management**: Each service gets its own database
- **Service Discovery**: Service registration and discovery (optional)

## Demo Flow

Follow the **[docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)** for a step-by-step guide on:
1. Understanding the current monolithic architecture
2. Identifying domain boundaries
3. Using GitHub Copilot to extract microservices
4. Implementing inter-service communication
5. Testing the distributed system

## Documentation

- **[Quick Start Guide](docs/QUICKSTART.md)** - Get up and running quickly
- **[Demo Script](docs/DEMO_SCRIPT.md)** - 60-minute live demonstration guide
- **[Copilot Prompts](docs/COPILOT_PROMPTS.md)** - 50+ prompts for refactoring with GitHub Copilot
- **[Architecture](docs/ARCHITECTURE.md)** - Detailed architecture documentation
- **[Diagrams](docs/DIAGRAMS.md)** - Visual architecture diagrams and service flows
- **[API Examples](docs/API_EXAMPLES.md)** - Comprehensive API testing guide
- **[Project Summary](docs/PROJECT_SUMMARY.md)** - Technical overview
- **[Presenter Checklist](docs/PRESENTER_CHECKLIST.md)** - Demo preparation checklist
- **[Build Notes](docs/BUILD_NOTES.md)** - Build configuration and troubleshooting

## Learning Objectives

By completing this demo, you will learn how to:
- ✅ Identify domain boundaries in a monolithic application
- ✅ Use GitHub Copilot to generate microservice boilerplate
- ✅ Extract domain logic into separate services
- ✅ Implement REST-based inter-service communication
- ✅ Handle distributed transactions and data consistency
- ✅ Test microservices independently and as a system

## Next Steps

After completing the basic refactoring, consider these advanced topics:
- Implement API Gateway pattern
- Add service discovery (Eureka, Consul)
- Implement circuit breakers (Resilience4j)
- Add distributed tracing (Zipkin, Jaeger)
- Implement event-driven communication (Kafka, RabbitMQ)
- Add container orchestration (Docker, Kubernetes)

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Microservices Patterns](https://microservices.io/patterns/index.html)
- [GitHub Copilot Documentation](https://docs.github.com/en/copilot)
- [Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)

## License

This is a demo project for educational purposes.

## Support

For questions or issues, please open a GitHub issue or contact your FIS Global representative.
