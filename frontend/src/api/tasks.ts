import apiClient from './client'
import type {
  Task,
  TaskPage,
  CreateTaskRequest,
  UpdateTaskRequest,
  HistoryEntry,
  Comment,
  TaskTypeDefinition,
} from './types'

export interface ListTasksParams {
  cursor?: string
  limit?: number
  projectId?: string
  groupId?: string
  status?: string
  priority?: string
  ownerId?: string
  assigneeId?: string
  type?: string
  tag?: string
  dueBefore?: string
  dueAfter?: string
  q?: string
  includeDeleted?: boolean
}

export async function listTasks(params?: ListTasksParams): Promise<TaskPage> {
  const response = await apiClient.get<TaskPage>('/tasks', { params })
  return response.data
}

export async function getTask(taskId: string): Promise<Task> {
  const response = await apiClient.get<Task>(`/tasks/${taskId}`)
  return response.data
}

export async function createTask(data: CreateTaskRequest): Promise<Task> {
  const response = await apiClient.post<Task>('/tasks', data)
  return response.data
}

export async function updateTask(taskId: string, data: UpdateTaskRequest): Promise<Task> {
  const response = await apiClient.patch<Task>(`/tasks/${taskId}`, data)
  return response.data
}

export async function deleteTask(taskId: string): Promise<void> {
  await apiClient.delete(`/tasks/${taskId}`)
}

export async function changeTaskStatus(
  taskId: string,
  status: string,
  reason?: string
): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${taskId}/status`, { status, reason })
  return response.data
}

export async function addTaskAssignees(taskId: string, userIds: string[]): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${taskId}/assignees`, { userIds })
  return response.data
}

export async function removeTaskAssignee(taskId: string, userId: string): Promise<Task> {
  const response = await apiClient.delete<Task>(`/tasks/${taskId}/assignees/${userId}`)
  return response.data
}

export async function transferTaskOwner(
  taskId: string,
  newOwnerId: string,
  reason?: string
): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${taskId}/owner`, { newOwnerId, reason })
  return response.data
}

export async function submitTaskReview(
  taskId: string,
  decision: string,
  role: string,
  reason?: string
): Promise<Task> {
  const response = await apiClient.post<Task>(`/tasks/${taskId}/review`, {
    decision,
    role,
    reason,
  })
  return response.data
}

export async function listTaskComments(taskId: string): Promise<Comment[]> {
  const response = await apiClient.get<Comment[]>(`/tasks/${taskId}/comments`)
  return response.data
}

export async function addTaskComment(taskId: string, content: string): Promise<Comment> {
  const response = await apiClient.post<Comment>(`/tasks/${taskId}/comments`, { content })
  return response.data
}

export async function updateTaskComment(
  taskId: string,
  commentId: string,
  content: string
): Promise<Comment> {
  const response = await apiClient.patch<Comment>(`/tasks/${taskId}/comments/${commentId}`, {
    content,
  })
  return response.data
}

export async function deleteTaskComment(taskId: string, commentId: string): Promise<void> {
  await apiClient.delete(`/tasks/${taskId}/comments/${commentId}`)
}

export async function getTaskHistory(taskId: string): Promise<HistoryEntry[]> {
  const response = await apiClient.get<HistoryEntry[]>(`/tasks/${taskId}/history`)
  return response.data
}

export async function listTaskTypes(): Promise<TaskTypeDefinition[]> {
  const response = await apiClient.get<TaskTypeDefinition[]>('/task-types')
  return response.data
}
