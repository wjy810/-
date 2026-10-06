<script setup lang="ts">
import { computed } from 'vue'
import { Check, CloudOff, LoaderCircle } from 'lucide-vue-next'
import type { DraftState } from '../types'

const props = defineProps<{ state: DraftState }>()
const emit = defineEmits<{ retry: [] }>()

const label = computed(() => ({
  idle: '已同步',
  waiting: '有未保存的修改',
  saving: '正在保存…',
  saved: '已保存',
  error: '保存失败',
} as const)[props.state])
</script>

<template>
  <span class="save-indicator" :class="`is-${state}`" role="status" aria-live="polite">
    <LoaderCircle v-if="state === 'saving'" class="save-indicator__spin" :size="13" aria-hidden="true" />
    <CloudOff v-else-if="state === 'error'" :size="13" aria-hidden="true" />
    <span v-else-if="state === 'waiting'" class="save-indicator__dot" aria-hidden="true" />
    <Check v-else :size="13" aria-hidden="true" />
    <span>{{ label }}</span>
    <button v-if="state === 'error'" type="button" class="save-indicator__retry" @click="emit('retry')">重试</button>
  </span>
</template>

<style scoped>
.save-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease-standard);
}

.save-indicator.is-saved {
  color: var(--color-success-text);
}

.save-indicator.is-waiting {
  color: var(--color-warning-text);
}

.save-indicator.is-error {
  color: var(--color-danger-text);
  font-weight: 600;
}

.save-indicator__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-warning);
  animation: save-pulse 1.4s var(--ease-standard) infinite;
}

.save-indicator__spin {
  animation: save-spin 0.9s linear infinite;
}

.save-indicator__retry {
  padding: 1px 7px;
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-full);
  color: var(--color-danger-text);
  font-size: 11px;
  font-weight: 600;
}

.save-indicator__retry:hover {
  background: var(--color-danger-soft);
}

@keyframes save-pulse {
  50% {
    opacity: 0.35;
  }
}

@keyframes save-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .save-indicator__dot,
  .save-indicator__spin {
    animation: none;
  }
}
</style>
