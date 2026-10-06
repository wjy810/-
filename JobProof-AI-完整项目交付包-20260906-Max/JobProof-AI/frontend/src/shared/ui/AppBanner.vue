<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'ink' | 'ok' | 'warn' | 'bad'
  }>(),
  { tone: 'ink' },
)
</script>

<template>
  <div class="banner" :class="`banner--${tone}`" role="status">
    <slot />
  </div>
</template>
