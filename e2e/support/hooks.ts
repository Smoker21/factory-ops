import {
  BeforeAll,
  AfterAll,
  Before,
  After,
  Status,
} from '@cucumber/cucumber';
import { chromium, request as playwrightRequest } from '@playwright/test';
import type { Browser, APIRequestContext } from '@playwright/test';
import type { FactoryOpsWorld } from './world';
import { BACKEND_BASE_URL, launchOptions, contextOptions } from '../playwright.config';
import { cleanupTestRoot } from './api';

let sharedBrowser: Browser | null = null;
let sharedApiContext: APIRequestContext | null = null;

// ----------------------------------------------------------------------------
// BeforeAll — launch a single browser + shared API request context
// ----------------------------------------------------------------------------
BeforeAll(async function () {
  sharedBrowser = await chromium.launch(launchOptions);
  sharedApiContext = await playwrightRequest.newContext({
    baseURL: BACKEND_BASE_URL,
    extraHTTPHeaders: { 'Content-Type': 'application/json' },
  });
});

// ----------------------------------------------------------------------------
// AfterAll — close shared resources
// ----------------------------------------------------------------------------
AfterAll(async function () {
  await sharedApiContext?.dispose();
  await sharedBrowser?.close();
});

// ----------------------------------------------------------------------------
// Before — fresh browser context + page per scenario
// ----------------------------------------------------------------------------
Before(async function (this: FactoryOpsWorld) {
  if (!sharedBrowser || !sharedApiContext) {
    throw new Error('Browser or API context not initialized. BeforeAll must run first.');
  }

  this.browser = sharedBrowser;
  this.apiRequest = sharedApiContext;

  // Create a fresh, isolated browser context per scenario
  this.context = await sharedBrowser.newContext({
    ...contextOptions,
    storageState: undefined,
  });

  // Start tracing for every scenario
  await this.context.tracing.start({
    screenshots: true,
    snapshots: true,
  });

  this.page = await this.context.newPage();

  // Initialize per-scenario test root prefix (for fixture naming)
  const randomSuffix = Math.random().toString(36).slice(2, 10);
  this.testRoot = {
    prefix: `e2e-${randomSuffix}`,
    createdProjectIds: [],
    createdTaskIds: [],
    createdActionRequestIds: [],
  };

  this.scenarioData = {};
  this.currentUser = null;
});

// ----------------------------------------------------------------------------
// After — capture artifacts on failure, then close context
// ----------------------------------------------------------------------------
After(async function (this: FactoryOpsWorld, scenario) {
  const failed = scenario.result?.status === Status.FAILED;

  if (failed) {
    // Screenshot on failure
    try {
      const screenshot = await this.page.screenshot({ fullPage: true });
      this.attach(screenshot, 'image/png');
    } catch {
      // ignore if page is already closed
    }

    // Stop trace and save on failure
    try {
      await this.context.tracing.stop({
        path: `reports/traces/${scenario.pickle.name.replace(/\s+/g, '_')}.zip`,
      });
    } catch {
      await this.context.tracing.stop().catch(() => undefined);
    }
  } else {
    await this.context.tracing.stop().catch(() => undefined);
  }

  // Best-effort cleanup of test fixtures created during this scenario
  try {
    if (this.currentUser?.accessToken) {
      await cleanupTestRoot(this.apiRequest, this.testRoot, this.currentUser.accessToken);
    }
  } catch {
    // cleanup is best-effort; do not fail the scenario
  }

  await this.page.close().catch(() => undefined);
  await this.context.close().catch(() => undefined);
});
