import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({ fetchSharedResume: vi.fn(), startResumeView: vi.fn(), sendReadTime: vi.fn() }))
vi.mock('../services/applicationsApi', () => api)
vi.mock('@/resume-render/templates/registry', () => ({ loadTemplate: vi.fn(async () => ({ manifest: { id: 'classic' } })) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { token: 'tok123' } }) }))
import SharedResumePage from './SharedResumePage.vue'

function shared(ownerPreview = false) {
  return {
    templateId: 'classic', design: {}, content: { basics: { name: '周以宁' } }, photo: null, pageLimit: 2,
    sharedAt: '2026-10-18T08:00:00Z', expiresAt: null, ownerPreview, hideContact: false,
  }
}

function mountPage() {
  return mount(SharedResumePage, { global: { stubs: { ResumeDocument: true, BrandMark: true, UiIllustration: true } } })
}

describe('shared resume page', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    document.title = ''
  })

  it('shows the resume, says what is recorded and starts a receipt', async () => {
    api.fetchSharedResume.mockResolvedValue(shared())
    api.startResumeView.mockResolvedValue({ recorded: true, viewId: 'v1', viewKey: 'k1', readSeconds: 0 })
    const wrapper = mountPage()
    await flushPromises()
    expect(api.fetchSharedResume).toHaveBeenCalledWith('tok123')
    expect(api.startResumeView).toHaveBeenCalledWith('tok123', expect.any(String))
    expect(wrapper.find('resume-document-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('不记录你的 IP 地址')
    expect(document.title).toBe('周以宁的简历')
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex, nofollow')
    wrapper.unmount()
  })

  it('never counts the owner looking at their own link', async () => {
    api.fetchSharedResume.mockResolvedValue(shared(true))
    const wrapper = mountPage()
    await flushPromises()
    expect(api.startResumeView).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('你自己打开不会计入回执')
    wrapper.unmount()
  })

  it('explains a revoked link instead of an empty page', async () => {
    api.fetchSharedResume.mockRejectedValue(new ApiClientError({ category: 'USER_CORRECTABLE', reason: 'RESUME_LINK_REVOKED', message: '已撤回' }, 400))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('求职者已撤回这个链接')
    expect(wrapper.find('resume-document-stub').exists()).toBe(false)
    wrapper.unmount()
  })
})
