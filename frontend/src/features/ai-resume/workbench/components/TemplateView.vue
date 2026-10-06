<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, LoaderCircle } from 'lucide-vue-next'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import TemplateThumbnail from '@/resume-render/components/TemplateThumbnail.vue'
import { sampleFor } from '@/resume-render/samples'
import type { TemplateCategory, TemplateManifest } from '@/resume-render/templates/manifest'
import { manifestOf } from '@/resume-render/templates/manifests'
import type { AiSmartTemplate } from '../../types'
import { useWorkbench } from '../useWorkbench'

type Filter = 'all' | TemplateCategory

const CATEGORY_LABELS: Record<TemplateCategory, string> = { steady: '稳健', modern: '现代', design: '设计感', industry: '行业' }

const wb = useWorkbench()
const design = wb.design
const filter = ref<Filter>('all')
const scroller = ref<HTMLElement | null>(null)
const surface = useScrollSurface(scroller)

/** Server list (availability, saved design) joined with this build's manifests (layout code). */
const templates = computed(() => wb.session.templates.value
  .map(template => ({ template, manifest: manifestOf(template.templateId) }))
  .filter((entry): entry is { template: AiSmartTemplate; manifest: TemplateManifest } => Boolean(entry.manifest)))

const filters = computed(() => (['all', 'steady', 'modern', 'design', 'industry'] as const).map(value => ({
  value,
  label: value === 'all' ? '全部' : CATEGORY_LABELS[value],
  count: templates.value.filter(entry => value === 'all' || entry.manifest.category === value).length,
})))
const visible = computed(() => templates.value.filter(entry => filter.value === 'all' || entry.manifest.category === filter.value))
const activeId = computed(() => design.activeTemplate.value?.templateId)

/** Thumbnails show the user's own resume once it has a name and some content; before that, a sample. */
const ownContent = computed(() => {
  const content = wb.previewContent.value
  const basics = (content.basics ?? {}) as Record<string, unknown>
  const filled = ['experiences', 'education', 'projects'].some(key => Array.isArray(content[key]) && (content[key] as unknown[]).length > 0)
  return typeof basics.name === 'string' && basics.name.trim() && filled ? content : null
})
function contentFor(manifest: TemplateManifest): unknown {
  return ownContent.value ?? sampleFor(manifest.locale, manifest.id === 'campus' ? 'campus' : undefined)
}

function tags(manifest: TemplateManifest): string[] {
  const result = [manifest.maxPages === 1 ? '单页' : `最多 ${manifest.maxPages} 页`]
  if (manifest.photo === 'optional') result.push('可放照片')
  if (manifest.locale === 'en') result.push('英文')
  if (manifest.atsLevel === 'strict') result.push('ATS 友好')
  return result
}
</script>

<template>
  <div class="template-view scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
    <div ref="scroller" class="template-view__scroll" @scroll.passive="surface.onScroll">
      <header class="template-view__head">
        <div class="template-view__title">
          <strong>{{ templates.length }} 款模板</strong>
          <span>同一份内容一键换版；字号、间距等通用设置随你走，配色与版式按模板记忆{{ ownContent ? '' : ' · 缩略图为示例内容' }}</span>
        </div>
        <UiSegmented v-model="filter" :items="filters" aria-label="按风格筛选模板" size="sm" />
      </header>

      <div v-if="wb.session.templatesPending.value && !templates.length" class="template-gallery" aria-busy="true">
        <UiSkeleton v-for="index in 6" :key="index" height="260px" radius="var(--radius-lg)" />
      </div>

      <div v-else class="template-gallery">
        <button
          v-for="{ template, manifest } in visible"
          :key="template.templateId"
          type="button"
          class="template-card"
          :aria-pressed="activeId === template.templateId"
          :disabled="design.templatePending.value"
          :title="`适合：${manifest.bestFor}`"
          @click="design.changeTemplate(template.templateId)"
        >
          <span class="template-card__thumb">
            <TemplateThumbnail
              :template-id="template.templateId"
              :content="contentFor(manifest)"
              :design="template.design.settings"
              :photo="ownContent ? wb.session.conversation.value?.photo?.contentUrl ?? null : null"
              :label="`${manifest.name}模板缩略图`"
            />
            <span v-if="design.switchingTemplateId.value === template.templateId" class="template-card__busy"><LoaderCircle :size="22" aria-hidden="true" />正在换版</span>
          </span>
          <span class="template-card__meta">
            <span class="template-card__name"><strong>{{ manifest.name }}</strong><em>{{ manifest.nameEn }}</em></span>
            <small class="template-card__summary">{{ manifest.summary }}</small>
            <span class="template-card__foot">
              <span class="template-card__palettes" aria-hidden="true">
                <i v-for="palette in manifest.palettes" :key="palette.id" :style="{ background: palette.accent }" />
              </span>
              <small>{{ tags(manifest).join(' · ') }}</small>
            </span>
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
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
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
  background: var(--sheet-bg);
  box-shadow: inset 0 0 0 1px var(--sheet-edge);
  pointer-events: none;
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

.template-card__name {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.template-card__name strong {
  font-size: var(--fs-sm);
  font-weight: 650;
}

.template-card__name em {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 11px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-card__summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: var(--text-secondary);
  font-size: 11.5px;
  line-height: 1.45;
}

.template-card__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.template-card__palettes {
  display: inline-flex;
  gap: 3px;
}

.template-card__palettes i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.12);
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
