/** Workbench content (conversation.content / version snapshots) → RenderDocument. Pure; shared by preview and print. */
import type { ResumeDesignV2 } from '../theme/design'
import { formatDate, formatRange } from './dates'
import { SECTION_TITLES } from './labels'
import { isEmptyRich, parseRichText } from './richText'
import type {
  Link, ListItem, Locale, RenderDocument, RenderHeader, RenderSection, SectionKey, SkillGroup, TimelineItem,
} from './types'
import { SECTION_KEYS } from './types'

type Record_ = Record<string, unknown>

function obj(value: unknown): Record_ {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record_ : {}
}
function str(value: unknown): string {
  return typeof value === 'string' ? value.trim() : typeof value === 'number' ? String(value) : ''
}
function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}
function join(separator: string, ...values: unknown[]): string {
  return values.map(str).filter(Boolean).join(separator)
}

const SAFE_HOST = /^[a-z0-9.-]+\.[a-z]{2,}(?::\d{2,5})?(?:[/?#][^\s]*)?$/i

/** Only http(s) links survive; bare domains get https://. */
export function toLink(raw: unknown): Link | null {
  const value = str(raw)
  if (!value) return null
  let href = value
  if (!/^https?:\/\//i.test(href)) {
    if (/^[a-z][a-z0-9+.-]*:/i.test(href) || !SAFE_HOST.test(href)) return null
    href = `https://${href}`
  }
  let url: URL
  try {
    url = new URL(href)
  } catch {
    return null
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') return null
  const label = `${url.host.replace(/^www\./, '')}${url.pathname === '/' ? '' : url.pathname.replace(/\/$/, '')}`
  const host = url.host.toLowerCase()
  const kind: Link['kind'] = host.endsWith('github.com') ? 'github' : host.endsWith('linkedin.com') ? 'linkedin' : 'web'
  return { label, href: url.href, kind }
}

function header(content: Record_, photo: string | null): RenderHeader {
  const basics = obj(content.basics)
  const name = str(basics.name)
  const links = list(basics.links).map(item => toLink(typeof item === 'object' ? obj(item).url ?? obj(item).href : item))
    .filter((link): link is Link => link !== null)
  return {
    name,
    title: str(obj(content.intentions).targetJob) || str(basics.title),
    email: str(basics.email),
    phone: str(basics.phone),
    location: str(basics.location) || str(basics.city),
    links,
    photo,
    initial: Array.from(name)[0] ?? '',
  }
}

type TimelineKey = 'experience' | 'projects' | 'education' | 'organizations'

function timeline(key: TimelineKey, raw: unknown[], design: ResumeDesignV2, locale: Locale): TimelineItem[] {
  return raw.map((entry, index): TimelineItem | null => {
    if (typeof entry === 'string') {
      const body = parseRichText(entry)
      return isEmptyRich(body) ? null : { id: `${key}-${index}`, title: '', subtitle: '', location: '', dates: '', start: '', end: '', body }
    }
    const item = obj(entry)
    const title = key === 'education' ? str(item.school) : key === 'experience' ? str(item.company) : str(item.name)
    const subtitle = key === 'education' ? join(' · ', item.major, item.degree) : join(' · ', item.role, item.department)
    const range = formatRange(item.startDate, item.endDate, item.current, design.dateFormat, locale)
    const highlights = list(item.highlights).map(str).filter(Boolean)
    const description = [str(item.description), ...highlights.map(line => `- ${line}`)].filter(Boolean).join('\n')
    const body = parseRichText(description)
    if (!title && !subtitle && isEmptyRich(body)) return null
    return { id: `${key}-${index}`, title, subtitle, location: str(item.location), dates: range.text, start: range.start, end: range.end, body }
  }).filter((item): item is TimelineItem => item !== null)
}

function skills(raw: unknown[]): SkillGroup[] {
  return raw.map((entry, index): SkillGroup | null => {
    if (typeof entry === 'string') {
      const items = entry.split(/[、,，;；/]/).map(value => value.trim()).filter(Boolean)
      return items.length ? { id: `skills-${index}`, name: '', items, body: parseRichText('') } : null
    }
    const item = obj(entry)
    const items = list(item.items).map(str).filter(Boolean)
    const name = str(item.category) || str(item.name)
    const body = parseRichText(item.description)
    if (!items.length && !name && isEmptyRich(body)) return null
    return { id: `skills-${index}`, name, items, body }
  }).filter((group): group is SkillGroup => group !== null)
}

function listItems(key: 'certificates' | 'honors' | 'languages', raw: unknown[], design: ResumeDesignV2): ListItem[] {
  return raw.map((entry, index): ListItem | null => {
    if (typeof entry === 'string') {
      return entry.trim() ? { id: `${key}-${index}`, title: entry.trim(), detail: '', date: '', body: parseRichText('') } : null
    }
    const item = obj(entry)
    const title = key === 'languages' ? str(item.language) : str(item.name)
    const detail = key === 'languages' ? join(' · ', item.level, item.score) : str(item.issuer)
    const date = key === 'languages' ? '' : formatDate(item.date, design.dateFormat)
    const body = parseRichText(item.description)
    if (!title && !detail && isEmptyRich(body)) return null
    return { id: `${key}-${index}`, title, detail, date, body }
  }).filter((item): item is ListItem => item !== null)
}

function sectionFor(key: SectionKey, content: Record_, design: ResumeDesignV2, locale: Locale, title: string): RenderSection | null {
  switch (key) {
    case 'summary': {
      const body = parseRichText(typeof content.summary === 'string' ? content.summary : obj(content.summary).text)
      return isEmptyRich(body) ? null : { kind: 'text', key, title, body }
    }
    case 'experience': case 'projects': case 'education': case 'organizations': {
      const items = timeline(key, list(content[key === 'experience' ? 'experiences' : key] ?? content[key]), design, locale)
      return items.length ? { kind: 'timeline', key, title, items } : null
    }
    case 'skills': {
      const groups = skills(list(content.skills))
      return groups.length ? { kind: 'skills', key, title, groups } : null
    }
    default: {
      const items = listItems(key, list(content[key]), design)
      return items.length ? { kind: 'list', key, title, items } : null
    }
  }
}

export interface NormalizeOptions {
  locale: Locale
  photo?: string | null
  /** Template default titles, overridden by the user's own titles. */
  templateTitles?: Partial<Record<SectionKey, string>>
  /** Keep empty sections as placeholders (workbench preview only). */
  keepEmpty?: boolean
}

export function normalize(contentRaw: unknown, design: ResumeDesignV2, options: NormalizeOptions): RenderDocument {
  const content = obj(contentRaw)
  const hidden = new Set(design.hiddenSections)
  const order = [...design.sectionOrder.filter(key => SECTION_KEYS.includes(key)), ...SECTION_KEYS.filter(key => !design.sectionOrder.includes(key))]
  const sections: RenderSection[] = []
  for (const key of order) {
    if (hidden.has(key)) continue
    const title = design.sectionTitles[key]?.trim() || options.templateTitles?.[key] || SECTION_TITLES[options.locale][key]
    const section = sectionFor(key, content, design, options.locale, title)
    if (section) sections.push(section)
  }
  return { locale: options.locale, header: header(content, options.photo ?? null), sections }
}
