/**
 * Unit tests for frontend/src/api/client.ts
 *
 * Tests the cookie-aware axios instance behavior:
 * - withCredentials is set to true
 * - CSRF echo interceptor (GET/HEAD exempt; POST/PATCH/PUT/DELETE echo X-XSRF-TOKEN)
 * - 401 response interceptor: refresh → retry; refresh failure → redirectToLogin guard
 * - Refresh de-dupe: concurrent 401s trigger exactly one refresh request
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { server } from './server'
import { http, HttpResponse } from 'msw'

// ── helpers ────────────────────────────────────────────────────────────────────

/** Inject a raw cookie string into jsdom document.cookie */
function setDocumentCookie(value: string) {
  Object.defineProperty(document, 'cookie', {
    writable: true,
    configurable: true,
    value,
  })
}

/** Restore document.cookie to normal writable state */
function resetDocumentCookie() {
  Object.defineProperty(document, 'cookie', {
    writable: true,
    configurable: true,
    value: '',
  })
}

// ── withCredentials ────────────────────────────────────────────────────────────

describe('apiClient – withCredentials', () => {
  it('has withCredentials set to true on the axios instance', async () => {
    // Lazy import so interceptors re-run per suite
    const { apiClient } = await import('@/api/client')
    expect((apiClient.defaults as { withCredentials: boolean }).withCredentials).toBe(true)
  })
})

// ── CSRF echo interceptor ──────────────────────────────────────────────────────

