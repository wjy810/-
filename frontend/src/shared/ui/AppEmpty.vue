<script setup lang="ts">
/** @deprecated Use UiEmptyState. Maps legacy icon names to illustrations. */
import { computed } from 'vue'
import UiEmptyState from './UiEmptyState.vue'
import type { IllustrationName } from './illustrations'
import type { IconName } from './icons'

const props = withDefaults(defineProps<{ text: string; hint?: string; icon?: IconName }>(), { hint: '', icon: 'inbox' })
const ILLUSTRATION: Partial<Record<IconName, IllustrationName>> = {
  'file-text': 'empty-resume', folder: 'empty-folder', bell: 'empty-notification', target: 'empty-match',
  mic: 'empty-interview', search: 'empty-search', grid: 'empty-templates', 'alert-circle': 'error-plane',
}
const illustration = computed<IllustrationName>(() => ILLUSTRATION[props.icon] ?? 'empty-search')
</script>

<template>
  <UiEmptyState :title="text" :description="hint" :illustration="illustration" size="sm"><slot /></UiEmptyState>
</template>
