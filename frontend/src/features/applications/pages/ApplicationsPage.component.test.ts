import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { ApplicationOverview, JobApplication } from '../types'

const api = vi.hoisted(() => ({
  fetchApplications: vi.fn(), moveApplication: vi.fn(), fetchArchivedApplications: vi.fn(), restoreApplication: vi.fn(),
  deleteApplication: vi.fn(),
}))
const confirmMock = vi.hoisted(() => vi.fn())
vi.mock('@/shared/ui/confirm', () => ({ confirm: confirmMock }))
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
    archivedCount: 0,
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
    confirmMock.mockReset()
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

  it('narrows the list to overdue next steps and opened resumes', async () => {
    const data = overview()
    data.items[1] = { ...data.items[1]!, nextStep: '一面', nextStepAt: new Date(Date.now() - 3_600_000).toISOString() }
    api.fetchApplications.mockResolvedValue(data)
    window.localStorage.setItem('jp:applications:view', 'list')
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.findAll('.applications-table tbody tr')).toHaveLength(2)
    const chip = (label: string) => wrapper.findAll('.filter-chip').find(button => button.text().startsWith(label))!
    await chip('下一步已逾期').trigger('click')
    expect(wrapper.findAll('.applications-table tbody tr').map(row => row.find('strong').text())).toEqual(['星图数据'])
    await chip('下一步已逾期').trigger('click')
    await chip('简历被打开过').trigger('click')
    expect(wrapper.findAll('.applications-table tbody tr').map(row => row.find('strong').text())).toEqual(['合成科技'])
    await wrapper.get('.filter-clear').trigger('click')
    expect(wrapper.findAll('.applications-table tbody tr')).toHaveLength(2)
  })

  it('offers accept or decline only once an offer was reached, and offers to close the rest after accepting', async () => {
    const data = overview()
    data.items[1] = { ...data.items[1]!, stage: 'OFFER', furthestStage: 'OFFER' }
    api.fetchApplications.mockResolvedValue(data)
    api.moveApplication.mockImplementation(async (id: string, stage: string, _version: number, outcome?: string) =>
      ({ ...data.items.find(item => item.id === id)!, stage, outcome: outcome ?? null, version: 2 }))
    confirmMock.mockResolvedValue(true)
    const wrapper = mountPage()
    await flushPromises()

    wrapper.getComponent(ApplicationBoard).vm.$emit('move', 'a1', 'CLOSED')
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('接受 Offer')
    await wrapper.get('[role="dialog"]').findAll('button').find(button => button.text() === '取消')!.trigger('click')

    wrapper.getComponent(ApplicationBoard).vm.$emit('move', 'a2', 'CLOSED', 'ACCEPTED')
    await flushPromises()
    expect(confirmMock).toHaveBeenCalledWith(expect.objectContaining({ confirmText: '一起结束' }))
    expect(api.moveApplication).toHaveBeenCalledWith('a1', 'CLOSED', 1, 'WITHDRAWN')
  })

  it('lists archived applications and restores one', async () => {
    const data = overview()
    data.archivedCount = 1
    api.fetchApplications.mockResolvedValue(data)
    const old = application({ id: 'a9', company: '青禾教育', archivedAt: recently })
    api.fetchArchivedApplications.mockResolvedValue([old])
    api.restoreApplication.mockResolvedValue({ ...old, archivedAt: null })
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text().includes('归档 1'))!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.archive-list').text()).toContain('青禾教育')
    await wrapper.get('.archive-list').findAll('button').find(button => button.text().includes('恢复'))!.trigger('click')
    await flushPromises()
    expect(api.restoreApplication).toHaveBeenCalledWith('a9')
    expect(wrapper.get('.archive-list').text()).toContain('归档是空的')
  })
})
