import type { Locale } from './types'

export const DATE_FORMATS = ['YYYY.MM', 'YYYY年MM月', 'MM/YYYY', 'MMM YYYY'] as const
export type DateFormat = (typeof DATE_FORMATS)[number]

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** Accepts "2024-03", "2024-03-01", "2024.03", "2024/3", "2024年3月" or a bare year. */
export function parseYearMonth(value: unknown): { year: number; month: number | null } | null {
  const text = typeof value === 'string' ? value.trim() : typeof value === 'number' ? String(value) : ''
  const match = /^(\d{4})(?:\s*[-./年]\s*(\d{1,2}))?/.exec(text)
  if (!match) return null
  const month = match[2] ? Number(match[2]) : null
  return { year: Number(match[1]), month: month && month >= 1 && month <= 12 ? month : null }
}

export function formatDate(value: unknown, format: DateFormat): string {
  const parsed = parseYearMonth(value)
  if (!parsed) return typeof value === 'string' ? value.trim() : ''
  const { year, month } = parsed
  if (month === null) return String(year)
  const mm = String(month).padStart(2, '0')
  switch (format) {
    case 'YYYY年MM月': return `${year}年${mm}月`
    case 'MM/YYYY': return `${mm}/${year}`
    case 'MMM YYYY': return `${MONTHS[month - 1]} ${year}`
    default: return `${year}.${mm}`
  }
}

export function presentLabel(locale: Locale): string {
  return locale === 'en' ? 'Present' : '至今'
}

export function formatRange(start: unknown, end: unknown, current: unknown, format: DateFormat, locale: Locale) {
  const from = formatDate(start, format)
  const isCurrent = current === true || (typeof end === 'string' && /^(至今|present|now)$/i.test(end.trim()))
  const to = isCurrent ? presentLabel(locale) : formatDate(end, format)
  const text = from && to ? `${from} – ${to}` : from || to
  return { start: from, end: to, text }
}
