import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { NotificationPreferences } from '../types'

const api = vi.hoisted(() => ({ fetchNotificationPreferences: vi.fn(), saveNotificationPreferences: vi.fn() }))
vi.mock('../services/notificationApi', () => api)
vi.mock('vue-router', () => ({ RouterLink: { name: 'RouterLink', props: ['to'], template: '<a :data-to="to"><slot /></a>' } }))
const sonner = vi.hoisted(() => Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn() }))
vi.mock('vue-sonner', () => ({ toast: sonner }))
import NotificationPreferencesPage from './NotificationPreferencesPage.vue'
import UiSwitch from '@/shared/ui/UiSwitch.vue'

function prefs(patch: Partial<NotificationPreferences> = {}): NotificationPreferences {
  return {
    items: [
      { type: 'APPLICATION_ACTIVITY', inApp: true, email: true },
      { type: 'REMINDER', inApp: true, email: true },
      { type: 'MEMBERSHIP', inApp: true, email: true },
      { type: 'PRODUCT_UPDATE', inApp: true, email: false },
    ],
    emailAvailable: true,
    emailAddress: 'se***@example.com',
    ...patch,
  }
}

describe('notification preferences', () => {
  beforeEach(() => {
    api.fetchNotificationPreferences.mockReset()
    api.saveNotificationPreferences.mockReset()
    sonner.success.mockReset()
    sonner.error.mockReset()
  })

  it('lists each kind with its two channels and the receiving address', async () => {
    api.fetchNotificationPreferences.mockResolvedValue(prefs())
    const wrapper = mount(NotificationPreferencesPage)
    await flushPromises()
    expect(wrapper.findAll('.prefs__row').map(row => row.find('strong').text())).toEqual(['简历回执', '日程提醒', '会员', '产品更新'])
    expect(wrapper.get('.mailbox').text()).toContain('se***@example.com')
    const switches = wrapper.findAllComponents(UiSwitch)
    expect(switches).toHaveLength(8)
    expect(switches[7]!.props('modelValue')).toBe(false)
    expect(switches.every(item => !item.props('disabled'))).toBe(true)
  })

  it('saves one switch at a time and puts it back when the server refuses', async () => {
    api.fetchNotificationPreferences.mockResolvedValue(prefs())
    api.saveNotificationPreferences.mockRejectedValueOnce(new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503))
    const wrapper = mount(NotificationPreferencesPage)
    await flushPromises()
    wrapper.findAllComponents(UiSwitch)[2]!.vm.$emit('update:modelValue', false)
    await wrapper.vm.$nextTick()
    expect(wrapper.findAllComponents(UiSwitch)[2]!.props('modelValue')).toBe(false)
    await flushPromises()
    expect(api.saveNotificationPreferences).toHaveBeenCalledWith([{ type: 'REMINDER', inApp: false, email: true }])
    expect(wrapper.findAllComponents(UiSwitch)[2]!.props('modelValue')).toBe(true)
    expect(sonner.error).toHaveBeenCalled()

    api.saveNotificationPreferences.mockResolvedValueOnce(prefs({ items: prefs().items.map(item => item.type === 'PRODUCT_UPDATE' ? { ...item, email: true } : item) }))
    wrapper.findAllComponents(UiSwitch)[7]!.vm.$emit('update:modelValue', true)
    await flushPromises()
    expect(wrapper.findAllComponents(UiSwitch)[7]!.props('modelValue')).toBe(true)
    expect(sonner.success).toHaveBeenCalled()
  })

  it('keeps email switches off until the account has a verified address', async () => {
    api.fetchNotificationPreferences.mockResolvedValue(prefs({ emailAddress: null }))
    const wrapper = mount(NotificationPreferencesPage)
    await flushPromises()
    expect(wrapper.get('.mailbox').text()).toContain('还没有可接收提醒的邮箱')
    expect(wrapper.get('.mailbox a').attributes('data-to')).toBe('/account')
    const email = wrapper.findAllComponents(UiSwitch).filter((_, index) => index % 2 === 1)
    expect(email.every(item => item.props('disabled') && item.props('modelValue') === false)).toBe(true)
  })
})
