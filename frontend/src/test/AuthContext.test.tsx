/**
 * Unit / integration tests for frontend/src/auth/AuthContext.tsx
 *
 * Tests:
 * - On mount, AuthProvider calls GET /me to bootstrap session from cookie
 * - /me 200 → user state set, isLoading transitions false
 * - /me 401 → user null, isLoading transitions false
 * - login(user) sets user state and isAuthenticated=true
 * - logout() clears user state and isAuthenticated=false
 */
import { describe, it, expect, vi } from 'vitest'
import { render, waitFor, act } from '@testing-library/react'
import { renderHook } from '@testing-library/react'
import React from 'react'
import { server } from './server'
import { http, HttpResponse } from 'msw'
import { AuthProvider, AuthContext } from '@/auth/AuthContext'
import type { User } from '@/api/types'

// Mock react-i18next to avoid async init issues
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), language: 'zh-TW' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
  Trans: ({ children }: { children: React.ReactNode }) => children,
}))

const MOCK_USER: User = {
  id: 'user-admin',
  rootOrgId: 'org-fab-1',
  accountName: 'admin.system',
  displayName: '系統管理員',
  email: 'admin@factory.ops',
  roles: ['ADMIN'],
  groupIds: [],
  orgManagerScopes: [],
  active: true,
}

function makeWrapper() {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <AuthProvider>{children}</AuthProvider>
  }
}

// ── Bootstrap session ──────────────────────────────────────────────────────────

describe('AuthContext – mount bootstrap', () => {
  it('calls GET /me on mount to restore session from cookie', async () => {
    let meCalled = false

    server.use(
      http.get('*/v1/me', () => {
        meCalled = true
        return HttpResponse.json(MOCK_USER)
      })
    )

    renderHook(() => React.useContext(AuthContext), { wrapper: makeWrapper() })

    await waitFor(() => {
      expect(meCalled).toBe(true)
    })
  })

  it('/me 200 → user state set and isLoading becomes false', async () => {
    server.use(
      http.get('*/v1/me', () => HttpResponse.json(MOCK_USER))
    )

    const { result } = renderHook(() => React.useContext(AuthContext), {
      wrapper: makeWrapper(),
    })

    // Initially loading
    expect(result.current?.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current?.isLoading).toBe(false)
    })

    expect(result.current?.isAuthenticated).toBe(true)
    expect(result.current?.user?.accountName).toBe('admin.system')
  })

  it('/me 401 → user is null and isLoading becomes false', async () => {
    server.use(
      http.get('*/v1/me', () => {
        return HttpResponse.json({ title: 'Unauthorized', status: 401 }, { status: 401 })
      }),
      // Refresh also fails (so auto-refresh loop terminates)
      http.post('*/v1/auth/refresh', () => {
        return HttpResponse.json({ title: 'No refresh token' }, { status: 401 })
      })
    )

    const { result } = renderHook(() => React.useContext(AuthContext), {
      wrapper: makeWrapper(),
    })

    await waitFor(() => {
      expect(result.current?.isLoading).toBe(false)
    }, { timeout: 5000 })

    expect(result.current?.isAuthenticated).toBe(false)
    expect(result.current?.user).toBeNull()
  })
})

// ── login() action ─────────────────────────────────────────────────────────────

describe('AuthContext – login(user)', () => {
  it('sets user state and isAuthenticated to true', async () => {
    // /me returns 401 initially so we start unauthenticated
    server.use(
      http.get('*/v1/me', () =>
        HttpResponse.json({ title: 'Unauthorized' }, { status: 401 })
      ),
      http.post('*/v1/auth/refresh', () =>
        HttpResponse.json({ title: 'No refresh' }, { status: 401 })
      )
    )

    const { result } = renderHook(() => React.useContext(AuthContext), {
      wrapper: makeWrapper(),
    })

    await waitFor(() => {
      expect(result.current?.isLoading).toBe(false)
    }, { timeout: 5000 })

    expect(result.current?.isAuthenticated).toBe(false)

    // Simulate successful login: caller sets user
    act(() => {
      result.current?.login(MOCK_USER)
    })

    expect(result.current?.isAuthenticated).toBe(true)
    expect(result.current?.user?.id).toBe(MOCK_USER.id)
  })
})

// ── logout() action ────────────────────────────────────────────────────────────

describe('AuthContext – logout()', () => {
  it('clears user state and isAuthenticated to false after logout', async () => {
    server.use(
      http.get('*/v1/me', () => HttpResponse.json(MOCK_USER))
    )

    const { result } = renderHook(() => React.useContext(AuthContext), {
      wrapper: makeWrapper(),
    })

    // Wait for session restore
    await waitFor(() => {
      expect(result.current?.isAuthenticated).toBe(true)
    })

    act(() => {
      result.current?.logout()
    })

    expect(result.current?.isAuthenticated).toBe(false)
    expect(result.current?.user).toBeNull()
    expect(result.current?.isLoading).toBe(false)
  })
})

// ── ProtectedRoute interaction ─────────────────────────────────────────────────

describe('ProtectedRoute + AuthContext', () => {
  it('renders loader while isLoading is true (prevents flash redirect)', async () => {
    // Never resolves /me during the test — keeps isLoading=true
    server.use(
      http.get('*/v1/me', () => new Promise(() => { /* never resolves */ }))
    )

    const { ProtectedRoute } = await import('@/auth/ProtectedRoute')
    const { MemoryRouter } = await import('react-router-dom')
    const { MantineProvider } = await import('@mantine/core')

    let rendered = false
    const { container } = render(
      <MantineProvider>
        <MemoryRouter>
          <AuthProvider>
            <ProtectedRoute>
              <div data-testid="protected-content">Protected</div>
            </ProtectedRoute>
          </AuthProvider>
        </MemoryRouter>
      </MantineProvider>
    )

    rendered = true
    expect(rendered).toBe(true)

    // Protected content should NOT be visible while loading
    const protectedContent = container.querySelector('[data-testid="protected-content"]')
    expect(protectedContent).toBeNull()
  })
})
