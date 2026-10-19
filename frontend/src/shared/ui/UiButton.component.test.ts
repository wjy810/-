import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
vi.mock('vue-router', () => ({ RouterLink: { name: 'RouterLink', props: ['to'], template: '<a href="#" @click="$attrs.onNavigate?.()"><slot /></a>' } }))
import UiButton from './UiButton.vue'

describe('UiButton', () => {
  it('keeps a pending button focusable but ignores clicks and form submits', async () => {
    const clicked = vi.fn()
    const submitted = vi.fn()
    const form = mount({
      components: { UiButton },
      setup: () => ({ clicked, submitted }),
      template: '<form @submit.prevent="submitted"><UiButton type="submit" pending @click="clicked">保存</UiButton></form>',
    }, { attachTo: document.body })
    const button = form.get('button')
    expect(button.attributes('disabled')).toBeUndefined()
    expect(button.attributes('aria-disabled')).toBe('true')
    expect(button.attributes('aria-busy')).toBe('true')
    await button.trigger('click')
    expect(clicked).not.toHaveBeenCalled()
    expect(submitted).not.toHaveBeenCalled()
    form.unmount()
  })

  it('a disabled button is disabled, and an enabled one clicks', async () => {
    const clicked = vi.fn()
    const disabled = mount(UiButton, { props: { disabled: true }, slots: { default: '删除' } })
    expect(disabled.get('button').attributes('disabled')).toBeDefined()
    const enabled = mount(UiButton, { attrs: { onClick: clicked }, slots: { default: '确定' } })
    await enabled.get('button').trigger('click')
    expect(clicked).toHaveBeenCalledTimes(1)
  })
})
