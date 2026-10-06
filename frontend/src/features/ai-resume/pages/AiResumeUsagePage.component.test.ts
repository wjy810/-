import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ quota: vi.fn() }))
vi.mock('../services/aiResumeApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/aiResumeApi')>(),
  fetchAiResumeQuota: api.quota,
}))

import AiResumeUsagePage from './AiResumeUsagePage.vue'

function mountPage() {
  return mount(AiResumeUsagePage, { global: { stubs: { UiIllustration: true, RouterLink: { template: '<a><slot /></a>' } } } })
}

describe('AiResumeUsagePage', () => {
  it('shows a retryable error instead of zero quota when the quota fails to load', async () => {
    api.quota.mockRejectedValueOnce(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-quota'))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('AI 额度读取失败')
    expect(wrapper.text()).toContain('req-quota')
    expect(wrapper.text()).not.toContain('0 次可用')
    expect(wrapper.text()).not.toContain('SYSTEM')
  })

  it('shows the loaded quota', async () => {
    api.quota.mockResolvedValueOnce({ id: 'q', periodKey: '2026-10', grantedUnits: 30, usedUnits: 6, heldUnits: 1, remainingUnits: 23, versionNo: 1 })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('2026 年 10 月 AI 额度')
    expect(wrapper.text()).toContain('23 次可用')
    expect(wrapper.text()).toContain('本月已使用 20%')
  })
})
