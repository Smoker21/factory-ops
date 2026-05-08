/**
 * Unit tests for utils/cookies.ts
 * These tests manipulate document.cookie directly (jsdom environment).
 */
import { describe, it, expect, afterEach } from 'vitest'
import { getCookie } from '@/utils/cookies'

// Helper to set a cookie in jsdom
function setCookieValue(name: string, value: string) {
  document.cookie = `${name}=${encodeURIComponent(value)}`
}

describe('getCookie', () => {
  afterEach(() => {
    // Clean up all cookies set during the test
    document.cookie.split(';').forEach((c) => {
      const name = c.trim().split('=')[0]
      document.cookie = `${name}=; Max-Age=0`
    })
  })

  it('returns null when the cookie is absent', () => {
    expect(getCookie('XSRF-TOKEN')).toBeNull()
  })

  it('returns the value of an existing cookie', () => {
    setCookieValue('XSRF-TOKEN', 'abc123')
    expect(getCookie('XSRF-TOKEN')).toBe('abc123')
  })

  it('returns null for a different cookie name', () => {
    setCookieValue('XSRF-TOKEN', 'abc123')
    expect(getCookie('other-cookie')).toBeNull()
  })

  it('handles URL-encoded cookie values', () => {
    setCookieValue('test-cookie', 'hello world')
    expect(getCookie('test-cookie')).toBe('hello world')
  })

  it('handles multiple cookies and returns the correct one', () => {
    setCookieValue('cookie-a', 'valueA')
    setCookieValue('cookie-b', 'valueB')
    setCookieValue('XSRF-TOKEN', 'csrf-value')
    expect(getCookie('cookie-a')).toBe('valueA')
    expect(getCookie('XSRF-TOKEN')).toBe('csrf-value')
    expect(getCookie('cookie-b')).toBe('valueB')
  })

  it('returns null when document is undefined (SSR guard)', () => {
    // Simulate server-side environment by temporarily hiding document
    const originalDocument = global.document
    // @ts-expect-error -- intentionally removing document for SSR simulation
    delete global.document
    expect(getCookie('XSRF-TOKEN')).toBeNull()
    global.document = originalDocument
  })

  it('handles empty cookie string gracefully', () => {
    // jsdom with no cookies set
    expect(getCookie('')).toBeNull()
  })

  it('handles cookie values that contain "=" characters (base64 padding)', () => {
    // Values such as base64 tokens contain "=" signs — must parse correctly
    // document.cookie stores the raw value; we simulate it directly
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      // The raw cookie string as the browser would expose it
      value: 'access_token=eyJhbGc.eyJzdWIiOiJ1c2VyIn0=.sig',
    })
    // getCookie finds the first '=' to split key from value, so value = everything after that
    const result = getCookie('access_token')
    expect(result).toBe('eyJhbGc.eyJzdWIiOiJ1c2VyIn0=.sig')
    // Restore
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: '',
    })
  })

  it('returns null and does not throw when cookie string contains only semicolons', () => {
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: ';;;',
    })
    expect(getCookie('XSRF-TOKEN')).toBeNull()
    Object.defineProperty(document, 'cookie', {
      configurable: true,
      writable: true,
      value: '',
    })
  })
})
