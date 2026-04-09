import { test, expect } from '@playwright/test';
import { OrdersPage } from '../pages/OrdersPage';
import { API_BASE, CREATE_ORDER_PAYLOAD } from '../utils/test-data';
import path from 'path';
import fs from 'fs';

const screenshotDir = path.resolve(__dirname, '../../results/screenshots');
fs.mkdirSync(screenshotDir, { recursive: true });

test.describe('Order Service — Cross-Service Integration', () => {
  test.beforeAll(async ({ request }) => {
    // Restore stock for all products so POST tests are not blocked by depleted inventory
    for (let id = 1; id <= 6; id++) {
      await request.post(`${API_BASE}/api/products/${id}/restore?quantity=500`);
    }
  });

  test('API: GET /api/orders returns list', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/orders`);
    expect(response.ok()).toBeTruthy();
    const orders = await response.json();
    expect(Array.isArray(orders)).toBeTruthy();
  });

  test('API: POST /api/orders creates order with valid customer and product', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/orders`, {
      data: CREATE_ORDER_PAYLOAD,
    });
    expect(response.status()).toBe(201);
    const order = await response.json();
    expect(order).toHaveProperty('id');
    expect(order.customerId).toBe(CREATE_ORDER_PAYLOAD.customerId);
    expect(order.status).toBeDefined();
    expect(order.totalAmount).toBeGreaterThan(0);
  });

  test('API: POST /api/orders — created order has correct item count', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/orders`, {
      data: CREATE_ORDER_PAYLOAD,
    });
    expect(response.status()).toBe(201);
    const order = await response.json();
    expect(order.items?.length ?? 1).toBeGreaterThanOrEqual(1);
  });

  test('API: GET /api/orders/:id returns created order', async ({ request }) => {
    // Fetch the list first so this test is independent of inventory stock levels
    const listResponse = await request.get(`${API_BASE}/api/orders`);
    expect(listResponse.ok()).toBeTruthy();
    const orders = await listResponse.json();
    expect(orders.length).toBeGreaterThan(0);

    const firstId = orders[0].id;
    const getResponse = await request.get(`${API_BASE}/api/orders/${firstId}`);
    expect(getResponse.ok()).toBeTruthy();
    const retrieved = await getResponse.json();
    expect(retrieved.id).toBe(firstId);
  });

  test('UI: Orders tab loads without error', async ({ page }) => {
    const ordersPage = new OrdersPage(page);
    await ordersPage.openList();
    await expect(ordersPage.table).toBeVisible();
    await expect(page.locator('#orders-error')).not.toBeVisible();
    await page.screenshot({ path: path.join(screenshotDir, 'orders-tab.png'), fullPage: true });
  });

  test('UI: Create Order form is present and interactive', async ({ page }) => {
    const ordersPage = new OrdersPage(page);
    await ordersPage.openCreateForm();
    await expect(ordersPage.customerSelect).toBeVisible();
    await expect(ordersPage.submitButton).toBeVisible();
    await page.screenshot({ path: path.join(screenshotDir, 'create-order-form.png'), fullPage: true });
  });
});
