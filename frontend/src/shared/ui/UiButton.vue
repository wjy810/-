<script setup lang="ts">
import { computed, type Component } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import UiSpinner from './UiSpinner.vue'

export type ButtonVariant = 'primary' | 'secondary' | 'soft' | 'ghost' | 'danger' | 'ai' | 'link'
export type ButtonSize = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: ButtonVariant
    size?: ButtonSize
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
    pending?: boolean
    block?: boolean
    icon?: Component
    iconRight?: Component
    to?: RouteLocationRaw
    href?: string
    target?: string
  }>(),
  { variant: 'primary', size: 'md', type: 'button' },
)

const tag = computed(() => (props.to ? RouterLink : props.href ? 'a' : 'button'))
const iconSize = computed(() => (props.size === 'sm' ? 15 : props.size === 'lg' ? 18 : 16))
const inert = computed(() => props.disabled || props.pending)

// Bind only what each element understands: an `href: undefined` falling through onto
// RouterLink would override the href it renders, leaving an unfocusable <a>.
const tagAttrs = computed<Record<string, unknown>>(() => {
  if (props.to) {
    return { to: props.to, target: props.target, 'aria-disabled': inert.value ? 'true' : undefined, tabindex: inert.value ? -1 : undefined }
  }
  if (props.href) {
    return {
      href: props.href,
      target: props.target,
      rel: props.target === '_blank' ? 'noopener noreferrer' : undefined,
      'aria-disabled': inert.value ? 'true' : undefined,
      tabindex: inert.value ? -1 : undefined,
    }
  }
  return { type: props.type, disabled: inert.value }
})
</script>

<template>
  <component
    :is="tag"
    class="ui-btn"
    :class="[`ui-btn--${variant}`, `ui-btn--${size}`, { 'is-block': block, 'is-pending': pending }]"
    v-bind="tagAttrs"
    :aria-busy="pending || undefined"
  >
    <UiSpinner v-if="pending" :size="iconSize" class="ui-btn__icon" />
    <component :is="icon" v-else-if="icon" :size="iconSize" :stroke-width="2" class="ui-btn__icon" aria-hidden="true" />
    <span v-if="$slots.default" class="ui-btn__label"><slot /></span>
    <component :is="iconRight" v-if="iconRight" :size="iconSize" :stroke-width="2" class="ui-btn__icon ui-btn__icon--right" aria-hidden="true" />
  </component>
</template>

<style scoped>
.ui-btn {
  position: relative;
  isolation: isolate;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: var(--control-md);
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-size: var(--fs-body);
  font-weight: 550;
  line-height: 1;
  white-space: nowrap;
  text-decoration: none;
  user-select: none;
  cursor: pointer;
  transition: transform var(--dur-instant) var(--ease-standard), background-color var(--dur-fast) var(--ease-standard),
    border-color var(--dur-fast) var(--ease-standard), color var(--dur-fast) var(--ease-standard),
    box-shadow var(--dur-base) var(--ease-out), opacity var(--dur-fast);
}

.ui-btn:active:not(:disabled):not([aria-disabled='true']) {
  transform: scale(0.98);
}

.ui-btn:focus-visible {
  box-shadow: var(--focus-ring);
  border-radius: var(--radius-md);
}

.ui-btn:disabled,
.ui-btn[aria-disabled='true'] {
  opacity: 0.5;
  cursor: not-allowed;
}

.ui-btn.is-pending {
  opacity: 0.85;
  cursor: progress;
}

.ui-btn.is-block {
  display: flex;
  width: 100%;
}

.ui-btn__icon {
  flex-shrink: 0;
}

.ui-btn__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* sizes */
.ui-btn--sm {
  height: var(--control-sm);
  padding: 0 10px;
  font-size: var(--fs-sm);
  border-radius: var(--radius-sm);
}

.ui-btn--lg {
  height: var(--control-lg);
  padding: 0 20px;
  font-size: 15px;
  gap: 8px;
}

/* variants */
.ui-btn--primary {
  background: var(--color-primary);
  color: var(--text-on-primary);
  box-shadow: var(--shadow-xs), inset 0 1px 0 rgba(255, 255, 255, 0.14);
}

.ui-btn--primary:hover:not(:disabled):not([aria-disabled='true']) {
  background: var(--color-primary-hover);
  color: var(--text-on-primary);
  box-shadow: var(--shadow-primary);
}

.ui-btn--secondary {
  background: var(--surface-1);
  border-color: var(--border-default);
  color: var(--text-primary);
  box-shadow: var(--shadow-xs);
}

.ui-btn--secondary:hover:not(:disabled):not([aria-disabled='true']) {
  background: var(--surface-2);
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.ui-btn--soft {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.ui-btn--soft:hover:not(:disabled):not([aria-disabled='true']) {
  background: var(--color-primary-soft-hover);
  color: var(--color-primary-text);
}

.ui-btn--ghost {
  background: transparent;
  color: var(--text-secondary);
}

.ui-btn--ghost:hover:not(:disabled):not([aria-disabled='true']) {
  background: var(--surface-3);
  color: var(--text-primary);
}

.ui-btn--danger {
  background: var(--color-danger);
  color: var(--text-on-primary);
}

.ui-btn--danger:hover:not(:disabled):not([aria-disabled='true']) {
  background: var(--color-danger-hover);
  color: var(--text-on-primary);
}

.ui-btn--danger:focus-visible {
  box-shadow: var(--focus-ring-danger);
}

.ui-btn--ai {
  color: var(--text-primary);
  background: var(--surface-1);
  border: 1.5px solid transparent;
  background-image: linear-gradient(var(--surface-1), var(--surface-1)), var(--gradient-ai);
  background-origin: border-box;
  background-clip: padding-box, border-box;
  box-shadow: var(--shadow-xs);
}

.ui-btn--ai :deep(.ui-btn__icon) {
  color: var(--color-accent);
}

.ui-btn--ai:hover:not(:disabled):not([aria-disabled='true']) {
  background-image: linear-gradient(var(--surface-2), var(--surface-2)), var(--gradient-ai);
  box-shadow: 0 4px 14px -6px rgba(155, 107, 242, 0.5);
  color: var(--text-primary);
}

.ui-btn--link {
  height: auto;
  padding: 2px 2px;
  background: transparent;
  color: var(--color-primary-text);
  border-radius: var(--radius-xs);
}

.ui-btn--link:hover:not(:disabled):not([aria-disabled='true']) {
  color: var(--color-primary-hover);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 640px) {
  .ui-btn--md {
    min-height: 40px;
  }
}
</style>
