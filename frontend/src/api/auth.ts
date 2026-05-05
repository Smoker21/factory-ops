import apiClient from './client'
import type { LoginRequest, TokenPair } from './types'

export async function login(data: LoginRequest): Promise<TokenPair> {
  const response = await apiClient.post<TokenPair>('/auth/login', data)
  return response.data
}

export async function refreshToken(refreshToken: string): Promise<TokenPair> {
  const response = await apiClient.post<TokenPair>('/auth/refresh', { refreshToken })
  return response.data
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post('/auth/logout', { refreshToken })
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiClient.put('/auth/password', { currentPassword, newPassword })
}
