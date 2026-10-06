<script setup lang="ts">
/** @deprecated Use UiDialog. Adapter preserving the historical props/events. */
import UiDialog from './UiDialog.vue'

withDefaults(defineProps<{ open: boolean; title: string; width?: number; mobileSheet?: boolean }>(), { width: 520, mobileSheet: false })
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <UiDialog :open="open" :title="title" :width="width" :mobile-sheet="mobileSheet" @close="emit('close')">
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </UiDialog>
</template>
