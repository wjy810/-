import type { Inline, RichText } from './types'

const BULLET = /^\s*(?:[-*•·▪◦●]|\d{1,2}[.、)）])\s*/

/** `**bold**` is the only inline markup; everything else is literal text. */
export function parseInline(line: string): Inline[] {
  const parts: Inline[] = []
  const pattern = /\*\*(.+?)\*\*/g
  let cursor = 0
  for (const match of line.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > cursor) parts.push({ text: line.slice(cursor, index) })
    parts.push({ text: match[1]!, bold: true })
    cursor = index + match[0].length
  }
  if (cursor < line.length) parts.push({ text: line.slice(cursor) })
  return parts.filter(part => part.text.length > 0)
}

/**
 * Description text → paragraphs and bullets. Explicit bullet markers make bullets; otherwise a
 * description written as several lines is treated as a list (the way resumes are usually written),
 * and a single line stays a paragraph.
 */
export function parseRichText(value: unknown): RichText {
  const text = typeof value === 'string' ? value : ''
  const lines = text.replace(/\r\n?/g, '\n').split('\n').map(line => line.trim()).filter(Boolean)
  if (!lines.length) return { paragraphs: [], bullets: [] }
  const marked = lines.filter(line => BULLET.test(line))
  if (marked.length) {
    const paragraphs = lines.filter(line => !BULLET.test(line)).map(parseInline)
    return { paragraphs, bullets: marked.map(line => parseInline(line.replace(BULLET, ''))) }
  }
  if (lines.length >= 2) return { paragraphs: [], bullets: lines.map(parseInline) }
  return { paragraphs: [parseInline(lines[0]!)], bullets: [] }
}

export function isEmptyRich(value: RichText): boolean {
  return value.paragraphs.length === 0 && value.bullets.length === 0
}

export function plainText(value: RichText): string {
  return [...value.paragraphs, ...value.bullets].map(line => line.map(part => part.text).join('')).join('\n')
}
