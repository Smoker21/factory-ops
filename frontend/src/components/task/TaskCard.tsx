import { Card, Badge, Text, Group, Stack } from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Task } from '@/api/types'
import { formatDate } from '@/lib/format'

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

interface TaskCardProps {
  task: Task
}

export function TaskCard({ task }: TaskCardProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <Card
      shadow="sm"
      padding="md"
      radius="md"
      withBorder
      style={{ cursor: 'pointer' }}
      onClick={() => navigate(`/tasks/${task.id}`)}
    >
      <Stack gap="xs">
        <Group justify="space-between" wrap="nowrap">
          <Text fw={600} size="sm" lineClamp={2} style={{ flex: 1 }}>
            {task.title}
          </Text>
          <Badge color={PRIORITY_COLORS[task.priority]} size="xs" variant="filled">
            {t(`task.priority${task.priority}`)}
          </Badge>
        </Group>

        <Group gap="xs" wrap="wrap">
          <Badge color={STATUS_COLORS[task.status]} size="sm">
            {t(`task.status${task.status}`)}
          </Badge>
          {task.qaReviewPolicy?.dualSignRequired && (
            <Badge color="purple" size="xs" variant="outline">
              雙簽
            </Badge>
          )}
          {task.overdue && (
            <Badge color="red" size="xs">
              逾期
            </Badge>
          )}
        </Group>

        {task.schedule?.due && (
          <Text size="xs" c="dimmed">
            截止：{formatDate(task.schedule.due)}
          </Text>
        )}
      </Stack>
    </Card>
  )
}
