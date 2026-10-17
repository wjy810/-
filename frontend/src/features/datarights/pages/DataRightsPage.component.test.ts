import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  preview: vi.fn(),
  history: vi.fn(),
  exports: vi.fn(),
  deletions: vi.fn(),
  records: vi.fn(),
  files: vi.fn(),
}))
const route = vi.hoisted(() => ({ value: null as unknown as { query: Record<string, string> } }))
vi.mock('../services/dataRightsApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/dataRightsApi')>(),
  fetchDeletionPreview: api.preview,
  listExports: api.exports,
  listDeletions: api.deletions,
}))
vi.mock('@/features/career-library/services/careerLibraryApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/features/career-library/services/careerLibraryApi')>(),
  fetchCareerHistory: api.history,
  fetchCareerRecords: api.records,
  fetchCareerFiles: api.files,
}))
vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRoute: () => route.value,
}))

import DataRightsPage from './DataRightsPage.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-rights')
const accountPreview = { scope: 'ACCOUNT', impactSummary: '将删除账号', legalExceptionNote: '依法保留审计记录', canProceed: true, impacts: [], blockers: [] }
const record = { id: 'record-2', type: 'PROJECT', title: '增长实验平台', organization: '某科技', resumeReferenceCount: 0, status: 'ACTIVE' }

function mountPage() {
  return mount(DataRightsPage, { global: { plugins: [createPinia()], stubs: { UiIllustration: true } } })
}

beforeEach(() => {
  route.value = reactive({ query: {} })
  api.preview.mockResolvedValue(accountPreview)
  api.history.mockResolvedValue({ total: 2, counts: { APPLICATION: 2 }, archivedAt: null })
  api.exports.mockResolvedValue([])
  api.deletions.mockResolvedValue([])
  api.records.mockResolvedValue({ items: [record], total: 1, page: 0, size: 50 })
  api.files.mockResolvedValue({ items: [], total: 0, page: 0, size: 50 })
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
    expect(wrapper.get('#object').find('button.ui-btn--danger').attributes('disabled')).toBeDefined()
  })

  it('lets the user pick an item to delete instead of typing its id', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const row = wrapper.get('#object').findAll('[role="radio"]').find(item => item.text().includes('增长实验平台'))
    expect(row).toBeDefined()
    expect(wrapper.get('#object').find('input#object-id').exists()).toBe(false)
    await row!.trigger('click')
    await flushPromises()
    expect(api.preview).toHaveBeenCalledWith({ scope: 'OBJECT', targetType: 'CAREER_RECORD', targetId: 'record-2' })
  })

  it('remembers a finished export after a reload and offers only the download', async () => {
    api.exports.mockResolvedValue([{ id: 'exp-1', taskId: 'task-1', taskStatus: 'SUCCEEDED', downloadAvailable: true,
      downloadExpired: false, downloadExpiresAt: new Date(Date.now() + 5 * 3600_000).toISOString(), createdAt: new Date().toISOString() }])
    const wrapper = mountPage()
    await flushPromises()
    const panel = wrapper.get('#export').text()
    expect(panel).toContain('导出文件已就绪')
    expect(panel).toContain('下载导出文件')
    expect(panel).not.toContain('取消导出')
    expect(panel).not.toContain('发起导出')
  })

  it('shows open deletion requests after a reload', async () => {
    api.deletions.mockResolvedValue([{ id: 'del-1', scope: 'OBJECT', targetType: 'CAREER_FILE', status: 'COMPLETED',
      receipts: [{ moduleCode: 'career-library', status: 'SUCCEEDED' }], createdAt: new Date().toISOString() }])
    const wrapper = mountPage()
    await flushPromises()
    const list = wrapper.get('#deletion-progress').text()
    expect(list).toContain('资料文件')
    expect(list).toContain('已完成')
    expect(list).not.toMatch(/CAREER_FILE|COMPLETED/)
  })
})
