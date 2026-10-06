<script setup lang="ts" generic="T extends string">
import type { Component } from 'vue'
import { TabsIndicator, TabsList, TabsRoot, TabsTrigger } from 'reka-ui'

export type TabItem<V extends string = string> = { value: V; label: string; icon?: Component; count?: number | null }

defineProps<{ modelValue: T; items: TabItem<T>[]; ariaLabel?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: T] }>()
</script>

<template>
  <TabsRoot :model-value="modelValue" activation-mode="manual" @update:model-value="emit('update:modelValue', $event as T)">
    <TabsList class="ui-tabs" :aria-label="ariaLabel">
      <TabsIndicator class="ui-tabs__indicator" />
      <TabsTrigger v-for="item in items" :key="item.value" :value="item.value" class="ui-tabs__trigger">
        <component :is="item.icon" v-if="item.icon" :size="15" :stroke-width="1.9" aria-hidden="true" />
        <span>{{ item.label }}</span>
        <span v-if="item.count != null" class="ui-tabs__count">{{ item.count }}</span>
      </TabsTrigger>
    </TabsList>
  </TabsRoot>
</template>

<style>
.ui-tabs {
  position: relative;
  display: flex;
  gap: var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
  overflow-x: auto;
  scrollbar-width: none;
}

.ui-tabs::-webkit-scrollbar {
  display: none;
}

.ui-tabs__trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 2px;
  color: var(--text-secondary);
  font-size: var(--fs-body);
  font-weight: 500;
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease-standard);
}

.ui-tabs__trigger:hover {
  color: var(--text-primary);
}

.ui-tabs__trigger[data-state='active'] {
  color: var(--text-primary);
  font-weight: 600;
}

.ui-tabs__count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--surface-3);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.ui-tabs__trigger[data-state='active'] .ui-tabs__count {
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
}

.ui-tabs__indicator {
  position: absolute;
  left: 0;
  bottom: -1px;
  height: 2px;
  width: var(--reka-tabs-indicator-size);
  transform: translateX(var(--reka-tabs-indicator-position));
  border-radius: 2px;
  background: var(--color-primary);
  transition: transform var(--dur-base) var(--ease-out), width var(--dur-base) var(--ease-out);
}
</style>
