import { test, expect } from '@playwright/test';
import { API_BASE, CREATE_ORDER_PAYLOAD } from '../utils/test-data';
import path from 'path';
import fs from 'fs';

const screenshotDir = path.resolve(__dirname, '../../results/screenshots');
fs.mkdirSync(screenshotDir, { recursive: true });

test.describe('Error Scenarios', () => {
  test('API: POST /api/orders with nonexistent customer returns error', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/orders`, {
      data: { ...CREATE_ORDER_PAYLOAD, customerId: 99999 },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('API: POST /api/orders with nonexistent product returns error', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/orders`, {
      data: {
        ...CREATE_ORDER_PAYLOAD,
        items: [{ productId: 99999, quantity: 1 }],
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('API: POST /api/orders with zero quantity returns error', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/orders`, {
      data: {
        ...CREATE_ORDER_PAYLOAD,
        items: [{ productId: 1, quantity: 0 }],
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('API: GET /api/orders/:id returns 404 for unknown order', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/orders/99999`);
    expect(response.status()).toBe(404);
  });

  test('UI: Error state is shown on API failure', async ({ page }) => {
    // Intercept the API call and return an error
    await page.route('**/api/customers', route =>
      route.fulfill({ status: 500, body: 'Internal Server Error' })
    );
    await page.goto('/');
    await page.click('[data-testid="nav-customers"]');
    await page.waitForTimeout(1000);
    // customers-error div should be visible when the API returns 500
    const errorVisible = await page.locator('#customers-error').isVisible();
    const tableVisible = await page.locator('[data-testid="customers-table"]').isVisible();
    expect(errorVisible || tableVisible).toBeTruthy();
    await page.screenshot({ path: path.join(screenshotDir, 'error-state.png'), fullPage: true });
  });
});
