import {
  Title,
  Stack,
  Text,
  Badge,
  Group,
  Divider,
  Textarea,
  Button,
  Skeleton,
  Paper,
  Tabs,
} from '@mantine/core'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { notifications } from '@mantine/notifications'
import { useTask, useUpdateTask, useTaskComments, useAddTaskComment } from '@/hooks/useTasks'
import { TaskStatusFlow } from '@/components/task/TaskStatusFlow'
import { AssigneeManager } from '@/components/task/AssigneeManager'
import { QaReviewPanel } from '@/components/task/QaReviewPanel'
import { MarkdownRenderer } from '@/components/markdown/MarkdownRenderer'
import { PageError } from '@/components/common/ErrorBoundary'
import { formatDateTime } from '@/lib/format'
import { extractProblemMessage } from '@/lib/utils'
import { useQuery } from '@tanstack/react-query'
import { listUsers } from '@/api/users'

const STATUS_COLORS: Record<string, string> = {
  OPEN: 'gray',
  IN_PROGRESS: 'blue',
  BLOCKED: 'red',
  IN_REVIEW: 'yellow',
  DONE: 'green',
  CANCELLED: 'dark',
}

const PRIORITY_COLORS: Record<string, string> = {
  LOW: 'gray',
  NORMAL: 'blue',
  HIGH: 'orange',
  URGENT: 'red',
}

const TYPE_LABELS: Record<string, string> = {
  EQUIPMENT_INSPECTION: '設備檢查',
  INCIDENT_RESPONSE: '異常處置',
  SHIFT_HANDOVER: '交班事項',
  GENERAL: '一般任務',
}

