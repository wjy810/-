import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { JobMatch } from '../types'

const api = vi.hoisted(() => ({
  fetchJobMatch: vi.fn(),
  fetchMatchCapabilities: vi.fn(),
  resumeAnalysis: vi.fn(),
  cancelAnalysis: vi.fn(),
}))
vi.mock('../services/jobMatchApi', () => api)
const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'match-1' }, query: {} }),
  useRouter: () => router,
}))
import JobMatchAnalyzingPage from './JobMatchAnalyzingPage.vue'

class FakeEventSource {
  addEventListener() {}
  close() {}
}

function match(overrides: Partial<JobMatch> = {}): JobMatch {
  return {
    id: 'match-1', status: 'ANALYZING', version: 3, progress: 72, checkpoint: 'AI_SEMANTIC_ANALYSIS',
    jobId: 'job-1', jobVersionId: 'jv-1', title: 'Java 后端工程师', company: '合成科技',
    requirements: [], clarifications: [], evidenceMode: 'NONE',
    createdAt: '2026-10-06T08:00:00Z', updatedAt: '2026-10-06T08:00:05Z',
    ...overrides,
  }
}

function mountPage() {
  return mount(JobMatchAnalyzingPage, { global: { stubs: { UiIllustration: true, UiDrawer: true } } })
}

describe('analysis progress', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('EventSource', FakeEventSource)
    Object.values(api).forEach(mock => mock.mockReset())
    router.push.mockReset()
    api.fetchMatchCapabilities.mockResolvedValue({ quota: { remainingUnits: 3 } })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('shows the server percentage and stage, and does not advance it on its own', async () => {
    api.fetchJobMatch.mockResolvedValue(match())
    const wrapper = mountPage()
    await flushPromises()
    const ring = wrapper.get('[role="progressbar"]')
    expect(ring.attributes('aria-valuenow')).toBe('72')
    expect(wrapper.text()).toContain('AI 正在分析匹配优势与缺口')
    expect(wrapper.text()).not.toContain('预计')

    // Time passing without a new server state changes nothing.
    await vi.advanceTimersByTimeAsync(10_000)
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('72')
    expect(wrapper.findAll('.jm-checkpoint.is-done')).toHaveLength(2)
    expect(wrapper.findAll('.jm-checkpoint.is-active')).toHaveLength(1)
  })

  it('moves only when the server reports a new checkpoint', async () => {
    api.fetchJobMatch.mockResolvedValueOnce(match())
      .mockResolvedValue(match({ progress: 88, checkpoint: 'FACT_VALIDATION', updatedAt: '2026-10-06T08:00:20Z' }))
    const wrapper = mountPage()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2_100)
    await flushPromises()
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('88')
    expect(wrapper.text()).toContain('正在核对事实来源与建议')
  })

  it('shows an indeterminate state when the server gives no percentage', async () => {
    api.fetchJobMatch.mockResolvedValue(match({ progress: 0, checkpoint: 'FREEZE_INPUTS' }))
    const wrapper = mountPage()
    await flushPromises()
    const ring = wrapper.get('[role="progressbar"]')
    expect(ring.classes()).toContain('is-indeterminate')
    expect(ring.attributes('aria-valuenow')).toBeUndefined()
    expect(ring.text()).not.toMatch(/\d+%/)
    expect(wrapper.text()).toContain('已冻结输入，等待后台开始')
  })

  it('explains a pause in plain words and keeps the code in technical details', async () => {
    api.fetchJobMatch.mockResolvedValue(match({ status: 'ANALYSIS_PAUSED', checkpoint: 'ANALYSIS_PAUSED', errorCode: 'JOB_MATCH_AI_FAILED' }))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('AI 服务这次没有完成分析')
    expect(wrapper.get('details.jm-tech-details').text()).toContain('JOB_MATCH_AI_FAILED')
    expect(wrapper.get('.jm-progress-log .jm-log-line').text()).not.toContain('JOB_MATCH_AI_FAILED')
  })

  it('shows an error with retry when the first load fails, not a 0% ring', async () => {
    api.fetchJobMatch.mockRejectedValueOnce(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('分析状态读取失败')

    api.fetchJobMatch.mockResolvedValue(match())
    await wrapper.get('.ui-error button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('72')
  })

  it('offers retry, cancel and the earlier report when a run is paused', async () => {
    api.fetchJobMatch.mockResolvedValue(match({ status: 'ANALYSIS_PAUSED', checkpoint: 'ANALYSIS_PAUSED', reportId: 'report-1', reportVersions: 2 }))
    api.cancelAnalysis.mockResolvedValue(match({ status: 'CANCELLED', version: 4, reportId: 'report-1', reportVersions: 2 }))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('[data-testid="retry-analysis"]').text()).toContain('重试分析')
    await wrapper.findAll('button').find(button => button.text() === '查看上一版报告')!.trigger('click')
    expect(router.push).toHaveBeenCalledWith({ name: 'job-match-report', params: { id: 'match-1' } })
    await wrapper.get('[data-testid="cancel-paused"]').trigger('click')
    await flushPromises()
    expect(api.cancelAnalysis).toHaveBeenCalledWith('match-1', 3)
    await wrapper.get('[data-testid="reanalyze"]').trigger('click')
    expect(router.push).toHaveBeenLastCalledWith({ name: 'job-match-new', query: { id: 'match-1' } })
  })

  it('points a first run that paused to its rule preview', async () => {
    api.fetchJobMatch.mockResolvedValue(match({ status: 'ANALYSIS_PAUSED', checkpoint: 'ANALYSIS_PAUSED', reportId: 'report-1', reportVersions: 0 }))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('已有按关键词得到的规则预估')
    expect(wrapper.findAll('button').some(button => button.text() === '查看规则预估')).toBe(true)
  })
})