describe('apiClient – CSRF echo interceptor', () => {
  beforeEach(() => {
    // Reset cookie before each test
    resetDocumentCookie()
  })

  afterEach(() => {
    resetDocumentCookie()
    vi.restoreAllMocks()
  })

  it('does NOT add X-XSRF-TOKEN header for GET requests', async () => {
    setDocumentCookie('XSRF-TOKEN=test-csrf-token')

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.get('*/v1/health', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return HttpResponse.json({ status: 'UP' })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.get('/health').catch(() => null)

    expect(capturedHeaders['x-xsrf-token']).toBeUndefined()
  })

  it('does NOT add X-XSRF-TOKEN header for HEAD requests', async () => {
    setDocumentCookie('XSRF-TOKEN=test-csrf-token')

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.head('*/v1/health', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return new HttpResponse(null, { status: 200 })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.head('/health').catch(() => null)

    expect(capturedHeaders['x-xsrf-token']).toBeUndefined()
  })

  it('adds X-XSRF-TOKEN header for POST requests when cookie is present', async () => {
    setDocumentCookie('XSRF-TOKEN=my-csrf-value')

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.post('*/v1/projects', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return HttpResponse.json({ id: 'p1' }, { status: 201 })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.post('/projects', {}).catch(() => null)

    expect(capturedHeaders['x-xsrf-token']).toBe('my-csrf-value')
  })

  it('adds X-XSRF-TOKEN header for PATCH requests when cookie is present', async () => {
    setDocumentCookie('XSRF-TOKEN=patch-csrf')

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.patch('*/v1/tasks/task-1', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return HttpResponse.json({ id: 'task-1' })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.patch('/tasks/task-1', {}).catch(() => null)

    expect(capturedHeaders['x-xsrf-token']).toBe('patch-csrf')
  })

  it('adds X-XSRF-TOKEN header for PUT requests when cookie is present', async () => {
    setDocumentCookie('XSRF-TOKEN=put-csrf')

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.put('*/v1/auth/password', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return new HttpResponse(null, { status: 204 })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.put('/auth/password', {}).catch(() => null)

    expect(capturedHeaders['x-xsrf-token']).toBe('put-csrf')
  })

  it('adds X-XSRF-TOKEN header for DELETE requests when cookie is present', async () => {
    setDocumentCookie('XSRF-TOKEN=delete-csrf')

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.delete('*/v1/tasks/task-1/assignees/user-1', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return new HttpResponse(null, { status: 204 })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.delete('/tasks/task-1/assignees/user-1').catch(() => null)

    expect(capturedHeaders['x-xsrf-token']).toBe('delete-csrf')
  })

  it('does NOT add X-XSRF-TOKEN header for POST when cookie is absent', async () => {
    // No cookie set — getCookie returns null
    resetDocumentCookie()

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.post('*/v1/projects', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return HttpResponse.json({ id: 'p1' }, { status: 201 })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.post('/projects', {}).catch(() => null)

    // Header must be absent — sending an empty string would be wrong
    const val = capturedHeaders['x-xsrf-token']
    expect(val === undefined || val === null).toBe(true)
  })

  it('correctly decodes URL-encoded XSRF-TOKEN cookie value', async () => {
    // Value with a space: "csrf token" URL-encoded = "csrf%20token"
    setDocumentCookie('XSRF-TOKEN=csrf%20token')

    const capturedHeaders: Record<string, string> = {}
    server.use(
      http.post('*/v1/projects', ({ request }) => {
        request.headers.forEach((v, k) => { capturedHeaders[k] = v })
        return HttpResponse.json({ id: 'p1' }, { status: 201 })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.post('/projects', {}).catch(() => null)

    expect(capturedHeaders['x-xsrf-token']).toBe('csrf token')
  })
})

// ── 401 response interceptor – refresh logic ──────────────────────────────────

describe('apiClient – 401 interceptor', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    resetDocumentCookie()
  })

  it('retries original request after a successful refresh', async () => {
    let projectCallCount = 0
    let refreshCallCount = 0

    server.use(
      // First projects call returns 401; second (retry) returns 200
      http.get('*/v1/projects', () => {
        projectCallCount++
        if (projectCallCount === 1) {
          return HttpResponse.json({ title: 'Unauthorized' }, { status: 401 })
        }
        return HttpResponse.json({ items: [], pageInfo: { nextCursor: null, hasMore: false } })
      }),
      // Refresh succeeds
      http.post('*/v1/auth/refresh', () => {
        refreshCallCount++
        return HttpResponse.json({ accessToken: 'new-token' })
      })
    )

    const { apiClient } = await import('@/api/client')
    const response = await apiClient.get('/projects')

    expect(refreshCallCount).toBe(1)
    expect(projectCallCount).toBe(2)
    expect(response.status).toBe(200)
  })

  it('rejects caller request and fires exactly one refresh when refresh fails (redirect tested by BDD)', async () => {
    // The redirectToLogin() guard in client.ts sets window.location.href = '/login'.
    // jsdom cannot assert href setter without "Not implemented: navigation" complexity.
    // This test covers the NO-INFINITE-RETRY and REQUEST-REJECTION contracts.
    // The actual /login redirect is verified in BDD auth.feature "登出後 reload 不再登入"
    // and the "refresh token 也失效時被導回登入頁" RBAC scenario.
    let projectCallCount = 0
    let refreshCallCount = 0

    server.use(
      http.get('*/v1/projects', () => {
        projectCallCount++
        return HttpResponse.json({ title: 'Unauthorized' }, { status: 401 })
      }),
      http.post('*/v1/auth/refresh', () => {
        refreshCallCount++
        return HttpResponse.json({ title: 'Invalid refresh token' }, { status: 401 })
      })
    )

    const { apiClient } = await import('@/api/client')
    const error = await apiClient.get('/projects').catch((e) => e)

    // Contract: exactly 1 refresh, no retry after refresh failure, error propagated
    expect(refreshCallCount).toBe(1)
    // projects was called once (the interceptor does NOT retry after refresh failure)
    expect(projectCallCount).toBe(1)
    expect(error).toBeDefined()
  })

  it('does NOT trigger refresh for 401 on /auth/login itself', async () => {
    let refreshCallCount = 0

    server.use(
      http.post('*/v1/auth/login', () => {
        return HttpResponse.json({ title: 'Bad credentials' }, { status: 401 })
      }),
      http.post('*/v1/auth/refresh', () => {
        refreshCallCount++
        return HttpResponse.json({ accessToken: 'new' })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.post('/auth/login', { accountName: 'x', password: 'y' }).catch(() => null)

    expect(refreshCallCount).toBe(0)
  })

  it('does NOT trigger refresh for 401 on /auth/refresh itself', async () => {
    let refreshCallCount = 0

    server.use(
      http.post('*/v1/auth/refresh', () => {
        refreshCallCount++
        return HttpResponse.json({ title: 'Invalid' }, { status: 401 })
      })
    )

    const { apiClient } = await import('@/api/client')
    await apiClient.post('/auth/refresh', {}).catch(() => null)

    // Only the direct call — no recursive retry
    expect(refreshCallCount).toBe(1)
  })

  it('fires only one refresh request when multiple concurrent 401s arrive', async () => {
    let projectCallCount = 0
    let refreshCallCount = 0

    server.use(
      http.get('*/v1/projects', () => {
        projectCallCount++
        // Both first-pass calls get 401; retries get 200
        if (projectCallCount <= 2) {
          return HttpResponse.json({ title: 'Unauthorized' }, { status: 401 })
        }
        return HttpResponse.json({ items: [], pageInfo: { nextCursor: null, hasMore: false } })
      }),
      http.post('*/v1/auth/refresh', () => {
        refreshCallCount++
        return HttpResponse.json({ accessToken: 'new' })
      })
    )

    const { apiClient } = await import('@/api/client')
    // Fire two requests in parallel — both should see 401 on first attempt
    const [r1, r2] = await Promise.all([
      apiClient.get('/projects').catch(() => null),
      apiClient.get('/projects').catch(() => null),
    ])

    // De-dupe: only 1 refresh request regardless of how many 401s came in
    expect(refreshCallCount).toBe(1)
    // Both retries should succeed (got 200)
    expect(r1?.status).toBe(200)
    expect(r2?.status).toBe(200)
  })
})
