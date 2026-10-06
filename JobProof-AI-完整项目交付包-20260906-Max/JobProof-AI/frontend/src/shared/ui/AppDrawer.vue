<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    width?: number
  }>(),
  { width: 480 },
)

const emit = defineEmits<{ close: [] }>()
const drawer = ref<HTMLElement | null>(null)
let restoreFocus: HTMLElement | null = null
let previousOverflow = ''

function focusableElements(): HTMLElement[] {
  if (!drawer.value) return []
  return Array.from(drawer.value.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'))
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key !== 'Tab') return
  const items = focusableElements()
  if (!items.length) {
    event.preventDefault()
    drawer.value?.focus()
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
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
      restoreFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
      previousOverflow = document.body.style.overflow
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
      void nextTick(() => (focusableElements()[0] ?? drawer.value)?.focus())
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = previousOverflow
      restoreFocus?.focus()
      restoreFocus = null
    }
  },
)

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = previousOverflow
})
</script>

<template>
  <Teleport to="body">
    <Transition name="app-drawer">
      <div v-if="open" class="drawer-mask" @click.self="emit('close')">
        <aside
          ref="drawer"
          class="drawer"
          :style="{ width: `${width}px` }"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
          tabindex="-1"
        >
          <header class="drawer__head">
            <h3 class="drawer__title">{{ title }}</h3>
            <button class="icon-btn" type="button" title="关闭" @click="emit('close')">
              <AppIcon name="x" :size="16" />
            </button>
          </header>
          <div class="drawer__body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="drawer__foot">
            <slot name="footer" />
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(15, 23, 42, 0.45);
}

.drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  max-width: calc(100vw - 48px);
  display: flex;
  flex-direction: column;
  background: var(--surface);
  box-shadow: var(--shadow-l);
}

.app-drawer-enter-active,
.app-drawer-leave-active {
  transition: background-color var(--motion-base) ease;
}

.app-drawer-enter-active .drawer,
.app-drawer-leave-active .drawer {
  transition:
    opacity var(--motion-base) ease,
    transform var(--motion-slow) var(--motion-ease-out);
}

.app-drawer-enter-from,
.app-drawer-leave-to {
  background: rgba(15, 23, 42, 0);
}

.app-drawer-enter-from .drawer,
.app-drawer-leave-to .drawer {
  opacity: 0;
  transform: translateX(36px);
}

.drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.drawer__title {
  font-size: 16px;
}

.drawer__body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.drawer__foot {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (prefers-reduced-motion: reduce) {
  .app-drawer-enter-active,
  .app-drawer-leave-active,
  .app-drawer-enter-active .drawer,
  .app-drawer-leave-active .drawer { transition: none; }
}
</style>
