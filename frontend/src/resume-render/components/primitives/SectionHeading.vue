<script setup lang="ts">
defineProps<{ title: string; sectionKey: string; index: number }>()
</script>

<template>
  <h3 class="rr-heading" :data-section-key="sectionKey">
    <span class="rr-heading__index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
    <span class="rr-heading__text">{{ title }}</span>
  </h3>
</template>
