import { Page, Locator } from '@playwright/test';
import { BasePage } from './BasePage';

export class ProductsPage extends BasePage {
  readonly table: Locator;

  constructor(page: Page) {
    super(page);
    this.table = page.locator('[data-testid="products-table"]');
  }

  async open() {
    await this.goto();
    await this.navigateTo('products');
    await this.table.waitFor({ state: 'visible' });
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('tbody tr').count();
  }
}
