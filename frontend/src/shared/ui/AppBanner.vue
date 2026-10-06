<script setup lang="ts">
/** @deprecated Use UiBanner. */
import { computed } from 'vue'
import UiBanner from './UiBanner.vue'

const props = withDefaults(defineProps<{ tone?: 'ink' | 'ok' | 'warn' | 'bad' }>(), { tone: 'ink' })
const tone = computed(() => ({ ink: 'info', ok: 'success', warn: 'warning', bad: 'danger' } as const)[props.tone])
</script>

<template>
  <UiBanner :tone="tone"><slot /></UiBanner>
</template>
