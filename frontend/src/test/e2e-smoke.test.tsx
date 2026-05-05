import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MantineProvider } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import React from 'react'

// Mock i18n to avoid async initialization issues in tests
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), language: 'zh-TW' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
  Trans: ({ children }: { children: React.ReactNode }) => children,
}))

import { AuthProvider } from '@/auth/AuthContext'
import { LoginPage } from '@/routes/LoginPage'
import { login } from '@/api/auth'
import { getMe } from '@/api/users'
import type { TokenPair } from '@/api/types'

const ACCESS_TOKEN_KEY = 'factory_ops_access_token'

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

function Wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = createTestQueryClient()
  return (
    <MantineProvider>
      <Notifications />
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <MemoryRouter>{children}</MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </MantineProvider>
  )
}

describe('Auth Smoke Tests', () => {
  it('renders login page with account and password fields', async () => {
    render(
      <Wrapper>
        <LoginPage />
      </Wrapper>
    )

    await waitFor(() => {
      expect(screen.getByPlaceholderText('accountName')).toBeDefined()
      expect(screen.getByPlaceholderText('••••••••')).toBeDefined()
    })
  })

  it('shows error on invalid credentials', async () => {
    render(
      <Wrapper>
        <LoginPage />
      </Wrapper>
    )

    await waitFor(() => {
      expect(screen.getByPlaceholderText('accountName')).toBeDefined()
    })

    const accountInput = screen.getByPlaceholderText('accountName')
    const passwordInput = screen.getByPlaceholderText('••••••••')

    fireEvent.change(accountInput, { target: { value: 'wrong.user' } })
    fireEvent.change(passwordInput, { target: { value: 'WrongPassword123' } })
    fireEvent.submit(accountInput.closest('form')!)

    await waitFor(() => {
      const alerts = document.querySelectorAll('[role="alert"]')
      expect(alerts.length).toBeGreaterThan(0)
    }, { timeout: 3000 })
  })
})

describe('API Integration Smoke Tests', () => {
  let tokens: TokenPair

  beforeEach(async () => {
    tokens = await login({ accountName: 'leader.chen', password: 'Leader@123456789' })
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  })

  it('can call login API with valid credentials', async () => {
    const result = await login({ accountName: 'leader.chen', password: 'Leader@123456789' })
    expect(result.accessToken).toBeTruthy()
    expect(result.refreshToken).toBeTruthy()
    expect(result.tokenType).toBe('Bearer')
  })

  it('can get me after setting auth token', async () => {
    const user = await getMe()
    expect(user.accountName).toBe('leader.chen')
    expect(user.displayName).toBeTruthy()
  })

  it('can list projects', async () => {
    const { listProjects } = await import('@/api/projects')
    const page = await listProjects()
    expect(page.items).toBeDefined()
    expect(Array.isArray(page.items)).toBe(true)
    expect(page.items.length).toBeGreaterThan(0)
  })

  it('can list tasks for a project', async () => {
    const { listTasks } = await import('@/api/tasks')
    const page = await listTasks({ projectId: 'project-1' })
    expect(page.items).toBeDefined()
    expect(page.items.length).toBeGreaterThan(0)
  })

  it('can get a single task', async () => {
    const { getTask } = await import('@/api/tasks')
    const task = await getTask('task-1')
    expect(task.id).toBe('task-1')
    expect(task.title).toBeTruthy()
    expect(task.qaReviewPolicy).toBeDefined()
  })

  it('can change task status', async () => {
    const { changeTaskStatus } = await import('@/api/tasks')
    const task = await changeTaskStatus('task-2', 'IN_PROGRESS')
    expect(task.status).toBe('IN_PROGRESS')
  })

  it('can add assignees to task', async () => {
    const { addTaskAssignees } = await import('@/api/tasks')
    const task = await addTaskAssignees('task-1', ['user-manager'])
    expect(task.assignees).toContain('user-manager')
  })

  it('can transfer task owner to existing assignee', async () => {
    const { transferTaskOwner } = await import('@/api/tasks')
    // task-1 already has user-leader and user-operator as assignees
    const task = await transferTaskOwner('task-1', 'user-operator')
    expect(task.ownerId).toBe('user-operator')
  })

  it('can complete smoke path: login -> projects -> tasks -> status change', async () => {
    const { listProjects } = await import('@/api/projects')
    const { listTasks, changeTaskStatus } = await import('@/api/tasks')

    // Step 1: list projects
    const projectsPage = await listProjects()
    expect(projectsPage.items.length).toBeGreaterThan(0)
    const firstProject = projectsPage.items[0]

    // Step 2: list tasks for first project
    const tasksPage = await listTasks({ projectId: firstProject.id })
    expect(tasksPage.items.length).toBeGreaterThan(0)

    // Step 3: change status of first task
    const firstTask = tasksPage.items[0]
    if (firstTask.status === 'OPEN') {
      const updated = await changeTaskStatus(firstTask.id, 'IN_PROGRESS')
      expect(updated.status).toBe('IN_PROGRESS')
    } else {
      expect(firstTask.status).toBeTruthy()
    }
  })
})
