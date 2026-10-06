import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ preview: vi.fn(), history: vi.fn() }))
const route = vi.hoisted(() => ({ value: null as unknown as { query: Record<string, string> } }))
vi.mock('../services/dataRightsApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/dataRightsApi')>(),
  fetchDeletionPreview: api.preview,
}))
vi.mock('@/features/career-library/services/careerLibraryApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/features/career-library/services/careerLibraryApi')>(),
  fetchCareerHistory: api.history,
}))
vi.mock('vue-router', () => ({ useRoute: () => route.value }))

import DataRightsPage from './DataRightsPage.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-rights')
const accountPreview = { scope: 'ACCOUNT', impactSummary: '将删除账号', legalExceptionNote: '依法保留审计记录', canProceed: true, impacts: [], blockers: [] }

function mountPage() {
  return mount(DataRightsPage, { global: { stubs: { UiIllustration: true, AppSelect: true } } })
}

beforeEach(() => {
  route.value = reactive({ query: {} })
  api.preview.mockResolvedValue(accountPreview)
  api.history.mockResolvedValue({ total: 2, counts: { APPLICATION: 2 }, archivedAt: null })
})

describe('DataRightsPage', () => {
  it('shows a retryable error, not "no history" or 0 条, when the history summary fails', async () => {
    api.history.mockRejectedValueOnce(failure)
    const wrapper = mountPage()
    await flushPromises()
    const card = wrapper.get('#career-history').text()
    expect(card).toContain('历史记录读取失败')
    expect(card).toContain('req-rights')
    expect(card).not.toContain('没有需要保留')
    expect(card).not.toContain('0 条')
  })

  it('labels history counts in plain language', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const card = wrapper.get('#career-history').text()
    expect(card).toContain('投递记录')
    expect(card).not.toContain('APPLICATION')
  })

  it('keeps account deletion closed when the impact summary could not be read', async () => {
    api.preview.mockRejectedValueOnce(failure)
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('#preview').text()).toContain('删除影响说明读取失败')
    expect(wrapper.get('#deletion').text()).toContain('读取后才能提交注销申请')
  })

  it('previews a record sent from the career library without exposing codes', async () => {
    route.value.query = { targetType: 'CAREER_RECORD', targetId: 'record-1' }
    api.preview.mockImplementation((query?: { scope?: string }) => Promise.resolve(query?.scope === 'OBJECT'
      ? { scope: 'OBJECT', targetType: 'CAREER_RECORD', targetId: 'record-1', impactSummary: '求职资料记录将被永久删除', canProceed: false,
          impacts: [{ kind: 'CAREER_RECORD', id: 'record-1', relation: 'TARGET', label: '求职资料记录' }], blockers: ['CAREER_RECORD_REFERENCED'] }
      : accountPreview))
    const wrapper = mountPage()
    await flushPromises()
    expect(api.preview).toHaveBeenCalledWith({ scope: 'OBJECT', targetType: 'CAREER_RECORD', targetId: 'record-1' })
    const section = wrapper.get('#object').text()
    expect(section).toContain('经历与成果记录')
    expect(section).toContain('永久删除')
    expect(section).toContain('正被简历版本引用')
    expect(section).not.toMatch(/CAREER_RECORD|TARGET|OBJECT|canProceed|impacts/)
  })
})
