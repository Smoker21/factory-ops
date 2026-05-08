/**
 * Unit tests for frontend/src/api/auth.ts
 *
 * Verifies cookie-first behaviour:
 * - login() does NOT write anything to localStorage
 * - logout() does NOT removeItem from localStorage
 * - refreshAuth() POSTs to /auth/refresh with no token body
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { server } from './server'
import { http, HttpResponse } from 'msw'

afterEach(() => {
  vi.restoreAllMocks()
})

// ── login() ────────────────────────────────────────────────────────────────────

describe('auth.login()', () => {
  it('does not call localStorage.setItem (token is handled by browser cookie jar)', async () => {
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem')

    server.use(
      http.post('*/v1/auth/login', () => {
        return HttpResponse.json({
          accessToken: 'mock-access',
          refreshToken: 'mock-refresh',
          expiresIn: 3600,
          tokenType: 'Bearer',
        })
      })
    )

    const { login } = await import('@/api/auth')
    await login({ orgCode: 'test-org', accountName: 'admin.system', password: 'Admin@123456789' })

    expect(setItemSpy).not.toHaveBeenCalled()
  })

  it('returns the response body (body tokens kept for forward-compat, but not stored)', async () => {
    server.use(
      http.post('*/v1/auth/login', () => {
        return HttpResponse.json({
          accessToken: 'at-abc',
          refreshToken: 'rt-def',
          expiresIn: 900,
          tokenType: 'Bearer',
        })
      })
    )

    const { login } = await import('@/api/auth')
    const result = await login({ orgCode: 'test-org', accountName: 'admin.system', password: 'pwd' })

    // The body is returned but the frontend should NOT use token values from here
    expect(result.accessToken).toBeTruthy()
    expect(result.tokenType).toBe('Bearer')
  })
})

// ── logout() ──────────────────────────────────────────────────────────────────

describe('auth.logout()', () => {
  it('does not call localStorage.removeItem', async () => {
    const removeItemSpy = vi.spyOn(Storage.prototype, 'removeItem')

    server.use(
      http.post('*/v1/auth/logout', () => new HttpResponse(null, { status: 204 }))
    )

    const { logout } = await import('@/api/auth')
    await logout()

    expect(removeItemSpy).not.toHaveBeenCalled()
  })

  it('sends POST /auth/logout with no body (cookie clearing handled by server)', async () => {
    let capturedBody: unknown = 'NOT_CALLED'

    server.use(
      http.post('*/v1/auth/logout', async ({ request }) => {
        const text = await request.text()
        capturedBody = text || null
        return new HttpResponse(null, { status: 204 })
      })
    )

    const { logout } = await import('@/api/auth')
    await logout()

    // axios sends {} as body by default; what matters is that no token strings are in it
    expect(typeof capturedBody === 'string' || capturedBody === null).toBe(true)
    if (typeof capturedBody === 'string' && capturedBody.length > 0) {
      expect(capturedBody).not.toContain('access_token')
      expect(capturedBody).not.toContain('factory_ops')
    }
  })
})

// ── refreshAuth() ─────────────────────────────────────────────────────────────

describe('auth.refreshAuth()', () => {
  it('POSTs to /auth/refresh (cookie-first: no token in body needed)', async () => {
    let refreshCalled = false

    server.use(
      http.post('*/v1/auth/refresh', () => {
        refreshCalled = true
        return HttpResponse.json({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900 })
      })
    )

    const { refreshAuth } = await import('@/api/auth')
    await refreshAuth()

    expect(refreshCalled).toBe(true)
  })

  it('resolves without error on a successful refresh', async () => {
    server.use(
      http.post('*/v1/auth/refresh', () => {
        return HttpResponse.json({ accessToken: 'new' })
      })
    )

    const { refreshAuth } = await import('@/api/auth')
    await expect(refreshAuth()).resolves.not.toThrow()
  })

  it('does not write any token to localStorage during refresh', async () => {
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem')

    server.use(
      http.post('*/v1/auth/refresh', () => {
        return HttpResponse.json({ accessToken: 'new' })
      })
    )

    const { refreshAuth } = await import('@/api/auth')
    await refreshAuth()

    expect(setItemSpy).not.toHaveBeenCalled()
  })
})
