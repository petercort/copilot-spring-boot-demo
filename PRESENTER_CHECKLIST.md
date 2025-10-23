# Demo Presenter's Checklist

Use this checklist to prepare for your microservices refactoring demonstration.

---

## Pre-Demo Setup (1 day before)

### Environment Setup
- [ ] Java 17+ installed and configured (`java -version`)
- [ ] Maven 3.6+ installed (`mvn -version`)
- [ ] Git installed (`git --version`)
- [ ] IDE installed (VS Code, IntelliJ, or Eclipse)
- [ ] GitHub Copilot subscription active and working
- [ ] Copilot enabled in your IDE

### Project Setup
- [ ] Repository cloned to local machine
- [ ] Project builds successfully (`mvn clean install`)
- [ ] Application runs successfully (`mvn spring-boot:run`)
- [ ] Can access H2 console at http://localhost:8080/h2-console
- [ ] Can access API at http://localhost:8080/api/customers
- [ ] Sample data loads correctly (3 customers, 6 products)

### Documentation Review
- [ ] Read README.md completely
- [ ] Review DEMO_SCRIPT.md (your presentation guide)
- [ ] Familiarize yourself with COPILOT_PROMPTS.md
- [ ] Review ARCHITECTURE.md to understand design decisions
- [ ] Test curl commands from API_EXAMPLES.md

### Tools & Utilities
- [ ] curl or Postman installed for API testing
- [ ] jq installed for pretty JSON formatting (optional)
- [ ] Terminal/command prompt ready
- [ ] Browser ready for H2 console
- [ ] Screen sharing software tested

---

## Pre-Demo Setup (1 hour before)

### Application Verification
- [ ] Start the monolithic application
- [ ] Verify all APIs respond correctly:
  ```bash
  curl http://localhost:8080/api/customers
  curl http://localhost:8080/api/products
  curl http://localhost:8080/api/orders
  ```
