import {
  Title,
  Grid,
  Stack,
  Text,
  Paper,
  Skeleton,
  Badge,
  Group,
} from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { useDailyBoard } from '@/hooks/useDailyBoard'
import { TaskCard } from '@/components/task/TaskCard'
import type { Task } from '@/api/types'

interface SectionProps {
  title: string
  tasks: Task[]
  isLoading: boolean
  badgeColor?: string
}

function Section({ title, tasks, isLoading, badgeColor = 'blue' }: SectionProps) {
  const { t } = useTranslation()

  return (
    <Paper withBorder p="md" radius="md">
      <Stack gap="md">
        <Group justify="space-between">
          <Text fw={700} size="lg">
            {title}
          </Text>
          <Badge color={badgeColor} size="lg">
            {isLoading ? '...' : tasks.length}
          </Badge>
        </Group>

        {isLoading && (
          <Stack gap="xs">
            <Skeleton height={80} radius="md" />
            <Skeleton height={80} radius="md" />
          </Stack>
        )}

        {!isLoading && tasks.length === 0 && (
          <Text c="dimmed" size="sm">
            {t('dailyBoard.noTasks')}
          </Text>
        )}

        {!isLoading && tasks.map((task) => <TaskCard key={task.id} task={task} />)}
      </Stack>
    </Paper>
  )
}

export function DailyBoardPage() {
  const { t } = useTranslation()
  const { isLoading, data } = useDailyBoard()

  return (
    <Stack gap="lg">
      <Title order={2}>{t('dailyBoard.title')}</Title>

      <Grid>
        <Grid.Col span={{ base: 12, md: 6 }}>
          <Section
            title={t('dailyBoard.myOwned')}
            tasks={data.myOwnedTasks}
            isLoading={isLoading}
            badgeColor="blue"
          />
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 6 }}>
          <Section
            title={t('dailyBoard.myAssigned')}
            tasks={data.myAssignedTasks}
            isLoading={isLoading}
            badgeColor="cyan"
          />
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 6 }}>
          <Section
            title={t('dailyBoard.groupOverdue')}
            tasks={data.groupOverdueTasks}
            isLoading={isLoading}
            badgeColor="red"
          />
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 6 }}>
          <Section
            title={t('dailyBoard.pendingReview')}
            tasks={data.pendingReviewTasks}
            isLoading={isLoading}
            badgeColor="yellow"
          />
        </Grid.Col>
      </Grid>
    </Stack>
  )
}
