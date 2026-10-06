<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, LoaderCircle } from 'lucide-vue-next'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import ResumeTemplatePreview from '@/features/resume/components/ResumeTemplatePreview.vue'
import type { AiSmartTemplate } from '../../types'
import { useWorkbench } from '../useWorkbench'

type Filter = 'all' | 'single' | 'multi' | 'english'

const wb = useWorkbench()
const design = wb.design
const templates = wb.session.templates
const filter = ref<Filter>('all')
const scroller = ref<HTMLElement | null>(null)
const surface = useScrollSurface(scroller)

function matches(template: AiSmartTemplate, value: Filter): boolean {
  if (value === 'single') return template.recommendedPages === '1'
  if (value === 'multi') return template.recommendedPages !== '1'
  if (value === 'english') return template.languageCode.toLowerCase().startsWith('en')
  return true
}

const filters = computed(() => ([
  { value: 'all', label: '全部' },
  { value: 'single', label: '单页' },
  { value: 'multi', label: '多页' },
  { value: 'english', label: '英文' },
] as const).map(item => ({ ...item, count: templates.value.filter(template => matches(template, item.value)).length })))
const visible = computed(() => templates.value.filter(template => matches(template, filter.value)))
const activeId = computed(() => design.activeTemplate.value?.templateId)
</script>

<template>
  <div class="template-view scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
    <div ref="scroller" class="template-view__scroll" @scroll.passive="surface.onScroll">
      <header class="template-view__head">
        <div class="template-view__title"><strong>{{ templates.length || 12 }} 款智能模板</strong><span>同一份结构化内容，一键换版；设计设置按模板分别记忆</span></div>
        <UiSegmented v-model="filter" :items="filters" aria-label="筛选模板" size="sm" />
      </header>

      <div v-if="wb.session.templatesPending.value && !templates.length" class="template-gallery" aria-busy="true">
        <UiSkeleton v-for="index in 6" :key="index" height="260px" radius="var(--radius-lg)" />
      </div>

      <div v-else class="template-gallery">
        <button
          v-for="template in visible"
          :key="template.templateId"
          type="button"
          class="template-card"
          :aria-pressed="activeId === template.templateId"
          :disabled="design.templatePending.value"
          @click="design.changeTemplate(template.templateId)"
        >
          <span class="template-card__thumb">
            <ResumeTemplatePreview
              :resume="wb.previewResume.value"
              :content="wb.previewContent.value"
              :design="template.design.settings"
              :template-id="template.templateId"
              :layout-definition-json="template.layoutDefinitionJson"
              :renderer-protocol="template.rendererProtocol"
              :variant-code="template.design.variantCode"
              compact
            />
            <span v-if="design.switchingTemplateId.value === template.templateId" class="template-card__busy"><LoaderCircle :size="22" aria-hidden="true" />正在换版</span>
          </span>
          <span class="template-card__meta">
            <strong>{{ template.displayName }}</strong>
            <small>{{ template.recommendedPages }} 页 · {{ template.languageCode }}</small>
          </span>
          <span v-if="activeId === template.templateId" class="template-card__check"><Check :size="13" :stroke-width="3" aria-hidden="true" />当前</span>
        </button>
      </div>
      <p v-if="!wb.session.templatesPending.value && templates.length && !visible.length" class="template-view__empty">没有符合条件的模板。</p>
    </div>
  </div>
</template>

<style scoped>
.template-view {
  min-height: 0;
  height: 100%;
}

.template-view__scroll {
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-4) var(--space-5) var(--space-8);
  display: grid;
  align-content: start;
  gap: var(--space-4);
}

.template-view__head {
  display: grid;
  gap: var(--space-3);
}

.template-view__title {
  display: grid;
  gap: 2px;
}

.template-view__head strong {
  font-size: var(--fs-body);
  font-weight: 700;
}

.template-view__head span {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.template-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--space-3);
}

.template-card {
  position: relative;
  min-width: 0;
  padding: 8px;
  /* Flex, not grid: a grid button + aspect-ratio thumbnail + container-query text loops Chrome's layout. */
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  text-align: left;
  transition: border-color var(--dur-fast), box-shadow var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out);
}

.template-card:hover:not(:disabled) {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.template-card[aria-pressed='true'] {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary), var(--shadow-sm);
}

.template-card:disabled {
  cursor: progress;
}

.template-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.template-card__thumb {
  position: relative;
  aspect-ratio: 210 / 297;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: #fff;
  box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.06);
  pointer-events: none;
}

.template-card__thumb :deep(.resume-pages) {
  width: 100%;
}

.template-card__thumb :deep(.resume-sheet) {
  border: 0;
  box-shadow: none;
}

.template-card__busy {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 6px;
  background: color-mix(in srgb, var(--surface-1) 78%, transparent);
  color: var(--color-primary-text);
  font-size: var(--fs-xs);
  font-weight: 600;
  backdrop-filter: blur(2px);
}

.template-card__busy svg {
  animation: template-spin 0.9s linear infinite;
}

.template-card__meta {
  min-width: 0;
  display: grid;
  gap: 1px;
  padding: 0 2px 2px;
}

.template-card__meta strong {
  overflow: hidden;
  font-size: var(--fs-sm);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-card__meta small {
  color: var(--text-tertiary);
  font-size: 11px;
}

.template-card__check {
  position: absolute;
  top: 14px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px 2px 6px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  color: var(--text-on-primary);
  font-size: 11px;
  font-weight: 650;
  box-shadow: var(--shadow-sm);
}

.template-view__empty {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  text-align: center;
}

@keyframes template-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .template-view__scroll {
    padding: var(--space-3);
  }

  .template-gallery {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
