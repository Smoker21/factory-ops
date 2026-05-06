/**
 * Playwright browser launch options used by the Cucumber World.
 * NOTE: We are NOT using the Playwright test runner — Cucumber is the runner.
 * Playwright provides browser automation only.
 */
import type { LaunchOptions, BrowserContextOptions } from '@playwright/test';

export const FRONTEND_BASE_URL = 'http://localhost:5173';
export const BACKEND_BASE_URL = 'http://localhost:8080';

export const launchOptions: LaunchOptions = {
  headless: process.env['DEBUG'] !== '1',
  slowMo: process.env['DEBUG'] === '1' ? 200 : 0,
};

export const contextOptions: BrowserContextOptions = {
  recordVideo:
    process.env['CI'] === 'true'
      ? { dir: 'test-results/videos' }
      : undefined,
  viewport: { width: 1280, height: 720 },
};
