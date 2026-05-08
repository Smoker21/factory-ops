import type { APIRequestContext } from '@playwright/test';
import type { FactoryOpsWorld, CurrentUser, TestRoot } from './world';
import { BACKEND_BASE_URL } from '../playwright.config';

// eslint-disable-next-line @typescript-eslint/no-var-requires
const SEED = require('../fixtures/seed.json') as typeof import('../fixtures/seed.json');

// Known seed IDs from the running system
export const SEED_USERS = SEED.users;
export const SEED_ORG = SEED.org;

// ---------------------------------------------------------------------------
// Authentication helpers
// ---------------------------------------------------------------------------

/**
 * Login via API, store tokens + user profile on the world.
 * Does NOT navigate the browser — call injectAuthToLocalStorage + page.goto if you need UI.
 */
export async function loginAs(
  world: FactoryOpsWorld,
  accountName: string,
  password: string,
  orgCode = 'taichung-fab'
): Promise<CurrentUser> {
  const response = await world.apiRequest.post('/v1/auth/login', {
    data: { orgCode, accountName, password },
  });

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Login failed for ${accountName}: HTTP ${response.status()} — ${body}`);
  }

  const tokens = (await response.json()) as {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    tokenType: string;
  };

  // Decode JWT payload to get user info (base64url decode middle section)
  const payloadB64 = tokens.accessToken.split('.')[1];
  const payloadJson = Buffer.from(payloadB64, 'base64').toString('utf-8');
  const payload = JSON.parse(payloadJson) as {
    userId: string;
    accountName: string;
    displayName?: string;
    rootOrgId: string;
    groups?: string[];
    roles?: string[];
  };

  const currentUser: CurrentUser = {
    id: payload.userId,
    accountName: payload.accountName,
    displayName: payload.displayName ?? accountName,
    roles: payload.groups ?? payload.roles ?? [],
    rootOrgId: payload.rootOrgId,
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
  };

  // Enrich displayName from /me
  try {
    const meResp = await world.apiRequest.get('/v1/me', {
      headers: { Authorization: `Bearer ${tokens.accessToken}` },
    });
    if (meResp.ok()) {
      const me = (await meResp.json()) as { displayName: string };
      currentUser.displayName = me.displayName;
    }
  } catch {
    // best-effort enrichment
  }

  world.currentUser = currentUser;
  return currentUser;
}

/**
 * Inject auth cookies into the Playwright BrowserContext so the browser
 * automatically sends them on subsequent requests (cookie-based auth, ADR-0015).
 *
 * Use this instead of injectAuthToLocalStorage after the M5.5 cookie migration.
 * The tokens come from the loginAs() response and are set as cookies matching
 * the backend's Set-Cookie attributes.
 */
export async function injectAuthCookies(
  world: FactoryOpsWorld,
  accessToken: string,
  refreshToken: string,
  xsrfToken = 'mock-xsrf-token'
): Promise<void> {
  const domain = 'localhost';
  await world.context.addCookies([
    {
      name: 'access_token',
      value: accessToken,
      domain,
      path: '/v1',
      httpOnly: true,
      secure: false,
      sameSite: 'Lax',
    },
    {
      name: 'refresh_token',
      value: refreshToken,
      domain,
      path: '/v1/auth',
      httpOnly: true,
      secure: false,
      sameSite: 'Lax',
    },
    {
      name: 'XSRF-TOKEN',
      value: xsrfToken,
      domain,
      path: '/v1',
      httpOnly: false,
      secure: false,
      sameSite: 'Lax',
    },
  ]);
}

/**
 * @deprecated Use injectAuthCookies() instead (M5.5 cookie-based auth).
 * Kept for backwards compatibility with existing step files that still compile.
 * The behaviour is now a no-op — cookie-based auth does not use localStorage.
 */
export async function injectAuthToLocalStorage(
  _world: FactoryOpsWorld,
  _accessToken: string,
  _refreshToken: string
): Promise<void> {
  // No-op: tokens are no longer stored in localStorage (ADR-0015).
  // Browser cookie jar is populated by the real backend Set-Cookie response
  // when the step calls loginAs() + page.goto() to the login page, or via
  // injectAuthCookies() for pre-authenticated scenarios.
}

/**
 * Read the XSRF-TOKEN cookie value from the current Playwright BrowserContext.
 * Returns empty string if the cookie is not found.
 */
export async function getXsrfTokenFromCookies(world: FactoryOpsWorld): Promise<string> {
  const cookies = await world.context.cookies();
  const xsrf = cookies.find((c) => c.name === 'XSRF-TOKEN');
  return xsrf?.value ?? '';
}

// ---------------------------------------------------------------------------
// Authenticated API request helper
// ---------------------------------------------------------------------------
function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` };
}

