import { Page, Locator } from '@playwright/test';
import { BasePage } from './BasePage';
import { API_BASE, CREATE_ORDER_PAYLOAD } from '../utils/test-data';

export class OrdersPage extends BasePage {
  readonly table: Locator;
  readonly form: Locator;
  readonly customerSelect: Locator;
  readonly submitButton: Locator;

  constructor(page: Page) {
    super(page);
    this.table = page.locator('[data-testid="orders-table"]');
    this.form = page.locator('[data-testid="create-order-form"]');
    this.customerSelect = page.locator('[data-testid="customer-select"]');
    this.submitButton = page.locator('[data-testid="submit-order"]');
  }

  async openList() {
    await this.goto();
    await this.navigateTo('orders');
    await this.table.waitFor({ state: 'visible' });
  }

  async openCreateForm() {
    await this.goto();
    await this.navigateTo('create-order');
    await this.form.waitFor({ state: 'visible' });
  }

  async createOrderViaAPI(): Promise<number> {
    const response = await this.page.request.post(`${API_BASE}/api/orders`, {
      data: CREATE_ORDER_PAYLOAD,
    });
    const body = await response.json();
    return body.id;
  }
}
