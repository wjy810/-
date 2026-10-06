<script setup lang="ts">
import { computed, provide } from 'vue'
import type { Block } from '../layout/blocks'
import { PAGE_LABEL } from '../model/labels'
import type { RenderDocument } from '../model/types'
import type { ResumeDesignV2 } from '../theme/design'
import type { ResolvedTheme } from '../theme/tokens'
import type { TemplateModule } from '../templates/manifest'
import { PAGE_CONTEXT } from './context'

const props = defineProps<{
  template: TemplateModule
  document: RenderDocument
  design: ResumeDesignV2
  theme: ResolvedTheme
  index: number
  total: number
  blocks: Record<string, Block[]>
  probe?: boolean
}>()

provide(PAGE_CONTEXT, { blocks: (region: string) => props.blocks[region] ?? [], probe: Boolean(props.probe) })

const page = computed(() => ({ index: props.index, total: props.total, continuation: props.index > 0 }))
const label = computed(() => PAGE_LABEL[props.document.locale](props.index + 1, props.total))
</script>

<template>
  <section
    class="rr-page"
    :class="{ 'rr-page--continuation': index > 0, 'rr-page--probe': probe }"
    :data-page="index + 1"
    :aria-label="probe ? undefined : label"
  >
    <component :is="template.Layout" :document="document" :design="design" :manifest="template.manifest" :theme="theme" :page="page" />
    <p v-if="total > 1 && !probe" class="rr-page__number">{{ label }}</p>
  </section>
</template>
