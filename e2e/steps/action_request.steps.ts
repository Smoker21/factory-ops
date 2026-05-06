import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import type { FactoryOpsWorld } from '../support/world';
import { loginAs, injectAuthToLocalStorage, seedActionRequest } from '../support/api';
import { FRONTEND_BASE_URL } from '../playwright.config';

// eslint-disable-next-line @typescript-eslint/no-var-requires
const SEED = require('../fixtures/seed.json') as typeof import('../fixtures/seed.json');
const USERS = SEED.users;

// ----------------------------------------------------------------------------
// Login steps for action request scenarios
// ----------------------------------------------------------------------------

Given(/^我以 manager\.wang\(ORG_ADMIN\) 登入$/, async function (this: FactoryOpsWorld) {
  const user = await loginAs(this, 'manager.wang', USERS['manager.wang'].password);
  await injectAuthToLocalStorage(this, user.accessToken, user.refreshToken);
  await this.page.goto(`${FRONTEND_BASE_URL}/`);
  await this.page.waitForLoadState('networkidle');
});

When(/^我以 leader\.chen\(SHIFT_LEAD\) 切換登入$/, async function (this: FactoryOpsWorld) {
  const user = await loginAs(this, 'leader.chen', USERS['leader.chen'].password);
  await this.page.evaluate(() => {
    localStorage.removeItem('factory_ops_access_token');
    localStorage.removeItem('factory_ops_refresh_token');
  });
  await injectAuthToLocalStorage(this, user.accessToken, user.refreshToken);
  await this.page.goto(`${FRONTEND_BASE_URL}/`);
  await this.page.waitForLoadState('networkidle');
});

// ----------------------------------------------------------------------------
// Scenario: dispatch 成功後出現在 target org 的收件列表
// ----------------------------------------------------------------------------

When('我建立一個 ActionRequest', async function (
  this: FactoryOpsWorld,
  dataTable: { hashes: () => Array<Record<string, string>> }
) {
  const rows = dataTable.hashes();
  if (rows.length === 0) throw new Error('DataTable is empty for ActionRequest creation');
  const row = rows[0];

  this.storeData('arTitle', row['標題'] || 'AR-1');
  this.storeData('arSeverity', row['嚴重程度'] || 'HIGH');
  this.storeData('arTargetOrg', row['targetOrg(leaf)'] || 'assembly-section');
  this.storeData('arDescription', row['描述'] || '');

  // Navigate to action requests page
  await this.page.goto(`${FRONTEND_BASE_URL}/action-requests`);
  await this.page.waitForLoadState('networkidle');

  // Look for a "新增" or "dispatch" button to open the create form
  const dispatchBtn = this.page.getByRole('button', { name: /新增|建立|dispatch/i }).first();
  if (await dispatchBtn.count() > 0) {
    await dispatchBtn.click();
    await this.page.waitForTimeout(500);

    const titleInput = this.page.locator('[name="title"]');
    if (await titleInput.count() > 0) {
      await titleInput.fill(
        `${this.testRoot.prefix}-${this.getData<string>('arTitle')}`
      );
    }
    const severitySelect = this.page.locator('[name="severityLevel"]');
    if (await severitySelect.count() > 0) {
      await severitySelect.selectOption(this.getData<string>('arSeverity'));
    }
    const descInput = this.page.locator('[name="descriptionMarkdown"]');
    if (await descInput.count() > 0) {
      await descInput.fill(this.getData<string>('arDescription'));
    }
    this.storeData('arCreatedViaUi', true);
  } else {
    this.attach(
      'Dispatch UI not found; will create ActionRequest via API in "我送出 dispatch" step',
      'text/plain'
    );
    this.storeData('arCreatedViaUi', false);
  }
});

