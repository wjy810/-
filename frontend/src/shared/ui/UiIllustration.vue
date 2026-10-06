<script setup lang="ts">
import { computed } from 'vue'
import { illustrationUrl, type IllustrationName } from './illustrations'

const props = withDefaults(defineProps<{ name: IllustrationName; size?: number; alt?: string; eager?: boolean; float?: boolean }>(), {
  size: 160,
  alt: '',
  eager: false,
  float: false,
})
const src = computed(() => illustrationUrl(props.name))
</script>

<template>
  <img
    v-if="src"
    class="ui-illustration"
    :class="{ 'is-floating': float }"
    :src="src"
    :alt="alt"
    :aria-hidden="alt ? undefined : 'true'"
    :width="size"
    :height="size"
    :loading="eager ? 'eager' : 'lazy'"
    decoding="async"
    draggable="false"
  />
</template>

<style scoped>
.ui-illustration {
  object-fit: contain;
  user-select: none;
  pointer-events: none;
}

.ui-illustration.is-floating {
  animation: jp-float 6s ease-in-out infinite;
}
</style>
