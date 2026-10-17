/** Characters Windows, macOS or Linux reject in file names, and control characters. */
// eslint-disable-next-line no-control-regex
const ILLEGAL = /[\\/:*?"<>|\u0000-\u001f\u007f]+/g
const MAX_BASE = 80

function clean(value: unknown): string {
  return typeof value === 'string' ? value.replace(ILLEGAL, ' ').replace(/\s+/g, ' ').trim() : ''
}

/**
 * A download name people recognise in their Downloads folder ("周以宁-数据分析师-简历.pdf") instead of
 * the storage key. The anonymous copy never carries the name; without name and target job the
 * resume title is used.
 */
export function resumeFileName(parts: {
  name?: unknown
  target?: unknown
  title?: unknown
  anonymous?: boolean
  extension: 'pdf' | 'docx'
}): string {
  const pieces = [parts.anonymous ? '' : clean(parts.name), clean(parts.target)].filter(Boolean)
  const base = pieces.length
    ? [...pieces, parts.anonymous ? '匿名简历' : '简历'].join('-')
    : clean(parts.title) || (parts.anonymous ? '匿名简历' : '简历')
  return `${Array.from(base).slice(0, MAX_BASE).join('').trim()}.${parts.extension}`
}
