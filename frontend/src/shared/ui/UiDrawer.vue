<script setup lang="ts">
import { X } from 'lucide-vue-next'
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    width?: number
    dismissible?: boolean
  }>(),
  { description: '', width: 480, dismissible: true },
)

const emit = defineEmits<{ close: []; 'update:open': [value: boolean] }>()

function onOpenChange(value: boolean): void {
  if (value || !props.dismissible) return
  emit('update:open', false)
  emit('close')
}

function preventIfLocked(event: Event): void {
  if (!props.dismissible) event.preventDefault()
}
</script>

<template>
  <DialogRoot :open="open" @update:open="onOpenChange">
    <DialogPortal>
      <DialogOverlay class="ui-drawer-overlay" />
      <DialogContent
        class="ui-drawer"
        :style="{ '--drawer-width': `${width}px` }"
        @escape-key-down="preventIfLocked"
        @pointer-down-outside="preventIfLocked"
      >
        <header class="ui-drawer__head">
          <div>
            <DialogTitle class="ui-drawer__title">{{ title }}</DialogTitle>
            <DialogDescription v-if="description" class="ui-drawer__desc">{{ description }}</DialogDescription>
          </div>
          <slot name="head-actions" />
          <DialogClose class="ui-drawer__close" aria-label="关闭" :disabled="!dismissible">
            <X :size="18" :stroke-width="1.9" />
          </DialogClose>
        </header>
        <div class="ui-drawer__body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="ui-drawer__foot">
          <slot name="footer" />
        </footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style>
.ui-drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-drawer);
  background: var(--scrim);
  animation: jp-fade-in var(--dur-slow) var(--ease-standard);
}

.ui-drawer-overlay[data-state='closed'] {
  animation: ui-dialog-fade-out var(--dur-base) var(--ease-in) forwards;
}

.ui-drawer {
  position: fixed;
  z-index: var(--z-drawer);
  top: 8px;
  right: 8px;
  bottom: 8px;
  width: min(var(--drawer-width), calc(100vw - 16px));
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-lg);
  animation: ui-drawer-in var(--dur-slow) var(--ease-out);
  outline: none;
}

.ui-drawer[data-state='closed'] {
  animation: ui-drawer-out var(--dur-base) var(--ease-in) forwards;
}

.ui-drawer__head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-5) var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--border-subtle);
}

.ui-drawer__head > div:first-child {
  flex: 1;
  min-width: 0;
}

.ui-drawer__title {
  margin: 0;
  font-size: 17px;
  line-height: 26px;
  font-weight: 650;
}

.ui-drawer__desc {
  margin: 2px 0 0;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.ui-drawer__close {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  transition: background-color var(--dur-fast), color var(--dur-fast);
}

.ui-drawer__close:hover {
  background: var(--surface-3);
  color: var(--text-primary);
}

.ui-drawer__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--space-5) var(--space-6);
}

.ui-drawer__foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--border-subtle);
}

@keyframes ui-drawer-in {
  from {
    opacity: 0.6;
    transform: translateX(calc(100% + 16px));
  }
}

@keyframes ui-drawer-out {
  to {
    opacity: 0.6;
    transform: translateX(calc(100% + 16px));
  }
}

@media (max-width: 640px) {
  .ui-drawer {
    top: auto;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100vw;
    max-height: 90vh;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    animation: ui-sheet-in var(--dur-slow) var(--ease-out);
  }

  .ui-drawer[data-state='closed'] {
    animation: ui-sheet-out var(--dur-base) var(--ease-in) forwards;
  }
}
</style>
