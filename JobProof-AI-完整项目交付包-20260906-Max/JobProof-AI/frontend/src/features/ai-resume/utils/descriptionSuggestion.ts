import type { AiDescriptionSuggestion } from '../types'

export type DescriptionSuggestionState = {
  status: 'loading' | 'ready' | 'error'
  requestId: string
  factsFingerprint: string
  candidate?: AiDescriptionSuggestion
  error?: string
}

export function descriptionSuggestionKey(cardId: string, recordIndex: number): string {
  return `${cardId}:${recordIndex}`
}

export function descriptionFactsFingerprint(facts: Record<string, unknown>): string {
  return JSON.stringify(Object.keys(facts).sort().map((key) => [key, facts[key]]))
}

export function descriptionInputIssue(cardType: string, facts: Record<string, unknown>): string {
  const contextFields: Record<string, string[]> = {
    EDUCATION: ['school', 'major', 'degree', 'description'],
    EXPERIENCE: ['company', 'role', 'description'],
    PROJECTS: ['name', 'role', 'department', 'description'],
    ORGANIZATIONS: ['name', 'role', 'department', 'description'],
    LANGUAGES: ['language', 'level', 'score', 'description'],
  }
  const hasContext = (contextFields[cardType] ?? []).some((field) => {
    const value = facts[field]
    return typeof value === 'string' ? value.trim().length > 0 : typeof value === 'number'
  })
  if (hasContext) return ''
  if (cardType === 'EDUCATION') return '请先填写学校、专业、学历或补充描述中的至少一项。'
  if (cardType === 'EXPERIENCE') return '请先填写公司、岗位或补充描述中的至少一项。'
  if (cardType === 'PROJECTS') return '请先填写项目名称、角色或补充描述中的至少一项。'
  if (cardType === 'ORGANIZATIONS') return '请先填写组织名称、角色或补充描述中的至少一项。'
  if (cardType === 'LANGUAGES') return '请先从下拉框选择一种语言。'
  return ''
}

export function mergeDescription(current: string, suggestion: string, mode: 'append' | 'replace'): string {
  const cleanSuggestion = suggestion.trim()
  if (mode === 'replace') return cleanSuggestion
  const cleanCurrent = current.trim()
  if (!cleanCurrent) return cleanSuggestion
  if (!cleanSuggestion || cleanCurrent.includes(cleanSuggestion)) return cleanCurrent
  return `${cleanCurrent}\n${cleanSuggestion}`
}
