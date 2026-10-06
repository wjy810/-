import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AppToastViewport from './AppToastViewport.vue'
import { clearToasts, toast, toastItems, useToastFeedback } from './toast'

describe('AppToastViewport', () => {
  afterEach(() => {
    clearToasts()
    vi.useRealTimers()
  })

  it('renders page feedback in a fixed global viewport with semantic roles', async () => {
    const wrapper = mount(AppToastViewport)
    toast.error('保存失败')
    toast.success('保存成功')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.app-toast-viewport').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').text()).toContain('保存失败')
    expect(wrapper.find('[role="status"]').text()).toContain('保存成功')
  })

  it('keeps errors until dismissed and automatically closes success feedback', async () => {
    vi.useFakeTimers()
    const wrapper = mount(AppToastViewport)
    toast.error('持续错误')
    toast.success('短暂成功')
    await wrapper.vm.$nextTick()

    vi.advanceTimersByTime(4_000)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('持续错误')
    expect(wrapper.text()).not.toContain('短暂成功')

    await wrapper.find('[role="alert"] button').trigger('click')
    expect(toastItems).toHaveLength(0)
  })

  it('deduplicates repeated messages and limits the visible stack to four', async () => {
    const wrapper = mount(AppToastViewport)
    toast.error('重复消息')
    toast.error('重复消息')
    for (let index = 1; index <= 5; index += 1) toast.warning(`警告 ${index}`)
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.app-toast-item')).toHaveLength(4)
    expect(wrapper.text()).toContain('警告 5')
    expect(wrapper.text()).not.toContain('重复消息')
  })

  it('runs the supplied close callback for manual dismissal', async () => {
    const wrapper = mount(AppToastViewport)
    const onClose = vi.fn()
    toast.info('可关闭通知', { duration: 0, onClose })
    await wrapper.vm.$nextTick()

    await wrapper.find('button').trigger('click')
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('bridges existing page feedback refs without rendering an in-flow element', async () => {
    const pageError = ref('')
    const Page = defineComponent({
      setup() {
        useToastFeedback(pageError, 'error', 'test-page-error')
        return { pageError }
      },
      template: '<main data-page-content>稳定正文</main>',
    })
    const page = mount(Page)
    const viewport = mount(AppToastViewport)
    pageError.value = '页面操作失败'
    await page.vm.$nextTick()

    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(viewport.find('[role="alert"]').text()).toContain('页面操作失败')
    await viewport.find('button').trigger('click')
    expect(pageError.value).toBe('')
  })
})
