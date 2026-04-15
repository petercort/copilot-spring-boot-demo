# Integration Test Plan for Service Controllers

## Overview

This document outlines the integration testing strategy for the e-commerce microservices architecture, focusing on controller-level interactions across Customer Service (port 8081), Inventory Service (port 8082), and Order Service (port 8083).

## Test Environment Setup

### Prerequisites
- Java 17+
- Maven 3.6+
- All three microservices running
- Eureka Server (port 8761) running (optional, services can work with direct URLs)
- H2 in-memory databases initialized with test data

### Service Endpoints
- **Customer Service**: `http://localhost:8081/api/customers`
- **Inventory Service**: `http://localhost:8082/api/products`
- **Order Service**: `http://localhost:8083/api/orders`

---

## Test Categories

### 1. Individual Service Controller Tests (Isolated)

#### 1.1 Customer Service Controller Tests

**Test Case 1.1.1: Create Customer**
- **Endpoint**: `POST /api/customers`
- **Objective**: Verify customer creation with valid data
- **Request Body**:
  ```json
  {
    "firstName": "Alice",
    "lastName": "Williams",
    "email": "alice.williams@example.com",
    "phoneNumber": "+1-555-0100"
  }
  ```
- **Expected Response**: 201 Created with customer object containing generated ID
- **Validation**: Customer data persisted and retrievable via GET

**Test Case 1.1.2: Get Customer by ID**
- **Endpoint**: `GET /api/customers/{id}`
- **Objective**: Retrieve existing customer
- **Expected Response**: 200 OK with customer details
- **Negative Case**: 404 Not Found for non-existent ID

**Test Case 1.1.3: Get Customer by Email**
- **Endpoint**: `GET /api/customers/email/{email}`
- **Objective**: Retrieve customer by unique email
- **Expected Response**: 200 OK with customer details
- **Negative Case**: 404 Not Found for non-existent email

**Test Case 1.1.4: Update Customer**
- **Endpoint**: `PUT /api/customers/{id}`
- **Objective**: Modify existing customer data
- **Expected Response**: 200 OK with updated customer
- **Negative Case**: 404 Not Found for invalid ID

**Test Case 1.1.5: Delete Customer**
- **Endpoint**: `DELETE /api/customers/{id}`
- **Objective**: Remove customer record
- **Expected Response**: 204 No Content
- **Negative Case**: 404 Not Found for invalid ID

#### 1.2 Inventory Service Controller Tests

**Test Case 1.2.1: Create Product**
- **Endpoint**: `POST /api/products`
- **Objective**: Add new product to inventory
- **Request Body**:
  ```json
  {
    "name": "Monitor",
    "sku": "MON-001",
    "description": "27-inch 4K Monitor",
    "category": "Electronics",
    "price": 399.99,
    "stockQuantity": 25,
    "inStock": true
  }
  ```
- **Expected Response**: 201 Created with product object

**Test Case 1.2.2: Get Product by ID**
- **Endpoint**: `GET /api/products/{id}`
- **Objective**: Retrieve product details
- **Expected Response**: 200 OK with product data

**Test Case 1.2.3: Get Product by SKU**
- **Endpoint**: `GET /api/products/sku/{sku}`
- **Objective**: Retrieve product by unique SKU
- **Expected Response**: 200 OK with product data

**Test Case 1.2.4: Get Products by Category**
- **Endpoint**: `GET /api/products/category/{category}`
- **Objective**: Filter products by category
- **Expected Response**: 200 OK with list of products

**Test Case 1.2.5: Get Low Stock Products**
- **Endpoint**: `GET /api/products/low-stock?threshold=10`
- **Objective**: Identify products needing restock
- **Expected Response**: 200 OK with filtered product list

**Test Case 1.2.6: Reserve Stock**
- **Endpoint**: `POST /api/products/{id}/reserve?quantity=5`
- **Objective**: Decrease available stock
- **Expected Response**: 200 OK if sufficient stock, 400 Bad Request if insufficient

