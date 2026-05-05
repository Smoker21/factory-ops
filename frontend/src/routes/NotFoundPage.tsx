import { Stack, Title, Text, Button, Center } from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export function NotFoundPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <Center mih="60vh">
      <Stack align="center" gap="md">
        <Title order={1} size="5rem" c="dimmed">
          404
        </Title>
        <Title order={3}>{t('error.notFound')}</Title>
        <Text c="dimmed">{t('error.notFoundDesc')}</Text>
        <Button onClick={() => navigate('/')} style={{ minHeight: 44 }}>
          {t('error.goHome')}
        </Button>
      </Stack>
    </Center>
  )
}
