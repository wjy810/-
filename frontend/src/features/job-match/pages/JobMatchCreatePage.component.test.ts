import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  authorizeEvidence: vi.fn(), confirmResumeImport: vi.fn(), createResumeImport: vi.fn(), createTextMatch: vi.fn(),
  createUrlMatch: vi.fn(), fetchEvidenceRecommendations: vi.fn(), fetchJobMatch: vi.fn(), fetchMatchCapabilities: vi.fn(),
  fetchResumeImport: vi.fn(), fetchResumeOptions: vi.fn(), selectMatchResume: vi.fn(), startAnalysis: vi.fn(),
  updateJdStructure: vi.fn(), uploadJd: vi.fn(), uploadMatchResume: vi.fn(), revokeEvidence: vi.fn(),
  fetchRedactionPreview: vi.fn(),
}))
const dialogs = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/shared/ui/confirm', () => dialogs)
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
  return mount(JobMatchCreatePage, { global: { stubs: { UiIllustration: true, UiDrawer: true, Transition: false } } })
}

describe('job match creation', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    dialogs.confirm.mockReset()
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

  it('withdraws the library authorisation before analysis and returns to the evidence step', async () => {
    const authorised = {
      id: 'match-1', status: 'EVIDENCE_AUTHORIZED', version: 7, progress: 55, checkpoint: 'EVIDENCE_AUTHORIZED',
      title: 'Java 后端工程师', company: '合成科技', requirements: [], clarifications: [],
      resume: { masterId: 'm', branchId: 'b', revisionId: 'r', title: '我的简历', languageCode: 'zh-CN', contentHash: 'h', updatedAt: '2026-10-06T08:00:00Z' },
      evidenceMode: 'AUTO', authorizationId: 'auth-1', createdAt: '2026-10-06T08:00:00Z', updatedAt: '2026-10-06T08:10:00Z',
    }
    route.query = { id: 'match-1' }
    api.fetchMatchCapabilities.mockResolvedValue(capabilities())
    api.fetchJobMatch.mockResolvedValue(authorised)
    api.fetchEvidenceRecommendations.mockResolvedValue([
      { sourceType: 'CAREER_RECORD', sourceId: 'rec-1', title: '订单系统重构', strength: 'MEDIUM', relevance: 60, recommended: true },
      { sourceType: 'CAREER_RECORD', sourceId: 'rec-2', title: '校园社团', strength: 'MEDIUM', relevance: 10, recommended: false },
    ])
    api.fetchRedactionPreview.mockResolvedValue({ matchId: 'match-1', resumeRevisionId: 'r', excludedFields: [], notice: '',
      includedSources: [{ source_type: 'CAREER_RECORD', source_id: 'rec-2', title: '校园社团' }] })
    api.revokeEvidence.mockResolvedValue({ ...authorised, status: 'RESUME_CONFIRMED', version: 8, evidenceMode: null, authorizationId: null })
    dialogs.confirm.mockResolvedValue(true)
    // Default Transition stub: the step change renders at once instead of waiting for leave frames.
    const wrapper = mount(JobMatchCreatePage, { global: { stubs: { UiIllustration: true, UiDrawer: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('查看发送给 AI 的内容')
    const revoke = wrapper.findAll('button').find(button => button.text().includes('撤销资料授权'))!
    await revoke.trigger('click')
    await flushPromises()
    expect(api.revokeEvidence).toHaveBeenCalledWith('match-1', 7)
    expect(wrapper.text()).toContain('补充求职资料证据')
    // The ticks show what had been authorised, not the default recommendation.
    const ticked = wrapper.findAll('.jm-evidence-option.is-selected').map(option => option.text())
    expect(ticked).toEqual([expect.stringContaining('校园社团')])
  })

  it('turns automatic evidence into a manual choice once a tick changes', async () => {
    route.query = { id: 'match-2' }
    api.fetchMatchCapabilities.mockResolvedValue(capabilities())
    api.fetchJobMatch.mockResolvedValue({
      id: 'match-2', status: 'RESUME_CONFIRMED', version: 3, progress: 40, checkpoint: 'RESUME_CONFIRMED',
      title: '岗位', requirements: [], clarifications: [], createdAt: '2026-10-06T08:00:00Z', updatedAt: '2026-10-06T08:00:00Z',
    })
    api.fetchEvidenceRecommendations.mockResolvedValue([
      { sourceType: 'CAREER_RECORD', sourceId: 'rec-1', title: '订单系统重构', strength: 'MEDIUM', relevance: 60, recommended: true },
    ])
    api.authorizeEvidence.mockResolvedValue({ id: 'match-2', status: 'EVIDENCE_AUTHORIZED', version: 4, requirements: [], clarifications: [] })
    const wrapper = mount(JobMatchCreatePage, { global: { stubs: { UiIllustration: true, UiDrawer: true } } })
    await flushPromises()
    await wrapper.get('.jm-evidence-option input').trigger('change')
    expect(wrapper.get('.jm-choice.is-selected').text()).toContain('手动选择')
    await wrapper.findAll('button').find(button => button.text() === '确认资料范围')!.trigger('click')
    await flushPromises()
    expect(api.authorizeEvidence).toHaveBeenCalledWith('match-2', expect.objectContaining({ mode: 'MANUAL', recordIds: [] }))
  })

  it('reviews the parsed requirements: shows conflicts, adds, removes and recategorises before confirming', async () => {
    const parsed = {
      id: 'match-1', status: 'DRAFT', version: 2, progress: 20, checkpoint: 'JD_PARSED', title: '数据分析实习生', company: '合成科技',
      parseConflicts: ['REQUIREMENTS_INCOMPLETE'], clarifications: [], createdAt: '2026-10-06T08:00:00Z', updatedAt: '2026-10-06T08:10:00Z',
      requirements: [
        { id: 'r1', sequence: 1, category: 'SKILL', text: '熟练使用 SQL', priority: 'MUST', hardGate: false, sourceLocator: 'line:3', sourceQuote: '熟练使用 SQL', confidence: 90, userCorrected: false },
        { id: 'r2', sequence: 2, category: 'SKILL', text: '会用 Excel 做透视表', priority: 'IMPORTANT', hardGate: false, sourceLocator: 'line:4', sourceQuote: '会用 Excel', confidence: 84, userCorrected: false },
      ],
    }
    route.query = { id: 'match-1' }
    api.fetchMatchCapabilities.mockResolvedValue(capabilities())
    api.fetchJobMatch.mockResolvedValue(parsed)
    api.updateJdStructure.mockResolvedValue({ ...parsed, status: 'JD_PARSED', checkpoint: 'JD_CONFIRMED', version: 3, parseConflicts: [] })
    api.fetchResumeOptions.mockResolvedValue([])
    const wrapper = mount(JobMatchCreatePage, { global: { stubs: { UiIllustration: true, UiDrawer: true, Transition: false,
      UiSelect: { props: ['modelValue'], emits: ['update:modelValue'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>' } } } })
    await flushPromises()
    expect(wrapper.get('[data-testid="parse-conflicts"]').text()).toContain('只识别出 2 条岗位要求')
    const confirmButton = () => wrapper.findAll('.jm-wizard__footer button').find(button => button.text().includes('确认并选择简历'))!
    expect(confirmButton().attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="add-requirement"]').trigger('click')
    expect(wrapper.findAll('[data-testid="jd-requirement"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('第 3 条要求还没有填写')
    await wrapper.findAll('[data-testid="jd-requirement"]')[2]!.get('input[type=text]').setValue('有一段数据分析实习经历')
    await wrapper.findAll('[data-testid="jd-requirement"]')[2]!.get('select[aria-label="类别"]').setValue('EXPERIENCE')
    await wrapper.get('[data-testid="add-requirement"]').trigger('click')
    await wrapper.findAll('[data-testid="remove-requirement"]')[3]!.trigger('click')
    expect(wrapper.findAll('[data-testid="jd-requirement"]')).toHaveLength(3)
    expect(confirmButton().attributes('disabled')).toBeUndefined()

    await confirmButton().trigger('click')
    await flushPromises()
    const sent = api.updateJdStructure.mock.calls[0]![1].requirements
    expect(sent).toHaveLength(3)
    expect(sent[2]).toMatchObject({ text: '有一段数据分析实习经历', category: 'EXPERIENCE', priority: 'IMPORTANT' })
  })

  it('reads the resume list again when going back to the resume step', async () => {
    const atEvidence = {
      id: 'match-1', status: 'RESUME_CONFIRMED', version: 5, progress: 40, checkpoint: 'RESUME_CONFIRMED', title: 'Java 后端工程师',
      requirements: [], clarifications: [], createdAt: '2026-10-06T08:00:00Z', updatedAt: '2026-10-06T08:10:00Z',
      resume: { masterId: 'm', branchId: 'b', revisionId: 'r', title: '我的简历', languageCode: 'zh-CN', contentHash: 'h', updatedAt: '2026-10-06T08:00:00Z' },
    }
    route.query = { id: 'match-1' }
    api.fetchMatchCapabilities.mockResolvedValue(capabilities())
    api.fetchJobMatch.mockResolvedValue(atEvidence)
    api.fetchEvidenceRecommendations.mockResolvedValue([])
    api.fetchResumeOptions.mockResolvedValue([{ masterId: 'm', branchId: 'b', revisionId: 'r', title: '我的简历', languageCode: 'zh-CN', contentHash: 'h', updatedAt: '2026-10-06T08:00:00Z' }])
    const wrapper = mountPage()
    await flushPromises()
    expect(api.fetchResumeOptions).not.toHaveBeenCalled()
    await wrapper.findAll('.jm-wizard__footer button').find(button => button.text() === '返回')!.trigger('click')
    await flushPromises()
    expect(api.fetchResumeOptions).toHaveBeenCalledWith('match-1')
    expect(wrapper.text()).toContain('我的简历')
    expect(wrapper.text()).not.toContain('还没有可用的正式简历')
  })
})