**Test Case 1.2.7: Restore Stock**
- **Endpoint**: `POST /api/products/{id}/restore?quantity=5`
- **Objective**: Increase available stock (order cancellation)
- **Expected Response**: 200 OK

**Test Case 1.2.8: Update Product**
- **Endpoint**: `PUT /api/products/{id}`
- **Objective**: Modify product details or stock
- **Expected Response**: 200 OK with updated product

**Test Case 1.2.9: Delete Product**
- **Endpoint**: `DELETE /api/products/{id}`
- **Objective**: Remove product from catalog
- **Expected Response**: 204 No Content

#### 1.3 Order Service Controller Tests

**Test Case 1.3.1: Get All Orders**
- **Endpoint**: `GET /api/orders`
- **Objective**: Retrieve all orders
- **Expected Response**: 200 OK with order list

**Test Case 1.3.2: Get Order by ID**
- **Endpoint**: `GET /api/orders/{id}`
- **Objective**: Retrieve specific order
- **Expected Response**: 200 OK with order details

**Test Case 1.3.3: Get Orders by Customer ID**
- **Endpoint**: `GET /api/orders/customer/{customerId}`
- **Objective**: Retrieve customer order history
- **Expected Response**: 200 OK with filtered orders

**Test Case 1.3.4: Get Orders by Status**
- **Endpoint**: `GET /api/orders/status/{status}`
- **Objective**: Filter orders by status (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED)
- **Expected Response**: 200 OK with filtered orders

**Test Case 1.3.5: Update Order Status**
- **Endpoint**: `PATCH /api/orders/{id}/status?status=SHIPPED`
- **Objective**: Progress order through fulfillment stages
- **Expected Response**: 200 OK with updated order

**Test Case 1.3.6: Delete Order**
- **Endpoint**: `DELETE /api/orders/{id}`
- **Objective**: Remove order and restore inventory
- **Expected Response**: 204 No Content

---

### 2. Cross-Service Integration Tests

#### 2.1 Order Creation Integration Flow

**Test Case 2.1.1: Successful Order Creation**
- **Primary Service**: Order Service
- **Dependencies**: Customer Service, Inventory Service
- **Endpoint**: `POST /api/orders`
- **Request Body**:
  ```json
  {
    "customerId": 1,
    "items": [
      {"productId": 1, "quantity": 2},
      {"productId": 3, "quantity": 1}
    ],
    "shippingAddress": "456 Oak Avenue",
    "shippingCity": "San Francisco",
    "shippingState": "CA",
    "shippingZip": "94102",
    "shippingCountry": "USA"
  }
  ```
- **Test Steps**:
  1. Verify customer exists via Customer Service
  2. Check product availability via Inventory Service
  3. Reserve stock for each product via Inventory Service
  4. Create order in Order Service
  5. Verify order status is CONFIRMED
- **Expected Response**: 201 Created with order object
- **Validation Checks**:
  - Order contains correct customer ID
  - Order items match request
  - Product stock quantities reduced in Inventory Service
  - Total amount calculated correctly
  - Order number generated

**Test Case 2.1.2: Order Creation with Non-Existent Customer**
- **Endpoint**: `POST /api/orders`
- **Scenario**: Customer ID does not exist in Customer Service
- **Expected Behavior**:
  - Customer Service returns 404 or empty Optional
  - Order Service throws IllegalArgumentException
  - No stock reserved
  - No order persisted
- **Expected Response**: 400 Bad Request

**Test Case 2.1.3: Order Creation with Non-Existent Product**
- **Endpoint**: `POST /api/orders`
- **Scenario**: Product ID in order items does not exist
- **Expected Behavior**:
  - Inventory Service returns 404 for product lookup
  - Order Service aborts transaction
  - No stock reserved
  - No order persisted
- **Expected Response**: 400 Bad Request

**Test Case 2.1.4: Order Creation with Insufficient Stock**
- **Endpoint**: `POST /api/orders`
- **Scenario**: Requested quantity exceeds available stock
- **Expected Behavior**:
  - Stock reservation fails in Inventory Service
  - Order Service triggers compensation logic
  - Previously reserved items restored
  - No order persisted
