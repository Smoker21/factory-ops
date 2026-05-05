import { Stack, Title, Grid, Text, Paper, Badge, Group, Skeleton } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { OrgTreeView } from '@/components/org/OrgTreeView'
import { useOrgTree } from '@/hooks/useOrgTree'
import type { OrgTreeNode } from '@/hooks/useOrgTree'

export function OrganizationTreePage() {
  const { t } = useTranslation()
  const [selectedNode, setSelectedNode] = useState<OrgTreeNode | null>(null)
  const orgTreeQuery = useOrgTree()

  return (
    <Stack gap="lg">
      <Title order={2}>{t('organization.title')}</Title>

      <Grid>
        <Grid.Col span={{ base: 12, md: 5 }}>
          <Paper withBorder p="md" radius="md">
            <Text fw={700} mb="md">
              組織架構
            </Text>
            {orgTreeQuery.isLoading && (
              <Stack>
                <Skeleton height={40} />
                <Skeleton height={40} />
                <Skeleton height={40} />
              </Stack>
            )}
            {orgTreeQuery.data && (
              <OrgTreeView
                nodes={orgTreeQuery.data}
                onSelect={setSelectedNode}
                selectedId={selectedNode?.id}
              />
            )}
          </Paper>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 7 }}>
          {selectedNode ? (
            <Paper withBorder p="md" radius="md">
              <Stack gap="md">
                <Group justify="space-between">
                  <Text fw={700} size="lg">
                    {selectedNode.name}
                  </Text>
                  <Badge color="blue">{selectedNode.type}</Badge>
                </Group>

                <Group gap="xl">
                  <Text size="sm">
                    <strong>代碼：</strong>
                    {selectedNode.code}
                  </Text>
                  {selectedNode.depth !== undefined && (
                    <Text size="sm">
                      <strong>層級：</strong>
                      {selectedNode.depth}
                    </Text>
                  )}
                  {selectedNode.isLeaf && (
                    <Badge color="green" variant="outline">
                      leaf（可承載工作）
                    </Badge>
                  )}
                </Group>

                {selectedNode.managerId && (
                  <Text size="sm">
                    <strong>{t('organization.manager')}：</strong>
                    {selectedNode.managerId}
                  </Text>
                )}

                {selectedNode.leaderIds && selectedNode.leaderIds.length > 0 && (
                  <Text size="sm">
                    <strong>{t('organization.leaders')}：</strong>
                    {selectedNode.leaderIds.join(', ')}
                  </Text>
                )}

                {!selectedNode.managerId && (
                  <Text size="sm" c="dimmed">
                    {t('organization.noManager')}
                  </Text>
                )}
              </Stack>
            </Paper>
          ) : (
            <Paper withBorder p="xl" radius="md" ta="center">
              <Text c="dimmed">點選左側節點查看詳情</Text>
            </Paper>
          )}
        </Grid.Col>
      </Grid>
    </Stack>
  )
}
