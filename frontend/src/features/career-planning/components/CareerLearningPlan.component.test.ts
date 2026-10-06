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

import CareerLearningPlan from './CareerLearningPlan.vue'

describe('CareerLearningPlan', () => {
  beforeEach(() => Object.values(api).forEach(mock => mock.mockReset()))

  it('renders one transition-safe root and inherits tabpanel accessibility attributes', () => {
    const canvas = makeCanvas()
    const wrapper = mount(CareerLearningPlan, {
      attrs: {
        id: 'career-workbench-panel-plan',
        role: 'tabpanel',
        'aria-labelledby': 'career-workbench-tab-plan',
      },
      props: { sessionId: 'session-1', plan: makePlan(canvas), canvas, canCreate: true },
      global: { stubs: { AppModal: ModalStub } },
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
      global: { stubs: { AppModal: ModalStub } },
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
})
