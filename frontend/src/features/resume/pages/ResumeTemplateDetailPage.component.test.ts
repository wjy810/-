import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ template: vi.fn(), resumes: vi.fn(), load: vi.fn() }))
const route = vi.hoisted(() => ({ value: null as unknown as { params: Record<string, string> } }))
vi.mock('../services/resumeApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../services/resumeApi')>(),
  fetchResumeTemplate: api.template,
  listResumes: api.resumes,
}))
vi.mock('@/resume-render/templates/registry', () => ({ loadTemplate: api.load }))
vi.mock('vue-router', () => ({
  useRoute: () => route.value,
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}))

import ResumeTemplateDetailPage from './ResumeTemplateDetailPage.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-tpl')
const stubs = { UiIllustration: true, ResumeDocument: true, UiSelect: true, UiSegmented: true }

beforeEach(() => {
  route.value = reactive({ params: { templateId: 'classic' } })
  api.template.mockResolvedValue({ id: 'classic' })
  api.resumes.mockResolvedValue([])
  api.load.mockResolvedValue({})
})

describe('ResumeTemplateDetailPage', () => {
  it('shows a retryable error when the template fails to load', async () => {
    api.template.mockRejectedValueOnce(failure)
    const wrapper = mount(ResumeTemplateDetailPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('模板读取失败')
    expect(wrapper.text()).toContain('req-tpl')
    await wrapper.findAll('button').find(button => button.text().includes('重试'))!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('用此模板创建简历')
  })

  it('says the resume list failed instead of silently hiding "应用到这份简历"', async () => {
    api.resumes.mockRejectedValueOnce(failure)
    const wrapper = mount(ResumeTemplateDetailPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('简历列表没有读取成功')
    api.resumes.mockResolvedValueOnce([{ id: 'm1', title: '我的简历', status: 'ACTIVE' }])
    await wrapper.findAll('button').find(button => button.text() === '重试')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('应用到这份简历')
  })

  it('shows a way back, not a dead retry, for an unknown template', async () => {
    route.value.params.templateId = 'no-such-template'
    const wrapper = mount(ResumeTemplateDetailPage, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('模板不存在')
    expect(wrapper.text()).toContain('返回模板中心')
    expect(wrapper.text()).not.toContain('重试')
    expect(api.template).not.toHaveBeenCalled()
  })
})