// ---------------------------------------------------------------------------
// Project seed helpers
// ---------------------------------------------------------------------------
export interface SeedProjectOptions {
  name: string;
  organizationId?: string;
  ownerId?: string;
  groupIds?: string[];
  descriptionMarkdown?: string;
}

export async function seedProject(
  world: FactoryOpsWorld,
  options: SeedProjectOptions
): Promise<{ id: string; name: string; status: string }> {
  const token = world.currentUser?.accessToken;
  if (!token) throw new Error('No current user token. Call loginAs first.');

  const payload = {
    organizationId: options.organizationId ?? SEED_ORG.leafOrgId,
    name: `${world.testRoot.prefix}-${options.name}`,
    ownerId: options.ownerId ?? world.currentUser?.id ?? SEED_USERS['admin.system'].id,
    groupIds: options.groupIds ?? [SEED_ORG.groupId],
    descriptionMarkdown: options.descriptionMarkdown ?? null,
  };

  const resp = await world.apiRequest.post('/v1/projects', {
    data: payload,
    headers: authHeaders(token),
  });

  if (!resp.ok()) {
    throw new Error(`seedProject failed: HTTP ${resp.status()} — ${await resp.text()}`);
  }

  const project = (await resp.json()) as { id: string; name: string; status: string };
  world.testRoot.createdProjectIds.push(project.id);
  return project;
}

// ---------------------------------------------------------------------------
// Task seed helpers
// ---------------------------------------------------------------------------
export interface SeedTaskOptions {
  projectId: string;
  title: string;
  type?: string;
  status?: string;
  ownerId?: string;
  assignees?: string[];
}

export async function seedTask(
  world: FactoryOpsWorld,
  options: SeedTaskOptions
): Promise<{ id: string; title: string; status: string }> {
  const token = world.currentUser?.accessToken;
  if (!token) throw new Error('No current user token. Call loginAs first.');

  const ownerId = options.ownerId ?? world.currentUser?.id ?? SEED_USERS['admin.system'].id;

  const payload = {
    projectId: options.projectId,
    type: options.type ?? 'GENERAL',
    title: `${world.testRoot.prefix}-${options.title}`,
    ownerId,
    assignees: options.assignees ?? [ownerId],
  };

  const resp = await world.apiRequest.post('/v1/tasks', {
    data: payload,
    headers: authHeaders(token),
  });

  if (!resp.ok()) {
    throw new Error(`seedTask failed: HTTP ${resp.status()} — ${await resp.text()}`);
  }

  const task = (await resp.json()) as { id: string; title: string; status: string };
  world.testRoot.createdTaskIds.push(task.id);

  // Advance status if needed
  if (options.status && options.status !== 'OPEN') {
    await advanceTaskStatus(world, task.id, options.status, token);
  }

  return task;
}

/**
 * Advance a task through valid status transitions to reach the target status.
 */
async function advanceTaskStatus(
  world: FactoryOpsWorld,
  taskId: string,
  targetStatus: string,
  token: string
): Promise<void> {
  const validPaths: Record<string, string[]> = {
    IN_PROGRESS: ['IN_PROGRESS'],
    IN_REVIEW: ['IN_PROGRESS', 'IN_REVIEW'],
    DONE: ['IN_PROGRESS', 'IN_REVIEW', 'DONE'],
    CANCELLED: ['CANCELLED'],
  };

  const steps = validPaths[targetStatus];
  if (!steps) throw new Error(`Unknown target status: ${targetStatus}`);

  for (const status of steps) {
    const resp = await world.apiRequest.post(`/v1/tasks/${taskId}/status`, {
      data: { status },
      headers: authHeaders(token),
    });
    if (!resp.ok()) {
      const body = await resp.text();
      throw new Error(`advanceTaskStatus to ${status} failed: HTTP ${resp.status()} — ${body}`);
    }
  }
}

