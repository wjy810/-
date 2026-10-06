<script setup lang="ts">
/**
 * One of skeleton / error with retry / empty / content for a page's primary data (X-1).
 * An error with no data shows the error state, never the empty state; an error after an earlier
 * success keeps the content and shows a compact notice above it.
 */
import { computed } from 'vue'
import { RotateCcw } from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import UiBanner from './UiBanner.vue'
import UiButton from './UiButton.vue'
import UiErrorState from './UiErrorState.vue'
import UiSkeleton from './UiSkeleton.vue'

const props = withDefaults(defineProps<{
  loading?: boolean
  error?: unknown
  /** Data has been loaded at least once. */
  loaded?: boolean
  /** Loaded data has nothing to show. */
  empty?: boolean
  errorTitle?: string
  compact?: boolean
}>(), { loading: false, error: null, loaded: false, empty: false, errorTitle: '加载没有成功', compact: false })
defineEmits<{ retry: [] }>()

const state = computed<'skeleton' | 'error' | 'empty' | 'content'>(() => {
  if (!props.loaded) {
    if (props.error) return 'error'
    return 'skeleton'
  }
  return props.empty ? 'empty' : 'content'
})
const staleError = computed(() => props.loaded && Boolean(props.error))
</script>

<template>
  <div class="page-state" :aria-busy="loading || undefined">
    <template v-if="state === 'skeleton'">
      <slot name="skeleton">
        <div class="page-state__skeleton"><UiSkeleton height="22px" width="36%" /><UiSkeleton :lines="4" /><UiSkeleton height="160px" radius="var(--radius-lg)" /></div>
      </slot>
    </template>
    <UiErrorState v-else-if="state === 'error'" :error="error" :title="errorTitle" :compact="compact" :retrying="loading" @retry="$emit('retry')" />
    <template v-else>
      <UiBanner v-if="staleError" tone="warning" class="page-state__stale">
        刷新失败：{{ errorMessage(error) }}。下面是上次读取的内容。
        <template #actions><UiButton size="sm" variant="secondary" :icon="RotateCcw" :pending="loading" @click="$emit('retry')">重试</UiButton></template>
      </UiBanner>
      <slot v-if="state === 'empty'" name="empty" />
      <slot v-else />
    </template>
  </div>
</template>

<style scoped>
.page-state__skeleton { display: grid; gap: var(--space-4); padding: var(--space-2) 0; }
.page-state__stale { margin-bottom: var(--space-4); }
</style>
