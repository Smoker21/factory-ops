export interface JwtPayload {
  sub: string
  userId: string
  accountName: string
  displayName?: string
  rootOrgId: string
  orgPath?: string[]
  roles: string[]
  groupIds?: string[]
  orgManagerScopes?: string[]
  exp: number
  iat: number
}

export function decodeJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = parts[1]
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decoded) as JwtPayload
  } catch {
    return null
  }
}

export function isTokenExpired(token: string): boolean {
  const payload = decodeJwt(token)
  if (!payload) return true
  const nowSeconds = Math.floor(Date.now() / 1000)
  return payload.exp < nowSeconds + 30
}

export function getTokenExpiresAt(token: string): number {
  const payload = decodeJwt(token)
  if (!payload) return 0
  return payload.exp
}
