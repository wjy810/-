export type DateTimeParts = {
  year: number
  month: number
  day: number
  hour: number
  minute: number
}

export type CalendarDay = {
  value: string
  year: number
  month: number
  day: number
  inCurrentMonth: boolean
  selected: boolean
  today: boolean
}

export function padDatePart(value: number): string {
  return String(value).padStart(2, '0')
}

export function monthValue(year: number, month: number): string {
  return `${year}-${padDatePart(month + 1)}`
}

export function dayValue(year: number, month: number, day: number): string {
  return `${monthValue(year, month)}-${padDatePart(day)}`
}

export function datetimeValue(parts: DateTimeParts): string {
  return `${dayValue(parts.year, parts.month, parts.day)}T${padDatePart(parts.hour)}:${padDatePart(parts.minute)}`
}

export function parseMonthValue(value?: string | null): { year: number; month: number } | null {
  const match = /^(\d{4})-(\d{2})$/.exec(value ?? '')
  if (!match) return null
  const year = Number(match[1])
  const month = Number(match[2]) - 1
  if (year < 1000 || year > 9999 || month < 0 || month > 11) return null
  return { year, month }
}

export function parseDateTimeValue(value?: string | null): DateTimeParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value ?? '')
  if (!match) return null
  const parts: DateTimeParts = {
    year: Number(match[1]),
    month: Number(match[2]) - 1,
    day: Number(match[3]),
    hour: Number(match[4]),
    minute: Number(match[5]),
  }
  const date = new Date(parts.year, parts.month, parts.day, parts.hour, parts.minute)
  if (
    date.getFullYear() !== parts.year
    || date.getMonth() !== parts.month
    || date.getDate() !== parts.day
    || parts.hour < 0
    || parts.hour > 23
    || parts.minute < 0
    || parts.minute > 59
  ) return null
  return parts
}

export function formatMonthDisplay(value?: string | null): string {
  const parsed = parseMonthValue(value)
  return parsed ? `${parsed.year}年${parsed.month + 1}月` : ''
}

export function formatDateTimeDisplay(value?: string | null): string {
  const parsed = parseDateTimeValue(value)
  return parsed
    ? `${parsed.year}年${parsed.month + 1}月${parsed.day}日 ${padDatePart(parsed.hour)}:${padDatePart(parsed.minute)}`
    : ''
}

export function buildCalendarDays(
  year: number,
  month: number,
  selectedValue = '',
  today = new Date(),
): CalendarDay[] {
  const first = new Date(year, month, 1)
  const mondayOffset = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - mondayOffset)
  const todayValue = dayValue(today.getFullYear(), today.getMonth(), today.getDate())

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    const value = dayValue(date.getFullYear(), date.getMonth(), date.getDate())
    return {
      value,
      year: date.getFullYear(),
      month: date.getMonth(),
      day: date.getDate(),
      inCurrentMonth: date.getFullYear() === year && date.getMonth() === month,
      selected: value === selectedValue,
      today: value === todayValue,
    }
  })
}
