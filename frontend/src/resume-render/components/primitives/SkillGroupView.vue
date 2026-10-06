<script setup lang="ts">
import type { SkillGroup } from '../../model/types'
import RichTextView from './RichTextView.vue'

defineProps<{ group: SkillGroup; separator: string }>()
</script>

<template>
  <div class="rr-skill">
    <span v-if="group.name" class="rr-skill__name">{{ group.name }}</span>
    <span v-if="group.items.length" class="rr-skill__items">
      <template v-for="(item, index) in group.items" :key="index"><span class="rr-skill__item">{{ item }}</span><span v-if="index < group.items.length - 1" class="rr-skill__sep">{{ separator }}</span></template>
    </span>
    <div v-if="group.body.paragraphs.length || group.body.bullets.length" class="rr-skill__note"><RichTextView :value="group.body" /></div>
  </div>
</template>
