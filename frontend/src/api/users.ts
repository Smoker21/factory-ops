import apiClient from './client'
import type { User, UserPage } from './types'

export interface ListUsersParams {
  cursor?: string
  limit?: number
  q?: string
  accountName?: string
  role?: string
  groupId?: string
  organizationId?: string
  active?: boolean
}

export async function listUsers(params?: ListUsersParams): Promise<UserPage> {
  const response = await apiClient.get<UserPage>('/users', { params })
  return response.data
}

export async function getUser(userId: string): Promise<User> {
  const response = await apiClient.get<User>(`/users/${userId}`)
  return response.data
}

export async function getMe(): Promise<User> {
  const response = await apiClient.get<User>('/me')
  return response.data
}
