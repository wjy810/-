import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApplicationDetail, JobApplication, ResumeLink } from '../types'

const api = vi.hoisted(() => ({
  archiveApplication: vi.fn(), completeNextStep: vi.fn(), createResumeLink: vi.fn(), fetchApplication: vi.fn(),
  fetchShareableVersions: vi.fn(), markOwnView: vi.fn(), moveApplication: vi.fn(), restoreApplication: vi.fn(),
  revokeResumeLink: vi.fn(), updateResumeLink: vi.fn(),
}))
vi.mock('../services/applicationsApi', () => api)
vi.mock('@/shared/ui/confirm', () => ({ confirm: vi.fn() }))
vi.mock('vue-sonner', () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn() }) }))
vi.mock('vue-router', () => ({ RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' } }))
import ApplicationDrawer from './ApplicationDrawer.vue'

const stubs = {
  UiDrawer: { props: ['open'], template: '<div v-if="open"><slot /><slot name="footer" /></div>' },
  UiIllustration: true,
  UiSelect: { props: ['modelValue'], template: '<select />' },
}

function link(patch: Partial<ResumeLink> = {}): ResumeLink {
  return { id: 'l1', token: 't1', path: '/r/t1', label: '归档科技 · 数据分析师', resumeVersionId: 'v1', applicationId: 'a1',
    hideContact: false, status: 'SUSPENDED', viewCount: 0, visitorCount: 0, readSeconds: 0, createdAt: '2026-10-10T00:00:00Z', ...patch }
}
function application(patch: Partial<JobApplication> = {}): JobApplication {
  return { id: 'a1', company: '归档科技', positionTitle: '数据分析师', channel: 'JOB_BOARD', stage: 'APPLIED', furthestStage: 'APPLIED',
    version: 2, createdAt: '2026-10-10T00:00:00Z', updatedAt: '2026-10-12T00:00:00Z', archivedAt: '2026-10-12T00:00:00Z',
    link: link(), ...patch }
}
function detail(app: JobApplication): ApplicationDetail {
  return { application: app, events: [], links: app.link ? [app.link] : [], views: [] }
}

describe('ApplicationDrawer archived', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    api.fetchShareableVersions.mockResolvedValue([])
  })

  it('says the link is off while archived and restores from the drawer instead of offering to archive again', async () => {
    api.fetchApplication.mockResolvedValueOnce(detail(application()))
      .mockResolvedValueOnce(detail(application({ archivedAt: null, link: link({ status: 'ACTIVE' }) })))
    const restored = application({ archivedAt: null, link: link({ status: 'ACTIVE' }) })
    api.restoreApplication.mockResolvedValue(restored)
    const wrapper = mount(ApplicationDrawer, { props: { open: true, applicationId: 'a1' }, global: { stubs } })
    await flushPromises()
    expect(wrapper.get('[data-testid="archived-link-note"]').text()).toContain('专属链接已停用')
    expect(wrapper.text()).not.toContain('生成专属链接')
    expect(wrapper.text()).not.toContain('移入归档')

    await wrapper.findAll('button').find(button => button.text() === '从归档恢复')!.trigger('click')
    await flushPromises()
    expect(api.restoreApplication).toHaveBeenCalledWith('a1')
    expect(wrapper.emitted('restored')?.[0]?.[0]).toEqual(restored)
    expect(wrapper.text()).toContain('移入归档')
  })

  it('offers to turn a link back on when the application came back but the plan was full', async () => {
    api.fetchApplication.mockResolvedValue(detail(application({ archivedAt: null })))
    api.updateResumeLink.mockResolvedValue(link({ status: 'ACTIVE' }))
    const wrapper = mount(ApplicationDrawer, { props: { open: true, applicationId: 'a1' }, global: { stubs } })
    await flushPromises()
    const waiting = wrapper.get('[data-testid="waiting-link"]')
    expect(waiting.text()).toContain('有效链接名额已满')
    await waiting.get('button').trigger('click')
    await flushPromises()
    expect(api.updateResumeLink).toHaveBeenCalledWith('l1', { reactivate: true })
  })
})
