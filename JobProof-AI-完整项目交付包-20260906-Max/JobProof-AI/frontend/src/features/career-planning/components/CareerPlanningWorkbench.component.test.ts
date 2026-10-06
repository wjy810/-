import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { makePlan, makeSession } from '../../../../tests/careerPlanningFixtures'

const api = vi.hoisted(() => ({
  fetchCareerExecution: vi.fn(),
  fetchCareerPlanningSession: vi.fn(),
}))
const route = vi.hoisted(() => ({ query: { view: 'canvas' } as Record<string, string> }))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))

vi.mock('../services/careerPlanningApi', async () => ({
  ...(await vi.importActual<typeof import('../services/careerPlanningApi')>('../services/careerPlanningApi')),
  ...api,
}))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

import CareerPlanningWorkbench from './CareerPlanningWorkbench.vue'

const viewStubs = {
  CareerAbilityCanvas: { emits: ['validate'], template: '<section class="canvas-stub"><input value="canvas"><button class="validate-node" @click="$emit(\'validate\',\'skill-oop\')">验证</button><button class="validate-batch" @click="$emit(\'validate\',[\'skill-oop\',\'task-api\'])">批量验证</button></section>' },
  CareerLearningPlanView: { template: '<section class="plan-stub"><textarea aria-label="计划草稿"></textarea></section>' },
  CareerValidationWorkspace: { props: ['initialNodeIds'], template: '<section class="validation-stub" :data-node-ids="initialNodeIds.join(\',\')"><textarea aria-label="验证说明"></textarea></section>' },
  CareerVersionHistory: { template: '<section class="history-stub">版本</section>' },
  CareerProposalReview: { template: '<div />' },
  CareerPlanSetupDialog: { template: '<div />' },
}

describe('CareerPlanningWorkbench navigation', () => {
  beforeEach(() => {
    route.query = { view: 'canvas' }
    router.push.mockReset()
    router.replace.mockReset()
    const session = makeSession()
    api.fetchCareerExecution.mockResolvedValue({
      activePlan: makePlan(session.canvas!),
      pendingProposal: null,
      validations: [],
    })
    api.fetchCareerPlanningSession.mockResolvedValue(session)
  })

  it('exposes accessible tabs, keyboard navigation, and five explicit mobile destinations', async () => {
    const wrapper = mount(CareerPlanningWorkbench, {
      props: { session: makeSession() },
      global: { stubs: viewStubs },
    })
    await flushPromises()

    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs).toHaveLength(4)
    expect(tabs.map(tab => tab.get('[data-jobproof-icon]').attributes('data-jobproof-icon'))).toEqual([
      'planning-ability-canvas', 'planning-learning-plan', 'planning-pending-validation', 'planning-canvas-versions',
    ])
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[role="tabpanel"]').attributes('aria-labelledby')).toBe('career-workbench-tab-canvas')

    await wrapper.get('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(wrapper.get('[role="tabpanel"]').attributes('aria-labelledby')).toBe('career-workbench-tab-plan')
    expect(router.replace).toHaveBeenCalledWith({ query: { view: 'plan' } })

    expect(wrapper.findAll('.workbench-mobile-nav button span').map(label => label.text())).toEqual([
      '对话', '画布', '计划', '验证', '版本',
    ])
    expect(wrapper.findAll('.workbench-mobile-nav [data-jobproof-icon]').map(icon => icon.attributes('data-jobproof-icon'))).toEqual([
      'planning-ability-canvas', 'planning-learning-plan', 'planning-pending-validation', 'planning-canvas-versions',
    ])
  })

  it('keeps unsaved plan and validation input when switching cached views', async () => {
    const wrapper = mount(CareerPlanningWorkbench, {
      props: { session: makeSession() },
      global: { stubs: viewStubs },
    })
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()
    await wrapper.get('textarea[aria-label="计划草稿"]').setValue('保留本周复盘')

    await wrapper.findAll('[role="tab"]')[2].trigger('click')
    await flushPromises()
    await wrapper.get('textarea[aria-label="验证说明"]').setValue('保留验证说明')

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()
    expect((wrapper.get('textarea[aria-label="计划草稿"]').element as HTMLTextAreaElement).value).toBe('保留本周复盘')

    await wrapper.findAll('[role="tab"]')[2].trigger('click')
    await flushPromises()
    expect((wrapper.get('textarea[aria-label="验证说明"]').element as HTMLTextAreaElement).value).toBe('保留验证说明')
  })

  it('keeps the selected node in the validation URL when validation starts from the canvas', async () => {
    const wrapper = mount(CareerPlanningWorkbench, {
      props: { session: makeSession() },
      global: { stubs: viewStubs },
    })
    await flushPromises()

    await wrapper.get('.validate-node').trigger('click')
    await flushPromises()

    expect(router.replace).toHaveBeenLastCalledWith({ query: { view: 'validation', nodeId: 'skill-oop' } })
    expect(wrapper.find('.validation-stub').exists()).toBe(true)
  })

  it('stores a multi-node validation selection in the URL and restores it through the workspace prop', async () => {
    const wrapper = mount(CareerPlanningWorkbench, {
      props: { session: makeSession() },
      global: { stubs: viewStubs },
    })
    await flushPromises()

    await wrapper.get('.validate-batch').trigger('click')
    await flushPromises()

    expect(router.replace).toHaveBeenLastCalledWith({ query: {
      view: 'validation', nodeId: undefined, nodeIds: 'skill-oop,task-api',
    } })
    expect(wrapper.get('.validation-stub').attributes('data-node-ids')).toBe('skill-oop,task-api')
  })
})