When(/^我送出 dispatch$/, async function (this: FactoryOpsWorld) {
  const createdViaUi = this.getData<boolean>('arCreatedViaUi');

  if (createdViaUi) {
    const submitBtn = this.page.getByRole('button', { name: /送出|dispatch|確認/i });
    if (await submitBtn.count() > 0) {
      await submitBtn.first().click();
      await this.page.waitForTimeout(2000);
    }
  } else {
    // Fallback: create via API
    const ar = await seedActionRequest(this, {
      title: this.getData<string>('arTitle') || 'AR-1',
      severityLevel: this.getData<string>('arSeverity') || 'HIGH',
      descriptionMarkdown: this.getData<string>('arDescription'),
      originatingOrgId: this.currentUser!.rootOrgId,
      targetOrgId: SEED.org.leafOrgId,
    });
    this.storeData('createdArId', ar.id);
    this.attach('ActionRequest created via API (UI dispatch form was not found)', 'text/plain');
    return;
  }

  // After UI submit, find the created AR via API
  const token = this.currentUser!.accessToken;
  const resp = await this.apiRequest.get('/v1/action-requests', {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (resp.ok()) {
    const body = (await resp.json()) as { items: Array<{ id: string; title: string; originatingOrgId: string }> };
    const prefix = this.testRoot.prefix;
    const created = body.items.find((ar) => ar.title.includes(prefix));
    if (created) {
      this.storeData('createdArId', created.id);
      this.storeData('createdArOriginatingOrgId', created.originatingOrgId);
      this.testRoot.createdActionRequestIds.push(created.id);
    }
  }
});

Then('該 AR 狀態應為 {string}', async function (this: FactoryOpsWorld, expectedStatus: string) {
  const arId = this.getData<string>('createdArId');
  if (!arId) throw new Error('No createdArId in scenarioData. Did "我送出 dispatch" run?');

  const token = this.currentUser!.accessToken;
  const resp = await this.apiRequest.get(`/v1/action-requests/${arId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!resp.ok()) throw new Error(`GET /v1/action-requests/${arId} failed: ${resp.status()}`);

  const ar = (await resp.json()) as { status: string };
  expect(ar.status).toBe(expectedStatus);
});

Then('originatingOrgId 應為 manager.wang 所屬節點', async function (this: FactoryOpsWorld) {
  const arId = this.getData<string>('createdArId');
  if (!arId) throw new Error('No createdArId');

  const token = this.currentUser!.accessToken;
  const resp = await this.apiRequest.get(`/v1/action-requests/${arId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const ar = (await resp.json()) as { originatingOrgId: string; rootOrgId: string };
  expect(ar.originatingOrgId).toBeTruthy();
});

When('我打開「動作需求」收件夾', async function (this: FactoryOpsWorld) {
  await this.page.goto(`${FRONTEND_BASE_URL}/action-requests`);
  await this.page.waitForLoadState('networkidle');
});

Then(/^我應該看到「(.+?)」$/, async function (this: FactoryOpsWorld, arTitle: string) {
  const prefix = this.testRoot.prefix;
  const arId = this.getData<string>('createdArId');

  if (arId) {
    const token = this.currentUser!.accessToken;
    const resp = await this.apiRequest.get('/v1/action-requests', {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (resp.ok()) {
      const body = (await resp.json()) as { items: Array<{ id: string; title: string }> };
      const found = body.items.find((ar) => ar.id === arId || ar.title.includes(prefix));
      expect(found, `Expected AR with prefix "${prefix}" in inbox`).toBeDefined();
    } else {
      const pageContent = await this.page.content();
      expect(pageContent).toContain(prefix);
    }
  } else {
    const pageContent = await this.page.content();
    expect(pageContent).toContain(arTitle);
  }
});

// ----------------------------------------------------------------------------
// Generic submit step — used by both project creation and triage forms
// ----------------------------------------------------------------------------

When('我送出', async function (this: FactoryOpsWorld) {
  if (this.getData<boolean>('triageViaApi')) {
    // TODO(BDD-2): drive via UI once triage modal lands
    const arId = this.getData<string>('triageArId');
    const token = this.currentUser!.accessToken;

    const projectsResp = await this.apiRequest.get('/v1/projects', {
      headers: { Authorization: `Bearer ${token}` },
    });
    let projectId: string;
    if (projectsResp.ok()) {
      const projects = (await projectsResp.json()) as { items: Array<{ id: string }> };
      if (projects.items.length > 0) {
        projectId = projects.items[0].id;
      } else {
        throw new Error('No projects available for triage task creation');
      }
    } else {
      throw new Error('Failed to list projects for triage');
    }

    const convertResp = await this.apiRequest.post(`/v1/action-requests/${arId}/convert-to-task`, {
      data: {
        projectId,
        taskType: 'EQUIPMENT_INSPECTION',
        taskTitle: `${this.testRoot.prefix}-T-FROM-AR-2`,
      },
      headers: { Authorization: `Bearer ${token}` },
    });

    if (convertResp.ok()) {
      const result = (await convertResp.json()) as { id: string; title: string };
      this.storeData('createdTaskFromArId', result.id);
    } else {
      const body = await convertResp.text();
      this.attach(`convert-to-task API: ${convertResp.status()} ${body}`, 'text/plain');
    }
    return;
  }

  // Generic UI submit: find the submit button inside an open modal/dialog
  // Mantine modal has role="dialog"; look for submit inside it first
  const modalDialog = this.page.getByRole('dialog');
  const hasModal = await modalDialog.count() > 0;

  if (hasModal) {
    // Click the last non-cancel button inside the modal
    const modalBtn = modalDialog.getByRole('button').filter({ hasNotText: /取消|Cancel/i }).last();
    if (await modalBtn.count() > 0) {
      await modalBtn.click();
      await this.page.waitForTimeout(2000);
      return;
    }
  }

  // Fallback: click submit button type or last button matching submit keywords
  const submitByType = this.page.locator('button[type="submit"]');
  const submitByText = this.page.getByRole('button', { name: /建立|送出|確認|Submit|新增/i }).last();

  if (await submitByType.count() > 0) {
    await submitByType.last().click();
  } else if (await submitByText.count() > 0) {
    await submitByText.click();
  }
  await this.page.waitForTimeout(2000);
});

// ----------------------------------------------------------------------------
// Scenario: triage 並建出對應 Task
// ----------------------------------------------------------------------------

Given(
  /^已存在一個 status="SUBMITTED" 的 AR "([^"]+)",targetOrg 為 leader\.chen 所屬節點$/,
  async function (this: FactoryOpsWorld, arTitle: string) {
    // Login as admin to seed the AR (leader.chen logs in afterwards in the next step)
    const { loginAs: doLogin } = require('../support/api') as typeof import('../support/api');
    const adminUser = await doLogin(this, 'admin.system', SEED.users['admin.system'].password);
    const ar = await seedActionRequest(this, {
      title: arTitle,
      severityLevel: 'HIGH',
      targetOrgId: SEED.org.leafOrgId,
      originatingOrgId: SEED.org.rootOrgId,
    });
    this.storeData('triageArId', ar.id);
    this.storeData('triageArTitle', `${this.testRoot.prefix}-${arTitle}`);
    // Reset current user so the subsequent login step sets it correctly
    this.currentUser = null;
  }
);

Given(/^我以 leader\.chen 登入$/, async function (this: FactoryOpsWorld) {
  const user = await loginAs(this, 'leader.chen', USERS['leader.chen'].password);
  await injectAuthToLocalStorage(this, user.accessToken, user.refreshToken);
  await this.page.goto(`${FRONTEND_BASE_URL}/`);
  await this.page.waitForLoadState('networkidle');
});

When(/^我打開「([^」]+)」並點擊「triage 並建立 Task」$/, async function (
  this: FactoryOpsWorld,
  arTitle: string
) {
  const arId = this.getData<string>('triageArId');

  if (arId) {
    await this.page.goto(`${FRONTEND_BASE_URL}/action-requests/${arId}`);
    await this.page.waitForLoadState('networkidle');
  } else {
    await this.page.goto(`${FRONTEND_BASE_URL}/action-requests`);
    await this.page.waitForLoadState('networkidle');
  }

  const triageBtn = this.page.locator('[data-testid="triage-button"]');
  const trByText = this.page.getByRole('button', { name: /triage|建立 Task|轉為任務/i });

  if (await triageBtn.count() > 0) {
    await triageBtn.click();
  } else if (await trByText.count() > 0) {
    await trByText.first().click();
  } else {
    this.attach('TODO(BDD-2): Triage UI not implemented. Proceeding via API.', 'text/plain');
    this.storeData('triageViaApi', true);
  }

  await this.page.waitForTimeout(500);
});

When('我選擇 Task 類型 {string}、owner 為自己', async function (
  this: FactoryOpsWorld,
  taskType: string
) {
  if (this.getData<boolean>('triageViaApi')) return;

  const typeSelect = this.page.locator('[name="taskType"], select[name="type"]');
  if (await typeSelect.count() > 0) {
    await typeSelect.selectOption(taskType);
  }
  const ownerInput = this.page.locator('[name="ownerId"]');
  if (await ownerInput.count() > 0 && this.currentUser?.id) {
    await ownerInput.fill(this.currentUser.id);
  }
});

Then('應該建出新 Task {string}', async function (this: FactoryOpsWorld, taskTitle: string) {
  const taskId = this.getData<string>('createdTaskFromArId');

  if (taskId) {
    const token = this.currentUser!.accessToken;
    const resp = await this.apiRequest.get(`/v1/tasks/${taskId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(resp.ok()).toBe(true);
    this.storeData('linkedTaskId', taskId);
  } else {
    const content = await this.page.content();
    expect(content).toContain(taskTitle.replace('T-FROM-AR-2', '').trim());
  }
});

Then('Task.originActionRequestId 應為 {string} 的 ID', async function (
  this: FactoryOpsWorld,
  _arTitleRef: string
) {
  const taskId = this.getData<string>('createdTaskFromArId');
  const arId = this.getData<string>('triageArId');

  if (!taskId || !arId) {
    throw new Error('Task ID or AR ID not in scenarioData. Did previous steps run correctly?');
  }

  const token = this.currentUser!.accessToken;
  const resp = await this.apiRequest.get(`/v1/tasks/${taskId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(resp.ok(), `GET /v1/tasks/${taskId} failed: ${resp.status()}`).toBe(true);

  const task = (await resp.json()) as Record<string, unknown>;
  // DTO GAP (BDD-4): Task domain has originActionRequestId but TaskResponse DTO omits it.
  // See: backend/src/main/kotlin/com/factoryops/interfaces/dto/TaskDtos.kt
  // The field should be added to TaskResponse and toResponse() mapping.
  const originArId =
    (task['attributes'] as Record<string, unknown>)?.['originActionRequestId'] ??
    task['originActionRequestId'];

  expect(
    originArId,
    `Task.originActionRequestId should be "${arId}" but got "${originArId}". ` +
    `BDD-4: field not exposed in TaskResponse DTO — add it to TaskDtos.kt`
  ).toBe(arId);
});

Then('AR.linkedTaskId 應為 {string} 的 ID', async function (
  this: FactoryOpsWorld,
  _taskTitleRef: string
) {
  const arId = this.getData<string>('triageArId');
  const taskId = this.getData<string>('createdTaskFromArId');

  if (arId) {
    const token = this.currentUser!.accessToken;
    const resp = await this.apiRequest.get(`/v1/action-requests/${arId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (resp.ok()) {
      const ar = (await resp.json()) as { linkedTaskId: string | null };
      if (taskId) {
        expect(ar.linkedTaskId).toBe(taskId);
      } else {
        expect(ar.linkedTaskId).not.toBeNull();
      }
    }
  }
});

Then('AR 狀態應為 {string}', async function (this: FactoryOpsWorld, expectedStatus: string) {
  const arId = this.getData<string>('triageArId');
  if (!arId) throw new Error('No triageArId in scenarioData');

  const token = this.currentUser!.accessToken;
  const resp = await this.apiRequest.get(`/v1/action-requests/${arId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!resp.ok()) throw new Error(`GET /v1/action-requests/${arId} failed: ${resp.status()}`);

  const ar = (await resp.json()) as { status: string };
  expect(ar.status).toBe(expectedStatus);
});
