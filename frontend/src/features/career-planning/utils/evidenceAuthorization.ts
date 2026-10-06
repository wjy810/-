import type { EvidenceOption } from '../types'

export const EVIDENCE_SCOPE_OPTIONS = [
  { value: 'PROFILE_INTERVIEW', label: 'AI 补充访谈' },
  { value: 'RECOMMENDATION', label: '方向推荐' },
  { value: 'CANVAS', label: '能力画布' },
  { value: 'PLAN', label: '学习计划' },
  { value: 'VALIDATION', label: '能力验证' },
] as const

export type EvidenceScope = typeof EVIDENCE_SCOPE_OPTIONS[number]['value']
export type EvidenceScopeSelections = Record<string, EvidenceScope[]>

const scopeOrder = new Map<EvidenceScope, number>(
  EVIDENCE_SCOPE_OPTIONS.map((option, index) => [option.value, index]),
)

export function isEvidenceScope(value: string): value is EvidenceScope {
  return scopeOrder.has(value as EvidenceScope)
}

export function hydrateEvidenceScopeSelections(options: EvidenceOption[]): EvidenceScopeSelections {
  return Object.fromEntries(options.map(option => [
    option.sourceId,
    option.selected
      ? [...new Set(option.scopes.filter(isEvidenceScope))].sort((left, right) => scopeOrder.get(left)! - scopeOrder.get(right)!)
      : [],
  ]))
}

export function setEvidenceScope(
  selections: EvidenceScopeSelections,
  sourceId: string,
  scope: EvidenceScope,
  selected: boolean,
): EvidenceScopeSelections {
  const current = selections[sourceId] ?? []
  const next = selected
    ? [...new Set([...current, scope])].sort((left, right) => scopeOrder.get(left)! - scopeOrder.get(right)!)
    : current.filter(value => value !== scope)
  return { ...selections, [sourceId]: next }
}

export function hasEvidenceScope(
  selections: EvidenceScopeSelections,
  sourceId: string,
  scope: EvidenceScope,
): boolean {
  return selections[sourceId]?.includes(scope) ?? false
}

export function toEvidenceAuthorizationSelections(selections: EvidenceScopeSelections) {
  return Object.entries(selections)
    .filter(([, scopes]) => scopes.length > 0)
    .map(([sourceId, scopes]) => ({ sourceId, scopes: [...scopes] }))
}

export function evidenceSelectionCount(selections: EvidenceScopeSelections): number {
  return Object.values(selections).filter(scopes => scopes.length > 0).length
}
