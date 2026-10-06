<script setup lang="ts">
import { computed } from 'vue'
import type { RichText } from '../../model/types'

const props = withDefaults(defineProps<{ value: RichText; paragraphs?: boolean; from?: number; to?: number }>(), {
  paragraphs: true, from: 0, to: Number.POSITIVE_INFINITY,
})
const bullets = computed(() => props.value.bullets.slice(props.from, props.to))
</script>

<template>
  <template v-if="paragraphs">
    <p v-for="(line, index) in value.paragraphs" :key="`p${index}`" class="rr-p">
      <template v-for="(part, i) in line" :key="i"><strong v-if="part.bold">{{ part.text }}</strong><template v-else>{{ part.text }}</template></template>
    </p>
  </template>
  <ul v-if="bullets.length" class="rr-bullets">
    <li v-for="(line, index) in bullets" :key="`b${index}`">
      <template v-for="(part, i) in line" :key="i"><strong v-if="part.bold">{{ part.text }}</strong><template v-else>{{ part.text }}</template></template>
    </li>
  </ul>
</template>
