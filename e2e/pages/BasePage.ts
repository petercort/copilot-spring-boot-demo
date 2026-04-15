import { Page } from '@playwright/test';

export class BasePage {
  constructor(protected page: Page) {}

  async navigateTo(tab: 'customers' | 'products' | 'orders' | 'create-order') {
    await this.page.click(`[data-testid="nav-${tab}"]`);
    await this.page.waitForTimeout(500);
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('networkidle');
  }
}
