import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ listPersonalChannels: vi.fn(), getChannelPreference: vi.fn(), saveChannelPreference: vi.fn(), createPersonalChannel: vi.fn(), updatePersonalChannel: vi.fn(), testPersonalChannel: vi.fn(), activatePersonalChannel: vi.fn(), disablePersonalChannel: vi.fn(), archivePersonalChannel: vi.fn() }))
const confirmation = vi.hoisted(() => vi.fn())
vi.mock('../services/aiApi', () => api)
vi.mock('@/shared/ui/confirm', () => ({ confirm: confirmation }))
import AiPersonalChannelsPage from './AiPersonalChannelsPage.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
const capability = { id: 'test-1', channelId: 'one', normalizedBaseUrl: 'https://provider.example/v1', checkedAt: '2026-10-21T01:00:00Z', configVersion: 1, status: 'PASSED', testedModel: 'working-model', models: ['working-model', 'another-model'], authenticated: true, modelDiscovery: true, plainResponse: true, streamingResponse: true, structuredResponse: true, detail: '全部能力测试通过' }
const channel = { id: 'one', name: '我的通道', providerCode: '', baseUrl: 'https://provider.example/v1', apiKeyMasked: '****abcd', protocol: 'OPENAI_CHAT', status: 'ACTIVE', healthStatus: 'HEALTHY', selectedModel: 'working-model', versionNo: 3, configVersion: 1, capability }
const selection = { source: 'PERSONAL', personalChannelId: 'one', versionNo: 2, subscriptionActive: true, subscriptionEnd: '2099-01-01T00:00:00Z' }
const wrappers: ReturnType<typeof mount>[] = []
const mountPage = () => {
  const wrapper = mount(AiPersonalChannelsPage, { attachTo: document.body, global: { plugins: [createPinia()], stubs: { UiIllustration: true, RouterLink: true } } })
  wrappers.push(wrapper)
  return wrapper
}
const button = (wrapper: ReturnType<typeof mount>, text: string) => wrapper.findAll('button').find(item => item.text() === text)!

describe('personal AI channel management', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
    Object.values(api).forEach(mock => mock.mockReset())
    confirmation.mockReset().mockResolvedValue(false)
    api.listPersonalChannels.mockResolvedValue([channel])
    api.getChannelPreference.mockResolvedValue(selection)
  })
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); document.body.innerHTML = '' })

  it('retains expired configuration and selection while disabling creation and use', async () => {
    api.getChannelPreference.mockResolvedValue({ ...selection, subscriptionActive: false })
    const wrapper = mountPage(); await flushPromises()
    expect(wrapper.text()).toContain('个人通道订阅未开通或已到期')
    expect(wrapper.text()).toContain('我的通道')
    expect(wrapper.text()).toContain('当前选择不可用')
    expect(wrapper.find('#channel-name').exists()).toBe(false)
    expect(button(wrapper, '检测可用模型').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '编辑配置').attributes('disabled')).toBeUndefined()
    expect(api.saveChannelPreference).not.toHaveBeenCalled()
    expect(button(wrapper, '切换平台通道')).toBeDefined()
  })

  it('requires explicit platform confirmation and keeps the selection when cancelled', async () => {
    const wrapper = mountPage(); await flushPromises()
    await button(wrapper, '切换平台通道').trigger('click'); await flushPromises()
    expect(confirmation).toHaveBeenCalledWith(expect.objectContaining({ title: '切换为平台 AI 通道' }))
    expect(api.saveChannelPreference).not.toHaveBeenCalled()
    confirmation.mockResolvedValue(true)
    api.saveChannelPreference.mockResolvedValue({ ...selection, source: 'PLATFORM', personalChannelId: null, versionNo: 3 })
    await button(wrapper, '切换平台通道').trigger('click'); await flushPromises()
    expect(api.saveChannelPreference).toHaveBeenCalledWith(selection, null, true)
    expect(wrapper.text()).toContain('当前使用：平台 AI 通道')
  })

  it('reuses the searchable shared model selector and tests pending key configuration before activation', async () => {
    const pending = { baseUrl: 'https://replacement.example', protocol: 'OPENAI_CHAT', apiKeyMasked: '****next', selectedModel: 'working-model', configVersion: 2 }
    api.listPersonalChannels.mockResolvedValueOnce([{ ...channel, configVersion: 2, pending }])
      .mockResolvedValue([{ ...channel, versionNo: 5, configVersion: 2, pending, capability: { ...capability, configVersion: 2 } }])
    api.testPersonalChannel.mockResolvedValue({ ...capability, configVersion: 2 })
    const wrapper = mountPage(); await flushPromises()
    const model = wrapper.findAllComponents(UiSelect).find(select => select.props('ariaLabel') === '选择模型')!
    expect(model.props('searchable')).toBe(true)
    expect(wrapper.text()).toContain('待生效配置')
    expect(wrapper.text()).toContain('当前生效模型：working-model')
    await button(wrapper, '启用所选模型').trigger('click'); await flushPromises()
    expect(api.testPersonalChannel).toHaveBeenCalledWith('one', 'working-model')
    expect(api.activatePersonalChannel).toHaveBeenCalledWith(expect.objectContaining({ versionNo: 5, configVersion: 2 }), 'working-model')
  })

  it('keeps API key blank on editing and uses the personal endpoint', async () => {
    const wrapper = mountPage(); await flushPromises()
    await button(wrapper, '编辑配置').trigger('click'); await flushPromises()
    const key = document.querySelector<HTMLInputElement>('#edit-channel-api-key')!
    expect(key.value).toBe('')
    expect(key.required).toBe(false)
    expect(document.querySelector('#edit-channel-weight')).toBeNull()
    document.querySelector('#channel-edit-form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(api.updatePersonalChannel).toHaveBeenCalledWith(expect.objectContaining({ id: 'one' }), expect.objectContaining({ apiKey: '', baseUrl: channel.baseUrl }))
  })

  it('counts only unarchived channels towards the visible five-channel limit', async () => {
    api.listPersonalChannels.mockResolvedValue(Array.from({ length: 6 }, (_, index) => ({ ...channel, id: String(index), status: index === 5 ? 'ARCHIVED' : 'ACTIVE' })))
    const wrapper = mountPage(); await flushPromises()
    expect(wrapper.text()).toContain('新增个人通道 · 5/5')
    expect(wrapper.find('#channel-name').exists()).toBe(false)
    expect(wrapper.text()).toContain('已归档')
  })
})
