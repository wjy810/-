<script setup lang="ts">
/** @deprecated Use UiBadge. */
import { computed } from 'vue'
import UiBadge, { type BadgeTone } from './UiBadge.vue'

const props = withDefaults(defineProps<{ tone?: 'blue' | 'green' | 'orange' | 'red' | 'gray' | 'purple' }>(), { tone: 'gray' })
const MAP: Record<NonNullable<typeof props.tone>, BadgeTone> = {
  blue: 'primary', green: 'success', orange: 'accent', red: 'danger', gray: 'neutral', purple: 'ai',
}
const tone = computed(() => MAP[props.tone])
</script>

<template>
  <UiBadge :tone="tone"><slot /></UiBadge>
</template>
