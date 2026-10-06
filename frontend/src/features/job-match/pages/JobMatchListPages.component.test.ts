import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ fetchMatchDashboard: vi.fn(), fetchJobMatchHistory: vi.fn() }))
vi.mock('../services/jobMatchApi', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))
import JobMatchHomePage from './JobMatchHomePage.vue'
import JobMatchHistoryPage from './JobMatchHistoryPage.vue'

const failure = () => new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503)
const stubs = { UiIllustration: true, JobProofIcon: true }

describe('job match home', () => {
  beforeEach(() => { api.fetchMatchDashboard.mockReset() })

  it('shows a failed load as an error, not as zero records', async () => {
    api.fetchMatchDashboard.mockRejectedValue(failure())
    const wrapper = mount(JobMatchHomePage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('匹配记录读取失败')
    expect(wrapper.text()).not.toContain('创建第一份岗位匹配')
    expect(wrapper.find('.jm-stats').exists()).toBe(false)
  })

  it('shows the empty state only for a successful empty load, and no average without scores', async () => {
    api.fetchMatchDashboard.mockResolvedValue({ total: 0, completed: 0, pending: 0, optimized: 0, recent: [] })
    const empty = mount(JobMatchHomePage, { global: { stubs } })
    await flushPromises()
    expect(empty.text()).toContain('创建第一份岗位匹配')

    api.fetchMatchDashboard.mockResolvedValue({
      total: 1, completed: 0, pending: 1, optimized: 0,
      recent: [{ id: 'm-1', title: '后端工程师', status: 'RESUME_CONFIRMED', progress: 40, updatedAt: '2026-10-06T08:00:00Z' }],
    })
    const one = mount(JobMatchHomePage, { global: { stubs } })
    await flushPromises()
    const average = one.findAll('.jm-stat')[1]
    expect(average.text()).toContain('—')
    expect(average.text()).not.toContain('0%')
    expect(one.findAll('.jm-stat')[2].text()).toContain('1')
  })
})

describe('job match history', () => {
  beforeEach(() => { api.fetchJobMatchHistory.mockReset() })

  it('shows a failed load as an error, not as "no records"', async () => {
    api.fetchJobMatchHistory.mockRejectedValue(failure())
    const wrapper = mount(JobMatchHistoryPage, { global: { stubs: { ...stubs, AppSelect: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('历史记录读取失败')
    expect(wrapper.text()).not.toContain('没有符合条件的匹配记录')
    expect(wrapper.text()).not.toContain('还没有岗位匹配记录')
  })

  it('distinguishes an empty history from an empty search', async () => {
    api.fetchJobMatchHistory.mockResolvedValue({ items: [], page: 0, size: 20, total: 0, totalPages: 0 })
    const wrapper = mount(JobMatchHistoryPage, { global: { stubs: { ...stubs, AppSelect: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('还没有岗位匹配记录')
  })
})
