import { test, expect } from '@playwright/test';
import { ProductsPage } from '../pages/ProductsPage';
import { SEED_PRODUCTS, API_BASE } from '../utils/test-data';
import path from 'path';
import fs from 'fs';

const screenshotDir = path.resolve(__dirname, '../../results/screenshots');
fs.mkdirSync(screenshotDir, { recursive: true });

test.describe('Inventory Service', () => {
  test('API: GET /api/products returns seeded products', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/products`);
    expect(response.ok()).toBeTruthy();
    const products = await response.json();
    expect(Array.isArray(products)).toBeTruthy();
    expect(products.length).toBeGreaterThanOrEqual(SEED_PRODUCTS.length);
  });

  test('API: GET /api/products/:id returns specific product', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/products/1`);
    expect(response.ok()).toBeTruthy();
    const product = await response.json();
    expect(product.id).toBe(1);
    expect(product.price).toBe(SEED_PRODUCTS[0].price);
  });

  test('API: product has required fields', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/products/2`);
    const product = await response.json();
    expect(product).toHaveProperty('id');
    expect(product).toHaveProperty('name');
    expect(product).toHaveProperty('price');
    expect(product).toHaveProperty('stockQuantity');
  });

  test('UI: Products tab displays all seeded products', async ({ page }) => {
    const productsPage = new ProductsPage(page);
    await productsPage.open();
    const rowCount = await productsPage.getRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(SEED_PRODUCTS.length);
    await page.screenshot({ path: path.join(screenshotDir, 'products-tab.png'), fullPage: true });
  });
});
