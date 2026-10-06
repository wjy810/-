/**
 * What this deployment offers (GET /api/v1/capabilities), loaded once per page load. Until it is
 * known, or if it cannot be read, every feature counts as available: the API still enforces its
 * own switches, and a network hiccup must not hide working features.
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchSiteCapabilities, type FeatureKey, type SiteCapabilities } from '@/shared/api/capabilities'

export const FEATURE_LABELS: Record<FeatureKey, string> = {
  aiWorkbench: 'AI 简历助手',
  jobMatch: '岗位匹配',
  careerPlanning: '职业规划',
  aiAssistant: 'AI 评估',
}

export const useCapabilitiesStore = defineStore('capabilities', () => {
  const value = ref<SiteCapabilities | null>(null)
  let inflight: Promise<void> | null = null

  function load(): Promise<void> {
    if (value.value) return Promise.resolve()
    inflight ??= fetchSiteCapabilities()
      .then((loaded) => { value.value = loaded })
      .catch(() => undefined)
      .finally(() => { inflight = null })
    return inflight
  }

  function enabled(feature: FeatureKey | undefined): boolean {
    if (!feature || !value.value) return true
    return value.value.features[feature] !== false
  }

  const operator = computed(() => value.value?.operator ?? null)
  const registrationOpen = computed(() => value.value?.registrationOpen ?? null)
  const policiesPublished = computed(() => value.value?.policies.published ?? null)

  return { value, load, enabled, operator, registrationOpen, policiesPublished }
})
