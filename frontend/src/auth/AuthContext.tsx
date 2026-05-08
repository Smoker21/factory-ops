/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useCallback, useEffect, useState } from 'react'
import type { User } from '@/api/types'
import { getMe } from '@/api/users'
import logger from '@/lib/logger'

// Auth state no longer holds token strings — authentication is handled
// exclusively via httpOnly cookies managed by the browser (ADR-0015).
// On reload we probe GET /me to determine whether the session is still valid.

export interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
}

export interface AuthContextValue extends AuthState {
  /** Called after a successful login to set the current user. */
  login: (user: User) => void
  /** Clears local user state; the caller is responsible for the API logout call. */
  logout: () => void
  setUser: (user: User) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
  })

  // On mount: probe /me to restore session from existing cookies.
  // If the access_token cookie is still valid the server returns 200 + user.
  // If it has expired the axios interceptor in client.ts will attempt a silent
  // refresh; on success the retry returns 200 and we restore the session.
  // Only a definitive 401 after refresh failure leaves us unauthenticated.
  useEffect(() => {
    let cancelled = false

    async function bootstrapSession() {
      try {
        const user = await getMe()
        if (!cancelled) {
          setState({ user, isAuthenticated: true, isLoading: false })
          logger.debug('Session restored from cookie', { accountName: user.accountName })
        }
      } catch {
        if (!cancelled) {
          setState({ user: null, isAuthenticated: false, isLoading: false })
          logger.debug('No active session (cookie absent or expired)')
        }
      }
    }

    void bootstrapSession()
    return () => { cancelled = true }
  }, [])

  const login = useCallback((user: User) => {
    setState({ user, isAuthenticated: true, isLoading: false })
    logger.info('User logged in', { accountName: user.accountName })
  }, [])

  const logout = useCallback(() => {
    setState({ user: null, isAuthenticated: false, isLoading: false })
    logger.info('User logged out')
  }, [])

  const setUser = useCallback((user: User) => {
    setState((prev) => ({ ...prev, user }))
  }, [])

  return (
    <AuthContext.Provider value={{ ...state, login, logout, setUser }}>
      {children}
    </AuthContext.Provider>
  )
}
