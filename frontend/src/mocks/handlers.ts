import { http, HttpResponse } from 'msw'
import type {
  TokenPair,
  User,
  Project,
  ProjectPage,
  Task,
  TaskPage,
  TaskTypeDefinition,
  Comment,
  Role,
  ReviewDecision,
} from '@/api/types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/v1'

// Seed users matching backend dev seed
const SEED_USERS: Record<string, User & { password: string }> = {
  'admin.system': {
    id: 'user-admin',
    rootOrgId: 'org-fab-1',
    accountName: 'admin.system',
    displayName: '系統管理員',
    email: 'admin@factory.ops',
    roles: ['ADMIN'],
    groupIds: [],
    orgManagerScopes: [],
    active: true,
    password: 'Admin@123456789',
  },
  'manager.wang': {
    id: 'user-manager',
    rootOrgId: 'org-fab-1',
    accountName: 'manager.wang',
    displayName: '王經理',
    email: 'manager.wang@factory.ops',
    roles: ['ORG_ADMIN'],
    groupIds: ['group-1'],
    orgManagerScopes: ['org-fab-1'],
    active: true,
    password: 'Manager@123456789',
  },
  'leader.chen': {
    id: 'user-leader',
    rootOrgId: 'org-fab-1',
    accountName: 'leader.chen',
    displayName: '陳班長',
    email: 'leader.chen@factory.ops',
    roles: ['SHIFT_LEAD', 'GROUP_MANAGER'],
    groupIds: ['group-1', 'group-2'],
    orgManagerScopes: [],
    active: true,
    password: 'Leader@123456789',
  },
  'operator.li': {
    id: 'user-operator',
    rootOrgId: 'org-fab-1',
    accountName: 'operator.li',
    displayName: '李作業員',
    email: 'operator.li@factory.ops',
    roles: ['OPERATOR'],
    groupIds: ['group-1'],
    orgManagerScopes: [],
    active: true,
    password: 'Operator@123456789',
  },
  'qa.zhang': {
    id: 'user-qa',
    rootOrgId: 'org-fab-1',
    accountName: 'qa.zhang',
    displayName: '張品保',
    email: 'qa.zhang@factory.ops',
    roles: ['QA', 'ENGINEER'],
    groupIds: ['group-2'],
    orgManagerScopes: [],
    active: true,
    password: 'QA@1234567890',
  },
}

const SEED_PROJECTS: Project[] = [
  {
    id: 'project-1',
    rootOrgId: 'org-fab-1',
    organizationId: 'org-section-1',
    name: '月度設備保養計畫',
    descriptionMarkdown: '每月例行設備保養與檢查作業。',
    status: 'ACTIVE',
    ownerId: 'user-leader',
    memberIds: ['user-leader', 'user-operator', 'user-qa'],
    groupIds: ['group-1'],
    schedule: { start: '2026-05-01T08:00:00+08:00', due: '2026-05-31T17:00:00+08:00' },
    tags: ['maintenance', 'monthly'],
    overdue: false,
    createdAt: '2026-05-01T08:00:00+08:00',
    updatedAt: '2026-05-01T08:00:00+08:00',
  },
  {
    id: 'project-2',
    rootOrgId: 'org-fab-1',
    organizationId: 'org-section-1',
    name: '空壓機異常處置',
    descriptionMarkdown: '夜班發現空壓機異音，需即時處置。',
    status: 'ACTIVE',
    ownerId: 'user-leader',
    memberIds: ['user-leader', 'user-qa'],
    groupIds: ['group-2'],
    schedule: { start: '2026-05-03T06:00:00+08:00', due: '2026-05-04T06:00:00+08:00' },
    tags: ['incident', 'urgent'],
    overdue: false,
    createdAt: '2026-05-03T06:00:00+08:00',
    updatedAt: '2026-05-03T06:00:00+08:00',
  },
]

