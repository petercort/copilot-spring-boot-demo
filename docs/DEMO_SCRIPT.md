# Demo Script: Splitting a Monolith into Microservices with GitHub Copilot

## Presentation Overview (60 minutes)

This demo script guides you through a live demonstration of refactoring a monolithic Spring Boot application into domain-specific microservices using GitHub Copilot.

**Target Audience**: Developers, Architects, Technical Leaders

**Prerequisites**: 
- GitHub Copilot enabled and active
- Java 17+ and Maven installed
- IDE open with this project loaded
- Familiarity with Spring Boot basics

---

## Phase 1: Introduction & Context (10 minutes)

### 1.1 Introduction

**Script**: 
> "Today, I'll demonstrate how GitHub Copilot can accelerate the process of refactoring a monolithic application into microservices. We'll start with a single Spring Boot application that handles three business domains: Customers, Inventory, and Orders. By the end, we'll have three independent microservices."

### 1.2 Show the Monolithic Application

**Action**: Open the project structure in your IDE

**Script**:
> "Let's look at our starting point. This is a typical monolithic e-commerce application with three main domains:"

**Demo Steps**:
1. Show the package structure: `customer/`, `inventory/`, `order/`
2. Open `OrderService.java` and highlight the dependencies:
   ```java
   private final CustomerService customerService;
   private final ProductService productService;
   ```

**Script**:
> "Notice how the OrderService directly depends on CustomerService and ProductService. This is tight coupling. All three domains share the same database and deployment lifecycle."

### 1.3 Run the Monolith

**Demo Steps**:
1. Run the application: `mvn spring-boot:run`
2. Open browser to `http://localhost:8080/h2-console`
3. Show the shared database with all tables

**Script**:
> "When we run this application, all domains share a single database instance. Let's create a test order to see the cross-domain dependencies in action."

**Demo Steps**:
4. Use curl or Postman to create an order:
   ```bash
   curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": 1,
       "items": [{"productId": 1, "quantity": 1}],
       "shippingAddress": "123 Main St",
       "shippingCity": "New York",
       "shippingState": "NY",
       "shippingZip": "10001",
       "shippingCountry": "USA"
     }'
   ```

**Script**:
> "This single API call validates the customer, checks inventory, reserves stock, and creates an order—all within a single transaction. While this works, it creates tight coupling and scaling challenges."

---

## Phase 2: Planning the Microservices Architecture (10 minutes)

### 2.1 Identify Domain Boundaries

**Action**: Open a whiteboard or diagram tool

**Script**:
> "Before we refactor, we need to identify clear domain boundaries. In Domain-Driven Design terms, we're looking for bounded contexts."

**Demo Steps**:
1. Draw three boxes representing the future microservices:
   - **Customer Service** (Port 8081) - Manages customer data
   - **Inventory Service** (Port 8082) - Manages products and stock
   - **Order Service** (Port 8083) - Orchestrates order processing

2. Draw arrows showing communication:
   - Order Service → Customer Service (validate customer)
   - Order Service → Inventory Service (check/reserve stock)

**Script**:
> "Each service will own its data and expose REST APIs. The Order Service will coordinate with the other services via HTTP calls instead of direct Java method calls."

### 2.2 Discuss Challenges

**Script**:
> "When splitting a monolith, we face several challenges:
> 1. **Distributed Transactions** - We can't use database transactions across services
> 2. **Data Duplication** - Services may need to cache data from other services
> 3. **Network Latency** - HTTP calls are slower than method calls
> 4. **Deployment Complexity** - We now manage three services instead of one
> 
> GitHub Copilot will help us generate the boilerplate code quickly, but we still need to design for these challenges."

---

## Phase 3: Extract the Customer Service (15 minutes)

### 3.1 Create the Customer Service Project

**Action**: Create a new directory for the customer service

**Demo Steps**:
1. Open terminal in the project root
2. Create new directory: `mkdir customer-service`
3. Navigate: `cd customer-service`

**Script**:
> "Let's create our first microservice. I'll use GitHub Copilot to generate the project structure."

### 3.2 Use Copilot to Generate pom.xml

**Action**: Create a new file `customer-service/pom.xml`

**Copilot Prompt** (type as a comment):
```xml
<!-- 
Create a Spring Boot 3.2.0 pom.xml for a microservice named customer-service
Include dependencies for:
- Spring Boot Web
- Spring Boot Data JPA
- H2 Database
- Lombok
- Validation
Port should be 8081
-->
```

