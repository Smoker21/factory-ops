import apiClient from './client'
import type {
  Project,
  ProjectPage,
  CreateProjectRequest,
  UpdateProjectRequest,
  Membership,
  HistoryEntry,
} from './types'

export interface ListProjectsParams {
  cursor?: string
  limit?: number
  status?: string
  ownerId?: string
  memberId?: string
  groupId?: string
  tag?: string
  q?: string
}

export async function listProjects(params?: ListProjectsParams): Promise<ProjectPage> {
  const response = await apiClient.get<ProjectPage>('/projects', { params })
  return response.data
}

export async function getProject(projectId: string): Promise<Project> {
  const response = await apiClient.get<Project>(`/projects/${projectId}`)
  return response.data
}

export async function createProject(data: CreateProjectRequest): Promise<Project> {
  const response = await apiClient.post<Project>('/projects', data)
  return response.data
}

export async function updateProject(
  projectId: string,
  data: UpdateProjectRequest
): Promise<Project> {
  const response = await apiClient.patch<Project>(`/projects/${projectId}`, data)
  return response.data
}

export async function deleteProject(projectId: string): Promise<void> {
  await apiClient.delete(`/projects/${projectId}`)
}

export async function changeProjectStatus(
  projectId: string,
  status: string,
  reason?: string
): Promise<Project> {
  const response = await apiClient.post<Project>(`/projects/${projectId}/status`, {
    status,
    reason,
  })
  return response.data
}

export async function changeProjectOwner(
  projectId: string,
  newOwnerId: string,
  reason?: string
): Promise<Project> {
  const response = await apiClient.post<Project>(`/projects/${projectId}/owner`, {
    newOwnerId,
    reason,
  })
  return response.data
}

export async function listProjectMembers(projectId: string): Promise<Membership[]> {
  const response = await apiClient.get<Membership[]>(`/projects/${projectId}/members`)
  return response.data
}

export async function addProjectMember(
  projectId: string,
  userId: string,
  role?: string
): Promise<Membership> {
  const response = await apiClient.post<Membership>(`/projects/${projectId}/members`, {
    userId,
    role,
  })
  return response.data
}

export async function removeProjectMember(projectId: string, userId: string): Promise<void> {
  await apiClient.delete(`/projects/${projectId}/members/${userId}`)
}

export async function getProjectHistory(projectId: string): Promise<HistoryEntry[]> {
  const response = await apiClient.get<HistoryEntry[]>(`/projects/${projectId}/history`)
  return response.data
}
