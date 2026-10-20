import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '@/stores/session'

const api = vi.hoisted(() => ({
  changePassword: vi.fn(), fetchSessions: vi.fn(), fetchVerificationCapabilities: vi.fn(),
  revokeOtherSessions: vi.fn(), revokeSession: vi.fn(), fetchMe: vi.fn(), logoutAccount: vi.fn(),
}))
vi.mock('../services/authApi', () => api)
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ replace: vi.fn(), push: vi.fn() }) }))
vi.mock('vue-sonner', () => ({ toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), dismiss: vi.fn() }) }))
import AccountPage from './AccountPage.vue'

const dialogStub = { props: ['open', 'channel', 'current'], template: '<div v-if="open" class="bind-stub">{{ channel }}|{{ current }}</div>' }

describe('AccountPage contacts', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    route.query = {}
    api.fetchSessions.mockResolvedValue([
      { id: 's1', current: true, clientLabel: 'Mac · Safari', createdAt: '2026-10-20T01:00:00Z', lastSeenAt: '2026-10-20T02:00:00Z', expiresAt: '' },
      { id: 's2', current: false, clientLabel: 'Windows · Chrome', createdAt: '2026-10-19T01:00:00Z', lastSeenAt: '2026-10-19T02:00:00Z', expiresAt: '' },
    ])
    api.fetchVerificationCapabilities.mockResolvedValue({ emailEnabled: true, smsEnabled: true, codeLength: 6, codeTtlMinutes: 10, resendCooldownSeconds: 60, registrationEnabled: true, termsVersion: '', privacyVersion: '' })
    useSessionStore().remember({ id: 'a1', email: null, phoneMasked: '138****0000', displayIdentifier: '138****0000', status: 'ACTIVE', role: 'SEEKER', phoneVerified: true, emailVerified: false })
  })

  it('shows what is bound, offers to bind the email, and names the signed-in devices', async () => {
    const wrapper = mount(AccountPage, { global: { stubs: { ContactBindDialog: dialogStub, UiIllustration: true } } })
    await flushPromises()
    expect(wrapper.get('[data-testid="contact-email"]').text()).toContain('未绑定')
    expect(wrapper.get('[data-testid="contact-sms"]').text()).toContain('138****0000')
    expect(wrapper.get('[data-testid="contact-sms"]').text()).toContain('已验证')
    expect(wrapper.text()).not.toContain('暂不支持绑定或更换联系方式')
    expect(wrapper.text()).toContain('Windows · Chrome')
    await wrapper.get('[data-testid="bind-email"]').trigger('click')
    expect(wrapper.get('.bind-stub').text()).toBe('EMAIL|')
  })

  it('opens the email binding straight away when sent from the notification settings', async () => {
    route.query = { bind: 'email' }
    const wrapper = mount(AccountPage, { global: { stubs: { ContactBindDialog: dialogStub, UiIllustration: true } } })
    await flushPromises()
    expect(wrapper.get('.bind-stub').text()).toBe('EMAIL|')
  })

  it('does not claim a single device when the device list failed to load', async () => {
    api.fetchSessions.mockRejectedValue(new Error('network'))
    const wrapper = mount(AccountPage, { global: { stubs: { ContactBindDialog: dialogStub, UiIllustration: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('读取失败')
    expect(wrapper.text()).not.toContain('仅当前设备')
  })
})