**Demo Steps**:
1. Start typing the comment
2. Let Copilot suggest the complete pom.xml
3. Accept the suggestion

**Script**:
> "Notice how Copilot understands the context and generates a complete pom.xml with all necessary dependencies. I specified port 8081 to avoid conflicts with the monolith."

### 3.3 Use Copilot to Generate Application Class

**Action**: Create `customer-service/src/main/java/com/fisglobal/customer/CustomerServiceApplication.java`

**Copilot Prompt** (type as a comment):
```java
// Spring Boot application class for customer microservice on port 8081
```

**Demo Steps**:
1. Let Copilot generate the `@SpringBootApplication` class
2. Accept the suggestion

### 3.4 Copy Customer Domain Code

**Script**:
> "Now I'll copy the customer domain code from the monolith. In a real scenario, Copilot can help us refactor imports and package names."

**Demo Steps**:
1. Create the directory structure and copy files from monolith to customer-service:

   ```bash
   # From the customer-service directory
   # Create package structure
   mkdir -p src/main/java/com/fisglobal/customer/{model,repository,service,controller}
   mkdir -p src/main/resources
   
   # Copy the customer domain files
   cp -a ../src/main/java/com/fisglobal/demo/customer/model/Customer.java \
      src/main/java/com/fisglobal/customer/model/
   
   cp -a ../src/main/java/com/fisglobal/demo/customer/repository/CustomerRepository.java \
      src/main/java/com/fisglobal/customer/repository/
   
   cp -a ../src/main/java/com/fisglobal/demo/customer/service/CustomerService.java \
      src/main/java/com/fisglobal/customer/service/
   
   cp -a ../src/main/java/com/fisglobal/demo/customer/controller/CustomerController.java \
      src/main/java/com/fisglobal/customer/controller/
   ```

2. Use Copilot to fix package names:
   - Select all copied files
   - Use Copilot Chat: "Update all package names from com.fisglobal.demo.customer to com.fisglobal.customer"

### 3.5 Create application.properties

**Action**: Create `customer-service/src/main/resources/application.properties`

**Copilot Prompt**:
```properties
# Spring Boot configuration for customer microservice
# Port: 8081
# Database: H2 in-memory
# Service name: customer-service
```

**Demo Steps**:
1. Let Copilot complete the configuration
2. Verify port is 8081

### 3.6 Test the Customer Service

**Demo Steps**:
1. Run: `mvn spring-boot:run` (in customer-service directory)
2. Test: `curl http://localhost:8081/api/customers`
3. Show that it returns the customer list

**Script**:
> "Our first microservice is running! It's independent, has its own database, and exposes a clean REST API."

---

## Phase 4: Extract the Inventory Service (10 minutes)

### 4.1 Use Copilot to Generate Inventory Service

**Script**:
> "Now that we've done this once, let's see how Copilot can speed up creating the second service."

**Demo Steps**:
1. Create directory structure and copy inventory domain files:
   ```bash
   # From the inventory-service directory
   # Create package structure
   mkdir -p src/main/java/com/fisglobal/inventory/{model,repository,service,controller}
   mkdir -p src/main/resources
   
   # Copy inventory domain files
   cp -a ../src/main/java/com/fisglobal/demo/inventory/model/Product.java \
      src/main/java/com/fisglobal/inventory/model/
   
   cp -a ../src/main/java/com/fisglobal/demo/inventory/repository/ProductRepository.java \
      src/main/java/com/fisglobal/inventory/repository/
   
   cp -a ../src/main/java/com/fisglobal/demo/inventory/service/ProductService.java \
      src/main/java/com/fisglobal/inventory/service/
   
   cp -a ../src/main/java/com/fisglobal/demo/inventory/controller/ProductController.java \
      src/main/java/com/fisglobal/inventory/controller/
   ```

2. Use Copilot Chat to update package names and create supporting files:

**Copilot Chat Prompt**:
```
Update all package names from com.fisglobal.demo.inventory to com.fisglobal.inventory.
Create a Spring Boot application class for inventory-service on port 8082.
Create pom.xml similar to customer-service but with artifactId inventory-service.
Create application.properties for port 8082 with database name inventorydb.
```

### 4.2 Test the Inventory Service

**Demo Steps**:
1. Run: `mvn spring-boot:run` (in inventory-service directory)
2. Test: `curl http://localhost:8082/api/products`
3. Show the product list

**Script**:
> "With Copilot's help, creating the second service was much faster. Notice how it maintained consistency with our first service's structure."

