<script setup lang="ts">
import { defineAsyncComponent, ref, watch } from 'vue'
import { TooltipProvider } from 'reka-ui'
import { RotateCcw } from 'lucide-vue-next'
import BrandMark from '@/shared/ui/BrandMark.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiConfirmHost from '@/shared/ui/UiConfirmHost.vue'
import UiToaster from '@/shared/ui/UiToaster.vue'
import { useSessionStore } from '@/stores/session'
import { useCommandPaletteStore } from '@/stores/commandPalette'
import { useGlobalShortcuts } from '@/features/command/useGlobalShortcuts'

const loadCommandPalette = () => import('@/features/command/CommandPalette.vue')
const CommandPalette = defineAsyncComponent(loadCommandPalette)
const ShortcutsDialog = defineAsyncComponent(() => import('@/features/command/ShortcutsDialog.vue'))

const session = useSessionStore()
const palette = useCommandPaletteStore()
useGlobalShortcuts()

// The palette carries the pinyin dictionary (~145 KB gzip): mount it on first use and
// warm the chunk when the browser is idle, so it never competes with the first paint.
const paletteActivated = ref(false)
watch(() => palette.open, open => {
  if (open) paletteActivated.value = true
})
watch(
  () => session.signedIn,
  signedIn => {
    if (!signedIn || typeof window === 'undefined') return
    const warm = () => void loadCommandPalette().catch(() => undefined)
    if ('requestIdleCallback' in window) window.requestIdleCallback(warm, { timeout: 8000 })
    else globalThis.setTimeout(warm, 4000)
  },
  { immediate: true },
)
</script>

<template>
  <TooltipProvider :delay-duration="400" :skip-delay-duration="200">
    <div class="app-root">
      <Transition name="fade" mode="out-in">
        <div v-if="!session.ready" key="boot" class="boot" role="status" aria-live="polite">
          <BrandMark :size="44" :wordmark="false" class="boot__mark" />
          <p class="boot__text">正在恢复你的工作区…</p>
        </div>
        <div v-else-if="session.bootError" key="error" class="boot" role="alert">
          <BrandMark :size="44" :wordmark="false" />
          <h1>暂时连不上服务</h1>
          <p class="boot__text">{{ session.bootError }}</p>
          <UiButton :icon="RotateCcw" @click="session.retryBoot()">重试</UiButton>
        </div>
        <RouterView v-else key="app" />
      </Transition>
      <UiToaster />
      <UiConfirmHost />
      <CommandPalette v-if="session.signedIn && paletteActivated" />
      <ShortcutsDialog />
    </div>
  </TooltipProvider>
</template>

<style scoped>
.boot__mark {
  animation: boot-pulse 1.6s var(--ease-standard) infinite;
}

.boot__text {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

@keyframes boot-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(0.94);
    opacity: 0.75;
  }
}
</style>
