import { NavLink, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canViewWebhooks } from '@/rbac/rbacGuards'

const navItems = [
  { label: 'nav.dailyBoard', path: '/', icon: '📋' },
  { label: 'nav.projects', path: '/projects', icon: '📁' },
  { label: 'nav.actionRequests', path: '/action-requests', icon: '⚡' },
  { label: 'nav.organizations', path: '/organizations', icon: '🏭' },
  { label: 'nav.groups', path: '/groups', icon: '👥' },
  { label: 'nav.templates', path: '/templates', icon: '📝' },
]

export function NavBar({ onClose }: { onClose?: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()

  const handleNav = (path: string) => {
    navigate(path)
    onClose?.()
  }

  const visibleItems = [
    ...navItems,
    ...(canViewWebhooks(user)
      ? [{ label: 'nav.webhooks', path: '/webhooks', icon: '🔗' }]
      : []),
  ]

  return (
    <Stack gap={4} p="xs">
      <Text size="xs" c="dimmed" fw={600} px="sm" py="xs" tt="uppercase">
        選單
      </Text>
      {visibleItems.map((item) => (
        <NavLink
          key={item.path}
          label={t(item.label)}
          leftSection={<span style={{ fontSize: '1.2rem' }}>{item.icon}</span>}
          active={
            item.path === '/'
              ? location.pathname === '/'
              : location.pathname.startsWith(item.path)
          }
          onClick={() => handleNav(item.path)}
          styles={{ root: { borderRadius: 8, minHeight: 44 } }}
        />
      ))}
    </Stack>
  )
}
