import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import { clearToasts } from '@/shared/ui/toast'
import { confirmState, settleConfirm } from '@/shared/ui/confirm'
import { makeSession } from '../../../../tests/careerPlanningFixtures'

const api = vi.hoisted(() => ({
  fetchCareerPlanningOverview: vi.fn(),
  fetchCareerPlanningSession: vi.fn(),
  startCareerPlanning: vi.fn(),
  updateCareerPlanningProfile: vi.fn(),
}))
const sonner = vi.hoisted(() => ({
  success: vi.fn(() => 'toast-id'),
  error: vi.fn(() => 'toast-id'),
  warning: vi.fn(() => 'toast-id'),
  info: vi.fn(() => 'toast-id'),
  loading: vi.fn(() => 'toast-id'),
  promise: vi.fn(),
  dismiss: vi.fn(),
}))
vi.mock('vue-sonner', () => ({ toast: sonner, Toaster: { template: '<div />' } }))
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
          UiDialog: { template: '<div />' },
          CareerPlanningWorkbench: {
            template: '<button class="emit-notice" @click="$emit(\'notice\', \'完整能力树已生成，并保存为新的画布版本。\')">触发提示</button>',
          },
        },
      },
    })
    await flushPromises()

    await wrapper.get('.emit-notice').trigger('click')
    await flushPromises()
    // The notice is rendered by the global toaster, never inline in the page layout.
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(sonner.success).toHaveBeenCalledWith(
      expect.stringContaining('完整能力树已生成'),
      expect.objectContaining({ duration: 4000 }),
    )
  })
})

