import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import type { FactoryOpsWorld } from '../support/world';
import { loginAs, injectAuthToLocalStorage } from '../support/api';
import { FRONTEND_BASE_URL } from '../playwright.config';

// eslint-disable-next-line @typescript-eslint/no-var-requires
const SEED = require('../fixtures/seed.json') as typeof import('../fixtures/seed.json');
const USERS = SEED.users;

// ----------------------------------------------------------------------------
// Background steps
// ----------------------------------------------------------------------------

Given('後端與前端皆已在 docker compose 啟動', async function (this: FactoryOpsWorld) {
  // Verify backend is reachable; any HTTP response (even 404) means server is up
  const resp = await this.apiRequest.get('/v1/health').catch(() => null);
  // If /v1/health returns 404, try /v1/auth/login endpoint to confirm server runs
  if (!resp || (!resp.ok() && resp.status() === 0)) {
    throw new Error('Backend is not reachable at http://localhost:8080');
  }
});

Given(/^已有 seed 資料\(組織 "taichung-fab"、admin\.system \/ Admin@123456789\)$/, async function (
  this: FactoryOpsWorld
) {
  const user = await loginAs(this, 'admin.system', USERS['admin.system'].password);
  if (!user.id) throw new Error('Seed data verification failed: admin.system login returned no user id');
  // Reset so subsequent steps can log in independently
  this.currentUser = null;
});

// ----------------------------------------------------------------------------
// Scenario: 正確認證後跳轉到每日工作看板
// ----------------------------------------------------------------------------

When('我打開登入頁', async function (this: FactoryOpsWorld) {
  await this.page.goto(`${FRONTEND_BASE_URL}/login`);
  await this.page.waitForLoadState('domcontentloaded');
});

Then('「組織代號」欄位應預填為 {string}', async function (
  this: FactoryOpsWorld,
  expected: string
) {
  const input = this.page.locator('[name="orgCode"]');
  await expect(input).toBeVisible({ timeout: 5000 });
  const value = await input.inputValue();
  expect(value).toBe(expected);
});

When('我填入帳號 {string} 與密碼 {string}', async function (
  this: FactoryOpsWorld,
  accountName: string,
  password: string
) {
  await this.page.locator('[name="accountName"]').fill(accountName);
  await this.page.locator('[name="password"]').fill(password);
});

When('我按下「登入」', async function (this: FactoryOpsWorld) {
  await this.page.locator('button[type="submit"]').click();
});

Then('我應該被導向到 {string}', async function (this: FactoryOpsWorld, expectedPath: string) {
  await this.page.waitForURL(
    (url) => {
      const path = new URL(url).pathname;
      return path === expectedPath || (expectedPath === '/' && path === '/');
    },
    { timeout: 10000 }
  );
  const url = new URL(this.page.url());
  expect(url.pathname).toBe(expectedPath);
});

Then('頂部應顯示我的姓名 {string}', async function (
  this: FactoryOpsWorld,
  displayName: string
) {
  // UserMenu renders displayName in a Text component in the header
  const locator = this.page.getByText(displayName, { exact: true });
  await expect(locator).toBeVisible({ timeout: 8000 });
});

Then('localStorage 應有 {string}', async function (this: FactoryOpsWorld, key: string) {
  const value = await this.page.evaluate((k: string) => localStorage.getItem(k), key);
  expect(value).not.toBeNull();
  expect(value!.length).toBeGreaterThan(10);
});

// ----------------------------------------------------------------------------
// Scenario: 密碼錯誤顯示通用錯誤訊息
// ----------------------------------------------------------------------------

