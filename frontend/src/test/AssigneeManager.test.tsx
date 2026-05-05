/**
 * Component tests for AssigneeManager.
 * Tests:
 * - Owner is shown with "負責人" badge
 * - Remove button for owner is disabled (cannot remove owner)
 * - Transfer button shows only when canTransfer is true
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MantineProvider } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import React from 'react'
import type { Task, User } from '@/api/types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), language: 'zh-TW' },
  }),
}))

vi.mock('@/auth/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/hooks/useTasks', () => ({
  useAddTaskAssignees: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useRemoveTaskAssignee: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useTransferTaskOwner: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>()
  return {
    ...actual,
    useQuery: vi.fn(() => ({ data: { items: [] } })),
  }
})

import { useAuth } from '@/auth/useAuth'
import { AssigneeManager } from '@/components/task/AssigneeManager'

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 'user-1',
    rootOrgId: 'org-1',
    accountName: 'test.user',
    displayName: 'Test User',
    roles: ['GROUP_MANAGER'],
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
    status: 'IN_PROGRESS',
    priority: 'NORMAL',
    ownerId: 'user-1',
    assignees: ['user-1', 'user-2'],
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

function Wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <MantineProvider>
      <Notifications />
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>
  )
}

describe('AssigneeManager', () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({
      user: makeUser(),
      accessToken: 'fake-token',
      refreshToken: 'fake-refresh',
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      setUser: vi.fn(),
      updateTokens: vi.fn(),
    })
  })

  it('should show "負責人" badge for the task owner', () => {
    const task = makeTask({ ownerId: 'user-1', assignees: ['user-1', 'user-2'] })
    render(<Wrapper><AssigneeManager task={task} /></Wrapper>)

    expect(screen.getByText('負責人')).toBeDefined()
  })

  it('should show add assignee button', () => {
    const task = makeTask()
    render(<Wrapper><AssigneeManager task={task} /></Wrapper>)

    expect(screen.getByText('+ task.addAssignee')).toBeDefined()
  })

  it('should show transfer owner button for GROUP_MANAGER', () => {
    const task = makeTask()
    render(<Wrapper><AssigneeManager task={task} /></Wrapper>)

    expect(screen.getByText('task.transferOwner')).toBeDefined()
  })

  it('should not show transfer owner button for OPERATOR who is not owner', () => {
    vi.mocked(useAuth).mockReturnValue({
      user: makeUser({ id: 'user-99', roles: ['OPERATOR'] }),
      accessToken: 'fake-token',
      refreshToken: 'fake-refresh',
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      setUser: vi.fn(),
      updateTokens: vi.fn(),
    })

    const task = makeTask({ ownerId: 'user-1' })
    render(<Wrapper><AssigneeManager task={task} /></Wrapper>)

    // canTransfer = false for non-owner OPERATOR
    const transferBtn = screen.queryByText('task.transferOwner')
    expect(transferBtn).toBeNull()
  })

  it('should disable remove button for owner assignee (INV-1 guard)', () => {
    const task = makeTask({ ownerId: 'user-1', assignees: ['user-1', 'user-2'] })
    render(<Wrapper><AssigneeManager task={task} /></Wrapper>)

    // The owner's remove button should be disabled
    const removeButtons = screen.getAllByRole('button', { name: /×/i })
    // First remove button (for user-1, who is owner) should be disabled
    const ownerRemoveBtn = removeButtons[0]
    expect(ownerRemoveBtn.hasAttribute('disabled') || ownerRemoveBtn.getAttribute('data-disabled') === 'true').toBe(true)
  })
})
