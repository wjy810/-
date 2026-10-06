<script setup lang="ts">
import { computed } from 'vue'
import { LayoutTemplate, MessageSquareText, Palette, SquarePen } from 'lucide-vue-next'
import UiTooltip from '@/shared/ui/UiTooltip.vue'
import type { WorkbenchView } from '../types'

const props = defineProps<{ modelValue: WorkbenchView }>()
const emit = defineEmits<{ 'update:modelValue': [value: WorkbenchView] }>()

const tabs = [
  { value: 'conversation', label: '对话', icon: MessageSquareText },
  { value: 'edit', label: '编辑', icon: SquarePen },
  { value: 'design', label: '设计', icon: Palette },
  { value: 'templates', label: '模板', icon: LayoutTemplate },
] as const

const activeIndex = computed(() => Math.max(0, tabs.findIndex(tab => tab.value === props.modelValue)))

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  const next = (activeIndex.value + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length
  emit('update:modelValue', tabs[next]!.value)
  ;(event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('[role="tab"]')[next]?.focus()
}
</script>

<template>
  <nav class="workbench-tabs" role="tablist" aria-label="工作台视图" @keydown="onKeydown">
    <span class="workbench-tabs__indicator" :style="{ transform: `translateX(${activeIndex * 100}%)` }" aria-hidden="true" />
    <UiTooltip v-for="(tab, index) in tabs" :key="tab.value" :content="tab.label" :shortcut="['alt', String(index + 1)]" side="bottom">
      <button
        type="button"
        role="tab"
        class="workbench-tabs__tab"
        :aria-selected="modelValue === tab.value"
        :tabindex="modelValue === tab.value ? 0 : -1"
        @click="emit('update:modelValue', tab.value)"
      >
        <component :is="tab.icon" :size="15" :stroke-width="1.9" aria-hidden="true" />{{ tab.label }}
      </button>
    </UiTooltip>
  </nav>
</template>

<style scoped>
.workbench-tabs {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 4px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  box-shadow: inset 0 0 0 1px var(--border-subtle);
}

.workbench-tabs__indicator {
  position: absolute;
  z-index: -1;
  inset: 4px auto 4px 4px;
  width: calc((100% - 8px) / 4);
  border-radius: calc(var(--radius-md) - 3px);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  transition: transform var(--dur-slow) var(--ease-spring);
}

.workbench-tabs__tab {
  min-width: 0;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: calc(var(--radius-md) - 3px);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 550;
  transition: color var(--dur-fast) var(--ease-standard);
}

.workbench-tabs__tab:hover {
  color: var(--text-primary);
}

.workbench-tabs__tab[aria-selected='true'] {
  color: var(--color-primary-text);
  font-weight: 650;
}

.workbench-tabs__tab:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

@media (prefers-reduced-motion: reduce) {
  .workbench-tabs__indicator {
    transition: none;
  }
}
</style>
