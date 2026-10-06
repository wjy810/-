import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AiResumeConversation, AiResumeDesignPreference, AiSmartTemplate } from '../types'
import type { ResumeDesignSettings } from '@/features/resume/types'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  fetchAiResume: vi.fn(), listAiResumeSmartTemplates: vi.fn(), selectAiResumeTemplate: vi.fn(),
  saveAiResumeCardDraft: vi.fn(), submitAiResumeCard: vi.fn(), saveAiResumeDesign: vi.fn(), exportAiResumePdf: vi.fn(),
}))
const resumeApi = vi.hoisted(() => ({ fetchCurrentResumeLayout: vi.fn(), downloadPrivateFile: vi.fn() }))
vi.mock('../services/aiResumeApi', async () => ({
  ...(await vi.importActual<typeof import('../services/aiResumeApi')>('../services/aiResumeApi')), ...api,
}))
vi.mock('@/features/resume/services/resumeApi', () => resumeApi)
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { conversationId: 'conversation-1' }, query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
  RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' },
}))
vi.mock('vue-sonner', () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn(), promise: vi.fn() }) }))
import AiResumeWorkbenchPage from './AiResumeWorkbenchPage.vue'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const settings: ResumeDesignSettings = {
  schemaVersion: 'resume-design-v1', fontPreset: 'MODERN_SANS', fontScale: 'STANDARD', lineHeight: 'STANDARD',
  pageMargin: 'STANDARD', accentColor: '#112233', dateFormat: 'YYYY_DOT_MM', headerLayout: 'MINIMAL',
  headingStyle: 'RULE', photoMode: 'AUTO', density: 'STANDARD', hiddenSections: [], sectionOrder: [],
}
function template(id: string): AiSmartTemplate {
  return {
    templateId: id, displayName: id, familyName: '简约', languageCode: 'zh-CN', recommendedPages: '1', photoPolicy: 'OPTIONAL',
    variants: ['MONO'], rendererProtocol: 'resume-layout-v3', layoutDefinitionJson: '{}', docxAvailable: true, presets: [],
    design: { templateId: id, variantCode: 'MONO', settings: { ...settings }, versionNo: 1 },
  }
}
function conversation(templateId = 'template-a'): AiResumeConversation {
  return {
    id: 'conversation-1', masterId: 'resume-1', activeBranchId: 'branch-1', status: 'ACTIVE', onboardingStage: 'READY_FOR_PREVIEW',
    identityType: 'PROFESSIONAL', lastSequence: 0, versionNo: 1,
    resume: { id: 'resume-1', status: 'DRAFT', title: '我的简历', version: 1 },
    content: { basics: { name: '原姓名', email: 'job@example.test' } },
    layout: { id: 'layout-1', masterId: 'resume-1', templateVersionId: `${templateId}-v1`, templateId, variantCode: 'MONO',
      design: { ...settings }, status: 'VALID', overflow: { valid: true, consumedUnits: 1, items: [] }, version: 1 },
    activeTemplate: template(templateId), activeDesign: template(templateId).design,
    cards: [
      { id: 'contact-1', cardType: 'CONTACT', schemaVersion: '1', status: 'CONFIRMED', payload: { name: '原姓名', email: 'job@example.test' }, versionNo: 1, createdAt: '', updatedAt: '' },
      { id: 'summary-1', cardType: 'SUMMARY', schemaVersion: '1', status: 'DRAFT', payload: { text: '原简介' }, versionNo: 1, createdAt: '', updatedAt: '' },
    ],
    messages: [], changeSets: [], quota: { id: 'quota-1', periodKey: '', grantedUnits: 100, usedUnits: 0, heldUnits: 0, remainingUnits: 100, versionNo: 1 },
    consent: { status: 'GRANTED', policyVersion: '1' }, careerLibraryEvidence: { enabled: false, snapshotVersion: 1, source: 'USER' },
    aiAvailable: false, createdAt: '', updatedAt: '',
  }
}
function mountWorkbench() {
  return mount(AiResumeWorkbenchPage, { global: { plugins: [createPinia()], stubs: {
    ResumeTemplatePreview: true, AiSummarySuggestionPanel: true, ToolDrawer: true,
    UiTooltip: { template: '<slot />' },
    UiDialog: { props: ['open'], template: '<div v-if="open" role="dialog"><slot /><slot name="footer" /></div>' },
  } } })
}
async function openTab(wrapper: VueWrapper, title: string) {
  await wrapper.findAll('.workbench-tabs button').find((button) => button.text() === title)!.trigger('click')
}
const FONT_SCALE: Record<string, string> = { SMALL: '小', STANDARD: '标准', LARGE: '大' }
async function pickFontScale(wrapper: VueWrapper, value: keyof typeof FONT_SCALE) {
  await wrapper.findAll('[aria-label="字号"] button').find((button) => button.text() === FONT_SCALE[value])!.trigger('click')
}
function fontScale(wrapper: VueWrapper): string {
  const label = wrapper.get('[aria-label="字号"] [data-state="on"]').text()
  return Object.entries(FONT_SCALE).find(([, text]) => text === label)![0]
}

