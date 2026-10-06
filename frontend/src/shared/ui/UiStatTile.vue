<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(defineProps<{ label: string; value: string | number; hint?: string; icon?: Component; tone?: 'primary' | 'accent' | 'success' | 'violet' }>(), {
  hint: '',
  icon: undefined,
  tone: 'primary',
})
</script>

<template>
  <div class="ui-stat">
    <span v-if="icon" class="ui-stat__icon" :class="`ui-stat__icon--${tone}`"><component :is="icon" :size="18" :stroke-width="1.9" /></span>
    <div class="ui-stat__body">
      <span class="ui-stat__value">{{ value }}</span>
      <span class="ui-stat__label">{{ label }}</span>
      <span v-if="hint" class="ui-stat__hint">{{ hint }}</span>
    </div>
  </div>
</template>

<style scoped>
.ui-stat {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
}

.ui-stat__icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.ui-stat__icon--accent {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.ui-stat__icon--success {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.ui-stat__icon--violet {
  background: var(--color-primary-soft);
  color: var(--violet-400);
}

.ui-stat__body {
  display: grid;
  min-width: 0;
}

.ui-stat__value {
  font-size: 22px;
  line-height: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.ui-stat__label {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.ui-stat__hint {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}
</style>
