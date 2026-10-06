<script setup lang="ts">
import { computed, type Component } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import UiSpinner from './UiSpinner.vue'
import UiTooltip from './UiTooltip.vue'

// The root is a tooltip wrapper; listeners and attributes belong on the real button.
defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    label: string
    icon?: Component
    size?: 'sm' | 'md' | 'lg'
    variant?: 'ghost' | 'secondary' | 'soft' | 'primary'
    type?: 'button' | 'submit'
    disabled?: boolean
    pending?: boolean
    active?: boolean
    tooltip?: boolean
    shortcut?: string | string[]
    tooltipSide?: 'top' | 'right' | 'bottom' | 'left'
    to?: RouteLocationRaw
    badge?: number | string | null
  }>(),
  { size: 'md', variant: 'ghost', type: 'button', tooltip: true, tooltipSide: 'bottom', badge: null },
)

const iconSize = computed(() => (props.size === 'sm' ? 15 : props.size === 'lg' ? 20 : 17))
const tag = computed(() => (props.to ? RouterLink : 'button'))
</script>

<template>
  <UiTooltip :content="label" :shortcut="shortcut" :side="tooltipSide" :disabled="!tooltip">
    <component
      :is="tag"
      v-bind="$attrs"
      class="ui-icon-btn"
      :class="[`ui-icon-btn--${size}`, `ui-icon-btn--${variant}`, { 'is-active': active }]"
      :type="tag === 'button' ? type : undefined"
      :to="to"
      :aria-label="label"
      :aria-pressed="active === undefined ? undefined : active"
      :disabled="tag === 'button' ? disabled || pending : undefined"
    >
      <UiSpinner v-if="pending" :size="iconSize" />
      <slot v-else>
        <component :is="icon" :size="iconSize" :stroke-width="1.9" aria-hidden="true" />
      </slot>
      <span v-if="badge !== null && badge !== 0 && badge !== ''" class="ui-icon-btn__badge">{{ badge }}</span>
    </component>
  </UiTooltip>
</template>

<style scoped>
.ui-icon-btn {
  position: relative;
  width: var(--control-md);
  height: var(--control-md);
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  background: transparent;
  text-decoration: none;
  transition: transform var(--dur-instant) var(--ease-standard), background-color var(--dur-fast) var(--ease-standard),
    color var(--dur-fast) var(--ease-standard), border-color var(--dur-fast);
}

.ui-icon-btn:hover:not(:disabled) {
  background: var(--surface-3);
  color: var(--text-primary);
}

.ui-icon-btn:active:not(:disabled) {
  transform: scale(0.92);
}

.ui-icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ui-icon-btn:focus-visible {
  box-shadow: var(--focus-ring);
  border-radius: var(--radius-md);
}

.ui-icon-btn.is-active {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.ui-icon-btn--sm {
  width: var(--control-sm);
  height: var(--control-sm);
  border-radius: var(--radius-sm);
}

.ui-icon-btn--lg {
  width: var(--control-lg);
  height: var(--control-lg);
}

.ui-icon-btn--secondary {
  background: var(--surface-1);
  border-color: var(--border-default);
  box-shadow: var(--shadow-xs);
}

.ui-icon-btn--secondary:hover:not(:disabled) {
  background: var(--surface-2);
  border-color: var(--border-strong);
}

.ui-icon-btn--soft {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.ui-icon-btn--soft:hover:not(:disabled) {
  background: var(--color-primary-soft-hover);
  color: var(--color-primary-text);
}

.ui-icon-btn--primary {
  background: var(--color-primary);
  color: var(--text-on-primary);
}

.ui-icon-btn--primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  color: var(--text-on-primary);
}

.ui-icon-btn__badge {
  position: absolute;
  top: 2px;
  right: 1px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--color-accent);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  box-shadow: 0 0 0 2px var(--surface-1);
  font-variant-numeric: tabular-nums;
  animation: jp-pop-in var(--dur-base) var(--ease-spring);
}
</style>
