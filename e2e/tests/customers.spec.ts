import { test, expect } from '@playwright/test';
import { CustomersPage } from '../pages/CustomersPage';
import { SEED_CUSTOMERS, API_BASE } from '../utils/test-data';
import path from 'path';
import fs from 'fs';

const screenshotDir = path.resolve(__dirname, '../../results/screenshots');
fs.mkdirSync(screenshotDir, { recursive: true });

test.describe('Customer Service', () => {
  test('API: GET /api/customers returns seeded customers', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/customers`);
    expect(response.ok()).toBeTruthy();
    const customers = await response.json();
    expect(Array.isArray(customers)).toBeTruthy();
    expect(customers.length).toBeGreaterThanOrEqual(SEED_CUSTOMERS.length);
  });

  test('API: GET /api/customers/:id returns specific customer', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/customers/1`);
    expect(response.ok()).toBeTruthy();
    const customer = await response.json();
    expect(customer.id).toBe(1);
    expect(customer.email).toBe(SEED_CUSTOMERS[0].email);
  });

  test('API: GET /api/customers/:id returns 404 for unknown id', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/customers/99999`);
    expect(response.status()).toBe(404);
  });

  test('UI: Customers tab displays all seeded customers', async ({ page }) => {
    const customersPage = new CustomersPage(page);
    await customersPage.open();
    const rowCount = await customersPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(3);
    await page.screenshot({ path: path.join(screenshotDir, 'customers-tab.png'), fullPage: true });
  });

  test('UI: Customer table shows correct email for first customer', async ({ page }) => {
    const customersPage = new CustomersPage(page);
    await customersPage.open();
    const email = await customersPage.getCustomerEmail(0);
    const emails = SEED_CUSTOMERS.map(c => c.email);
    expect(emails).toContain(email);
    await page.screenshot({ path: path.join(screenshotDir, 'customers-email-check.png'), fullPage: true });
  });
});
