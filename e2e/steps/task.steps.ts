import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import type { FactoryOpsWorld } from '../support/world';
import {
  seedProject,
  seedTask,
  fetchTask,
  fetchTaskHistory,
} from '../support/api';
import { FRONTEND_BASE_URL } from '../playwright.config';

// eslint-disable-next-line @typescript-eslint/no-var-requires
const SEED = require('../fixtures/seed.json') as typeof import('../fixtures/seed.json');
const USERS = SEED.users;

// Map Gherkin task name aliases to the seeded task data key
const TASK_ALIAS_KEY = 'tasksByAlias';

// ----------------------------------------------------------------------------
// Background steps
// (背景: 我以 admin.system 登入 is handled by auth.steps)
// ----------------------------------------------------------------------------

Given(
  '已存在一個 status={string} 的 Task {string},owner 為 admin.system',
  async function (this: FactoryOpsWorld, status: string, taskAlias: string) {
    // Create a project first, then create the task under it
    const project = await seedProject(this, { name: `task-test-proj` });
    const task = await seedTask(this, {
      projectId: project.id,
      title: taskAlias,
      status: status,
      ownerId: this.currentUser!.id,
      assignees: [this.currentUser!.id],
    });

    // Store task by alias for subsequent steps
    const aliases = this.getData<Record<string, { id: string; title: string; projectId: string }>>(TASK_ALIAS_KEY) || {};
    aliases[taskAlias] = { id: task.id, title: task.title, projectId: project.id };
    this.storeData(TASK_ALIAS_KEY, aliases);
    this.storeData('currentProjectId', project.id);
  }
);

// ----------------------------------------------------------------------------
// Scenario Outline: 合法狀態轉移會更新 Task 與 history
// ----------------------------------------------------------------------------

