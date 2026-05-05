import {
  Title,
  Stack,
  Text,
  Badge,
  Group,
  Tabs,
  Button,
  Skeleton,
  Modal,
  TextInput,
  Textarea,
  Select,
} from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useForm, FormProvider } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { notifications } from '@mantine/notifications'
import { useProject, useChangeProjectStatus } from '@/hooks/useProjects'
import { useTasks, useCreateTask } from '@/hooks/useTasks'
import { TaskCard } from '@/components/task/TaskCard'
import { ProjectStatusBadge } from '@/components/project/ProjectStatusBadge'
import { TaskTypeForm } from '@/components/task/TaskTypeForm'
import { useAuth } from '@/auth/useAuth'
import { canCreateTask, canEditProject } from '@/rbac/rbacGuards'
import { formatDate } from '@/lib/format'
import { extractProblemMessage } from '@/lib/utils'
import { PageError } from '@/components/common/ErrorBoundary'
import { useTaskTypes } from '@/hooks/useTasks'
import type { Priority } from '@/api/types'

const createTaskSchema = z.object({
  title: z.string().min(1, '任務名稱必填').max(200),
  type: z.string().min(1, '請選擇類型'),
  descriptionMarkdown: z.string().optional(),
  priority: z.string().min(1, '請選擇優先等級'),
  ownerId: z.string().min(1, '必須指定負責人'),
})

type CreateTaskFormData = z.infer<typeof createTaskSchema>

