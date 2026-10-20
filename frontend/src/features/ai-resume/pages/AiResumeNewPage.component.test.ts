import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  createAiResume: vi.fn(), ensureAiResume: vi.fn(), selectAiResumeTemplate: vi.fn(), saveAiResumeDesign: vi.fn(),
  startResumeTextImport: vi.fn(), startResumeFileImport: vi.fn(), fetchResumeImport: vi.fn(), confirmResumeImport: vi.fn(),
}))
const library = vi.hoisted(() => ({ fetchCareerFiles: vi.fn(), uploadCareerFile: vi.fn() }))
const poll = vi.hoisted(() => ({ pollTask: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
const route = vi.hoisted(() => ({ params: {}, query: {} as Record<string, string> }))
const toasts = vi.hoisted(() => ({ warning: vi.fn() }))
vi.mock('../services/aiResumeApi', () => api)
vi.mock('@/features/career-library/services/careerLibraryApi', () => library)
vi.mock('@/shared/ui/toast', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/shared/ui/toast')>(),
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: toasts.warning, dismiss: vi.fn() },
}))
vi.mock('@/shared/lib/pollTask', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/shared/lib/pollTask')>(),
  pollTask: poll.pollTask,
}))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
  RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
}))

import AiResumeNewPage from './AiResumeNewPage.vue'
import { manifestOf } from '@/resume-render/templates/manifests'
import { defaultDesign } from '@/resume-render/theme/tokens'

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

function mountPage() {
  return mount(AiResumeNewPage, { global: { stubs: { UiTooltip: { template: '<slot />' } } } })
}

async function startImport(source: 'TEXT' | 'FILE' = 'TEXT') {
  const wrapper = mountPage()
  await button(wrapper, '有，我已经有简历了').trigger('click')
  if (source === 'TEXT') await button(wrapper, '粘贴文字').trigger('click')
  return wrapper
}