// ---------------------------------------------------------------------------
// ActionRequest seed helpers
// ---------------------------------------------------------------------------
export interface SeedActionRequestOptions {
  title: string;
  targetOrgId?: string;
  originatingOrgId?: string;
  severityLevel?: string;
  descriptionMarkdown?: string;
}

export async function seedActionRequest(
  world: FactoryOpsWorld,
  options: SeedActionRequestOptions
): Promise<{ id: string; title: string; status: string }> {
  const token = world.currentUser?.accessToken;
  if (!token) throw new Error('No current user token. Call loginAs first.');

  const payload = {
    originatingOrgId: options.originatingOrgId ?? SEED_ORG.rootOrgId,
    targetOrgId: options.targetOrgId ?? SEED_ORG.leafOrgId,
    title: `${world.testRoot.prefix}-${options.title}`,
    severityLevel: options.severityLevel ?? 'NORMAL',
    descriptionMarkdown: options.descriptionMarkdown ?? null,
  };

  const resp = await world.apiRequest.post('/v1/action-requests', {
    data: payload,
    headers: authHeaders(token),
  });

  if (!resp.ok()) {
    throw new Error(`seedActionRequest failed: HTTP ${resp.status()} — ${await resp.text()}`);
  }

  const ar = (await resp.json()) as { id: string; title: string; status: string };
  world.testRoot.createdActionRequestIds.push(ar.id);
  return ar;
}

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

/**
 * Best-effort soft-delete of all entities created during this scenario.
 * Failures are swallowed — cleanup must never fail a test.
 */
export async function cleanupTestRoot(
  apiRequest: APIRequestContext,
  testRoot: TestRoot,
  adminToken: string
): Promise<void> {
  const headers = authHeaders(adminToken);

  // Soft-delete tasks first
  for (const taskId of testRoot.createdTaskIds) {
    await apiRequest.delete(`/v1/tasks/${taskId}`, { headers }).catch(() => undefined);
  }

  // Soft-delete projects
  for (const projectId of testRoot.createdProjectIds) {
    await apiRequest.delete(`/v1/projects/${projectId}`, { headers }).catch(() => undefined);
  }

  // TODO(BDD-CLEANUP): Action requests lack a delete endpoint; leave them as-is
}

// ---------------------------------------------------------------------------
// API assertion helpers
// ---------------------------------------------------------------------------

export async function fetchProjects(
  apiRequest: APIRequestContext,
  token: string
): Promise<Array<{ id: string; name: string; status: string }>> {
  const resp = await apiRequest.get('/v1/projects', {
    headers: authHeaders(token),
  });
  if (!resp.ok()) throw new Error(`GET /v1/projects failed: ${resp.status()}`);
  const body = (await resp.json()) as { items: Array<{ id: string; name: string; status: string }> };
  return body.items;
}

export async function fetchTask(
  apiRequest: APIRequestContext,
  token: string,
  taskId: string
): Promise<Record<string, unknown>> {
  const resp = await apiRequest.get(`/v1/tasks/${taskId}`, {
    headers: authHeaders(token),
  });
  if (!resp.ok()) throw new Error(`GET /v1/tasks/${taskId} failed: ${resp.status()}`);
  return resp.json() as Promise<Record<string, unknown>>;
}

export async function fetchTaskHistory(
  apiRequest: APIRequestContext,
  token: string,
  taskId: string
): Promise<Array<{ action: string; payload: Record<string, unknown> }>> {
  const resp = await apiRequest.get(`/v1/tasks/${taskId}/history`, {
    headers: authHeaders(token),
  });
  if (!resp.ok()) throw new Error(`GET /v1/tasks/${taskId}/history failed: ${resp.status()}`);
  return resp.json() as Promise<Array<{ action: string; payload: Record<string, unknown> }>>;
}
