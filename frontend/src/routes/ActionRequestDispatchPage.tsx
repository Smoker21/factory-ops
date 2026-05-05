import { Stack, Title, Text, Button } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/useAuth'
import { canDispatchActionRequest } from '@/rbac/rbacGuards'
import { DispatchActionRequestModal } from '@/components/org/DispatchActionRequestModal'

export function ActionRequestDispatchPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [opened, { open, close }] = useDisclosure(false)

  if (!canDispatchActionRequest(user)) {
    return (
      <Stack>
        <Title order={2}>{t('actionRequest.dispatch')}</Title>
        <Text c="dimmed">{t('error.forbiddenDesc')}</Text>
      </Stack>
    )
  }

  return (
    <Stack gap="lg">
      <Title order={2}>{t('actionRequest.dispatch')}</Title>
      <Text c="dimmed">跨層派工 — 向下級 leaf 組織直接派 ActionRequest</Text>
      <Button onClick={open} style={{ minHeight: 44, alignSelf: 'flex-start' }}>
        {t('actionRequest.dispatch')}
      </Button>
      <DispatchActionRequestModal opened={opened} onClose={close} />
    </Stack>
  )
}