---

## Phase 5: Extract the Order Service with REST Communication (10 minutes)

### 5.1 Create the Order Service

**Script**:
> "The Order Service is more complex because it needs to communicate with both Customer and Inventory services. Let's use Copilot to help us implement REST-based communication."

**Demo Steps**:
1. Create directory structure and copy order domain files:
   ```bash
   # From the order-service directory
   # Create package structure
   mkdir -p src/main/java/com/fisglobal/order/{model,repository,service,controller,dto,client}
   mkdir -p src/main/resources
   
   # Copy order domain files
   cp -a ../src/main/java/com/fisglobal/demo/order/model/Order.java \
      src/main/java/com/fisglobal/order/model/
   
   cp -a ../src/main/java/com/fisglobal/demo/order/model/OrderItem.java \
      src/main/java/com/fisglobal/order/model/
   
   cp -a ../src/main/java/com/fisglobal/demo/order/repository/OrderRepository.java \
      src/main/java/com/fisglobal/order/repository/
   
   cp -a ../src/main/java/com/fisglobal/demo/order/service/OrderService.java \
      src/main/java/com/fisglobal/order/service/
   
   cp -a ../src/main/java/com/fisglobal/demo/order/controller/OrderController.java \
      src/main/java/com/fisglobal/order/controller/
   
   cp -a ../src/main/java/com/fisglobal/demo/order/dto/CreateOrderRequest.java \
      src/main/java/com/fisglobal/order/dto/
   
   cp -a ../src/main/java/com/fisglobal/demo/order/dto/OrderItemRequest.java \
      src/main/java/com/fisglobal/order/dto/
   ```

2. Use Copilot to update package names:
   - Use Copilot Chat: "Update all package names from com.fisglobal.demo.order to com.fisglobal.order"

### 5.2 Use Copilot to Create REST Clients

**Action**: Create `order-service/src/main/java/com/fisglobal/order/client/CustomerClient.java`

**Copilot Prompt**:
```java
// REST client for calling customer-service at http://localhost:8081
// Use Spring's RestTemplate
// Method: getCustomerById(Long id) returns Customer
```

**Demo Steps**:
1. Let Copilot generate the CustomerClient class
2. Review and accept

**Action**: Create `order-service/src/main/java/com/fisglobal/order/client/InventoryClient.java`

**Copilot Prompt**:
```java
// REST client for calling inventory-service at http://localhost:8082
// Use Spring's RestTemplate
// Methods:
// - getProductById(Long id) returns Product
// - reserveStock(Long productId, Integer quantity) returns boolean
```

**Demo Steps**:
1. Let Copilot generate the InventoryClient class
2. Review and accept

### 5.3 Refactor OrderService to Use REST Clients

**Action**: Update `order-service/src/main/java/com/fisglobal/order/service/OrderService.java`

**Copilot Prompt** (in Copilot Chat):
```
Refactor the OrderService to use CustomerClient and InventoryClient instead of 
direct service dependencies. Replace all calls to customerService with REST calls 
via CustomerClient, and all calls to productService with REST calls via InventoryClient.
Handle potential HTTP errors appropriately.
```

**Demo Steps**:
1. Let Copilot refactor the service
2. Review the changes:
   - Direct method calls → REST calls
   - Exception handling for HTTP errors
   - Proper error responses

### 5.4 Configure RestTemplate Bean

**Action**: Add RestTemplate configuration

**Copilot Prompt**:
```java
// Create a Configuration class that provides a RestTemplate bean
// with connection timeout of 5 seconds and read timeout of 10 seconds
```

**Demo Steps**:
1. Let Copilot create the configuration
2. Review and accept

### 5.5 Test the Complete System

**Script**:
> "Now let's test all three services working together."

**Demo Steps**:
1. Ensure all three services are running:
   - Customer Service (8081)
   - Inventory Service (8082)
   - Order Service (8083)

2. Create an order:
   ```bash
   curl -X POST http://localhost:8083/api/orders \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": 1,
       "items": [{"productId": 1, "quantity": 1}],
       "shippingAddress": "123 Main St",
       "shippingCity": "New York",
       "shippingState": "NY",
       "shippingZip": "10001",
       "shippingCountry": "USA"
     }'
   ```

3. Show the order was created
4. Check inventory stock was reduced:
   ```bash
   curl http://localhost:8082/api/products/1
   ```

