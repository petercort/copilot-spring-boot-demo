# Quick Start Guide

Follow these steps to get the demo application running quickly.

## Prerequisites Check

Make sure you have:
- [ ] Java 17 or higher installed (`java -version`)
- [ ] Maven 3.6+ installed (`mvn -version`)
- [ ] Git installed (`git --version`)
- [ ] GitHub Copilot enabled in your IDE

## 1. Quick Start (5 minutes)

### Clone and Build
```bash
# Clone the repository
git clone <repository-url>
cd copilot-spring-boot-demo

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

### Verify It's Running
```bash
# Test the API
curl http://localhost:8080/api/customers

# You should see 3 customers in JSON format
```

### Access the Database Console
Open your browser to: http://localhost:8080/h2-console

Credentials:
- JDBC URL: `jdbc:h2:mem:ecommercedb`
- Username: `sa`
- Password: (leave empty)

## 2. Try the API (5 minutes)

### Create an Order
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

### View Orders
```bash
curl http://localhost:8080/api/orders
```

### Check Product Stock
```bash
curl http://localhost:8080/api/products/1
```

## 3. Explore the Code (10 minutes)

Open the project in your IDE and explore:

### Key Files to Review
1. **Main Application**: `src/main/java/com/fisglobal/demo/EcommerceApplication.java`
2. **Customer Domain**: `src/main/java/com/fisglobal/demo/customer/`
3. **Order Service**: `src/main/java/com/fisglobal/demo/order/service/OrderService.java`
   - Notice the tight coupling with `CustomerService` and `ProductService`
4. **Configuration**: `src/main/resources/application.properties`

### Architecture Observations
- All domains in one application
- Shared database (H2 in-memory)
- Direct method calls between services
- Single deployment unit

## 4. Next Steps

Choose your path:

### Path A: Follow the Demo Script
Read **DEMO_SCRIPT.md** for a guided walkthrough of splitting this into microservices.

### Path B: Use Copilot Prompts Directly
Jump to **COPILOT_PROMPTS.md** and start extracting services using the provided prompts.

### Path C: Experiment on Your Own
Try these exercises:
1. Add a new customer via the API
2. Create an order and watch the stock decrease
3. Cancel an order and watch the stock restore
4. Look at the logs to understand the flow

## 5. Common Issues

### Port 8080 Already in Use
```bash
# Kill the process using port 8080 (macOS/Linux)
lsof -ti:8080 | xargs kill -9

# Or change the port in application.properties
server.port=8081
```

### Maven Build Fails
```bash
# Clean and rebuild
mvn clean install -U

# Skip tests if needed
mvn clean install -DskipTests
```

### IDE Not Recognizing Project
```bash
# Reimport Maven project in your IDE
# IntelliJ: File → Invalidate Caches and Restart
# VS Code: Reload Window (Cmd+Shift+P → "Reload Window")
```

## 6. Resources

- Full documentation: **[README.md](../README.md)**
- API examples: **API_EXAMPLES.md**
- Demo script: **DEMO_SCRIPT.md**
- Copilot prompts: **COPILOT_PROMPTS.md**

## Need Help?

- Check application logs in the console
- Review the error messages carefully
- Consult the documentation files
- Open an issue if you find a bug

---

**You're ready to start!** 🚀

Recommended: Start with the demo script (DEMO_SCRIPT.md) for a guided experience.
