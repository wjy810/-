<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import type { IconName } from './icons'

withDefaults(
  defineProps<{
    text: string
    hint?: string
    icon?: IconName
  }>(),
  { icon: 'inbox' },
)
</script>

<template>
  <div class="empty">
    <AppIcon :name="icon" :size="28" />
    <p>{{ text }}</p>
    <p v-if="hint" class="fine">{{ hint }}</p>
    <slot />
  </div>
</template>
