import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { ResumeMasterSummary, ResumeMasterView } from '../types'

const api = vi.hoisted(() => ({ listResumes: vi.fn(), fetchResume: vi.fn(), updateResume: vi.fn() }))
vi.mock('../services/resumeApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/resumeApi')>(),
  ...api,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :data-to="typeof to === \'string\' ? to : \'\'"><slot /></a>' },
}))
vi.mock('vue-sonner', () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn(), promise: vi.fn() }) }))

import ResumeListPage from './ResumeListPage.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-list')
const stubs = {
  UiIllustration: true,
  JobProofIcon: true,
  AppSelect: true,
  UiDialog: { props: ['open', 'title'], template: '<div v-if="open" role="dialog"><slot /><slot name="footer" /></div>' },
}

function summary(id: string, title: string, version = 3): ResumeMasterSummary {
  return { id, title, status: 'DRAFT', version, updatedAt: `2026-09-0${id.length}T08:00:00Z` }
}
function detail(item: ResumeMasterSummary): ResumeMasterView {
  return { ...item, keyOutcomes: [], versions: [] }
}

function mountList() {
  return mount(ResumeListPage, { global: { stubs } })
}

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset())
  const items = [summary('a', 'Java 后端简历'), summary('bb', '产品经理简历')]
  api.listResumes.mockResolvedValue(items)
  api.fetchResume.mockImplementation(async (id: string) => detail(items.find((item) => item.id === id)!))
})

describe('ResumeListPage', () => {
  it('shows a retryable error, never the empty state, when the list fails to load', async () => {
    api.listResumes.mockRejectedValueOnce(failure)
    const wrapper = mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('简历列表读取失败')
    expect(wrapper.text()).toContain('req-list')
    expect(wrapper.find('[data-testid="resume-empty"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('还没有简历')

    await wrapper.findAll('button').find((button) => button.text() === '重试')!.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="resume-row"]')).toHaveLength(2)
  })

  it('has no placeholder columns for target job or export state', async () => {
    const wrapper = mountList()
    await flushPromises()
    const headers = wrapper.findAll('th').map((cell) => cell.text())
    expect(headers).not.toContain('目标岗位')
    expect(headers).not.toContain('导出状态')
    expect(wrapper.text()).not.toContain('通用 / 默认')
  })

  it('filters by name and offers to clear a search that matches nothing', async () => {
    const wrapper = mountList()
    await flushPromises()
    const search = wrapper.get('input[data-testid="resume-search"]')
    await search.setValue('JAVA')
    expect(wrapper.findAll('[data-testid="resume-row"]').map((row) => row.text())).toEqual([expect.stringContaining('Java 后端简历')])

    await search.setValue('运营')
    expect(wrapper.findAll('[data-testid="resume-row"]')).toHaveLength(0)
    expect(wrapper.get('[data-testid="resume-empty"]').text()).toContain('没有名称包含“运营”的简历')
    await wrapper.findAll('button').find((button) => button.text() === '清除搜索')!.trigger('click')
    expect(wrapper.findAll('[data-testid="resume-row"]')).toHaveLength(2)
  })

  it('renames a resume with the current version and refuses a blank name', async () => {
    api.updateResume.mockResolvedValue({ ...detail(summary('a', '后端开发 · 校招', 4)) })
    const wrapper = mountList()
    await flushPromises()
    const row = wrapper.findAll('[data-testid="resume-row"]').find((item) => item.text().includes('Java 后端简历'))!
    await row.get('[data-testid="rename-resume"]').trigger('click')
    const input = wrapper.get('input[data-testid="rename-input"]')
    expect((input.element as HTMLInputElement).value).toBe('Java 后端简历')

    await input.setValue('   ')
    await wrapper.get('[data-testid="rename-save"]').trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('请输入简历名称')
    expect(api.updateResume).not.toHaveBeenCalled()

    await input.setValue('  后端开发   · 校招 ')
    await wrapper.get('[data-testid="rename-save"]').trigger('click')
    await flushPromises()
    expect(api.updateResume).toHaveBeenCalledWith('a', { title: '后端开发 · 校招', expectedVersion: 3 })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(api.listResumes).toHaveBeenCalledTimes(2)
  })
})