- [ ] Test creating an order:
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
- [ ] Stop the application (you'll restart during demo)

### IDE Preparation
- [ ] Open the project in your IDE
- [ ] Ensure Copilot icon is visible and active
- [ ] Test Copilot with a simple comment:
  ```java
  // Create a method that returns hello world
  ```
- [ ] Close unnecessary files/tabs for clean demo start
- [ ] Set font size large enough for audience to read
- [ ] Close distracting notifications

### Workspace Organization
- [ ] Open DEMO_SCRIPT.md in a separate window for reference
- [ ] Have COPILOT_PROMPTS.md ready in another tab
- [ ] Have API_EXAMPLES.md ready for testing
- [ ] Terminal window ready
- [ ] Browser with H2 console tab ready
- [ ] Clear terminal history if desired

### Backup Plan
- [ ] Create a backup branch: `git checkout -b demo-backup`
- [ ] Have the completed microservices code ready (in case of issues)
- [ ] Keep error solutions handy (see DEMO_SCRIPT.md Appendix)
- [ ] Know how to reset: `git reset --hard HEAD`

---

## During Demo - Opening (5 minutes)

- [ ] Welcome audience
- [ ] Introduce yourself
- [ ] State learning objectives:
  - Understanding monolithic challenges
  - Using Copilot for refactoring
  - Extracting microservices
  - Implementing service communication
- [ ] Set expectations (60-minute demo)
- [ ] Mention Q&A at the end

---

## During Demo - Phase 1: Monolith Overview (10 minutes)

- [ ] Show project structure in IDE
- [ ] Explain the three domains (Customer, Inventory, Order)
- [ ] Open `OrderService.java` and highlight dependencies:
  ```java
  private final CustomerService customerService;
  private final ProductService productService;
  ```
- [ ] Point out the tight coupling
- [ ] Start the application: `mvn spring-boot:run`
- [ ] Open H2 console, show shared database
- [ ] Create a test order via curl
- [ ] Show logs demonstrating cross-domain calls
- [ ] Stop the application

---

## During Demo - Phase 2: Architecture Planning (10 minutes)

- [ ] Draw/show the target architecture (3 microservices)
- [ ] Explain domain boundaries
- [ ] Discuss communication patterns (REST vs direct calls)
- [ ] Highlight challenges:
  - Distributed transactions
  - Network latency
  - Data consistency
- [ ] Show architecture diagram from ARCHITECTURE.md

---

## During Demo - Phase 3: Extract Customer Service (15 minutes)

- [ ] Create directory: `mkdir customer-service`
- [ ] Open COPILOT_PROMPTS.md for reference
- [ ] Use Copilot to generate `pom.xml`:
  ```xml
  <!-- Create a Spring Boot 3.2.0 pom.xml for customer-service on port 8081 -->
  ```
- [ ] Use Copilot to generate Application class
- [ ] Copy Customer domain files from monolith
- [ ] Use Copilot to fix package names
- [ ] Create `application.properties` with Copilot
- [ ] Build: `mvn clean install`
- [ ] Run: `mvn spring-boot:run`
- [ ] Test: `curl http://localhost:8081/api/customers`
- [ ] Celebrate success! 🎉

---

## During Demo - Phase 4: Extract Inventory Service (10 minutes)

- [ ] Create directory: `mkdir inventory-service`
- [ ] Use Copilot Chat:
  ```
  Create a Spring Boot microservice called inventory-service on port 8082,
  similar to customer-service. Include Product entity, repository, service,
  and controller based on the inventory package in the monolith.
  ```
- [ ] Review generated files
- [ ] Build and run
- [ ] Test: `curl http://localhost:8082/api/products`

---

## During Demo - Phase 5: Extract Order Service (10 minutes)

- [ ] Create directory: `mkdir order-service`
- [ ] Use Copilot to create basic structure
- [ ] **Key part**: Create REST clients
  - CustomerClient
  - InventoryClient
- [ ] Use Copilot to refactor OrderService
- [ ] Show the change from direct calls to REST calls
- [ ] Build and run all three services
- [ ] Test complete order flow
- [ ] Show logs from all three services

---

## During Demo - Closing (5 minutes)

- [ ] Summarize what was accomplished
- [ ] Review benefits gained:
  - Independent deployment
  - Independent scaling
  - Technology flexibility
  - Team autonomy
- [ ] Review challenges introduced:
  - Network latency
  - Distributed transactions
  - Operational complexity
- [ ] Highlight Copilot's role:
  - Generated 70% of boilerplate
  - Maintained consistency
  - Suggested best practices
- [ ] Mention next steps (API Gateway, Service Discovery, etc.)
- [ ] Share repository access
- [ ] Open for Q&A

---

## Post-Demo

- [ ] Answer questions
- [ ] Share repository URL
- [ ] Share contact information
- [ ] Provide additional resources:
  - Spring Boot documentation
  - Microservices patterns
  - GitHub Copilot documentation
- [ ] Schedule follow-up if requested
- [ ] Collect feedback
- [ ] Push demo code to shared repository (if desired)

---

## Troubleshooting Quick Reference

### Port Already in Use
```bash
# macOS/Linux
lsof -ti:8080 | xargs kill -9

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Copilot Not Working
- Check Copilot icon in IDE (should be active)
- Try restarting IDE
- Check Copilot subscription status
- Try a simple test comment

### Application Won't Start
- Check Java version: `java -version` (need 17+)
- Check Maven: `mvn -version`
- Clean build: `mvn clean install -U`
- Check logs for specific errors

### API Returns 404
- Verify application is running: `curl http://localhost:8080/actuator/health`
- Check port number
- Verify endpoint path in logs
- Check controller mapping

### Database Issues
- Application uses H2 in-memory (no persistence needed)
- Restart application to reset data
- Check H2 console at /h2-console

---

## Demo Success Criteria

You've delivered a successful demo if:
- [ ] Audience understands the monolith's problems
- [ ] At least one microservice was extracted live
- [ ] Copilot generated code successfully
- [ ] REST communication was demonstrated
- [ ] Questions were answered
- [ ] Audience is excited to try it themselves

---

## Speaking Tips

### Do:
- ✅ Speak clearly and pace yourself
- ✅ Explain what you're doing before doing it
- ✅ Show, don't just tell
- ✅ Highlight when Copilot generates code
- ✅ Acknowledge when things go wrong
- ✅ Engage the audience with questions
- ✅ Use the demo script for structure

### Don't:
- ❌ Rush through complex topics
- ❌ Assume everyone knows Spring Boot
- ❌ Skip error handling
- ❌ Just copy-paste code without explanation
- ❌ Ignore questions until the end
- ❌ Go too deep into theory
- ❌ Forget to show the results

---

## Time Management

| Phase | Time | Critical? |
|-------|------|-----------|
| Introduction | 5 min | No - can compress |
| Monolith Overview | 10 min | Yes - sets context |
| Architecture Planning | 10 min | Yes - explains approach |
| Extract Customer Service | 15 min | **Most Critical** |
| Extract Inventory Service | 10 min | Can be shortened |
| Extract Order Service | 10 min | Can be shortened |
| Wrap-up & Q&A | 10 min | Yes - close strong |

**Tip**: If running short on time, fully complete Customer Service extraction and briefly demo the others from pre-built code.

---

## Backup Demonstrations

If live coding has issues, have these ready:

### Plan B: Show Pre-built Microservices
- Have completed microservices in a separate branch
- `git checkout complete-microservices`
- Show the running services
- Walk through the code

### Plan C: Focus on Copilot
- Skip the extraction process
- Focus on individual Copilot prompts
- Show 5-10 impressive generations
- Discuss best practices

---

## Demo Day Checklist

### Morning of Demo
- [ ] Test internet connection
- [ ] Test screen sharing
- [ ] Open all necessary applications
- [ ] Close email/chat applications
- [ ] Silence phone
- [ ] Set "Do Not Disturb" mode
- [ ] Full screen mode ready
- [ ] Water/coffee ready

### 15 Minutes Before
- [ ] Run through quick test
- [ ] Verify Copilot is working
- [ ] Close unnecessary browser tabs
- [ ] Clear terminal
- [ ] Have backup plan ready

### Ready to Start
- [ ] Take a deep breath
- [ ] You've got this! 🚀
- [ ] Remember: The audience wants you to succeed
- [ ] Technical issues happen - stay calm
- [ ] Focus on teaching, not perfection

---

## Contact for Issues

If you encounter issues with this demo:
- Review DEMO_SCRIPT.md Appendix: Troubleshooting
- Check PROJECT_SUMMARY.md for overview
- Review ARCHITECTURE.md for design decisions
- Contact FIS Global Demo Team

---

**Good luck with your demonstration!** 🎉

Remember: You're teaching valuable skills. Even if everything doesn't go perfectly, your audience will learn from the process.
