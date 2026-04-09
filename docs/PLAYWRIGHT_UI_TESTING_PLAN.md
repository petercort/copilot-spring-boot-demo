# Playwright UI End-to-End Testing Plan

> **Project:** copilot-spring-boot-demo microservices  
> **Stack:** Java 25 · Spring Boot 3.5.0 · Spring Cloud 2024.0.1  
> **Last updated:** 2025-01

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [Architecture Recap](#2-architecture-recap)
3. [Minimal Demo UI](#3-minimal-demo-ui)
4. [playwright-cli Setup](#4-playwright-cli-setup)
5. [Playwright Test Project Setup](#5-playwright-test-project-setup)
6. [Test Scenarios & Code Examples](#6-test-scenarios--code-examples)
7. [API Interaction Patterns](#7-api-interaction-patterns)
8. [Test Data Management](#8-test-data-management)
9. [CI/CD Integration](#9-cicd-integration)
10. [playwright-cli Usage Guide (AI-Assisted Testing)](#10-playwright-cli-usage-guide-ai-assisted-testing)
11. [Complete File List](#11-complete-file-list)
12. [Quick Start](#12-quick-start)

---

## 1. Overview & Goals

### What is Playwright?

[Playwright](https://playwright.dev/) is Microsoft's open-source, cross-browser end-to-end testing framework. It controls real browsers (Chromium, Firefox, WebKit) programmatically and provides:

- Auto-waiting for elements (no flaky `sleep()` calls)
- Network interception (`page.route`, `page.request`)
- First-class TypeScript support
- Parallel test execution
- Trace viewer, screenshot, and video capture on failure

### What is playwright-cli?

[`@playwright/cli`](https://github.com/microsoft/playwright-cli) is Microsoft's token-efficient CLI for browser automation, built for coding agents. It exposes browser control as concise shell commands rather than verbose JSON-RPC tool schemas, making it well-suited for high-throughput agents working within limited context windows.

```
 ┌──────────────────────────────────────────────────────┐
 │  GitHub Copilot / Claude Code (AI Agent)             │
 │   ↕ shell commands (playwright-cli <cmd>)            │
 │  playwright-cli (stateful browser session)           │
 │   ↕ Playwright API                                   │
 │  Real Chromium browser ──→ Demo UI (localhost:8090)  │
 │                         ──→ API Gateway (localhost:8080) │
 └──────────────────────────────────────────────────────┘
```

The **two distinct uses** are:

| Use case | Tool | When to use |
|---|---|---|
| Write & run automated E2E regression tests | `playwright.config.ts` + `.spec.ts` files | CI/CD, PR gates, nightly runs |
| Agent-driven interactive testing | `playwright-cli` + installed skills | Generating new tests, debugging, demos |

### Goals for This Project

1. **Validate cross-service integration** — the most valuable tests verify that creating an order correctly calls both `customer-service` (Feign) and `inventory-service` (Feign) and produces the correct response.
2. **Catch regression** in the API Gateway routing layer.
3. **Document the happy path** via human-readable test descriptions.
4. **Enable AI-assisted test authoring** via `playwright-cli` skills.

---

## 2. Architecture Recap

```
Browser / Playwright
        │
        ▼  port 3000
┌───────────────────┐
│   Demo UI         │  (vanilla HTML + JS — to be created)
│   (static server) │
└─────────┬─────────┘
          │  REST  port 8080
          ▼
┌───────────────────┐
│   API Gateway     │  Spring Cloud Gateway
└──┬────────────────┘
   │  lb://customer-service   /api/customers/**
   │  lb://inventory-service  /api/products/**
   │  lb://order-service      /api/orders/**
   │
   ▼ Eureka  port 8761
┌──────────────────────────┐
│  customer-service :8081  │  H2 in-memory — seeds 3 customers on startup
│  inventory-service :8082 │  H2 in-memory — seeds 6 products on startup
│  order-service     :8083 │  H2 in-memory — seeds no orders (they are created)
└──────────────────────────┘
```

**Seed data available out of the box:**

| Service | Data |
|---|---|
| customer-service | John Doe (id=1), Jane Smith (id=2), Bob Johnson (id=3) |
| inventory-service | Laptop ($1299.99, qty=50), Wireless Mouse ($29.99, qty=200), Mechanical Keyboard ($149.99, qty=75), Office Chair ($299.99, qty=30), Standing Desk ($599.99, qty=20), Webcam HD ($79.99, qty=100) |
| order-service | None (created by tests) |

---

## 3. Minimal Demo UI

Because this project has no frontend, we create a **minimal single-page app** whose sole purpose is to be a Playwright test target. It intentionally avoids build complexity.

### Technology Choice

**Vanilla HTML + JavaScript** served by a minimal **Node.js/Express** static server. No bundler, no framework. This keeps the demo UI:
- Free of build failures unrelated to the microservices
- Easy to run with a single `node server.js`
- Fully transparent for AI inspection

### File: `demo-ui/server.js`

```javascript
const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.static(path.join(__dirname, 'public')));

app.listen(PORT, () => {
  console.log(`Demo UI running at http://localhost:${PORT}`);
});
```

### File: `demo-ui/package.json`

```json
{
  "name": "demo-ui",
  "version": "1.0.0",
  "description": "Minimal demo UI for Playwright E2E testing",
  "main": "server.js",
  "scripts": {
    "start": "node server.js",
    "dev": "nodemon server.js"
  },
  "dependencies": {
    "express": "^4.18.2"
  },
  "devDependencies": {
    "nodemon": "^3.0.2"
  }
}
```

### File: `demo-ui/public/index.html`

This is the complete single-page app. All REST calls go to `http://localhost:8080` (the API Gateway).

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Microservices Demo</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: system-ui, sans-serif; background: #f5f5f5; color: #333; }
    header { background: #1a73e8; color: white; padding: 1rem 2rem; }
    header h1 { font-size: 1.4rem; }
    nav { display: flex; gap: 1rem; padding: 1rem 2rem; background: white; border-bottom: 1px solid #ddd; }
    nav button { padding: 0.5rem 1.2rem; border: 1px solid #1a73e8; border-radius: 4px;
                 background: white; color: #1a73e8; cursor: pointer; font-size: 0.95rem; }
    nav button.active, nav button:hover { background: #1a73e8; color: white; }
    main { padding: 2rem; max-width: 900px; margin: 0 auto; }
    section { display: none; }
    section.active { display: block; }
    h2 { margin-bottom: 1rem; font-size: 1.2rem; }
    table { width: 100%; border-collapse: collapse; background: white;
            border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.1); }
    th { background: #f0f4ff; padding: 0.75rem 1rem; text-align: left;
         font-size: 0.85rem; text-transform: uppercase; letter-spacing: .05em; }
    td { padding: 0.75rem 1rem; border-top: 1px solid #eee; }
    tr:hover td { background: #fafafa; }
    .badge { display: inline-block; padding: 0.2rem 0.6rem; border-radius: 12px;
             font-size: 0.8rem; font-weight: 600; }
    .badge.electronics { background: #dbeafe; color: #1d4ed8; }
    .badge.furniture { background: #dcfce7; color: #166534; }
    .badge.pending { background: #fef9c3; color: #854d0e; }
    .badge.confirmed { background: #dcfce7; color: #166534; }
    form { background: white; padding: 1.5rem; border-radius: 8px;
           box-shadow: 0 1px 3px rgba(0,0,0,.1); max-width: 500px; }
    label { display: block; margin-bottom: 0.3rem; font-weight: 500; font-size: 0.9rem; }
    select, input[type=number] { width: 100%; padding: 0.5rem; border: 1px solid #ccc;
                                  border-radius: 4px; margin-bottom: 1rem; font-size: 1rem; }
    .items-list { margin-bottom: 1rem; }
    .item-row { display: flex; gap: 0.5rem; margin-bottom: 0.5rem; }
    .item-row select { flex: 2; margin: 0; }
    .item-row input { flex: 1; margin: 0; }
    .item-row button { padding: 0.5rem 0.75rem; border: none; background: #fee2e2;
                       color: #991b1b; border-radius: 4px; cursor: pointer; }
    .btn { padding: 0.6rem 1.4rem; border: none; border-radius: 4px; cursor: pointer;
           font-size: 1rem; font-weight: 500; }
    .btn-primary { background: #1a73e8; color: white; }
    .btn-secondary { background: #e8eaed; color: #333; }
    .btn-sm { padding: 0.35rem 0.8rem; font-size: 0.85rem; }
    #status-msg { margin-top: 1rem; padding: 0.75rem 1rem; border-radius: 4px; display: none; }
    #status-msg.success { background: #dcfce7; color: #166534; display: block; }
    #status-msg.error   { background: #fee2e2; color: #991b1b; display: block; }
    .loading { color: #666; font-style: italic; padding: 1rem; }
  </style>
</head>
<body>
  <header>
    <h1>🛍️ Microservices Demo — Spring Boot + Playwright</h1>
  </header>

  <nav>
    <button class="active" onclick="showTab('customers')" data-tab="customers">Customers</button>
    <button onclick="showTab('products')" data-tab="products">Products</button>
    <button onclick="showTab('orders')" data-tab="orders">Orders</button>
    <button onclick="showTab('create-order')" data-tab="create-order">Create Order</button>
  </nav>

  <main>
    <!-- Customers Tab -->
    <section id="tab-customers" class="active">
      <h2>Customer List</h2>
      <div id="customers-table"><p class="loading">Loading customers…</p></div>
    </section>

    <!-- Products Tab -->
    <section id="tab-products">
      <h2>Product Catalog</h2>
      <div id="products-table"><p class="loading">Loading products…</p></div>
    </section>

    <!-- Orders Tab -->
    <section id="tab-orders">
      <h2>Orders</h2>
      <button class="btn btn-secondary btn-sm" onclick="loadOrders()" style="margin-bottom:1rem">↻ Refresh</button>
      <div id="orders-table"><p class="loading">Loading orders…</p></div>
    </section>

    <!-- Create Order Tab -->
    <section id="tab-create-order">
      <h2>Create New Order</h2>
      <form id="order-form" onsubmit="submitOrder(event)">
        <label for="customer-select">Customer</label>
        <select id="customer-select" name="customerId" required>
          <option value="">— Select a customer —</option>
        </select>

        <label>Order Items</label>
        <div class="items-list" id="items-list"></div>
        <button type="button" class="btn btn-secondary btn-sm" onclick="addItem()">+ Add Item</button>

        <div style="margin-top:1rem">
          <label for="shipping-address">Shipping Address</label>
          <input type="text" id="shipping-address" name="shippingAddress" placeholder="123 Main St" />
        </div>

        <div style="margin-top:1rem">
          <button type="submit" class="btn btn-primary" id="submit-order-btn">Place Order</button>
        </div>

        <div id="status-msg"></div>
      </form>
    </section>
  </main>

  <script>
    const API = 'http://localhost:8080';
    let productsCache = [];

    // ── Navigation ──────────────────────────────────────────────────
    function showTab(name) {
      document.querySelectorAll('section').forEach(s => s.classList.remove('active'));
      document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));
      document.getElementById('tab-' + name).classList.add('active');
      document.querySelector(`[data-tab="${name}"]`).classList.add('active');
    }

    // ── Customers ───────────────────────────────────────────────────
    async function loadCustomers() {
      const res = await fetch(`${API}/api/customers`);
      const data = await res.json();
      const select = document.getElementById('customer-select');
      document.getElementById('customers-table').innerHTML = `
        <table>
          <thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Phone</th><th>City</th><th>State</th></tr></thead>
          <tbody>${data.map(c => `
            <tr data-customer-id="${c.id}">
              <td>${c.id}</td>
              <td>${c.firstName} ${c.lastName}</td>
              <td>${c.email}</td>
              <td>${c.phone}</td>
              <td>${c.city}</td>
              <td>${c.state}</td>
            </tr>`).join('')}
          </tbody>
        </table>`;
      data.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.id;
        opt.textContent = `${c.firstName} ${c.lastName} (${c.email})`;
        select.appendChild(opt);
      });
    }

    // ── Products ────────────────────────────────────────────────────
    async function loadProducts() {
      const res = await fetch(`${API}/api/products`);
      productsCache = await res.json();
      document.getElementById('products-table').innerHTML = `
        <table>
          <thead><tr><th>ID</th><th>SKU</th><th>Name</th><th>Price</th><th>Stock</th><th>Category</th></tr></thead>
          <tbody>${productsCache.map(p => `
            <tr data-product-id="${p.id}" data-sku="${p.sku}">
              <td>${p.id}</td>
              <td>${p.sku}</td>
              <td>${p.name}</td>
              <td>$${parseFloat(p.price).toFixed(2)}</td>
              <td>${p.stockQuantity}</td>
              <td><span class="badge ${p.category.toLowerCase()}">${p.category}</span></td>
            </tr>`).join('')}
          </tbody>
        </table>`;
      refreshItemSelects();
    }

    // ── Orders ──────────────────────────────────────────────────────
    async function loadOrders() {
      const res = await fetch(`${API}/api/orders`);
      const data = await res.json();
      document.getElementById('orders-table').innerHTML = data.length === 0
        ? '<p class="loading">No orders yet. Create one!</p>'
        : `<table>
            <thead><tr><th>Order #</th><th>Customer ID</th><th>Status</th><th>Total</th><th>Created</th></tr></thead>
            <tbody>${data.map(o => `
              <tr data-order-id="${o.id}" data-order-number="${o.orderNumber}">
                <td>${o.orderNumber}</td>
                <td>${o.customerId}</td>
                <td><span class="badge ${o.status.toLowerCase()}">${o.status}</span></td>
                <td>$${parseFloat(o.totalAmount).toFixed(2)}</td>
                <td>${new Date(o.createdAt).toLocaleString()}</td>
              </tr>`).join('')}
            </tbody>
          </table>`;
    }

    // ── Create Order ─────────────────────────────────────────────────
    function refreshItemSelects() {
      document.querySelectorAll('.product-select').forEach(sel => {
        const current = sel.value;
        sel.innerHTML = '<option value="">— Select product —</option>' +
          productsCache.map(p =>
            `<option value="${p.id}">${p.name} — $${parseFloat(p.price).toFixed(2)} (${p.stockQuantity} in stock)</option>`
          ).join('');
        sel.value = current;
      });
    }

    function addItem() {
      const container = document.getElementById('items-list');
      const row = document.createElement('div');
      row.className = 'item-row';
      row.innerHTML = `
        <select class="product-select" required>
          <option value="">— Select product —</option>
          ${productsCache.map(p =>
            `<option value="${p.id}">${p.name} — $${parseFloat(p.price).toFixed(2)} (${p.stockQuantity} in stock)</option>`
          ).join('')}
        </select>
        <input type="number" class="quantity-input" min="1" value="1" required />
        <button type="button" onclick="this.parentElement.remove()">✕</button>`;
      container.appendChild(row);
    }

    async function submitOrder(event) {
      event.preventDefault();
      const msg = document.getElementById('status-msg');
      msg.className = '';
      msg.textContent = '';

      const customerId = parseInt(document.getElementById('customer-select').value);
      const rows = document.querySelectorAll('.item-row');
      const items = [];
      for (const row of rows) {
        const productId = parseInt(row.querySelector('.product-select').value);
        const quantity  = parseInt(row.querySelector('.quantity-input').value);
        if (!productId || !quantity) { alert('Fill in all item fields'); return; }
        items.push({ productId, quantity });
      }
      if (items.length === 0) { alert('Add at least one item'); return; }

      const body = {
        customerId,
        items,
        shippingAddress: document.getElementById('shipping-address').value || '123 Test St'
      };

      document.getElementById('submit-order-btn').disabled = true;
      try {
        const res = await fetch(`${API}/api/orders`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        if (res.ok) {
          const order = await res.json();
          msg.className = 'success';
          msg.textContent = `✅ Order created! Order number: ${order.orderNumber} | Total: $${parseFloat(order.totalAmount).toFixed(2)}`;
          document.getElementById('order-form').reset();
          document.getElementById('items-list').innerHTML = '';
        } else {
          const err = await res.text();
          msg.className = 'error';
          msg.textContent = `❌ Error ${res.status}: ${err || 'Order creation failed'}`;
        }
      } catch (e) {
        msg.className = 'error';
        msg.textContent = `❌ Network error: ${e.message}`;
      } finally {
        document.getElementById('submit-order-btn').disabled = false;
      }
    }

    // ── Init ─────────────────────────────────────────────────────────
    loadCustomers();
    loadProducts();
    loadOrders();
    addItem(); // start with one empty item row
  </script>
</body>
</html>
```

### Running the Demo UI

```bash
cd demo-ui
npm install
npm start
# → http://localhost:3000
```

> **CORS Note:** The Spring Boot services must allow requests from `http://localhost:3000`. Add a `@CrossOrigin` annotation or a global CORS config bean to each controller, or configure it in the API Gateway's `application.yml`:
>
> ```yaml
> spring:
>   cloud:
>     gateway:
>       globalcors:
>         corsConfigurations:
>           '[/**]':
>             allowedOrigins: "http://localhost:3000"
>             allowedMethods: "*"
>             allowedHeaders: "*"
> ```

---

## 4. playwright-cli Setup

### 4.1 Install playwright-cli

```bash
# Global install (recommended — makes playwright-cli available everywhere)
npm install -g @playwright/cli

# Install Chromium browser (first time only)
playwright-cli install chromium
```

### 4.2 Install Copilot Skills

```bash
playwright-cli install --skills
```

This writes `.claude/skills/playwright-cli/SKILL.md` — a skill file that GitHub Copilot and Claude Code read automatically. Once present, coding agents can use playwright-cli for browser automation without any additional configuration.

### 4.3 Core CLI Commands

```bash
# Open a browser session (headless by default)
playwright-cli open http://localhost:8090

# Open with visible browser
playwright-cli open http://localhost:8090 --headed

# Evaluate JavaScript in the current page
playwright-cli eval "() => document.title"

# Evaluate and return raw value (no status wrapper — useful for assertions)
playwright-cli eval "() => document.querySelectorAll('#customers-body tr').length" --raw

# Take a screenshot
playwright-cli screenshot --filename=results/screenshots/my-screenshot.png

# Navigate in an existing session
playwright-cli navigate http://localhost:8090/other-page

# Close the browser session
playwright-cli close
```

### 4.4 Session Persistence

Within a single shell, `playwright-cli` keeps the browser open between commands. Use named sessions to isolate parallel work:

```bash
playwright-cli open http://localhost:8090 -s=my-session
playwright-cli eval "() => showTab('products')" -s=my-session
playwright-cli screenshot --filename=results/screenshots/products.png -s=my-session
playwright-cli close -s=my-session
```

### 4.5 The Agent Test Script

`scripts/playwright-cli-test.sh` is the canonical agent-driven test runner for this project:

```bash
# Run all 5 suites (18 assertions, 5 screenshots)
./scripts/playwright-cli-test.sh
```

Each suite navigates to a tab, runs `eval --raw` DOM assertions, and takes a screenshot:

```bash
# Example: Customers suite
playwright-cli eval "() => showTab('customers')"
ROWS=$(playwright-cli eval "() => document.querySelectorAll('#customers-body tr').length" --raw | tr -d '"[:space:]')
[ "$ROWS" -gt 0 ] && echo "✅ Customers table has rows" || { echo "❌ Customers table empty"; exit 1; }
playwright-cli screenshot --filename=results/screenshots/cli-02-customers.png
```

---

## 5. Playwright Test Project Setup

The Playwright test suite lives in `e2e/` at the repo root. It is a separate Node.js project from `demo-ui/`.

### 5.1 Directory Structure

```
e2e/
├── package.json
├── playwright.config.ts
├── tests/
│   ├── customers.spec.ts
│   ├── products.spec.ts
│   ├── orders.spec.ts
│   └── error-scenarios.spec.ts
├── fixtures/
│   └── api.fixtures.ts        # Shared API helper types & request builders
├── pages/
│   ├── BasePage.ts
│   ├── CustomersPage.ts
│   ├── ProductsPage.ts
│   └── OrdersPage.ts          # Page Object Model (POM) classes
└── utils/
    └── test-data.ts           # Seed data constants & helpers
```

### 5.2 `e2e/package.json`

```json
{
  "name": "e2e-tests",
  "version": "1.0.0",
  "description": "Playwright E2E tests for copilot-spring-boot-demo",
  "scripts": {
    "test": "playwright test",
    "test:headed": "playwright test --headed",
    "test:ui": "playwright test --ui",
    "test:report": "playwright show-report",
    "test:customers": "playwright test customers",
    "test:products": "playwright test products",
    "test:orders": "playwright test orders"
  },
  "devDependencies": {
    "@playwright/test": "^1.44.0",
    "@types/node": "^20.0.0"
  }
}
```

Install:

```bash
cd e2e
npm install
npx playwright install chromium   # install browser binaries
```

### 5.3 `e2e/playwright.config.ts`

```typescript
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,           // microservices tests share state — run serially
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,                     // single worker to prevent order-of-operations issues
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],

  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
    // All API calls go directly to the gateway — no UI detour needed
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  },

  /* API base URL — used by page.request in tests */
  // Override per-test with: request.post('http://localhost:8080/...')

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    /* Uncomment to also test Firefox and WebKit:
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    */
  ],

  /* Optionally start the demo UI before tests */
  // webServer: {
  //   command: 'cd ../demo-ui && npm start',
  //   url: 'http://localhost:3000',
  //   reuseExistingServer: !process.env.CI,
  //   timeout: 30_000,
  // },
});
```

### 5.4 `e2e/utils/test-data.ts`

Centralize seed data so tests don't hard-code magic IDs.

```typescript
export const API_BASE = 'http://localhost:8080';
export const UI_BASE  = 'http://localhost:3000';

/** Customers seeded by DataInitializer on every startup */
export const SEEDED_CUSTOMERS = [
  { firstName: 'John',  lastName: 'Doe',     email: 'john.doe@example.com',     city: 'New York',    state: 'NY' },
  { firstName: 'Jane',  lastName: 'Smith',   email: 'jane.smith@example.com',   city: 'Los Angeles', state: 'CA' },
  { firstName: 'Bob',   lastName: 'Johnson', email: 'bob.johnson@example.com',  city: 'Chicago',     state: 'IL' },
] as const;

/** Products seeded by DataInitializer on every startup */
export const SEEDED_PRODUCTS = [
  { name: 'Laptop Computer',     sku: 'LAPTOP-001',   price: 1299.99, stockQuantity: 50,  category: 'Electronics' },
  { name: 'Wireless Mouse',      sku: 'MOUSE-001',    price: 29.99,   stockQuantity: 200, category: 'Electronics' },
  { name: 'Mechanical Keyboard', sku: 'KEYBOARD-001', price: 149.99,  stockQuantity: 75,  category: 'Electronics' },
  { name: 'Office Chair',        sku: 'CHAIR-001',    price: 299.99,  stockQuantity: 30,  category: 'Furniture'   },
  { name: 'Standing Desk',       sku: 'DESK-001',     price: 599.99,  stockQuantity: 20,  category: 'Furniture'   },
  { name: 'Webcam HD',           sku: 'WEBCAM-001',   price: 79.99,   stockQuantity: 100, category: 'Electronics' },
] as const;

export type SeededCustomer = typeof SEEDED_CUSTOMERS[number];
export type SeededProduct  = typeof SEEDED_PRODUCTS[number];
```

### 5.5 `e2e/pages/BasePage.ts`

```typescript
import { Page } from '@playwright/test';
import { UI_BASE } from '../utils/test-data';

export class BasePage {
  constructor(protected page: Page) {}

  async navigate(): Promise<void> {
    await this.page.goto(UI_BASE);
  }

  async clickTab(tabName: 'customers' | 'products' | 'orders' | 'create-order'): Promise<void> {
    await this.page.click(`[data-tab="${tabName}"]`);
    await this.page.waitForSelector(`#tab-${tabName}.active`);
  }
}
```

### 5.6 `e2e/pages/CustomersPage.ts`

```typescript
import { Page, Locator } from '@playwright/test';
import { BasePage } from './BasePage';

export class CustomersPage extends BasePage {
  readonly table: Locator;

  constructor(page: Page) {
    super(page);
    this.table = page.locator('#customers-table table');
  }

  async goto(): Promise<void> {
    await this.navigate();
    await this.clickTab('customers');
    await this.table.waitFor();
  }

  getRowByEmail(email: string): Locator {
    return this.page.locator(`#customers-table tr td:text("${email}")`).locator('..');
  }

  async getCustomerCount(): Promise<number> {
    return this.table.locator('tbody tr').count();
  }
}
```

### 5.7 `e2e/pages/ProductsPage.ts`

```typescript
import { Page, Locator } from '@playwright/test';
import { BasePage } from './BasePage';

export class ProductsPage extends BasePage {
  readonly table: Locator;

  constructor(page: Page) {
    super(page);
    this.table = page.locator('#products-table table');
  }

  async goto(): Promise<void> {
    await this.navigate();
    await this.clickTab('products');
    await this.table.waitFor();
  }

  getRowBySku(sku: string): Locator {
    return this.page.locator(`#products-table tr[data-sku="${sku}"]`);
  }

  async getProductCount(): Promise<number> {
    return this.table.locator('tbody tr').count();
  }
}
```

### 5.8 `e2e/pages/OrdersPage.ts`

```typescript
import { Page, Locator } from '@playwright/test';
import { BasePage } from './BasePage';

export class OrdersPage extends BasePage {
  readonly customerSelect: Locator;
  readonly itemsContainer: Locator;
  readonly shippingAddress: Locator;
  readonly submitBtn: Locator;
  readonly statusMsg: Locator;
  readonly ordersTable: Locator;

  constructor(page: Page) {
    super(page);
    this.customerSelect  = page.locator('#customer-select');
    this.itemsContainer  = page.locator('#items-list');
    this.shippingAddress = page.locator('#shipping-address');
    this.submitBtn       = page.locator('#submit-order-btn');
    this.statusMsg       = page.locator('#status-msg');
    this.ordersTable     = page.locator('#orders-table table');
  }

  async gotoCreateOrder(): Promise<void> {
    await this.navigate();
    await this.clickTab('create-order');
    await this.customerSelect.waitFor();
  }

  async gotoOrders(): Promise<void> {
    await this.navigate();
    await this.clickTab('orders');
  }

  async selectCustomerByName(fullName: string): Promise<void> {
    await this.customerSelect.selectOption({ label: new RegExp(fullName) });
  }

  async setItemProductAndQuantity(index: number, productName: string, quantity: number): Promise<void> {
    const rows = this.itemsContainer.locator('.item-row');
    const row  = rows.nth(index);
    await row.locator('.product-select').selectOption({ label: new RegExp(productName) });
    await row.locator('.quantity-input').fill(String(quantity));
  }

  async addItem(): Promise<void> {
    await this.page.click('button:has-text("+ Add Item")');
  }

  async submitOrder(): Promise<void> {
    await this.submitBtn.click();
    await this.statusMsg.waitFor({ state: 'visible' });
  }

  async getStatusMessage(): Promise<string> {
    return this.statusMsg.innerText();
  }

  async isOrderSuccessful(): Promise<boolean> {
    const cls = await this.statusMsg.getAttribute('class');
    return cls?.includes('success') ?? false;
  }
}
```

---

## 6. Test Scenarios & Code Examples

### 6.1 `e2e/tests/customers.spec.ts` — Complete Example

```typescript
import { test, expect } from '@playwright/test';
import { CustomersPage } from '../pages/CustomersPage';
import { SEEDED_CUSTOMERS, API_BASE } from '../utils/test-data';

test.describe('Customer Service', () => {

  test.describe('API — GET /api/customers', () => {

    test('returns all seeded customers', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/customers`);

      expect(response.status()).toBe(200);
      const customers = await response.json();
      expect(Array.isArray(customers)).toBe(true);
      expect(customers.length).toBeGreaterThanOrEqual(3);

      const emails = customers.map((c: any) => c.email);
      for (const seeded of SEEDED_CUSTOMERS) {
        expect(emails).toContain(seeded.email);
      }
    });

    test('returns a customer by ID', async ({ request }) => {
      // First get all, then fetch the first one by ID
      const list     = await request.get(`${API_BASE}/api/customers`);
      const allCustomers = await list.json();
      const firstId  = allCustomers[0].id;

      const response = await request.get(`${API_BASE}/api/customers/${firstId}`);
      expect(response.status()).toBe(200);

      const customer = await response.json();
      expect(customer.id).toBe(firstId);
      expect(customer.email).toBeTruthy();
    });

    test('returns 404 for non-existent customer', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/customers/999999`);
      expect(response.status()).toBe(404);
    });

    test('returns a customer by email', async ({ request }) => {
      const email    = SEEDED_CUSTOMERS[0].email;
      const response = await request.get(`${API_BASE}/api/customers/email/${email}`);

      expect(response.status()).toBe(200);
      const customer = await response.json();
      expect(customer.email).toBe(email);
      expect(customer.firstName).toBe(SEEDED_CUSTOMERS[0].firstName);
    });
  });

  test.describe('API — POST /api/customers', () => {

    test('creates a new customer', async ({ request }) => {
      const newCustomer = {
        firstName: 'Alice',
        lastName:  'Playwright',
        email:     `alice.playwright.${Date.now()}@example.com`,
        phone:     '555-0000',
        address:   '1 E2E Lane',
        city:      'Testville',
        state:     'TX',
        zipCode:   '73301',
        country:   'USA',
      };

      const response = await request.post(`${API_BASE}/api/customers`, { data: newCustomer });
      expect(response.status()).toBe(201);

      const created = await response.json();
      expect(created.id).toBeTruthy();
      expect(created.email).toBe(newCustomer.email);
    });

    test('rejects a customer missing required fields', async ({ request }) => {
      const response = await request.post(`${API_BASE}/api/customers`, {
        data: { firstName: 'No Email' } // missing required fields
      });
      expect(response.status()).toBeGreaterThanOrEqual(400);
    });
  });

  test.describe('UI — Customers tab', () => {

    test('displays the customer table with seeded data', async ({ page }) => {
      const customersPage = new CustomersPage(page);
      await customersPage.goto();

      const count = await customersPage.getCustomerCount();
      expect(count).toBeGreaterThanOrEqual(3);
    });

    test('shows John Doe in the customer list', async ({ page }) => {
      const customersPage = new CustomersPage(page);
      await customersPage.goto();

      const row = customersPage.getRowByEmail('john.doe@example.com');
      await expect(row).toBeVisible();
      await expect(row).toContainText('John');
      await expect(row).toContainText('Doe');
      await expect(row).toContainText('New York');
    });

    test('shows all three seeded customers', async ({ page }) => {
      const customersPage = new CustomersPage(page);
      await customersPage.goto();

      for (const customer of SEEDED_CUSTOMERS) {
        const row = customersPage.getRowByEmail(customer.email);
        await expect(row).toBeVisible();
      }
    });
  });
});
```

### 6.2 `e2e/tests/products.spec.ts`

```typescript
import { test, expect } from '@playwright/test';
import { ProductsPage } from '../pages/ProductsPage';
import { SEEDED_PRODUCTS, API_BASE } from '../utils/test-data';

test.describe('Inventory Service', () => {

  test.describe('API — GET /api/products', () => {

    test('returns all seeded products', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/products`);

      expect(response.status()).toBe(200);
      const products = await response.json();
      expect(products.length).toBeGreaterThanOrEqual(6);

      const skus = products.map((p: any) => p.sku);
      for (const seeded of SEEDED_PRODUCTS) {
        expect(skus).toContain(seeded.sku);
      }
    });

    test('returns product by SKU', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/products/sku/LAPTOP-001`);

      expect(response.status()).toBe(200);
      const product = await response.json();
      expect(product.name).toBe('Laptop Computer');
      expect(product.stockQuantity).toBeGreaterThan(0);
    });

    test('returns products by category', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/products/category/Electronics`);

      expect(response.status()).toBe(200);
      const products = await response.json();
      expect(products.length).toBeGreaterThanOrEqual(4);
      products.forEach((p: any) => expect(p.category).toBe('Electronics'));
    });

    test('returns active-only products when activeOnly=true', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/products?activeOnly=true`);
      expect(response.status()).toBe(200);
    });
  });

  test.describe('UI — Products tab', () => {

    test('displays the product catalog with all seeded products', async ({ page }) => {
      const productsPage = new ProductsPage(page);
      await productsPage.goto();

      const count = await productsPage.getProductCount();
      expect(count).toBeGreaterThanOrEqual(6);
    });

    test('shows SKU and price for each product', async ({ page }) => {
      const productsPage = new ProductsPage(page);
      await productsPage.goto();

      const laptopRow = productsPage.getRowBySku('LAPTOP-001');
      await expect(laptopRow).toBeVisible();
      await expect(laptopRow).toContainText('Laptop Computer');
      await expect(laptopRow).toContainText('1299.99');
    });

    test('shows category badges', async ({ page }) => {
      const productsPage = new ProductsPage(page);
      await productsPage.goto();

      await expect(page.locator('.badge.electronics').first()).toBeVisible();
      await expect(page.locator('.badge.furniture').first()).toBeVisible();
    });
  });
});
```

### 6.3 `e2e/tests/orders.spec.ts` — Cross-Service Integration

```typescript
import { test, expect } from '@playwright/test';
import { OrdersPage } from '../pages/OrdersPage';
import { API_BASE } from '../utils/test-data';

/**
 * Order tests exercise the most complex integration path:
 *   Browser → API Gateway → order-service → customer-service (Feign)
 *                                         → inventory-service (Feign)
 */
test.describe('Order Service', () => {

  test.describe('API — GET /api/orders', () => {

    test('returns an array (may be empty on fresh start)', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/orders`);
      expect(response.status()).toBe(200);
      const orders = await response.json();
      expect(Array.isArray(orders)).toBe(true);
    });
  });

  test.describe('API — POST /api/orders (end-to-end cross-service)', () => {

    let customerId: number;
    let productId: number;

    test.beforeAll(async ({ request }) => {
      // Resolve real IDs from seed data — avoids hard-coding H2 auto-increment values
      const customersRes = await request.get(`${API_BASE}/api/customers`);
      const customers    = await customersRes.json();
      customerId = customers.find((c: any) => c.email === 'john.doe@example.com')?.id;
      expect(customerId).toBeTruthy();

      const productsRes = await request.get(`${API_BASE}/api/products`);
      const products    = await productsRes.json();
      productId = products.find((p: any) => p.sku === 'MOUSE-001')?.id;
      expect(productId).toBeTruthy();
    });

    test('creates an order for a valid customer and product', async ({ request }) => {
      const body = {
        customerId,
        items: [{ productId, quantity: 2 }],
        shippingAddress: '999 Playwright Ave',
        shippingCity:    'Testville',
        shippingState:   'TX',
        shippingZip:     '73301',
        shippingCountry: 'USA',
      };

      const response = await request.post(`${API_BASE}/api/orders`, { data: body });
      expect(response.status()).toBe(201);

      const order = await response.json();
      expect(order.id).toBeTruthy();
      expect(order.orderNumber).toMatch(/^ORD-/);
      expect(order.customerId).toBe(customerId);
      expect(order.status).toBe('PENDING');

      // 2 × $29.99 = $59.98
      expect(parseFloat(order.totalAmount)).toBeCloseTo(59.98, 2);
      expect(order.items).toHaveLength(1);
      expect(order.items[0].quantity).toBe(2);
    });

    test('creates an order with multiple items', async ({ request }) => {
      const productsRes  = await request.get(`${API_BASE}/api/products`);
      const products     = await productsRes.json();
      const laptopId     = products.find((p: any) => p.sku === 'LAPTOP-001')?.id;
      const keyboardId   = products.find((p: any) => p.sku === 'KEYBOARD-001')?.id;

      const body = {
        customerId,
        items: [
          { productId: laptopId,   quantity: 1 },
          { productId: keyboardId, quantity: 2 },
        ],
        shippingAddress: '42 Multi-Item Blvd',
      };

      const response = await request.post(`${API_BASE}/api/orders`, { data: body });
      expect(response.status()).toBe(201);

      const order = await response.json();
      expect(order.items).toHaveLength(2);
      // 1 × 1299.99 + 2 × 149.99 = $1599.97
      expect(parseFloat(order.totalAmount)).toBeCloseTo(1599.97, 2);
    });

    test('newly created order appears in GET /api/orders', async ({ request }) => {
      const body = {
        customerId,
        items: [{ productId, quantity: 1 }],
        shippingAddress: '1 Check St',
      };

      const createRes = await request.post(`${API_BASE}/api/orders`, { data: body });
      const created   = await createRes.json();

      const listRes = await request.get(`${API_BASE}/api/orders`);
      const orders  = await listRes.json();
      const found   = orders.find((o: any) => o.orderNumber === created.orderNumber);
      expect(found).toBeTruthy();
    });

    test('retrieves order by order number', async ({ request }) => {
      const body = { customerId, items: [{ productId, quantity: 1 }] };
      const createRes  = await request.post(`${API_BASE}/api/orders`, { data: body });
      const created    = await createRes.json();

      const response   = await request.get(
        `${API_BASE}/api/orders/order-number/${created.orderNumber}`
      );
      expect(response.status()).toBe(200);
      const fetched = await response.json();
      expect(fetched.orderNumber).toBe(created.orderNumber);
    });

    test('retrieves all orders for a customer', async ({ request }) => {
      const response = await request.get(`${API_BASE}/api/orders/customer/${customerId}`);
      expect(response.status()).toBe(200);
      const orders = await response.json();
      orders.forEach((o: any) => expect(o.customerId).toBe(customerId));
    });
  });

  test.describe('UI — Create Order form', () => {

    test('shows customer dropdown populated from customer-service', async ({ page }) => {
      const ordersPage = new OrdersPage(page);
      await ordersPage.gotoCreateOrder();

      // Customer dropdown should have seeded customers
      const options = page.locator('#customer-select option');
      await expect(options).toHaveCountGreaterThan(1);
      await expect(options.filter({ hasText: 'John Doe' })).toBeVisible();
    });

    test('shows product dropdown populated from inventory-service', async ({ page }) => {
      const ordersPage = new OrdersPage(page);
      await ordersPage.gotoCreateOrder();

      // The first item row is auto-added — product select should list products
      const firstProductSelect = page.locator('.item-row .product-select').first();
      await expect(firstProductSelect.locator('option')).toHaveCountGreaterThan(1);
    });

    test('successfully creates an order via UI form', async ({ page }) => {
      const ordersPage = new OrdersPage(page);
      await ordersPage.gotoCreateOrder();

      // Select John Doe
      await ordersPage.selectCustomerByName('John Doe');

      // Set first item to Wireless Mouse, qty 1
      await ordersPage.setItemProductAndQuantity(0, 'Wireless Mouse', 1);

      // Fill shipping
      await ordersPage.shippingAddress.fill('42 Playwright Road');

      // Submit and wait for status message
      await ordersPage.submitOrder();

      const isSuccess = await ordersPage.isOrderSuccessful();
      expect(isSuccess).toBe(true);

      const message = await ordersPage.getStatusMessage();
      expect(message).toContain('ORD-');
      expect(message).toContain('29.99');
    });

    test('order appears in Orders tab after creation', async ({ page }) => {
      const ordersPage = new OrdersPage(page);
      await ordersPage.gotoCreateOrder();

      await ordersPage.selectCustomerByName('Jane Smith');
      await ordersPage.setItemProductAndQuantity(0, 'Webcam HD', 2);
      await ordersPage.submitOrder();

      const message = await ordersPage.getStatusMessage();
      const match   = message.match(/ORD-\d+/);
      expect(match).not.toBeNull();
      const orderNumber = match![0];

      // Navigate to Orders tab and verify
      await ordersPage.gotoOrders();
      await expect(page.locator(`[data-order-number="${orderNumber}"]`)).toBeVisible();
    });
  });
});
```

### 6.4 `e2e/tests/error-scenarios.spec.ts`

```typescript
import { test, expect } from '@playwright/test';
import { API_BASE } from '../utils/test-data';

test.describe('Error Scenarios', () => {

  test('POST /api/orders — rejects non-existent customer', async ({ request }) => {
    const body = {
      customerId: 999999,
      items: [{ productId: 1, quantity: 1 }],
    };

    const response = await request.post(`${API_BASE}/api/orders`, { data: body });
    // order-service calls customer-service via Feign; customer not found → 400
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('POST /api/orders — rejects non-existent product', async ({ request }) => {
    const customersRes = await request.get(`${API_BASE}/api/customers`);
    const customers    = await customersRes.json();
    const customerId   = customers[0].id;

    const body = {
      customerId,
      items: [{ productId: 999999, quantity: 1 }],
    };

    const response = await request.post(`${API_BASE}/api/orders`, { data: body });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('POST /api/orders — rejects order with empty items array', async ({ request }) => {
    const customersRes = await request.get(`${API_BASE}/api/customers`);
    const customers    = await customersRes.json();

    const body = {
      customerId: customers[0].id,
      items: [],
    };

    const response = await request.post(`${API_BASE}/api/orders`, { data: body });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('POST /api/orders — rejects order with zero quantity', async ({ request }) => {
    const [customersRes, productsRes] = await Promise.all([
      request.get(`${API_BASE}/api/customers`),
      request.get(`${API_BASE}/api/products`),
    ]);
    const customers = await customersRes.json();
    const products  = await productsRes.json();

    const body = {
      customerId: customers[0].id,
      items: [{ productId: products[0].id, quantity: 0 }],
    };

    const response = await request.post(`${API_BASE}/api/orders`, { data: body });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('GET /api/customers/999999 — returns 404', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/customers/999999`);
    expect(response.status()).toBe(404);
  });

  test('GET /api/products/sku/INVALID-SKU — returns 404', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/products/sku/INVALID-SKU-9999`);
    expect(response.status()).toBe(404);
  });

  test('POST /api/customers — returns 400 for missing email', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/customers`, {
      data: { firstName: 'Test', lastName: 'User' }, // no email
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });
});
```

---

## 7. API Interaction Patterns

### 7.1 Pure API Tests with `request` Fixture

Playwright's built-in `request` fixture makes HTTP calls without opening a browser. Use this for CRUD contract tests.

```typescript
test('CRUD lifecycle for a customer', async ({ request }) => {
  // Create
  const created = await (await request.post(`${API_BASE}/api/customers`, {
    data: { firstName: 'Lifecycle', lastName: 'Test', email: `lc.${Date.now()}@test.com`,
            phone: '555-0000', address: '1 CRUD St', city: 'Testburg',
            state: 'TX', zipCode: '73301', country: 'USA' }
  })).json();

  // Read
  const fetched = await (await request.get(`${API_BASE}/api/customers/${created.id}`)).json();
  expect(fetched.firstName).toBe('Lifecycle');

  // Update
  const updated = await (await request.put(`${API_BASE}/api/customers/${created.id}`, {
    data: { ...fetched, city: 'Updated City' }
  })).json();
  expect(updated.city).toBe('Updated City');

  // Delete
  const delRes = await request.delete(`${API_BASE}/api/customers/${created.id}`);
  expect(delRes.status()).toBe(204);

  // Confirm gone
  const gone = await request.get(`${API_BASE}/api/customers/${created.id}`);
  expect(gone.status()).toBe(404);
});
```

### 7.2 Mixing API Setup with UI Verification

Set up state via API (faster, no flakiness) and verify it in the UI:

```typescript
test('order created via API appears in the UI', async ({ page, request }) => {
  // 1. Resolve IDs from API
  const customers = await (await request.get(`${API_BASE}/api/customers`)).json();
  const products  = await (await request.get(`${API_BASE}/api/products`)).json();

  // 2. Create order via API (fast, reliable)
  const body = {
    customerId: customers[0].id,
    items: [{ productId: products[0].id, quantity: 1 }],
  };
  const order = await (await request.post(`${API_BASE}/api/orders`, { data: body })).json();

  // 3. Verify in the UI
  await page.goto('http://localhost:3000');
  await page.click('[data-tab="orders"]');
  await expect(page.locator(`[data-order-number="${order.orderNumber}"]`)).toBeVisible();
});
```

### 7.3 Intercepting API Calls in the Browser

Intercept requests to simulate failures:

```typescript
test('UI shows error when API Gateway is down', async ({ page }) => {
  // Intercept all orders API calls and return 503
  await page.route('**/api/orders', route =>
    route.fulfill({ status: 503, body: 'Service Unavailable' })
  );

  await page.goto('http://localhost:3000');
  await page.click('[data-tab="create-order"]');
  await page.locator('#customer-select').selectOption({ index: 1 });
  await page.locator('.product-select').first().selectOption({ index: 1 });
  await page.locator('#submit-order-btn').click();

  // App should show an error message
  const msg = page.locator('#status-msg');
  await expect(msg).toBeVisible();
  await expect(msg).toContainText(/error/i);
});
```

---

## 8. Test Data Management

### The H2 Reset Problem

Every time you restart the microservices, H2 creates fresh in-memory databases and `DataInitializer` re-seeds them with fixed names — but **new auto-increment IDs**. IDs are non-deterministic across restarts.

### Strategy

| Pattern | How |
|---|---|
| **Never hard-code IDs** | Always query `/api/customers` or `/api/products` first and look up by stable fields (`email`, `sku`) |
| **Use `test.beforeAll` for ID resolution** | Fetch and cache IDs before the test group runs — one HTTP request per suite, not per test |
| **Stable identifiers** | `SEEDED_CUSTOMERS[n].email` and `SEEDED_PRODUCTS[n].sku` are the ground truth |
| **Test isolation** | Tests that create resources use unique emails (`email.${Date.now()}@...`) to avoid collisions when tests run repeatedly without restarting services |
| **Order tests depend on prior service health** | Always run customer/product tests first to confirm the dependent services are up |

### Seed Data Reference

The `test-data.ts` file (Section 5.4) centralizes all seed constants. Always import from there — never hard-code strings in test files.

### Resetting State (Workaround for Repeated Test Runs)

Since H2 is in-memory, the cleanest reset is to restart the services. For CI, this is guaranteed because services start fresh. For local development, prefer:

```bash
# Restart all services (runs the run.sh script)
./run.sh
```

Or add a test helper that deletes test-created resources after each test:

```typescript
test.afterEach(async ({ request }, testInfo) => {
  // Only clean up if the test created a customer (attach cleanup steps here)
  if (testInfo.attachments.some(a => a.name === 'created-customer-id')) {
    const id = testInfo.attachments.find(a => a.name === 'created-customer-id')?.body?.toString();
    if (id) await request.delete(`${API_BASE}/api/customers/${id}`);
  }
});
```

---

## 9. CI/CD Integration

### `.github/workflows/e2e.yml`

```yaml
name: E2E Tests (Playwright)

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]
  workflow_dispatch:

jobs:
  e2e:
    name: Playwright E2E Tests
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      # ── Checkout & Java Setup ───────────────────────────────────────
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      # ── Build all microservices ─────────────────────────────────────
      - name: Build microservices (skip unit tests for E2E speed)
        run: mvn -B package -DskipTests --no-transfer-progress

      # ── Start microservices in background ───────────────────────────
      - name: Start Eureka Server
        run: |
          java -jar eureka-server/target/*.jar &
          echo "EUREKA_PID=$!" >> $GITHUB_ENV

      - name: Wait for Eureka to be ready
        run: |
          for i in {1..30}; do
            curl -sf http://localhost:8761/actuator/health && break
            echo "Waiting for Eureka... ($i/30)"
            sleep 3
          done

      - name: Start Customer Service
        run: java -jar customer-service/target/*.jar &

      - name: Start Inventory Service
        run: java -jar inventory-service/target/*.jar &

      - name: Start Order Service
        run: java -jar order-service/target/*.jar &

      - name: Start API Gateway
        run: |
          java -jar api-gateway/target/*.jar &
          echo "GATEWAY_PID=$!" >> $GITHUB_ENV

      - name: Wait for all services to register with Eureka
        run: |
          for i in {1..40}; do
            STATUS=$(curl -sf http://localhost:8080/api/customers 2>&1 && echo OK || echo FAIL)
            [ "$STATUS" = "OK" ] && break
            echo "Waiting for API Gateway... ($i/40)"
            sleep 4
          done

      # ── Start Demo UI ───────────────────────────────────────────────
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: demo-ui/package-lock.json

      - name: Install Demo UI dependencies
        run: cd demo-ui && npm ci

      - name: Start Demo UI
        run: cd demo-ui && npm start &

      - name: Wait for Demo UI
        run: |
          for i in {1..15}; do
            curl -sf http://localhost:3000 && break
            echo "Waiting for Demo UI... ($i/15)"
            sleep 2
          done

      # ── Playwright Tests ────────────────────────────────────────────
      - name: Install Playwright E2E dependencies
        run: cd e2e && npm ci

      - name: Install Playwright browsers
        run: cd e2e && npx playwright install --with-deps chromium

      - name: Run Playwright E2E tests
        run: cd e2e && npx playwright test
        env:
          CI: true

      # ── Artifacts ───────────────────────────────────────────────────
      - name: Upload Playwright report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: playwright-report
          path: e2e/playwright-report/
          retention-days: 14

      - name: Upload test traces (on failure)
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: playwright-traces
          path: e2e/test-results/
          retention-days: 7
```

### Notes

- Services start in parallel where Eureka allows it. The gateway is started last because it needs services registered.
- The `wait` loops use `curl -sf` which returns non-zero on failure — the `&& break` pattern exits the loop on success.
- `mvn -B package -DskipTests` is intentional: unit tests run in a separate CI job (`maven.yml`), and E2E tests run against real built JARs.
- Playwright `retries: 1` in CI mode means flaky tests get one retry before failing the build.

---

## 10. playwright-cli Usage Guide (AI-Assisted Testing)

### 10.1 What playwright-cli Enables

When `playwright-cli install --skills` has been run (creates `.claude/skills/playwright-cli/SKILL.md`), GitHub Copilot and Claude Code gain direct browser control via shell commands. You can ask the agent to:

- **Navigate** to pages and describe what it sees
- **Assert** DOM values using `eval --raw` for zero-overhead comparisons
- **Generate new test files** based on live browser inspection
- **Debug failing tests** by replaying actions in a real browser
- **Take screenshots** for visual verification

### 10.2 Prerequisites

1. All microservices running: `docker compose up -d` (or `./run.sh`)
2. Demo UI running: included in Docker Compose (port 8090)
3. `@playwright/cli` installed globally: `npm install -g @playwright/cli`
4. Skills installed: `playwright-cli install --skills`

### 10.3 Example Agent Prompts

**Exploring the UI:**

```
Using playwright-cli, open http://localhost:8090 and describe the page
structure. Then navigate to the Products tab and list all products shown.
```

**Generating a new test:**

```
Using playwright-cli:
1. Open http://localhost:8090
2. Navigate to Create Order (showTab('create-order'))
3. Select the first customer in #customer-select
4. Select the first product in #product-select and set #quantity to 1
5. Click #place-order-btn
6. Take a screenshot of the result

Based on what you saw, write a new Playwright spec in
e2e/tests/orders.spec.ts that automates this exact flow.
```

**Debugging a failing test:**

```
The test "successfully creates an order via UI form" in
e2e/tests/orders.spec.ts is failing. Using playwright-cli, open
http://localhost:8090, navigate to Create Order, attempt to place an
order, and report the DOM state and any visible error messages.
```

**Visual verification:**

```
Using playwright-cli, open http://localhost:8090 and take a screenshot
of each tab (Customers, Products, Orders, Create Order). Save them to
results/screenshots/ and describe any visual issues.
```

### 10.4 Workflow: playwright-cli → Test File

The recommended AI-assisted workflow is:

```
1. Use playwright-cli to explore/interact with the running app
        ↓
2. Ask Copilot to generate a test based on the interaction
        ↓
3. Save the generated test to e2e/tests/
        ↓
4. Run: cd e2e && npx playwright test --headed
        ↓
5. Use Playwright UI mode for debugging: npx playwright test --ui
        ↓
6. Commit the passing test
```

### 10.5 Playwright UI Mode (Interactive Debugging)

```bash
cd e2e
npx playwright test --ui
```

This opens a browser-based test runner where you can:
- See all tests listed
- Run individual tests
- Step through actions one by one
- Inspect the DOM at each step
- See screenshots, network calls, and console logs per step

---

## 11. Complete File List

The following files need to be created to implement this plan:

```
copilot-spring-boot-demo/
│
├── .claude/
│   └── skills/
│       └── playwright-cli/
│           └── SKILL.md                      ← playwright-cli Copilot skills (auto-installed)
│
├── demo-ui/
│   ├── package.json                      ← Node.js project for static server
│   ├── server.js                         ← Express static file server (port 3000)
│   └── public/
│       └── index.html                    ← Single-page app (Customers/Products/Orders/Create Order)
│
├── e2e/
│   ├── package.json                      ← Playwright test project dependencies
│   ├── playwright.config.ts              ← Playwright config (baseURL, browser targets, retries)
│   ├── tests/
│   │   ├── customers.spec.ts             ← Customer API + UI tests
│   │   ├── products.spec.ts              ← Product API + UI tests
│   │   ├── orders.spec.ts                ← Order creation E2E (main cross-service test)
│   │   └── error-scenarios.spec.ts       ← Negative/error path tests
│   ├── pages/
│   │   ├── BasePage.ts                   ← Shared navigation helpers (Page Object base)
│   │   ├── CustomersPage.ts              ← Customers tab Page Object
│   │   ├── ProductsPage.ts               ← Products tab Page Object
│   │   └── OrdersPage.ts                 ← Orders/Create Order Page Object
│   └── utils/
│       └── test-data.ts                  ← Seed data constants (emails, SKUs, etc.)
│
└── .github/
    └── workflows/
        └── e2e.yml                       ← GitHub Actions workflow for E2E tests
```

### Files Requiring Modification

| File | Change |
|---|---|
| `api-gateway/src/main/resources/application.yml` | Add `globalcors` config to allow requests from `http://localhost:3000` |
| Any service's `@CrossOrigin` annotations | Optional per-controller CORS, or use gateway config above |

---

## 12. Quick Start

### Prerequisites

- All 5 microservices built and runnable (`./build.sh`)
- Node.js 18+ installed

### Step 1: Start the Microservices

```bash
# From repo root
./run.sh
```

Wait ~30 seconds for all services to register with Eureka.

### Step 2: Start the Demo UI

```bash
cd demo-ui
npm install
npm start
# → http://localhost:3000
```

### Step 3: Install E2E Test Dependencies

```bash
cd e2e
npm install
npx playwright install chromium
```

### Step 4: Run the Tests

```bash
# Run all E2E tests (headless)
cd e2e && npx playwright test

# Run with visible browser
cd e2e && npx playwright test --headed

# Run a specific test file
cd e2e && npx playwright test orders

# Open interactive UI mode
cd e2e && npx playwright test --ui

# View the HTML report
cd e2e && npx playwright show-report
```

### Step 5: Use playwright-cli with Copilot

```bash
# Install CLI globally
npm install -g @playwright/cli

# Install Copilot skills
playwright-cli install --skills

# Run the agent test script (18 assertions, 5 screenshots)
./scripts/playwright-cli-test.sh

# Or ask Copilot directly:
# "Using playwright-cli, open http://localhost:8090 and test each tab"
```

---

*Document maintained by the copilot-spring-boot-demo team. For questions, see the [ARCHITECTURE.md](./ARCHITECTURE.md) and [QUICKSTART.md](./QUICKSTART.md).*
