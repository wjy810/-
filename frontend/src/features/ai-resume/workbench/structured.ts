/** Pure helpers for structured card records (education, experience, …). Behaviour-compatible with the v1 workbench. */
import type { StructuredItem } from './types'

export function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

export function clonePayload(value: Record<string, unknown> | null | undefined): Record<string, unknown> {
  return value ? JSON.parse(JSON.stringify(value)) as Record<string, unknown> : {}
}

export function blankStructuredItem(type: string): StructuredItem {
  const common = { startDate: '', endDate: '', current: false, location: '', description: '' }
  if (type === 'EDUCATION') return { school: '', major: '', degree: '', ...common }
  if (type === 'EXPERIENCE') return { company: '', role: '', ...common }
  if (type === 'PROJECTS' || type === 'ORGANIZATIONS') return { name: '', role: '', department: '', ...common }
  if (type === 'SKILLS') return { category: '', items: [], description: '' }
  if (type === 'CERTIFICATES' || type === 'HONORS') return { name: '', issuer: '', date: '', description: '' }
  return { language: '', level: '', score: '', description: '' }
}

/** Records of a payload; an empty card always shows one blank record to type into. */
export function itemsOf(type: string, payload: Record<string, unknown> | undefined): StructuredItem[] {
  const value = payload?.items
  if (Array.isArray(value) && value.length) return value as StructuredItem[]
  return [blankStructuredItem(type)]
}

/** Drops records where every field is blank (used when projecting drafts onto the preview). */
export function cleanItems(value: unknown): StructuredItem[] {
  if (!Array.isArray(value)) return []
  return value.map(item => objectRecord(item)).filter(item => Object.values(item).some(entry =>
    Array.isArray(entry) ? entry.length > 0 : typeof entry === 'boolean' ? entry : String(entry ?? '').trim().length > 0))
}

export function structuredTitle(type: string): string {
  if (type === 'EDUCATION') return '教育经历'
  if (type === 'EXPERIENCE') return '工作或实习经历'
  if (type === 'PROJECTS') return '项目经历'
  if (type === 'ORGANIZATIONS') return '社团或组织经历'
  if (type === 'SKILLS') return '技能类别'
  if (type === 'CERTIFICATES') return '证书'
  if (type === 'HONORS') return '荣誉'
  return '语言能力'
}

/** Short headline of a record for collapsed rows: "浙江大学 · 计算机科学与技术". */
export function recordHeadline(type: string, item: StructuredItem): string {
  const parts = type === 'EDUCATION'
    ? [item.school, item.major]
    : type === 'EXPERIENCE' ? [item.company, item.role]
      : type === 'SKILLS' ? [item.category]
        : type === 'LANGUAGES' ? [item.language, item.level]
          : [item.name, item.role ?? item.issuer]
  return parts.map(part => String(part ?? '').trim()).filter(Boolean).join(' · ')
}

/** Plain-text projection used by the legacy `resume.*` fields of the preview. */
export function formatStructuredItems(type: string, items: StructuredItem[]): string {
  return items.map((item) => {
    const heading = type === 'EDUCATION'
      ? [item.school, item.major, item.degree]
      : type === 'EXPERIENCE' ? [item.company, item.role] : [item.name, item.role]
    const end = item.current ? '至今' : item.endDate
    const meta = [[item.startDate, end].filter(Boolean).join(' - '), item.location].filter(Boolean).join(' · ')
    return [heading.filter(Boolean).join(' · '), meta, item.description].filter(Boolean).join('\n')
  }).filter(Boolean).join('\n\n')
}

export function skillItemsText(item: StructuredItem): string {
  return Array.isArray(item.items) ? item.items.map(String).join('、') : String(item.items ?? '')
}

export function parseSkillItems(value: string): string[] {
  return value.split(/[、,，\n]/).map(item => item.trim()).filter(Boolean)
}

/** Moves `from` to `to`, keeping every other element's relative order. */
export function reorder<T>(items: readonly T[], from: number, to: number): T[] {
  const next = [...items]
  if (from < 0 || from >= next.length || to < 0 || to >= next.length || from === to) return next
  const [moved] = next.splice(from, 1)
  next.splice(to, 0, moved as T)
  return next
}
