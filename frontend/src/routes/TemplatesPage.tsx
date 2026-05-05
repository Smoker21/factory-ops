import {
  Stack,
  Title,
  Tabs,
  Text,
  SimpleGrid,
  Card,
  Badge,
  Group,
  Button,
  Skeleton,
} from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  listProjectTemplates,
  listGlobalProjectTemplates,
  forkProjectTemplate,
} from '@/api/templates'
import { useAuth } from '@/auth/useAuth'
import { notifications } from '@mantine/notifications'
import { extractProblemMessage } from '@/lib/utils'

export function TemplatesPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const rootOrgId = user?.rootOrgId ?? ''

  const orgTemplatesQuery = useQuery({
    queryKey: ['project-templates', rootOrgId, 'org'],
    queryFn: () => listProjectTemplates(rootOrgId, { active: true }),
    enabled: !!rootOrgId,
  })

  const globalTemplatesQuery = useQuery({
    queryKey: ['project-templates', 'global'],
    queryFn: () => listGlobalProjectTemplates({ active: true }),
  })

  const forkMutation = useMutation({
    mutationFn: (globalTplId: string) => forkProjectTemplate(rootOrgId, globalTplId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-templates', rootOrgId] })
      notifications.show({ message: t('template.forkSuccess'), color: 'green' })
    },
    onError: (err) => {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    },
  })

  const orgTemplates = orgTemplatesQuery.data?.items ?? []
  const globalTemplates = globalTemplatesQuery.data?.items ?? []

  return (
    <Stack gap="lg">
      <Title order={2}>{t('template.title')}</Title>

      <Tabs defaultValue="org">
        <Tabs.List>
          <Tabs.Tab value="org">{t('template.org')}</Tabs.Tab>
          <Tabs.Tab value="global">{t('template.global')}</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="org" pt="md">
          {orgTemplatesQuery.isLoading && (
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              <Skeleton height={100} />
              <Skeleton height={100} />
            </SimpleGrid>
          )}
          {!orgTemplatesQuery.isLoading && orgTemplates.length === 0 && (
            <Text c="dimmed">{t('app.noData')}</Text>
          )}
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
            {orgTemplates.map((tpl) => (
              <Card key={tpl.id} withBorder>
                <Stack gap="xs">
                  <Text fw={700}>{tpl.name}</Text>
                  <Text size="sm" c="dimmed">
                    v{tpl.version}
                  </Text>
                  {tpl.tags && tpl.tags.length > 0 && (
                    <Group gap={4}>
                      {tpl.tags.map((tag) => (
                        <Badge key={tag} size="xs" variant="outline">
                          {tag}
                        </Badge>
                      ))}
                    </Group>
                  )}
                </Stack>
              </Card>
            ))}
          </SimpleGrid>
        </Tabs.Panel>

        <Tabs.Panel value="global" pt="md">
          {globalTemplatesQuery.isLoading && (
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              <Skeleton height={100} />
              <Skeleton height={100} />
            </SimpleGrid>
          )}
          {!globalTemplatesQuery.isLoading && globalTemplates.length === 0 && (
            <Text c="dimmed">{t('app.noData')}</Text>
          )}
          <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
            {globalTemplates.map((tpl) => (
              <Card key={tpl.id} withBorder>
                <Stack gap="xs">
                  <Group justify="space-between">
                    <Text fw={700}>{tpl.name}</Text>
                    <Badge color="purple" size="xs">
                      {t('template.global')}
                    </Badge>
                  </Group>
                  <Text size="sm" c="dimmed">
                    v{tpl.version}
                  </Text>
                  <Button
                    size="xs"
                    variant="outline"
                    onClick={() => forkMutation.mutate(tpl.id)}
                    loading={forkMutation.isPending}
                    style={{ minHeight: 44 }}
                  >
                    {t('template.fork')}
                  </Button>
                </Stack>
              </Card>
            ))}
          </SimpleGrid>
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}
