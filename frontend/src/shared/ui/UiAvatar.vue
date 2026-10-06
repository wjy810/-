<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ name?: string | null; src?: string | null; size?: number }>(), {
  name: '',
  src: null,
  size: 32,
})

const PALETTE = [
  ['#e4e4fd', '#3c36b6'],
  ['#ffe9d9', '#ae461c'],
  ['#e6f6ef', '#0b7451'],
  ['#efe6fd', '#6b3fc7'],
  ['#fdf4df', '#94600a'],
]

const initial = computed(() => (props.name || '?').trim().charAt(0).toUpperCase() || '?')
const colors = computed(() => {
  let hash = 0
  for (const char of props.name || '') hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return PALETTE[hash % PALETTE.length]
})
</script>

<template>
  <span
    class="ui-avatar"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.42)}px`, background: colors[0], color: colors[1] }"
    aria-hidden="true"
  >
    <img v-if="src" :src="src" alt="" />
    <template v-else>{{ initial }}</template>
  </span>
</template>

<style scoped>
.ui-avatar {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  font-weight: 650;
  line-height: 1;
  user-select: none;
}

.ui-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