describe('workbench persistence', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    window.localStorage.clear()
    Object.values(api).forEach((mock) => mock.mockReset())
    Object.values(resumeApi).forEach((mock) => mock.mockReset())
    vi.stubGlobal('EventSource', class { addEventListener() {} close() {} })
    api.fetchAiResume.mockResolvedValue(conversation())
    api.listAiResumeSmartTemplates.mockResolvedValue([template('template-a'), template('template-b')])
    resumeApi.fetchCurrentResumeLayout.mockResolvedValue({ layout: conversation().layout })
  })
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

  it('keeps an edited name across a template response before its debounce fires, and saves that name', async () => {
    api.selectAiResumeTemplate.mockResolvedValue(conversation('template-b'))
    api.saveAiResumeCardDraft.mockImplementation(async (_id, cardId, payload) => {
      const next = conversation('template-b')
      next.cards.find((card) => card.id === cardId)!.payload = payload
      return next
    })
    const wrapper = mountWorkbench()
    await flushPromises()
    await openTab(wrapper, '编辑')
    await wrapper.get('.card-editor input').setValue('用户刚输入的新姓名')
    await openTab(wrapper, '模板')
    await wrapper.findAll('.template-gallery button')[1]!.trigger('click')
    await flushPromises()
    await openTab(wrapper, '编辑')
    expect(wrapper.get<HTMLInputElement>('.card-editor input').element.value).toBe('用户刚输入的新姓名')
    await vi.advanceTimersByTimeAsync(1000)
    expect(api.saveAiResumeCardDraft).toHaveBeenCalledWith('conversation-1', 'contact-1', expect.objectContaining({ name: '用户刚输入的新姓名' }), 1)
  })

  it('serializes design edits and waits for the final acknowledged design before exporting', async () => {
    const first = deferred<AiResumeDesignPreference>()
    const second = deferred<AiResumeDesignPreference>()
    api.saveAiResumeDesign.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    api.exportAiResumePdf.mockRejectedValue(new Error('stop at export boundary'))
    const wrapper = mountWorkbench()
    await flushPromises()
    await openTab(wrapper, '设计')
    await pickFontScale(wrapper, 'SMALL')
    await vi.advanceTimersByTimeAsync(500)
    await pickFontScale(wrapper, 'LARGE')
    await vi.advanceTimersByTimeAsync(500)
    expect(api.saveAiResumeDesign).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="export-pdf"]').trigger('click')
    await wrapper.findAll('[role="dialog"] button').find((button) => button.text().includes('确认并下载'))!.trigger('click')
    await flushPromises()
    expect(api.exportAiResumePdf).not.toHaveBeenCalled()
    first.resolve({ ...template('template-a').design, settings: { ...settings, fontScale: 'SMALL' }, versionNo: 2 })
    await flushPromises()
    expect(fontScale(wrapper)).toBe('LARGE')
    expect(wrapper.get('.design-panel__head').text()).not.toContain('已保存')
    expect(api.saveAiResumeDesign).toHaveBeenLastCalledWith('conversation-1', 'template-a', 'MONO', expect.objectContaining({ fontScale: 'LARGE' }), 2)
    expect(api.exportAiResumePdf).not.toHaveBeenCalled()

    second.resolve({ ...template('template-a').design, settings: { ...settings, fontScale: 'LARGE' }, versionNo: 3 })
    await flushPromises()
    expect(api.exportAiResumePdf).toHaveBeenCalledTimes(1)
  })

  it('restores a rejected design from its local snapshot after re-entering the workbench', async () => {
    api.saveAiResumeDesign.mockRejectedValue(new ApiClientError({ category: 'CONFLICT', reason: 'VERSION_CONFLICT', message: '设计版本冲突' }, 409))
    const wrapper = mountWorkbench()
    await flushPromises()
    await openTab(wrapper, '设计')
    await pickFontScale(wrapper, 'LARGE')
    await vi.advanceTimersByTimeAsync(500)
    expect(fontScale(wrapper)).toBe('LARGE')
    wrapper.unmount()

    const restored = mountWorkbench()
    await flushPromises()
    await openTab(restored, '设计')
    expect(fontScale(restored)).toBe('LARGE')
    expect(restored.get('.design-panel__head').text()).not.toContain('已保存')
  })

  it('confirming one card retains both another dirty card and later edits to the submitted card', async () => {
    const confirmation = deferred<AiResumeConversation>()
    api.submitAiResumeCard.mockReturnValue(confirmation.promise)
    const wrapper = mountWorkbench()
    await flushPromises()
    await openTab(wrapper, '编辑')
    await wrapper.get('.card-editor input').setValue('未保存的新姓名')
    await wrapper.findAll('.card-nav button')[1]!.trigger('click')
    await wrapper.get('.card-editor footer button').trigger('click')
    expect(api.submitAiResumeCard).toHaveBeenCalledTimes(1)
    await wrapper.get('.card-editor textarea').setValue('确认请求发出以后又补充的简介')
    const next = conversation()
    next.cards[1]!.status = 'CONFIRMED'
    next.cards[1]!.versionNo = 2
    confirmation.resolve(next)
    await flushPromises()
    expect(wrapper.get<HTMLTextAreaElement>('.card-editor textarea').element.value).toBe('确认请求发出以后又补充的简介')
    await wrapper.findAll('.card-nav button')[0]!.trigger('click')
    expect(wrapper.get<HTMLInputElement>('.card-editor input').element.value).toBe('未保存的新姓名')
  })

  it('does not restore a stale template when an earlier card draft responds after template switching', async () => {
    const cardSave = deferred<AiResumeConversation>()
    api.saveAiResumeCardDraft.mockReturnValue(cardSave.promise)
    const selected = conversation('template-b')
    selected.layout!.version = 2
    api.selectAiResumeTemplate.mockResolvedValue(selected)
    const wrapper = mountWorkbench()
    await flushPromises()
    await openTab(wrapper, '编辑')
    await wrapper.get('.card-editor input').setValue('在途姓名')
    await vi.advanceTimersByTimeAsync(1000)
    await openTab(wrapper, '模板')
    await wrapper.findAll('.template-gallery button')[1]!.trigger('click')
    await flushPromises()
    const staleResponse = conversation('template-a')
    staleResponse.cards[0]!.payload.name = '在途姓名'
    staleResponse.cards[0]!.versionNo = 2
    cardSave.resolve(staleResponse)
    await flushPromises()
    expect(wrapper.get('.preview-template strong').text()).toBe('template-b')
    await openTab(wrapper, '编辑')
    expect(wrapper.get<HTMLInputElement>('.card-editor input').element.value).toBe('在途姓名')
  })

  it('retains a newer design backup when a previous mounted workbench save finishes late', async () => {
    const old = deferred<AiResumeDesignPreference>()
    api.saveAiResumeDesign.mockReturnValue(old.promise)
    const wrapper = mountWorkbench()
    await flushPromises()
    await openTab(wrapper, '设计')
    await pickFontScale(wrapper, 'SMALL')
    await vi.advanceTimersByTimeAsync(500)
    wrapper.unmount()
    const replacement = mountWorkbench()
    await flushPromises()
    await openTab(replacement, '设计')
    expect(fontScale(replacement)).toBe('SMALL')
    await pickFontScale(replacement, 'LARGE')
    old.resolve({ ...template('template-a').design, settings: { ...settings, fontScale: 'SMALL' }, versionNo: 2 })
    await flushPromises()
    replacement.unmount()
    const restored = mountWorkbench()
    await flushPromises()
    await openTab(restored, '设计')
    expect(fontScale(restored)).toBe('LARGE')
  })
})
