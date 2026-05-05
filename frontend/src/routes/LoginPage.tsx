import {
  TextInput,
  PasswordInput,
  Button,
  Stack,
  Title,
  Text,
  Paper,
  Center,
  Alert,
} from '@mantine/core'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { login as apiLogin } from '@/api/auth'
import { getMe } from '@/api/users'
import { useAuth } from '@/auth/useAuth'
import { decodeJwt } from '@/auth/jwtUtils'
import type { User } from '@/api/types'

const schema = z.object({
  accountName: z.string().min(1, '帳號必填').max(30),
  password: z.string().min(1, '密碼必填'),
})

type LoginFormData = z.infer<typeof schema>

export function LoginPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/'
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({ resolver: zodResolver(schema) })

  const onSubmit = async (data: LoginFormData) => {
    setError(null)
    try {
      const tokens = await apiLogin(data)
      const payload = decodeJwt(tokens.accessToken)
      let user: User | null = null
      try {
        user = await getMe()
      } catch {
        if (payload) {
          user = {
            id: payload.userId,
            rootOrgId: payload.rootOrgId,
            accountName: payload.accountName,
            displayName: payload.displayName ?? payload.accountName,
            roles: payload.roles as User['roles'],
            groupIds: payload.groupIds ?? [],
            orgManagerScopes: payload.orgManagerScopes ?? [],
            active: true,
          }
        }
      }
      if (!user) {
        setError(t('auth.loginFailed'))
        return
      }
      login(tokens.accessToken, tokens.refreshToken, user)
      navigate(from, { replace: true })
    } catch {
      setError(t('auth.loginFailed'))
    }
  }

  return (
    <Center mih="100vh" bg="gray.0">
      <Paper shadow="md" p="xl" radius="md" w={{ base: '90%', sm: 400 }}>
        <Stack gap="lg">
          <Stack gap="xs" align="center">
            <Title order={2} ta="center">
              🏭
            </Title>
            <Title order={3} ta="center">
              {t('auth.loginTitle')}
            </Title>
            <Text c="dimmed" size="sm" ta="center">
              {t('auth.loginSubtitle')}
            </Text>
          </Stack>

          {error && (
            <Alert color="red" title={t('app.error')}>
              {error}
            </Alert>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <Stack gap="md">
              <TextInput
                label={t('auth.accountName')}
                placeholder="accountName"
                required
                autoCapitalize="off"
                autoCorrect="off"
                autoComplete="username"
                error={errors.accountName?.message}
                size="md"
                styles={{ input: { minHeight: 44 } }}
                {...register('accountName')}
              />

              <PasswordInput
                label={t('auth.password')}
                placeholder="••••••••"
                required
                autoComplete="current-password"
                error={errors.password?.message}
                size="md"
                styles={{ input: { minHeight: 44 } }}
                {...register('password')}
              />

              <Button
                type="submit"
                fullWidth
                size="md"
                loading={isSubmitting}
                style={{ minHeight: 48 }}
                mt="xs"
              >
                {isSubmitting ? t('auth.loggingIn') : t('auth.loginButton')}
              </Button>
            </Stack>
          </form>
        </Stack>
      </Paper>
    </Center>
  )
}
