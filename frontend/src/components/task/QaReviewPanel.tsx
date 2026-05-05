import { Stack, Text, Badge, Button, Group, Textarea, Select } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { notifications } from '@mantine/notifications'
import type { Task, Role } from '@/api/types'
import { useSubmitTaskReview } from '@/hooks/useTasks'
import { useAuth } from '@/auth/useAuth'
import { canReviewTask } from '@/rbac/rbacGuards'
import { extractProblemMessage } from '@/lib/utils'
import { formatDateTime } from '@/lib/format'

interface QaReviewPanelProps {
  task: Task
}

export function QaReviewPanel({ task }: QaReviewPanelProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [decision, setDecision] = useState<string>('APPROVED')
  const [role, setRole] = useState<string>('')
  const [reason, setReason] = useState('')

  const submitReviewMutation = useSubmitTaskReview(task.id)

  const policy = task.qaReviewPolicy
  if (!policy?.dualSignRequired) return null

  const canReview = canReviewTask(user, task)
  const requiredRoles = policy.requiredReviewerRoles ?? []
  const reviews = task.qaReviews ?? []

  const reviewedRoles = new Set(
    reviews.filter((r) => r.decision === 'APPROVED').map((r) => r.reviewerRole)
  )
  const pendingRoles = requiredRoles.filter((r) => !reviewedRoles.has(r))

  const myEligibleRoles = canReview
    ? requiredRoles.filter((r) => user?.roles.includes(r as Role) && pendingRoles.includes(r))
    : []

  const handleSubmit = async () => {
    if (!role) return
    try {
      await submitReviewMutation.mutateAsync({ decision, role, reason })
      notifications.show({ message: '已提交審核', color: 'green' })
      setReason('')
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  const roleOptions = myEligibleRoles.map((r) => ({ value: r, label: r }))

  return (
    <Stack gap="md">
      <Text fw={600}>{t('task.qaReview')}</Text>

      <Stack gap="xs">
        <Text size="sm" fw={500}>
          必要審核角色：
        </Text>
        <Group gap="xs" wrap="wrap">
          {requiredRoles.map((r) => (
            <Badge
              key={r}
              color={reviewedRoles.has(r) ? 'green' : 'gray'}
              variant={reviewedRoles.has(r) ? 'filled' : 'outline'}
            >
              {reviewedRoles.has(r) ? '✓ ' : '○ '}
              {r}
            </Badge>
          ))}
        </Group>
      </Stack>

      {reviews.length > 0 && (
        <Stack gap="xs">
          <Text size="sm" fw={500}>
            審核記錄：
          </Text>
          {reviews.map((review, idx) => (
            <Group key={idx} gap="xs">
              <Badge color={review.decision === 'APPROVED' ? 'green' : 'red'} size="sm">
                {review.decision === 'APPROVED' ? '核准' : '退回'}
              </Badge>
              <Text size="xs">
                {review.reviewerRole} · {formatDateTime(review.at)}
              </Text>
              {review.reason && <Text size="xs" c="dimmed">「{review.reason}」</Text>}
            </Group>
          ))}
        </Stack>
      )}

      {myEligibleRoles.length > 0 && task.status === 'IN_REVIEW' && (
        <Stack gap="sm">
          <Text size="sm" fw={500}>
            提交審核：
          </Text>
          <Select
            label="審核角色"
            data={roleOptions}
            value={role}
            onChange={(v) => setRole(v ?? '')}
            required
          />
          <Select
            label="審核決定"
            data={[
              { value: 'APPROVED', label: t('task.reviewApprove') },
              { value: 'REJECTED', label: t('task.reviewReject') },
            ]}
            value={decision}
            onChange={(v) => setDecision(v ?? 'APPROVED')}
          />
          <Textarea
            label={`${t('task.reviewDecision')} 原因（退回時建議填寫）`}
            value={reason}
            onChange={(e) => setReason(e.currentTarget.value)}
            rows={2}
          />
          <Button
            onClick={handleSubmit}
            disabled={!role}
            loading={submitReviewMutation.isPending}
            style={{ minHeight: 44 }}
          >
            提交審核
          </Button>
        </Stack>
      )}
    </Stack>
  )
}
