import apiClient from './client'
import type { LoginRequest, User } from './types'

export interface LoginResponse {
  user?: User
  // accessToken / refreshToken are returned by the backend for forward-compat
  // but the frontend ignores them — authentication is handled exclusively via
  // the three httpOnly cookies that backend sets with Set-Cookie (ADR-0015).
  accessToken?: string
  refreshToken?: string
  expiresIn?: number
  tokenType?: string
}

/**
 * POST /auth/login
 * On success the backend sets three cookies:
 *   refresh_token (httpOnly, Path=/v1/auth)
 *   access_token  (httpOnly, Path=/v1)
 *   XSRF-TOKEN    (non-httpOnly, Path=/v1) — readable by JS for CSRF echo
 *
 * The returned object contains user info from the body (if provided).
 * Token values in the body are intentionally ignored.
 */
export async function login(data: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', data)
  return response.data
}

/**
 * POST /auth/refresh
 * Sends no body — the backend reads the refresh_token cookie directly.
 * On success three new cookies are issued by the server.
 */
export async function refreshAuth(): Promise<void> {
  await apiClient.post('/auth/refresh', {})
}

/**
 * POST /auth/logout
 * Sends no body — the backend reads the refresh_token cookie, adds the jti
 * to the revoke blacklist, and clears all three cookies (Max-Age=0).
 */
export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout', {})
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiClient.put('/auth/password', { currentPassword, newPassword })
}
