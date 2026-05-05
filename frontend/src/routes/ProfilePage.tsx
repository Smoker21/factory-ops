import { Stack, Title, Text, Paper, Group, Badge, Avatar } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/auth/useAuth'
import { getInitials } from '@/lib/utils'

export function ProfilePage() {
  const { t } = useTranslation()
  const { user } = useAuth()

  if (!user) return null

  return (
    <Stack gap="lg">
      <Title order={2}>{t('nav.profile')}</Title>

      <Paper withBorder p="xl" maw={500}>
        <Stack gap="md">
          <Group>
            <Avatar size="xl" color="blue" radius="xl">
              {getInitials(user.displayName)}
            </Avatar>
            <Stack gap="xs">
              <Text fw={700} size="xl">
                {user.displayName}
              </Text>
              <Text c="dimmed">{user.accountName}</Text>
            </Stack>
          </Group>

          {user.email && (
            <Group>
              <Text size="sm" fw={600} w={100}>
                Email：
              </Text>
              <Text size="sm">{user.email}</Text>
            </Group>
          )}

          {user.employeeNo && (
            <Group>
              <Text size="sm" fw={600} w={100}>
                工號：
              </Text>
              <Text size="sm">{user.employeeNo}</Text>
            </Group>
          )}

          <Stack gap="xs">
            <Text size="sm" fw={600}>
              角色：
            </Text>
            <Group gap="xs" wrap="wrap">
              {user.roles.map((role) => (
                <Badge key={role} size="sm">
                  {role}
                </Badge>
              ))}
            </Group>
          </Stack>

          <Group>
            <Text size="sm" fw={600} w={100}>
              狀態：
            </Text>
            <Badge color={user.active ? 'green' : 'red'}>
              {user.active ? '在職' : '離職'}
            </Badge>
          </Group>
        </Stack>
      </Paper>
    </Stack>
  )
}
