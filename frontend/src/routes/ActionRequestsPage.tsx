import { Stack, Title, Text, Card, Group, Badge, Button, Skeleton } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listActionRequests } from '@/api/actionRequests'
import { DispatchActionRequestModal } from '@/components/org/DispatchActionRequestModal'
import { useAuth } from '@/auth/useAuth'
import { canDispatchActionRequest } from '@/rbac/rbacGuards'

const STATUS_COLORS: Record<string, string> = {
  SUBMITTED: 'gray',
  TRIAGED: 'blue',
  IN_PROGRESS: 'cyan',
  RESOLVED: 'green',
  REJECTED: 'red',
}

export function ActionRequestsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [dispatchOpen, { open: openDispatch, close: closeDispatch }] = useDisclosure(false)

  const query = useQuery({
    queryKey: ['action-requests'],
    queryFn: () => listActionRequests({ limit: 50 }),
  })

  const items = query.data?.items ?? []

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={2}>{t('actionRequest.title')}</Title>
        {canDispatchActionRequest(user) && (
          <Button onClick={openDispatch} style={{ minHeight: 44 }}>
            {t('actionRequest.dispatch')}
          </Button>
        )}
      </Group>

      {query.isLoading && (
        <Stack>
          <Skeleton height={80} />
          <Skeleton height={80} />
        </Stack>
      )}

      {!query.isLoading && items.length === 0 && (
        <Text c="dimmed">{t('app.noData')}</Text>
      )}

      {items.map((ar) => (
        <Card
          key={ar.id}
          withBorder
          style={{ cursor: 'pointer' }}
          onClick={() => navigate(`/action-requests/${ar.id}`)}
        >
          <Group justify="space-between">
            <Text fw={600}>{ar.title}</Text>
            <Badge color={STATUS_COLORS[ar.status] ?? 'gray'}>
              {t(`actionRequest.status${ar.status}`)}
            </Badge>
          </Group>
        </Card>
      ))}

      <DispatchActionRequestModal opened={dispatchOpen} onClose={closeDispatch} />
    </Stack>
  )
}
