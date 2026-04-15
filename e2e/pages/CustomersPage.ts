import { Page, Locator } from '@playwright/test';
import { BasePage } from './BasePage';

export class CustomersPage extends BasePage {
  readonly table: Locator;

  constructor(page: Page) {
    super(page);
    this.table = page.locator('[data-testid="customers-table"]');
  }

  async open() {
    await this.goto();
    await this.navigateTo('customers');
    await this.table.waitFor({ state: 'visible' });
  }

  async getRowCount(): Promise<number> {
    return this.table.locator('tbody tr').count();
  }

  async getCustomerEmail(index: number): Promise<string> {
    return this.table.locator('tbody tr').nth(index).locator('td').nth(2).innerText();
  }
}
