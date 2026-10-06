/** Resume names are shown in lists, tabs and the workbench header; keep them short and non-empty. */
export const RESUME_TITLE_MAX = 60

export function normalizeResumeTitle(value: string): string {
  return value.replace(/\s+/g, ' ').trim()
}

/** Why `value` cannot be saved as a resume name, or '' when it can. */
export function resumeTitleIssue(value: string): string {
  const title = normalizeResumeTitle(value)
  if (!title) return '请输入简历名称'
  if (Array.from(title).length > RESUME_TITLE_MAX) return `简历名称最多 ${RESUME_TITLE_MAX} 个字`
  return ''
}

/** Case-insensitive match of a search query against a resume name. */
export function titleMatches(title: string, query: string): boolean {
  const needle = normalizeResumeTitle(query).toLowerCase()
  return !needle || title.toLowerCase().includes(needle)
}
