import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ResumeLink } from '../types'

const api = vi.hoisted(() => ({
  fetchResumeLinks: vi.fn(),
  fetchLinkViews: vi.fn(),
  fetchShareableVersions: vi.fn(),
  createResumeLink: vi.fn(),
  revokeResumeLink: vi.fn(),
  updateResumeLink: vi.fn(),
  markOwnView: vi.fn(),
}))
vi.mock('../services/applicationsApi', () => api)
const dialogs = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/shared/ui/confirm', () => dialogs)
const sonner = vi.hoisted(() => Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn() }))
vi.mock('vue-sonner', () => ({ toast: sonner }))
vi.mock('vue-router', () => ({ RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' } }))
import ResumeLinksPanel from './ResumeLinksPanel.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'

const now = new Date('2026-10-19T04:00:00Z')
function link(patch: Partial<ResumeLink>): ResumeLink {
  return { id: 'l1', token: 't1', path: '/r/t1', label: '合成科技 · Java 后端工程师', resumeVersionId: 'v1', resumeTitle: '导入简历', applicationId: 'a1',
    hideContact: false, status: 'ACTIVE', viewCount: 3, visitorCount: 2, readSeconds: 252, lastViewedAt: '2026-10-19T03:30:00Z', createdAt: '2026-10-10T00:00:00Z', ...patch }
}
const dialogStub = { props: ['open', 'title'], emits: ['close'], template: '<div v-if="open" role="dialog"><h2>{{ title }}</h2><slot /><slot name="footer" /></div>' }
const mountPanel = () => mount(ResumeLinksPanel, { props: { now }, global: { stubs: { UiIllustration: true, UiDialog: dialogStub } } })

describe('resume links panel', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    dialogs.confirm.mockReset()
    sonner.success.mockReset()
  })

  it('lists application and general links with their receipts and filters them', async () => {
    api.fetchResumeLinks.mockResolvedValue([
      link({}),
      link({ id: 'g1', label: '秋招双选会', applicationId: null, viewCount: 0, visitorCount: 0, readSeconds: 0, lastViewedAt: null, expiresAt: '2026-10-21T00:00:00Z' }),
      link({ id: 'r1', label: '旧链接', applicationId: null, status: 'REVOKED', viewCount: 0, lastViewedAt: null }),
    ])
    const wrapper = mountPanel()
    await flushPromises()
    const rows = wrapper.findAll('.link-row')
    expect(rows.map(row => row.find('strong').text())).toEqual(['合成科技 · Java 后端工程师', '秋招双选会', '旧链接'])
    expect(rows[0]!.text()).toContain('打开 3 次 · 2 位读者 · 累计阅读 4 分 12 秒')
    expect(rows[1]!.text()).toContain('通用')
    expect(rows[1]!.text()).toContain('2 天后到期')
    expect(rows[2]!.classes()).toContain('is-off')
    wrapper.getComponent(UiSegmented).vm.$emit('update:modelValue', 'general')
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.link-row').map(row => row.find('strong').text())).toEqual(['秋招双选会', '旧链接'])
  })

  it('creates a general link and shows who opened a link', async () => {
    api.fetchResumeLinks.mockResolvedValue([link({})])
    api.fetchShareableVersions.mockResolvedValue([{ versionId: 'v9', masterId: 'm', resumeTitle: '导入简历', ordinal: 3, anonymous: false, frozenAt: '2026-10-01T00:00:00Z' }])
    api.createResumeLink.mockResolvedValue(link({ id: 'g2', label: '校友内推群', applicationId: null, viewCount: 0, lastViewedAt: null }))
    api.fetchLinkViews.mockResolvedValue([{ id: 'v', linkId: 'l1', device: 'MOBILE', client: 'WECHAT', referrerHost: null, startedAt: '2026-10-19T03:30:00Z', readSeconds: 125 }])
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.findAll('button').find(button => button.text() === '打开记录')!.trigger('click')
    await flushPromises()
    expect(api.fetchLinkViews).toHaveBeenCalledWith('l1')
    expect(wrapper.get('.link-row__views').text()).toContain('读了 2 分 5 秒')

    await wrapper.findAll('button').find(button => button.text() === '新建通用链接')!.trigger('click')
    await flushPromises()
    await wrapper.get('#general-link-form').trigger('submit')
    expect(api.createResumeLink).not.toHaveBeenCalled()
    await wrapper.get('#general-link-form input').setValue('校友内推群')
    await wrapper.get('#general-link-form').trigger('submit')
    await flushPromises()
    expect(api.createResumeLink).toHaveBeenCalledWith({ resumeVersionId: 'v9', label: '校友内推群', hideContact: false, expiresInDays: 60 })
    expect(wrapper.findAll('.link-row').map(row => row.find('strong').text())).toContain('校友内推群')
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('revokes a link after confirmation', async () => {
    api.fetchResumeLinks.mockResolvedValue([link({})])
    api.revokeResumeLink.mockResolvedValue(link({ status: 'REVOKED' }))
    dialogs.confirm.mockResolvedValue(true)
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '撤回')!.trigger('click')
    await flushPromises()
    expect(api.revokeResumeLink).toHaveBeenCalledWith('l1')
    expect(wrapper.get('.link-row').classes()).toContain('is-off')
    expect(wrapper.findAll('button').some(button => button.text() === '复制')).toBe(false)
  })

  it('lets a general link be renamed, hidden and renewed in place, and previews only links that open', async () => {
    const general = link({ id: 'g1', label: '双选会', applicationId: null })
    const expired = link({ id: 'e1', label: '旧群', applicationId: null, status: 'EXPIRED', viewCount: 0, lastViewedAt: null })
    api.fetchResumeLinks.mockResolvedValue([general, expired])
    api.updateResumeLink.mockImplementation(async (id: string, patch: Record<string, unknown>) =>
      id === 'g1' ? { ...general, label: String(patch.label ?? general.label), hideContact: Boolean(patch.hideContact) } : { ...expired, status: 'ACTIVE' })
    const wrapper = mountPanel()
    await flushPromises()
    const rows = wrapper.findAll('.link-row')
    expect(rows[0]!.text()).toContain('预览')
    expect(rows[1]!.text()).not.toContain('预览')
    expect(rows[1]!.text()).not.toContain('复制')
    expect(rows[1]!.get('[data-testid="link-off-reason"]').text()).toContain('重新设置有效期即可启用')

    await rows[0]!.get('[data-testid="link-settings"]').trigger('click')
    await wrapper.get('#link-settings-form input').setValue('秋招双选会')
    await wrapper.get('#link-settings-form .ui-checkbox__box').trigger('click')
    await wrapper.get('#link-settings-form').trigger('submit')
    await flushPromises()
    expect(api.updateResumeLink).toHaveBeenCalledWith('g1', { label: '秋招双选会', hideContact: true })
    expect(wrapper.findAll('.link-row')[0]!.find('strong').text()).toBe('秋招双选会')

    const renew = wrapper.findAll('.link-row')[1]!.get('[data-testid="link-settings"]')
    expect(renew.text()).toBe('重新启用')
    await renew.trigger('click')
    expect(wrapper.get('[role="dialog"] h2').text()).toBe('重新启用链接')
    await wrapper.get('#link-settings-form').trigger('submit')
    await flushPromises()
    expect(api.updateResumeLink).toHaveBeenLastCalledWith('e1', { expiresInDays: 60 })
  })

  it('takes the owner\'s own opening out of a general link\'s receipts', async () => {
    const general = link({ id: 'g1', label: '个人主页', applicationId: null, viewCount: 2, visitorCount: 2 })
    api.fetchResumeLinks.mockResolvedValue([general])
    api.fetchLinkViews
      .mockResolvedValueOnce([{ id: 'w1', linkId: 'g1', device: 'MOBILE', client: 'WECHAT', referrerHost: null, startedAt: '2026-10-19T03:30:00Z', readSeconds: 20 }])
      .mockResolvedValueOnce([])
    api.markOwnView.mockResolvedValue({ ...general, viewCount: 1, visitorCount: 1 })
    dialogs.confirm.mockResolvedValue(true)
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '打开记录')!.trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="mark-own"]').trigger('click')
    await flushPromises()
    expect(api.markOwnView).toHaveBeenCalledWith('g1', 'w1')
    expect(wrapper.get('.link-row').text()).toContain('打开 1 次 · 1 位读者')
  })

  it('turns a link left off after its application came back on again', async () => {
    const waiting = link({ id: 's1', status: 'SUSPENDED' })
    api.fetchResumeLinks.mockResolvedValue([waiting])
    api.updateResumeLink.mockResolvedValue({ ...waiting, status: 'ACTIVE' })
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.get('.link-row').text()).toContain('已停用')
    expect(wrapper.get('[data-testid="link-off-reason"]').text()).toContain('投递已归档')
    expect(wrapper.get('.link-row').text()).not.toContain('预览')
    await wrapper.get('[data-testid="reactivate-link"]').trigger('click')
    await flushPromises()
    expect(api.updateResumeLink).toHaveBeenCalledWith('s1', { reactivate: true })
    expect(wrapper.get('.link-row').text()).toContain('复制')
  })
})
