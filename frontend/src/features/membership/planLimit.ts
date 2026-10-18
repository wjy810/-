/** The upgrade nudge shown when a free account hits a plan limit anywhere in the app (docs/phase2/02). */
import type { Router } from 'vue-router'
import { setPlanLimitHandler } from '@/shared/api/client'
import { toast } from '@/shared/ui/toast'
import { fetchMembership } from './services/membershipApi'
import { planLimitCopy } from './labels'

export function installPlanLimitUpsell(router: Router, eligible: () => boolean): void {
  setPlanLimitHandler((error) => {
    if (!eligible() || router.currentRoute.value.path.startsWith('/account/membership')) return
    // The page that made the request reports the refusal itself; this only adds the way out, and only
    // to accounts that can actually upgrade (a PRO account at its limit gets no sales pitch).
    void fetchMembership().then((membership) => {
      if (membership.plan !== 'FREE') return
      const copy = planLimitCopy(error.reason, membership.free, membership.pro)
      toast.info(copy.title, {
        dedupeKey: 'plan-limit',
        description: copy.description,
        duration: 9000,
        action: { label: '了解专业版', onClick: () => void router.push('/account/membership') },
      })
    }).catch(() => undefined)
  })
}
