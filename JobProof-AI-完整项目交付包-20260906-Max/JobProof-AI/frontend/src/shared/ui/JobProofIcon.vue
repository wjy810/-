<script setup lang="ts">
import { computed } from 'vue'
import spriteUrl from '@/assets/icons/jobproof-icons-sprite.svg'
import { jobProofIconNames, type JobProofIconName } from './jobProofIcons'

const props = withDefaults(
  defineProps<{
    name: JobProofIconName
    size?: number | string
  }>(),
  { size: 20 },
)

const href = computed(() => jobProofIconNames.has(props.name) ? `${spriteUrl}#${props.name}` : '')
</script>

<template>
  <svg
    v-if="href"
    class="jobproof-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
    :width="size"
    :height="size"
    :data-jobproof-icon="name"
    aria-hidden="true"
    focusable="false"
  >
    <use :href="href" />
  </svg>
</template>

<style scoped>
.jobproof-icon {
  flex: 0 0 auto;
  display: inline-block;
  overflow: visible;
  vertical-align: middle;
}
</style>
