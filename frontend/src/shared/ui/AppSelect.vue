<script setup lang="ts">
/** @deprecated Use UiSelect. Same props/events; legacy `<option>` children are supported. */
import UiSelect, { type SelectOption, type SelectValue } from './UiSelect.vue'

withDefaults(
  defineProps<{
    modelValue: SelectValue
    options?: readonly SelectOption[]
    ariaLabel?: string
    id?: string
    name?: string
    required?: boolean
    disabled?: boolean
    searchable?: boolean
    searchPlaceholder?: string
    emptyText?: string
    menuAlign?: 'start' | 'end'
  }>(),
  { options: () => [], ariaLabel: '选择选项', id: undefined, name: undefined, searchPlaceholder: '搜索选项', emptyText: '没有匹配选项', menuAlign: 'start' },
)
const emit = defineEmits<{ 'update:modelValue': [value: SelectValue]; change: [value: SelectValue] }>()
</script>

<template>
  <UiSelect
    :model-value="modelValue"
    :options="options"
    :aria-label="ariaLabel"
    :placeholder="ariaLabel"
    :id="id"
    :name="name"
    :required="required"
    :disabled="disabled"
    :searchable="searchable"
    :search-placeholder="searchPlaceholder"
    :empty-text="emptyText"
    :menu-align="menuAlign"
    @update:model-value="emit('update:modelValue', $event)"
    @change="emit('change', $event)"
  ><slot /></UiSelect>
</template>
