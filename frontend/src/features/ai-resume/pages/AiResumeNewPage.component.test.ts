import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  createAiResume: vi.fn(), ensureAiResume: vi.fn(), selectAiResumeTemplate: vi.fn(),
  startResumeTextImport: vi.fn(), fetchResumeImport: vi.fn(), confirmResumeImport: vi.fn(),
}))
const poll = vi.hoisted(() => ({ pollTask: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
vi.mock('../services/aiResumeApi', () => api)
vi.mock('@/shared/lib/pollTask', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/shared/lib/pollTask')>(),
  pollTask: poll.pollTask,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => router,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}))

import AiResumeNewPage from './AiResumeNewPage.vue'

const RESUME_TEXT = [
  '张三', 'zhangsan@example.test', '教育经历', '浙江大学 计算机科学与技术 2019.09 - 2023.06',
  '工作经历', '某科技有限公司 后端开发工程师 2023.07 - 至今', '负责订单服务的接口开发与性能优化，接口平均耗时下降。',
  '专业技能', 'Java、Spring Boot、MySQL、Redis',
].join('\n')

const session = (patch: Record<string, unknown> = {}) => ({
  id: 'import-1', sourceType: 'PASTED_TEXT', status: 'READY_FOR_CONFIRMATION', version: 1,
  structuredDraft: {
    basics: { name: '张三', email: 'zhangsan@example.test' },
    education: [{ school: '浙江大学' }],
    experiences: [{ company: '某科技有限公司' }],
    skills: [{ category: '专业技能', items: ['Java', 'Spring Boot', 'MySQL', 'Redis'] }],
  },
  ...patch,
})

function button(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((item) => item.text().includes(text))!
}

async function startImport() {
  const wrapper = mount(AiResumeNewPage, { global: { stubs: { UiTooltip: { template: '<slot />' } } } })
  await button(wrapper, '有，我已经有简历了').trigger('click')
  return wrapper
}

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset())
  poll.pollTask.mockReset().mockResolvedValue({ id: 'task-1', status: 'SUCCEEDED' })
  router.replace.mockReset()
  api.startResumeTextImport.mockResolvedValue({ importSession: session({ status: 'PARSING' }), task: { id: 'task-1', status: 'PENDING' } })
})

describe('AiResumeNewPage paste import', () => {
  it('checks the minimum length before calling the server', async () => {
    const wrapper = await startImport()
    await wrapper.get('[data-testid="import-text"]').setValue('教育经历 浙江大学')
    await wrapper.get('[data-testid="import-parse"]').trigger('click')
    expect(wrapper.text()).toContain('至少需要 80 个字')
    expect(api.startResumeTextImport).not.toHaveBeenCalled()
  })

  it('parses the pasted text, shows what was found, and opens the new resume in the workbench', async () => {
    api.fetchResumeImport.mockResolvedValue(session())
    api.confirmResumeImport.mockResolvedValue(session({ status: 'CONFIRMED', resultMasterId: 'resume-9', version: 2 }))
    api.ensureAiResume.mockResolvedValue({ id: 'conversation-9', layout: null })
    const wrapper = await startImport()
    await wrapper.get('[data-testid="import-text"]').setValue(RESUME_TEXT)
    await wrapper.get('[data-testid="import-parse"]').trigger('click')
    await flushPromises()

    expect(api.startResumeTextImport).toHaveBeenCalledWith(RESUME_TEXT)
    expect(poll.pollTask).toHaveBeenCalledWith('task-1', expect.any(Function), expect.any(AbortSignal))
    const review = wrapper.get('[data-testid="import-review"]').text()
    expect(review).toContain('教育经历1 段')
    expect(review).toContain('技能4 项')
    expect(review).toContain('识别到姓名、邮箱')

    await wrapper.get('[data-testid="import-confirm"]').trigger('click')
    await flushPromises()
    expect(api.confirmResumeImport).toHaveBeenCalledWith('import-1', undefined, 1)
    expect(api.ensureAiResume).toHaveBeenCalledWith('resume-9')
    expect(router.replace).toHaveBeenCalledWith({ path: '/ai-resume/conversation-9', query: { source: 'import' } })
  })

  it('explains a parse failure in plain words and keeps the pasted text', async () => {
    poll.pollTask.mockResolvedValue({ id: 'task-1', status: 'FAILED', errorCode: 'RESUME_STRUCTURE_INSUFFICIENT' })
    api.fetchResumeImport.mockResolvedValue(session({ status: 'FAILED', errorCode: 'RESUME_STRUCTURE_INSUFFICIENT', structuredDraft: null }))
    const wrapper = await startImport()
    await wrapper.get('[data-testid="import-text"]').setValue(RESUME_TEXT)
    await wrapper.get('[data-testid="import-parse"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('请保留原简历里的板块标题')
    expect(wrapper.text()).not.toContain('RESUME_STRUCTURE_INSUFFICIENT')
    expect((wrapper.get('[data-testid="import-text"]').element as HTMLTextAreaElement).value).toBe(RESUME_TEXT)
    expect(api.confirmResumeImport).not.toHaveBeenCalled()
  })

  it('shows a server error from starting the import and lets the user retry', async () => {
    api.startResumeTextImport.mockRejectedValueOnce(new ApiClientError({ category: 'USER_CORRECTABLE', reason: 'RESUME_IMPORT_TEXT_TOO_LARGE', message: '粘贴文本不能超过 200000 字符' }, 400, 'req-1'))
    const wrapper = await startImport()
    await wrapper.get('[data-testid="import-text"]').setValue(RESUME_TEXT)
    await wrapper.get('[data-testid="import-parse"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('粘贴文本不能超过 200000 字符')
    expect(wrapper.get('[data-testid="import-parse"]').attributes('disabled')).toBeUndefined()
  })
})