const SEED_TASKS: Task[] = [
  {
    id: 'task-1',
    rootOrgId: 'org-fab-1',
    projectId: 'project-1',
    type: 'EQUIPMENT_INSPECTION',
    attributes: { equipmentId: 'EQ-001', checklistVersion: 'v3.2' },
    title: '空壓機 A 定期點檢',
    descriptionMarkdown: '依照 SOP-EQ-001 進行空壓機 A 的月度點檢作業。',
    status: 'IN_PROGRESS',
    priority: 'NORMAL',
    ownerId: 'user-leader',
    assignees: ['user-leader', 'user-operator'],
    schedule: { start: '2026-05-03T08:00:00+08:00', due: '2026-05-03T17:00:00+08:00' },
    qaReviewPolicy: {
      dualSignRequired: true,
      requiredReviewerRoles: ['QA', 'SHIFT_LEAD'],
      snapshotAt: '2026-05-01T08:00:00+08:00',
      sourceGroupIds: ['group-1'],
    },
    qaReviews: [],
    tags: ['equipment', 'inspection'],
    overdue: false,
    createdAt: '2026-05-03T07:00:00+08:00',
    updatedAt: '2026-05-03T07:00:00+08:00',
  },
  {
    id: 'task-2',
    rootOrgId: 'org-fab-1',
    projectId: 'project-1',
    type: 'SHIFT_HANDOVER',
    attributes: { shiftFrom: '夜班', shiftTo: '早班', handoverNotes: '設備正常，無待處理事項' },
    title: '交班事項 - 05/03 夜班→早班',
    descriptionMarkdown: '夜班交班注意事項...',
    status: 'OPEN',
    priority: 'NORMAL',
    ownerId: 'user-leader',
    assignees: ['user-leader'],
    schedule: { due: '2026-05-03T08:00:00+08:00' },
    qaReviewPolicy: {
      dualSignRequired: false,
      requiredReviewerRoles: [],
      snapshotAt: '2026-05-01T08:00:00+08:00',
      sourceGroupIds: ['group-1'],
    },
    qaReviews: [],
    tags: ['handover'],
    overdue: false,
    createdAt: '2026-05-03T06:00:00+08:00',
    updatedAt: '2026-05-03T06:00:00+08:00',
  },
  {
    id: 'task-3',
    rootOrgId: 'org-fab-1',
    projectId: 'project-2',
    type: 'INCIDENT_RESPONSE',
    attributes: { incidentType: '設備異音', severity: 'HIGH', equipmentId: 'EQ-AIR-001' },
    title: '空壓機異音處置',
    descriptionMarkdown: '夜班發現空壓機 AIR-001 有異音，需立即檢查。',
    status: 'OPEN',
    priority: 'URGENT',
    ownerId: 'user-qa',
    assignees: ['user-qa', 'user-operator'],
    schedule: { due: '2026-05-04T06:00:00+08:00' },
    qaReviewPolicy: {
      dualSignRequired: true,
      requiredReviewerRoles: ['QA'],
      snapshotAt: '2026-05-03T06:00:00+08:00',
      sourceGroupIds: ['group-2'],
    },
    qaReviews: [],
    tags: ['incident', 'urgent'],
    overdue: false,
    createdAt: '2026-05-03T06:00:00+08:00',
    updatedAt: '2026-05-03T06:00:00+08:00',
  },
]

const TASK_TYPES: TaskTypeDefinition[] = [
  {
    type: 'EQUIPMENT_INSPECTION',
    label: '設備檢查',
    description: '定期設備點檢作業',
    requiresReview: true,
  },
  {
    type: 'INCIDENT_RESPONSE',
    label: '異常處置',
    description: '現場異常事件處置',
    requiresReview: true,
  },
  {
    type: 'SHIFT_HANDOVER',
    label: '交班事項',
    description: '班別交接確認事項',
    requiresReview: false,
  },
  {
    type: 'GENERAL',
    label: '一般任務',
    description: '一般性工作任務',
    requiresReview: false,
  },
]

const taskComments: Comment[] = [
  {
    id: 'comment-1',
    taskId: 'task-1',
    authorId: 'user-leader',
    content: '已開始執行點檢，目前正在檢查潤滑油位。',
    createdAt: '2026-05-03T09:00:00+08:00',
  },
]

