<script setup lang="ts">
/** The 12 smart templates, rendered live with sample content; picking one starts an AI resume with it. */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Image, ImageOff, ScanText, Sparkles } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import ResumeTemplatePreview from './ResumeTemplatePreview.vue'
import { SAMPLE_CONTENT, SAMPLE_RESUME } from '../sampleResume'
import { listResumeTemplates } from '../services/resumeApi'
import type { ResumeTemplateSummary } from '../types'

type Filter = 'all' | 'single' | 'multi' | 'photo' | 'english'

const router = useRouter()
const templates = ref<ResumeTemplateSummary[]>([])
const loading = ref(true)
const error = ref<unknown>(null)
const filter = ref<Filter>('all')

function matches(template: ResumeTemplateSummary, value: Filter): boolean {
  if (value === 'single') return template.recommendedPages === '1'
  if (value === 'multi') return template.recommendedPages !== '1'
  if (value === 'photo') return template.photoPolicy !== 'DISABLED'
  if (value === 'english') return template.languageCode.toLowerCase().startsWith('en')
  return true
}

const filters = computed(() => ([
  { value: 'all', label: '全部' },
  { value: 'single', label: '单页' },
  { value: 'multi', label: '多页' },
  { value: 'photo', label: '支持照片' },
  { value: 'english', label: '英文' },
] as const).map(item => ({ ...item, count: templates.value.filter(template => matches(template, item.value)).length })))
const visible = computed(() => templates.value.filter(template => matches(template, filter.value)))

function photoText(policy: string): string {
  return policy === 'DISABLED' ? '无照片' : policy === 'REQUIRED' ? '含照片' : '照片可选'
}

function start(template: ResumeTemplateSummary): void {
  void router.push({ path: '/ai-resume/new', query: { template: template.id } })
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    templates.value = (await listResumeTemplates({ size: 50 })).items
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
        <h2><Sparkles :size="18" aria-hidden="true" />智能模板</h2>
        <p>同一份结构化内容随时换版，在 AI 简历工作台中在线编辑、逐条确认，并导出 PDF。预览使用示例内容。</p>
      </div>
      <UiSegmented v-model="filter" :items="filters" aria-label="筛选智能模板" size="sm" />
    </header>

    <UiErrorState v-if="error" :error="error" title="智能模板读取失败" compact @retry="load" />

    <div v-else-if="loading" class="smart-gallery__grid" aria-busy="true">
      <UiSkeleton v-for="index in 8" :key="index" height="360px" radius="var(--radius-lg)" />
    </div>

    <div v-else class="smart-gallery__grid">
      <article v-for="template in visible" :key="template.id" class="smart-card">
        <RouterLink class="smart-card__preview" :to="{ name: 'resume-template-detail', params: { templateId: template.id } }" :aria-label="`查看${template.displayName}详情`">
          <ResumeTemplatePreview
            :resume="SAMPLE_RESUME"
            :content="SAMPLE_CONTENT"
            :template-id="template.id"
            :layout-definition-json="template.layoutDefinitionJson ?? undefined"
            :renderer-protocol="template.rendererProtocol ?? undefined"
            :variant-code="template.variants[0] ?? 'MONO'"
            compact
          />
        </RouterLink>
        <div class="smart-card__body">
          <h3>{{ template.displayName }}</h3>
          <div class="smart-card__facts">
            <span><ScanText :size="12" aria-hidden="true" />{{ template.recommendedPages }} 页</span>
            <span>
              <ImageOff v-if="template.photoPolicy === 'DISABLED'" :size="12" aria-hidden="true" />
              <Image v-else :size="12" aria-hidden="true" />{{ photoText(template.photoPolicy) }}
            </span>
            <span>{{ template.languageCode.startsWith('en') ? '英文' : '中文' }}</span>
            <span v-if="template.variants.length > 1">{{ template.variants.length }} 种配色</span>
          </div>
          <UiButton size="sm" variant="soft" block :icon-right="ArrowRight" @click="start(template)">用此模板创建</UiButton>
        </div>
      </article>
    </div>
    <p v-if="!loading && !error && templates.length && !visible.length" class="smart-gallery__empty">没有符合条件的模板。</p>
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

.smart-card__preview :deep(.resume-pages) {
  width: 100%;
  margin: 0;
}

.smart-card__preview :deep(.resume-sheet) {
  border: 0;
  border-radius: 3px 3px 0 0;
  box-shadow: var(--shadow-md);
  transition: transform var(--dur-slow) var(--ease-out);
}

.smart-card:hover .smart-card__preview :deep(.resume-sheet) {
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

.smart-card__body h3 {
  overflow: hidden;
  font-size: var(--fs-body);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
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
