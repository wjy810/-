<script setup lang="ts">
import UiIllustration from '@/shared/ui/UiIllustration.vue'

withDefaults(defineProps<{ active?: boolean; size?: number }>(), { active: false, size: 32 })
</script>

<template>
  <span class="assistant-avatar" :class="{ 'is-active': active }" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <UiIllustration name="ai-orb" :size="size" eager />
  </span>
</template>

<style scoped>
.assistant-avatar {
  position: relative;
  display: inline-grid;
  place-items: center;
  flex: none;
  border-radius: 50%;
  background: var(--gradient-ai-soft);
  box-shadow: 0 0 0 1px var(--border-subtle);
}

.assistant-avatar :deep(img) {
  width: 118%;
  height: 118%;
  object-fit: contain;
}

.assistant-avatar.is-active::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: var(--gradient-ai) border-box;
  mask: linear-gradient(#000 0 0) padding-box exclude, linear-gradient(#000 0 0);
  animation: assistant-spin 1.6s linear infinite;
}

@keyframes assistant-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-avatar.is-active::after {
    animation: none;
  }
}

:global(html[data-motion='reduce']) .assistant-avatar.is-active::after {
  animation: none;
}
</style>