describe('CareerPlanningPage welcome', () => {
  function mountWelcome() {
    return mount(CareerPlanningPage, {
      global: {
        stubs: {
          AppChrome: { template: '<div><slot /></div>' },
          UiDialog: { template: '<div />' },
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

  })

  it('offers to continue an exploration left unfinished instead of silently starting a second one', async () => {
    api.fetchCareerPlanningOverview.mockReset().mockResolvedValue({
      enabled: true,
      session: { ...makeSession(), id: 'session-left', phase: 'INTERVIEW' },
      quota: { periodKey: '2026-08', grantedUnits: 500, usedUnits: 0, heldUnits: 0, remainingUnits: 500 },
    })
    router.push.mockReset()
    const wrapper = mountWelcome()
    await flushPromises()

    const banner = wrapper.get('.cp-unfinished')
    expect(banner.text()).toContain('停在「AI 访谈」')
    await banner.get('button').trigger('click')
    expect(router.push).toHaveBeenCalledWith({ name: 'career-planning-session', params: { sessionId: 'session-left' } })
  })

  it('leaves AI consent unchecked and keeps the AI entry disabled until the user consents', async () => {
    const wrapper = mountWelcome()
    await flushPromises()

    const consent = wrapper.get<HTMLInputElement>('.cp-consent input')
    const start = wrapper.get<HTMLButtonElement>('.cp-entry--primary .cp-primary')
    expect(consent.element.checked).toBe(false)
    expect(start.element.disabled).toBe(true)
    expect(start.attributes('aria-describedby')).toBe('cp-consent-hint-discovery')
    expect(wrapper.get('#cp-consent-hint-discovery').text()).toContain('允许本次规划调用 AI')
    await start.trigger('click')
    await flushPromises()
    expect(api.startCareerPlanning).not.toHaveBeenCalled()

    await consent.setValue(true)
    expect(start.element.disabled).toBe(false)
    expect(wrapper.find('#cp-consent-hint-discovery').exists()).toBe(false)
  })

  it('keeps the AI discovery entry wired to AI_DISCOVERY once consent is given', async () => {
    const wrapper = mountWelcome()
    await flushPromises()

    await wrapper.get('.cp-consent input').setValue(true)
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
    expect(wrapper.get<HTMLButtonElement>('.cp-target-setup .cp-primary').element.disabled).toBe(true)
    expect(wrapper.find('#cp-consent-hint-target').exists()).toBe(true)

    await wrapper.get('.cp-consent input').setValue(true)
    await wrapper.get('.cp-target-setup .cp-primary').trigger('click')
    await flushPromises()

    expect(api.startCareerPlanning).toHaveBeenCalledWith('KNOWN_TARGET', true, 'job-java')
  })

  it('shows an error with retry instead of the welcome page when the first read fails', async () => {
    api.fetchCareerPlanningOverview.mockReset()
      .mockRejectedValueOnce(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-1'))
      .mockResolvedValueOnce({
        enabled: true, session: null,
        quota: { periodKey: '2026-08', grantedUnits: 500, usedUnits: 0, heldUnits: 0, remainingUnits: 500 },
      })
    const wrapper = mountWelcome()
    await flushPromises()

    expect(wrapper.find('.cp-welcome').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('开始职业方向判定')
    expect(wrapper.get('[role="alert"]').text()).toContain('服务暂时不可用')

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(api.fetchCareerPlanningOverview).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.cp-welcome').exists()).toBe(true)
  })
})

describe('CareerPlanningPage sessions', () => {
  const stubs = {
    AppChrome: { template: '<div><slot /></div>' },
    UiDialog: { template: '<div />' },
    UiSelect: { template: '<div />' },
    CareerSkillPicker: { template: '<div />' },
    CareerProfilePreview: { template: '<aside />' },
    CareerPlanningWorkbench: { template: '<div />' },
  }

  beforeEach(() => {
    route.params.sessionId = 'session-1'
    Object.defineProperty(globalThis, 'EventSource', { configurable: true, value: TestEventSource })
    api.fetchCareerPlanningOverview.mockReset().mockResolvedValue({
      enabled: true, session: null,
      quota: { periodKey: '2026-08', grantedUnits: 500, usedUnits: 0, heldUnits: 0, remainingUnits: 500 },
    })
    api.fetchCareerPlanningSession.mockReset()
  })

  it('never falls back to the welcome page when the requested plan cannot be read', async () => {
    api.fetchCareerPlanningSession.mockRejectedValue(new Error('network down'))
    const wrapper = mount(CareerPlanningPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('.cp-welcome').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.cp-mobile-nav').exists()).toBe(false)
  })

  it('offers only working mobile destinations while the profile is being built', async () => {
    api.fetchCareerPlanningSession.mockResolvedValue({ ...makeSession(), phase: 'PROFILE', recommendationSet: null })
    const wrapper = mount(CareerPlanningPage, { global: { stubs } })
    await flushPromises()

    const buttons = wrapper.findAll('.cp-mobile-nav button')
    expect(buttons.map(button => button.text())).toEqual(['画像', '方向'])
    expect(wrapper.text()).not.toContain('我的')
  })

  it('describes recommendations without internal model names or raw source ids', async () => {
    const session = makeSession()
    api.fetchCareerPlanningSession.mockResolvedValue({
      ...session,
      phase: 'RECOMMENDATIONS',
      recommendationSet: { ...session.recommendationSet!, model: 'internal-model-v3' },
    })
    const wrapper = mount(CareerPlanningPage, { global: { stubs } })
    await flushPromises()

    expect(wrapper.text()).toContain('AI 辅助生成')
    expect(wrapper.text()).not.toContain('internal-model-v3')
    expect(wrapper.text()).not.toContain('SYSTEM')
  })
})

describe('CareerPlanningPage confirmed profile', () => {
  const stubs = {
    AppChrome: { template: '<div><slot /></div>' },
    UiDialog: { template: '<div />' },
    UiSelect: { template: '<div />' },
    CareerSkillPicker: { template: '<div />' },
    CareerProfilePreview: { template: '<aside />' },
    CareerPlanningWorkbench: { template: '<div />' },
  }
  const basics = { identity: '应届生', education: '本科', experienceYears: '暂无正式经验', weeklyLearningHours: '5~10小时' }

  function recommendingSession() {
    const session = makeSession()
    return { ...session, phase: 'RECOMMENDATIONS', activeGoal: null, canvas: null,
      profile: { ...session.profile, basics } }
  }

  beforeEach(() => {
    route.params.sessionId = 'session-1'
    Object.defineProperty(globalThis, 'EventSource', { configurable: true, value: TestEventSource })
    api.fetchCareerPlanningOverview.mockReset().mockResolvedValue({
      enabled: true, session: null,
      quota: { periodKey: '2026-08', grantedUnits: 500, usedUnits: 0, heldUnits: 0, remainingUnits: 500 },
    })
    api.fetchCareerPlanningSession.mockReset().mockResolvedValue(recommendingSession())
    api.updateCareerPlanningProfile.mockReset()
  })
  afterEach(() => { if (confirmState.current) settleConfirm(false) })

  async function openProfile() {
    const wrapper = mount(CareerPlanningPage, { global: { stubs } })
    await flushPromises()
    await wrapper.findAll('.cp-mobile-nav button').find(button => button.text() === '画像')!.trigger('click')
    return wrapper
  }

  it('lets the user look at a confirmed profile and leave without touching its recommendations', async () => {
    const wrapper = await openProfile()
    const banner = wrapper.get('[data-testid="settled-profile"]')
    expect(banner.text()).toContain('现有 2 个职业方向')
    expect(banner.text()).toContain('消耗 1 次 AI 额度')
    expect(wrapper.get('.cp-autosave-state').text()).toBe('没有未保存的修改')
    expect(wrapper.text()).not.toContain('修改后自动保存')

    await wrapper.get('form.cp-profile-form').trigger('submit')
    await flushPromises()
    expect(api.updateCareerPlanningProfile).not.toHaveBeenCalled()
    expect(confirmState.current).toBeNull()

    await banner.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="settled-profile"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('职业方向推荐')
  })

  it('asks before an edit sets the recommendations aside and saves only when the user agrees', async () => {
    api.updateCareerPlanningProfile.mockResolvedValue({ ...recommendingSession(), phase: 'PROFILE', recommendationSet: null,
      profile: { ...recommendingSession().profile, status: 'DRAFT', version: 3, basics: { ...basics, currentRole: '实习生' } } })
    const wrapper = await openProfile()
    await wrapper.get('input[placeholder="例如：机械设计助理"]').setValue('实习生')
    expect(wrapper.get('.cp-autosave-state').text()).toBe('有未保存的修改')

    await wrapper.get('form.cp-profile-form').trigger('submit')
    await flushPromises()
    expect(confirmState.current?.title).toBe('现有职业方向会失效')
    expect(confirmState.current?.message).toContain('现有 2 个职业方向')
    settleConfirm(false)
    await flushPromises()
    expect(api.updateCareerPlanningProfile).not.toHaveBeenCalled()

    await wrapper.get('form.cp-profile-form').trigger('submit')
    await flushPromises()
    settleConfirm(true)
    await flushPromises()
    expect(api.updateCareerPlanningProfile).toHaveBeenCalledTimes(1)
    expect(api.updateCareerPlanningProfile.mock.calls[0]![1]).toMatchObject({ basics: { currentRole: '实习生' } })
  })
})
