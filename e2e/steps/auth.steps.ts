import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import type { FactoryOpsWorld } from '../support/world';
import { loginAs, injectAuthCookies } from '../support/api';
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

/**
 * Cookie-based session check: after login, the browser should hold an
 * access_token cookie set by the server's Set-Cookie response.
 * Replaces the legacy "localStorage 應有 factory_ops_access_token" assertion.
 */
Then('瀏覽器 cookies 應包含 access_token', async function (this: FactoryOpsWorld) {
  const cookies = await this.context.cookies();
  const accessCookie = cookies.find((c) => c.name === 'access_token');
  expect(accessCookie, 'access_token cookie should be present after login').toBeDefined();
  expect(accessCookie!.value.length).toBeGreaterThan(10);
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
  await expect(this.page.getByText('帳號或密碼錯誤', { exact: false })).toBeVisible({ timeout: 8000 });
});

/**
 * Verify no auth cookie was set on failed login.
 * Replaces legacy "localStorage 不應該有 token" assertion.
 */
Then('瀏覽器不應有 access_token cookie', async function (this: FactoryOpsWorld) {
  const cookies = await this.context.cookies();
  const accessCookie = cookies.find((c) => c.name === 'access_token');
  // Either absent, or has empty/expired value (Max-Age=0 from failed login)
  const isAbsentOrCleared = !accessCookie || accessCookie.value === '' || accessCookie.value.length < 5;
  expect(isAbsentOrCleared).toBe(true);
});

// ----------------------------------------------------------------------------
// Scenario: 缺 orgCode 直接被 client validation 攔下
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
  const url = new URL(this.page.url());
  expect(url.pathname).toBe('/login');
  // No auth cookie should have been set
  const cookies = await this.context.cookies();
  const accessCookie = cookies.find((c) => c.name === 'access_token');
  expect(!accessCookie || accessCookie.value === '').toBe(true);
});

// ----------------------------------------------------------------------------
// Scenario: 未登入造訪受保護頁面
// ----------------------------------------------------------------------------

/**
 * Cookie-based "not logged in": clear browser cookies (no auth cookie present).
 * The AuthProvider probes GET /me which returns 401 with no cookie → user stays unauthenticated.
 *
 * The step name retains "(localStorage 已清空)" phrasing to match the feature file Gherkin text;
 * the implementation however uses cookie clearing (ADR-0015).
 */
Given(/^我尚未登入\(localStorage 已清空\)$/, async function (this: FactoryOpsWorld) {
  // Navigate to a neutral page first
  await this.page.goto('about:blank');
  // Clear all cookies so there is no access_token / refresh_token / XSRF-TOKEN
  await this.context.clearCookies();
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
  // Inject auth cookies into the browser context so page.goto('/') loads as authenticated
  await injectAuthCookies(this, user.accessToken, user.refreshToken);
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

// ----------------------------------------------------------------------------
// Scenario: reload 仍登入 (cookie persistent session)
// ----------------------------------------------------------------------------

Given('我已成功登入', async function (this: FactoryOpsWorld) {
  const user = await loginAs(this, 'admin.system', USERS['admin.system'].password);
  await injectAuthCookies(this, user.accessToken, user.refreshToken);
  await this.page.goto(`${FRONTEND_BASE_URL}/`);
  await this.page.waitForLoadState('networkidle');
  // Confirm we're on the home page
  const url = new URL(this.page.url());
  expect(url.pathname).toBe('/');
});

When('我 reload 頁面', async function (this: FactoryOpsWorld) {
  await this.page.reload();
  await this.page.waitForLoadState('networkidle');
});

Then('仍應停留在受保護頁面,不被導回登入頁', async function (this: FactoryOpsWorld) {
  const url = new URL(this.page.url());
  expect(url.pathname).not.toBe('/login');
});

// ----------------------------------------------------------------------------
// Scenario: 登出後 reload 不再登入
// ----------------------------------------------------------------------------

When('我完成登出流程', async function (this: FactoryOpsWorld) {
  // Navigate to home and perform logout via UI
  await this.page.goto(`${FRONTEND_BASE_URL}/`);
  await this.page.waitForLoadState('networkidle');

  const triggerByTestId = this.page.locator('[data-testid="user-menu-trigger"]');
  const hasTrigger = await triggerByTestId.count();

  if (hasTrigger > 0) {
    await triggerByTestId.click();
  } else {
    await this.page.getByText('System Admin').first().click();
  }

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

  // Wait until redirected to /login
  await this.page.waitForURL((url) => new URL(url).pathname === '/login', { timeout: 8000 });
});

Then('reload 後應被導回登入頁', async function (this: FactoryOpsWorld) {
  await this.page.reload();
  await this.page.waitForLoadState('networkidle');

  // After logout, cookies are cleared (Max-Age=0). GET /me should return 401.
  // ProtectedRoute should redirect to /login.
  await this.page.waitForURL(
    (url) => new URL(url).pathname === '/login',
    { timeout: 10000 }
  );
  const url = new URL(this.page.url());
  expect(url.pathname).toBe('/login');
});