- **Expected Response**: 400 Bad Request
- **Validation**: Stock quantities unchanged for all products

**Test Case 2.1.5: Order Creation with Out-of-Stock Product**
- **Endpoint**: `POST /api/orders`
- **Scenario**: Product marked as `inStock: false`
- **Expected Behavior**:
  - Order Service detects out-of-stock condition
  - No reservation attempted
  - No order persisted
- **Expected Response**: 400 Bad Request

**Test Case 2.1.6: Partial Stock Reservation Rollback (Saga Compensation)**
- **Endpoint**: `POST /api/orders`
- **Scenario**: Multi-item order where second item has insufficient stock
- **Test Steps**:
  1. Request order with 3 items
  2. First item reserves successfully
  3. Second item fails due to insufficient stock
  4. Third item never attempted
- **Expected Behavior**:
  - First item stock restored via compensation
  - Order creation fails
  - No order persisted
- **Expected Response**: 400 Bad Request
- **Critical Validation**: First product stock quantity matches pre-order level

#### 2.2 Order Cancellation Integration Flow

**Test Case 2.2.1: Cancel Confirmed Order**
- **Primary Service**: Order Service
- **Dependencies**: Inventory Service
- **Endpoint**: `PATCH /api/orders/{id}/status?status=CANCELLED`
- **Pre-conditions**: Order exists with status CONFIRMED
- **Expected Behavior**:
  - Order status updated to CANCELLED
  - Inventory Service restores stock for all order items
- **Expected Response**: 200 OK with updated order
- **Validation**: Stock quantities match pre-order levels

**Test Case 2.2.2: Delete Confirmed Order**
- **Endpoint**: `DELETE /api/orders/{id}`
- **Pre-conditions**: Order exists with status CONFIRMED
- **Expected Behavior**:
  - Order deleted from database
  - Inventory Service restores stock automatically
- **Expected Response**: 204 No Content
- **Validation**: Stock quantities restored

**Test Case 2.2.3: Cancel Already Cancelled Order**
- **Endpoint**: `PATCH /api/orders/{id}/status?status=CANCELLED`
- **Pre-conditions**: Order already CANCELLED
- **Expected Behavior**:
  - Status update succeeds (idempotent)
  - No duplicate stock restoration
- **Expected Response**: 200 OK

#### 2.3 Customer Deletion Impact

**Test Case 2.3.1: Delete Customer with Existing Orders**
- **Primary Service**: Customer Service
- **Dependencies**: Order Service
- **Endpoint**: `DELETE /api/customers/{id}`
- **Pre-conditions**: Customer has one or more orders
- **Test Approach**: Verify referential integrity handling
- **Expected Behavior**: 
  - **Option A**: Deletion prevented (400 Bad Request)
  - **Option B**: Soft delete with customer marked inactive
  - **Option C**: Orders remain with orphaned customer ID (denormalized)
- **Validation**: Order history preserved or properly handled

#### 2.4 Product Deletion Impact

**Test Case 2.4.1: Delete Product Referenced in Orders**
- **Primary Service**: Inventory Service
- **Dependencies**: Order Service
- **Endpoint**: `DELETE /api/products/{id}`
- **Pre-conditions**: Product exists in order items
- **Expected Behavior**:
  - **Option A**: Deletion prevented if in active orders
  - **Option B**: Soft delete marking product inactive
  - **Option C**: Allowed since order items store denormalized product data
- **Validation**: Order integrity maintained

---

### 3. Concurrent Access & Race Condition Tests

**Test Case 3.1: Simultaneous Stock Reservation**
- **Scenario**: Two orders request last available units concurrently
- **Setup**: Product has 5 units in stock
- **Actions**:
  - Thread 1: Reserve 5 units
  - Thread 2: Reserve 5 units (simultaneously)
- **Expected Behavior**: One succeeds, one fails due to insufficient stock
- **Validation**: Stock never goes negative

**Test Case 3.2: Concurrent Order Creation for Same Customer**
- **Scenario**: Multiple orders placed simultaneously by same customer
- **Expected Behavior**: All valid orders succeed independently
- **Validation**: No data corruption, correct stock deductions

