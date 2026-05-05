import apiClient from './client'
import type {
  ProjectTemplate,
  ProjectTemplatePage,
  TaskTemplate,
  TaskTemplatePage,
} from './types'

export interface ListProjectTemplatesParams {
  cursor?: string
  limit?: number
  tag?: string
  active?: boolean
  q?: string
  includeGlobal?: boolean
}

export async function listProjectTemplates(
  rootOrgId: string,
  params?: ListProjectTemplatesParams
): Promise<ProjectTemplatePage> {
  const response = await apiClient.get<ProjectTemplatePage>(
    `/orgs/${rootOrgId}/project-templates`,
    { params }
  )
  return response.data
}

export async function listGlobalProjectTemplates(
  params?: ListProjectTemplatesParams
): Promise<ProjectTemplatePage> {
  const response = await apiClient.get<ProjectTemplatePage>('/system/project-templates', {
    params,
  })
  return response.data
}

export async function listTaskTemplates(
  rootOrgId: string,
  params?: { cursor?: string; limit?: number; type?: string; tag?: string; includeGlobal?: boolean }
): Promise<TaskTemplatePage> {
  const response = await apiClient.get<TaskTemplatePage>(`/orgs/${rootOrgId}/task-templates`, {
    params,
  })
  return response.data
}

export async function forkProjectTemplate(
  rootOrgId: string,
  globalTplId: string,
  data?: { code?: string; name?: string }
): Promise<ProjectTemplate> {
  const response = await apiClient.post<ProjectTemplate>(
    `/orgs/${rootOrgId}/project-templates/fork-from-global/${globalTplId}`,
    data
  )
  return response.data
}

export async function forkTaskTemplate(
  rootOrgId: string,
  globalTplId: string,
  data?: { code?: string; name?: string }
): Promise<TaskTemplate> {
  const response = await apiClient.post<TaskTemplate>(
    `/orgs/${rootOrgId}/task-templates/fork-from-global/${globalTplId}`,
    data
  )
  return response.data
}
