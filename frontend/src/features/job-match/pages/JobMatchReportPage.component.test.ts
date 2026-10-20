import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { JobMatch, JobMatchReport } from '../types'

const api = vi.hoisted(() => ({
  fetchJobMatch: vi.fn(),
  fetchMatchReport: vi.fn(),
  fetchMatchReportVersions: vi.fn(),
  compareMatchReportVersions: vi.fn(),
  createReportExport: vi.fn(),
  createResumeOptimization: vi.fn(),
  downloadReportExport: vi.fn(),
  updateImprovement: vi.fn(),
  sendClaimFeedback: vi.fn(),
  resumeAnalysis: vi.fn(),
  cancelAnalysis: vi.fn(),
}))
const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }))
const route = vi.hoisted(() => ({ params: { id: 'match-1' }, query: {} as Record<string, string> }))
vi.mock('../services/jobMatchApi', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))
import JobMatchReportPage from './JobMatchReportPage.vue'

const RECORD_ID = '7f3c2a10-1111-2222-3333-444455556666'

function match(overrides: Partial<JobMatch> = {}): JobMatch {
  return {
    id: 'match-1', status: 'COMPLETED', version: 5, progress: 100, checkpoint: 'REPORT_READY',
    jobId: 'job-1', jobVersionId: 'jv-1', title: 'Java 后端工程师', company: '合成科技',
    requirements: [{ id: 'req-1', sequence: 2, category: 'SKILL', text: '熟悉 Spring Boot', priority: 'MUST', hardGate: false, sourceLocator: 'L4', sourceQuote: '熟悉 Spring Boot、MySQL', confidence: 90, userCorrected: false }],
    clarifications: [], createdAt: '2026-10-06T08:00:00Z', updatedAt: '2026-10-06T08:10:00Z',
    ...overrides,
  }
}

function report(overrides: Partial<JobMatchReport['report']> = {}, extra: Partial<JobMatchReport> = {}): JobMatchReport {
  return {
    id: 'report-1', matchId: 'match-1', status: 'COMPLETED', updatedAt: '2026-10-06T08:10:00Z',
    report: {
      score: 68, confidence: 74, hardGatePassed: true,
      dimensions: { coreSkills: 70 },
      requirements: [{ requirementId: 'req-1', text: '熟悉 Spring Boot', status: 'RESUME_SUPPORTED' }],
      ai: {
        strengths: [{ requirementId: 'req-1', title: 'Spring Boot 项目经验', explanation: '简历项目中有对应描述', evidenceIds: [RECORD_ID] }],
        gaps: [], evidenceMatrix: [], hardGates: [], learningPlan: [], resumeSuggestions: [], interviewTopics: [],
      },
      ...overrides,
    },
    claims: [{ id: 'claim-1', requirementId: 'req-1', conclusionType: 'STRENGTH', confidence: 74, evidenceIds: [RECORD_ID], reasoning: '项目经历直接对应', version: 0 }],
    learningPlan: [],
    evidenceSources: [{ id: RECORD_ID, sourceType: 'CAREER_RECORD', title: '订单系统重构项目' }],
    ...extra,
  }
}

function mountPage() {
  return mount(JobMatchReportPage, { global: { stubs: { UiIllustration: true, UiDrawer: true, UiDialog: true, Transition: false } } })
}

