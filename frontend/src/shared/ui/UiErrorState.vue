<script setup lang="ts">
import { computed } from 'vue'
import { RotateCcw } from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import UiButton from './UiButton.vue'
import UiIllustration from './UiIllustration.vue'

const props = withDefaults(defineProps<{ error?: unknown; title?: string; compact?: boolean; retrying?: boolean }>(), {
  error: undefined,
  title: '加载没有成功',
  compact: false,
  retrying: false,
})
defineEmits<{ retry: [] }>()

const message = computed(() => (props.error ? errorMessage(props.error) : '请检查网络后重试。'))
const requestId = computed(() => {
  const candidate = props.error as { requestId?: string } | null
  return candidate && typeof candidate.requestId === 'string' ? candidate.requestId : ''
})
</script>

<template>
  <section class="ui-error" :class="{ 'is-compact': compact }" role="alert">
    <UiIllustration v-if="!compact" name="error-plane" :size="132" />
    <h3 class="ui-error__title">{{ title }}</h3>
    <p class="ui-error__msg">{{ message }}</p>
    <UiButton variant="secondary" size="sm" :icon="RotateCcw" :pending="retrying" @click="$emit('retry')">重试</UiButton>
    <p v-if="requestId" class="ui-error__rid">请求编号 <code>{{ requestId }}</code></p>
  </section>
</template>

<style scoped>
.ui-error {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: var(--space-10) var(--space-6);
  text-align: center;
}

.ui-error.is-compact {
  padding: var(--space-5);
  border: 1px solid color-mix(in srgb, var(--color-danger) 22%, transparent);
  border-radius: var(--radius-lg);
  background: var(--color-danger-soft);
}

.ui-error__title {
  font-size: var(--fs-h3);
  font-weight: 650;
}

.ui-error__msg {
  max-width: 440px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  margin-bottom: var(--space-2);
}

.ui-error__rid {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.ui-error__rid code {
  user-select: all;
}
</style>
