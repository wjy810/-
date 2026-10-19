import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ fetch: vi.fn() }))
vi.mock('@/shared/api/capabilities', () => ({ fetchSiteCapabilities: api.fetch }))

import AiUnavailableNotice from './AiUnavailableNotice.vue'

const base = { features: { aiWorkbench: true, jobMatch: true, careerPlanning: true, aiAssistant: true }, registrationOpen: true, policies: { published: true, termsVersion: '1', privacyVersion: '1' }, operator: {} }

function mountNotice() {
  return mount(AiUnavailableNotice, {
    props: { kind: 'jobMatch', feature: '岗位匹配', fallback: '可以先看规则预估。' },
    global: { stubs: { AiUnavailableNotice: false } },
  })
}

describe('AiUnavailableNotice', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('says so when the server reports no model for the feature', async () => {
    api.fetch.mockResolvedValue({ ...base, ai: { resume: true, jobMatch: false, careerPlanning: true } })
    const wrapper = mountNotice()
    await flushPromises()
    expect(wrapper.text()).toContain('AI 服务暂时不可用')
    expect(wrapper.text()).toContain('可以先看规则预估。')
  })

  it('stays silent while AI is available or unknown', async () => {
    api.fetch.mockResolvedValue({ ...base, ai: { resume: true, jobMatch: true, careerPlanning: true } })
    expect((await (async () => { const w = mountNotice(); await flushPromises(); return w })()).text()).toBe('')
    setActivePinia(createPinia())
    api.fetch.mockRejectedValue(new Error('offline'))
    const unknown = mountNotice()
    await flushPromises()
    expect(unknown.text()).toBe('')
  })
})
