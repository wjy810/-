import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { makeSession } from '../../../../tests/careerPlanningFixtures'

const api = vi.hoisted(() => ({
  archiveCareerPlanningSession: vi.fn(),
  createCareerCanvas: vi.fn(),
  fetchCareerCanvasDashboard: vi.fn(),
  makeCareerCanvasPrimary: vi.fn(),
}))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const confirmMock = vi.hoisted(() => vi.fn())
vi.mock('@/shared/ui/confirm', () => ({ confirm: confirmMock }))

vi.mock('../services/careerPlanningApi', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

import CareerCanvasOverviewPage from './CareerCanvasOverviewPage.vue'

const dashboard = {
  stats: { canvasCount: 2, primaryCount: 1, abilityNodeCount: 55, pendingValidationCount: 3, versionCount: 8 },
  items: [
    {
      sessionId: 'session-1', goalId: 'goal-1', title: 'Java 后端工程师', status: 'ACTIVE', primary: true,
      overallProgress: 72, masteredCount: 18, learningCount: 3, learnableCount: 25, nodeCount: 31, domainCount: 5, pendingValidationCount: 2, canvasVersion: 4,
      planStatus: 'ACTIVE', currentWeek: 3, durationWeeks: 12, planRevision: 2,
      currentFocus: '完成订单 API 与接口测试', recentChanges: ['计划状态与节点进度已更新'],
      createdAt: '2026-08-20T00:00:00Z', updatedAt: '2026-08-28T00:00:00Z',
    },
    {
      sessionId: 'session-2', goalId: 'goal-2', title: 'AI 应用工程师', status: 'ACTIVE', primary: false,
      overallProgress: 0, masteredCount: 0, learningCount: 0, learnableCount: 0, nodeCount: 24, domainCount: 4, pendingValidationCount: 1, canvasVersion: 4,
      planStatus: 'NOT_CREATED', currentWeek: null, durationWeeks: null, planRevision: 0,
      currentFocus: '等待 AI 生成完整能力树', recentChanges: [],
      createdAt: '2026-08-25T00:00:00Z', updatedAt: '2026-08-27T00:00:00Z',
    },
  ],
  drafts: [
    { sessionId: 'draft-1', phase: 'INTERVIEW', entryMode: 'AI_DISCOVERY', createdAt: '2026-08-26T00:00:00Z', updatedAt: '2026-08-26T01:00:00Z' },
  ],
}

const stubs = {
  AppChrome: { template: '<div><slot name="topbar"/><slot/></div>' },
  UiSelect: { props: ['modelValue'], template: '<select :value="modelValue" />' },
  RouterLink: { template: '<a><slot/></a>' },
  UiDialog: {
    props: ['open'],
    template: '<section v-if="open" role="dialog"><slot/><footer><slot name="footer"/></footer></section>',
  },
  JobTaxonomyPicker: {
    template: '<button class="taxonomy-fixture" @click="$emit(\'select\', { job: { id: \'job-1\', displayName: \'后端开发\' }, categoryId: \'category-1\', groupId: \'group-1\' })">选择测试岗位</button>',
  },
  UiIllustration: true,
}

describe('CareerCanvasOverviewPage', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    router.push.mockReset()
    router.replace.mockReset()
    route.query = {}
    api.fetchCareerCanvasDashboard.mockResolvedValue(dashboard)
    api.makeCareerCanvasPrimary.mockResolvedValue({
      ...dashboard,
      items: dashboard.items.map(item => ({ ...item, primary: item.sessionId === 'session-2' })),
    })
    api.createCareerCanvas.mockResolvedValue({ ...makeSession(), id: 'session-new' })
  })

  it('renders independent canvas summaries and creates a fresh target canvas', async () => {
    const wrapper = mount(CareerCanvasOverviewPage, { global: { stubs } })
    await flushPromises()

    expect(api.fetchCareerCanvasDashboard).toHaveBeenCalledWith('', 'ALL', 'RECENT')
    expect(wrapper.text()).toContain('能力画布总览')
    expect(wrapper.text()).toContain('Java 后端工程师')
    expect(wrapper.text()).toContain('AI 应用工程师')
    expect(wrapper.text()).toContain('55')
    expect(wrapper.text()).not.toContain('可复用节点')
    expect(wrapper.get('.new-canvas-button [data-jobproof-icon]').attributes('data-jobproof-icon')).toBe('planning-new-canvas')
    expect(wrapper.findAll('.overview-stats [data-jobproof-icon]').map(icon => icon.attributes('data-jobproof-icon'))).toEqual([
      'planning-ability-canvas', 'planning-pending-validation', 'planning-canvas-versions',
    ])
    expect(wrapper.find('.overview-heading button[title="聚焦搜索"]').exists()).toBe(false)
    expect(wrapper.get('input[aria-label="搜索职业或能力节点"]').exists()).toBe(true)

    await wrapper.get('button[title="设为主目标"]').trigger('click')
    await flushPromises()
    expect(api.makeCareerCanvasPrimary).toHaveBeenCalledWith('session-2')

    await wrapper.findAll('button').find(button => button.text().includes('新建能力画布'))!.trigger('click')
    await wrapper.get('.taxonomy-fixture').trigger('click')
    const create = wrapper.findAll('button').find(button => button.text().includes('创建目标画布'))!
    expect((create.element as HTMLButtonElement).disabled).toBe(true)
    expect(wrapper.get('.dialog-consent input').element).toHaveProperty('checked', false)
    expect(wrapper.find('#new-canvas-consent-hint').exists()).toBe(true)

    await wrapper.get('.dialog-consent input').setValue(true)
    expect((create.element as HTMLButtonElement).disabled).toBe(false)
    await create.trigger('click')
    await flushPromises()
    expect(api.createCareerCanvas).toHaveBeenCalledWith('job-1', true)
    expect(router.push).toHaveBeenCalledWith({
      name: 'career-planning-session', params: { sessionId: 'session-new' }, query: { view: 'canvas', source: 'new' },
    })
  })

  it('shows an error with retry instead of an empty overview when the first read fails', async () => {
    api.fetchCareerCanvasDashboard.mockReset()
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce(dashboard)
    const wrapper = mount(CareerCanvasOverviewPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('.overview-stats').exists()).toBe(false)
    expect(wrapper.find('.create-canvas-card').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Java 后端工程师')
  })

  it('reports progress as what was mastered and lets unfinished explorations be resumed or removed', async () => {
    api.archiveCareerPlanningSession.mockResolvedValue({ ...dashboard, drafts: [] })
    const wrapper = mount(CareerCanvasOverviewPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('已掌握 18 / 25 项 · 学习中 3')
    expect(wrapper.text()).toContain('能力树生成后开始计算')
    expect(wrapper.find('select[aria-label="筛选能力画布状态"]').exists()).toBe(false)

    const draft = wrapper.get('.draft-section li')
    expect(draft.text()).toContain('AI 帮我选方向')
    expect(draft.text()).toContain('停在「AI 访谈」')
    await draft.get('.draft-resume').trigger('click')
    expect(router.push).toHaveBeenCalledWith({ name: 'career-planning-session', params: { sessionId: 'draft-1' } })

    confirmMock.mockResolvedValueOnce(false)
    await draft.get('.draft-remove').trigger('click')
    await flushPromises()
    expect(api.archiveCareerPlanningSession).not.toHaveBeenCalled()
    confirmMock.mockResolvedValueOnce(true)
    await draft.get('.draft-remove').trigger('click')
    await flushPromises()
    expect(api.archiveCareerPlanningSession).toHaveBeenCalledWith('draft-1')
    expect(wrapper.find('.draft-section').exists()).toBe(false)
  })
})
