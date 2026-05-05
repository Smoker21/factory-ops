import {
  Modal,
  Stack,
  TextInput,
  Textarea,
  Select,
  Button,
  Group,
  Text,
  Alert,
} from '@mantine/core'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { notifications } from '@mantine/notifications'
import { useQuery } from '@tanstack/react-query'
import { dispatchActionRequest } from '@/api/actionRequests'
import { listOrganizations, listOrgLeaders } from '@/api/organizations'
import { useState } from 'react'
import type { Organization } from '@/api/types'
import { extractProblemMessage } from '@/lib/utils'

const schema = z.object({
  title: z.string().min(1, '標題必填'),
  descriptionMarkdown: z.string().optional(),
  severity: z.string().optional(),
  ownerId: z.string().optional(),
})

type FormData = z.infer<typeof schema>

interface DispatchActionRequestModalProps {
  opened: boolean
  onClose: () => void
}

export function DispatchActionRequestModal({ opened, onClose }: DispatchActionRequestModalProps) {
  const { t } = useTranslation()
  const [targetOrgId, setTargetOrgId] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const leafOrgsQuery = useQuery({
    queryKey: ['orgs', { leafOnly: true }],
    queryFn: () => listOrganizations({ leafOnly: true, limit: 50 }),
    enabled: opened,
  })

  const leadersQuery = useQuery({
    queryKey: ['org-leaders', targetOrgId],
    queryFn: () => listOrgLeaders(targetOrgId!),
    enabled: !!targetOrgId,
  })

  const leafOrgs: Organization[] = leafOrgsQuery.data?.items ?? []
  const leaders = leadersQuery.data ?? []

  const orgOptions = leafOrgs.map((o) => ({ value: o.id, label: o.name }))
  const leaderOptions = leaders.map((l) => ({ value: l.id, label: l.displayName }))

  const onSubmit = async (data: FormData) => {
    if (!targetOrgId) return
    try {
      await dispatchActionRequest(targetOrgId, {
        title: data.title,
        descriptionMarkdown: data.descriptionMarkdown,
        severity: data.severity as Parameters<typeof dispatchActionRequest>[1]['severity'],
        ownerId: data.ownerId,
      })
      notifications.show({ message: '已成功派工', color: 'green' })
      reset()
      setTargetOrgId(null)
      onClose()
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={t('actionRequest.dispatch')}
      size="lg"
      centered
    >
      <form onSubmit={handleSubmit(onSubmit)}>
        <Stack>
          <Select
            label="目標組織（必須為課/leaf）"
            placeholder="選擇目標組織..."
            data={orgOptions}
            value={targetOrgId}
            onChange={(v) => {
              setTargetOrgId(v)
              setValue('ownerId', '')
            }}
            required
            searchable
          />

          {leaders.length === 0 && targetOrgId && (
            <Alert color="orange">此組織沒有設定負責人，無法派工</Alert>
          )}

          {leaders.length > 1 && (
            <Select
              label="指定負責人（目標組織有多位負責人時必填）"
              data={leaderOptions}
              value={watch('ownerId')}
              onChange={(v) => setValue('ownerId', v ?? '')}
              required
            />
          )}

          {leaders.length === 1 && (
            <Text size="sm" c="dimmed">
              負責人將自動指定為：{leaders[0].displayName}
            </Text>
          )}

          <TextInput
            label="標題"
            required
            error={errors.title?.message}
            {...register('title')}
          />

          <Textarea
            label="說明"
            rows={3}
            {...register('descriptionMarkdown')}
          />

          <Select
            label="嚴重程度"
            data={[
              { value: 'LOW', label: '低' },
              { value: 'MEDIUM', label: '中' },
              { value: 'HIGH', label: '高' },
              { value: 'CRITICAL', label: '緊急' },
            ]}
            value={watch('severity')}
            onChange={(v) => setValue('severity', v ?? '')}
          />

          <Group justify="flex-end">
            <Button variant="outline" onClick={onClose}>
              {t('app.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={!targetOrgId || leaders.length === 0}
              style={{ minHeight: 44 }}
            >
              {t('actionRequest.dispatch')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