---

### 4. Service Failure & Resilience Tests

**Test Case 4.1: Customer Service Unavailable**
- **Scenario**: Customer Service not responding (down or network issue)
- **Endpoint**: `POST /api/orders`
- **Expected Behavior**:
  - Feign client timeout or connection exception
  - Order Service returns 503 Service Unavailable or 500 Internal Server Error
  - No order persisted
  - No stock reserved

**Test Case 4.2: Inventory Service Unavailable During Creation**
- **Scenario**: Inventory Service fails after customer validation
- **Expected Behavior**:
  - Order creation fails gracefully
  - Error propagated to client
  - No partial order persisted

**Test Case 4.3: Inventory Service Unavailable During Cancellation**
- **Scenario**: Cannot restore stock due to Inventory Service failure
- **Expected Behavior**:
  - Order status still updated to CANCELLED
  - Log error for manual stock reconciliation
  - Return success to client (eventual consistency)

**Test Case 4.4: Feign Client Timeout**
- **Scenario**: Inter-service call exceeds timeout threshold
- **Expected Behavior**: Proper timeout handling with clear error message

---

### 5. Data Consistency & Validation Tests

**Test Case 5.1: Order Total Calculation**
- **Validation**: Order total matches sum of (quantity × unit price) for all items
- **Cross-Service Check**: Prices in order match current Inventory Service prices

**Test Case 5.2: Stock Quantity Accuracy**
- **Scenario**: Create and cancel multiple orders
- **Validation**: Final stock matches initial stock (reserve/restore symmetry)

**Test Case 5.3: Order Item Denormalization**
- **Validation**: Order stores product name, SKU, and price at time of order
- **Test**: Update product details in Inventory Service
- **Expected**: Historical orders retain original values

**Test Case 5.4: Customer Email Uniqueness**
- **Endpoint**: `POST /api/customers`
- **Scenario**: Create customer with duplicate email
- **Expected Response**: 400 Bad Request or 409 Conflict

**Test Case 5.5: Product SKU Uniqueness**
- **Endpoint**: `POST /api/products`
- **Scenario**: Create product with duplicate SKU
- **Expected Response**: 400 Bad Request or 409 Conflict

---

### 6. End-to-End User Journey Tests

**Test Case 6.1: Complete Order Lifecycle**
1. Create customer via Customer Service
2. Create products via Inventory Service
3. Create order via Order Service
4. Verify stock reduced
5. Update order status: CONFIRMED → SHIPPED → DELIVERED
6. Verify final state

**Test Case 6.2: Order Failure and Retry**
1. Attempt order with insufficient stock (fails)
2. Restock product via Inventory Service
3. Retry order (succeeds)
4. Verify stock correctly managed

**Test Case 6.3: Multi-Item Order with Mixed Outcomes**
1. Create order with 3 items:
   - Item 1: Available
   - Item 2: Low stock (insufficient for request)
   - Item 3: Available
2. Verify order fails
3. Verify no stock changes persisted

---

## Test Data Management

### Initial Test Data Setup

**Customers:**
```json
[
  {"id": 1, "firstName": "John", "lastName": "Doe", "email": "john.doe@example.com"},
  {"id": 2, "firstName": "Jane", "lastName": "Smith", "email": "jane.smith@example.com"},
  {"id": 3, "firstName": "Bob", "lastName": "Johnson", "email": "bob.johnson@example.com"}
]
```

**Products:**
```json
[
  {"id": 1, "name": "Laptop", "sku": "LAP-001", "price": 999.99, "stockQuantity": 10},
  {"id": 2, "name": "Mouse", "sku": "MOU-001", "price": 29.99, "stockQuantity": 50},
  {"id": 3, "name": "Keyboard", "sku": "KEY-001", "price": 79.99, "stockQuantity": 30},
  {"id": 4, "name": "Office Chair", "sku": "CHR-001", "price": 299.99, "stockQuantity": 5},
  {"id": 5, "name": "Standing Desk", "sku": "DSK-001", "price": 599.99, "stockQuantity": 3},
  {"id": 6, "name": "Webcam", "sku": "WEB-001", "price": 89.99, "stockQuantity": 15}
]
```

