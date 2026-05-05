import { Button, Group } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { PageInfo } from '@/api/types'

interface CursorPagerProps {
  pageInfo?: PageInfo
  onLoadMore: () => void
  isLoading?: boolean
}

export function CursorPager({ pageInfo, onLoadMore, isLoading }: CursorPagerProps) {
  const { t } = useTranslation()

  if (!pageInfo?.hasMore) return null

  return (
    <Group justify="center" mt="md">
      <Button variant="subtle" onClick={onLoadMore} loading={isLoading}>
        {t('app.loadMore')}
      </Button>
    </Group>
  )
}
