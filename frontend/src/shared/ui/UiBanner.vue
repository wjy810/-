<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, CheckCircle2, Info, Sparkles, X, XCircle } from 'lucide-vue-next'

const props = withDefaults(defineProps<{ tone?: 'info' | 'success' | 'warning' | 'danger' | 'ai'; title?: string; dismissible?: boolean }>(), {
  tone: 'info',
  title: '',
  dismissible: false,
})
defineEmits<{ dismiss: [] }>()
const icon = computed(() => ({ info: Info, success: CheckCircle2, warning: AlertTriangle, danger: XCircle, ai: Sparkles })[props.tone])
</script>

<template>
  <div class="ui-banner" :class="`ui-banner--${tone}`" :role="tone === 'danger' || tone === 'warning' ? 'alert' : 'status'">
    <component :is="icon" class="ui-banner__icon" :size="17" :stroke-width="2" aria-hidden="true" />
    <div class="ui-banner__body">
      <strong v-if="title" class="ui-banner__title">{{ title }}</strong>
      <div class="ui-banner__text"><slot /></div>
    </div>
    <div v-if="$slots.actions" class="ui-banner__actions"><slot name="actions" /></div>
    <button v-if="dismissible" class="ui-banner__close" type="button" aria-label="关闭提示" @click="$emit('dismiss')"><X :size="15" /></button>
  </div>
</template>

<style scoped>
.ui-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-size: var(--fs-sm);
  line-height: var(--lh-sm);
}

.ui-banner__icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.ui-banner__body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}

.ui-banner__title {
  font-weight: 650;
}

.ui-banner__text {
  color: var(--text-secondary);
}

.ui-banner__actions {
  display: flex;
  gap: 8px;
  align-self: center;
}

.ui-banner__close {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-xs);
  color: var(--text-tertiary);
}

.ui-banner__close:hover {
  background: rgba(0, 0, 0, 0.05);
}

.ui-banner--info {
  background: var(--color-info-soft);
  border-color: var(--color-primary-border);
}

.ui-banner--info .ui-banner__icon,
.ui-banner--info .ui-banner__title {
  color: var(--color-info-text);
}

.ui-banner--success {
  background: var(--color-success-soft);
  border-color: color-mix(in srgb, var(--color-success) 28%, transparent);
}

.ui-banner--success .ui-banner__icon,
.ui-banner--success .ui-banner__title {
  color: var(--color-success-text);
}

.ui-banner--warning {
  background: var(--color-warning-soft);
  border-color: color-mix(in srgb, var(--color-warning) 30%, transparent);
}

.ui-banner--warning .ui-banner__icon,
.ui-banner--warning .ui-banner__title {
  color: var(--color-warning-text);
}

.ui-banner--danger {
  background: var(--color-danger-soft);
  border-color: color-mix(in srgb, var(--color-danger) 28%, transparent);
}

.ui-banner--danger .ui-banner__icon,
.ui-banner--danger .ui-banner__title {
  color: var(--color-danger-text);
}

.ui-banner--ai {
  background: var(--gradient-ai-soft);
  border-color: var(--color-primary-border);
}

.ui-banner--ai .ui-banner__icon {
  color: var(--color-accent);
}
</style>