**Script**:
> "Success! The Order Service called the Customer Service to validate the customer, then called the Inventory Service to reserve stock. All three services are working independently but coordinating through REST APIs."

---

## Phase 6: Discuss Trade-offs and Next Steps (5 minutes)

### 6.1 Review What We've Achieved

**Script**:
> "Let's review what we've accomplished:
> ✅ Split one monolith into three independent microservices
> ✅ Each service has its own database and lifecycle
> ✅ Services communicate via REST APIs
> ✅ Used GitHub Copilot to accelerate development by 50-70%
> 
> GitHub Copilot helped us with:
> - Generating boilerplate code (pom.xml, application classes)
> - Creating REST clients
> - Refactoring service dependencies to REST calls
> - Maintaining code consistency across services"

### 6.2 Discuss Trade-offs

**Script**:
> "We've gained:
> - Independent scaling (scale order service separately)
> - Independent deployment (update customer service without touching orders)
> - Technology flexibility (each service could use different databases)
> - Team autonomy (different teams can own different services)
> 
> But we've also introduced:
> - Network latency (REST calls are slower than method calls)
> - Distributed transaction challenges (no ACID across services)
> - Increased operational complexity (monitoring, logging, deployment)
> - Data consistency challenges (eventual consistency vs strong consistency)"

### 6.3 Next Steps and Advanced Patterns

**Script**:
> "To make this production-ready, you'd want to add:
> 1. **API Gateway** - Single entry point (Spring Cloud Gateway)
> 2. **Service Discovery** - Dynamic service registration (Eureka, Consul)
> 3. **Circuit Breakers** - Resilience patterns (Resilience4j)
> 4. **Distributed Tracing** - Request tracking (Zipkin, Jaeger)
> 5. **Event-Driven Communication** - Async messaging (Kafka, RabbitMQ)
> 6. **Containerization** - Docker and Kubernetes
> 
> GitHub Copilot can help with all of these patterns too!"

---

## Phase 7: Q&A and Experimentation (remaining time)

### Suggested Activities

**Script**:
> "Feel free to experiment with this codebase. Here are some exercises you can try with Copilot's help:"

1. **Add a new endpoint**: Use Copilot to add a "search customers by city" endpoint
2. **Implement caching**: Ask Copilot to add Redis caching to the Customer Service
3. **Add validation**: Use Copilot to enhance input validation
4. **Create DTOs**: Ask Copilot to create separate DTOs for requests/responses
5. **Add error handling**: Use Copilot to implement global exception handling
6. **Write tests**: Ask Copilot to generate unit and integration tests

### Copilot Tips for Continued Learning

**Script**:
> "Tips for using Copilot effectively:
> - Be specific in your comments and prompts
> - Provide context (mention related files or patterns)
> - Review and understand the generated code
> - Use Copilot Chat for larger refactorings
> - Iterate on suggestions - you can reject and try again
> - Combine Copilot with your domain knowledge"

---

## Appendix: Troubleshooting

### Common Issues

**Issue**: Services can't communicate
- **Solution**: Check all services are running on correct ports
- **Verify**: `curl http://localhost:8081/actuator/health` (if actuator is enabled)

**Issue**: Port already in use
- **Solution**: Change port in application.properties
- **Kill process**: `lsof -ti:8081 | xargs kill -9` (macOS/Linux)

**Issue**: RestTemplate not found
- **Solution**: Ensure spring-boot-starter-web is in pom.xml

**Issue**: Database connection errors
- **Solution**: Check H2 configuration in application.properties

---

## Demo Checklist

Before the demo:
- [ ] All dependencies installed (Java, Maven)
- [ ] GitHub Copilot enabled and working
- [ ] Project runs successfully
- [ ] Sample curl commands tested
- [ ] IDE configured and ready
- [ ] Backup code in case of issues

During the demo:
- [ ] Explain the problem domain clearly
- [ ] Show the monolith first
- [ ] Let Copilot generate code (don't just paste)
- [ ] Explain what Copilot generated
- [ ] Test each service as you create it
- [ ] Encourage questions throughout

After the demo:
- [ ] Share repository access
- [ ] Provide additional resources
- [ ] Schedule follow-up if needed

---

## Conclusion

This demo shows how GitHub Copilot can dramatically accelerate the process of refactoring monolithic applications into microservices. While Copilot handles the boilerplate and repetitive tasks, you maintain control over architecture decisions and business logic.

**Key Takeaway**: Copilot is a productivity multiplier, not a replacement for good architectural thinking. Use it to move faster while keeping quality high.
