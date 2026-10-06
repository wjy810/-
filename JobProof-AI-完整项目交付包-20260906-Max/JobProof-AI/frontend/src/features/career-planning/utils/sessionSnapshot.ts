import type { CareerPlanningSession } from '../types'

export function shouldAcceptCareerPlanningSnapshot(
  current: CareerPlanningSession | null,
  candidate: CareerPlanningSession,
): boolean {
  if (!current || current.id !== candidate.id) return true
  if (candidate.version < current.version) return false
  if (candidate.profile.version < current.profile.version) return false
  if (current.activeGoal?.id && current.activeGoal.id !== candidate.activeGoal?.id) return false
  if (
    current.canvas
    && candidate.canvas
    && candidate.canvas.version < current.canvas.version
  ) return false
  return true
}