describe('job match report', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    route.query = {}
    router.push.mockReset()
    api.fetchJobMatch.mockResolvedValue(match())
    api.fetchMatchReportVersions.mockResolvedValue([])
  })

  it('builds next steps only from what the report produced', async () => {
    api.fetchMatchReport.mockResolvedValue(report())
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).not.toContain('优化简历中的项目成果')
    expect(wrapper.text()).not.toContain('按岗位主题准备模拟面试')
    expect(wrapper.text()).toContain('这份报告没有附带简历修改建议、提升任务或面试主题')

    api.fetchMatchReport.mockResolvedValue(report({
      ai: { strengths: [], gaps: [], interviewTopics: ['事务一致性', { topic: '缓存击穿' }], resumeSuggestions: [{ requirementId: 'req-1' }] },
    }))
    const withSteps = mountPage()
    await flushPromises()
    expect(withSteps.text()).toContain('1 条简历修改建议')
    expect(withSteps.text()).toContain('2 个面试准备主题')
    expect(withSteps.text()).toContain('事务一致性、缓存击穿')
  })

  it('shows no default verdict or score when the report has none', async () => {
    api.fetchMatchReport.mockResolvedValue(report({ score: undefined, confidence: undefined, ai: { strengths: [], gaps: [] } }))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('.jm-score-value').text()).toBe('—')
    expect(wrapper.text()).not.toContain('准备后投递')
    expect(wrapper.text()).toContain('这份报告没有给出投递结论')
    expect(wrapper.text()).not.toContain('可信度 0%')
  })

  it('shows strengths without an invented score and names evidence by source', async () => {
    route.query = { tab: 'strengths' }
    api.fetchMatchReport.mockResolvedValue(report())
    const wrapper = mountPage()
    await flushPromises()
    const item = wrapper.get('.jm-detail-item')
    expect(item.text()).toContain('资料库记录：订单系统重构项目')
    expect(item.text()).not.toContain('80 分')
    expect(item.text()).not.toContain(RECORD_ID.slice(0, 8))
  })

  it('shows an error with retry when the report cannot be read', async () => {
    api.fetchMatchReport.mockRejectedValue(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('岗位匹配报告读取失败')
    expect(wrapper.find('.jm-report-overview').exists()).toBe(false)
  })

  it('records a verdict on a conclusion and withdraws it on a second press', async () => {
    api.fetchMatchReport.mockResolvedValue(report())
    api.sendClaimFeedback.mockImplementation(async (_id: string, claimId: string, feedback: string, version: number) => (
      { ...report().claims[0], id: claimId, feedback, version: version + 1 }
    ))
    const wrapper = mount(JobMatchReportPage, { global: { stubs: {
      UiIllustration: true, UiDialog: true, Transition: false,
      UiDrawer: { props: ['open'], template: '<div v-if="open"><slot /></div>' },
    } } })
    await flushPromises()
    await wrapper.get('.jm-privacy .jm-secondary').trigger('click')
    const inaccurate = () => wrapper.get('.jm-feedback .is-inaccurate')
    await inaccurate().trigger('click')
    await flushPromises()
    expect(api.sendClaimFeedback).toHaveBeenCalledWith('match-1', 'claim-1', 'INACCURATE', 0)
    expect(inaccurate().attributes('aria-pressed')).toBe('true')
    expect(wrapper.text()).toContain('你已反馈 1 项')
    await inaccurate().trigger('click')
    await flushPromises()
    expect(api.sendClaimFeedback).toHaveBeenLastCalledWith('match-1', 'claim-1', 'IGNORE', 1)
    expect(inaccurate().attributes('aria-pressed')).toBe('false')
  })

  it('says a re-analysis paused, keeps the earlier report, and retries or cancels it from there', async () => {
    api.fetchJobMatch.mockResolvedValue(match({ status: 'ANALYSIS_PAUSED', errorCode: 'JOB_MATCH_AI_TIMEOUT', reportVersions: 1 }))
    api.fetchMatchReport.mockResolvedValue(report())
    api.resumeAnalysis.mockResolvedValue(match({ status: 'ANALYZING', version: 6 }))
    const wrapper = mountPage()
    await flushPromises()
    const notice = wrapper.get('[data-testid="run-notice"]').text()
    expect(notice).toContain('AI 服务响应超时')
    expect(notice).toContain('下面仍是上一版报告')
    expect(wrapper.text()).toContain('68')
    await wrapper.get('[data-testid="run-retry"]').trigger('click')
    await flushPromises()
    expect(api.resumeAnalysis).toHaveBeenCalledWith('match-1', 5)
    expect(router.push).toHaveBeenCalledWith({ name: 'job-match-analyzing', params: { id: 'match-1' } })
  })

  it('cancels a paused re-analysis and keeps showing the report', async () => {
    api.fetchJobMatch.mockResolvedValue(match({ status: 'ANALYSIS_PAUSED', reportVersions: 1 }))
    api.fetchMatchReport.mockResolvedValue(report())
    api.cancelAnalysis.mockResolvedValue(match({ status: 'CANCELLED', version: 6, reportVersions: 1 }))
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="run-cancel"]').trigger('click')
    await flushPromises()
    expect(api.cancelAnalysis).toHaveBeenCalledWith('match-1', 5)
    expect(wrapper.get('[data-testid="run-notice"]').text()).toContain('最近一次分析已取消')
    expect(wrapper.find('[data-testid="run-retry"]').exists()).toBe(false)
  })

  it('lets a finished learning task be reopened', async () => {
    route.query = { tab: 'plan' }
    const task = { id: 'task-1', gapCode: 'GENERAL', phase: 'BEFORE_INTERVIEW', title: '整理一个性能优化案例', task: '复盘一次接口优化',
      expectedOutput: '一页案例', acceptanceCriteria: '讲清前后指标', estimatedHours: 4, priority: 'P1', status: 'DONE', version: 2 }
    api.fetchMatchReport.mockResolvedValue(report({}, { learningPlan: [task] }))
    api.updateImprovement.mockResolvedValue({ ...task, status: 'TODO', version: 3 })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('已完成')
    await wrapper.get('[data-testid="undo-task"]').trigger('click')
    await flushPromises()
    expect(api.updateImprovement).toHaveBeenCalledWith('match-1', 'task-1', 'TODO', 2)
    expect(wrapper.find('[data-testid="undo-task"]').exists()).toBe(false)
    expect(wrapper.findAll('button').some(button => button.text() === '完成')).toBe(true)
  })
})
