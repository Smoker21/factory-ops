import {
  AppShell,
  Burger,
  Group,
  Text,
  Alert,
  Transition,
} from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { NavBar } from './NavBar'
import { UserMenu } from './UserMenu'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { useTranslation } from 'react-i18next'

interface AppLayoutProps {
  children: React.ReactNode
}

export function AppLayout({ children }: AppLayoutProps) {
  const [opened, { toggle, close }] = useDisclosure()
  const isOnline = useOnlineStatus()
  const { t } = useTranslation()

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{
        width: 240,
        breakpoint: 'md',
        collapsed: { mobile: !opened },
      }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group>
            <Burger opened={opened} onClick={toggle} hiddenFrom="md" size="sm" />
            <Text fw={700} size="lg" c="blue">
              {t('app.title')}
            </Text>
          </Group>
          <UserMenu />
        </Group>
      </AppShell.Header>

      <AppShell.Navbar>
        <NavBar onClose={close} />
      </AppShell.Navbar>

      <AppShell.Main>
        <Transition mounted={!isOnline} transition="slide-down">
          {(styles) => (
            <Alert
              color="orange"
              mb="md"
              style={styles}
              styles={{ root: { borderRadius: 0, position: 'sticky', top: 0, zIndex: 100 } }}
            >
              {t('status.offline')}
            </Alert>
          )}
        </Transition>
        {children}
      </AppShell.Main>
    </AppShell>
  )
}
