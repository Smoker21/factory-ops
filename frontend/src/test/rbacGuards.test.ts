/**
 * Unit tests for RBAC guard functions.
 * These are pure functions: user + task → boolean.
 */
import { describe, it, expect } from 'vitest'
import {
  hasRole,
  hasAnyRole,
  canTransferTaskOwner,
  canChangeTaskStatus,
  canReviewTask,
  canCreateTask,
  isOrgManager,
} from '@/rbac/rbacGuards'
import type { User, Task } from '@/api/types'

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 'user-1',
    rootOrgId: 'org-1',
    accountName: 'test.user',
    displayName: 'Test User',
    roles: ['OPERATOR'],
    groupIds: [],
    orgManagerScopes: [],
    active: true,
    ...overrides,
  }
}

function makeTask(overrides: Partial<Task> = {}): Task {
  return {
    id: 'task-1',
    rootOrgId: 'org-1',
    projectId: 'proj-1',
    type: 'GENERAL',
    attributes: {},
    title: 'Test Task',
    status: 'OPEN',
    priority: 'NORMAL',
    ownerId: 'user-1',
    assignees: ['user-1'],
    qaReviewPolicy: {
      dualSignRequired: false,
      requiredReviewerRoles: [],
      snapshotAt: '2026-01-01T00:00:00Z',
      sourceGroupIds: [],
    },
    qaReviews: [],
    tags: [],
    overdue: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('hasRole', () => {
  it('should return true when user has the role', () => {
    const user = makeUser({ roles: ['QA', 'SHIFT_LEAD'] })
    expect(hasRole(user, 'QA')).toBe(true)
  })

  it('should return false when user does not have the role', () => {
    const user = makeUser({ roles: ['OPERATOR'] })
    expect(hasRole(user, 'ADMIN')).toBe(false)
  })

  it('should return false for null user', () => {
    expect(hasRole(null, 'OPERATOR')).toBe(false)
  })
})

describe('hasAnyRole', () => {
  it('should return true if user has at least one role from list', () => {
    const user = makeUser({ roles: ['SHIFT_LEAD'] })
    expect(hasAnyRole(user, ['OPERATOR', 'SHIFT_LEAD', 'QA'])).toBe(true)
  })

  it('should return false if user has none of the roles', () => {
    const user = makeUser({ roles: ['OPERATOR'] })
    expect(hasAnyRole(user, ['ADMIN', 'ORG_ADMIN'])).toBe(false)
  })
})

describe('canTransferTaskOwner', () => {
  it('should allow task owner to transfer ownership', () => {
    const user = makeUser({ id: 'user-1', roles: ['OPERATOR'] })
    const task = makeTask({ ownerId: 'user-1' })
    expect(canTransferTaskOwner(user, task)).toBe(true)
  })

  it('should allow GROUP_MANAGER to transfer any task ownership', () => {
    const user = makeUser({ id: 'user-2', roles: ['GROUP_MANAGER'] })
    const task = makeTask({ ownerId: 'user-1' })
    expect(canTransferTaskOwner(user, task)).toBe(true)
  })

  it('should deny non-owner OPERATOR from transferring ownership', () => {
    const user = makeUser({ id: 'user-2', roles: ['OPERATOR'] })
    const task = makeTask({ ownerId: 'user-1' })
    expect(canTransferTaskOwner(user, task)).toBe(false)
  })

  it('should return false for null user', () => {
    const task = makeTask()
    expect(canTransferTaskOwner(null, task)).toBe(false)
  })
})

describe('canChangeTaskStatus', () => {
  it('should allow task owner to change status', () => {
    const user = makeUser({ id: 'user-1', roles: ['OPERATOR'] })
    const task = makeTask({ ownerId: 'user-1' })
    expect(canChangeTaskStatus(user, task)).toBe(true)
  })

  it('should allow task assignee to change status', () => {
    const user = makeUser({ id: 'user-3', roles: ['OPERATOR'] })
    const task = makeTask({ ownerId: 'user-1', assignees: ['user-1', 'user-3'] })
    expect(canChangeTaskStatus(user, task)).toBe(true)
  })

  it('should deny user who is neither owner nor assignee', () => {
    const user = makeUser({ id: 'user-99', roles: ['OPERATOR'] })
    const task = makeTask({ ownerId: 'user-1', assignees: ['user-1'] })
    expect(canChangeTaskStatus(user, task)).toBe(false)
  })

  it('should allow ADMIN to change any task status', () => {
    const user = makeUser({ id: 'user-admin', roles: ['ADMIN'] })
    const task = makeTask({ ownerId: 'user-1' })
    expect(canChangeTaskStatus(user, task)).toBe(true)
  })
})

describe('canReviewTask', () => {
  it('should allow user with required reviewer role to review', () => {
    const user = makeUser({ id: 'user-qa', roles: ['QA'] })
    const task = makeTask({
      qaReviewPolicy: {
        dualSignRequired: true,
        requiredReviewerRoles: ['QA', 'SHIFT_LEAD'],
        snapshotAt: '2026-01-01T00:00:00Z',
        sourceGroupIds: [],
      },
    })
    expect(canReviewTask(user, task)).toBe(true)
  })

  it('should deny user without required reviewer role', () => {
    const user = makeUser({ id: 'user-op', roles: ['OPERATOR'] })
    const task = makeTask({
      qaReviewPolicy: {
        dualSignRequired: true,
        requiredReviewerRoles: ['QA'],
        snapshotAt: '2026-01-01T00:00:00Z',
        sourceGroupIds: [],
      },
    })
    expect(canReviewTask(user, task)).toBe(false)
  })

  it('should deny review when task has no required roles', () => {
    const user = makeUser({ roles: ['QA'] })
    const task = makeTask({
      qaReviewPolicy: {
        dualSignRequired: false,
        requiredReviewerRoles: [],
        snapshotAt: '2026-01-01T00:00:00Z',
        sourceGroupIds: [],
      },
    })
    expect(canReviewTask(user, task)).toBe(false)
  })
})

describe('isOrgManager', () => {
  it('should return true when user has orgManagerScopes', () => {
    const user = makeUser({ orgManagerScopes: ['org-dept-1'] })
    expect(isOrgManager(user)).toBe(true)
  })

  it('should return true for ORG_ADMIN', () => {
    const user = makeUser({ roles: ['ORG_ADMIN'], orgManagerScopes: [] })
    expect(isOrgManager(user)).toBe(true)
  })

  it('should return false for regular OPERATOR with no scopes', () => {
    const user = makeUser({ roles: ['OPERATOR'], orgManagerScopes: [] })
    expect(isOrgManager(user)).toBe(false)
  })
})

describe('canCreateTask', () => {
  it('should allow SHIFT_LEAD to create tasks', () => {
    const user = makeUser({ roles: ['SHIFT_LEAD'] })
    expect(canCreateTask(user)).toBe(true)
  })

  it('should allow ENGINEER to create tasks', () => {
    const user = makeUser({ roles: ['ENGINEER'] })
    expect(canCreateTask(user)).toBe(true)
  })

  it('should deny OPERATOR from creating tasks', () => {
    const user = makeUser({ roles: ['OPERATOR'] })
    expect(canCreateTask(user)).toBe(false)
  })
})
