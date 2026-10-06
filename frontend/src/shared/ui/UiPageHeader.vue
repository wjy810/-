<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next'
import type { RouteLocationRaw } from 'vue-router'

withDefaults(defineProps<{ title: string; description?: string; back?: RouteLocationRaw; backLabel?: string; eyebrow?: string }>(), {
  description: '',
  back: undefined,
  backLabel: '返回',
  eyebrow: '',
})
</script>

<template>
  <header class="ui-page-header">
    <div class="ui-page-header__main">
      <RouterLink v-if="back" :to="back" class="ui-page-header__back">
        <ArrowLeft :size="15" :stroke-width="2" aria-hidden="true" />{{ backLabel }}
      </RouterLink>
      <p v-if="eyebrow" class="ui-page-header__eyebrow">{{ eyebrow }}</p>
      <div class="ui-page-header__title-row">
        <h1 class="ui-page-header__title">{{ title }}</h1>
        <slot name="title-suffix" />
      </div>
      <p v-if="description || $slots.description" class="ui-page-header__desc"><slot name="description">{{ description }}</slot></p>
    </div>
    <div v-if="$slots.actions" class="ui-page-header__actions"><slot name="actions" /></div>
  </header>
</template>

<style scoped>
.ui-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4) var(--space-6);
  flex-wrap: wrap;
}

.ui-page-header__main {
  display: grid;
  gap: 6px;
  min-width: 0;
  flex: 1 1 360px;
}

.ui-page-header__back {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 500;
}

.ui-page-header__back:hover {
  color: var(--text-primary);
}

.ui-page-header__eyebrow {
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  font-weight: 650;
  letter-spacing: 0.04em;
}

.ui-page-header__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.ui-page-header__title {
  font-size: var(--fs-h1);
  line-height: var(--lh-h1);
  font-weight: 700;
  letter-spacing: -0.02em;
}

.ui-page-header__desc {
  max-width: 680px;
  color: var(--text-secondary);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
}

.ui-page-header__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (max-width: 640px) {
  .ui-page-header__actions {
    width: 100%;
  }
}
</style>
