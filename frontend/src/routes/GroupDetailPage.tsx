import { Stack, Title, Text, Paper, Group, Badge, Tabs, Skeleton, Button } from '@mantine/core'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { getGroup, listGroupMembers } from '@/api/groups'
import { useAuth } from '@/auth/useAuth'

export function GroupDetailPage() {
  const { groupId } = useParams<{ groupId: string }>()
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const rootOrgId = user?.rootOrgId ?? ''

  const groupQuery = useQuery({
    queryKey: ['groups', rootOrgId, groupId],
    queryFn: () => getGroup(rootOrgId, groupId!),
    enabled: !!groupId && !!rootOrgId,
  })

  const membersQuery = useQuery({
    queryKey: ['groups', rootOrgId, groupId, 'members'],
    queryFn: () => listGroupMembers(rootOrgId, groupId!),
    enabled: !!groupId && !!rootOrgId,
  })

  if (groupQuery.isLoading) {
    return <Skeleton height={200} />
  }

  const group = groupQuery.data
  if (!group) return <Text c="red">找不到群組</Text>

  const members = membersQuery.data ?? []

  return (
    <Stack gap="lg">
      <Button variant="subtle" size="xs" onClick={() => navigate(-1)}>
        ← {t('app.back')}
      </Button>

      <Stack gap="xs">
        <Group>
          <Title order={2}>{group.name}</Title>
          <Badge>{group.type}</Badge>
        </Group>
        <Text size="sm" c="dimmed">
          {group.code}
        </Text>
      </Stack>

      <Tabs defaultValue="members">
        <Tabs.List>
          <Tabs.Tab value="members">{t('group.members')}</Tabs.Tab>
          <Tabs.Tab value="settings">{t('group.settings')}</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="members" pt="md">
          <Stack>
            {members.length === 0 && <Text c="dimmed">{t('app.noData')}</Text>}
            {members.map((m) => (
              <Paper key={m.id} withBorder p="sm">
                <Group justify="space-between">
                  <Text size="sm">{m.userId}</Text>
                  <Badge size="xs">{m.role}</Badge>
                </Group>
              </Paper>
            ))}
          </Stack>
        </Tabs.Panel>

        <Tabs.Panel value="settings" pt="md">
          <Paper withBorder p="md">
            <Stack gap="md">
              <Text fw={700}>{t('group.qaSettings')}</Text>
              <Group>
                <Text size="sm">{t('group.dualSignRequired')}：</Text>
                <Badge color={group.settings.qa.dualSignRequired ? 'green' : 'gray'}>
                  {group.settings.qa.dualSignRequired ? '啟用' : '停用'}
                </Badge>
              </Group>
              {group.settings.qa.requiredReviewerRoles &&
                group.settings.qa.requiredReviewerRoles.length > 0 && (
                  <Stack gap="xs">
                    <Text size="sm">{t('group.requiredReviewerRoles')}：</Text>
                    <Group gap="xs">
                      {group.settings.qa.requiredReviewerRoles.map((r) => (
                        <Badge key={r} size="sm">
                          {r}
                        </Badge>
                      ))}
                    </Group>
                  </Stack>
                )}
            </Stack>
          </Paper>
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}
