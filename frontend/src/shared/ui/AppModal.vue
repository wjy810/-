<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    width?: number
    mobileSheet?: boolean
  }>(),
  { width: 520, mobileSheet: false },
)

const emit = defineEmits<{ close: [] }>()
const modal = ref<HTMLElement | null>(null)
let previousFocus: HTMLElement | null = null
let previousBodyOverflow = ''

const focusableSelector = [
  'a[href]', 'button:not([disabled])', 'input:not([disabled])', 'select:not([disabled])',
  'textarea:not([disabled])', '[tabindex]:not([tabindex="-1"])',
].join(',')

function focusableElements(): HTMLElement[] {
  if (!modal.value) return []
  return Array.from(modal.value.querySelectorAll<HTMLElement>(focusableSelector))
    .filter(element => !element.hasAttribute('hidden') && element.getAttribute('aria-hidden') !== 'true')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key !== 'Tab' || !modal.value) return
  const elements = focusableElements()
  if (!elements.length) {
    event.preventDefault()
    modal.value.focus()
    return
  }
  const first = elements[0]
  const last = elements[elements.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
      previousBodyOverflow = document.body.style.overflow
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
      void nextTick(() => {
        const preferred = modal.value?.querySelector<HTMLElement>('[autofocus]')
        ;(preferred ?? focusableElements()[0] ?? modal.value)?.focus()
      })
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = previousBodyOverflow
      const target = previousFocus
      previousFocus = null
      void nextTick(() => target?.focus())
    }
  },
)

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = previousBodyOverflow
  previousFocus?.focus()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="app-modal">
      <div v-if="open" class="modal-mask" :class="{ 'modal-mask--mobile-sheet': mobileSheet }" @click.self="emit('close')">
        <div
          ref="modal"
          class="modal"
          :class="{ 'modal--mobile-sheet': mobileSheet }"
          :style="{ maxWidth: `${width}px` }"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
          tabindex="-1"
        >
          <header class="modal__head">
            <h3 class="modal__title">{{ title }}</h3>
            <button class="icon-btn" type="button" title="关闭" @click="emit('close')">
              <AppIcon name="x" :size="16" />
            </button>
          </header>
          <div class="modal__body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="modal__foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(15, 23, 42, 0.45);
  display: grid;
  place-items: center;
  padding: 24px;
}

.modal {
  width: 100%;
  max-height: calc(100vh - 96px);
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border-radius: var(--radius-l);
  box-shadow: var(--shadow-l);
}

.app-modal-enter-active,
.app-modal-leave-active {
  transition: background-color var(--motion-base) ease;
}

.app-modal-enter-active .modal,
.app-modal-leave-active .modal {
  transition:
    opacity var(--motion-base) ease,
    transform var(--motion-base) var(--motion-ease-out);
}

.app-modal-enter-from,
.app-modal-leave-to {
  background: rgba(15, 23, 42, 0);
}

.app-modal-enter-from .modal,
.app-modal-leave-to .modal {
  opacity: 0;
  transform: translateY(9px) scale(0.985);
}

.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.modal__title {
  font-size: 16px;
}

.modal__body {
  padding: 20px;
  overflow-y: auto;
}

.modal__foot {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 700px) {
  .modal-mask--mobile-sheet {
    place-items: end center;
    padding: 0;
    background: rgba(29, 43, 67, 0.5);
  }

  .modal--mobile-sheet {
    position: relative;
    max-width: none !important;
    max-height: min(88dvh, 760px);
    border-radius: 22px 22px 0 0;
    box-shadow: 0 -20px 60px rgba(21, 45, 82, 0.24);
  }

  .modal--mobile-sheet::before {
    content: '';
    position: absolute;
    z-index: 2;
    top: 9px;
    left: 50%;
    width: 44px;
    height: 4px;
    border-radius: 999px;
    background: #c8d3e3;
    transform: translateX(-50%);
  }

  .modal--mobile-sheet .modal__head {
    min-height: 64px;
    padding: 22px 18px 12px;
  }

  .modal--mobile-sheet .modal__body {
    padding: 14px 18px 20px;
    scrollbar-width: none;
    -ms-overflow-style: none;
    overscroll-behavior: contain;
  }

  .modal--mobile-sheet .modal__body::-webkit-scrollbar {
    display: none;
  }

  .modal--mobile-sheet .modal__foot {
    min-height: 72px;
    padding: 12px 18px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
  }

  .app-modal-enter-from .modal--mobile-sheet,
  .app-modal-leave-to .modal--mobile-sheet {
    transform: translateY(32px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-modal-enter-active,
  .app-modal-leave-active,
  .app-modal-enter-active .modal,
  .app-modal-leave-active .modal { transition: none; }
}
</style>
