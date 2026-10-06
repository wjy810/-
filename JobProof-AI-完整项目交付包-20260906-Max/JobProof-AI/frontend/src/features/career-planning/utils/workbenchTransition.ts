export const CAREER_WORKBENCH_VIEWS = ['CANVAS', 'PLAN', 'VALIDATION', 'HISTORY'] as const

export type CareerWorkbenchView = typeof CAREER_WORKBENCH_VIEWS[number]
export type CareerWorkbenchDirection = 'forward' | 'backward' | 'none'

const VIEW_QUERY: Record<CareerWorkbenchView, string> = {
  CANVAS: 'canvas',
  PLAN: 'plan',
  VALIDATION: 'validation',
  HISTORY: 'history',
}

const QUERY_VIEW = Object.fromEntries(
  Object.entries(VIEW_QUERY).map(([view, query]) => [query, view]),
) as Record<string, CareerWorkbenchView>

export function careerWorkbenchViewFromQuery(value: unknown): CareerWorkbenchView | null {
  return typeof value === 'string' ? QUERY_VIEW[value] ?? null : null
}

export function careerWorkbenchQueryValue(view: CareerWorkbenchView): string {
  return VIEW_QUERY[view]
}

export function careerWorkbenchDirection(
  from: CareerWorkbenchView,
  to: CareerWorkbenchView,
): CareerWorkbenchDirection {
  const delta = CAREER_WORKBENCH_VIEWS.indexOf(to) - CAREER_WORKBENCH_VIEWS.indexOf(from)
  return delta === 0 ? 'none' : delta > 0 ? 'forward' : 'backward'
}

export function resolveCareerWorkbenchKey(
  current: CareerWorkbenchView,
  key: string,
): CareerWorkbenchView | null {
  if (key === 'Home') return CAREER_WORKBENCH_VIEWS[0]
  if (key === 'End') return CAREER_WORKBENCH_VIEWS[CAREER_WORKBENCH_VIEWS.length - 1]

  const index = CAREER_WORKBENCH_VIEWS.indexOf(current)
  if (key === 'ArrowRight' || key === 'ArrowDown') {
    return CAREER_WORKBENCH_VIEWS[(index + 1) % CAREER_WORKBENCH_VIEWS.length]
  }
  if (key === 'ArrowLeft' || key === 'ArrowUp') {
    return CAREER_WORKBENCH_VIEWS[(index - 1 + CAREER_WORKBENCH_VIEWS.length) % CAREER_WORKBENCH_VIEWS.length]
  }
  return null
}
