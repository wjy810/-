<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { toast, toastItems, type ToastTone } from './toast'

const iconByTone: Record<ToastTone, 'check-circle' | 'alert-circle' | 'info'> = {
  success: 'check-circle',
  error: 'alert-circle',
  warning: 'alert-circle',
  info: 'info',
}

const visibleItems = computed(() => [...toastItems])
</script>

<template>
  <div class="app-toast-viewport" aria-live="polite" aria-relevant="additions removals">
    <TransitionGroup name="app-toast" tag="div" class="app-toast-stack">
      <article
        v-for="item in visibleItems"
        :key="item.id"
        class="app-toast-item"
        :class="`app-toast-item--${item.tone}`"
        :role="item.tone === 'error' || item.tone === 'warning' ? 'alert' : 'status'"
      >
        <span class="app-toast-item__icon"><AppIcon :name="iconByTone[item.tone]" :size="17" /></span>
        <p>{{ item.message }}</p>
        <button type="button" aria-label="关闭通知" title="关闭" @click="toast.dismiss(item.id)">
          <AppIcon name="x" :size="15" />
        </button>
      </article>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.app-toast-viewport{position:fixed;z-index:120;top:76px;left:50%;width:min(680px,calc(100vw - 32px));pointer-events:none;transform:translateX(-50%)}
.app-toast-stack{display:grid;gap:8px}
.app-toast-item{min-width:0;min-height:48px;padding:10px 10px 10px 13px;display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:10px;border:1px solid;border-radius:8px;background:#fff;box-shadow:0 14px 34px rgba(25,45,74,.16);pointer-events:auto}
.app-toast-item__icon{width:28px;height:28px;display:grid;place-items:center;border-radius:7px}
.app-toast-item p{margin:0;overflow-wrap:anywhere;font-size:13px;line-height:1.55;letter-spacing:0}
.app-toast-item button{width:30px;height:30px;display:grid;place-items:center;border:0;border-radius:6px;color:inherit;background:transparent;cursor:pointer}
.app-toast-item button:hover{background:rgba(15,23,42,.06)}
.app-toast-item button:focus-visible{outline:2px solid currentColor;outline-offset:1px}
.app-toast-item--success{border-color:#a7dfbf;color:#087848}.app-toast-item--success .app-toast-item__icon{background:#e9f8f0}
.app-toast-item--error{border-color:#f5bfc5;color:#b42332}.app-toast-item--error .app-toast-item__icon{background:#fff0f2}
.app-toast-item--warning{border-color:#f1d199;color:#9a5b0b}.app-toast-item--warning .app-toast-item__icon{background:#fff7e8}
.app-toast-item--info{border-color:#bcd3f6;color:#1859aa}.app-toast-item--info .app-toast-item__icon{background:#edf5ff}
.app-toast-enter-active,.app-toast-leave-active,.app-toast-move{transition:opacity 180ms ease,transform 220ms cubic-bezier(.2,.8,.2,1)}
.app-toast-enter-from,.app-toast-leave-to{opacity:0;transform:translateY(-8px)}
@media(max-width:640px){.app-toast-viewport{top:calc(72px + env(safe-area-inset-top));width:calc(100vw - 24px)}.app-toast-item{align-items:start}.app-toast-item__icon{margin-top:1px}}
@media(prefers-reduced-motion:reduce){.app-toast-enter-active,.app-toast-leave-active,.app-toast-move{transition:none}.app-toast-enter-from,.app-toast-leave-to{transform:none}}
</style>