export function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [createTaskOpen, { open: openCreateTask, close: closeCreateTask }] = useDisclosure(false)

  const projectQuery = useProject(projectId ?? '')
  const tasksQuery = useTasks({ projectId })
  const taskTypesQuery = useTaskTypes()
  const changeStatusMutation = useChangeProjectStatus(projectId ?? '')
  const createTaskMutation = useCreateTask()

  const methods = useForm<CreateTaskFormData>({
    resolver: zodResolver(createTaskSchema),
    defaultValues: { priority: 'NORMAL', type: 'GENERAL', ownerId: user?.id },
  })
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = methods

  if (projectQuery.isLoading) {
    return (
      <Stack>
        <Skeleton height={40} />
        <Skeleton height={200} />
      </Stack>
    )
  }

  if (projectQuery.isError || !projectQuery.data) {
    return <PageError message="找不到專案" onRetry={() => projectQuery.refetch()} />
  }

  const project = projectQuery.data
  const tasks = tasksQuery.data?.items ?? []

  const taskTypeOptions = (taskTypesQuery.data ?? []).map((tt) => ({
    value: tt.type,
    label: tt.label ?? tt.type,
  }))

  const onCreateTask = async (data: CreateTaskFormData) => {
    try {
      await createTaskMutation.mutateAsync({
        projectId: projectId ?? '',
        type: data.type,
        title: data.title,
        descriptionMarkdown: data.descriptionMarkdown,
        priority: data.priority as Priority,
        ownerId: data.ownerId,
        assignees: [data.ownerId],
      })
      notifications.show({ message: '任務已建立', color: 'green' })
      reset()
      closeCreateTask()
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  const statusTransitions: Array<{ status: string; label: string; color: string }> = []
  if (project.status === 'DRAFT') {
    statusTransitions.push({ status: 'ACTIVE', label: '啟動', color: 'blue' })
  }
  if (project.status === 'ACTIVE') {
    statusTransitions.push({ status: 'PAUSED', label: '暫停', color: 'yellow' })
    statusTransitions.push({ status: 'COMPLETED', label: '完成', color: 'green' })
  }
  if (project.status === 'PAUSED') {
    statusTransitions.push({ status: 'ACTIVE', label: '繼續', color: 'blue' })
  }

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-start">
        <Stack gap="xs">
          <Group>
            <Button variant="subtle" size="xs" onClick={() => navigate(-1)}>
              ← {t('app.back')}
            </Button>
          </Group>
          <Title order={2}>{project.name}</Title>
          <Group gap="xs">
            <ProjectStatusBadge status={project.status} />
            {project.overdue && <Badge color="red">逾期</Badge>}
          </Group>
        </Stack>
        {canEditProject(user, project) && (
          <Group>
            {statusTransitions.map((t) => (
              <Button
                key={t.status}
                color={t.color}
                size="sm"
                variant="outline"
                style={{ minHeight: 44 }}
                onClick={async () => {
                  try {
                    await changeStatusMutation.mutateAsync({ status: t.status })
                    notifications.show({ message: `狀態已更新`, color: 'green' })
                  } catch (err) {
                    notifications.show({ message: extractProblemMessage(err), color: 'red' })
                  }
                }}
              >
                {t.label}
              </Button>
            ))}
          </Group>
        )}
      </Group>

      {project.descriptionMarkdown && (
        <Text c="dimmed">{project.descriptionMarkdown}</Text>
      )}

      <Group gap="xl">
        {project.schedule?.start && (
          <Text size="sm">
            <strong>開始：</strong>
            {formatDate(project.schedule.start)}
          </Text>
        )}
        {project.schedule?.due && (
          <Text size="sm">
            <strong>截止：</strong>
            {formatDate(project.schedule.due)}
          </Text>
        )}
      </Group>

      <Tabs defaultValue="tasks">
        <Tabs.List>
          <Tabs.Tab value="tasks">{t('project.tasks')}</Tabs.Tab>
          <Tabs.Tab value="history">{t('project.history')}</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="tasks" pt="md">
          <Stack>
            {canCreateTask(user) && (
              <Group justify="flex-end">
                <Button onClick={openCreateTask} style={{ minHeight: 44 }}>
                  {t('task.create')}
                </Button>
              </Group>
            )}
            {tasksQuery.isLoading && (
              <Stack>
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} height={80} />
                ))}
              </Stack>
            )}
            {!tasksQuery.isLoading && tasks.length === 0 && (
              <Text c="dimmed">{t('app.noData')}</Text>
            )}
            {tasks.map((task) => (
              <TaskCard key={task.id} task={task} />
            ))}
          </Stack>
        </Tabs.Panel>

        <Tabs.Panel value="history" pt="md">
          <Text c="dimmed">歷程功能開發中...</Text>
        </Tabs.Panel>
      </Tabs>

      <Modal
        opened={createTaskOpen}
        onClose={closeCreateTask}
        title={t('task.create')}
        size="lg"
        centered
      >
        <FormProvider {...methods}>
          <form onSubmit={handleSubmit(onCreateTask)}>
            <Stack>
              <TextInput
                label={t('task.name')}
                required
                error={errors.title?.message}
                {...register('title')}
              />
              <Select
                label={t('task.type')}
                required
                data={taskTypeOptions}
                value={watch('type')}
                onChange={(v) => setValue('type', v ?? 'GENERAL')}
                error={errors.type?.message}
              />
              <Textarea
                label={t('task.description')}
                rows={3}
                {...register('descriptionMarkdown')}
              />
              <Select
                label={t('task.priority')}
                required
                data={[
                  { value: 'LOW', label: t('task.priorityLOW') },
                  { value: 'NORMAL', label: t('task.priorityNORMAL') },
                  { value: 'HIGH', label: t('task.priorityHIGH') },
                  { value: 'URGENT', label: t('task.priorityURGENT') },
                ]}
                value={watch('priority')}
                onChange={(v) => setValue('priority', v ?? 'NORMAL')}
              />
              <TextInput
                label="負責人 ID"
                defaultValue={user?.id}
                required
                error={errors.ownerId?.message}
                {...register('ownerId')}
              />
              <TaskTypeForm taskType={watch('type')} />
              <Group justify="flex-end">
                <Button variant="outline" onClick={closeCreateTask}>
                  {t('app.cancel')}
                </Button>
                <Button type="submit" loading={isSubmitting} style={{ minHeight: 44 }}>
                  {t('app.create')}
                </Button>
              </Group>
            </Stack>
          </form>
        </FormProvider>
      </Modal>
    </Stack>
  )
}
