import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-tw'

dayjs.extend(relativeTime)
dayjs.locale('zh-tw')

export function formatDateTime(dateStr?: string | null): string {
  if (!dateStr) return '-'
  return dayjs(dateStr).format('YYYY/MM/DD HH:mm')
}

export function formatDate(dateStr?: string | null): string {
  if (!dateStr) return '-'
  return dayjs(dateStr).format('YYYY/MM/DD')
}

export function formatRelative(dateStr?: string | null): string {
  if (!dateStr) return '-'
  return dayjs(dateStr).fromNow()
}

export function isOverdue(dueAt?: string | null): boolean {
  if (!dueAt) return false
  return dayjs().isAfter(dayjs(dueAt))
}

export function isToday(dateStr?: string | null): boolean {
  if (!dateStr) return false
  return dayjs(dateStr).isSame(dayjs(), 'day')
}
