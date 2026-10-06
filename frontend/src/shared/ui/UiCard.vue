<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

const props = withDefaults(
  defineProps<{ padding?: 'none' | 'sm' | 'md' | 'lg'; interactive?: boolean; to?: RouteLocationRaw; as?: string; tone?: 'default' | 'sunken' | 'ai' | 'accent' }>(),
  { padding: 'md', interactive: false, to: undefined, as: 'section', tone: 'default' },
)
const tag = computed(() => (props.to ? RouterLink : props.as))
</script>

<template>
  <component :is="tag" :to="to" class="ui-card" :class="[`ui-card--p-${padding}`, `ui-card--${tone}`, { 'is-interactive': interactive || !!to }]">
    <slot />
  </component>
</template>

<style scoped>
.ui-card {
  position: relative;
  display: block;
  min-width: 0;
  color: inherit;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-base) var(--ease-out);
}

.ui-card--p-sm {
  padding: var(--space-4);
}

.ui-card--p-md {
  padding: var(--space-5);
}

.ui-card--p-lg {
  padding: var(--space-6);
}

.ui-card--sunken {
  background: var(--surface-2);
  box-shadow: none;
}

.ui-card--ai {
  background: linear-gradient(var(--surface-1), var(--surface-1)) padding-box, var(--gradient-ai) border-box;
  border: 1.5px solid transparent;
}

.ui-card--accent {
  background: var(--color-accent-soft);
  border-color: var(--color-accent-border);
  box-shadow: none;
}

.ui-card.is-interactive {
  cursor: pointer;
}

.ui-card.is-interactive:hover {
  transform: translateY(-2px);
  border-color: var(--border-default);
  box-shadow: var(--shadow-md);
  color: inherit;
}

.ui-card.is-interactive:active {
  transform: translateY(0) scale(0.995);
}

.ui-card.is-interactive:focus-visible {
  box-shadow: var(--focus-ring), var(--shadow-md);
  border-radius: var(--radius-lg);
}
</style>
