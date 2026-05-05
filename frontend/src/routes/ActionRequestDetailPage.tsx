import { Stack, Title, Text, Button } from '@mantine/core'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { getActionRequest } from '@/api/actionRequests'

export function ActionRequestDetailPage() {
  const { actionRequestId } = useParams<{ actionRequestId: string }>()
  const { t } = useTranslation()
  const navigate = useNavigate()

  const query = useQuery({
    queryKey: ['action-requests', actionRequestId],
    queryFn: () => getActionRequest(actionRequestId!),
    enabled: !!actionRequestId,
  })

  return (
    <Stack gap="lg">
      <Button variant="subtle" size="xs" onClick={() => navigate(-1)}>
        ← {t('app.back')}
      </Button>
      <Title order={2}>{t('actionRequest.title')}</Title>
      {query.isLoading && <Text>{t('app.loading')}</Text>}
      {query.data && (
        <Stack>
          <Text fw={700}>{query.data.title}</Text>
          <Text size="sm">狀態：{query.data.status}</Text>
          <Text size="sm">負責人：{query.data.ownerId}</Text>
        </Stack>
      )}
    </Stack>
  )
}
