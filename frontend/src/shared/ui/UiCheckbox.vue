<script setup lang="ts">
import { Check, Minus } from 'lucide-vue-next'
import { CheckboxIndicator, CheckboxRoot } from 'reka-ui'

withDefaults(defineProps<{ modelValue: boolean | 'indeterminate'; disabled?: boolean; label?: string; id?: string }>(), {
  disabled: false,
  label: undefined,
  id: undefined,
})
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <label class="ui-checkbox" :class="{ 'is-disabled': disabled }">
    <CheckboxRoot
      :id="id"
      :model-value="modelValue"
      :disabled="disabled"
      class="ui-checkbox__box"
      @update:model-value="emit('update:modelValue', $event === true)"
    >
      <CheckboxIndicator class="ui-checkbox__indicator">
        <Minus v-if="modelValue === 'indeterminate'" :size="12" :stroke-width="3" />
        <Check v-else :size="12" :stroke-width="3" />
      </CheckboxIndicator>
    </CheckboxRoot>
    <span v-if="label || $slots.default" class="ui-checkbox__label"><slot>{{ label }}</slot></span>
  </label>
</template>

<style>
.ui-checkbox {
  display: inline-flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.ui-checkbox.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.ui-checkbox__box {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  margin-top: 2px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--border-strong);
  border-radius: 5px;
  background: var(--surface-1);
  color: #fff;
  transition: background-color var(--dur-fast), border-color var(--dur-fast);
}

.ui-checkbox__box[data-state='checked'],
.ui-checkbox__box[data-state='indeterminate'] {
  border-color: var(--color-primary);
  background: var(--color-primary);
}

.ui-checkbox__box:focus-visible {
  box-shadow: var(--focus-ring);
  border-radius: 5px;
}

.ui-checkbox__indicator {
  display: grid;
  place-items: center;
  animation: jp-pop-in var(--dur-fast) var(--ease-spring);
}

.ui-checkbox__label {
  color: var(--text-primary);
  font-size: var(--fs-body);
  line-height: 22px;
}
</style>
