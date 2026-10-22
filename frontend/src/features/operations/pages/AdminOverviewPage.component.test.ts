import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { OperationsOverview } from '../types'

const api = vi.hoisted(() => ({ fetchOperationsOverview: vi.fn() }))
vi.mock('../services/operationsApi', () => api)
vi.mock('vue-router', () => ({ RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :data-to="to"><slot /></a>' } }))
import AdminOverviewPage from './AdminOverviewPage.vue'
import DailyColumns from '../components/DailyColumns.vue'

const days = Array.from({ length: 14 }, (_, index) => ({ day: `2026-10-${String(6 + index).padStart(2, '0')}`, signups: index === 13 ? 3 : 0, applications: index, opens: 0 }))
const overview: OperationsOverview = {
  generatedAt: '2026-10-19T03:45:00Z',
  users: { total: 12_900, new7: 40, new30: 120, active7: 300, active30: 900 },
  // The API leaves out nulls, so rates can be missing entirely.
  membership: { pro: 129, proShare: 1, redeemed30: 20, codesIssued: 500, codesRedeemed: 210, activeBatches: 3 },
  modules: [{ key: 'applications', label: '投递记录', total: 640, last7: 0 }],
  ai: { period: '2026-10', usedUnits: 4200, accounts: 88, quotaUnits: 4000, walletUnits: 150, personalUnits: 50 },
  funnel: [
    { key: 'registered', label: '注册', accounts: 12_900 },
    { key: 'resume', label: '写了简历', accounts: 9000, rate: 70 },
    { key: 'pro', label: '开通过专业版', accounts: 129, rate: 1 },
  ],
  days,
}

describe('operations overview', () => {
  it('leads with the tiles, one chart per measure and the funnel', async () => {
    api.fetchOperationsOverview.mockResolvedValue(overview)
    const wrapper = mount(AdminOverviewPage, { global: { stubs: { UiIllustration: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('1.3万')
    expect(wrapper.text()).toContain('占 1% · 近 30 天开通 20 次')
    const charts = wrapper.findAllComponents(DailyColumns)
    expect(charts.map(chart => chart.props('title'))).toEqual(['新注册', '新投递', '简历被打开'])
    expect(charts[0]!.props('points')).toHaveLength(14)
    expect(wrapper.findAll('.funnel__rate').map(rate => rate.text())).toEqual(['起点', '较上一步 70%', '占注册 1%'])
    expect(wrapper.text()).not.toContain('undefined')
    expect(wrapper.text()).toContain('额度 4000 / 余额 150 / 个人 50')
    expect(wrapper.findAll('.ops__table tbody tr')).toHaveLength(14)
    expect(wrapper.get('.modules tbody td:last-child').text()).toBe('0')
  })
})
