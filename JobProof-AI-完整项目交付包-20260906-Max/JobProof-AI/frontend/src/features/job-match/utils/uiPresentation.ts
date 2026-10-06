export type JobMatchReportTab = 'overview' | 'strengths' | 'gaps' | 'evidence' | 'plan'

export const JOB_MATCH_REPORT_TABS: ReadonlyArray<{ id: JobMatchReportTab; label: string }> = [
  { id: 'overview', label: '总览' },
  { id: 'strengths', label: '匹配优势' },
  { id: 'gaps', label: '能力缺口' },
  { id: 'evidence', label: '证据覆盖' },
  { id: 'plan', label: '提升计划' },
]

export function clampMatchScore(value: number | null | undefined): number {
  if (!Number.isFinite(value)) return 0
  return Math.round(Math.min(100, Math.max(0, value ?? 0)))
}

export function stepperTrackProgress(current: number): number {
  const normalized = Math.min(4, Math.max(1, Math.round(current)))
  return (normalized - 1) * 25
}

export function wizardTransitionName(previous: number, current: number): string {
  if (current > previous) return 'jm-stage-forward'
  if (current < previous) return 'jm-stage-backward'
  return 'jm-stage-fade'
}

export function reportTabFromQuery(value: unknown): JobMatchReportTab {
  if (typeof value !== 'string') return 'overview'
  return JOB_MATCH_REPORT_TABS.some(tab => tab.id === value)
    ? value as JobMatchReportTab
    : 'overview'
}
