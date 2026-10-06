<script setup lang="ts">
import { computed } from 'vue'
import { formatShortcut } from '@/shared/lib/keyboard'

const props = defineProps<{ keys: string | string[] }>()
const parts = computed(() => formatShortcut(props.keys))
</script>

<template>
  <span class="ui-kbd-group" :aria-label="parts.join(' ')">
    <kbd v-for="part in parts" :key="part" class="ui-kbd">{{ part }}</kbd>
  </span>
</template>

<style scoped>
.ui-kbd-group {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.ui-kbd {
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  display: inline-grid;
  place-items: center;
  border: 1px solid var(--border-default);
  border-bottom-width: 2px;
  border-radius: 5px;
  background: var(--surface-2);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}
</style>
