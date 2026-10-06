/** Renderer data model (docs/phase2/03 §3.1). Templates only ever read a RenderDocument. */

export type Locale = 'zh-CN' | 'en'

export const SECTION_KEYS = [
  'summary', 'experience', 'projects', 'education', 'organizations', 'skills', 'certificates', 'honors', 'languages',
] as const
export type SectionKey = (typeof SECTION_KEYS)[number]

export interface Inline { text: string; bold?: boolean }
/** Plain paragraphs plus bullet points; never raw HTML. */
export interface RichText { paragraphs: Inline[][]; bullets: Inline[][] }

export interface Link { label: string; href: string; kind: 'web' | 'github' | 'linkedin' | 'email' | 'phone' }

export interface RenderHeader {
  name: string
  title: string
  email: string
  phone: string
  location: string
  links: Link[]
  photo: string | null
  /** First character of the name, for monograms and seals. */
  initial: string
}

export interface TimelineItem {
  id: string
  title: string
  subtitle: string
  location: string
  /** Formatted range, e.g. "2024.03 – 2025.06". */
  dates: string
  start: string
  end: string
  body: RichText
}

export interface SkillGroup { id: string; name: string; items: string[]; body: RichText }

export interface ListItem { id: string; title: string; detail: string; date: string; body: RichText }

export type RenderSection =
  | { kind: 'text'; key: 'summary'; title: string; body: RichText }
  | { kind: 'timeline'; key: 'experience' | 'projects' | 'education' | 'organizations'; title: string; items: TimelineItem[] }
  | { kind: 'skills'; key: 'skills'; title: string; groups: SkillGroup[] }
  | { kind: 'list'; key: 'certificates' | 'honors' | 'languages'; title: string; items: ListItem[] }

export interface RenderDocument {
  locale: Locale
  header: RenderHeader
  sections: RenderSection[]
}