async function pickFile(wrapper: VueWrapper, file: File) {
  const input = wrapper.get('[data-testid="import-file"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset())
  Object.values(library).forEach((mock) => mock.mockReset())
  toasts.warning.mockReset()
  window.sessionStorage.clear()
  route.query = {}
  library.fetchCareerFiles.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
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

describe('AiResumeNewPage file import', () => {
  const pdf = () => new File(['%PDF-1.7'], '张三-简历.pdf', { type: 'application/pdf' })

  it('uploads the file to the library, waits for the safety check, then parses it and keeps the import in the URL', async () => {
    library.uploadCareerFile.mockResolvedValue({ file: { id: 'file-1' }, task: { id: 'scan-1', status: 'PENDING' } })
    api.startResumeFileImport.mockResolvedValue({ importSession: session({ sourceType: 'CAREER_FILE', status: 'PARSING' }), task: { id: 'task-2', status: 'PENDING' } })
    api.fetchResumeImport.mockResolvedValue(session({ sourceType: 'CAREER_FILE', sourceFilename: '张三-简历.pdf' }))
    const wrapper = await startImport('FILE')
    await wrapper.get('[data-testid="import-parse-file"]').trigger('click')
    expect(wrapper.text()).toContain('请先选择简历文件')
    expect(library.uploadCareerFile).not.toHaveBeenCalled()

    const file = pdf()
    await pickFile(wrapper, file)
    await wrapper.get('[data-testid="import-parse-file"]').trigger('click')
    await flushPromises()
    expect(library.uploadCareerFile).toHaveBeenCalledWith(file, 'RESUME')
    expect(poll.pollTask).toHaveBeenCalledWith('scan-1', expect.any(Function), expect.any(AbortSignal))
    expect(api.startResumeFileImport).toHaveBeenCalledWith('file-1')
    expect(router.replace).toHaveBeenCalledWith({ query: { import: 'import-1' } })
    expect(wrapper.text()).toContain('这是我的简历文件「张三-简历.pdf」')
    expect(wrapper.get('[data-testid="import-review"]').text()).toContain('教育经历1 段')
  })

  it('stops with a plain reason when the file fails the safety check', async () => {
    library.uploadCareerFile.mockResolvedValue({ file: { id: 'file-1' }, task: { id: 'scan-1', status: 'PENDING' } })
    poll.pollTask.mockResolvedValueOnce({ id: 'scan-1', status: 'FAILED', failureReason: 'CAREER_FILE_MALWARE_DETECTED' })
    const wrapper = await startImport('FILE')
    await pickFile(wrapper, pdf())
    await wrapper.get('[data-testid="import-parse-file"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('不安全的内容')
    expect(wrapper.text()).not.toContain('CAREER_FILE')
    expect(api.startResumeFileImport).not.toHaveBeenCalled()
  })

  it('rejects an old .doc file before uploading anything', async () => {
    const wrapper = await startImport('FILE')
    await pickFile(wrapper, new File(['x'], '简历.doc', { type: 'application/msword' }))
    expect(wrapper.text()).toContain('另存为 .docx')
    await wrapper.get('[data-testid="import-parse-file"]').trigger('click')
    expect(library.uploadCareerFile).not.toHaveBeenCalled()
  })

  it('imports a resume file already in the library without uploading it again', async () => {
    library.fetchCareerFiles.mockResolvedValue({ items: [
      { id: 'lib-1', displayName: '去年的简历', originalFilename: 'old.pdf', contentType: 'application/pdf', updatedAt: '2026-09-01T08:00:00Z' },
      { id: 'lib-2', displayName: '照片', originalFilename: 'me.png', contentType: 'image/png', updatedAt: '2026-09-01T08:00:00Z' },
    ], total: 2, page: 0, size: 20 })
    api.startResumeFileImport.mockResolvedValue({ importSession: session({ sourceType: 'CAREER_FILE', status: 'PARSING' }), task: { id: 'task-2', status: 'PENDING' } })
    api.fetchResumeImport.mockResolvedValue(session({ sourceType: 'CAREER_FILE' }))
    const wrapper = await startImport('FILE')
    await flushPromises()
    expect(library.fetchCareerFiles).toHaveBeenCalledWith(expect.objectContaining({ category: 'RESUME', processingStatus: 'READY' }))
    const files = wrapper.findAll('[data-testid="import-library-file"]')
    expect(files).toHaveLength(1)
    await files[0]!.trigger('click')
    await wrapper.get('[data-testid="import-parse-file"]').trigger('click')
    await flushPromises()
    expect(library.uploadCareerFile).not.toHaveBeenCalled()
    expect(api.startResumeFileImport).toHaveBeenCalledWith('lib-1')
    expect(wrapper.find('[data-testid="import-review"]').exists()).toBe(true)
  })

  it('picks the import up again after a refresh', async () => {
    route.query = { import: 'import-12345678' }
    api.fetchResumeImport.mockResolvedValue(session({ id: 'import-12345678', sourceType: 'CAREER_FILE', sourceFilename: '张三-简历.pdf' }))
    const wrapper = mountPage()
    await flushPromises()
    expect(api.fetchResumeImport).toHaveBeenCalledWith('import-12345678')
    expect(wrapper.get('[data-testid="import-review"]').text()).toContain('教育经历1 段')
  })

  it('keeps an import that is still parsing and checks it again on request', async () => {
    api.startResumeTextImport.mockResolvedValue({ importSession: session({ status: 'PARSING' }), task: { id: 'task-1', status: 'PENDING' } })
    poll.pollTask.mockRejectedValueOnce(new Error('任务仍在进行，已停止自动刷新。'))
    const wrapper = await startImport()
    await wrapper.get('[data-testid="import-text"]').setValue(RESUME_TEXT)
    await wrapper.get('[data-testid="import-parse"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('查看解析结果')
    expect(window.sessionStorage.getItem('jobproof:resume-import:text')).toBe(RESUME_TEXT)

    api.fetchResumeImport.mockResolvedValue(session())
    await wrapper.get('[data-testid="import-resume"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="import-review"]').exists()).toBe(true)
  })

  it('can switch to building a new resume after choosing to import', async () => {
    const wrapper = await startImport('FILE')
    await wrapper.get('[data-testid="import-switch-new"]').trigger('click')
    expect(wrapper.text()).toContain('选择你的身份')
    expect(wrapper.find('[data-testid="import-file"]').exists()).toBe(false)
  })
})

describe('AiResumeNewPage from the template center', () => {
  it('applies the template and the palette picked on its page to the new resume', async () => {
    const manifest = manifestOf('classic')!
    const palette = manifest.palettes[1]!.id
    route.query = { template: 'classic', palette }
    api.createAiResume.mockResolvedValue({ id: 'conversation-1', layout: { templateId: 'meridian', version: 1 } })
    api.selectAiResumeTemplate.mockResolvedValue({ id: 'conversation-1', layout: { templateId: 'classic', version: 2, design: defaultDesign(manifest) },
      activeDesign: { templateId: 'classic', variantCode: 'DEFAULT', settings: defaultDesign(manifest), versionNo: 0 } })
    api.saveAiResumeDesign.mockResolvedValue({ templateId: 'classic', variantCode: 'DEFAULT', settings: { ...defaultDesign(manifest), paletteId: palette }, versionNo: 1 })
    const wrapper = mountPage()
    await button(wrapper, '没有，我想新建一份').trigger('click')
    await button(wrapper, '学生').trigger('click')
    await flushPromises()
    expect(api.selectAiResumeTemplate).toHaveBeenCalledWith('conversation-1', 'classic', 1)
    expect(api.saveAiResumeDesign).toHaveBeenCalledWith('conversation-1', 'classic', 'DEFAULT', expect.objectContaining({ paletteId: palette, customAccent: null }), 0)
    expect(router.replace).toHaveBeenCalledWith({ path: '/ai-resume/conversation-1', query: { source: 'new' } })
    expect(toasts.warning).not.toHaveBeenCalled()
  })

  it('opens the resume anyway and says so when the template could not be applied', async () => {
    route.query = { template: 'classic' }
    api.createAiResume.mockResolvedValue({ id: 'conversation-1', layout: { templateId: 'meridian', version: 1 } })
    api.selectAiResumeTemplate.mockRejectedValue(new Error('down'))
    const wrapper = mountPage()
    await button(wrapper, '没有，我想新建一份').trigger('click')
    await button(wrapper, '学生').trigger('click')
    await flushPromises()
    expect(toasts.warning).toHaveBeenCalledWith(expect.stringContaining('没有应用成功'))
    expect(router.replace).toHaveBeenCalledWith({ path: '/ai-resume/conversation-1', query: { source: 'new' } })
  })
})
