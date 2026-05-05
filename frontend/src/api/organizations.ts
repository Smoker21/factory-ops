import apiClient from './client'
import type { Organization, OrganizationPage, User } from './types'

export interface ListOrgsParams {
  cursor?: string
  limit?: number
  type?: string
  parentId?: string
  leafOnly?: boolean
  underOrgId?: string
  q?: string
}

export async function listOrganizations(params?: ListOrgsParams): Promise<OrganizationPage> {
  const response = await apiClient.get<OrganizationPage>('/orgs', { params })
  return response.data
}

export async function getOrganization(orgId: string): Promise<Organization> {
  const response = await apiClient.get<Organization>(`/orgs/${orgId}`)
  return response.data
}

export async function listOrgLeaders(orgId: string): Promise<User[]> {
  const response = await apiClient.get<User[]>(`/orgs/${orgId}/leaders`)
  return response.data
}
