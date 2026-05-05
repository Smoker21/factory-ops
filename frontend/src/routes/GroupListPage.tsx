import { Stack, Title, SimpleGrid, Card, Text, Badge, Group, Skeleton } from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { listGroups } from '@/api/groups'
import { useAuth } from '@/auth/useAuth'

export function GroupListPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const rootOrgId = user?.rootOrgId ?? ''

  const groupsQuery = useQuery({
    queryKey: ['groups', rootOrgId],
    queryFn: () => listGroups(rootOrgId, { limit: 50 }),
    enabled: !!rootOrgId,
  })

  const groups = groupsQuery.data?.items ?? []

  return (
    <Stack gap="lg">
      <Title order={2}>{t('group.title')}</Title>

      {groupsQuery.isLoading && (
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} height={100} radius="md" />
          ))}
        </SimpleGrid>
      )}

      {!groupsQuery.isLoading && groups.length === 0 && (
        <Text c="dimmed">{t('app.noData')}</Text>
      )}

      <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
        {groups.map((group) => (
          <Card
            key={group.id}
            withBorder
            style={{ cursor: 'pointer' }}
            onClick={() => navigate(`/groups/${group.id}`)}
          >
            <Stack gap="xs">
              <Group justify="space-between">
                <Text fw={700}>{group.name}</Text>
                <Badge>{group.type}</Badge>
              </Group>
              <Text size="sm" c="dimmed">
                {group.code}
              </Text>
              {group.settings.qa.dualSignRequired && (
                <Badge size="xs" color="purple">
                  QA 雙簽啟用
                </Badge>
              )}
            </Stack>
          </Card>
        ))}
      </SimpleGrid>
    </Stack>
  )
}
