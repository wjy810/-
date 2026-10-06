<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'blue' | 'green' | 'orange' | 'red' | 'gray' | 'purple'
  }>(),
  { tone: 'gray' },
)
</script>

<template>
  <span class="tag" :class="`tag--${tone}`"><slot /></span>
</template>
