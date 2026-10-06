<script setup lang="ts">
import UiIllustration from './UiIllustration.vue'
import type { IllustrationName } from './illustrations'

withDefaults(
  defineProps<{ title: string; description?: string; illustration?: IllustrationName; size?: 'sm' | 'md' | 'lg'; bordered?: boolean }>(),
  { description: '', illustration: 'empty-search', size: 'md', bordered: false },
)
</script>

<template>
  <section class="ui-empty" :class="[`ui-empty--${size}`, { 'is-bordered': bordered }]">
    <UiIllustration :name="illustration" :size="size === 'sm' ? 96 : size === 'lg' ? 200 : 150" class="ui-empty__art" float />
    <h3 class="ui-empty__title">{{ title }}</h3>
    <p v-if="description" class="ui-empty__desc">{{ description }}</p>
    <div v-if="$slots.default" class="ui-empty__actions"><slot /></div>
  </section>
</template>

<style scoped>
.ui-empty {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: var(--space-12) var(--space-6);
  text-align: center;
  animation: jp-rise-in var(--dur-slow) var(--ease-out) both;
}

.ui-empty.is-bordered {
  border: 1.5px dashed var(--border-default);
  border-radius: var(--radius-xl);
  background: var(--surface-2);
}

.ui-empty--sm {
  padding: var(--space-6) var(--space-4);
}

.ui-empty__art {
  margin-bottom: var(--space-2);
  filter: drop-shadow(0 12px 18px rgba(74, 68, 217, 0.12));
}

.ui-empty__title {
  font-size: var(--fs-h3);
  line-height: var(--lh-h3);
  font-weight: 650;
}

.ui-empty--lg .ui-empty__title {
  font-size: var(--fs-h2);
  line-height: var(--lh-h2);
}

.ui-empty__desc {
  max-width: 420px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: var(--lh-sm);
}

.ui-empty__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
</style>
