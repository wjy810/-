<script setup lang="ts">
import type { Component } from 'vue'
import {
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuPortal,
  DropdownMenuRoot,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from 'reka-ui'
import UiKbd from './UiKbd.vue'

export type MenuItem =
  | { type?: 'item'; label: string; icon?: Component; danger?: boolean; disabled?: boolean; shortcut?: string; onSelect: () => void }
  | { type: 'separator' }
  | { type: 'label'; label: string }

withDefaults(
  defineProps<{ items: MenuItem[]; align?: 'start' | 'center' | 'end'; side?: 'top' | 'bottom' | 'left' | 'right'; width?: number }>(),
  { align: 'end', side: 'bottom', width: 200 },
)
</script>

<template>
  <DropdownMenuRoot :modal="false">
    <DropdownMenuTrigger as-child>
      <slot />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent class="ui-menu" :align="align" :side="side" :side-offset="6" :collision-padding="12" :style="{ minWidth: `${width}px` }">
        <slot name="header" />
        <template v-for="(item, index) in items" :key="index">
          <DropdownMenuSeparator v-if="item.type === 'separator'" class="ui-menu__sep" />
          <DropdownMenuLabel v-else-if="item.type === 'label'" class="ui-menu__label">{{ item.label }}</DropdownMenuLabel>
          <DropdownMenuItem
            v-else
            class="ui-menu__item"
            :class="{ 'is-danger': item.danger }"
            :disabled="item.disabled"
            @select="item.onSelect()"
          >
            <component :is="item.icon" v-if="item.icon" :size="16" :stroke-width="1.9" aria-hidden="true" />
            <span class="ui-menu__text">{{ item.label }}</span>
            <UiKbd v-if="item.shortcut" :keys="item.shortcut" />
          </DropdownMenuItem>
        </template>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<style>
.ui-menu {
  z-index: var(--z-dropdown);
  max-width: min(320px, calc(100vw - 24px));
  padding: 6px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-overlay);
  box-shadow: var(--shadow-md);
  transform-origin: var(--reka-dropdown-menu-content-transform-origin);
  animation: ui-pop-in var(--dur-base) var(--ease-out);
}

.ui-menu[data-state='closed'] {
  animation: ui-dialog-fade-out var(--dur-fast) var(--ease-in) forwards;
}

.ui-menu__item {
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: var(--fs-body);
  cursor: pointer;
  user-select: none;
  outline: none;
}

.ui-menu__item svg {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.ui-menu__item[data-highlighted] {
  background: var(--surface-3);
}

.ui-menu__item[data-highlighted] svg {
  color: var(--text-primary);
}

.ui-menu__item.is-danger,
.ui-menu__item.is-danger svg {
  color: var(--color-danger-text);
}

.ui-menu__item.is-danger[data-highlighted] {
  background: var(--color-danger-soft);
}

.ui-menu__item[data-disabled] {
  opacity: 0.45;
  cursor: not-allowed;
}

.ui-menu__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ui-menu__sep {
  height: 1px;
  margin: 6px -6px;
  background: var(--border-subtle);
}

.ui-menu__label {
  padding: 6px 10px 4px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 600;
}
</style>
