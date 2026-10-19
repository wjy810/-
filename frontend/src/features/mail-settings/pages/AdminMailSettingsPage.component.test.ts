import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import type { MailSettings } from '../types'

const api = vi.hoisted(() => ({ fetchMailSettings: vi.fn(), saveMailSettings: vi.fn(), clearMailSettings: vi.fn(), sendTestMail: vi.fn() }))
vi.mock('../services/mailSettingsApi', () => api)
const dialogs = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/shared/ui/confirm', () => dialogs)
const sonner = vi.hoisted(() => Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn(), loading: vi.fn(), dismiss: vi.fn() }))
vi.mock('vue-sonner', () => ({ toast: sonner }))
vi.mock('vue-router', () => ({ RouterLink: { name: 'RouterLink', template: '<a><slot /></a>' } }))
import AdminMailSettingsPage from './AdminMailSettingsPage.vue'

const empty: MailSettings = {
  source: 'NONE',
  notifications: false,
  canStoreSecrets: true,
  environment: { configured: false, notifications: false },
  delivery: { sent24h: 0, pending: 0, failed7d: 0, recent: [] },
}
const saved: MailSettings = {
  ...empty,
  source: 'ADMIN',
  notifications: true,
  admin: { host: 'smtp.qq.com', port: 465, security: 'SSL', username: 'notice@qq.com', fromAddress: 'notice@qq.com', fromName: 'JobProof AI',
    notifications: true, version: 1, updatedAt: '2026-10-19T04:00:00Z', usable: true },
}

const mountPage = () => mount(AdminMailSettingsPage, { global: { plugins: [createPinia()], stubs: { UiIllustration: true } } })

describe('mail settings', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    sonner.success.mockReset()
    sonner.error.mockReset()
  })

  it('starts from the QQ preset, keeps the sender in step with the account and saves', async () => {
    api.fetchMailSettings.mockResolvedValue(empty)
    api.saveMailSettings.mockResolvedValue(saved)
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('.mail-status').text()).toContain('还没有可用的邮件账号')
    const inputs = wrapper.findAll('#mail-settings-form input')
    expect((inputs[0]!.element as HTMLInputElement).value).toBe('smtp.qq.com')
    await inputs[2]!.setValue('notice@qq.com')
    await inputs[3]!.setValue('auth-code')
    expect((inputs[4]!.element as HTMLInputElement).value).toBe('notice@qq.com')
    await wrapper.get('#mail-settings-form').trigger('submit')
    await flushPromises()
    expect(api.saveMailSettings).toHaveBeenCalledWith({ host: 'smtp.qq.com', port: 465, security: 'SSL', username: 'notice@qq.com', password: 'auth-code',
      fromAddress: 'notice@qq.com', fromName: 'JobProof AI', notifications: true, expectedVersion: 0 })
    expect(wrapper.get('.mail-status').text()).toContain('正在使用后台配置')
    expect((wrapper.findAll('#mail-settings-form input')[3]!.element as HTMLInputElement).value).toBe('')
  })

  it('shows a refusal next to its field and a failed test in words', async () => {
    api.fetchMailSettings.mockResolvedValue(saved)
    api.saveMailSettings.mockRejectedValue(new ApiClientError({ category: 'USER_CORRECTABLE', reason: 'MAIL_FROM_MISMATCH', message: 'QQ 邮箱要求发件地址与登录账号一致' }, 400))
    api.sendTestMail.mockResolvedValue({ at: '2026-10-19T04:01:00Z', ok: false, message: '账号或授权码不正确：邮件服务器拒绝登录' })
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('#mail-settings-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('QQ 邮箱要求发件地址与登录账号一致')
    expect(sonner.error).not.toHaveBeenCalled()
    await wrapper.get('.mail-test input').setValue('me@example.com')
    await wrapper.get('.mail-test').trigger('submit')
    await flushPromises()
    expect(api.sendTestMail).toHaveBeenCalledWith('me@example.com')
    expect(wrapper.get('.mail-result').classes()).toContain('is-failed')
    expect(wrapper.get('.mail-result').text()).toContain('授权码不正确')
  })
})
