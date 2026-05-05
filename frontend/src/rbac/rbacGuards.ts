import type { User, Task, Project, Role } from '@/api/types'

// NOTE: Front-end RBAC is UX-only. Server enforces the real authorization.
// These guards are used to show/hide UI elements to improve UX.

export function hasRole(user: User | null, role: Role): boolean {
  if (!user) return false
  return user.roles.includes(role)
}

export function hasAnyRole(user: User | null, roles: Role[]): boolean {
  if (!user) return false
  return roles.some((r) => user.roles.includes(r))
}

export function isAdmin(user: User | null): boolean {
  return hasRole(user, 'ADMIN')
}

export function isOrgAdmin(user: User | null): boolean {
  return hasAnyRole(user, ['ORG_ADMIN', 'ADMIN'])
}

export function isGroupManager(user: User | null): boolean {
  return hasAnyRole(user, ['GROUP_MANAGER', 'GROUP_ADMIN', 'ORG_ADMIN', 'ADMIN'])
}

export function isOrgManager(user: User | null): boolean {
  if (!user) return false
  return (user.orgManagerScopes?.length ?? 0) > 0 || hasAnyRole(user, ['ORG_ADMIN', 'ADMIN'])
}

export function canCreateProject(user: User | null): boolean {
  return hasAnyRole(user, ['GROUP_MANAGER', 'GROUP_ADMIN', 'ORG_ADMIN', 'ADMIN', 'SHIFT_LEAD'])
}

export function canCreateTask(user: User | null): boolean {
  return hasAnyRole(user, [
    'GROUP_MANAGER',
    'GROUP_ADMIN',
    'SHIFT_LEAD',
    'ENGINEER',
    'QA',
    'ORG_ADMIN',
    'ADMIN',
  ])
}

export function canEditTask(user: User | null, task: Task): boolean {
  if (!user) return false
  if (hasAnyRole(user, ['GROUP_MANAGER', 'ORG_ADMIN', 'ADMIN'])) return true
  return task.ownerId === user.id || task.assignees.includes(user.id)
}

export function canTransferTaskOwner(user: User | null, task: Task): boolean {
  if (!user) return false
  if (hasAnyRole(user, ['GROUP_MANAGER', 'ORG_ADMIN', 'ADMIN'])) return true
  return task.ownerId === user.id
}

export function canChangeTaskStatus(user: User | null, task: Task): boolean {
  if (!user) return false
  if (hasAnyRole(user, ['GROUP_MANAGER', 'ORG_ADMIN', 'ADMIN'])) return true
  return task.ownerId === user.id || task.assignees.includes(user.id)
}

export function canDeleteTask(user: User | null, task: Task): boolean {
  if (!user) return false
  if (hasAnyRole(user, ['GROUP_MANAGER', 'ORG_ADMIN', 'ADMIN'])) return true
  return task.ownerId === user.id
}

export function canEditProject(user: User | null, project: Project): boolean {
  if (!user) return false
  if (hasAnyRole(user, ['GROUP_MANAGER', 'ORG_ADMIN', 'ADMIN'])) return true
  return project.ownerId === user.id
}

export function canDispatchActionRequest(user: User | null): boolean {
  return isOrgManager(user)
}

export function canReviewTask(user: User | null, task: Task): boolean {
  if (!user) return false
  const requiredRoles = task.qaReviewPolicy?.requiredReviewerRoles ?? []
  return requiredRoles.some((role) => user.roles.includes(role as Role))
}

export function canManageGroupSettings(user: User | null): boolean {
  return hasAnyRole(user, ['GROUP_MANAGER', 'GROUP_ADMIN', 'ORG_ADMIN', 'ADMIN'])
}

export function canManageTemplates(user: User | null): boolean {
  return hasAnyRole(user, [
    'GROUP_MANAGER',
    'GROUP_ADMIN',
    'ORG_ADMIN',
    'ADMIN',
    'SHIFT_LEAD',
  ])
}

export function canViewWebhooks(user: User | null): boolean {
  return isOrgAdmin(user)
}
