<script setup lang="ts">
import type { BlockPart } from '../../layout/blocks'
import RichTextView from './RichTextView.vue'

defineProps<{ part: Extract<BlockPart, { type: 'timeline' }> }>()
</script>

<template>
  <article class="rr-entry" :class="{ 'rr-entry--cont': !part.head }">
    <template v-if="part.head">
      <!-- DOM order = PDF text order: title, dates, meta, body (grid areas decide the visual placement). -->
      <h4 v-if="part.item.title" class="rr-entry__title">{{ part.item.title }}</h4>
      <p v-if="part.item.dates" class="rr-entry__date">
        <span class="rr-entry__start">{{ part.item.start || part.item.dates }}</span>
        <template v-if="part.item.start && part.item.end"><span class="rr-entry__sep"> – </span><span class="rr-entry__end">{{ part.item.end }}</span></template>
      </p>
      <p v-if="part.item.subtitle || part.item.location" class="rr-entry__meta">
        <span v-if="part.item.subtitle" class="rr-entry__subtitle">{{ part.item.subtitle }}</span>
        <span v-if="part.item.location" class="rr-entry__location">{{ part.item.location }}</span>
      </p>
    </template>
    <div class="rr-entry__body">
      <RichTextView :value="part.item.body" :paragraphs="part.paragraphs" :from="part.from" :to="part.to" />
    </div>
  </article>
</template>
