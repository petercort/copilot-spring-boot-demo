# GitHub Copilot Prompts for Microservices Refactoring

This document contains a curated collection of GitHub Copilot prompts that will help you refactor the monolithic application into microservices. Use these prompts as comments in your code or in Copilot Chat.

---

## Table of Contents

1. [Project Setup Prompts](#project-setup-prompts)
2. [Service Extraction Prompts](#service-extraction-prompts)
3. [REST Client Prompts](#rest-client-prompts)
4. [Configuration Prompts](#configuration-prompts)
5. [Data Transfer Object (DTO) Prompts](#data-transfer-object-dto-prompts)
6. [Error Handling Prompts](#error-handling-prompts)
7. [Testing Prompts](#testing-prompts)
8. [Advanced Patterns Prompts](#advanced-patterns-prompts)

---

## Project Setup Prompts

### Create a new Spring Boot microservice pom.xml

```xml
<!--
Create a Spring Boot 3.2.0 Maven pom.xml for a microservice named [SERVICE-NAME]
Group ID: com.fisglobal
Artifact ID: [service-name]
Version: 1.0.0-SNAPSHOT
Include dependencies:
- Spring Boot Web
- Spring Boot Data JPA
- H2 Database (runtime scope)
- Lombok (optional)
- Spring Boot Validation
- Spring Boot DevTools (runtime, optional)
- Spring Boot Starter Test (test scope)
Default port: [PORT-NUMBER]
Java version: 17
-->
```

**Usage**: Create a new `pom.xml` file and paste this as a comment. Replace `[SERVICE-NAME]` and `[PORT-NUMBER]` with your values.

---

### Create Spring Boot application class

```java
// Create a Spring Boot application class for [SERVICE-NAME] microservice
// Package: com.fisglobal.[servicename]
// Class name: [ServiceName]Application
// Include main method and @SpringBootApplication annotation
```

**Usage**: Create a new Java file and paste this comment at the top. Copilot will generate the complete class.

---

### Create application.properties for a microservice

```properties
# Spring Boot configuration for [SERVICE-NAME] microservice
# Application name: [service-name]
# Server port: [PORT]
# Database: H2 in-memory with name [dbname]
# JPA: Hibernate with ddl-auto=create-drop
# Enable SQL logging
# Enable H2 console at /h2-console
# Set logging level for com.fisglobal to DEBUG
```

**Usage**: Create `application.properties` file and paste this comment. Copilot will generate all properties.

---

## Service Extraction Prompts

### Extract Customer Service

```java
// Create a CustomerService class that manages customer operations
// Package: com.fisglobal.customer.service
// Dependencies: CustomerRepository (autowired)
// Methods:
// - getAllCustomers() returns List<Customer>
// - getCustomerById(Long id) returns Optional<Customer>
// - getCustomerByEmail(String email) returns Optional<Customer>
// - createCustomer(Customer customer) returns Customer, validates email uniqueness
// - updateCustomer(Long id, Customer details) returns Customer
// - deleteCustomer(Long id) returns void
// Use @Service, @RequiredArgsConstructor, @Slf4j, @Transactional annotations
// Add logging for each operation
```

---

### Extract Inventory Service

```java
// Create a ProductService class for inventory management
// Package: com.fisglobal.inventory.service
// Dependencies: ProductRepository
// Methods:
// - getAllProducts() returns all products
// - getProductById(Long id) returns Optional<Product>
// - getProductBySku(String sku) returns Optional<Product>
// - getProductsByCategory(String category) returns List<Product>
// - createProduct(Product product) validates SKU uniqueness
// - updateProduct(Long id, Product details) updates product
// - reserveStock(Long productId, Integer quantity) returns boolean, checks availability
// - restoreStock(Long productId, Integer quantity) adds back stock
// - deleteProduct(Long id) deletes product
// Use proper annotations and logging
```

---

### Extract Order Service (simplified, without REST calls)

```java
// Create an OrderService class for order management
// Package: com.fisglobal.order.service
// Dependencies: OrderRepository
// Methods:
// - getAllOrders() returns all orders
// - getOrderById(Long id) returns Optional<Order>
// - getOrdersByCustomerId(Long customerId) returns List<Order>
// - getOrdersByStatus(OrderStatus status) returns List<Order>
// - createOrder(CreateOrderRequest request) creates order
// - updateOrderStatus(Long id, OrderStatus status) updates status
// - deleteOrder(Long id) deletes order
// Use @Service, @Transactional, logging
```

---

## REST Client Prompts

### Create RestTemplate Configuration

```java
// Create a Configuration class that provides a RestTemplate bean
// Package: com.fisglobal.[service].config
// Class: RestTemplateConfig
// Bean name: restTemplate
// Configure timeouts: connection timeout 5000ms, read timeout 10000ms
// Use SimpleClientHttpRequestFactory for timeout configuration
// Add @Configuration annotation
```

---

### Create Customer REST Client

```java
// Create a REST client for calling the customer-service microservice
// Package: com.fisglobal.order.client
// Class: CustomerClient
// Base URL: http://localhost:8081
// Dependencies: RestTemplate (autowired)
// Methods:
// - getCustomerById(Long customerId) returns Optional<Customer>
//   GET /api/customers/{id}
//   Handle 404 as Optional.empty()
//   Handle other errors with appropriate exceptions
// Use @Service annotation
// Add error logging
```

---

### Create Inventory REST Client

```java
// Create a REST client for calling the inventory-service microservice
// Package: com.fisglobal.order.client
// Class: InventoryClient
// Base URL: http://localhost:8082
// Dependencies: RestTemplate
// Methods:
// - getProductById(Long productId) returns Optional<Product>
//   GET /api/products/{id}
// - reserveStock(Long productId, Integer quantity) returns boolean
//   POST /api/products/{id}/reserve?quantity={quantity}
//   Returns true if 200 OK, false if 400 Bad Request
// - restoreStock(Long productId, Integer quantity) returns void
//   POST /api/products/{id}/restore?quantity={quantity}
// Handle HTTP errors appropriately
// Use @Service and logging
```

---

### Refactor OrderService to Use REST Clients

**Copilot Chat Prompt**:
```
Refactor the OrderService class to use CustomerClient and InventoryClient instead of 
direct service dependencies (CustomerService and ProductService).

Changes needed:
1. Replace CustomerService with CustomerClient
2. Replace ProductService with InventoryClient
3. Update createOrder method to make REST calls
4. Update deleteOrder method to make REST calls
5. Add proper error handling for REST call failures
6. Maintain existing business logic
7. Keep transaction boundaries appropriate

Handle these scenarios:
- Customer not found (404 from customer-service)
- Product not found (404 from inventory-service)
- Insufficient stock (400 from inventory-service)
- Network errors (connection timeout, etc.)
```

---

## Configuration Prompts

### Create application.yml instead of properties

```yaml
# Create application.yml for [SERVICE-NAME] microservice
# Spring application name: [service-name]
# Server port: [PORT]
# Database: H2 in-memory
# JPA settings: show SQL, format SQL, ddl-auto create-drop
# H2 console: enabled at /h2-console
# Logging: DEBUG for com.fisglobal, INFO for Spring
# External service URLs (for order-service):
#   customer-service: http://localhost:8081
#   inventory-service: http://localhost:8082
```

---

### Create Bootstrap Configuration Class

```java
// Create a DataInitializer class that loads sample data on startup
// Package: com.fisglobal.[service].config
// Uses CommandLineRunner
// Dependencies: [Repository] classes
// For customer-service: create 3 sample customers
// For inventory-service: create 5 sample products
// Only initialize if tables are empty
// Add logging
// Use @Configuration annotation
```

---

## Data Transfer Object (DTO) Prompts

### Create Request DTO

```java
// Create a CreateOrderRequest DTO class
// Package: com.fisglobal.order.dto
// Fields:
// - customerId: Long, @NotNull
// - items: List<OrderItemRequest>, @NotEmpty, @Valid
// - shippingAddress: String
// - shippingCity: String
// - shippingState: String
// - shippingZip: String
// - shippingCountry: String
// Use Lombok @Data, @NoArgsConstructor, @AllArgsConstructor
// Add Jakarta validation annotations
```

---

### Create Response DTO

```java
// Create an OrderResponse DTO class for API responses
// Package: com.fisglobal.order.dto
// Fields:
// - id: Long
// - orderNumber: String
// - customerId: Long
// - customerName: String (not in Order entity, populated from customer-service)
// - status: OrderStatus
// - items: List<OrderItemResponse>
// - totalAmount: BigDecimal
// - createdAt: LocalDateTime
// Use Lombok annotations
// Add a static method: fromEntity(Order order, String customerName)
```

---

### Create Mapper Utility

```java
// Create a mapper class to convert between entities and DTOs
// Package: com.fisglobal.order.mapper
// Class: OrderMapper
// Static methods:
// - toOrderResponse(Order order, Customer customer) returns OrderResponse
// - toOrder(CreateOrderRequest request) returns Order
// - toOrderItemResponse(OrderItem item) returns OrderItemResponse
// Use @Component for Spring management
// Handle null checks appropriately
```

---

## Error Handling Prompts

### Create Global Exception Handler

```java
// Create a global exception handler for REST controllers
// Package: com.fisglobal.[service].exception
// Class: GlobalExceptionHandler
// Use @RestControllerAdvice
// Handle these exceptions:
// - IllegalArgumentException: return 400 Bad Request
// - ResourceNotFoundException: return 404 Not Found
// - MethodArgumentNotValidException: return 400 with validation errors
// - HttpClientErrorException: propagate status code
// - Generic Exception: return 500 Internal Server Error
// Response format: ErrorResponse with timestamp, status, message, path
// Add logging for all exceptions
```

---

### Create Custom Exception

```java
// Create a custom ResourceNotFoundException
// Package: com.fisglobal.[service].exception
// Extends RuntimeException
// Constructor: ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue)
// Message format: "[ResourceName] not found with [fieldName]: [fieldValue]"
// Example: "Customer not found with id: 123"
```

---

### Create ErrorResponse DTO

```java
// Create an ErrorResponse class for error responses
// Package: com.fisglobal.[service].dto
// Fields:
// - timestamp: LocalDateTime
// - status: int
// - error: String
// - message: String
// - path: String
// Constructor that takes HttpStatus and message
// Use Lombok @Data, @AllArgsConstructor
```

---

## Testing Prompts

### Create Unit Test for Service

```java
// Create unit tests for CustomerService
// Package: com.fisglobal.customer.service (in test directory)
// Class: CustomerServiceTest
// Use @ExtendWith(MockitoExtension.class)
// Mock: CustomerRepository
// Test methods:
// - testGetAllCustomers_ReturnsCustomerList
// - testGetCustomerById_WhenExists_ReturnsCustomer
// - testGetCustomerById_WhenNotExists_ReturnsEmpty
// - testCreateCustomer_WhenEmailUnique_SavesCustomer
// - testCreateCustomer_WhenEmailExists_ThrowsException
// - testUpdateCustomer_WhenExists_UpdatesCustomer
// - testDeleteCustomer_WhenExists_DeletesCustomer
// Use Mockito when(), verify(), assertThat()
// Use proper given-when-then structure
```

---

### Create Integration Test for Controller

```java
// Create integration tests for CustomerController
// Package: com.fisglobal.customer.controller (in test directory)
// Class: CustomerControllerIntegrationTest
// Use @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
// Use @AutoConfigureTestDatabase
// Dependencies: TestRestTemplate, CustomerRepository
// Test methods:
// - testGetAllCustomers_Returns200AndCustomerList
// - testGetCustomerById_WhenExists_Returns200
// - testGetCustomerById_WhenNotExists_Returns404
// - testCreateCustomer_WithValidData_Returns201
// - testCreateCustomer_WithInvalidData_Returns400
// - testUpdateCustomer_WhenExists_Returns200
// - testDeleteCustomer_WhenExists_Returns204
// Clean up test data after each test (@AfterEach)
```

---

### Create REST Client Test

```java
// Create unit tests for CustomerClient
// Package: com.fisglobal.order.client (in test directory)
// Class: CustomerClientTest
// Use @ExtendWith(MockitoExtension.class)
// Mock: RestTemplate
// Test scenarios:
// - testGetCustomerById_WhenExists_ReturnsCustomer
// - testGetCustomerById_When404_ReturnsEmpty
// - testGetCustomerById_WhenServerError_ThrowsException
// - testGetCustomerById_WhenTimeout_ThrowsException
// Mock RestTemplate.getForEntity() and exchange()
// Simulate different HTTP responses
```

---

## Advanced Patterns Prompts

### Implement Circuit Breaker with Resilience4j

```java
// Add Resilience4j circuit breaker to CustomerClient
// Dependency: io.github.resilience4j:resilience4j-spring-boot2
// Wrap getCustomerById method with @CircuitBreaker
// Circuit breaker name: "customerService"
// Fallback method: getCustomerByIdFallback(Long customerId, Exception e)
// Fallback returns Optional.empty() and logs warning
// Configuration in application.yml:
//   failure rate threshold: 50%
//   wait duration in open state: 10s
//   permitted calls in half-open state: 3
```

---

### Add Service Discovery with Eureka

```java
// Configure this microservice as a Eureka client
// Add dependency: spring-cloud-starter-netflix-eureka-client
// Application class: add @EnableDiscoveryClient
// application.yml configuration:
//   eureka.client.service-url.defaultZone: http://localhost:8761/eureka
//   spring.application.name: [service-name]
//   eureka.instance.prefer-ip-address: true
// Update REST clients to use service names instead of localhost URLs
```

---

### Implement API Gateway

```java
// Create a new Spring Cloud Gateway project
// Dependencies:
// - spring-cloud-starter-gateway
// - spring-cloud-starter-netflix-eureka-client
// Port: 8080
// Route configuration for:
// - /api/customers/** -> customer-service
// - /api/products/** -> inventory-service
// - /api/orders/** -> order-service
// Add filters: logging, request ID
// Enable service discovery
```

---

### Add Distributed Tracing

```java
// Add Spring Cloud Sleuth and Zipkin to all microservices
// Dependencies:
// - spring-cloud-starter-sleuth
// - spring-cloud-sleuth-zipkin
// Configuration:
//   spring.zipkin.base-url: http://localhost:9411
//   spring.sleuth.sampler.probability: 1.0 (sample all requests)
// Add trace ID and span ID to logs
// Update log pattern to include trace info
```

---

### Implement Event-Driven Communication

```java
// Add Kafka event publishing to inventory-service
// Dependency: spring-kafka
// When stock is reserved, publish StockReservedEvent
// Event fields: productId, quantity, orderId, timestamp
// Topic: inventory-events
// Configuration:
//   spring.kafka.bootstrap-servers: localhost:9092
//   spring.kafka.producer.key-serializer: StringSerializer
//   spring.kafka.producer.value-serializer: JsonSerializer
// Create KafkaProducer component
// Update ProductService to publish events
```

---

### Add Caching with Redis

```java
// Add Redis caching to customer-service
// Dependencies:
// - spring-boot-starter-data-redis
// - spring-boot-starter-cache
// Enable caching: @EnableCaching on application class
// Cache configuration:
//   Redis host: localhost
//   Redis port: 6379
//   TTL: 5 minutes
// Add @Cacheable to getCustomerById method
//   Cache name: "customers"
//   Key: #id
// Add @CacheEvict on update and delete methods
// Add @CachePut on create method
```

---

### Implement Health Checks

```java
// Add custom health indicators to each microservice
// Dependency: spring-boot-starter-actuator
// Create CustomHealthIndicator class implementing HealthIndicator
// Check external dependencies:
//   - Database connection
//   - Dependent services (for order-service, check customer and inventory services)
//   - Disk space
// Return Health.up() or Health.down() with details
// Expose /actuator/health endpoint
// Configure management.endpoints.web.exposure.include: health,info
```

---

## Tips for Using These Prompts

### 1. **Start with Comments**
Place the prompt as a comment in the appropriate file location, then let Copilot generate the code.

### 2. **Use Copilot Chat for Refactoring**
For larger changes (like refactoring OrderService), use Copilot Chat instead of inline comments.

### 3. **Iterate on Suggestions**
If Copilot's first suggestion isn't perfect, reject it (Esc) and try rephrasing the prompt.

### 4. **Combine Multiple Prompts**
You can combine prompts to get more specific results. For example:
```java
// Create a CustomerService with CRUD operations
// Use constructor injection with Lombok @RequiredArgsConstructor
// Add @Transactional(readOnly = true) for query methods
// Add detailed logging with @Slf4j
// Include JSR-303 validation
```

### 5. **Reference Existing Code**
Mention existing classes in your prompts:
```java
// Create a CustomerClient similar to the InventoryClient
// Use the same error handling pattern
```

### 6. **Be Specific About Patterns**
Specify architectural patterns you want to follow:
```java
// Implement the Repository pattern for Customer entity
// Follow the same structure as ProductRepository
```

---

## Copilot Chat Examples

### Extract a Complete Service

```
@workspace Create a complete customer-service microservice based on the customer 
package in the monolith. Include:
1. A new Maven project with Spring Boot 3.2.0
2. Customer entity, repository, service, and controller
3. Configuration for H2 database
4. Sample data initializer
5. Application properties with port 8081
6. Package structure: com.fisglobal.customer

Maintain the same business logic but make it independent from other domains.
```

---

### Generate Tests for a Class

```
Generate comprehensive unit tests for the OrderService class. Include:
- Tests for all public methods
- Happy path scenarios
- Error scenarios (not found, invalid input)
- Edge cases (empty lists, null values)
- Mock all dependencies (OrderRepository, CustomerClient, InventoryClient)
- Use JUnit 5 and Mockito
- Follow AAA (Arrange-Act-Assert) pattern
```

---

### Implement a New Feature

```
Add a new feature to the order-service: order cancellation workflow.

Requirements:
1. New endpoint: POST /api/orders/{id}/cancel
2. Only orders with status PENDING or CONFIRMED can be cancelled
3. When cancelled:
   - Update order status to CANCELLED
   - Call inventory-service to restore stock for all items
   - Log the cancellation
4. Return 200 with updated order, or 400 if order can't be cancelled
5. Add appropriate error handling
6. Write unit tests

Update OrderService, OrderController, and create tests.
```

---

### Refactor for Best Practices

```
Refactor the OrderService to follow these best practices:
1. Separate DTOs from entities (don't expose entities in REST responses)
2. Create OrderResponse and CreateOrderRequest DTOs
3. Add input validation with Jakarta Validation
4. Implement proper exception handling with custom exceptions
5. Add comprehensive logging at service boundaries
6. Use Optional properly (don't use get() without checking)
7. Add Javadoc comments for public methods
8. Extract magic strings to constants

Maintain all existing functionality.
```

---

## Troubleshooting Copilot

### If Copilot Isn't Suggesting Anything

1. **Make your prompt more specific** - Add details about packages, method signatures, annotations
2. **Provide context** - Reference existing similar classes
3. **Break it down** - Split complex prompts into smaller chunks
4. **Check your comment syntax** - Use `//` for Java, `#` for properties/YAML
5. **Try Copilot Chat** - Sometimes chat works better for complex requests

### If Suggestions Are Wrong

1. **Reject and rephrase** - Press Esc and rewrite your prompt
2. **Add constraints** - Specify what NOT to do
3. **Provide examples** - Reference code style you want to match
4. **Use Chat for corrections** - "The previous suggestion had X wrong, please fix Y"

### If You Need Multiple Variations

1. **Use Ctrl/Cmd + Enter** - View multiple suggestions
2. **Ask for alternatives** - "Generate 3 different approaches to..."
3. **Specify the pattern** - "Create using Factory pattern" vs "Create using Builder pattern"

---

## Next Steps

After mastering these prompts, explore:
- Creating custom Copilot prompts for your organization's patterns
- Building a prompt library for common microservices patterns
- Sharing effective prompts with your team
- Combining Copilot with other tools (SonarQube, test coverage tools)

Remember: Copilot is a tool to accelerate development, but you still need to understand the architecture and verify the generated code!
