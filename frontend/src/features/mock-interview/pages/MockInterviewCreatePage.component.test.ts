import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  createMockInterview: vi.fn(), createMockInterviewDraft: vi.fn(), discardMockInterviewDraft: vi.fn(),
  fetchLatestMockInterviewDraft: vi.fn(), saveMockInterviewDraft: vi.fn(),
}))
const resumeApi = vi.hoisted(() => ({ listResumes: vi.fn() }))
const library = vi.hoisted(() => ({ fetchCareerFiles: vi.fn(), fetchCareerRecords: vi.fn(), uploadCareerFile: vi.fn() }))
const jobMatch = vi.hoisted(() => ({ fetchJobMatch: vi.fn(), fetchMatchReport: vi.fn() }))
const route = vi.hoisted(() => ({ params: {}, query: {} as Record<string, string> }))
vi.mock('../services/mockInterviewApi', () => api)
vi.mock('@/features/resume/services/resumeApi', () => resumeApi)
vi.mock('@/features/career-library/services/careerLibraryApi', () => library)
vi.mock('@/features/job-match/services/jobMatchApi', () => jobMatch)
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}))
import MockInterviewCreatePage from './MockInterviewCreatePage.vue'

const emptyDraft = { id: 'draft-new', step: 1, status: 'DRAFT', payload: {}, version: 0, updatedAt: '2026-10-20T08:00:00Z' }
const savedDraft = {
  id: 'draft-old', step: 3, status: 'DRAFT', version: 4, updatedAt: '2026-10-19T08:00:00Z',
  payload: {
    resumeId: 'resume-1', careerRecordIds: ['record-1', 'record-gone'], careerFileIds: [], positionName: '数据分析师',
    companyName: '拾光电商', mode: 'TEXT', interviewType: 'PRESSURE', difficulty: 'ADVANCED', durationMinutes: 45,
    questionCount: 10, feedbackMode: 'AFTER_EACH', followUpEnabled: true, consentConfirmed: true,
  },
}

function mountPage() {
  return mount(MockInterviewCreatePage, { global: { stubs: {
    AiUnavailableNotice: true, JobTaxonomyPicker: true,
    UiSelect: { props: ['modelValue'], template: '<select :value="modelValue"><slot /></select>' },
  } } })
}

function button(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find(item => item.text().includes(text))!
}

beforeEach(() => {
  Object.values(api).forEach(mock => mock.mockReset())
  route.query = {}
  api.createMockInterviewDraft.mockResolvedValue(emptyDraft)
  api.fetchLatestMockInterviewDraft.mockResolvedValue(null)
  api.saveMockInterviewDraft.mockImplementation(async (id: string, step: number, payload: Record<string, unknown>, version: number) =>
    ({ id, step, status: 'DRAFT', payload, version: version + 1, updatedAt: '2026-10-20T08:01:00Z' }))
  resumeApi.listResumes.mockResolvedValue([{ id: 'resume-1', title: '数据分析简历', status: 'DRAFT', version: 1, updatedAt: '' }])
  library.fetchCareerRecords.mockResolvedValue({ items: [{ id: 'record-1', title: '留存分析', type: 'PROJECT', confirmed: true }], total: 1, page: 0, size: 100 })
  library.fetchCareerFiles.mockResolvedValue({ items: [], total: 0, page: 0, size: 100 })
  Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
})
afterEach(() => { vi.unstubAllGlobals() })

describe('MockInterviewCreatePage drafts', () => {
  it('offers the saved draft and puts it back, keeping only what still exists and asking for consent again', async () => {
    api.fetchLatestMockInterviewDraft.mockResolvedValue(savedDraft)
    const wrapper = mountPage()
    await flushPromises()
    const offer = wrapper.get('[data-testid="draft-offer"]').text()
    expect(offer).toContain('数据分析师')
    expect(offer).toContain('第 3 步「面试设置」')
    await button(wrapper, '继续上次的').trigger('click')
    expect(wrapper.find('[data-testid="draft-offer"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('设置面试方式与难度')
    expect(wrapper.text()).toContain('压力面试')

    await button(wrapper, '保存草稿').trigger('click')
    await flushPromises()
    const [id, step, payload] = api.saveMockInterviewDraft.mock.calls[0]!
    expect(id).toBe('draft-old')
    expect(step).toBe(3)
    expect(payload).toMatchObject({ positionName: '数据分析师', interviewType: 'PRESSURE', durationMinutes: 45, careerRecordIds: ['record-1'], consentConfirmed: false })
  })

  it('sets the saved draft aside when starting over', async () => {
    api.fetchLatestMockInterviewDraft.mockResolvedValue(savedDraft)
    api.discardMockInterviewDraft.mockResolvedValue(null)
    const wrapper = mountPage()
    await flushPromises()
    await button(wrapper, '重新开始').trigger('click')
    await flushPromises()
    expect(api.discardMockInterviewDraft).toHaveBeenCalledWith('draft-old')
    expect(wrapper.find('[data-testid="draft-offer"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('选择本次面试资料')
  })

  it('does not offer an old draft when the visit brings its own job', async () => {
    route.query = { company: '拾光电商', position: '数据分析师' }
    mountPage()
    await flushPromises()
    expect(api.fetchLatestMockInterviewDraft).not.toHaveBeenCalled()
  })
})

describe('MockInterviewCreatePage voice checks', () => {
  it('says the browser cannot transcribe and offers text instead of letting a silent voice interview start', async () => {
    route.query = { mode: 'VOICE', type: 'PRESSURE' }
    const wrapper = mountPage()
    await flushPromises()
    for (let step = 1; step < 4; step++) {
      if (step === 2) await wrapper.get('input.mi-input').setValue('数据分析师')
      await button(wrapper, '下一步').trigger('click')
      await flushPromises()
    }
    expect(wrapper.get('[data-testid="recognition-check"]').text()).toContain('不支持')
    await button(wrapper, '下一步').trigger('click')
    expect(wrapper.text()).not.toContain('确认本次模拟面试')
    await wrapper.get('[data-testid="switch-to-text"]').trigger('click')
    expect(wrapper.text()).toContain('检查文字模拟环境')
  })
})
