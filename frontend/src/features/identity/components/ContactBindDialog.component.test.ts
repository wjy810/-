import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  requestContactVerification: vi.fn(), confirmContactVerification: vi.fn(), bindContact: vi.fn(), fetchDevMailbox: vi.fn(),
}))
vi.mock('../services/authApi', () => api)
import ContactBindDialog from './ContactBindDialog.vue'

const stubs = { UiDialog: { props: ['open', 'title', 'description'], template: '<div v-if="open"><h2>{{ title }}</h2><p class="desc">{{ description }}</p><slot /><slot name="footer" /></div>' } }

describe('ContactBindDialog', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    api.requestContactVerification.mockResolvedValue({ challengeId: 'c1', channel: 'EMAIL', destinationMasked: 'li***@example.com', expiresAt: '', resendAfterSeconds: 60 })
    api.confirmContactVerification.mockResolvedValue({ verificationToken: 'token-1', channel: 'EMAIL', purpose: 'CHANGE_CONTACT', destinationMasked: '', verificationTokenExpiresAt: '' })
    api.bindContact.mockResolvedValue({ id: 'a1', email: 'lin@example.com', displayIdentifier: 'lin@example.com', status: 'ACTIVE', role: 'SEEKER', emailVerified: true })
  })

  it('verifies the new address, asks for the password, and binds it', async () => {
    const wrapper = mount(ContactBindDialog, { props: { open: true, channel: 'EMAIL', current: null }, global: { stubs } })
    expect(wrapper.get('h2').text()).toBe('绑定邮箱')
    const [address, code, password] = wrapper.findAll('input')
    await address!.setValue('Lin@Example.com')
    await wrapper.get('[data-testid="send-code"]').trigger('click')
    await flushPromises()
    expect(api.requestContactVerification).toHaveBeenCalledWith('EMAIL', 'lin@example.com', 'CHANGE_CONTACT')
    expect(wrapper.text()).toContain('验证码已发送至 li***@example.com')

    await code!.setValue('123456')
    await wrapper.get('#contact-bind-form').trigger('submit')
    expect(api.bindContact).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入当前密码')

    await password!.setValue('Passw0rd!')
    await wrapper.get('#contact-bind-form').trigger('submit')
    await flushPromises()
    expect(api.confirmContactVerification).toHaveBeenCalledWith('c1', 'lin@example.com', '123456')
    expect(api.bindContact).toHaveBeenCalledWith({ channel: 'EMAIL', destination: 'lin@example.com', verificationToken: 'token-1', currentPassword: 'Passw0rd!' })
    expect(wrapper.emitted('bound')?.[0]?.[0]).toMatchObject({ email: 'lin@example.com' })
  })

  it('says a change warns the old address and shows why a bind failed', async () => {
    api.bindContact.mockRejectedValue(Object.assign(new Error('这个邮箱已被其他账号使用'), { name: 'ApiClientError' }))
    const wrapper = mount(ContactBindDialog, { props: { open: true, channel: 'EMAIL', current: 'old@example.com' }, global: { stubs } })
    expect(wrapper.get('h2').text()).toBe('更换邮箱')
    expect(wrapper.get('.desc').text()).toContain('原邮箱会收到一条安全提醒')
    const [address, code, password] = wrapper.findAll('input')
    await address!.setValue('taken@example.com')
    await wrapper.get('[data-testid="send-code"]').trigger('click')
    await flushPromises()
    await code!.setValue('123456')
    await password!.setValue('Passw0rd!')
    await wrapper.get('#contact-bind-form').trigger('submit')
    await flushPromises()
    expect(wrapper.emitted('bound')).toBeUndefined()
    expect(wrapper.text()).toContain('这个邮箱已被其他账号使用')
  })
})
