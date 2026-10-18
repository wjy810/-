import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Router } from 'vue-router'
import type { ApiError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ fetchMembership: vi.fn() }))
vi.mock('./services/membershipApi', () => api)
const client = vi.hoisted(() => ({ handler: null as null | ((error: ApiError) => void) }))
vi.mock('@/shared/api/client', () => ({ setPlanLimitHandler: (handler: (error: ApiError) => void) => { client.handler = handler } }))
const sonner = vi.hoisted(() => ({ info: vi.fn() }))
vi.mock('vue-sonner', () => ({ toast: sonner }))
import { installPlanLimitUpsell } from './planLimit'

const FREE = { plan: 'FREE' as const, aiMonthlyUnits: 50, activeLinks: 3 }
const PRO = { plan: 'PRO' as const, aiMonthlyUnits: 300, activeLinks: 20 }
const refusal: ApiError = { category: 'CONFLICT', reason: 'RESUME_LINK_LIMIT', message: '免费版最多同时保留 3 条有效链接' }

function router(path: string) {
  return { currentRoute: { value: { path } }, push: vi.fn() } as unknown as Router & { push: ReturnType<typeof vi.fn> }
}

describe('plan-limit upsell', () => {
  beforeEach(() => {
    api.fetchMembership.mockReset()
    sonner.info.mockReset()
  })

  it('offers the free plan a way to upgrade, with the account\'s own numbers', async () => {
    api.fetchMembership.mockResolvedValue({ plan: 'FREE', free: FREE, pro: PRO })
    const target = router('/applications')
    installPlanLimitUpsell(target, () => true)
    client.handler!(refusal)
    await flushPromises()
    expect(sonner.info).toHaveBeenCalledTimes(1)
    const [title, options] = sonner.info.mock.calls[0]!
    expect(title).toBe('免费版最多同时保留 3 条有效链接')
    expect(options.description).toContain('20 条')
    options.action.onClick()
    expect(target.push).toHaveBeenCalledWith('/account/membership')
  })

  it('stays quiet for a pro account, an operator, or on the membership page itself', async () => {
    api.fetchMembership.mockResolvedValue({ plan: 'PRO', free: FREE, pro: PRO })
    installPlanLimitUpsell(router('/applications'), () => true)
    client.handler!(refusal)
    await flushPromises()
    installPlanLimitUpsell(router('/applications'), () => false)
    client.handler!(refusal)
    installPlanLimitUpsell(router('/account/membership'), () => true)
    client.handler!(refusal)
    await flushPromises()
    expect(api.fetchMembership).toHaveBeenCalledTimes(1)
    expect(sonner.info).not.toHaveBeenCalled()
  })
})
