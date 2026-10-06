<script setup lang="ts">
export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'accent' | 'ai'

withDefaults(defineProps<{ tone?: BadgeTone; dot?: boolean; size?: 'sm' | 'md' }>(), {
  tone: 'neutral',
  dot: false,
  size: 'md',
})
</script>

<template>
  <span class="ui-badge" :class="[`ui-badge--${tone}`, `ui-badge--${size}`]">
    <span v-if="dot" class="ui-badge__dot" aria-hidden="true" />
    <slot />
  </span>
</template>

<style scoped>
.ui-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--radius-xs);
  font-size: var(--fs-xs);
  font-weight: 550;
  line-height: 1;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  background: var(--surface-3);
  color: var(--text-secondary);
}

.ui-badge--sm {
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
}

.ui-badge :deep(svg) {
  flex-shrink: 0;
}

.ui-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.ui-badge--primary {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.ui-badge--success {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.ui-badge--warning {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.ui-badge--danger {
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
}

.ui-badge--accent {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.ui-badge--ai {
  background: var(--gradient-ai-soft);
  color: var(--color-primary-text);
  box-shadow: inset 0 0 0 1px var(--color-primary-border);
}
</style>
