<script setup lang="ts">
/**
 * Accessible select built on Reka UI.
 * Accepts options via the `options` prop or legacy `<option value>` children,
 * and switches to a searchable combobox with `searchable`.
 */
import { computed, isVNode, ref, useSlots, type VNodeChild } from 'vue'
import { Check, ChevronDown, Search } from 'lucide-vue-next'
import {
  ComboboxAnchor,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxItemIndicator,
  ComboboxPortal,
  ComboboxRoot,
  ComboboxTrigger,
  ComboboxViewport,
  SelectContent,
  SelectIcon,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectPortal,
  SelectRoot,
  SelectTrigger,
  SelectValue,
  SelectViewport,
} from 'reka-ui'

export type SelectValue = string | number
export type SelectOption = {
  value: SelectValue
  label: string
  count?: number
  keywords?: string
  disabled?: boolean
  description?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: SelectValue | null | undefined
    options?: readonly SelectOption[]
    ariaLabel?: string
    placeholder?: string
    id?: string
    name?: string
    required?: boolean
    disabled?: boolean
    searchable?: boolean
    searchPlaceholder?: string
    emptyText?: string
    menuAlign?: 'start' | 'end'
    size?: 'sm' | 'md' | 'lg'
    invalid?: boolean
  }>(),
  {
    options: () => [],
    ariaLabel: '选择选项',
    placeholder: '请选择',
    id: undefined,
    name: undefined,
    required: false,
    disabled: false,
    searchable: false,
    searchPlaceholder: '搜索选项',
    emptyText: '没有匹配选项',
    menuAlign: 'start',
    size: 'md',
    invalid: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: SelectValue]
  change: [value: SelectValue]
}>()

// Reka forbids '' as an item value, so the empty value travels as a sentinel.
const EMPTY = '__jp_empty__'
const encode = (value: SelectValue | null | undefined): string | number => (value === '' || value == null ? EMPTY : value)
const decode = (value: unknown): SelectValue => (value === EMPTY ? '' : (value as SelectValue))

const slots = useSlots()

function readText(children: VNodeChild | VNodeChild[] | null): string {
  if (children == null || typeof children === 'boolean') return ''
  if (typeof children === 'string' || typeof children === 'number') return String(children)
  if (Array.isArray(children)) return children.map(child => readText(child)).join('')
  if (isVNode(children)) return readText(children.children as VNodeChild | VNodeChild[] | null)
  return ''
}

function collectSlotOptions(nodes: VNodeChild[], result: SelectOption[]): void {
  for (const node of nodes) {
    if (Array.isArray(node)) {
      collectSlotOptions(node, result)
      continue
    }
    if (!isVNode(node)) continue
    if (node.type === 'option') {
      const label = readText(node.children as VNodeChild | VNodeChild[] | null).replace(/\s+/g, ' ').trim()
      const disabled = node.props?.disabled
      result.push({
        value: (node.props?.value ?? label) as SelectValue,
        label,
        disabled: disabled === '' || disabled === true || disabled === 'true',
      })
    } else if (Array.isArray(node.children)) {
      collectSlotOptions(node.children as VNodeChild[], result)
    }
  }
}

const allOptions = computed<SelectOption[]>(() => {
  if (props.options.length) return [...props.options]
  const result: SelectOption[] = []
  const nodes = slots.default?.() ?? []
  collectSlotOptions(nodes, result)
  return result
})

const selected = computed(() => allOptions.value.find(option => option.value === props.modelValue) ?? null)
const internalValue = computed(() => encode(props.modelValue))

function update(value: unknown): void {
  const next = decode(value)
  if (next === props.modelValue) return
  emit('update:modelValue', next)
  emit('change', next)
}

// --- searchable combobox ---
const query = ref('')
const comboOpen = ref(false)
const filtered = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return allOptions.value
  return allOptions.value.filter(option =>
    `${option.label} ${option.keywords ?? ''} ${option.value}`.toLowerCase().includes(keyword),
  )
})

function onComboOpen(open: boolean): void {
  comboOpen.value = open
  if (!open) query.value = ''
}
</script>

