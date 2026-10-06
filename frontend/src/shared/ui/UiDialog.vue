<script setup lang="ts">
import { computed } from 'vue'
import { X } from 'lucide-vue-next'
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle, VisuallyHidden } from 'reka-ui'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    width?: number | 'sm' | 'md' | 'lg' | 'xl'
    mobileSheet?: boolean
    closable?: boolean
    /** When false, Esc / outside click / close button do nothing (e.g. while saving). */
    dismissible?: boolean
    hideTitle?: boolean
    bodyClass?: string
  }>(),
  { description: '', width: 'md', mobileSheet: true, closable: true, dismissible: true, hideTitle: false, bodyClass: '' },
)

const emit = defineEmits<{ close: []; 'update:open': [value: boolean] }>()

const WIDTHS = { sm: 440, md: 560, lg: 720, xl: 920 } as const
const maxWidth = computed(() => `${typeof props.width === 'number' ? props.width : WIDTHS[props.width]}px`)

function onOpenChange(value: boolean): void {
  if (value) return
  if (!props.dismissible) return
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
      <DialogOverlay class="ui-dialog-overlay" />
      <DialogContent
        class="ui-dialog"
        :class="{ 'ui-dialog--sheet': mobileSheet }"
        :style="{ '--dialog-width': maxWidth }"
        @escape-key-down="preventIfLocked"
        @pointer-down-outside="preventIfLocked"
        @interact-outside="preventIfLocked"
      >
        <header v-if="!hideTitle" class="ui-dialog__head">
          <div class="ui-dialog__titles">
            <DialogTitle class="ui-dialog__title">{{ title }}</DialogTitle>
            <DialogDescription v-if="description" class="ui-dialog__desc">{{ description }}</DialogDescription>
            <slot name="subtitle" />
          </div>
          <DialogClose v-if="closable" class="ui-dialog__close" aria-label="关闭" :disabled="!dismissible">
            <X :size="18" :stroke-width="1.9" />
          </DialogClose>
        </header>
        <VisuallyHidden v-else>
          <DialogTitle>{{ title }}</DialogTitle>
          <DialogDescription v-if="description">{{ description }}</DialogDescription>
        </VisuallyHidden>
        <div class="ui-dialog__body" :class="bodyClass">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="ui-dialog__foot">
          <slot name="footer" />
        </footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style>
.ui-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  background: var(--scrim);
  backdrop-filter: blur(4px);
  animation: jp-fade-in var(--dur-slow) var(--ease-standard);
}

.ui-dialog-overlay[data-state='closed'] {
  animation: ui-dialog-fade-out var(--dur-base) var(--ease-in) forwards;
}

.ui-dialog {
  position: fixed;
  z-index: var(--z-dialog);
  top: 50%;
  left: 50%;
  width: min(var(--dialog-width), calc(100vw - 32px));
  max-height: min(86vh, 900px);
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-lg);
  transform: translate(-50%, -50%);
  animation: ui-dialog-in var(--dur-slow) var(--ease-out);
  outline: none;
}

.ui-dialog[data-state='closed'] {
  animation: ui-dialog-out var(--dur-base) var(--ease-in) forwards;
}

.ui-dialog__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-5) var(--space-3) var(--space-6);
}

.ui-dialog__titles {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ui-dialog__title {
  margin: 0;
  font-size: 17px;
  line-height: 26px;
  font-weight: 650;
  color: var(--text-primary);
}

.ui-dialog__desc {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: var(--lh-sm);
}

.ui-dialog__close {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin: -4px -4px 0 0;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  transition: background-color var(--dur-fast), color var(--dur-fast);
}

.ui-dialog__close:hover:not(:disabled) {
  background: var(--surface-3);
  color: var(--text-primary);
}

.ui-dialog__body {
  min-height: 0;
  overflow: auto;
  padding: var(--space-2) var(--space-6) var(--space-5);
  color: var(--text-primary);
}

.ui-dialog__foot {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--border-subtle);
  background: var(--surface-2);
  border-radius: 0 0 var(--radius-xl) var(--radius-xl);
}

@keyframes ui-dialog-in {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 10px)) scale(0.96);
  }
}

@keyframes ui-dialog-out {
  to {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 4px)) scale(0.98);
  }
}

@media (max-width: 640px) {
  .ui-dialog--sheet {
    top: auto;
    bottom: 0;
    left: 0;
    width: 100vw;
    max-height: 92vh;
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    transform: none;
    animation: ui-sheet-in var(--dur-slow) var(--ease-out);
    padding-bottom: env(safe-area-inset-bottom);
  }

  .ui-dialog--sheet[data-state='closed'] {
    animation: ui-sheet-out var(--dur-base) var(--ease-in) forwards;
  }

  .ui-dialog--sheet .ui-dialog__foot {
    border-radius: 0;
  }

  .ui-dialog__foot > * {
    flex: 1 1 auto;
  }
}
</style>
