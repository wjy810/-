import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MockInterviewHomePage from '@/features/mock-interview/pages/MockInterviewHomePage.vue'
import JobMatchHomePage from '@/features/job-match/pages/JobMatchHomePage.vue'
import { fetchMockInterviewDashboard, listMockInterviews } from '@/features/mock-interview/services/mockInterviewApi'
import { fetchMatchDashboard } from '@/features/job-match/services/jobMatchApi'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/shared/ui/toast', () => ({ useToastFeedback: vi.fn() }))
vi.mock('@/features/mock-interview/services/mockInterviewApi', () => ({
  fetchMockInterviewDashboard: vi.fn(), listMockInterviews: vi.fn(),
}))
vi.mock('@/features/job-match/services/jobMatchApi', () => ({ fetchMatchDashboard: vi.fn() }))

const stubs = { AppChrome: { template: '<div><slot /></div>' }, AppSelect: true, JobProofIcon: true }
afterEach(() => vi.resetAllMocks())

describe('dashboard score presentation', () => {
  it.each([0, 73, null])('preserves interview score %s while distinguishing missing data', async score => {
    vi.mocked(fetchMockInterviewDashboard).mockResolvedValue({
      total: 1, completed: 1, textCount: 1, voiceCount: 0, averageScore: score, recent: [], resumable: null,
    })
    vi.mocked(listMockInterviews).mockResolvedValue([])
    const wrapper = mount(MockInterviewHomePage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.findAll('.mi-home-stats strong')[1]?.text()).toBe(score == null ? '—' : String(score))
    wrapper.unmount()
  })

  it.each([0, 73, null])('preserves matching score %s and its percentage unit', async score => {
    vi.mocked(fetchMatchDashboard).mockResolvedValue({
      total: 1, completed: 1, averageScore: score, optimized: 0, recent: [],
    })
    const wrapper = mount(JobMatchHomePage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.findAll('.jm-stats strong')[1]?.text()).toBe(score == null ? '—' : `${score}%`)
    wrapper.unmount()
  })
})
