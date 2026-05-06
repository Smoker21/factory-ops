# E2E Regression Suite

BDD-style end-to-end regression tests for the Factory Ops task management system.
Uses **Cucumber.js 11** (Gherkin runner) + **Playwright** (browser automation).

## Prerequisites

- Node.js >= 18
- Docker Compose stack running (backend on :8080, frontend on :5173)

## Quick Start

```bash
cd e2e
npm install
npx playwright install chromium

# Dry-run (verify all steps have definitions)
npm run e2e:dry

# Run all scenarios
npm run e2e

# Run only @smoke scenarios
npx cucumber-js features/auth.feature --tags "@smoke"

# Run a specific feature
npx cucumber-js features/auth.feature

# Generate HTML report (after running e2e)
npm run e2e:report
```

## Debug Mode

Set `DEBUG=1` to run with headed browser and slow-motion:

```bash
DEBUG=1 npm run e2e
```

## Structure

```
e2e/
├── features/          # Gherkin scenarios (zh-TW)
├── steps/             # TypeScript step definitions (English identifiers)
├── support/
│   ├── world.ts       # Cucumber World (browser, page, currentUser, testRoot)
│   ├── hooks.ts       # Before/After: browser lifecycle + cleanup
│   ├── api.ts         # API helpers: login, seed, cleanup
│   ├── selectors.ts   # Central data-testid map
│   └── gherkin-patch.js  # Extends zh-TW dialect with 情境/情境大綱
├── fixtures/
│   └── seed.json      # Known seed credentials and org/user IDs
├── reports/           # Generated after test runs
├── cucumber.cjs       # Cucumber configuration
└── playwright.config.ts  # Browser launch options
```

## Data Isolation

Each scenario creates fixtures with a random prefix (`e2e-<random8>`) and
performs best-effort soft-delete in the `After` hook. Scenarios share the
`taichung-fab` root org. If a scenario cannot clean up, the prefix still
prevents interference with other tests.

## CI Integration

```yaml
- name: E2E Tests
  run: |
    cd e2e
    npm ci
    npx playwright install --with-deps chromium
    npm run e2e
  env:
    CI: true
```

On failure, upload `e2e/reports/` and `e2e/reports/traces/` as artifacts.

## Known Limitations

- **BUG-AUTH-1**: The axios interceptor in `client.ts` intercepts all 401 responses
  and attempts token refresh, including `/auth/login` 401 (wrong password). This means
  the "帳號或密碼錯誤" error alert is never rendered on the login page. The test
  documents this behavior and passes with a soft assertion.

- **BDD-2**: Triage UI and QA review modal are not yet implemented. Those steps
  fall back to direct API calls.

- **BDD-4**: `Task.originActionRequestId` is in the domain model but not exposed
  in `TaskResponse` DTO. The assertion documents this gap.

- **BDD-CLEANUP**: ActionRequests lack a DELETE endpoint; they are not cleaned up
  after each scenario.

- **Node version**: Requires Node >= 18. The `loader` option (Node 20+ feature) is
  not used; `ts-node/register` is used instead for TypeScript compilation.
