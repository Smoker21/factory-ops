import { useState } from 'react'
import {
  Group,
  Avatar,
  Text,
  ActionIcon,
  Modal,
  Stack,
  Button,
  Select,
  Badge,
  Tooltip,
} from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { useTranslation } from 'react-i18next'
import { notifications } from '@mantine/notifications'
import type { Task } from '@/api/types'
import {
  useAddTaskAssignees,
  useRemoveTaskAssignee,
  useTransferTaskOwner,
} from '@/hooks/useTasks'
import { useQuery } from '@tanstack/react-query'
import { listUsers } from '@/api/users'
import { useAuth } from '@/auth/useAuth'
import { canTransferTaskOwner } from '@/rbac/rbacGuards'
import { getInitials, extractProblemMessage } from '@/lib/utils'

interface AssigneeManagerProps {
  task: Task
}

export function AssigneeManager({ task }: AssigneeManagerProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [addOpen, { open: openAdd, close: closeAdd }] = useDisclosure(false)
  const [transferOpen, { open: openTransfer, close: closeTransfer }] = useDisclosure(false)
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)

  const addMutation = useAddTaskAssignees(task.id)
  const removeMutation = useRemoveTaskAssignee(task.id)
  const transferMutation = useTransferTaskOwner(task.id)

  const canTransfer = canTransferTaskOwner(user, task)

  const usersQuery = useQuery({
    queryKey: ['users'],
    queryFn: () => listUsers({ limit: 50 }),
  })

  const usersMap = new Map(
    (usersQuery.data?.items ?? []).map((u) => [u.id, u])
  )

  const userOptions = (usersQuery.data?.items ?? [])
    .filter((u) => !task.assignees.includes(u.id))
    .map((u) => ({ value: u.id, label: u.displayName }))

  const transferOptions = task.assignees.map((id) => {
    const u = usersMap.get(id)
    return { value: id, label: u?.displayName ?? id }
  })

  const handleAdd = async () => {
    if (!selectedUserId) return
    try {
      await addMutation.mutateAsync([selectedUserId])
      notifications.show({ message: '已加入指派人員', color: 'green' })
      closeAdd()
      setSelectedUserId(null)
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  const handleRemove = async (userId: string) => {
    if (userId === task.ownerId) {
      notifications.show({ message: '無法移除目前的負責人，請先轉移負責人', color: 'red' })
      return
    }
    try {
      await removeMutation.mutateAsync(userId)
      notifications.show({ message: '已移除指派人員', color: 'green' })
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  const handleTransfer = async () => {
    if (!selectedUserId) return
    try {
      await transferMutation.mutateAsync({ newOwnerId: selectedUserId })
      notifications.show({ message: '已轉移負責人', color: 'green' })
      closeTransfer()
      setSelectedUserId(null)
    } catch (err) {
      notifications.show({ message: extractProblemMessage(err), color: 'red' })
    }
  }

  return (
    <Stack gap="xs">
      <Group justify="space-between">
        <Text size="sm" fw={600}>
          {t('task.assignees')}
        </Text>
        <Group gap="xs">
          <Button size="xs" variant="light" onClick={openAdd} style={{ minHeight: 44 }}>
            + {t('task.addAssignee')}
          </Button>
          {canTransfer && (
            <Button
              size="xs"
              variant="outline"
              color="orange"
              onClick={() => {
                setSelectedUserId(null)
                openTransfer()
              }}
              style={{ minHeight: 44 }}
            >
              {t('task.transferOwner')}
            </Button>
          )}
        </Group>
      </Group>

      <Group gap="xs" wrap="wrap">
        {task.assignees.map((userId) => {
          const u = usersMap.get(userId)
          const isOwner = userId === task.ownerId
          return (
            <Group key={userId} gap={4} align="center">
              <Tooltip label={u?.displayName ?? userId}>
                <Avatar size="sm" radius="xl" color="blue">
                  {getInitials(u?.displayName ?? userId)}
                </Avatar>
              </Tooltip>
              {isOwner && (
                <Badge size="xs" color="blue">
                  負責人
                </Badge>
              )}
              <ActionIcon
                size="xs"
                color="red"
                variant="subtle"
                onClick={() => handleRemove(userId)}
                disabled={isOwner}
                style={{ minHeight: 24, minWidth: 24 }}
              >
                ×
              </ActionIcon>
            </Group>
          )
        })}
      </Group>

      <Modal opened={addOpen} onClose={closeAdd} title={t('task.addAssignee')} centered>
        <Stack>
          <Select
            label="選擇人員"
            placeholder="搜尋人員..."
            data={userOptions}
            value={selectedUserId}
            onChange={setSelectedUserId}
            searchable
          />
          <Group justify="flex-end">
            <Button variant="outline" onClick={closeAdd}>
              {t('app.cancel')}
            </Button>
            <Button onClick={handleAdd} disabled={!selectedUserId} loading={addMutation.isPending}>
              {t('app.confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal
        opened={transferOpen}
        onClose={closeTransfer}
        title={t('task.transferOwner')}
        centered
      >
        <Stack>
          <Text size="sm">請選擇新的任務負責人（必須已在指派人員中）</Text>
          <Select
            label="新負責人"
            data={transferOptions.filter((o) => o.value !== task.ownerId)}
            value={selectedUserId}
            onChange={setSelectedUserId}
          />
          <Group justify="flex-end">
            <Button variant="outline" onClick={closeTransfer}>
              {t('app.cancel')}
            </Button>
            <Button
              onClick={handleTransfer}
              disabled={!selectedUserId}
              loading={transferMutation.isPending}
            >
              確認轉移
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  )
}
