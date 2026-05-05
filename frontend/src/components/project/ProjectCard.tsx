import { Card, Badge, Text, Group, Stack } from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Project } from '@/api/types'
import { formatDate } from '@/lib/format'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'gray',
  ACTIVE: 'blue',
  PAUSED: 'yellow',
  COMPLETED: 'green',
  CANCELLED: 'dark',
}

interface ProjectCardProps {
  project: Project
}

export function ProjectCard({ project }: ProjectCardProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <Card
      shadow="sm"
      padding="md"
      radius="md"
      withBorder
      style={{ cursor: 'pointer' }}
      onClick={() => navigate(`/projects/${project.id}`)}
    >
      <Stack gap="xs">
        <Group justify="space-between" wrap="nowrap">
          <Text fw={600} lineClamp={1} style={{ flex: 1 }}>
            {project.name}
          </Text>
          <Badge color={STATUS_COLORS[project.status]}>
            {t(`project.status${project.status}`)}
          </Badge>
        </Group>

        {project.descriptionMarkdown && (
          <Text size="sm" c="dimmed" lineClamp={2}>
            {project.descriptionMarkdown}
          </Text>
        )}

        <Group gap="xs" justify="space-between">
          {project.overdue && (
            <Badge color="red" size="xs">
              逾期
            </Badge>
          )}
          {project.schedule?.due && (
            <Text size="xs" c="dimmed">
              截止：{formatDate(project.schedule.due)}
            </Text>
          )}
        </Group>
      </Stack>
    </Card>
  )
}
