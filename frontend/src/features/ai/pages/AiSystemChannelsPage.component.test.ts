import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ listSystemChannels: vi.fn(), createSystemChannel: vi.fn(), testSystemChannel: vi.fn(), activateSystemChannel: vi.fn(), disableSystemChannel: vi.fn(), updateSystemChannel: vi.fn() }))
vi.mock('../services/aiApi', () => api)
import AiSystemChannelsPage from './AiSystemChannelsPage.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'

const capability = { id: 'test-1', channelId: 'channel-1', normalizedBaseUrl: 'https://provider.example/v1', checkedAt: '2026-10-21T01:00:00Z', status: 'PASSED', testedModel: 'working-model', models: ['embedding-only', 'working-model', 'gpt-5.6-luna'], authenticated: true, modelDiscovery: true, plainResponse: true, streamingResponse: true, structuredResponse: true, detail: '全部能力测试通过' }
const channel = { id: 'channel-1', name: '直连通道', providerCode: '', baseUrl: 'https://provider.example/v1', apiKeyMasked: '****key', protocol: 'OPENAI_CHAT', status: 'DRAFT', healthStatus: 'UNKNOWN', versionNo: 0 }
const wrappers: ReturnType<typeof mount>[] = []
const mountPage = () => {
  const wrapper = mount(AiSystemChannelsPage, { attachTo: document.body, global: { plugins: [createPinia()], stubs: { UiIllustration: true, RouterLink: true } } })
  wrappers.push(wrapper)
  return wrapper
}
const modelSelect = (wrapper: ReturnType<typeof mount>) => wrapper.findAllComponents(UiSelect).find(select => select.props('ariaLabel') === '选择模型')!
const button = (wrapper: ReturnType<typeof mount>, text: string) => wrapper.findAll('button').find(item => item.text() === text)!

