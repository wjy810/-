<script setup lang="ts" generic="T extends string">
import type { Component } from 'vue'
import { ToggleGroupItem, ToggleGroupRoot } from 'reka-ui'
import UiTooltip from './UiTooltip.vue'

export type SegmentItem<V extends string = string> = { value: V; label: string; icon?: Component; shortcut?: string; count?: number | null }

withDefaults(defineProps<{ modelValue: T; items: SegmentItem<T>[]; ariaLabel?: string; size?: 'sm' | 'md'; block?: boolean; iconOnly?: boolean }>(), {
  ariaLabel: undefined,
  size: 'md',
  block: false,
  iconOnly: false,
})
const emit = defineEmits<{ 'update:modelValue': [value: T] }>()

function onUpdate(value: unknown): void {
  if (typeof value === 'string' && value) emit('update:modelValue', value as T)
}
</script>

<template>
  <ToggleGroupRoot
    type="single"
    :model-value="modelValue"
    class="ui-seg"
    :class="[`ui-seg--${size}`, { 'is-block': block }]"
    :aria-label="ariaLabel"
    @update:model-value="onUpdate"
  >
    <template v-for="item in items" :key="item.value">
      <!-- Tooltip triggers write their own data-state, so only wrap items that need a tooltip. -->
      <UiTooltip v-if="iconOnly || item.shortcut" :content="item.label" :shortcut="item.shortcut">
        <span class="ui-seg__tip">
          <ToggleGroupItem :value="item.value" class="ui-seg__item" :aria-label="item.label">
            <component :is="item.icon" v-if="item.icon" :size="15" :stroke-width="1.9" aria-hidden="true" />
            <span v-if="!iconOnly">{{ item.label }}</span>
          </ToggleGroupItem>
        </span>
      </UiTooltip>
      <ToggleGroupItem v-else :value="item.value" class="ui-seg__item" :aria-label="item.label">
        <component :is="item.icon" v-if="item.icon" :size="15" :stroke-width="1.9" aria-hidden="true" />
        <span>{{ item.label }}</span>
        <span v-if="item.count != null" class="ui-seg__count">{{ item.count }}</span>
      </ToggleGroupItem>
    </template>
  </ToggleGroupRoot>
</template>

<style>
.ui-seg {
  justify-self: start;
  align-self: start;
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: var(--radius-md);
  background: var(--surface-3);
  box-shadow: inset 0 0 0 1px var(--border-subtle);
}

.ui-seg.is-block {
  justify-self: stretch;
  display: flex;
  width: 100%;
}

.ui-seg.is-block .ui-seg__item {
  flex: 1;
}

.ui-seg__item {
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 12px;
  border-radius: calc(var(--radius-md) - 3px);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 550;
  white-space: nowrap;
  transition: background-color var(--dur-base) var(--ease-out), color var(--dur-fast), box-shadow var(--dur-base) var(--ease-out);
}

.ui-seg--sm .ui-seg__item {
  height: 26px;
  padding: 0 10px;
  font-size: var(--fs-xs);
}

.ui-seg__item:hover {
  color: var(--text-primary);
}

.ui-seg__tip {
  display: inline-flex;
}

.ui-seg.is-block .ui-seg__tip {
  flex: 1;
}

.ui-seg.is-block .ui-seg__tip .ui-seg__item {
  width: 100%;
}

.ui-seg__item[data-state='on'],
.ui-seg__item[aria-pressed='true'] {
  background: var(--surface-1);
  color: var(--text-primary);
  box-shadow: var(--shadow-xs), 0 0 0 1px var(--border-subtle);
}

.ui-seg__count {
  color: var(--text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
</style>
