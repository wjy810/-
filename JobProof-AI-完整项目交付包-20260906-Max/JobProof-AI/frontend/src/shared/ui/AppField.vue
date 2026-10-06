<script setup lang="ts">
defineProps<{
  id: string
  label: string
  error?: string | null
  hint?: string
}>()
</script>

<template>
  <label class="field" :for="id">
    <span class="field__label">{{ label }}</span>
    <slot />
    <span v-if="error" class="field__error" role="alert">{{ error }}</span>
    <span v-else-if="hint" class="field__hint">{{ hint }}</span>
  </label>
</template>
