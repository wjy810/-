import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { ResumeMasterView } from '../types'

const api = vi.hoisted(() => ({
  fetchResume: vi.fn(), fetchCurrentResumeLayout: vi.fn(), fetchResumeTemplate: vi.fn(),
}))
const career = vi.hoisted(() => ({ fetchCareerRecords: vi.fn() }))
vi.mock('../services/resumeApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/resumeApi')>(),
  ...api,
}))
vi.mock('@/features/career-library/services/careerLibraryApi', () => career)
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'resume-1' }, query: {}, hash: '' }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :href="typeof to === \'string\' ? to : \'\'"><slot /></a>' },
}))
vi.mock('vue-sonner', () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn(), promise: vi.fn() }) }))

import ResumeEditorPage from './ResumeEditorPage.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-ver')
const stubs = {
  UiIllustration: true,
  ResumeCompareModal: true,
  ResumeEvidenceDrawer: true,
  UiDialog: { props: ['open', 'title'], template: '<div v-if="open" role="dialog"><slot /><slot name="footer" /></div>' },
}

function master(patch: Partial<ResumeMasterView> = {}): ResumeMasterView {
  return {
    id: 'resume-1', title: '后端开发简历', status: 'READY_TO_EXPORT', version: 7, keyOutcomes: [], pendingCandidateIds: [],
    updatedAt: '2026-09-02T08:00:00Z',
    versions: [
      { id: 'v-new', masterId: 'resume-1', status: 'FROZEN', source: 'AI_WORKBENCH_PDF', immutable: true, version: 1, createdAt: '2026-09-02T08:00:00Z' },
      { id: 'v-old', masterId: 'resume-1', status: 'FROZEN', source: 'USER_FREEZE', immutable: true, version: 1, createdAt: '2026-09-01T08:00:00Z' },
    ],
    ...patch,
  }
}

async function openFreeze(patch: Partial<ResumeMasterView>) {
  api.fetchResume.mockResolvedValue(master(patch))
  const wrapper = mount(ResumeEditorPage, { global: { stubs } })
  await flushPromises()
  await wrapper.get('[data-testid="open-freeze"]').trigger('click')
  return wrapper.get('[data-testid="freeze-pending-ai"]').text()
}

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset())
  career.fetchCareerRecords.mockReset().mockResolvedValue({ items: [], total: 0, page: 0, size: 100 })
  api.fetchCurrentResumeLayout.mockResolvedValue({ selected: false, layout: null })
})

describe('ResumeEditorPage (版本与导出)', () => {
  it('shows a retryable error when the resume fails to load, then the versions with a workbench link', async () => {
    api.fetchResume.mockRejectedValueOnce(failure).mockResolvedValue(master())
    const wrapper = mount(ResumeEditorPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('简历读取失败')
    expect(wrapper.text()).toContain('req-ver')
    expect(wrapper.text()).not.toContain('还没有版本')

    await wrapper.findAll('button').find((button) => button.text() === '重试')!.trigger('click')
    await flushPromises()
    const rows = wrapper.findAll('[data-testid="version-row"]').map((row) => row.text())
    expect(rows[0]).toContain('版本 2')
    expect(rows[0]).toContain('工作台导出 PDF')
    expect(rows[1]).toContain('版本 1')
    expect(wrapper.get('[data-testid="open-workbench"]').attributes('href')).toBe('/resumes/resume-1')
  })

  it('has no plain-text content editors any more', async () => {
    api.fetchResume.mockResolvedValue(master())
    const wrapper = mount(ResumeEditorPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.findAll('textarea')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('保存正式字段')
  })

  it('reports the real number of AI items awaiting confirmation in the freeze dialog', async () => {
    expect(await openFreeze({ pendingCandidateIds: ['c-1', 'c-2'] })).toContain('2 条未处理')
    expect(await openFreeze({ pendingCandidateIds: [] })).toContain('没有')
    expect(await openFreeze({ pendingCandidateIds: undefined })).toContain('无法确认')
  })
})
