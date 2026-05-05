/**
 * Unit tests for frontend utility and format functions.
 * These are pure functions with no side effects.
 */
import { describe, it, expect } from 'vitest'
import { extractProblemMessage, getInitials, cn } from '@/lib/utils'
import { formatDateTime, formatDate, isOverdue, isToday } from '@/lib/format'

describe('utils: extractProblemMessage', () => {
  it('should extract detail from axios-style error response', () => {
    const err = { response: { data: { detail: 'Resource not found', title: 'Error' } } }
    expect(extractProblemMessage(err)).toBe('Resource not found')
  })

  it('should fall back to title when detail is missing', () => {
    const err = { response: { data: { title: 'Bad Request' } } }
    expect(extractProblemMessage(err)).toBe('Bad Request')
  })

  it('should return error.message for plain Error instances', () => {
    const err = new Error('Network error')
    expect(extractProblemMessage(err)).toBe('Network error')
  })

  it('should return fallback message for unknown error shape', () => {
    expect(extractProblemMessage(null)).toBe('操作失敗，請稍後再試')
    expect(extractProblemMessage(undefined)).toBe('操作失敗，請稍後再試')
    expect(extractProblemMessage('string error')).toBe('操作失敗，請稍後再試')
  })
})

describe('utils: getInitials', () => {
  it('should return first char uppercase for a single word', () => {
    // getInitials splits by space, single-word "admin" → ['admin'] → first chars = 'A'
    expect(getInitials('admin')).toBe('A')
  })

  it('should return initials from multi-word name', () => {
    expect(getInitials('John Doe')).toBe('JD')
  })

  it('should return up to 2 chars for multi-word names', () => {
    expect(getInitials('Alice Bob Charlie')).toBe('AB')
  })

  it('should handle single-letter name', () => {
    expect(getInitials('A')).toBe('A')
  })
})

describe('utils: cn (class name builder)', () => {
  it('should join class names', () => {
    expect(cn('foo', 'bar')).toBe('foo bar')
  })

  it('should handle conditional classes', () => {
    const isHidden = false
    expect(cn('base', isHidden && 'hidden', 'active')).toBe('base active')
  })

  it('should handle empty inputs', () => {
    expect(cn()).toBe('')
  })
})

describe('format: formatDateTime', () => {
  it('should format ISO date string to YYYY/MM/DD HH:mm', () => {
    const result = formatDateTime('2026-05-04T08:30:00+08:00')
    // Accept UTC offset variation - just check format pattern
    expect(result).toMatch(/\d{4}\/\d{2}\/\d{2} \d{2}:\d{2}/)
  })

  it('should return "-" for null input', () => {
    expect(formatDateTime(null)).toBe('-')
  })

  it('should return "-" for undefined input', () => {
    expect(formatDateTime(undefined)).toBe('-')
  })
})

describe('format: formatDate', () => {
  it('should format ISO date to YYYY/MM/DD', () => {
    const result = formatDate('2026-05-04T00:00:00Z')
    expect(result).toMatch(/\d{4}\/\d{2}\/\d{2}/)
  })

  it('should return "-" for null', () => {
    expect(formatDate(null)).toBe('-')
  })
})

describe('format: isOverdue', () => {
  it('should return false when due date is in the future', () => {
    const future = new Date(Date.now() + 86400000).toISOString()
    expect(isOverdue(future)).toBe(false)
  })

  it('should return true when due date is in the past', () => {
    const past = new Date(Date.now() - 86400000).toISOString()
    expect(isOverdue(past)).toBe(true)
  })

  it('should return false for null/undefined', () => {
    expect(isOverdue(null)).toBe(false)
    expect(isOverdue(undefined)).toBe(false)
  })
})

describe('format: isToday', () => {
  it('should return true for today\'s date', () => {
    const today = new Date().toISOString()
    expect(isToday(today)).toBe(true)
  })

  it('should return false for yesterday', () => {
    const yesterday = new Date(Date.now() - 86400000).toISOString()
    expect(isToday(yesterday)).toBe(false)
  })

  it('should return false for null', () => {
    expect(isToday(null)).toBe(false)
  })
})
