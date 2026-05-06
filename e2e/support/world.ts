import { World, IWorldOptions, setWorldConstructor, setDefaultTimeout } from '@cucumber/cucumber';

// Increase default step timeout to 30 seconds to allow for slow UI interactions
setDefaultTimeout(30000);
import type { Browser, BrowserContext, Page, APIRequestContext } from '@playwright/test';

export interface CurrentUser {
  id: string;
  accountName: string;
  displayName: string;
  roles: string[];
  rootOrgId: string;
  accessToken: string;
  refreshToken: string;
}

export interface TestRoot {
  /** A short random prefix used for all fixture names created in this scenario */
  prefix: string;
  /** IDs of entities created during this scenario, for cleanup */
  createdProjectIds: string[];
  createdTaskIds: string[];
  createdActionRequestIds: string[];
}

export class FactoryOpsWorld extends World {
  // Shared across all scenarios (set in BeforeAll hook)
  browser!: Browser;
  apiRequest!: APIRequestContext;

  // Per-scenario (set in Before hook)
  context!: BrowserContext;
  page!: Page;

  // Set after a successful login
  currentUser: CurrentUser | null = null;

  // Per-scenario test data tracking for cleanup
  testRoot: TestRoot = {
    prefix: '',
    createdProjectIds: [],
    createdTaskIds: [],
    createdActionRequestIds: [],
  };

  // Scenario-scoped storage for step-to-step data passing
  scenarioData: Record<string, unknown> = {};

  constructor(options: IWorldOptions) {
    super(options);
  }

  /** Store a value in scenario-scoped data bag */
  storeData(key: string, value: unknown): void {
    this.scenarioData[key] = value;
  }

  /** Retrieve a value from scenario-scoped data bag */
  getData<T>(key: string): T {
    return this.scenarioData[key] as T;
  }
}

setWorldConstructor(FactoryOpsWorld);