let nextTaskId = 10
const taskStore = [...SEED_TASKS]
const projectStore = [...SEED_PROJECTS]
const commentStore = [...taskComments]

// Unicode-safe base64 encoding for mock JWT (supports Chinese characters in displayName)
function unicodeBase64(str: string): string {
  // Encode UTF-8 to bytes then base64
  const bytes = new TextEncoder().encode(str)
  let binary = ''
  bytes.forEach((b) => { binary += String.fromCharCode(b) })
  return btoa(binary)
}

function makeToken(user: User): string {
  const payload = {
    sub: user.accountName,
    userId: user.id,
    accountName: user.accountName,
    displayName: user.accountName, // Use accountName (ASCII) to avoid btoa issues
    rootOrgId: user.rootOrgId,
    roles: user.roles,
    groupIds: user.groupIds,
    orgManagerScopes: user.orgManagerScopes,
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  }
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = unicodeBase64(JSON.stringify(payload))
  return `${header}.${body}.mock_signature`
}

// Track current session user in the mock (set on login, cleared on logout)
let currentMockUser: (User & { password: string }) | null = null

/**
 * Reset the in-memory session state between tests.
 * Call this in a beforeEach / afterEach in test setup to ensure test isolation.
 * `server.resetHandlers()` only resets per-test handler overrides — it does NOT
 * reset module-scoped state like currentMockUser.
 */
export function resetMockSession(): void {
  currentMockUser = null
}

// Build the three Set-Cookie headers that the real backend emits.
// MSW v2 requires appending multiple Set-Cookie values to a Headers instance
// because a single header string cannot represent two cookies with the same
// field name.
const MOCK_XSRF = 'mock-xsrf-token'

function buildAuthCookieHeaders(user: User): Headers {
  const accessToken = makeToken(user)
  const refreshToken = `refresh_${user.id}_${Date.now()}`
  const headers = new Headers()
  // access_token: httpOnly (simulated — jsdom cannot set httpOnly from JS, but
  // the header is emitted for realism; the cookie will be readable in tests)
  headers.append(
    'Set-Cookie',
    `access_token=${accessToken}; Path=/v1; Max-Age=900; SameSite=Lax`
  )
  // refresh_token: httpOnly
  headers.append(
    'Set-Cookie',
    `refresh_token=${refreshToken}; Path=/v1/auth; Max-Age=604800; SameSite=Lax`
  )
  // XSRF-TOKEN: non-httpOnly (must be readable by JS for CSRF echo)
  headers.append(
    'Set-Cookie',
    `XSRF-TOKEN=${MOCK_XSRF}; Path=/v1; Max-Age=604800; SameSite=Lax`
  )
  return headers
}

function buildClearCookieHeaders(): Headers {
  const headers = new Headers()
  headers.append('Set-Cookie', 'access_token=; Path=/v1; Max-Age=0; SameSite=Lax')
  headers.append('Set-Cookie', 'refresh_token=; Path=/v1/auth; Max-Age=0; SameSite=Lax')
  headers.append('Set-Cookie', 'XSRF-TOKEN=; Path=/v1; Max-Age=0; SameSite=Lax')
  return headers
}

// Decode the access_token cookie value to retrieve accountName.
// Falls back to Authorization header for backward-compat with existing Bearer tests.
function resolveUserFromRequest(request: Request): (User & { password: string }) | null {
  // Cookie-based auth (new path)
  const cookieHeader = request.headers.get('Cookie') ?? ''
  const accessTokenMatch = cookieHeader.match(/(?:^|;\s*)access_token=([^;]+)/)
  if (accessTokenMatch) {
    try {
      const token = accessTokenMatch[1]
      const parts = token.split('.')
      if (parts.length >= 2) {
        const payload = JSON.parse(atob(parts[1])) as { accountName: string }
        const found = SEED_USERS[payload.accountName]
        if (found) return found
      }
    } catch {
      // fall through to next method
    }
  }

  // In-memory session set by the login handler (works in jsdom where
  // document.cookie and fetch cookie jar interact differently)
  if (currentMockUser) return currentMockUser

  // Bearer header fallback for backward-compat with existing tests
  const auth = request.headers.get('Authorization')
  if (auth?.startsWith('Bearer ')) {
    try {
      const token = auth.slice(7)
      const parts = token.split('.')
      if (parts.length >= 2) {
        const payload = JSON.parse(atob(parts[1])) as { accountName: string }
        const found = SEED_USERS[payload.accountName]
        if (found) return found
      }
    } catch {
      // fall through
    }
  }

  return null
}

