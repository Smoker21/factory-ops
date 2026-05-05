import { Badge } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { ProjectStatus } from '@/api/types'

const STATUS_COLORS: Record<ProjectStatus, string> = {
  DRAFT: 'gray',
  ACTIVE: 'blue',
  PAUSED: 'yellow',
  COMPLETED: 'green',
  CANCELLED: 'dark',
}

interface ProjectStatusBadgeProps {
  status: ProjectStatus
  size?: 'xs' | 'sm' | 'md' | 'lg'
}

export function ProjectStatusBadge({ status, size = 'sm' }: ProjectStatusBadgeProps) {
  const { t } = useTranslation()
  return (
    <Badge color={STATUS_COLORS[status]} size={size}>
      {t(`project.status${status}`)}
    </Badge>
  )
}
