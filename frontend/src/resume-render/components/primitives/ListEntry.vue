<script setup lang="ts">
import type { ListItem } from '../../model/types'
import RichTextView from './RichTextView.vue'

defineProps<{ item: ListItem }>()
</script>

<template>
  <div class="rr-list-item">
    <p class="rr-list-item__line">
      <span class="rr-list-item__title">{{ item.title }}</span>
      <span v-if="item.detail" class="rr-list-item__detail">{{ item.detail }}</span>
      <span v-if="item.date" class="rr-list-item__date">{{ item.date }}</span>
    </p>
    <div v-if="item.body.paragraphs.length || item.body.bullets.length" class="rr-list-item__body"><RichTextView :value="item.body" /></div>
  </div>
</template>
