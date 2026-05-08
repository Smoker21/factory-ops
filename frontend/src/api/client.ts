import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { Problem } from './types'
import { getCookie } from '@/utils/cookies'
import logger from '@/lib/logger'

// Use a relative base URL so requests go through the Vite proxy (/v1 → localhost:8080/v1).
// This keeps the cookie origin consistent (same-origin) and avoids SameSite issues
// when the frontend dev server runs on a different port to the backend.
// In production the reverse proxy serves both under the same origin.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/v1'

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
  timeout: 30000,
  // Required for the browser to send httpOnly auth cookies cross-context
  // and to allow Set-Cookie headers to be applied by the browser.
  withCredentials: true,
})

let isRefreshing = false
let refreshQueue: Array<{
  resolve: () => void
  reject: (error: unknown) => void
}> = []

function processRefreshQueue(error: unknown = null) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (error === null) resolve()
    else reject(error)
  })
  refreshQueue = []
}

// CSRF echo interceptor — reads the non-httpOnly XSRF-TOKEN cookie that the
// backend sets on login/refresh and echoes its value in the X-XSRF-TOKEN
// request header for every mutating request.
// Safe methods (GET, HEAD, OPTIONS) are exempt per ADR-0015.
const MUTATING_METHODS = new Set(['post', 'put', 'patch', 'delete'])

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const method = (config.method ?? '').toLowerCase()
    if (MUTATING_METHODS.has(method)) {
      const xsrfToken = getCookie('XSRF-TOKEN')
      if (xsrfToken) {
        config.headers['X-XSRF-TOKEN'] = xsrfToken
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// URLs whose 401 responses are user-actionable failures (wrong credentials,
// invalid refresh token) and must NOT trigger the auto-refresh flow.
// Letting /auth/login or /auth/refresh enter the interceptor causes the
// wrong-password Alert on LoginPage to be swallowed and forces a hard
// redirect to /login, erasing the user's input.
const AUTH_PUBLIC_PATHS = ['/auth/login', '/auth/refresh', '/auth/logout']

function isAuthPublicRequest(url: string | undefined): boolean {
  if (!url) return false
  return AUTH_PUBLIC_PATHS.some((p) => url.includes(p))
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<Problem>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !isAuthPublicRequest(originalRequest.url)
    ) {
      if (isRefreshing) {
        return new Promise<void>((resolve, reject) => {
          refreshQueue.push({ resolve, reject })
        }).then(() => {
          return apiClient(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        // Cookie-first refresh: send no body — the backend reads the
        // refresh_token httpOnly cookie directly (ADR-0015 §Decision).
        await axios.post(
          `${BASE_URL}/auth/refresh`,
          {},
          { withCredentials: true }
        )
        // Backend responds with three new Set-Cookie headers.
        // The browser automatically stores them; the next request will
        // carry the updated access_token and XSRF-TOKEN cookies.
        processRefreshQueue()
        return apiClient(originalRequest)
      } catch (refreshError) {
        processRefreshQueue(refreshError)
        redirectToLogin()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    const problem = error.response?.data
    if (problem) {
      logger.error('API error', {
        status: error.response?.status,
        title: problem.title,
        detail: problem.detail,
      })
    }

    return Promise.reject(error)
  }
)

function redirectToLogin() {
  // Guard against redirect loops: if we are already on /login (or /login?...),
  // doing another navigation would cause an infinite reload cycle, particularly
  // on the initial load when there are no auth cookies and both /me (bootstrap)
  // and /auth/refresh (auto-retry) return 401.
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

export default apiClient
