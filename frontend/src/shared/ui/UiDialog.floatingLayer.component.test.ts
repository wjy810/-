import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/features/ai-resume/services/aiResumeApi', () => ({
  listJobTaxonomy: vi.fn(async () => []),
}))

import JobTaxonomyPicker from '@/features/ai-resume/components/JobTaxonomyPicker.vue'
import UiDialog from './UiDialog.vue'

function mountDialogWithPicker() {
  const open = ref(true)
  const Host = defineComponent({
    setup: () => () => h(UiDialog, { open: open.value, title: '新建', 'onUpdate:open': (value: boolean) => { open.value = value } },
      { default: () => h(JobTaxonomyPicker, { selectedName: '', selectedNodeId: '' }) }),
  })
  const wrapper = mount(Host, { attachTo: document.body })
  return { wrapper, open }
}

function escape(target: EventTarget = document.activeElement ?? document.body): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
  target.dispatchEvent(event)
  return event
}

describe('UiDialog with a teleported picker', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('marks the picker panel as a floating layer and keeps the dialog open on Esc', async () => {
    const { wrapper, open } = mountDialogWithPicker()
    await flushPromises()
    const trigger = document.querySelector<HTMLButtonElement>('.job-taxonomy-trigger')!
    trigger.click()
    await flushPromises()

    const panel = document.querySelector('.job-taxonomy-panel')
    expect(panel?.hasAttribute('data-floating-layer')).toBe(true)

    const event = escape(trigger)
    await nextTick()
    expect(event.defaultPrevented).toBe(true)
    expect(open.value).toBe(true)
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    wrapper.unmount()
  })

  it('lets Esc close the dialog once the picker is closed', async () => {
    const { wrapper } = mountDialogWithPicker()
    await flushPromises()
    const trigger = document.querySelector<HTMLButtonElement>('.job-taxonomy-trigger')!
    expect(escape(trigger).defaultPrevented).toBe(false)
    wrapper.unmount()
  })
})
