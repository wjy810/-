import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { ApplicationOverview, JobApplication } from '../types'

const api = vi.hoisted(() => ({ fetchApplications: vi.fn(), moveApplication: vi.fn() }))
vi.mock('../services/applicationsApi', () => api)
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' },
}))
vi.mock('vue-sonner', () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn() }) }))
import ApplicationsPage from './ApplicationsPage.vue'
import ApplicationBoard from '../components/ApplicationBoard.vue'

const recently = new Date(Date.now() - 20 * 60_000).toISOString()

function application(patch: Partial<JobApplication>): JobApplication {
  return {
    id: 'a1', company: '合成科技', positionTitle: 'Java 后端工程师', channel: 'REFERRAL', stage: 'APPLIED', furthestStage: 'APPLIED',
    version: 1, createdAt: recently, updatedAt: recently, ...patch,
  }
}

function overview(): ApplicationOverview {
  return {
    items: [
      application({
        link: { id: 'l1', token: 'tok', path: '/r/tok', label: '合成科技', resumeVersionId: 'v', hideContact: false, status: 'ACTIVE',
          viewCount: 2, visitorCount: 1, readSeconds: 95, lastViewedAt: recently, firstViewedAt: recently, createdAt: recently },
      }),
      application({ id: 'a2', company: '星图数据', positionTitle: '数据分析师', stage: 'INTERVIEW', furthestStage: 'INTERVIEW' }),
    ],
    stats: { total: 2, active: 2, applied: 2, linked: 1, opened: 1, interviews: 1, offers: 0, overdue: 0, upcoming: 0 },
  }
}

function mountPage() {
  return mount(ApplicationsPage, { global: { stubs: {
    ApplicationDrawer: true, ApplicationDialog: true, UiIllustration: true,
    UiDialog: { props: ['open', 'title'], template: '<div v-if="open" role="dialog"><h2>{{ title }}</h2><slot /><slot name="footer" /></div>' },
  } } })
}

describe('applications board', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    route.query = {}
    try { window.localStorage.clear() } catch { /* jsdom */ }
  })

  it('shows each application in its stage with the resume link receipt', async () => {
    api.fetchApplications.mockResolvedValue(overview())
    const wrapper = mountPage()
    await flushPromises()
    const columns = wrapper.findAll('.board__column')
    expect(columns.map(column => column.find('.board__count').text())).toEqual(['0', '1', '0', '1', '0', '0'])
    expect(columns[1]!.text()).toContain('已打开 2 次')
    expect(columns[3]!.text()).toContain('未附简历链接')
    expect(wrapper.get('.fresh-strip').text()).toContain('合成科技')
    expect(wrapper.text()).toContain('简历被打开')
  })

  it('moves a card at once and puts it back when the server refuses', async () => {
    api.fetchApplications.mockResolvedValue(overview())
    api.moveApplication.mockRejectedValue(new ApiClientError({ category: 'CONFLICT', reason: 'VERSION_CONFLICT', message: '版本冲突' }, 409))
    const wrapper = mountPage()
    await flushPromises()
    wrapper.getComponent(ApplicationBoard).vm.$emit('move', 'a1', 'ASSESSMENT')
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.board__column')[2]!.text()).toContain('合成科技')
    await flushPromises()
    expect(api.moveApplication).toHaveBeenCalledWith('a1', 'ASSESSMENT', 1, undefined)
    expect(wrapper.findAll('.board__column')[1]!.text()).toContain('合成科技')
  })

  it('asks for the outcome before an application is closed', async () => {
    api.fetchApplications.mockResolvedValue(overview())
    api.moveApplication.mockResolvedValue(application({ id: 'a2', company: '星图数据', stage: 'CLOSED', outcome: 'REJECTED', furthestStage: 'INTERVIEW', version: 2 }))
    const wrapper = mountPage()
    await flushPromises()
    wrapper.getComponent(ApplicationBoard).vm.$emit('move', 'a2', 'CLOSED')
    await flushPromises()
    expect(api.moveApplication).not.toHaveBeenCalled()
    expect(wrapper.get('[role="dialog"]').text()).toContain('结束「星图数据」的投递')
    await wrapper.get('[role="dialog"]').findAll('button').find(button => button.text() === '移到已结束')!.trigger('click')
    await flushPromises()
    expect(api.moveApplication).toHaveBeenCalledWith('a2', 'CLOSED', 1, 'REJECTED')
  })

  it('offers to record the first application when there is none', async () => {
    api.fetchApplications.mockResolvedValue({ items: [], stats: { ...overview().stats, total: 0 } })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('把每一次投递放进看板')
    expect(wrapper.find('.board').exists()).toBe(false)
  })
})
