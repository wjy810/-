<script setup lang="ts">
withDefaults(
  defineProps<{ width?: string; height?: string; radius?: string; lines?: number; circle?: boolean }>(),
  { width: '100%', height: '14px', radius: 'var(--radius-xs)', lines: 1, circle: false },
)
</script>

<template>
  <span v-if="lines > 1" class="ui-skeleton-lines" aria-hidden="true">
    <span
      v-for="line in lines"
      :key="line"
      class="ui-skeleton"
      :style="{ width: line === lines ? '62%' : width, height, borderRadius: radius }"
    />
  </span>
  <span
    v-else
    class="ui-skeleton"
    aria-hidden="true"
    :style="{ width: circle ? height : width, height, borderRadius: circle ? '50%' : radius }"
  />
</template>

<style scoped>
.ui-skeleton-lines {
  display: grid;
  gap: 8px;
  width: 100%;
}

.ui-skeleton {
  position: relative;
  display: block;
  overflow: hidden;
  background: var(--skeleton-base);
}

.ui-skeleton::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, var(--skeleton-shine), transparent);
  animation: jp-shimmer 1.4s linear infinite;
}
</style>