---

## Testing Tools & Frameworks

### Recommended Approaches

1. **Spring Boot Test with @SpringBootTest**
   - Start all services with random ports
   - Use TestRestTemplate or WebTestClient
   - Integration tests with real HTTP calls

2. **WireMock for Service Mocking**
   - Mock Customer/Inventory services when testing Order Service
   - Simulate failures and timeouts
   - Verify service call contracts

3. **Testcontainers**
   - Spin up services in Docker containers
   - Full microservices environment for tests
   - Database isolation per test

4. **REST Assured**
   - Fluent API for HTTP testing
   - BDD-style test syntax
   - Response validation

5. **Contract Testing (Spring Cloud Contract)**
   - Define consumer-driven contracts
   - Verify service compatibility
   - Prevent breaking changes

### Example Test Structure

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class OrderServiceIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testCreateOrder_Success() {
        // Arrange: Ensure customer and products exist
        CustomerDto customer = createTestCustomer();
        ProductDto product = createTestProduct();
        
        CreateOrderRequest request = new CreateOrderRequest(
            customer.getId(),
            List.of(new OrderItemRequest(product.getId(), 2)),
            "123 Main St", "New York", "NY", "10001", "USA"
        );
        
        // Act: Create order
        ResponseEntity<Order> response = restTemplate.postForEntity(
            "http://localhost:8083/api/orders",
            request,
            Order.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCustomerId()).isEqualTo(customer.getId());
        
        // Verify stock reduced
        ProductDto updatedProduct = getProduct(product.getId());
        assertThat(updatedProduct.getStockQuantity())
            .isEqualTo(product.getStockQuantity() - 2);
    }
}
```

---

## Test Execution Strategy

### Test Phases

**Phase 1: Unit Tests (Isolated Controllers)**
- Mock service dependencies
- Fast execution
- Run on every commit

**Phase 2: Integration Tests (Single Service)**
- Real database (H2 in-memory)
- Mock external service calls with WireMock
- Run on pull requests

**Phase 3: End-to-End Tests (All Services Running)**
- All services deployed
- Real inter-service communication
- Run nightly or before releases

### CI/CD Pipeline Integration

```yaml
# Example GitHub Actions workflow
stages:
  - name: Unit Tests
    run: mvn test -Dtest=*ControllerTest
    
  - name: Integration Tests
    services:
      - customer-service
      - inventory-service
      - order-service
    run: mvn verify -Dtest=*IntegrationTest
    
  - name: E2E Tests
    run: ./run-e2e-tests.sh
```

---

## Success Criteria

- **Code Coverage**: >80% for controller integration paths
- **Test Execution Time**: <5 minutes for full integration suite
- **Reliability**: <1% flakiness rate
- **Failure Detection**: All integration issues caught before production

---

## Known Issues & Future Improvements

### Current Limitations
1. No distributed tracing for request tracking across services
2. Manual retry logic not implemented
3. Circuit breaker pattern not yet in place
4. No chaos engineering tests (random service failures)

### Planned Enhancements
1. Add API Gateway integration tests
2. Implement performance/load testing scenarios
3. Add security/authentication integration tests
4. Create contract tests with Pact or Spring Cloud Contract
5. Implement service mesh testing (if Istio/Linkerd added)

---

## Appendix: Test Checklists

### Pre-Test Checklist
- [ ] All services building successfully
- [ ] Eureka Server running (if using service discovery)
- [ ] Test databases initialized
- [ ] Test data scripts executed
- [ ] Network connectivity verified between services

### Post-Test Checklist
- [ ] All temporary test data cleaned up
- [ ] Service logs reviewed for errors
- [ ] Performance metrics captured
- [ ] Failed tests documented with tickets
- [ ] Test coverage report generated

---

## References

- [Spring Boot Testing Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Microservices Testing Strategies](https://martinfowler.com/articles/microservice-testing/)
- [REST Assured Documentation](https://rest-assured.io/)
- [WireMock Documentation](http://wiremock.org/docs/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
