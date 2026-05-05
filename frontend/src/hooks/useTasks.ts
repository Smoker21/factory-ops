import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  listTasks,
  getTask,
  createTask,
  updateTask,
  changeTaskStatus,
  addTaskAssignees,
  removeTaskAssignee,
  transferTaskOwner,
  submitTaskReview,
  listTaskComments,
  addTaskComment,
  listTaskTypes,
} from '@/api/tasks'
import type { CreateTaskRequest, UpdateTaskRequest } from '@/api/types'
import type { ListTasksParams } from '@/api/tasks'

export function useTasks(params?: ListTasksParams) {
  return useQuery({
    queryKey: ['tasks', params],
    queryFn: () => listTasks(params),
  })
}

export function useTask(taskId: string) {
  return useQuery({
    queryKey: ['tasks', taskId],
    queryFn: () => getTask(taskId),
    enabled: !!taskId,
  })
}

export function useTaskTypes() {
  return useQuery({
    queryKey: ['task-types'],
    queryFn: listTaskTypes,
    staleTime: 1000 * 60 * 10,
  })
}

export function useCreateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateTaskRequest) => createTask(data),
    onSuccess: (_task, variables) => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      if (variables.projectId) {
        queryClient.invalidateQueries({ queryKey: ['tasks', { projectId: variables.projectId }] })
      }
    },
  })
}

export function useUpdateTask(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateTaskRequest) => updateTask(taskId, data),
    onSuccess: (updatedTask) => {
      queryClient.setQueryData(['tasks', taskId], updatedTask)
    },
  })
}

export function useChangeTaskStatus(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ status, reason }: { status: string; reason?: string }) =>
      changeTaskStatus(taskId, status, reason),
    onSuccess: (updatedTask) => {
      queryClient.setQueryData(['tasks', taskId], updatedTask)
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    },
  })
}

export function useAddTaskAssignees(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userIds: string[]) => addTaskAssignees(taskId, userIds),
    onSuccess: (updatedTask) => {
      queryClient.setQueryData(['tasks', taskId], updatedTask)
    },
  })
}

export function useRemoveTaskAssignee(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => removeTaskAssignee(taskId, userId),
    onSuccess: (updatedTask) => {
      queryClient.setQueryData(['tasks', taskId], updatedTask)
    },
  })
}

export function useTransferTaskOwner(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ newOwnerId, reason }: { newOwnerId: string; reason?: string }) =>
      transferTaskOwner(taskId, newOwnerId, reason),
    onSuccess: (updatedTask) => {
      queryClient.setQueryData(['tasks', taskId], updatedTask)
    },
  })
}

export function useSubmitTaskReview(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      decision,
      role,
      reason,
    }: {
      decision: string
      role: string
      reason?: string
    }) => submitTaskReview(taskId, decision, role, reason),
    onSuccess: (updatedTask) => {
      queryClient.setQueryData(['tasks', taskId], updatedTask)
    },
  })
}

export function useTaskComments(taskId: string) {
  return useQuery({
    queryKey: ['tasks', taskId, 'comments'],
    queryFn: () => listTaskComments(taskId),
    enabled: !!taskId,
  })
}

export function useAddTaskComment(taskId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (content: string) => addTaskComment(taskId, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', taskId, 'comments'] })
    },
  })
}
