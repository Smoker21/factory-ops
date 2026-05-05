/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useCallback, useEffect, useState } from 'react'
import type { User } from '@/api/types'
import { decodeJwt } from './jwtUtils'
import logger from '@/lib/logger'

const ACCESS_TOKEN_KEY = 'factory_ops_access_token'
const REFRESH_TOKEN_KEY = 'factory_ops_refresh_token'

export interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: User | null
  isAuthenticated: boolean
}

export interface AuthContextValue extends AuthState {
  login: (accessToken: string, refreshToken: string, user: User) => void
  logout: () => void
  setUser: (user: User) => void
  updateTokens: (accessToken: string, refreshToken: string) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>(() => {
    const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)

    if (accessToken && refreshToken) {
      const payload = decodeJwt(accessToken)
      if (payload) {
        const user: User = {
          id: payload.userId,
          rootOrgId: payload.rootOrgId,
          accountName: payload.accountName,
          displayName: payload.displayName ?? payload.accountName,
          roles: payload.roles as User['roles'],
          groupIds: payload.groupIds ?? [],
          orgManagerScopes: payload.orgManagerScopes ?? [],
          active: true,
        }
        return { accessToken, refreshToken, user, isAuthenticated: true }
      }
    }
    return { accessToken: null, refreshToken: null, user: null, isAuthenticated: false }
  })

  const login = useCallback((accessToken: string, refreshToken: string, user: User) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    setState({ accessToken, refreshToken, user, isAuthenticated: true })
    logger.info('User logged in', { accountName: user.accountName })
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    setState({ accessToken: null, refreshToken: null, user: null, isAuthenticated: false })
    logger.info('User logged out')
  }, [])

  const setUser = useCallback((user: User) => {
    setState((prev) => ({ ...prev, user }))
  }, [])

  const updateTokens = useCallback((accessToken: string, refreshToken: string) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    const payload = decodeJwt(accessToken)
    setState((prev) => ({
      ...prev,
      accessToken,
      refreshToken,
      user: prev.user
        ? {
            ...prev.user,
            roles: (payload?.roles as User['roles']) ?? prev.user.roles,
          }
        : prev.user,
    }))
  }, [])

  useEffect(() => {
    logger.debug('Auth state initialized', { isAuthenticated: state.isAuthenticated })
  }, [state.isAuthenticated])

  return (
    <AuthContext.Provider value={{ ...state, login, logout, setUser, updateTokens }}>
      {children}
    </AuthContext.Provider>
  )
}
