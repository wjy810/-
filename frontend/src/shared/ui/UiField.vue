<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    label?: string
    hint?: string
    error?: string | null
    required?: boolean
    optional?: boolean
    id?: string
    counter?: string
  }>(),
  { label: '', hint: '', error: null, required: false, optional: false, id: undefined, counter: undefined },
)

const generated = useId()
const fieldId = computed(() => props.id ?? `field-${generated}`)
const messageId = computed(() => `${fieldId.value}-message`)
</script>

<template>
  <div class="ui-field" :class="{ 'has-error': !!error }">
    <div v-if="label || counter" class="ui-field__top">
      <label v-if="label" class="ui-field__label" :for="fieldId">
        {{ label }}
        <span v-if="required" class="ui-field__flag">必填</span>
        <span v-else-if="optional" class="ui-field__flag ui-field__flag--muted">选填</span>
      </label>
      <span v-if="counter" class="ui-field__counter">{{ counter }}</span>
    </div>
    <slot :id="fieldId" :described-by="error || hint ? messageId : undefined" :invalid="!!error" />
    <Transition name="fade" mode="out-in">
      <p v-if="error" :id="messageId" :key="'e'" class="ui-field__error" role="alert">{{ error }}</p>
      <p v-else-if="hint" :id="messageId" :key="'h'" class="ui-field__hint">{{ hint }}</p>
    </Transition>
  </div>
</template>

<style scoped>
.ui-field {
  display: grid;
  /* An implicit auto track would never shrink below a native input's intrinsic width. */
  grid-template-columns: minmax(0, 1fr);
  gap: 6px;
  align-content: start;
  min-width: 0;
}

.ui-field__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.ui-field__label {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  color: var(--text-primary);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.ui-field__flag {
  color: var(--color-accent-text);
  font-size: 11px;
  font-weight: 500;
}

.ui-field__flag--muted {
  color: var(--text-tertiary);
}

.ui-field__counter {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
}

.ui-field__hint,
.ui-field__error {
  margin: 0;
  font-size: var(--fs-xs);
  line-height: var(--lh-xs);
}

.ui-field__hint {
  color: var(--text-tertiary);
}

.ui-field__error {
  color: var(--color-danger-text);
}
</style>
