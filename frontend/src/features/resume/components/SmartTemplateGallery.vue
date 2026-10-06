<script setup lang="ts">
/**
 * Built-in templates rendered live with sample content (TPL-02). The server says which templates are
 * available; this build's manifests say how they look. Picking one starts an AI resume with it.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Sparkles } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import TemplateThumbnail from '@/resume-render/components/TemplateThumbnail.vue'
import { sampleFor } from '@/resume-render/samples'
import type { TemplateCategory, TemplateManifest } from '@/resume-render/templates/manifest'
import { manifestOf } from '@/resume-render/templates/manifests'
import { listResumeTemplates } from '../services/resumeApi'
import { TEMPLATE_CATEGORY_LABELS, templateFacts } from '../templateFacts'

type Filter = 'all' | TemplateCategory

const router = useRouter()
const manifests = ref<TemplateManifest[]>([])
const loading = ref(true)
const error = ref<unknown>(null)
const filter = ref<Filter>('all')
/** Palette shown on each card; switching it only changes the preview. */
const palettes = reactive<Record<string, string>>({})

const filters = computed(() => (['all', 'steady', 'modern', 'design', 'industry'] as const).map(value => ({
  value,
  label: value === 'all' ? '全部' : TEMPLATE_CATEGORY_LABELS[value],
  count: manifests.value.filter(manifest => value === 'all' || manifest.category === value).length,
})))
const visible = computed(() => manifests.value.filter(manifest => filter.value === 'all' || manifest.category === filter.value))

function sample(manifest: TemplateManifest): unknown {
  return sampleFor(manifest.locale, manifest.id === 'campus' ? 'campus' : undefined)
}

function start(manifest: TemplateManifest): void {
  void router.push({ path: '/ai-resume/new', query: { template: manifest.id } })
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const available = (await listResumeTemplates({ size: 50 })).items
    manifests.value = available.map(item => manifestOf(item.id)).filter((item): item is TemplateManifest => Boolean(item))
  } catch (reason) {
    error.value = reason
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section id="capability-panel-SMART_EDITABLE" class="smart-gallery" role="tabpanel" aria-labelledby="capability-tab-SMART_EDITABLE">
    <header class="smart-gallery__head">
      <div class="smart-gallery__intro">
        <h2><Sparkles :size="18" aria-hidden="true" />智能模板 <small>{{ manifests.length }} 款</small></h2>
        <p>同一份结构化内容随时换版；在 AI 简历工作台中在线编辑、逐条确认，导出的 PDF 与预览一致。缩略图使用示例内容。</p>
      </div>
      <UiSegmented v-model="filter" :items="filters" aria-label="按风格筛选模板" size="sm" />
    </header>

    <UiErrorState v-if="error" :error="error" title="智能模板读取失败" compact @retry="load" />

    <div v-else-if="loading" class="smart-gallery__grid" aria-busy="true">
      <UiSkeleton v-for="index in 8" :key="index" height="380px" radius="var(--radius-lg)" />
    </div>

    <div v-else class="smart-gallery__grid">
      <article v-for="manifest in visible" :key="manifest.id" class="smart-card">
        <RouterLink class="smart-card__preview" :to="{ name: 'resume-template-detail', params: { templateId: manifest.id } }" :aria-label="`查看${manifest.name}模板详情`">
          <TemplateThumbnail
            class="smart-card__paper"
            :template-id="manifest.id"
            :content="sample(manifest)"
            :design="{ paletteId: palettes[manifest.id] ?? manifest.palettes[0]!.id }"
            :label="`${manifest.name}模板示例`"
          />
        </RouterLink>
        <div class="smart-card__body">
          <div class="smart-card__title">
            <h3>{{ manifest.name }}<small>{{ manifest.nameEn }}</small></h3>
            <span class="smart-card__palettes" role="radiogroup" :aria-label="`${manifest.name}配色`">
              <button
                v-for="palette in manifest.palettes"
                :key="palette.id"
                type="button"
                role="radio"
                :aria-checked="(palettes[manifest.id] ?? manifest.palettes[0]!.id) === palette.id"
                :aria-label="palette.name"
                :title="palette.name"
                :style="{ '--swatch': palette.accent }"
                @click="palettes[manifest.id] = palette.id"
              />
            </span>
          </div>
          <p class="smart-card__summary">{{ manifest.summary }}</p>
          <div class="smart-card__facts">
            <span v-for="fact in templateFacts(manifest)" :key="fact">{{ fact }}</span>
          </div>
          <UiButton size="sm" variant="soft" block :icon-right="ArrowRight" @click="start(manifest)">用此模板创建</UiButton>
        </div>
      </article>
    </div>
    <p v-if="!loading && !error && manifests.length && !visible.length" class="smart-gallery__empty">没有符合条件的模板。</p>
  </section>
</template>

<style scoped>
.smart-gallery {
  display: grid;
  gap: var(--space-5);
}

.smart-gallery__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3) var(--space-6);
}

.smart-gallery__intro {
  max-width: 620px;
  display: grid;
  gap: 4px;
}

.smart-gallery__head h2 {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-h3);
  font-weight: 700;
}

.smart-gallery__head h2 svg {
  color: var(--color-primary-text);
}

.smart-gallery__head p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.smart-gallery__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: var(--space-5);
}

.smart-card {
  min-width: 0;
  /* Flex column: a grid card with an aspect-ratio preview loops Chrome's layout (see TemplateView). */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-1);
  box-shadow: var(--shadow-xs);
  transition: transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-fast);
}

.smart-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-default);
  box-shadow: var(--shadow-lg);
}

.smart-card__preview {
  display: block;
  padding: var(--space-4) var(--space-4) 0;
  background-color: var(--bg-sunken);
  background-image: radial-gradient(var(--dot-color) 1px, transparent 1px);
  background-size: 16px 16px;
}

.smart-card__paper {
  border-radius: 3px 3px 0 0;
  box-shadow: var(--shadow-md);
  aspect-ratio: 210 / 250;
  transition: transform var(--dur-slow) var(--ease-out);
}

.smart-card:hover .smart-card__paper {
  transform: translateY(-4px);
}

.smart-card__preview:focus-visible {
  outline: none;
  box-shadow: inset var(--focus-ring);
}

.smart-card__body {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.smart-card__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.smart-card__body h3 {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: var(--fs-body);
  font-weight: 700;
}

.smart-card__body h3 small,
.smart-gallery__head h2 small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  font-weight: 500;
}

.smart-card__palettes {
  display: inline-flex;
  gap: 5px;
}

.smart-card__palettes button {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--swatch);
  box-shadow: 0 0 0 1px var(--border-default);
  transition: transform var(--dur-fast) var(--ease-spring), box-shadow var(--dur-fast);
}

.smart-card__palettes button:hover {
  transform: scale(1.15);
}

.smart-card__palettes button[aria-checked='true'] {
  box-shadow: 0 0 0 2px var(--surface-1), 0 0 0 3.5px var(--swatch);
}

.smart-card__palettes button:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.smart-card__summary {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  line-height: 1.55;
}

.smart-card__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.smart-card__facts span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--surface-2);
  color: var(--text-secondary);
  font-size: 11.5px;
}

.smart-gallery__empty {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  text-align: center;
}

@media (max-width: 640px) {
  .smart-gallery__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-3);
  }

  .smart-card__preview {
    padding: var(--space-2) var(--space-2) 0;
  }

  .smart-card__body {
    padding: var(--space-3);
  }
}
</style>
