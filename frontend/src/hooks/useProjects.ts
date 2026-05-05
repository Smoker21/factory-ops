import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  listProjects,
  getProject,
  createProject,
  updateProject,
  changeProjectStatus,
  changeProjectOwner,
} from '@/api/projects'
import type { CreateProjectRequest, UpdateProjectRequest } from '@/api/types'
import type { ListProjectsParams } from '@/api/projects'

export function useProjects(params?: ListProjectsParams) {
  return useQuery({
    queryKey: ['projects', params],
    queryFn: () => listProjects(params),
  })
}

export function useProject(projectId: string) {
  return useQuery({
    queryKey: ['projects', projectId],
    queryFn: () => getProject(projectId),
    enabled: !!projectId,
  })
}

export function useCreateProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateProjectRequest) => createProject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
  })
}

export function useUpdateProject(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: UpdateProjectRequest) => updateProject(projectId, data),
    onSuccess: (updatedProject) => {
      queryClient.setQueryData(['projects', projectId], updatedProject)
      queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
  })
}

export function useChangeProjectStatus(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ status, reason }: { status: string; reason?: string }) =>
      changeProjectStatus(projectId, status, reason),
    onSuccess: (updatedProject) => {
      queryClient.setQueryData(['projects', projectId], updatedProject)
      queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
  })
}

export function useChangeProjectOwner(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ newOwnerId, reason }: { newOwnerId: string; reason?: string }) =>
      changeProjectOwner(projectId, newOwnerId, reason),
    onSuccess: (updatedProject) => {
      queryClient.setQueryData(['projects', projectId], updatedProject)
    },
  })
}
