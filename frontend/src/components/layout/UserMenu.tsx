import { Menu, Avatar, Text, Group } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { logout as apiLogout } from '@/api/auth'
import { getInitials } from '@/lib/utils'
import logger from '@/lib/logger'

export function UserMenu() {
  const { user, logout } = useAuth()
  const { t } = useTranslation()
  const navigate = useNavigate()

  const handleLogout = async () => {
    try {
      // POST /auth/logout — no body; backend reads the refresh_token cookie,
      // adds its jti to the revoke blacklist, and returns Max-Age=0 for all
      // three auth cookies (ADR-0015 §Decision).
      await apiLogout()
    } catch (err) {
      logger.warn('Logout API failed, clearing local state anyway', err)
    } finally {
      logout()
      navigate('/login')
    }
  }

  if (!user) return null

  return (
    <Menu shadow="md" width={200}>
      <Menu.Target>
        <Group style={{ cursor: 'pointer' }} gap="xs" data-testid="user-menu-trigger">
          <Avatar color="blue" radius="xl" size="sm">
            {getInitials(user.displayName)}
          </Avatar>
          <Text size="sm" visibleFrom="sm" data-testid="user-display-name">
            {user.displayName}
          </Text>
        </Group>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>{user.accountName}</Menu.Label>
        <Menu.Item onClick={() => navigate('/profile')}>{t('nav.profile')}</Menu.Item>
        <Menu.Divider />
        <Menu.Item color="red" onClick={handleLogout} data-testid="logout-menu-item">
          {t('auth.logout')}
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  )
}
