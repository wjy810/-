<script setup lang="ts">
import { TooltipContent, TooltipPortal, TooltipRoot, TooltipTrigger } from 'reka-ui'
import UiKbd from './UiKbd.vue'

withDefaults(
  defineProps<{
    content?: string
    shortcut?: string | string[]
    side?: 'top' | 'right' | 'bottom' | 'left'
    align?: 'start' | 'center' | 'end'
    delay?: number
    disabled?: boolean
  }>(),
  { content: '', shortcut: undefined, side: 'top', align: 'center', delay: 400, disabled: false },
)
</script>

<template>
  <TooltipRoot :delay-duration="delay" :disabled="disabled || (!content && !$slots.content)">
    <TooltipTrigger as-child>
      <slot />
    </TooltipTrigger>
    <TooltipPortal>
      <TooltipContent class="ui-tooltip" :side="side" :align="align" :side-offset="6" :collision-padding="8">
        <slot name="content">{{ content }}</slot>
        <UiKbd v-if="shortcut" class="ui-tooltip__kbd" :keys="shortcut" />
      </TooltipContent>
    </TooltipPortal>
  </TooltipRoot>
</template>

<style>
.ui-tooltip {
  z-index: var(--z-toast);
  max-width: 280px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 9px;
  border-radius: var(--radius-sm);
  background: var(--tooltip-bg);
  color: var(--tooltip-text);
  font-size: var(--fs-xs);
  font-weight: 500;
  line-height: 1.45;
  box-shadow: var(--shadow-md);
  transform-origin: var(--reka-tooltip-content-transform-origin);
  animation: ui-tooltip-in var(--dur-base) var(--ease-out);
  user-select: none;
}

.ui-tooltip[data-state='closed'] {
  animation: ui-tooltip-out var(--dur-fast) var(--ease-in);
}

.ui-tooltip__kbd .ui-kbd {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.18);
  color: inherit;
}

@keyframes ui-tooltip-in {
  from {
    opacity: 0;
    transform: scale(0.96);
  }
}

@keyframes ui-tooltip-out {
  to {
    opacity: 0;
    transform: scale(0.98);
  }
}
</style>
