<script setup lang="ts">
import { SwitchRoot, SwitchThumb } from 'reka-ui'

withDefaults(defineProps<{ modelValue: boolean; disabled?: boolean; label?: string; id?: string }>(), {
  disabled: false,
  label: undefined,
  id: undefined,
})
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <SwitchRoot
    :id="id"
    :model-value="modelValue"
    :disabled="disabled"
    :aria-label="label"
    class="ui-switch"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <SwitchThumb class="ui-switch__thumb" />
  </SwitchRoot>
</template>

<style>
.ui-switch {
  position: relative;
  flex-shrink: 0;
  width: 36px;
  height: 20px;
  padding: 2px;
  border-radius: 999px;
  background: var(--border-strong);
  transition: background-color var(--dur-fast) var(--ease-standard);
}

.ui-switch[data-state='checked'] {
  background: var(--color-primary);
}

.ui-switch[data-disabled] {
  opacity: 0.5;
  cursor: not-allowed;
}

.ui-switch:focus-visible {
  box-shadow: var(--focus-ring);
  border-radius: 999px;
}

.ui-switch__thumb {
  display: block;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
  transition: transform var(--dur-base) var(--ease-spring);
}

.ui-switch__thumb[data-state='checked'] {
  transform: translateX(16px);
}
</style>
