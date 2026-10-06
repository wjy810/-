import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession, rememberAccount } from '@/features/identity/session'

const api = vi.hoisted(() => ({
  fetchUnreadCount: vi.fn(),
  fetchWhatsNew: vi.fn(),
}))
const route = vi.hoisted(() => ({ path: '/career-library', fullPath: '/career-library' }))
const router = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('@/features/notification/services/notificationApi', async () => ({
  ...(await vi.importActual<typeof import('@/features/notification/services/notificationApi')>('@/features/notification/services/notificationApi')),
  fetchUnreadCount: api.fetchUnreadCount,
}))
vi.mock('@/features/updates/services/updatesApi', async () => ({
  ...(await vi.importActual<typeof import('@/features/updates/services/updatesApi')>('@/features/updates/services/updatesApi')),
  fetchWhatsNew: api.fetchWhatsNew,
}))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => router,
}))

import AppChrome from './AppChrome.vue'

describe('AppChrome main navigation icons', () => {
  beforeEach(() => {
    route.path = '/career-library'
    route.fullPath = '/career-library'
    api.fetchUnreadCount.mockResolvedValue(0)
    api.fetchWhatsNew.mockResolvedValue(null)
    rememberAccount({
      id: 'seeker-1',
      email: 'seeker@jobproof.local',
      displayIdentifier: 'seeker@jobproof.local',
      status: 'ACTIVE',
      role: 'SEEKER',
    })
  })

  afterEach(() => clearSession())

  it('uses the seven published category icons and keeps updates on the generic set', async () => {
    const wrapper = mount(AppChrome, {
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
          UpdateNotificationDrawer: { template: '<div />' },
          WhatsNewModal: { template: '<div />' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.findAll('.nav__item [data-jobproof-icon]').map(icon => icon.attributes('data-jobproof-icon'))).toEqual([
      'nav-career-library',
      'nav-resume-workbench',
      'nav-job-matching',
      'nav-career-planning',
      'nav-mock-interview',
      'nav-template-center',
      'nav-notification-center',
    ])
    expect(wrapper.findAll('.nav__item')).toHaveLength(8)
    expect(wrapper.findAll('.nav__item')[7].find('[data-jobproof-icon]').exists()).toBe(false)
  })

  it('opens and closes the mobile navigation from every signed-in route', async () => {
    route.path = '/resumes'
    route.fullPath = '/resumes'
    const wrapper = mount(AppChrome, {
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a><slot /></a>' },
          UpdateNotificationDrawer: { template: '<div />' },
          WhatsNewModal: { template: '<div />' },
        },
      },
    })
    await flushPromises()

    const menu = wrapper.get('[data-testid="mobile-nav-toggle"]')
    expect(menu.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.sidebar-scrim').exists()).toBe(false)

    await menu.trigger('click')
    expect(menu.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('.sidebar').classes()).toContain('is-mobile-open')
    expect(wrapper.find('.sidebar-scrim').exists()).toBe(true)

    await wrapper.get('.sidebar-scrim').trigger('click')
    expect(wrapper.get('.sidebar').classes()).not.toContain('is-mobile-open')
    expect(wrapper.find('.sidebar-scrim').exists()).toBe(false)
  })
})