export function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [commentText, setCommentText] = useState('')
  const [isEditingDesc, setIsEditingDesc] = useState(false)
  const [descDraft, setDescDraft] = useState('')

  const taskQuery = useTask(taskId ?? '')
  const commentsQuery = useTaskComments(taskId ?? '')
  const updateTaskMutation = useUpdateTask(taskId ?? '')
  const addCommentMutation = useAddTaskComment(taskId ?? '')

  const usersQuery = useQuery({
    queryKey: ['users'],
    queryFn: () => listUsers({ limit: 50 }),
  })
  const usersMap = new Map((usersQuery.data?.items ?? []).map((u) => [u.id, u]))

  if (taskQuery.isLoading) {
    return (
      <Stack>
        <Skeleton height={40} />
        <Skeleton height={200} />
        <Skeleton height={100} />
      </Stack>
    )
  }

  if (taskQuery.isError || !taskQuery.data) {
    return <PageError message="找不到任務" onRetry={() => taskQuery.refetch()} />
  }

  const task = taskQuery.data
  const comments = commentsQuery.data ?? []

  const handleSaveDesc = async () => {
    try {
      await updateTaskMutation.mutateAsync({ descriptionMarkdown: descDraft })
      setIsEditingDesc(false)
      notifications.show({ message: '描述已更新', color: 'green' })
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  const handleAddComment = async () => {
    if (!commentText.trim()) return
    try {
      await addCommentMutation.mutateAsync(commentText)
      setCommentText('')
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  const ownerUser = usersMap.get(task.ownerId)

  return (
    <Stack gap="lg">
      <Group>
        <Button variant="subtle" size="xs" onClick={() => navigate(-1)}>
          ← {t('app.back')}
        </Button>
      </Group>

      <Stack gap="xs">
        <Group gap="xs" align="flex-start" wrap="nowrap">
          <Title order={3} style={{ flex: 1 }}>
            {task.title}
          </Title>
        </Group>

        <Group gap="xs" wrap="wrap">
          <Badge color={STATUS_COLORS[task.status]}>{t(`task.status${task.status}`)}</Badge>
          <Badge color={PRIORITY_COLORS[task.priority]} variant="outline">
            {t(`task.priority${task.priority}`)}
          </Badge>
          <Badge variant="light">{TYPE_LABELS[task.type] ?? task.type}</Badge>
          {task.overdue && (
            <Badge color="red">逾期</Badge>
          )}
        </Group>

        <Group gap="xl" wrap="wrap">
          <Text size="sm">
            <strong>負責人：</strong>
            {ownerUser?.displayName ?? task.ownerId}
          </Text>
          {task.schedule?.due && (
            <Text size="sm">
              <strong>截止：</strong>
              {formatDateTime(task.schedule.due)}
            </Text>
          )}
          <Text size="sm">
            <strong>建立：</strong>
            {formatDateTime(task.createdAt)}
          </Text>
        </Group>
      </Stack>

      <Divider />

      <TaskStatusFlow task={task} />

      <Tabs defaultValue="description">
        <Tabs.List>
          <Tabs.Tab value="description">描述</Tabs.Tab>
          <Tabs.Tab value="assignees">指派人員</Tabs.Tab>
          <Tabs.Tab value="qa">QA 審核</Tabs.Tab>
          <Tabs.Tab value="comments">{t('task.comments')} ({comments.length})</Tabs.Tab>
          {task.attributes && Object.keys(task.attributes).length > 0 && (
            <Tabs.Tab value="attributes">型態屬性</Tabs.Tab>
          )}
        </Tabs.List>

        <Tabs.Panel value="description" pt="md">
          {!isEditingDesc ? (
            <Stack>
              {task.descriptionMarkdown ? (
                <MarkdownRenderer content={task.descriptionMarkdown} />
              ) : (
                <Text c="dimmed">尚無描述</Text>
              )}
              <Button
                variant="subtle"
                size="xs"
                onClick={() => {
                  setDescDraft(task.descriptionMarkdown ?? '')
                  setIsEditingDesc(true)
                }}
                style={{ alignSelf: 'flex-start' }}
              >
                {t('app.edit')}
              </Button>
            </Stack>
          ) : (
            <Stack>
              <Textarea
                value={descDraft}
                onChange={(e) => setDescDraft(e.currentTarget.value)}
                rows={8}
                placeholder="支援 Markdown 格式..."
              />
              <Group>
                <Button size="sm" onClick={handleSaveDesc} loading={updateTaskMutation.isPending}>
                  {t('app.save')}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setIsEditingDesc(false)}
                >
                  {t('app.cancel')}
                </Button>
              </Group>
            </Stack>
          )}
        </Tabs.Panel>

        <Tabs.Panel value="assignees" pt="md">
          <AssigneeManager task={task} />
        </Tabs.Panel>

        <Tabs.Panel value="qa" pt="md">
          <QaReviewPanel task={task} />
          {!task.qaReviewPolicy?.dualSignRequired && (
            <Text c="dimmed">此任務不需要 QA 雙簽審核</Text>
          )}
        </Tabs.Panel>

        <Tabs.Panel value="comments" pt="md">
          <Stack gap="md">
            {comments.map((comment) => (
              <Paper key={comment.id} withBorder p="sm" radius="md">
                <Stack gap="xs">
                  <Group justify="space-between">
                    <Text size="sm" fw={600}>
                      {usersMap.get(comment.authorId)?.displayName ?? comment.authorId}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {formatDateTime(comment.createdAt)}
                    </Text>
                  </Group>
                  <Text size="sm">{comment.content}</Text>
                </Stack>
              </Paper>
            ))}

            <Stack gap="xs">
              <Textarea
                placeholder={t('task.addComment')}
                value={commentText}
                onChange={(e) => setCommentText(e.currentTarget.value)}
                rows={3}
              />
              <Button
                onClick={handleAddComment}
                loading={addCommentMutation.isPending}
                disabled={!commentText.trim()}
                style={{ minHeight: 44, alignSelf: 'flex-end' }}
              >
                {t('task.addComment')}
              </Button>
            </Stack>
          </Stack>
        </Tabs.Panel>

        {task.attributes && Object.keys(task.attributes).length > 0 && (
          <Tabs.Panel value="attributes" pt="md">
            <Stack gap="xs">
              {Object.entries(task.attributes).map(([key, value]) => (
                <Group key={key} justify="space-between">
                  <Text size="sm" fw={600}>
                    {key}
                  </Text>
                  <Text size="sm">{String(value)}</Text>
                </Group>
              ))}
            </Stack>
          </Tabs.Panel>
        )}
      </Tabs>
    </Stack>
  )
}
