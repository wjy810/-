<script setup lang="ts">
import { ref, type Component } from 'vue'

defineOptions({ inheritAttrs: false })

withDefaults(
  defineProps<{
    modelValue?: string | number | null
    size?: 'sm' | 'md' | 'lg'
    icon?: Component
    invalid?: boolean
    clearable?: boolean
  }>(),
  { modelValue: '', size: 'md', icon: undefined, invalid: false, clearable: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const input = ref<HTMLInputElement | null>(null)
defineExpose({ focus: () => input.value?.focus(), el: input })
</script>

<template>
  <div class="ui-input" :class="[`ui-input--${size}`, { 'is-invalid': invalid, 'has-icon': icon }]">
    <component :is="icon" v-if="icon" class="ui-input__icon" :size="16" :stroke-width="1.9" aria-hidden="true" />
    <input
      ref="input"
      v-bind="$attrs"
      class="ui-input__control"
      :value="modelValue ?? ''"
      :aria-invalid="invalid || undefined"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <button
      v-if="clearable && modelValue"
      class="ui-input__clear"
      type="button"
      aria-label="清空"
      @click="emit('update:modelValue', ''); input?.focus()"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
    </button>
    <slot name="suffix" />
  </div>
</template>

<style scoped>
.ui-input {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  height: var(--control-md);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  box-shadow: var(--shadow-xs);
  transition: border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-base) var(--ease-out);
}

.ui-input:hover {
  border-color: var(--border-strong);
}

.ui-input:focus-within {
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}

.ui-input.is-invalid {
  border-color: var(--color-danger);
}

.ui-input--sm {
  height: var(--control-sm);
  border-radius: var(--radius-sm);
}

.ui-input--lg {
  height: var(--control-lg);
}

.ui-input__icon {
  position: absolute;
  left: 11px;
  color: var(--text-tertiary);
  pointer-events: none;
}

.ui-input:focus-within .ui-input__icon {
  color: var(--color-primary-text);
}

.ui-input__control {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 12px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--fs-body);
}

.ui-input__control:focus-visible {
  box-shadow: none;
}

.ui-input--sm .ui-input__control {
  font-size: var(--fs-sm);
}

.ui-input.has-icon .ui-input__control {
  padding-left: 34px;
}

.ui-input__control:disabled {
  color: var(--text-disabled);
  cursor: not-allowed;
}

.ui-input__clear {
  width: 24px;
  height: 24px;
  margin-right: 6px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--text-tertiary);
}

.ui-input__clear:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}
</style>
