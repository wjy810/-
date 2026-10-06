import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AppToastViewport from '@/shared/ui/AppToastViewport.vue'
import { clearToasts } from '@/shared/ui/toast'
import { makeSession } from '../../../../tests/careerPlanningFixtures'

const api = vi.hoisted(() => ({
  fetchCareerPlanningOverview: vi.fn(),
  fetchCareerPlanningSession: vi.fn(),
  startCareerPlanning: vi.fn(),
}))
const route = vi.hoisted(() => ({ params: { sessionId: 'session-1' }, query: {} }))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))

vi.mock('../services/careerPlanningApi', async () => ({
  ...(await vi.importActual<typeof import('../services/careerPlanningApi')>('../services/careerPlanningApi')),
  ...api,
}))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

class TestEventSource {
  onopen: ((event: Event) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  constructor(_url: string) {}
  addEventListener(): void {}
  close(): void {}
}

import CareerPlanningPage from './CareerPlanningPage.vue'

describe('CareerPlanningPage notifications', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    route.params.sessionId = 'session-1'
    Object.defineProperty(globalThis, 'EventSource', { configurable: true, value: TestEventSource })
    const session = makeSession()
    api.fetchCareerPlanningOverview.mockResolvedValue({
      enabled: true,
      session,
      quota: { periodKey: '2026-08', grantedUnits: 500, usedUnits: 1, heldUnits: 0, remainingUnits: 499 },
    })
    api.fetchCareerPlanningSession.mockResolvedValue(session)
  })

  afterEach(() => {
    clearToasts()
    vi.useRealTimers()
  })

  it('shows workbench success as a non-layout toast and dismisses it automatically', async () => {
    const wrapper = mount(CareerPlanningPage, {
      global: {
        stubs: {
          AppChrome: { template: '<div><slot /></div>' },
          AppModal: { template: '<div />' },
          CareerPlanningWorkbench: {
            template: '<button class="emit-notice" @click="$emit(\'notice\', \'完整能力树已生成，并保存为新的画布版本。\')">触发提示</button>',
          },
        },
      },
    })
    await flushPromises()

    const viewport = mount(AppToastViewport)
    await wrapper.get('.emit-notice').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(viewport.get('[role="status"]').text()).toContain('完整能力树已生成')

    await vi.advanceTimersByTimeAsync(3999)
    expect(viewport.find('[role="status"]').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(1)
    expect(viewport.find('[role="status"]').exists()).toBe(false)
    viewport.unmount()
  })
})

describe('CareerPlanningPage welcome', () => {
  function mountWelcome() {
    return mount(CareerPlanningPage, {
      global: {
        stubs: {
          AppChrome: { template: '<div><slot /></div>' },
          AppModal: { template: '<div />' },
          JobTaxonomyPicker: {
            template: `<button class="choose-job" type="button" @click="$emit('select', { job: { id: 'job-java', displayName: 'Java 后端工程师' } })">选择岗位</button>`,
          },
          CareerPlanningWorkbench: { template: '<div />' },
        },
      },
    })
  }

  beforeEach(() => {
    route.params.sessionId = ''
    router.replace.mockReset()
    api.fetchCareerPlanningOverview.mockReset().mockResolvedValue({
      enabled: true,
      session: null,
      quota: { periodKey: '2026-08', grantedUnits: 500, usedUnits: 0, heldUnits: 0, remainingUnits: 500 },
    })
    api.fetchCareerPlanningSession.mockReset()
    api.startCareerPlanning.mockReset().mockResolvedValue(makeSession())
  })

  afterEach(() => {
    route.params.sessionId = 'session-1'
  })

  it('renders the two focused entry paths without the retired safety panel', async () => {
    const wrapper = mountWelcome()
    await flushPromises()

    expect(wrapper.findAll('.cp-entry')).toHaveLength(2)
    expect(wrapper.findAll('.cp-discovery-steps li')).toHaveLength(3)
    expect(wrapper.findAll('.cp-outcome-list > div')).toHaveLength(4)
    expect(wrapper.text()).not.toContain('安全边界')
    expect(wrapper.text()).not.toContain('不承诺录用')

    const consent = wrapper.get<HTMLInputElement>('.cp-consent input')
    expect(consent.element.checked).toBe(true)
    await consent.setValue(false)
    expect(consent.element.checked).toBe(false)
  })

  it('keeps the AI discovery entry wired to AI_DISCOVERY', async () => {
    const wrapper = mountWelcome()
    await flushPromises()

    await wrapper.get('.cp-entry--primary .cp-primary').trigger('click')
    await flushPromises()

    expect(api.startCareerPlanning).toHaveBeenCalledWith('AI_DISCOVERY', true, undefined)
  })

  it('keeps the known target picker stable and submits the selected taxonomy job', async () => {
    const wrapper = mountWelcome()
    await flushPromises()

    await wrapper.get('.cp-known-target-start').trigger('click')
    await flushPromises()
    expect(wrapper.find('.cp-target-setup').exists()).toBe(true)

    await wrapper.get('.choose-job').trigger('click')
    await wrapper.get('.cp-target-setup .cp-primary').trigger('click')
    await flushPromises()

    expect(api.startCareerPlanning).toHaveBeenCalledWith('KNOWN_TARGET', true, 'job-java')
  })
})
