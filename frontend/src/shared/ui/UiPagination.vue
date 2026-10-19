<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { pageSlots } from './pagination'

/** Numbered pager; `page` is 0-based. Phones get "3 / 12" between the arrows instead of the numbers. */
const props = withDefaults(defineProps<{ page: number; pageCount: number; disabled?: boolean; ariaLabel?: string }>(), {
  disabled: false,
  ariaLabel: '分页',
})
const emit = defineEmits<{ 'update:page': [page: number] }>()

const slots = computed(() => pageSlots(props.page, props.pageCount))
const hasPrev = computed(() => props.page > 0)
const hasNext = computed(() => props.page < props.pageCount - 1)

function go(target: number): void {
  if (props.disabled || target === props.page || target < 0 || target >= props.pageCount) return
  emit('update:page', target)
}
</script>

<template>
  <nav class="ui-pagination" :aria-label="ariaLabel" :aria-busy="disabled || undefined">
    <slot />
    <div class="ui-pagination__pages">
      <button class="ui-pagination__btn" type="button" :disabled="disabled || !hasPrev" aria-label="上一页" @click="go(page - 1)">
        <ChevronLeft :size="15" aria-hidden="true" />
      </button>
      <template v-for="slot in slots" :key="slot">
        <span v-if="typeof slot === 'string'" class="ui-pagination__gap" aria-hidden="true">…</span>
        <button
          v-else
          class="ui-pagination__btn ui-pagination__num"
          :class="{ 'is-current': slot === page }"
          type="button"
          :disabled="disabled && slot !== page"
          :aria-current="slot === page ? 'page' : undefined"
          :aria-label="`第 ${slot + 1} 页`"
          @click="go(slot)"
        >{{ slot + 1 }}</button>
      </template>
      <span class="ui-pagination__status" aria-hidden="true">{{ page + 1 }} / {{ pageCount }}</span>
      <button class="ui-pagination__btn" type="button" :disabled="disabled || !hasNext" aria-label="下一页" @click="go(page + 1)">
        <ChevronRight :size="15" aria-hidden="true" />
      </button>
    </div>
  </nav>
</template>

<style scoped>
.ui-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.ui-pagination__pages {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.ui-pagination__btn {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  display: inline-grid;
  place-items: center;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-1);
  color: var(--text-primary);
  font-size: var(--fs-sm);
  font-variant-numeric: tabular-nums;
  transition: transform var(--dur-instant) var(--ease-standard), color var(--dur-fast) var(--ease-standard),
    border-color var(--dur-fast) var(--ease-standard), background-color var(--dur-fast) var(--ease-standard);
}

.ui-pagination__btn:hover:not(:disabled):not(.is-current) {
  border-color: var(--border-strong);
  background: var(--surface-2);
}

.ui-pagination__btn:active:not(:disabled) {
  transform: scale(0.94);
}

.ui-pagination__btn:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.ui-pagination__btn.is-current {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--text-on-primary);
  font-weight: 600;
  cursor: default;
}

.ui-pagination__btn:disabled:not(.is-current) {
  opacity: 0.45;
  cursor: not-allowed;
}

.ui-pagination__gap {
  min-width: 20px;
  text-align: center;
  color: var(--text-tertiary);
}

.ui-pagination__status {
  display: none;
  min-width: 56px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 480px) {
  .ui-pagination__num,
  .ui-pagination__gap {
    display: none;
  }

  .ui-pagination__status {
    display: inline;
  }
}
</style>
