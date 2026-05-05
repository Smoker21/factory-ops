import { Modal, Stack, Text, Button, Group, SimpleGrid, Card, Badge } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { listProjectTemplates } from '@/api/templates'
import { useAuth } from '@/auth/useAuth'
import type { ProjectTemplate } from '@/api/types'

interface ProjectFromTemplateModalProps {
  opened: boolean
  onClose: () => void
  onSelect: (template: ProjectTemplate) => void
}

export function ProjectFromTemplateModal({
  opened,
  onClose,
  onSelect,
}: ProjectFromTemplateModalProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const rootOrgId = user?.rootOrgId ?? ''

  const templatesQuery = useQuery({
    queryKey: ['project-templates', rootOrgId, { includeGlobal: true }],
    queryFn: () => listProjectTemplates(rootOrgId, { includeGlobal: true, active: true }),
    enabled: opened && !!rootOrgId,
  })

  const templates = templatesQuery.data?.items ?? []

  return (
    <Modal opened={opened} onClose={onClose} title={t('project.fromTemplate')} size="lg" centered>
      <Stack>
        {templatesQuery.isLoading && <Text c="dimmed">{t('app.loading')}</Text>}
        {templates.length === 0 && !templatesQuery.isLoading && (
          <Text c="dimmed">{t('app.noData')}</Text>
        )}
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          {templates.map((tpl) => (
            <Card
              key={tpl.id}
              withBorder
              style={{ cursor: 'pointer' }}
              onClick={() => {
                onSelect(tpl)
                onClose()
              }}
            >
              <Stack gap="xs">
                <Group justify="space-between">
                  <Text fw={600} size="sm">
                    {tpl.name}
                  </Text>
                  <Badge size="xs" color={tpl.scope === 'GLOBAL' ? 'purple' : 'blue'}>
                    {tpl.scope === 'GLOBAL' ? t('template.global') : t('template.org')}
                  </Badge>
                </Group>
                {tpl.descriptionMarkdown && (
                  <Text size="xs" c="dimmed" lineClamp={2}>
                    {tpl.descriptionMarkdown}
                  </Text>
                )}
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
        <Group justify="flex-end">
          <Button variant="subtle" onClick={onClose}>
            {t('app.cancel')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}
