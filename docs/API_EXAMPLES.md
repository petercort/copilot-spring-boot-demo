# API Testing Guide

This file contains sample API calls to test the monolithic application. You can use these with curl, Postman, or any HTTP client.

## Customer API

### Get All Customers
```bash
curl http://localhost:8080/api/customers
```

### Get Customer by ID
```bash
curl http://localhost:8080/api/customers/1
```

### Get Customer by Email
```bash
curl http://localhost:8080/api/customers/email/john.doe@example.com
```

### Create Customer
```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Alice",
    "lastName": "Williams",
    "email": "alice.williams@example.com",
    "phone": "555-1111",
    "address": "321 Elm St",
    "city": "Boston",
    "state": "MA",
    "zipCode": "02101",
    "country": "USA"
  }'
```

### Update Customer
```bash
curl -X PUT http://localhost:8080/api/customers/1 \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "555-9999",
    "address": "123 Main St",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "USA"
  }'
```

### Delete Customer
```bash
curl -X DELETE http://localhost:8080/api/customers/3
```

---

## Product API

### Get All Products
```bash
curl http://localhost:8080/api/products
```

### Get Active Products Only
```bash
curl "http://localhost:8080/api/products?activeOnly=true"
```

### Get Product by ID
```bash
curl http://localhost:8080/api/products/1
```

### Get Product by SKU
```bash
curl http://localhost:8080/api/products/sku/LAPTOP-001
```

### Get Products by Category
```bash
curl http://localhost:8080/api/products/category/Electronics
```

### Get Low Stock Products
```bash
curl "http://localhost:8080/api/products/low-stock?threshold=50"
```

### Create Product
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "USB-C Hub",
    "description": "7-in-1 USB-C hub with HDMI and ethernet",
    "sku": "HUB-001",
    "price": 49.99,
    "stockQuantity": 150,
    "category": "Electronics",
    "reorderLevel": 20,
    "active": true
  }'
```

### Update Product
```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Laptop Computer",
    "description": "High-performance laptop for business and gaming - Updated",
    "sku": "LAPTOP-001",
    "price": 1199.99,
    "stockQuantity": 45,
    "category": "Electronics",
    "reorderLevel": 10,
    "active": true
  }'
```

### Reserve Stock
```bash
curl -X POST "http://localhost:8080/api/products/1/reserve?quantity=2"
```

### Restore Stock
```bash
curl -X POST "http://localhost:8080/api/products/1/restore?quantity=2"
```

### Delete Product
```bash
curl -X DELETE http://localhost:8080/api/products/7
```

---

## Order API

### Get All Orders
```bash
curl http://localhost:8080/api/orders
```

### Get Order by ID
```bash
curl http://localhost:8080/api/orders/1
```

### Get Order by Order Number
```bash
curl http://localhost:8080/api/orders/order-number/ORD-1234567890
```

### Get Orders by Customer ID
```bash
curl http://localhost:8080/api/orders/customer/1
```

### Get Orders by Status
```bash
curl http://localhost:8080/api/orders/status/CONFIRMED
```

### Create Order (Single Item)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [
      {
        "productId": 1,
        "quantity": 1
      }
    ],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingState": "NY",
    "shippingZip": "10001",
    "shippingCountry": "USA"
  }'
```

### Create Order (Multiple Items)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 2,
    "items": [
      {
        "productId": 1,
        "quantity": 1
      },
      {
        "productId": 2,
        "quantity": 2
      },
      {
        "productId": 3,
        "quantity": 1
      }
    ],
    "shippingAddress": "456 Oak Ave",
    "shippingCity": "Los Angeles",
    "shippingState": "CA",
    "shippingZip": "90001",
    "shippingCountry": "USA"
  }'
```

### Update Order Status
```bash
curl -X PATCH "http://localhost:8080/api/orders/1/status?status=PROCESSING"
```

Available statuses: `PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`

### Cancel Order (Updates status to CANCELLED)
```bash
curl -X PATCH "http://localhost:8080/api/orders/1/status?status=CANCELLED"
```

### Delete Order
```bash
curl -X DELETE http://localhost:8080/api/orders/1
```

---

## Testing Scenarios

### Scenario 1: Complete Order Workflow

```bash
# 1. Check available products
curl http://localhost:8080/api/products

# 2. Check customer details
curl http://localhost:8080/api/customers/1

# 3. Create an order
ORDER_RESPONSE=$(curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [{"productId": 1, "quantity": 1}],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingState": "NY",
    "shippingZip": "10001",
    "shippingCountry": "USA"
  }')

echo $ORDER_RESPONSE

# 4. Verify stock was reduced
curl http://localhost:8080/api/products/1

# 5. Update order status
curl -X PATCH "http://localhost:8080/api/orders/1/status?status=SHIPPED"

# 6. Get order details
curl http://localhost:8080/api/orders/1
```

### Scenario 2: Order Cancellation

```bash
# 1. Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [{"productId": 2, "quantity": 5}],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingState": "NY",
    "shippingZip": "10001",
    "shippingCountry": "USA"
  }'

# 2. Check stock level (should be reduced)
curl http://localhost:8080/api/products/2

# 3. Cancel the order
curl -X PATCH "http://localhost:8080/api/orders/2/status?status=CANCELLED"

# 4. Check stock level again (should be restored)
curl http://localhost:8080/api/products/2
```

### Scenario 3: Error Handling

```bash
# Try to create order with non-existent customer
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 999,
    "items": [{"productId": 1, "quantity": 1}],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingState": "NY",
    "shippingZip": "10001",
    "shippingCountry": "USA"
  }'
# Expected: 400 Bad Request

# Try to create order with non-existent product
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [{"productId": 999, "quantity": 1}],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingState": "NY",
    "shippingZip": "10001",
    "shippingCountry": "USA"
  }'
# Expected: 400 Bad Request

# Try to create order with insufficient stock
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [{"productId": 1, "quantity": 1000}],
    "shippingAddress": "123 Main St",
    "shippingCity": "New York",
    "shippingState": "NY",
    "shippingZip": "10001",
    "shippingCountry": "USA"
  }'
# Expected: 400 Bad Request
```

---

## Using with Postman

1. Import these curl commands into Postman using "Import" → "Raw text"
2. Create environment variables:
   - `base_url`: `http://localhost:8080`
   - `customer_id`: `1`
   - `product_id`: `1`
   - `order_id`: `1`

3. Update requests to use variables:
   - `{{base_url}}/api/customers`
   - `{{base_url}}/api/customers/{{customer_id}}`

---

## Health Check

```bash
# Check if application is running
curl http://localhost:8080/actuator/health 2>/dev/null || echo "Application not running on port 8080"
```

---

## Pretty Print JSON (with jq)

If you have `jq` installed, pipe curl output for better formatting:

```bash
curl http://localhost:8080/api/customers | jq .
curl http://localhost:8080/api/products | jq .
curl http://localhost:8080/api/orders | jq .
```

---

## Tips

- Add `-v` flag to curl for verbose output (see headers, response codes)
- Add `-i` flag to see response headers
- Add `-s` flag for silent mode (no progress bar)
- Use `| jq .` for pretty JSON output
- Save responses to files: `curl http://... > response.json`