export const handlers = [
  // Auth
  http.post(`${BASE_URL}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as { accountName: string; password: string }
    const user = SEED_USERS[body.accountName]
    if (!user || user.password !== body.password) {
      return HttpResponse.json(
        { title: '帳號或密碼錯誤', status: 401 },
        { status: 401 }
      )
    }
    // Track session for jsdom environments where cookie jar isn't shared
    currentMockUser = user

    const { password: _pass, ...userWithoutPassword } = user
    const cookieHeaders = buildAuthCookieHeaders(user)

    // Keep body tokens for forward-compat; frontend ignores them (ADR-0015).
    const tokenPair: TokenPair = {
      accessToken: makeToken(user),
      refreshToken: `refresh_${user.id}_${Date.now()}`,
      expiresIn: 3600,
      tokenType: 'Bearer',
    }
    return HttpResponse.json({ ...tokenPair, user: userWithoutPassword }, { headers: cookieHeaders })
  }),

  http.post(`${BASE_URL}/auth/refresh`, ({ request }) => {
    // Cookie-first: resolve user from existing access_token cookie or in-memory session.
    // Unlike the old bearer-only mock, we do NOT fall back to a hardcoded user —
    // an unauthenticated refresh must return 401 so the client redirect-to-login
    // guard is exercised correctly.
    const user = resolveUserFromRequest(request)
    if (!user) {
      return HttpResponse.json({ title: 'Invalid or missing refresh token', status: 401 }, { status: 401 })
    }
    const cookieHeaders = buildAuthCookieHeaders(user)
    const tokenPair: TokenPair = {
      accessToken: makeToken(user),
      refreshToken: `refresh_${user.id}_${Date.now()}`,
      expiresIn: 3600,
      tokenType: 'Bearer',
    }
    return HttpResponse.json(tokenPair, { headers: cookieHeaders })
  }),

  http.post(`${BASE_URL}/auth/logout`, () => {
    currentMockUser = null
    return new HttpResponse(null, { status: 204, headers: buildClearCookieHeaders() })
  }),

  // Me — accepts cookie auth, in-memory session, or Bearer header (backward-compat)
  http.get(`${BASE_URL}/me`, ({ request }) => {
    const user = resolveUserFromRequest(request)
    if (!user) return HttpResponse.json({ title: 'Unauthorized', status: 401 }, { status: 401 })
    const { password: _pass, ...userWithoutPassword } = user
    return HttpResponse.json(userWithoutPassword)
  }),

  // Users
  http.get(`${BASE_URL}/users`, () => {
    const users = Object.values(SEED_USERS).map(({ password: _p, ...u }) => u)
    return HttpResponse.json({ items: users, pageInfo: { nextCursor: null, hasMore: false } })
  }),

  http.get(`${BASE_URL}/users/:userId`, ({ params }) => {
    const userId = params.userId as string
    const found = Object.values(SEED_USERS).find((u) => u.id === userId)
    if (!found) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const { password: _p, ...user } = found
    return HttpResponse.json(user)
  }),

  // Projects
  http.get(`${BASE_URL}/projects`, ({ request }) => {
    const url = new URL(request.url)
    const ownerId = url.searchParams.get('ownerId')
    const memberId = url.searchParams.get('memberId')
    const status = url.searchParams.get('status')
    const q = url.searchParams.get('q')

    let items = [...projectStore]
    if (ownerId) items = items.filter((p) => p.ownerId === ownerId)
    if (memberId) items = items.filter((p) => p.memberIds.includes(memberId))
    if (status) items = items.filter((p) => p.status === status)
    if (q) items = items.filter((p) => p.name.toLowerCase().includes(q.toLowerCase()))

    const page: ProjectPage = {
      items,
      pageInfo: { nextCursor: null, hasMore: false },
    }
    return HttpResponse.json(page)
  }),

  http.get(`${BASE_URL}/projects/:projectId`, ({ params }) => {
    const project = projectStore.find((p) => p.id === params.projectId)
    if (!project) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    return HttpResponse.json(project)
  }),

  http.post(`${BASE_URL}/projects`, async ({ request }) => {
    const body = (await request.json()) as Partial<Project>
    const newProject: Project = {
      id: `project-${Date.now()}`,
      rootOrgId: 'org-fab-1',
      organizationId: body.organizationId ?? 'org-section-1',
      name: body.name ?? 'New Project',
      status: 'DRAFT',
      ownerId: body.ownerId ?? 'user-leader',
      memberIds: body.memberIds ?? [body.ownerId ?? 'user-leader'],
      groupIds: body.groupIds ?? ['group-1'],
      descriptionMarkdown: body.descriptionMarkdown,
      schedule: body.schedule,
      tags: body.tags ?? [],
      overdue: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    projectStore.push(newProject)
    return HttpResponse.json(newProject, { status: 201 })
  }),

  http.patch(`${BASE_URL}/projects/:projectId`, async ({ params, request }) => {
    const idx = projectStore.findIndex((p) => p.id === params.projectId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as Partial<Project>
    projectStore[idx] = { ...projectStore[idx], ...body, updatedAt: new Date().toISOString() }
    return HttpResponse.json(projectStore[idx])
  }),

  http.post(`${BASE_URL}/projects/:projectId/status`, async ({ params, request }) => {
    const idx = projectStore.findIndex((p) => p.id === params.projectId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as { status: string }
    projectStore[idx] = {
      ...projectStore[idx],
      status: body.status as Project['status'],
      updatedAt: new Date().toISOString(),
    }
    return HttpResponse.json(projectStore[idx])
  }),

  http.post(`${BASE_URL}/projects/:projectId/owner`, async ({ params, request }) => {
    const idx = projectStore.findIndex((p) => p.id === params.projectId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as { newOwnerId: string }
    projectStore[idx] = {
      ...projectStore[idx],
      ownerId: body.newOwnerId,
      updatedAt: new Date().toISOString(),
    }
    return HttpResponse.json(projectStore[idx])
  }),

  http.get(`${BASE_URL}/projects/:projectId/members`, ({ params }) => {
    const project = projectStore.find((p) => p.id === params.projectId)
    if (!project) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const members = project.memberIds.map((id) => ({ id: `membership-${id}`, userId: id, role: 'MEMBER', projectId: project.id, joinedAt: project.createdAt }))
    return HttpResponse.json(members)
  }),

  http.post(`${BASE_URL}/projects/:projectId/members`, async ({ params, request }) => {
    const idx = projectStore.findIndex((p) => p.id === params.projectId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as { userId: string }
    if (!projectStore[idx].memberIds.includes(body.userId)) {
      projectStore[idx].memberIds.push(body.userId)
    }
    return HttpResponse.json({ id: `m-${Date.now()}`, userId: body.userId, role: 'MEMBER' }, { status: 201 })
  }),

  http.delete(`${BASE_URL}/projects/:projectId/members/:userId`, ({ params }) => {
    const idx = projectStore.findIndex((p) => p.id === params.projectId)
    if (idx !== -1) {
      projectStore[idx].memberIds = projectStore[idx].memberIds.filter(
        (id) => id !== params.userId
      )
    }
    return new HttpResponse(null, { status: 204 })
  }),

  // Tasks
  http.get(`${BASE_URL}/tasks`, ({ request }) => {
    const url = new URL(request.url)
    const projectId = url.searchParams.get('projectId')
    const ownerId = url.searchParams.get('ownerId')
    const assigneeId = url.searchParams.get('assigneeId')
    const status = url.searchParams.get('status')
    const groupId = url.searchParams.get('groupId')
    const dueBefore = url.searchParams.get('dueBefore')

    let items = [...taskStore]
    if (projectId) items = items.filter((t) => t.projectId === projectId)
    if (ownerId) items = items.filter((t) => t.ownerId === ownerId)
    if (assigneeId) items = items.filter((t) => t.assignees.includes(assigneeId))
    if (status) items = items.filter((t) => t.status === status)
    if (groupId) {
      const projectsInGroup = projectStore.filter((p) => p.groupIds.includes(groupId))
      const projectIds = projectsInGroup.map((p) => p.id)
      items = items.filter((t) => projectIds.includes(t.projectId))
    }
    if (dueBefore) {
      items = items.filter((t) => {
        if (!t.schedule?.due) return false
        return new Date(t.schedule.due) < new Date(dueBefore)
      })
    }

    const page: TaskPage = {
      items,
      pageInfo: { nextCursor: null, hasMore: false },
    }
    return HttpResponse.json(page)
  }),

  http.get(`${BASE_URL}/tasks/:taskId`, ({ params }) => {
    const task = taskStore.find((t) => t.id === params.taskId)
    if (!task) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    return HttpResponse.json(task)
  }),

  http.post(`${BASE_URL}/tasks`, async ({ request }) => {
    const body = (await request.json()) as Partial<Task>
    const newTask: Task = {
      id: `task-${++nextTaskId}`,
      rootOrgId: 'org-fab-1',
      projectId: body.projectId ?? 'project-1',
      type: body.type ?? 'GENERAL',
      attributes: body.attributes ?? {},
      title: body.title ?? 'New Task',
      descriptionMarkdown: body.descriptionMarkdown,
      status: 'OPEN',
      priority: body.priority ?? 'NORMAL',
      ownerId: body.ownerId ?? 'user-leader',
      assignees: body.assignees ?? [body.ownerId ?? 'user-leader'],
      schedule: body.schedule,
      qaReviewPolicy: {
        dualSignRequired: false,
        requiredReviewerRoles: [],
        snapshotAt: new Date().toISOString(),
        sourceGroupIds: [],
      },
      qaReviews: [],
      tags: body.tags ?? [],
      overdue: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    taskStore.push(newTask)
    return HttpResponse.json(newTask, { status: 201 })
  }),

  http.patch(`${BASE_URL}/tasks/:taskId`, async ({ params, request }) => {
    const idx = taskStore.findIndex((t) => t.id === params.taskId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as Partial<Task>
    taskStore[idx] = { ...taskStore[idx], ...body, updatedAt: new Date().toISOString() }
    return HttpResponse.json(taskStore[idx])
  }),

  http.post(`${BASE_URL}/tasks/:taskId/status`, async ({ params, request }) => {
    const idx = taskStore.findIndex((t) => t.id === params.taskId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as { status: string }
    taskStore[idx] = {
      ...taskStore[idx],
      status: body.status as Task['status'],
      updatedAt: new Date().toISOString(),
    }
    return HttpResponse.json(taskStore[idx])
  }),

  http.post(`${BASE_URL}/tasks/:taskId/assignees`, async ({ params, request }) => {
    const idx = taskStore.findIndex((t) => t.id === params.taskId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as { userIds: string[] }
    const existing = taskStore[idx].assignees
    const merged = Array.from(new Set([...existing, ...body.userIds]))
    taskStore[idx] = { ...taskStore[idx], assignees: merged, updatedAt: new Date().toISOString() }
    return HttpResponse.json(taskStore[idx])
  }),

  http.delete(`${BASE_URL}/tasks/:taskId/assignees/:userId`, ({ params }) => {
    const idx = taskStore.findIndex((t) => t.id === params.taskId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    taskStore[idx] = {
      ...taskStore[idx],
      assignees: taskStore[idx].assignees.filter((id) => id !== params.userId),
      updatedAt: new Date().toISOString(),
    }
    return HttpResponse.json(taskStore[idx])
  }),

  http.post(`${BASE_URL}/tasks/:taskId/owner`, async ({ params, request }) => {
    const idx = taskStore.findIndex((t) => t.id === params.taskId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as { newOwnerId: string }
    const assignees = taskStore[idx].assignees
    if (!assignees.includes(body.newOwnerId)) {
      assignees.push(body.newOwnerId)
    }
    taskStore[idx] = {
      ...taskStore[idx],
      ownerId: body.newOwnerId,
      assignees,
      updatedAt: new Date().toISOString(),
    }
    return HttpResponse.json(taskStore[idx])
  }),

  http.post(`${BASE_URL}/tasks/:taskId/review`, async ({ params, request }) => {
    const idx = taskStore.findIndex((t) => t.id === params.taskId)
    if (idx === -1) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    const body = (await request.json()) as { decision: string; role: string; reason?: string }
    const review = {
      reviewerId: 'user-qa',
      reviewerRole: body.role as Role,
      decision: body.decision as ReviewDecision,
      reason: body.reason,
      at: new Date().toISOString(),
    }
    taskStore[idx] = {
      ...taskStore[idx],
      qaReviews: [...(taskStore[idx].qaReviews ?? []), review],
      updatedAt: new Date().toISOString(),
    }
    return HttpResponse.json(taskStore[idx])
  }),

  // Task comments
  http.get(`${BASE_URL}/tasks/:taskId/comments`, ({ params }) => {
    const comments = commentStore.filter((c) => c.taskId === params.taskId)
    return HttpResponse.json(comments)
  }),

  http.post(`${BASE_URL}/tasks/:taskId/comments`, async ({ params, request }) => {
    const body = (await request.json()) as { content: string }
    const comment: Comment = {
      id: `comment-${Date.now()}`,
      taskId: params.taskId as string,
      authorId: 'user-leader',
      content: body.content,
      createdAt: new Date().toISOString(),
    }
    commentStore.push(comment)
    return HttpResponse.json(comment, { status: 201 })
  }),

  // Task types
  http.get(`${BASE_URL}/task-types`, () => {
    return HttpResponse.json(TASK_TYPES)
  }),

  // Action requests
  http.get(`${BASE_URL}/action-requests`, () => {
    return HttpResponse.json({ items: [], pageInfo: { nextCursor: null, hasMore: false } })
  }),

  http.post(`${BASE_URL}/action-requests`, async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>
    return HttpResponse.json({ id: `ar-${Date.now()}`, status: 'SUBMITTED', ...body }, { status: 201 })
  }),

  // Organizations
  http.get(`${BASE_URL}/orgs`, () => {
    return HttpResponse.json({
      items: [
        { id: 'org-fab-1', rootOrgId: 'org-fab-1', type: 'FAB', name: '第一廠', code: 'fab-1', parentId: null, isLeaf: false, depth: 0, leaderIds: ['user-manager'], managerId: 'user-manager' },
        { id: 'org-div-1', rootOrgId: 'org-fab-1', type: 'DIVISION', name: '製造處', code: 'div-1', parentId: 'org-fab-1', isLeaf: false, depth: 1, leaderIds: [], managerId: null },
        { id: 'org-dept-1', rootOrgId: 'org-fab-1', type: 'DEPARTMENT', name: '生產部', code: 'dept-1', parentId: 'org-div-1', isLeaf: false, depth: 2, leaderIds: [], managerId: null },
        { id: 'org-section-1', rootOrgId: 'org-fab-1', type: 'SECTION', name: '一課', code: 'section-1', parentId: 'org-dept-1', isLeaf: true, depth: 3, leaderIds: ['user-leader'], managerId: 'user-leader' },
      ],
      pageInfo: { nextCursor: null, hasMore: false },
    })
  }),

  http.get(`${BASE_URL}/orgs/:orgId`, ({ params }) => {
    const orgMap: Record<string, object> = {
      'org-fab-1': { id: 'org-fab-1', rootOrgId: 'org-fab-1', type: 'FAB', name: '第一廠', code: 'fab-1', parentId: null, isLeaf: false, depth: 0 },
      'org-section-1': { id: 'org-section-1', rootOrgId: 'org-fab-1', type: 'SECTION', name: '一課', code: 'section-1', parentId: 'org-dept-1', isLeaf: true, depth: 3 },
    }
    const org = orgMap[params.orgId as string]
    if (!org) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    return HttpResponse.json(org)
  }),

  // Groups
  http.get(`${BASE_URL}/orgs/:orgId/groups`, () => {
    return HttpResponse.json({
      items: [
        { id: 'group-1', rootOrgId: 'org-fab-1', organizationId: 'org-section-1', type: 'SHIFT', name: '早班', code: 'shift-morning', leaderId: 'user-leader', settings: { qa: { dualSignRequired: true, requiredReviewerRoles: ['QA', 'SHIFT_LEAD'] } } },
        { id: 'group-2', rootOrgId: 'org-fab-1', organizationId: 'org-section-1', type: 'LINE', name: 'SMT 產線', code: 'smt-line', leaderId: 'user-qa', settings: { qa: { dualSignRequired: false, requiredReviewerRoles: [] } } },
      ],
      pageInfo: { nextCursor: null, hasMore: false },
    })
  }),

  http.get(`${BASE_URL}/orgs/:orgId/groups/:groupId`, ({ params }) => {
    const groups: Record<string, object> = {
      'group-1': { id: 'group-1', rootOrgId: 'org-fab-1', organizationId: 'org-section-1', type: 'SHIFT', name: '早班', code: 'shift-morning', leaderId: 'user-leader', settings: { qa: { dualSignRequired: true, requiredReviewerRoles: ['QA', 'SHIFT_LEAD'] } } },
      'group-2': { id: 'group-2', rootOrgId: 'org-fab-1', organizationId: 'org-section-1', type: 'LINE', name: 'SMT 產線', code: 'smt-line', leaderId: 'user-qa', settings: { qa: { dualSignRequired: false, requiredReviewerRoles: [] } } },
    }
    const group = groups[params.groupId as string]
    if (!group) return HttpResponse.json({ title: 'Not found', status: 404 }, { status: 404 })
    return HttpResponse.json(group)
  }),

  http.get(`${BASE_URL}/orgs/:orgId/groups/:groupId/members`, () => {
    const members = Object.values(SEED_USERS).map(({ password: _p, ...u }) => ({
      id: `gm-${u.id}`,
      rootOrgId: 'org-fab-1',
      groupId: 'group-1',
      userId: u.id,
      role: 'MEMBER',
      joinedAt: '2026-05-01T08:00:00+08:00',
    }))
    return HttpResponse.json(members)
  }),

  http.get(`${BASE_URL}/orgs/:orgId/groups/:groupId/settings`, ({ params }) => {
    const settings = {
      'group-1': { qa: { dualSignRequired: true, requiredReviewerRoles: ['QA', 'SHIFT_LEAD'] } },
      'group-2': { qa: { dualSignRequired: false, requiredReviewerRoles: [] } },
    }
    return HttpResponse.json(settings[params.groupId as keyof typeof settings] ?? { qa: { dualSignRequired: false, requiredReviewerRoles: [] } })
  }),

  http.get(`${BASE_URL}/orgs/:orgId/leaders`, () => {
    return HttpResponse.json([])
  }),

  // Templates
  http.get(`${BASE_URL}/orgs/:orgId/project-templates`, () => {
    return HttpResponse.json({ items: [], pageInfo: { nextCursor: null, hasMore: false } })
  }),

  http.get(`${BASE_URL}/system/project-templates`, () => {
    return HttpResponse.json({ items: [], pageInfo: { nextCursor: null, hasMore: false } })
  }),

  http.get(`${BASE_URL}/orgs/:orgId/task-templates`, () => {
    return HttpResponse.json({ items: [], pageInfo: { nextCursor: null, hasMore: false } })
  }),

  // Webhooks
  http.get(`${BASE_URL}/webhooks`, () => {
    return HttpResponse.json([])
  }),

  // Health
  http.get(`${BASE_URL}/health`, () => {
    return HttpResponse.json({ status: 'UP' })
  }),
]
