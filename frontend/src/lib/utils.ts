import { clsx, type ClassValue } from 'clsx'
import type { Problem } from '@/api/types'

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs)
}

export function extractProblemMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { data?: Problem } }
    const problem = axiosError.response?.data
    if (problem?.detail) return problem.detail
    if (problem?.title) return problem.title
  }
  if (error instanceof Error) return error.message
  return '操作失敗，請稍後再試'
}

export function getInitials(name: string): string {
  return name
    .split(' ')
    .map((n) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)
}
