import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ApiClientError } from '@/shared/api/types'
import PageState from './PageState.vue'

const failure = new ApiClientError({ category: 'SYSTEM_FAILURE', reason: 'X', message: '服务暂时不可用' }, 503, 'req-1')

function render(props: Record<string, unknown>) {
  return mount(PageState, {
    props,
    slots: { default: '<p class="content">内容</p>', empty: '<p class="empty">还没有记录</p>' },
    global: { stubs: { UiIllustration: true } },
  })
}

describe('PageState', () => {
  it('shows the error with retry, not the empty state, when the first load fails', async () => {
    const wrapper = render({ loaded: false, error: failure, empty: true })
    expect(wrapper.find('.empty').exists()).toBe(false)
    expect(wrapper.text()).toContain('服务暂时不可用')
    expect(wrapper.text()).toContain('req-1')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('keeps earlier content with a notice when a refresh fails', () => {
    const wrapper = render({ loaded: true, error: failure })
    expect(wrapper.find('.content').exists()).toBe(true)
    expect(wrapper.text()).toContain('刷新失败')
  })

  it('shows the empty slot only for a successful empty load', () => {
    expect(render({ loaded: true, empty: true }).find('.empty').exists()).toBe(true)
    expect(render({ loaded: false, loading: true }).find('.empty').exists()).toBe(false)
  })
})
