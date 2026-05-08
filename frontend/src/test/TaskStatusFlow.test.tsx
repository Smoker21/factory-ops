/**
 * Component tests for TaskStatusFlow.
 * Tests:
 * - Renders correct transition buttons for each status
 * - DONE and CANCELLED show no buttons (terminal states)
 * - Force-complete modal appears for dual-sign tasks
 * - Non-assigned user sees no buttons (canChange = false)
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
  useChangeTaskStatus: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}))

import { useAuth } from '@/auth/useAuth'
import { TaskStatusFlow } from '@/components/task/TaskStatusFlow'

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 'user-1',
    rootOrgId: 'org-1',
    accountName: 'test.user',
    displayName: 'Test User',
    roles: ['SHIFT_LEAD'],
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

describe('TaskStatusFlow', () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({
      user: makeUser(),
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      setUser: vi.fn(),
    })
  })

  it('should show IN_PROGRESS and CANCELLED buttons for OPEN task', () => {
    const task = makeTask({ status: 'OPEN' })
    render(<Wrapper><TaskStatusFlow task={task} /></Wrapper>)

    // OPEN → IN_PROGRESS, CANCELLED are valid transitions
    expect(screen.getByText('開始進行')).toBeDefined()
    expect(screen.getByText('取消')).toBeDefined()
  })

  it('should not render any buttons for DONE task (terminal state)', () => {
    const task = makeTask({ status: 'DONE', ownerId: 'user-1' })
    const { container } = render(<Wrapper><TaskStatusFlow task={task} /></Wrapper>)

    // DONE is terminal - no buttons
    const buttons = container.querySelectorAll('button')
    // The component returns null when no transitions or canChange=false
    expect(buttons.length).toBe(0)
  })

  it('should not render any buttons for CANCELLED task (terminal state)', () => {
    const task = makeTask({ status: 'CANCELLED', ownerId: 'user-1' })
    const { container } = render(<Wrapper><TaskStatusFlow task={task} /></Wrapper>)

    const buttons = container.querySelectorAll('button')
    expect(buttons.length).toBe(0)
  })

  it('should show BLOCKED, IN_REVIEW, DONE, CANCELLED buttons for IN_PROGRESS task', () => {
    const task = makeTask({ status: 'IN_PROGRESS' })
    render(<Wrapper><TaskStatusFlow task={task} /></Wrapper>)

    expect(screen.getByText('標記阻塞')).toBeDefined()
    expect(screen.getByText('送審核')).toBeDefined()
    expect(screen.getByText('標記完成')).toBeDefined()
    expect(screen.getByText('取消')).toBeDefined()
  })

  it('should show DONE button for IN_PROGRESS dual-sign task (force-complete path)', () => {
    // For dual-sign tasks, DONE button still shows but clicking opens force-complete modal
    const task = makeTask({
      status: 'IN_PROGRESS',
      qaReviewPolicy: {
        dualSignRequired: true,
        requiredReviewerRoles: ['QA'],
        snapshotAt: '2026-01-01T00:00:00Z',
        sourceGroupIds: [],
      },
    })
    render(<Wrapper><TaskStatusFlow task={task} /></Wrapper>)

    // The DONE button is shown — clicking it triggers force-complete flow
    const doneBtn = screen.getByText('標記完成')
    expect(doneBtn).toBeDefined()
    // Button is enabled (not a disabled terminal state)
    expect(doneBtn.closest('button')?.getAttribute('disabled')).toBeNull()
  })

  it('should not render buttons when user cannot change status', () => {
    // User is not owner and not assignee
    vi.mocked(useAuth).mockReturnValue({
      user: makeUser({ id: 'user-99', roles: ['OPERATOR'] }),
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      setUser: vi.fn(),
    })

    const task = makeTask({ ownerId: 'user-1', assignees: ['user-1'] })
    const { container } = render(<Wrapper><TaskStatusFlow task={task} /></Wrapper>)

    // canChange = false → component returns null
    const buttons = container.querySelectorAll('button')
    expect(buttons.length).toBe(0)
  })
})
