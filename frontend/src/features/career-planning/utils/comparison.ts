import type { CareerRecommendation } from '../types'

export const MAX_CAREER_COMPARISONS = 3

export type ComparisonToggle = {
  ids: string[]
  limitReached: boolean
}

export function toggleCareerComparison(
  currentIds: string[],
  recommendationId: string,
  maximum = MAX_CAREER_COMPARISONS,
): ComparisonToggle {
  if (currentIds.includes(recommendationId)) {
    return { ids: currentIds.filter(id => id !== recommendationId), limitReached: false }
  }
  if (currentIds.length >= maximum) return { ids: currentIds, limitReached: true }
  return { ids: [...currentIds, recommendationId], limitReached: false }
}

export function selectedCareerComparisons(
  recommendations: CareerRecommendation[],
  ids: string[],
): CareerRecommendation[] {
  const byId = new Map(recommendations.map(item => [item.id, item]))
  return ids.map(id => byId.get(id)).filter((item): item is CareerRecommendation => Boolean(item))
}
