import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { makeCanvas, makePlan } from '../../../../tests/careerPlanningFixtures'
import { ModalStub } from '../../../../tests/vueStubs'

const api = vi.hoisted(() => ({
  addCareerLearningEvidence: vi.fn(),
  saveCareerWeeklyReview: vi.fn(),
  updateCareerLearningPlan: vi.fn(),
  updateCareerPlanTask: vi.fn(),
}))
vi.mock('../services/careerPlanningApi', () => api)
const library = vi.hoisted(() => ({ fetchCareerRecords: vi.fn(), fetchCareerFiles: vi.fn() }))
vi.mock('@/features/career-library/services/careerLibraryApi', () => library)
const confirmMock = vi.hoisted(() => vi.fn())
vi.mock('@/shared/ui/confirm', () => ({ confirm: confirmMock }))

import CareerLearningPlan from './CareerLearningPlan.vue'

describe('CareerLearningPlan', () => {
  beforeEach(() => [...Object.values(api), ...Object.values(library), confirmMock].forEach(mock => mock.mockReset()))

  it('renders one transition-safe root and inherits tabpanel accessibility attributes', () => {
    const canvas = makeCanvas()
    const wrapper = mount(CareerLearningPlan, {
      attrs: {
        id: 'career-workbench-panel-plan',
        role: 'tabpanel',
        'aria-labelledby': 'career-workbench-tab-plan',
      },
      props: { sessionId: 'session-1', plan: makePlan(canvas), canvas, canCreate: true },
      global: { stubs: { UiDialog: ModalStub } },
    })

    expect(wrapper.element.tagName).toBe('DIV')
    expect(wrapper.attributes()).toMatchObject({
      id: 'career-workbench-panel-plan',
      role: 'tabpanel',
      'aria-labelledby': 'career-workbench-tab-plan',
    })
  })

  it('blocks evidence-required completion and supports deterministic same-week reordering', async () => {
    const canvas = makeCanvas()
    const plan = makePlan(canvas)
    api.updateCareerPlanTask.mockResolvedValue(plan)
    const wrapper = mount(CareerLearningPlan, {
      props: { sessionId: 'session-1', plan, canvas, canCreate: true },
      global: { stubs: { UiDialog: ModalStub } },
    })

    await wrapper.findAll('button[aria-label="完成任务"]')[0].trigger('click')
    expect(api.updateCareerPlanTask).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('添加能力证据')
    expect(wrapper.emitted('notice')?.[0]?.[0]).toContain('先关联证据')

    await wrapper.findAll('button[aria-label="下移任务"]')[0].trigger('click')
    await flushPromises()
    expect(api.updateCareerPlanTask).toHaveBeenCalledWith('session-1', 'plan-1', 'task-1', {
      targetWeek: 1,
      sortOrder: 1,
      expectedVersion: 1,
    })
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })

  it('links a confirmed career-library record as evidence instead of only a typed note', async () => {
    const canvas = makeCanvas()
    const plan = makePlan(canvas)
    library.fetchCareerRecords.mockResolvedValue({ items: [
      { id: 'record-ok', title: '订单系统重构', organization: '拾光电商', role: '后端实习', confirmed: true },
      { id: 'record-draft', title: '未确认的草稿', confirmed: false },
    ], total: 2 })
    api.addCareerLearningEvidence.mockResolvedValue(plan)
    const wrapper = mount(CareerLearningPlan, {
      props: { sessionId: 'session-1', plan, canvas, canCreate: true },
      global: { stubs: { UiDialog: ModalStub } },
    })

    await wrapper.findAll('.task-evidence button').find(button => button.text().includes('添加证据'))!.trigger('click')
    await wrapper.findAll('.evidence-source button').find(button => button.text() === '资料库经历')!.trigger('click')
    await flushPromises()
    const options = wrapper.findAll('.library-options button')
    expect(options.map(option => option.text())).toEqual([expect.stringContaining('订单系统重构')])
    expect(wrapper.get('.dialog-primary').attributes('disabled')).toBeDefined()
    await options[0].trigger('click')
    await wrapper.get('.dialog-primary').trigger('click')
    await flushPromises()
    expect(api.addCareerLearningEvidence).toHaveBeenCalledWith('session-1', 'plan-1', expect.objectContaining({
      sourceType: 'CAREER_RECORD', sourceId: 'record-ok', title: '订单系统重构',
    }))
  })

  it('asks before replacing the plan', async () => {
    const canvas = makeCanvas()
    const wrapper = mount(CareerLearningPlan, {
      props: { sessionId: 'session-1', plan: makePlan(canvas), canvas, canCreate: true },
      global: { stubs: { UiDialog: ModalStub } },
    })
    const replan = wrapper.findAll('.plan-actions button').find(button => button.text().includes('重新规划'))!
    confirmMock.mockResolvedValueOnce(false)
    await replan.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('create')).toBeUndefined()
    confirmMock.mockResolvedValueOnce(true)
    await replan.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('create')).toHaveLength(1)
  })
})
