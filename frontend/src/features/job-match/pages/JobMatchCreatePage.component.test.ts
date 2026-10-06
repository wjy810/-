import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  authorizeEvidence: vi.fn(), confirmResumeImport: vi.fn(), createResumeImport: vi.fn(), createTextMatch: vi.fn(),
  createUrlMatch: vi.fn(), fetchEvidenceRecommendations: vi.fn(), fetchJobMatch: vi.fn(), fetchMatchCapabilities: vi.fn(),
  fetchResumeImport: vi.fn(), fetchResumeOptions: vi.fn(), selectMatchResume: vi.fn(), startAnalysis: vi.fn(),
  updateJdStructure: vi.fn(), uploadJd: vi.fn(), uploadMatchResume: vi.fn(),
}))
const route = vi.hoisted(() => ({ params: {}, query: {} as Record<string, string> }))
vi.mock('../services/jobMatchApi', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))
import JobMatchCreatePage from './JobMatchCreatePage.vue'

function capabilities(minJdChars = 300) {
  return {
    enabled: true, ocrAvailable: false, jdSources: ['TEXT'], resumeSources: [], maxFileBytes: 1, minJdChars, maxJdChars: 10_000,
    quota: { id: 'q', periodKey: 'p', grantedUnits: 5, usedUnits: 0, heldUnits: 0, remainingUnits: 5, versionNo: 0 },
  }
}

function mountPage() {
  return mount(JobMatchCreatePage, { global: { stubs: { UiIllustration: true, AppDrawer: true, Transition: false } } })
}

describe('job match creation', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    route.query = {}
  })

  it('says how many characters are missing instead of silently disabling the button', async () => {
    api.fetchMatchCapabilities.mockResolvedValue(capabilities())
    const wrapper = mountPage()
    await flushPromises()
    const submit = () => wrapper.findAll('.jm-wizard__footer button').find(button => button.text().includes('解析岗位要求'))!
    expect(wrapper.get('#jm-jd-hint').text()).toBe('还差 300 字（至少 300 字，不计空格）')
    expect(submit().attributes('disabled')).toBeDefined()

    await wrapper.get('textarea').setValue('岗'.repeat(120) + '   \n' + '位'.repeat(80))
    expect(wrapper.get('#jm-jd-hint').text()).toBe('还差 100 字（至少 300 字，不计空格）')
    expect(submit().attributes('aria-describedby')).toBe('jm-jd-hint')

    await wrapper.get('textarea').setValue('岗'.repeat(300))
    expect(wrapper.find('#jm-jd-hint').exists()).toBe(false)
    expect(submit().attributes('disabled')).toBeUndefined()
  })

  it('uses the minimum length the server reports', async () => {
    api.fetchMatchCapabilities.mockResolvedValue(capabilities(120))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('#jm-jd-hint').text()).toBe('还差 120 字（至少 120 字，不计空格）')
  })

  it('shows a failed restore as an error instead of a blank form', async () => {
    route.query = { id: 'match-1' }
    api.fetchMatchCapabilities.mockResolvedValue(capabilities())
    api.fetchJobMatch.mockRejectedValue(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('岗位匹配读取失败')
    expect(wrapper.find('textarea').exists()).toBe(false)
  })
})
