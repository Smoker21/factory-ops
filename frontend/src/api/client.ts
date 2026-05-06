import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { TokenPair, Problem } from './types'
import logger from '@/lib/logger'

const ACCESS_TOKEN_KEY = 'factory_ops_access_token'
const REFRESH_TOKEN_KEY = 'factory_ops_refresh_token'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/v1'

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
  timeout: 30000,
})

let isRefreshing = false
let refreshQueue: Array<{
  resolve: (token: string) => void
  reject: (error: unknown) => void
}> = []

function processRefreshQueue(token: string | null, error: unknown = null) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token)
    else reject(error)
  })
  refreshQueue = []
}

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// URLs whose 401 responses are user-actionable failures (wrong credentials,
// invalid refresh token) and must not trigger the auto-refresh flow.  Letting
// /auth/login or /auth/refresh enter the interceptor causes the wrong-password
// Alert on LoginPage to be swallowed and forces a hard redirect to /login,
// erasing the user's input.
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
        return new Promise<string>((resolve, reject) => {
          refreshQueue.push({ resolve, reject })
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return apiClient(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
      if (!refreshToken) {
        isRefreshing = false
        redirectToLogin()
        return Promise.reject(error)
      }

      try {
        const response = await axios.post<TokenPair>(`${BASE_URL}/auth/refresh`, {
          refreshToken,
        })
        const { accessToken, refreshToken: newRefreshToken } = response.data
        localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
        localStorage.setItem(REFRESH_TOKEN_KEY, newRefreshToken)
        processRefreshQueue(accessToken)
        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return apiClient(originalRequest)
      } catch (refreshError) {
        processRefreshQueue(null, refreshError)
        redirectToLogin()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    const problem = error.response?.data
    if (problem) {
      logger.error('API error', { status: error.response?.status, title: problem.title, detail: problem.detail })
    }

    return Promise.reject(error)
  }
)

function redirectToLogin() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  window.location.href = '/login'
}

export default apiClient
