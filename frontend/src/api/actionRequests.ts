import apiClient from './client'
import type { ActionRequest, ActionRequestPage, DispatchActionRequestRequest } from './types'

export interface ListActionRequestsParams {
  cursor?: string
  limit?: number
  status?: string
  ownerId?: string
  projectId?: string
  q?: string
}

export async function listActionRequests(
  params?: ListActionRequestsParams
): Promise<ActionRequestPage> {
  const response = await apiClient.get<ActionRequestPage>('/action-requests', { params })
  return response.data
}

export async function getActionRequest(id: string): Promise<ActionRequest> {
  const response = await apiClient.get<ActionRequest>(`/action-requests/${id}`)
  return response.data
}

export async function createActionRequest(data: Partial<ActionRequest>): Promise<ActionRequest> {
  const response = await apiClient.post<ActionRequest>('/action-requests', data)
  return response.data
}

export async function changeActionRequestStatus(
  id: string,
  status: string,
  reason?: string
): Promise<ActionRequest> {
  const response = await apiClient.post<ActionRequest>(`/action-requests/${id}/status`, {
    status,
    reason,
  })
  return response.data
}

export async function convertActionRequestToTask(
  id: string,
  projectId: string,
  taskData: Record<string, unknown>
): Promise<ActionRequest> {
  const response = await apiClient.post<ActionRequest>(`/action-requests/${id}/convert-to-task`, {
    projectId,
    ...taskData,
  })
  return response.data
}

export async function dispatchActionRequest(
  targetOrgId: string,
  data: DispatchActionRequestRequest
): Promise<ActionRequest> {
  const response = await apiClient.post<ActionRequest>(
    `/orgs/${targetOrgId}/dispatch-action-request`,
    data
  )
  return response.data
}
