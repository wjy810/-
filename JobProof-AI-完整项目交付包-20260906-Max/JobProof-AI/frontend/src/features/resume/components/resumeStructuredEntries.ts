export type ResumeTimelineEntry = {
  primary: string
  secondary: string
  date: string
  location: string
  description: string
  highlights: string[]
}

export type ResumeSectionProjection = {
  value: string
  entries?: ResumeTimelineEntry[]
}

const timelineKeys = new Set(['education', 'experience', 'projects', 'organizations'])

export function projectResumeSection(
  content: Record<string, unknown>,
  key: string,
  dateFormat = 'YYYY_DOT_MM',
): ResumeSectionProjection {
  const raw = content[key === 'experience' ? 'experiences' : key]
  if (typeof raw === 'string') return { value: raw.trim() }
  if (!Array.isArray(raw)) {
    const value = objectValue(raw)
    return { value: String(value.text ?? '').trim() }
  }
  if (timelineKeys.has(key)) {
    const entries = raw.map((item) => projectTimelineEntry(key, item, dateFormat)).filter((entry) => !entryEmpty(entry))
    return { value: entries.map(resumeTimelineEntryText).filter(Boolean).join('\n\n'), entries }
  }
  return { value: raw.map((item) => renderSimpleItem(key, item, dateFormat)).filter(Boolean).join('\n\n') }
}

export function resumeTimelineEntryText(entry: ResumeTimelineEntry): string {
  const top = join(entry.primary, entry.secondary)
  const meta = join(entry.date, entry.location)
  const highlights = entry.highlights.map((line) => `• ${line}`).join('\n')
  return [top, meta, entry.description, highlights].filter(Boolean).join('\n')
}

function projectTimelineEntry(key: string, raw: unknown, dateFormat: string): ResumeTimelineEntry {
  if (typeof raw === 'string') {
    return { primary: '', secondary: '', date: '', location: '', description: raw.trim(), highlights: [] }
  }
  const item = objectValue(raw)
  const primary = key === 'education' ? text(item.school)
    : key === 'experience' ? text(item.company) : text(item.name)
  const secondary = key === 'education' ? join(item.major, item.degree)
    : join(item.role, item.department)
  const date = joinDate(item.startDate, item.current ? '至今' : item.endDate, dateFormat)
  const highlights = Array.isArray(item.highlights)
    ? item.highlights.map(text).filter(Boolean) : []
  return {
    primary,
    secondary,
    date,
    location: text(item.location),
    description: text(item.description),
    highlights,
  }
}

function renderSimpleItem(key: string, raw: unknown, dateFormat: string): string {
  if (typeof raw === 'string') return raw.trim()
  const item = objectValue(raw)
  const heading = key === 'skills' ? join(item.category, item.name)
    : ['certificates', 'honors'].includes(key) ? join(item.name, item.issuer, formatDate(item.date, dateFormat))
      : key === 'languages' ? join(item.language, item.level, item.score) : text(item.text)
  const values = key === 'skills' && Array.isArray(item.items) ? item.items.map(text).filter(Boolean).join('、') : ''
  return [heading, text(item.description), values].filter(Boolean).join('\n')
}

function joinDate(start: unknown, end: unknown, dateFormat: string): string {
  const left = formatDate(start, dateFormat)
  const right = text(end) === '至今' ? '至今' : formatDate(end, dateFormat)
  return left && right ? `${left} - ${right}` : left || right
}

function formatDate(value: unknown, dateFormat: string): string {
  const raw = text(value)
  if (!/^\d{4}-\d{2}$/.test(raw)) return raw
  const [year, month] = raw.split('-')
  return dateFormat === 'YYYY_CN_MM' ? `${year}年${Number(month)}月` : `${year}.${month}`
}

function objectValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function text(value: unknown): string {
  return String(value ?? '').trim()
}

function join(...values: unknown[]): string {
  return values.map(text).filter(Boolean).join(' · ')
}

function entryEmpty(entry: ResumeTimelineEntry): boolean {
  return !entry.primary && !entry.secondary && !entry.date && !entry.location
    && !entry.description && entry.highlights.length === 0
}
