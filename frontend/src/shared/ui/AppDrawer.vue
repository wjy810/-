<script setup lang="ts">
/** @deprecated Use UiDrawer. */
import UiDrawer from './UiDrawer.vue'

withDefaults(defineProps<{ open: boolean; title: string; width?: number }>(), { width: 480 })
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <UiDrawer :open="open" :title="title" :width="width" @close="emit('close')">
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </UiDrawer>
</template>
