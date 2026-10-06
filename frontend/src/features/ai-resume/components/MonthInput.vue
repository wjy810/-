<script setup lang="ts">
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'

withDefaults(defineProps<{
  modelValue?: string
  disabled?: boolean
  label?: string
}>(), {
  modelValue: '',
  disabled: false,
  label: '月份',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <AppDatePicker
    mode="month"
    :model-value="modelValue"
    :disabled="disabled"
    :label="label"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