Given('Task {string} 目前狀態為 {string}', async function (
  this: FactoryOpsWorld,
  taskAlias: string,
  fromStatus: string
) {
  const aliases = this.getData<Record<string, { id: string; title: string; projectId: string }>>(TASK_ALIAS_KEY) || {};
  const taskInfo = aliases[taskAlias];

  if (!taskInfo) {
    throw new Error(`Task alias "${taskAlias}" not found. Did the background step run?`);
  }

  // Check current status and advance if needed
  const task = await fetchTask(this.apiRequest, this.currentUser!.accessToken, taskInfo.id);
  const currentStatus = task.status as string;

  if (currentStatus !== fromStatus) {
    // Need to advance to fromStatus via API
    const validPaths: Record<string, string[]> = {
      OPEN: [],
      IN_PROGRESS: ['IN_PROGRESS'],
      IN_REVIEW: ['IN_PROGRESS', 'IN_REVIEW'],
      DONE: ['IN_PROGRESS', 'IN_REVIEW', 'DONE'],
      CANCELLED: ['CANCELLED'],
    };
    const steps = validPaths[fromStatus] ?? [];
    const token = this.currentUser!.accessToken;

    // Only advance from current position
    let startIdx = 0;
    if (currentStatus === 'IN_PROGRESS') startIdx = 1;
    else if (currentStatus === 'IN_REVIEW') startIdx = 2;

    for (let i = startIdx; i < steps.length; i++) {
      const resp = await this.apiRequest.post(`/v1/tasks/${taskInfo.id}/status`, {
        data: { status: steps[i] },
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!resp.ok()) {
        throw new Error(`Failed to advance task to ${steps[i]}: ${resp.status()}`);
      }
    }
  }

  // Verify final status
  const updatedTask = await fetchTask(this.apiRequest, this.currentUser!.accessToken, taskInfo.id);
  expect(updatedTask.status).toBe(fromStatus);

  // Navigate to the task detail page
  await this.page.goto(`${FRONTEND_BASE_URL}/tasks/${taskInfo.id}`);
  await this.page.waitForLoadState('networkidle');

  this.storeData('currentTaskId', taskInfo.id);
});

When('我點擊轉到 {string} 的按鈕', async function (
  this: FactoryOpsWorld,
  toStatus: string
) {
  const STATUS_LABELS: Record<string, string> = {
    IN_PROGRESS: '開始進行',
    BLOCKED: '標記阻塞',
    IN_REVIEW: '送審核',
    DONE: '標記完成',
    CANCELLED: '取消',
  };

  const label = STATUS_LABELS[toStatus] ?? toStatus;

  // Try data-testid first
  const byTestId = this.page.locator(`[data-testid="task-status-btn-${toStatus}"]`);
  const byText = this.page.getByRole('button', { name: label, exact: true });
  const byTextLoose = this.page.getByRole('button', { name: new RegExp(label) });

  if (await byTestId.count() > 0) {
    await byTestId.click();
  } else if (await byText.count() > 0) {
    await byText.click();
  } else {
    await byTextLoose.first().click();
  }

  // Wait for status change to propagate
  await this.page.waitForTimeout(1500);
});

Then('Task 狀態應變為 {string}', async function (this: FactoryOpsWorld, expectedStatus: string) {
  const taskId = this.getData<string>('dualSignTaskId') || this.getData<string>('currentTaskId');
  if (!taskId) throw new Error('No task ID in scenarioData');
  const task = await fetchTask(this.apiRequest, this.currentUser!.accessToken, taskId);
  expect(task.status).toBe(expectedStatus);
});

Then('Task 狀態應為 {string}', async function (this: FactoryOpsWorld, expectedStatus: string) {
  const taskId = this.getData<string>('currentTaskId');
  if (!taskId) throw new Error('No currentTaskId in scenarioData');

  const task = await fetchTask(this.apiRequest, this.currentUser!.accessToken, taskId);
  expect(task.status).toBe(expectedStatus);
});

Then(
  'history 應 append 一筆 action={string} payload from={string} to={string}',
  async function (
    this: FactoryOpsWorld,
    action: string,
    fromStatus: string,
    toStatus: string
  ) {
    const taskId = this.getData<string>('currentTaskId');
    if (!taskId) throw new Error('No currentTaskId in scenarioData');

    const history = await fetchTaskHistory(this.apiRequest, this.currentUser!.accessToken, taskId);

    const matchingEntry = history.find(
      (h) =>
        h.action === action &&
        (h.payload as Record<string, unknown>)['from'] === fromStatus &&
        (h.payload as Record<string, unknown>)['to'] === toStatus
    );

    expect(matchingEntry).toBeDefined();
  }
);

// ----------------------------------------------------------------------------
// Scenario: 終態 Task 不顯示狀態變更按鈕
// ----------------------------------------------------------------------------

Given('Task {string} 狀態為 {string}', async function (
  this: FactoryOpsWorld,
  taskAlias: string,
  status: string
) {
  const aliases = this.getData<Record<string, { id: string; title: string; projectId: string }>>(TASK_ALIAS_KEY) || {};
  const taskInfo = aliases[taskAlias];

  if (!taskInfo) {
    throw new Error(`Task alias "${taskAlias}" not found. Background step may not have seeded it.`);
  }

  // The task was seeded in background; verify its status
  const task = await fetchTask(this.apiRequest, this.currentUser!.accessToken, taskInfo.id);
  // If status doesn't match, the background seeding should have set it
  if (task.status !== status) {
    // Re-seed with correct status
    const project = await seedProject(this, { name: `done-task-proj` });
    const newTask = await seedTask(this, {
      projectId: project.id,
      title: taskAlias + '-terminal',
      status: status,
      ownerId: this.currentUser!.id,
    });
    aliases[taskAlias] = { id: newTask.id, title: newTask.title, projectId: project.id };
    this.storeData(TASK_ALIAS_KEY, aliases);
    this.storeData('currentTaskId', newTask.id);
  } else {
    this.storeData('currentTaskId', taskInfo.id);
  }
});

When('我打開該 Task 詳情頁', async function (this: FactoryOpsWorld) {
  const taskId = this.getData<string>('currentTaskId');
  if (!taskId) {
    const aliases = this.getData<Record<string, { id: string }>>(TASK_ALIAS_KEY) || {};
    const firstAlias = Object.values(aliases)[0];
    if (firstAlias) {
      this.storeData('currentTaskId', firstAlias.id);
      await this.page.goto(`${FRONTEND_BASE_URL}/tasks/${firstAlias.id}`);
    }
  } else {
    await this.page.goto(`${FRONTEND_BASE_URL}/tasks/${taskId}`);
  }
  await this.page.waitForLoadState('networkidle');
});

Then('我應該看不到任何狀態轉換按鈕', async function (this: FactoryOpsWorld) {
  // TaskStatusFlow returns null when nextStatuses is empty (DONE/CANCELLED)
  // Check that none of the status transition buttons are visible
  const statusButtons = [
    this.page.getByRole('button', { name: '開始進行' }),
    this.page.getByRole('button', { name: '送審核' }),
    this.page.getByRole('button', { name: '標記完成' }),
    this.page.getByRole('button', { name: '取消' }),
  ];

  for (const btn of statusButtons) {
    const count = await btn.count();
    expect(count, `Expected no status transition button but found one`).toBe(0);
  }
});

// ----------------------------------------------------------------------------
// Scenario: dual-sign Task
// ----------------------------------------------------------------------------

Given(
  'Task {string} 屬於設定 dualSignRequired=true、requiredReviewerRoles=["QA","SHIFT_LEAD"] 的 Group,狀態為 IN_REVIEW',
  async function (this: FactoryOpsWorld, taskAlias: string) {
    // Use the existing seeded project which already has dualSignRequired=true in its group
    const project = await seedProject(this, { name: `dual-sign-proj` });

    // Create task — the group (groupId from seed) already has dualSignRequired=true
    const task = await seedTask(this, {
      projectId: project.id,
      title: taskAlias,
      status: 'IN_REVIEW',
      ownerId: this.currentUser!.id,
      assignees: [this.currentUser!.id],
    });

    this.storeData('currentTaskId', task.id);
    this.storeData('dualSignTaskId', task.id);
  }
);

When(/^我以 qa\.zhang\(QA\) 簽核 APPROVED$/, async function (this: FactoryOpsWorld) {
  // TODO(BDD-2): drive via UI once qa review modal lands
  // For now, call API directly as QA user
  const qaToken = await getTokenForUser(this, 'qa.zhang');
  const taskId = this.getData<string>('dualSignTaskId');

  const resp = await this.apiRequest.post(`/v1/tasks/${taskId}/review`, {
    data: { role: 'QA', decision: 'APPROVED' },
    headers: { Authorization: `Bearer ${qaToken}` },
  });

  if (!resp.ok()) {
    const body = await resp.text();
    this.attach(`QA review API response: ${resp.status()} ${body}`, 'text/plain');
    // Don't throw — let assertion steps determine success/failure
  }
});

Then('Task 狀態仍應為 {string}', async function (
  this: FactoryOpsWorld,
  expectedStatus: string
) {
  const taskId = this.getData<string>('dualSignTaskId') || this.getData<string>('currentTaskId');
  const task = await fetchTask(this.apiRequest, this.currentUser!.accessToken, taskId);
  expect(task.status).toBe(expectedStatus);
});

When(/^我以 leader\.chen\(SHIFT_LEAD\) 簽核 APPROVED$/, async function (this: FactoryOpsWorld) {
  // TODO(BDD-2): drive via UI once qa review modal lands
  const leaderToken = await getTokenForUser(this, 'leader.chen');
  const taskId = this.getData<string>('dualSignTaskId');

  const resp = await this.apiRequest.post(`/v1/tasks/${taskId}/review`, {
    data: { role: 'SHIFT_LEAD', decision: 'APPROVED' },
    headers: { Authorization: `Bearer ${leaderToken}` },
  });

  if (!resp.ok()) {
    const body = await resp.text();
    this.attach(`SHIFT_LEAD review API response: ${resp.status()} ${body}`, 'text/plain');
  }
});

Then('qaReviews 陣列長度應為 {int}', async function (
  this: FactoryOpsWorld,
  expectedLength: number
) {
  const taskId = this.getData<string>('dualSignTaskId') || this.getData<string>('currentTaskId');
  const task = await fetchTask(this.apiRequest, this.currentUser!.accessToken, taskId) as {
    qaReviews: unknown[];
  };
  expect(task.qaReviews.length).toBe(expectedLength);
});

// ---------------------------------------------------------------------------
// Helper: get an access token for a specific user
// ---------------------------------------------------------------------------
async function getTokenForUser(world: FactoryOpsWorld, accountName: string): Promise<string> {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const SEED_DATA = require('../fixtures/seed.json') as typeof import('../fixtures/seed.json');
  const userData = SEED_DATA.users[accountName as keyof typeof SEED_DATA.users];
  if (!userData) throw new Error(`No seed user found for accountName: ${accountName}`);

  const resp = await world.apiRequest.post('/v1/auth/login', {
    data: {
      orgCode: SEED_DATA.org.orgCode,
      accountName: userData.accountName,
      password: userData.password,
    },
  });

  if (!resp.ok()) {
    throw new Error(`Login for ${accountName} failed: ${resp.status()}`);
  }

  const tokens = (await resp.json()) as { accessToken: string };
  return tokens.accessToken;
}
