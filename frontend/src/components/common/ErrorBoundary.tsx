import React from 'react'
import { Alert, Button, Stack, Text, Title } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import logger from '@/lib/logger'

interface ErrorBoundaryState {
  hasError: boolean
  error: Error | null
}

interface ErrorBoundaryProps {
  children: React.ReactNode
  fallback?: React.ReactNode
}

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    logger.error('ErrorBoundary caught an error', { error: error.message, info })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback
      return (
        <ErrorFallback
          error={this.state.error}
          onReset={() => this.setState({ hasError: false, error: null })}
        />
      )
    }
    return this.props.children
  }
}

function ErrorFallback({ error, onReset }: { error: Error | null; onReset: () => void }) {
  const { t } = useTranslation()
  return (
    <Stack align="center" justify="center" p="xl" mih={200}>
      <Alert color="red" title={t('app.error')} w="100%" maw={500}>
        <Text size="sm">{error?.message ?? t('error.serverError')}</Text>
      </Alert>
      <Button variant="outline" onClick={onReset}>
        {t('app.retry')}
      </Button>
    </Stack>
  )
}

export function PageError({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  const { t } = useTranslation()
  return (
    <Stack align="center" p="xl">
      <Title order={4} c="red">
        {t('app.error')}
      </Title>
      <Text>{message ?? t('error.serverError')}</Text>
      {onRetry && (
        <Button onClick={onRetry} variant="outline">
          {t('app.retry')}
        </Button>
      )}
    </Stack>
  )
}
