import { Group, Button, Modal, Textarea, Text } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { notifications } from '@mantine/notifications'
import type { Task } from '@/api/types'
import { useChangeTaskStatus } from '@/hooks/useTasks'
import { useAuth } from '@/auth/useAuth'
import { canChangeTaskStatus } from '@/rbac/rbacGuards'
import { extractProblemMessage } from '@/lib/utils'

// Valid transitions: from -> to[]
const VALID_TRANSITIONS: Record<string, string[]> = {
  OPEN: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['BLOCKED', 'IN_REVIEW', 'DONE', 'CANCELLED'],
  BLOCKED: ['IN_PROGRESS', 'CANCELLED'],
  IN_REVIEW: ['IN_PROGRESS', 'DONE', 'CANCELLED'],
  DONE: [],
  CANCELLED: [],
}

const STATUS_LABELS: Record<string, string> = {
  OPEN: '待處理',
  IN_PROGRESS: '開始進行',
  BLOCKED: '標記阻塞',
  IN_REVIEW: '送審核',
  DONE: '標記完成',
  CANCELLED: '取消',
}

const STATUS_COLORS: Record<string, string> = {
  IN_PROGRESS: 'blue',
  BLOCKED: 'red',
  IN_REVIEW: 'yellow',
  DONE: 'green',
  CANCELLED: 'dark',
}

interface TaskStatusFlowProps {
  task: Task
}

export function TaskStatusFlow({ task }: TaskStatusFlowProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [forceCompleteOpen, { open: openForceComplete, close: closeForceComplete }] =
    useDisclosure(false)
  const [forceReason, setForceReason] = useState('')
  const [pendingStatus, setPendingStatus] = useState<string | null>(null)

  const changeStatusMutation = useChangeTaskStatus(task.id)

  const canChange = canChangeTaskStatus(user, task)
  const nextStatuses = VALID_TRANSITIONS[task.status] ?? []

  const handleStatusChange = async (newStatus: string) => {
    const isDualSignRequired = task.qaReviewPolicy?.dualSignRequired
    const isForceComplete =
      task.status === 'IN_PROGRESS' && newStatus === 'DONE' && isDualSignRequired

    if (isForceComplete) {
      setPendingStatus(newStatus)
      openForceComplete()
      return
    }

    try {
      await changeStatusMutation.mutateAsync({ status: newStatus })
      notifications.show({
        message: `狀態已更新為：${STATUS_LABELS[newStatus]}`,
        color: 'green',
      })
    } catch (err) {
      notifications.show({
        message: extractProblemMessage(err),
        color: 'red',
      })
    }
  }

  const handleForceComplete = async () => {
    if (!forceReason.trim()) return
    try {
      await changeStatusMutation.mutateAsync({
        status: pendingStatus ?? 'DONE',
        reason: forceReason,
      })
      notifications.show({ message: '已強制完成任務', color: 'green' })
      closeForceComplete()
      setForceReason('')
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  if (!canChange || nextStatuses.length === 0) return null

  return (
    <>
      <Group gap="xs" wrap="wrap">
        {nextStatuses.map((status) => (
          <Button
            key={status}
            color={STATUS_COLORS[status] ?? 'gray'}
            size="sm"
            variant={status === 'CANCELLED' ? 'outline' : 'filled'}
            onClick={() => handleStatusChange(status)}
            loading={changeStatusMutation.isPending}
            style={{ minHeight: 44 }}
          >
            {STATUS_LABELS[status]}
          </Button>
        ))}
      </Group>

      <Modal
        opened={forceCompleteOpen}
        onClose={closeForceComplete}
        title={t('task.forceComplete')}
        centered
      >
        <Text size="sm" mb="md">
          此任務需要 QA 雙簽審核。強制完成將繞過審核流程，請填寫原因。
        </Text>
        <Textarea
          label={t('task.forceCompleteReason')}
          required
          value={forceReason}
          onChange={(e) => setForceReason(e.currentTarget.value)}
          minRows={3}
          mb="md"
        />
        <Group justify="flex-end">
          <Button variant="outline" onClick={closeForceComplete}>
            {t('app.cancel')}
          </Button>
          <Button
            color="red"
            onClick={handleForceComplete}
            disabled={!forceReason.trim()}
            loading={changeStatusMutation.isPending}
          >
            {t('task.forceComplete')}
          </Button>
        </Group>
      </Modal>
    </>
  )
}
