<script setup lang="ts">
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'

withDefaults(
  defineProps<{ open?: boolean; align?: 'start' | 'center' | 'end'; side?: 'top' | 'bottom' | 'left' | 'right'; width?: number }>(),
  { open: undefined, align: 'start', side: 'bottom', width: 280 },
)
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
</script>

<template>
  <PopoverRoot :open="open" @update:open="emit('update:open', $event)">
    <PopoverTrigger as-child>
      <slot name="trigger" />
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent class="ui-popover" :align="align" :side="side" :side-offset="8" :collision-padding="12" :style="{ width: `${width}px` }">
        <slot />
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<style>
.ui-popover {
  z-index: var(--z-dropdown);
  max-width: calc(100vw - 24px);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-md);
  transform-origin: var(--reka-popover-content-transform-origin);
  animation: ui-pop-in var(--dur-base) var(--ease-out);
}

.ui-popover[data-state='closed'] {
  animation: ui-dialog-fade-out var(--dur-fast) var(--ease-in) forwards;
}
</style>
