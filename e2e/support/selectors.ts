/**
 * Central selector map for E2E tests.
 * Prefer role/text queries. Use data-testid only when disambiguation is needed.
 * Keep additions surgical — no behavior changes in frontend components.
 */

// Auth / Login page
export const LOGIN_ORG_CODE_INPUT = '[name="orgCode"]';
export const LOGIN_ACCOUNT_NAME_INPUT = '[name="accountName"]';
export const LOGIN_PASSWORD_INPUT = '[name="password"]';
export const LOGIN_SUBMIT_BUTTON = 'button[type="submit"]';

// Top navigation / AppShell header
export const USER_DISPLAY_NAME = '[data-testid="user-display-name"]';
export const USER_MENU_TRIGGER = '[data-testid="user-menu-trigger"]';
export const LOGOUT_MENU_ITEM = '[data-testid="logout-menu-item"]';

// Navigation
export const NAV_PROJECTS = '[data-testid="nav-projects"]';
export const NAV_ACTION_REQUESTS = '[data-testid="nav-action-requests"]';

// Projects page
export const CREATE_PROJECT_BUTTON = '[data-testid="create-project-button"]';
export const PROJECT_CARD = '[data-testid="project-card"]';

// Project detail page
export const PROJECT_OWNER_FIELD = '[data-testid="project-owner"]';
export const PROJECT_MEMBERS_LIST = '[data-testid="project-members"]';

// Tasks
export const TASK_STATUS_BADGE = '[data-testid="task-status-badge"]';
export const TASK_STATUS_BUTTON = (toStatus: string): string =>
  `[data-testid="task-status-btn-${toStatus}"]`;

// Action Requests
export const AR_INBOX_ITEM = '[data-testid="ar-inbox-item"]';
export const DISPATCH_SUBMIT_BUTTON = '[data-testid="dispatch-submit"]';
export const TRIAGE_BUTTON = '[data-testid="triage-button"]';
