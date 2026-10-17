<script setup lang="ts">
import { computed } from 'vue'
import type { Block } from '../layout/blocks'
import ListEntry from './primitives/ListEntry.vue'
import RichTextView from './primitives/RichTextView.vue'
import SectionHeading from './primitives/SectionHeading.vue'
import SkillGroupView from './primitives/SkillGroupView.vue'
import TimelineEntry from './primitives/TimelineEntry.vue'
import { skillLabelEm } from '../model/textWidth'

const props = defineProps<{ block: Block; locale: 'zh-CN' | 'en' }>()
const part = computed(() => props.block.part)
const separator = computed(() => (props.locale === 'en' ? ', ' : '、'))
/** Skill rows of one section line their items up behind the widest group name. */
const skillLabel = computed(() => {
  const section = props.block.section
  return section.kind === 'skills' ? skillLabelEm(section.groups.map(group => group.name), props.locale) : null
})
</script>

<template>
  <div
    class="rr-block"
    :class="[`rr-block--${part.type}`, {
      'rr-block--start': block.withHeading,
      'rr-block--cont': part.type === 'timeline' && !part.head,
      'rr-block--joined': block.joined,
      'rr-block--row': block.row,
    }]"
    :data-block="block.id"
    :data-section="block.sectionKey"
  >
    <SectionHeading v-if="block.withHeading" :title="block.section.title" :section-key="block.sectionKey" :index="block.sectionIndex" />
    <div v-if="part.type === 'text' && block.section.kind === 'text'" class="rr-summary"><RichTextView :value="block.section.body" /></div>
    <TimelineEntry v-else-if="part.type === 'timeline'" :part="part" />
    <SkillGroupView v-else-if="part.type === 'skill'" :group="part.group" :separator="separator" :label-em="skillLabel" />
    <ListEntry v-else-if="part.type === 'list'" :item="part.item" />
  </div>
</template>
