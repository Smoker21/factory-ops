import apiClient from './client'
import type { Group, GroupPage, GroupMembership } from './types'

export interface ListGroupsParams {
  cursor?: string
  limit?: number
  type?: string
  organizationId?: string
  underOrgId?: string
  leaderId?: string
  q?: string
}

export async function listGroups(rootOrgId: string, params?: ListGroupsParams): Promise<GroupPage> {
  const response = await apiClient.get<GroupPage>(`/orgs/${rootOrgId}/groups`, { params })
  return response.data
}

export async function getGroup(rootOrgId: string, groupId: string): Promise<Group> {
  const response = await apiClient.get<Group>(`/orgs/${rootOrgId}/groups/${groupId}`)
  return response.data
}

export async function listGroupMembers(
  rootOrgId: string,
  groupId: string,
  includeInactive = false
): Promise<GroupMembership[]> {
  const response = await apiClient.get<GroupMembership[]>(
    `/orgs/${rootOrgId}/groups/${groupId}/members`,
    { params: { includeInactive } }
  )
  return response.data
}

export async function addGroupMember(
  rootOrgId: string,
  groupId: string,
  userId: string,
  role?: string
): Promise<GroupMembership> {
  const response = await apiClient.post<GroupMembership>(
    `/orgs/${rootOrgId}/groups/${groupId}/members`,
    { userId, role }
  )
  return response.data
}

export async function removeGroupMember(
  rootOrgId: string,
  groupId: string,
  userId: string
): Promise<void> {
  await apiClient.delete(`/orgs/${rootOrgId}/groups/${groupId}/members/${userId}`)
}
