import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { confirmPublication, publicationState, settlePublication } from '../publication'
import PublicationConfirmHost from './PublicationConfirmHost.vue'

const client = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('@/shared/api/client', () => client)
const preview = { versionNo: 7, versionLabel: 'v3.0', title: '功能更新', audience: 'AUTHENTICATED', recipientCount: 123, sendNotification: false, showWhatsNew: false }
const options = { global: { stubs: {
  UiDialog: { props: ['open','title'], template: '<section v-if="open"><h2>{{title}}</h2><slot/><slot name="footer"/></section>' },
  UiButton: { template: '<button><slot/></button>' },
} } }
afterEach(() => { settlePublication(null); client.api.mockReset() })

describe('publication confirmation', () => {
  it('shows the approved version and cancelling returns no publication choice', async () => {
    client.api.mockResolvedValue(preview)
    const wrapper = mount(PublicationConfirmHost, options)
    const choice = confirmPublication('release')
    await flushPromises()
    expect(wrapper.text()).toContain('v3.0 · 功能更新')
    expect(wrapper.text()).toContain('不发送站内通知')
    await wrapper.findAll('button')[0]!.trigger('click')
    expect(await choice).toBeNull()
    expect(publicationState.current).toBeNull()
    expect(client.api).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('defaults to publishing without notices and carries the preview version', async () => {
    client.api.mockResolvedValue(preview)
    const wrapper = mount(PublicationConfirmHost, options)
    const choice = confirmPublication('release')
    await flushPromises()
    await wrapper.findAll('button')[1]!.trigger('click')
    expect(await choice).toEqual({ expectedVersion: 7, sendNotification: false, notificationConfirmed: false })
    wrapper.unmount()
  })
  it('requires an explicit notification choice for scheduled publishing', async () => {
    client.api.mockResolvedValue(preview)
    const wrapper = mount(PublicationConfirmHost, options)
    const choice = confirmPublication('release', '2026-10-23T08:00:00Z')
    await flushPromises()
    expect(wrapper.text()).toContain('确认定时发布')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    expect(wrapper.text()).toContain('预计通知 123 个有效账号')
    await wrapper.findAll('button')[1]!.trigger('click')
    expect(await choice).toEqual({ expectedVersion: 7, sendNotification: true, notificationConfirmed: true })
    wrapper.unmount()
  })
})
