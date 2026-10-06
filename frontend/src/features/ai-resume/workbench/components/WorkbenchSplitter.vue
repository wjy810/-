<script setup lang="ts">
defineProps<{ value: number; min: number; max: number; dragging: boolean }>()
</script>

<template>
  <div
    class="wb-splitter"
    :class="{ 'is-dragging': dragging }"
    role="separator"
    aria-label="调整编辑区与预览区宽度"
    aria-orientation="vertical"
    :aria-valuenow="Math.round(value)"
    :aria-valuemin="Math.round(min)"
    :aria-valuemax="Math.round(max)"
    tabindex="0"
    title="拖动调整宽度，双击恢复默认（← → 键微调）"
  >
    <span class="wb-splitter__grip" aria-hidden="true"><i /><i /><i /></span>
  </div>
</template>

<style scoped>
.wb-splitter {
  position: relative;
  z-index: 3;
  width: 9px;
  margin: 0 -4px;
  display: grid;
  place-items: center;
  cursor: col-resize;
  touch-action: none;
  outline: none;
}

.wb-splitter::before {
  content: '';
  position: absolute;
  inset: 0 4px;
  background: var(--border-subtle);
  transition: background-color var(--dur-fast), inset var(--dur-fast);
}

.wb-splitter:hover::before,
.wb-splitter:focus-visible::before,
.wb-splitter.is-dragging::before {
  inset: 0 3px;
  background: var(--color-primary);
}

.wb-splitter__grip {
  position: relative;
  width: 12px;
  height: 40px;
  display: grid;
  place-content: center;
  gap: 3px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  opacity: 0;
  transform: scale(0.9);
  transition: opacity var(--dur-fast), transform var(--dur-fast) var(--ease-spring);
}

.wb-splitter__grip i {
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: var(--text-tertiary);
}

.wb-splitter:hover .wb-splitter__grip,
.wb-splitter:focus-visible .wb-splitter__grip,
.wb-splitter.is-dragging .wb-splitter__grip {
  opacity: 1;
  transform: none;
}
</style>
