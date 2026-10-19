import { defineComponent, h } from 'vue'

/** Stands in for UiDialog: renders the body and footer inline while open. */
export const ModalStub = defineComponent({
  name: 'UiDialog',
  props: {
    open: { type: Boolean, default: false },
    title: { type: String, default: '' },
    width: { type: [Number, String], default: 'md' },
    mobileSheet: { type: Boolean, default: true },
  },
  emits: ['close'],
  setup(props, { slots, emit }) {
    return () => props.open
      ? h('section', {
          role: 'dialog',
          'aria-label': props.title,
          'data-mobile-sheet': String(props.mobileSheet),
        }, [
          h('h3', props.title),
          h('button', { type: 'button', class: 'modal-close', onClick: () => emit('close') }, '关闭'),
          slots.default?.(),
          slots.footer ? h('footer', slots.footer()) : null,
        ])
      : null
  },
})

/** Stands in for UiSelect as a native <select>, so tests can pick a value with setValue(). */
export const SelectStub = defineComponent({
  name: 'UiSelect',
  inheritAttrs: false,
  props: {
    modelValue: { type: [String, Number], default: '' },
    options: { type: Array as () => Array<{ value: string | number; label: string }>, default: () => [] },
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { attrs, emit, slots }) {
    return () => h('select', {
      ...attrs,
      value: props.modelValue,
      onChange: (event: Event) => {
        const value = (event.target as HTMLSelectElement).value
        emit('update:modelValue', value)
        emit('change', value)
      },
    }, props.options.length
      ? props.options.map(option => h('option', { value: option.value }, option.label))
      : slots.default?.())
  },
})