describe('direct system AI channels', () => {
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); document.body.innerHTML = '' })
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
    Object.values(api).forEach(mock => mock.mockReset())
    api.listSystemChannels.mockResolvedValue([channel])
  })

  it('creates a channel with an empty optional provider code', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('#channel-provider').attributes('required')).toBeUndefined()
    expect((wrapper.get('#channel-provider').element as HTMLInputElement).value).toBe('')
    await wrapper.get('#channel-name').setValue('新通道')
    await wrapper.get('#channel-base-url').setValue('https://provider.example')
    await wrapper.get('#channel-api-key').setValue('test-key')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.createSystemChannel).toHaveBeenCalledWith({ name: '新通道', providerCode: '', baseUrl: 'https://provider.example', apiKey: 'test-key', protocol: 'OPENAI_CHAT' })
  })

  it('automatically tests with no platform mapping and shows the selected model', async () => {
    api.testSystemChannel.mockResolvedValue(capability)
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('[aria-label="平台模型"]').exists()).toBe(false)
    await button(wrapper, '检测可用模型').trigger('click')
    await flushPromises()
    expect(api.testSystemChannel).toHaveBeenCalledWith('channel-1', '')
    expect(wrapper.text()).toContain('working-model')
  })

  it('retains the selected model and test results after reopening the page', async () => {
    api.listSystemChannels.mockResolvedValue([{ ...channel, selectedModel: 'working-model', capability }])
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('working-model')
    expect(wrapper.text()).toContain('全部能力测试通过')
    expect(wrapper.text()).not.toContain('映射 qwen')
  })

  it('defaults the themed dropdown to the applied model and exposes all discovered models', async () => {
    api.listSystemChannels.mockResolvedValue([{ ...channel, status: 'ACTIVE', selectedModel: 'gpt-5.6-luna', capability }])
    const wrapper = mountPage()
    await flushPromises()
    const select = modelSelect(wrapper)
    expect(select, 'model dropdown must use the shared themed select').toBeDefined()
    expect(select.props('modelValue')).toBe('gpt-5.6-luna')
    await select.get('button').trigger('click')
    await flushPromises()
    const options = Array.from(document.querySelectorAll('[role="option"]')).map(option => option.textContent)
    expect(options.join(' ')).toContain('embedding-only')
    expect(options.join(' ')).toContain('working-model')
    expect(options.join(' ')).toContain('gpt-5.6-luna')
    expect(wrapper.text()).toContain('当前生效模型：gpt-5.6-luna')
    const search = document.querySelector<HTMLInputElement>('[aria-label="搜索模型"]')!
    search.value = 'working'
    search.dispatchEvent(new Event('input', { bubbles: true }))
    await flushPromises()
    expect(Array.from(document.querySelectorAll('[role="option"]')).map(option => option.textContent)).toEqual(['working-model'])
    await select.get('button').trigger('click')
    await flushPromises()
    await select.get('button').trigger('click')
    await flushPromises()
    expect(document.querySelectorAll('[role="option"]').length).toBe(3)
  })

  it('keeps the applied model while selecting and testing, then applies only on enable', async () => {
    const active = { ...channel, status: 'ACTIVE', selectedModel: 'gpt-5.6-luna', capability }
    api.listSystemChannels.mockResolvedValue([active])
    api.testSystemChannel.mockResolvedValue(capability)
    const wrapper = mountPage()
    await flushPromises()
    const select = modelSelect(wrapper)
    expect(select).toBeDefined()
    select.vm.$emit('update:modelValue', 'working-model')
    await flushPromises()
    expect(api.activateSystemChannel).not.toHaveBeenCalled()
    await button(wrapper, '检测可用模型').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('当前生效模型：gpt-5.6-luna')
    expect(wrapper.text()).toContain('待启用模型：working-model')
    expect(api.activateSystemChannel).not.toHaveBeenCalled()
    api.activateSystemChannel.mockResolvedValue({ ...active, selectedModel: 'working-model' })
    api.listSystemChannels.mockResolvedValue([{ ...active, selectedModel: 'working-model' }])
    await button(wrapper, '启用所选模型').trigger('click')
    await flushPromises()
    expect(api.activateSystemChannel).toHaveBeenCalledWith(expect.objectContaining({ id: 'channel-1' }), 'working-model')
    expect(wrapper.text()).toContain('当前生效模型：working-model')
  })

  it('validates an untested selection on enable and leaves the active model intact on failure', async () => {
    api.listSystemChannels.mockResolvedValue([{ ...channel, status: 'ACTIVE', selectedModel: 'gpt-5.6-luna', capability }])
    api.testSystemChannel.mockResolvedValue({ ...capability, testedModel: 'embedding-only', status: 'FAILED', plainResponse: false, detail: '所选模型不可用' })
    const wrapper = mountPage()
    await flushPromises()
    const select = modelSelect(wrapper)
    expect(select).toBeDefined()
    select.vm.$emit('update:modelValue', 'embedding-only')
    await flushPromises()
    await button(wrapper, '启用所选模型').trigger('click')
    await flushPromises()
    expect(api.testSystemChannel).toHaveBeenCalledWith('channel-1', 'embedding-only')
    expect(api.activateSystemChannel).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前生效模型：gpt-5.6-luna')
  })

  it('enables a newly validated model with the refreshed channel version', async () => {
    const active = { ...channel, status: 'ACTIVE', selectedModel: 'gpt-5.6-luna', capability: { ...capability, testedModel: 'gpt-5.6-luna' } }
    api.listSystemChannels.mockResolvedValueOnce([active]).mockResolvedValueOnce([{ ...active, versionNo: 8, capability }])
      .mockResolvedValue([{ ...active, versionNo: 9, selectedModel: 'working-model', capability }])
    api.testSystemChannel.mockResolvedValue(capability)
    api.activateSystemChannel.mockResolvedValue({ ...active, selectedModel: 'working-model' })
    const wrapper = mountPage()
    await flushPromises()
    modelSelect(wrapper).vm.$emit('update:modelValue', 'working-model')
    await flushPromises()
    await button(wrapper, '启用所选模型').trigger('click')
    await flushPromises()
    expect(api.testSystemChannel).toHaveBeenCalledWith('channel-1', 'working-model')
    expect(api.activateSystemChannel).toHaveBeenCalledWith(expect.objectContaining({ versionNo: 8 }), 'working-model')
    expect(wrapper.text()).toContain('当前生效模型：working-model')
  })

  it('edits routing weight and connection as a draft while leaving the key blank', async () => {
    const active = { ...channel, status: 'ACTIVE', selectedModel: 'working-model', configVersion: 1, routingWeight: 100, capability: { ...capability, configVersion: 1 } }
    api.listSystemChannels.mockResolvedValueOnce([active]).mockResolvedValue([{ ...active, name: '修改名称', routingWeight: 400, versionNo: 1, configVersion: 2,
      pending: { baseUrl: 'https://new.example', protocol: 'OPENAI_CHAT', apiKeyMasked: active.apiKeyMasked, selectedModel: 'working-model', configVersion: 2 } }])
    const wrapper = mountPage(); await flushPromises()
    await button(wrapper, '编辑配置').trigger('click'); await flushPromises()
    const setInput = (id: string, value: string) => {
      const input = document.querySelector<HTMLInputElement>(id)!
      input.value = value; input.dispatchEvent(new Event('input', { bubbles: true }))
    }
    expect(document.querySelector<HTMLInputElement>('#edit-channel-api-key')!.value).toBe('')
    setInput('#edit-channel-name', '修改名称'); setInput('#edit-channel-base-url', 'https://new.example'); setInput('#edit-channel-weight', '400')
    document.querySelector('#channel-edit-form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(api.updateSystemChannel).toHaveBeenCalledWith(expect.objectContaining({ id: 'channel-1', versionNo: 0 }), expect.objectContaining({ name: '修改名称', routingWeight: 400, apiKey: '', baseUrl: 'https://new.example' }))
    expect(wrapper.text()).toContain('当前生效模型：working-model')
    expect(wrapper.text()).toContain('待生效配置')
    expect(wrapper.text()).toContain('https://provider.example/v1')
    expect(wrapper.text()).toContain('https://new.example')
    expect(api.activateSystemChannel).not.toHaveBeenCalled()
  })
})
