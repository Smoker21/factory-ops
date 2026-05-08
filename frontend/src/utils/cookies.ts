/**
 * Cookie utility — parses document.cookie into individual values.
 * Kept as a standalone module so it can be unit-tested without
 * importing the full axios / React stack.
 */

/**
 * Read a single cookie value by name.
 * Returns null when the cookie is absent or the environment has no
 * document (SSR / Node test runner).
 */
export function getCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const encoded = encodeURIComponent(name)
  const pairs = document.cookie.split(';')
  for (const pair of pairs) {
    const trimmed = pair.trim()
    const eqIdx = trimmed.indexOf('=')
    if (eqIdx === -1) continue
    const key = trimmed.slice(0, eqIdx).trim()
    if (key === encoded || key === name) {
      const value = trimmed.slice(eqIdx + 1).trim()
      return decodeURIComponent(value)
    }
  }
  return null
}