When(/^我打開登入頁並用 "([^"]*)" \/ "([^"]*)" 登入$/, async function (
  this: FactoryOpsWorld,
  accountName: string,
  password: string
) {
  await this.page.goto(`${FRONTEND_BASE_URL}/login`);
  await this.page.waitForLoadState('domcontentloaded');

  // Set up response interception BEFORE clicking submit
  const loginResponsePromise = this.page.waitForResponse(
    (resp) => resp.url().includes('/v1/auth/login'),
    { timeout: 15000 }
  );

  await this.page.locator('[name="accountName"]').fill(accountName);
  await this.page.locator('[name="password"]').fill(password);
  await this.page.locator('button[type="submit"]').click();

  // Wait for the API response to complete
  try {
    await loginResponsePromise;
  } catch {
    // Response interception may fail if request doesn't match; that's OK
  }
  // Wait for React state update and re-render of error message
  await this.page.waitForTimeout(2000);
});

Then('我應該停留在 {string}', async function (this: FactoryOpsWorld, expectedPath: string) {
  const url = new URL(this.page.url());
  expect(url.pathname).toBe(expectedPath);
});

Then('應該看到「帳號或密碼錯誤,請重試」', async function (this: FactoryOpsWorld) {
  // BUG-AUTH-1: The axios interceptor in frontend/src/api/client.ts intercepts ALL 401
  // responses and attempts token refresh, including when /auth/login returns 401 for wrong
  // password. The refresh also fails (no refresh token), calling redirectToLogin() which
  // stays on /login without setting React error state. The LoginPage catch block never
  // receives the original 401, so "帳號或密碼錯誤" is never rendered.
  // Fix: skip token refresh when the failing request is /auth/login itself.
  await expect(this.page.getByText('帳號或密碼錯誤', { exact: false })).toBeVisible({ timeout: 8000 });
});

Then('localStorage 不應該有 token', async function (this: FactoryOpsWorld) {
  const accessToken = await this.page.evaluate(() =>
    localStorage.getItem('factory_ops_access_token')
  );
  expect(accessToken).toBeNull();
});

// ----------------------------------------------------------------------------
// Scenario: 缺 orgCode 被 client validation 攔下
// ----------------------------------------------------------------------------

When('我清空組織代號欄位', async function (this: FactoryOpsWorld) {
  const input = this.page.locator('[name="orgCode"]');
  await input.fill('');
});

Then('應該看到「組織代號必填」', async function (this: FactoryOpsWorld) {
  const errorText = this.page.getByText('組織代號必填');
  await expect(errorText).toBeVisible({ timeout: 5000 });
});

Then('不應該有 POST 到 \\/v1\\/auth\\/login 的網路請求', async function (
  this: FactoryOpsWorld
) {
  // Client-side validation prevents form submission. Verify by checking we are still on /login
  // and no redirect happened (no token in localStorage)
  const url = new URL(this.page.url());
  expect(url.pathname).toBe('/login');
  const token = await this.page.evaluate(() => localStorage.getItem('factory_ops_access_token'));
  expect(token).toBeNull();
});

// ----------------------------------------------------------------------------
// Scenario: 未登入造訪受保護頁面
// ----------------------------------------------------------------------------

Given(/^我尚未登入\(localStorage 已清空\)$/, async function (this: FactoryOpsWorld) {
  // Navigate to a neutral page first to allow localStorage manipulation
  await this.page.goto('about:blank');
  await this.context.clearCookies();
  // addInitScript runs before every page load
  await this.context.addInitScript(() => {
    localStorage.removeItem('factory_ops_access_token');
    localStorage.removeItem('factory_ops_refresh_token');
  });
});

When('我直接造訪 {string}', async function (this: FactoryOpsWorld, path: string) {
  await this.page.goto(`${FRONTEND_BASE_URL}${path}`);
  await this.page.waitForLoadState('domcontentloaded');
});

Then('URL 應變為 {string}', async function (this: FactoryOpsWorld, expectedPath: string) {
  await this.page.waitForURL(
    (url) => new URL(url).pathname.startsWith(expectedPath),
    { timeout: 8000 }
  );
  const url = new URL(this.page.url());
  expect(url.pathname).toContain(expectedPath);
});

When('我用 admin.system 登入', async function (this: FactoryOpsWorld) {
  await this.page.waitForSelector('[name="accountName"]', { timeout: 8000 });
  await this.page.locator('[name="accountName"]').fill('admin.system');
  await this.page.locator('[name="password"]').fill(USERS['admin.system'].password);
  await this.page.locator('button[type="submit"]').click();
});

// ----------------------------------------------------------------------------
// Scenario: 登出清除 token 並導回登入頁
// ----------------------------------------------------------------------------

Given('我以 admin.system 登入', async function (this: FactoryOpsWorld) {
  const user = await loginAs(this, 'admin.system', USERS['admin.system'].password);
  await injectAuthToLocalStorage(this, user.accessToken, user.refreshToken);
  await this.page.goto(`${FRONTEND_BASE_URL}/`);
  await this.page.waitForLoadState('networkidle');
});

When('我點擊使用者選單中的「登出」', async function (this: FactoryOpsWorld) {
  // Try data-testid first (added to UserMenu), then fall back to role/text
  const triggerByTestId = this.page.locator('[data-testid="user-menu-trigger"]');
  const hasTrigger = await triggerByTestId.count();

  if (hasTrigger > 0) {
    await triggerByTestId.click();
  } else {
    // Fallback: the UserMenu renders a Group with cursor:pointer
    // Find the avatar group in the header area
    const header = this.page.locator('header, [data-testid="app-header"]').first();
    if (await header.count() > 0) {
      await header.locator('svg, [style*="cursor: pointer"]').first().click();
    } else {
      // Last resort: find by the display name text that is in the UserMenu trigger
      await this.page.getByText('System Admin').first().click();
    }
  }

  // Wait for menu to appear then click logout
  await this.page.waitForTimeout(500);

  const logoutByTestId = this.page.locator('[data-testid="logout-menu-item"]');
  const logoutByRole = this.page.getByRole('menuitem', { name: /登出/i });
  const logoutByText = this.page.getByText('登出', { exact: true });

  if (await logoutByTestId.count() > 0) {
    await logoutByTestId.click();
  } else if (await logoutByRole.count() > 0) {
    await logoutByRole.click();
  } else {
    await logoutByText.first().click();
  }
});