<template>
  <ComboboxRoot
    v-if="searchable"
    :model-value="internalValue"
    :open="comboOpen"
    :disabled="disabled"
    :ignore-filter="true"
    :name="name"
    :required="required"
    class="ui-select-root"
    @update:model-value="update"
    @update:open="onComboOpen"
  >
    <ComboboxAnchor as-child>
      <ComboboxTrigger
        :id="id"
        class="ui-select-trigger"
        :class="[`ui-select-trigger--${size}`, { 'is-invalid': invalid, 'is-placeholder': !selected }]"
        :aria-label="ariaLabel"
      >
        <span class="ui-select-trigger__value">{{ selected?.label ?? placeholder }}</span>
        <ChevronDown :size="16" class="ui-select-trigger__chevron" aria-hidden="true" />
      </ComboboxTrigger>
    </ComboboxAnchor>
    <ComboboxPortal>
      <ComboboxContent
        class="ui-select-content"
        position="popper"
        :side-offset="6"
        :align="menuAlign"
        :collision-padding="12"
        :body-lock="false"
      >
        <div class="ui-select-search">
          <Search :size="15" aria-hidden="true" />
          <ComboboxInput v-model="query" class="ui-select-search__input" :placeholder="searchPlaceholder" :aria-label="searchPlaceholder" auto-focus />
        </div>
        <ComboboxViewport class="ui-select-viewport">
          <ComboboxEmpty class="ui-select-empty">{{ emptyText }}</ComboboxEmpty>
          <ComboboxItem
            v-for="option in filtered"
            :key="String(option.value)"
            :value="encode(option.value)"
            :disabled="option.disabled"
            class="ui-select-item"
          >
            <span class="ui-select-item__label">{{ option.label }}</span>
            <span v-if="option.count !== undefined" class="ui-select-item__count">{{ option.count }}</span>
            <ComboboxItemIndicator class="ui-select-item__check"><Check :size="15" /></ComboboxItemIndicator>
          </ComboboxItem>
        </ComboboxViewport>
      </ComboboxContent>
    </ComboboxPortal>
  </ComboboxRoot>

  <SelectRoot
    v-else
    :model-value="internalValue"
    :disabled="disabled"
    :name="name"
    :required="required"
    @update:model-value="update"
  >
    <SelectTrigger
      :id="id"
      class="ui-select-trigger"
      :class="[`ui-select-trigger--${size}`, { 'is-invalid': invalid, 'is-placeholder': !selected }]"
      :aria-label="ariaLabel"
    >
      <SelectValue class="ui-select-trigger__value" :placeholder="placeholder">{{ selected?.label ?? placeholder }}</SelectValue>
      <SelectIcon as-child><ChevronDown :size="16" class="ui-select-trigger__chevron" aria-hidden="true" /></SelectIcon>
    </SelectTrigger>
    <SelectPortal>
      <SelectContent class="ui-select-content" position="popper" :side-offset="6" :align="menuAlign" :collision-padding="12" :body-lock="false">
        <SelectViewport class="ui-select-viewport">
          <SelectItem
            v-for="option in allOptions"
            :key="String(option.value)"
            :value="encode(option.value)"
            :disabled="option.disabled"
            class="ui-select-item"
          >
            <SelectItemText class="ui-select-item__label">{{ option.label }}</SelectItemText>
            <span v-if="option.count !== undefined" class="ui-select-item__count">{{ option.count }}</span>
            <SelectItemIndicator class="ui-select-item__check"><Check :size="15" /></SelectItemIndicator>
          </SelectItem>
          <div v-if="!allOptions.length" class="ui-select-empty">{{ emptyText }}</div>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>

<style>
.ui-select-root {
  display: contents;
}

.ui-select-trigger {
  position: relative;
  width: 100%;
  min-width: 0;
  height: var(--control-md);
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px 0 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-primary);
  font-size: var(--fs-body);
  text-align: left;
  box-shadow: var(--shadow-xs);
  transition: border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-base) var(--ease-out);
}

.ui-select-trigger:hover:not([data-disabled]) {
  border-color: var(--border-strong);
}

.ui-select-trigger:focus-visible,
.ui-select-trigger[data-state='open'] {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
  border-radius: var(--radius-md);
}

.ui-select-trigger[data-disabled] {
  background: var(--surface-3);
  color: var(--text-disabled);
  cursor: not-allowed;
}

.ui-select-trigger.is-invalid {
  border-color: var(--color-danger);
}

.ui-select-trigger--sm {
  height: var(--control-sm);
  font-size: var(--fs-sm);
  border-radius: var(--radius-sm);
}

.ui-select-trigger--lg {
  height: var(--control-lg);
}

.ui-select-trigger__value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ui-select-trigger.is-placeholder .ui-select-trigger__value,
.ui-select-trigger [data-placeholder] {
  color: var(--text-disabled);
}

.ui-select-trigger__chevron {
  flex-shrink: 0;
  color: var(--text-tertiary);
  transition: transform var(--dur-base) var(--ease-out);
}

.ui-select-trigger[data-state='open'] .ui-select-trigger__chevron {
  transform: rotate(180deg);
}

.ui-select-content {
  z-index: calc(var(--z-dialog) + 5);
  min-width: max(var(--reka-select-trigger-width, 0px), var(--reka-combobox-trigger-width, 0px), 180px);
  max-width: min(420px, calc(100vw - 24px));
  max-height: min(360px, var(--reka-select-content-available-height, 360px), var(--reka-combobox-content-available-height, 360px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 6px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-md);
  transform-origin: var(--reka-select-content-transform-origin, var(--reka-combobox-content-transform-origin));
  animation: ui-pop-in var(--dur-base) var(--ease-out);
}

.ui-select-viewport {
  overflow-y: auto;
}

.ui-select-item {
  position: relative;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 30px 6px 10px;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: var(--fs-body);
  line-height: 1.4;
  cursor: pointer;
  user-select: none;
  outline: none;
}

.ui-select-item[data-highlighted] {
  background: var(--surface-3);
}

.ui-select-item[data-state='checked'] {
  color: var(--color-primary-text);
  font-weight: 550;
}

.ui-select-item[data-disabled] {
  color: var(--text-disabled);
  cursor: not-allowed;
}

.ui-select-item__label {
  flex: 1;
  min-width: 0;
}

.ui-select-item__count {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
}

.ui-select-item__check {
  position: absolute;
  right: 8px;
  display: inline-grid;
  place-items: center;
  color: var(--color-primary);
}

.ui-select-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -2px -2px 6px;
  padding: 0 10px;
  height: 36px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
}

.ui-select-search__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
}

.ui-select-search__input:focus-visible {
  box-shadow: none;
}

.ui-select-empty {
  padding: 18px 10px;
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  text-align: center;
}

@keyframes ui-pop-in {
  from {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }
}
</style>
