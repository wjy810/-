<script setup lang="ts">
/**
 * One built-in template in full (TPL-04): both pages rendered live with sample content, palette,
 * font and header switches, what the template suits and what can be adjusted, and the two ways to
 * use it — start a new AI resume, or apply it to an existing resume.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Check, FileStack, RotateCcw } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage, isForbidden, isNotFound } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import ResumeDocument from '@/resume-render/components/ResumeDocument.vue'
import { sampleFor } from '@/resume-render/samples'
import type { FontPairing } from '@/resume-render/theme/design'
import type { TemplateManifest } from '@/resume-render/templates/manifest'
import { manifestOf } from '@/resume-render/templates/manifests'
import { loadTemplate } from '@/resume-render/templates/registry'
import { ensureAiResume, selectAiResumeTemplate } from '@/features/ai-resume/services/aiResumeApi'
import { fetchResumeTemplate, listResumes } from '../services/resumeApi'
import { templateFacts } from '../templateFacts'

const FONT_LABELS: Record<FontPairing, string> = { sans: '现代黑体', serif: '经典宋体', mixed: '宋黑混排', tech: '技术等宽' }
const ADJUSTABLE = ['配色与自定义强调色', '字体组合', '字号 5 档', '行距、段落间距、页边距', '目标页数（自动 / 1 页 / 2 页）', '日期格式', '板块顺序、显示与标题']

const route = useRoute()
const router = useRouter()
const templateId = computed(() => String(route.params.templateId ?? ''))
const manifest = computed<TemplateManifest | undefined>(() => manifestOf(templateId.value))
// The server decides availability (an operator may have retired the template).
const templateState = useLoadState(async () => {
  const id = templateId.value
  const [, loaded] = await Promise.all([fetchResumeTemplate(id), loadTemplate(id)])
  return { id, module: loaded }
})
const { loading, error: loadError } = templateState
/** The loaded template, only while it is the one in the URL. */
const module = computed(() => templateState.data.value?.id === templateId.value ? templateState.data.value.module : null)
const forbidden = computed(() => (isForbidden(loadError.value) ? loadError.value : null))
const notFound = computed(() => !manifest.value || (!module.value && isNotFound(loadError.value)))
const resumeState = useLoadState(() => listResumes())
const targetMasterId = ref('')
const applying = ref(false)
const error = ref('')
useToastFeedback(error, 'error', 'resume-template-detail-error')

const paletteId = ref('')
const fontPairing = ref<FontPairing>('sans')
const headerVariant = ref('')
const paperSize = ref<'A4' | 'LETTER'>('A4')
const pageCount = ref(0)

const design = computed(() => ({
  paletteId: paletteId.value,
  fontPairing: fontPairing.value,
  headerVariant: headerVariant.value,
  paperSize: paperSize.value,
}))
const sample = computed(() => manifest.value ? sampleFor(manifest.value.locale, manifest.value.id === 'campus' ? 'campus' : undefined) : {})
const usableResumes = computed(() => (resumeState.data.value ?? []).filter(item => item.status !== 'ARCHIVED'))
const resumeOptions = computed(() => usableResumes.value.map(item => ({ value: item.id, label: item.title || '未命名简历' })))

function reset(value: TemplateManifest): void {
  paletteId.value = value.palettes[0]!.id
  fontPairing.value = value.fontPairings[0]!
  headerVariant.value = value.headerVariants[0]?.id ?? ''
  paperSize.value = value.paperSizes[0]!
}

function load(): void {
  if (!manifest.value) return
  reset(manifest.value)
  void templateState.load()
}

function start(): void {
  void router.push({ path: '/ai-resume/new', query: { template: templateId.value } })
}

/** Applies the template through the resume's AI workbench conversation, then opens it there. */
async function applyToResume(): Promise<void> {
  if (!targetMasterId.value || applying.value) return
  applying.value = true
  error.value = ''
  try {
    const conversation = await ensureAiResume(targetMasterId.value)
    if (conversation.layout?.templateId !== templateId.value) {
      await selectAiResumeTemplate(conversation.id, templateId.value, conversation.layout?.version)
    }
    await router.push({ name: 'ai-resume-workbench', params: { conversationId: conversation.id } })
  } catch (cause) {
    error.value = errorMessage(cause, '应用模板失败，原简历版式保持不变')
  } finally {
    applying.value = false
  }
}

watch(resumeState.data, () => {
  if (!usableResumes.value.some(item => item.id === targetMasterId.value)) targetMasterId.value = usableResumes.value[0]?.id ?? ''
})
watch(templateId, load)
onMounted(() => { load(); void resumeState.load() })
</script>

<template>
  <ForbidState v-if="forbidden" :error="forbidden" />
  <section v-else class="page tpl-detail">
    <RouterLink class="tpl-detail__back" to="/resume-templates"><ArrowLeft :size="15" aria-hidden="true" />模板中心</RouterLink>

    <UiEmptyState v-if="notFound" title="模板不存在" description="这套模板可能已下架，或链接有误。" illustration="empty-templates">
      <UiButton variant="secondary" :icon="ArrowLeft" to="/resume-templates">返回模板中心</UiButton>
    </UiEmptyState>

    <PageState v-else :loading="loading" :error="loadError" :loaded="Boolean(module)" error-title="模板读取失败" @retry="load">
      <template #skeleton>
        <div class="tpl-detail__grid" aria-busy="true">
          <div class="tpl-detail__stage"><UiSkeleton height="960px" radius="2px" /></div>
          <UiSkeleton height="520px" radius="var(--radius-xl)" />
        </div>
      </template>
    <div v-if="manifest && module" class="tpl-detail__grid">
      <div class="tpl-detail__stage" aria-label="模板预览（示例内容）">
        <ResumeDocument
          :template="module"
          :content="sample"
          :design="design"
          @layout="pageCount = $event.pageCount"
        />
      </div>

      <aside class="tpl-detail__panel">
        <header class="tpl-detail__head">
          <h1>{{ manifest.name }}<small>{{ manifest.nameEn }}</small></h1>
          <p>{{ manifest.summary }}</p>
          <div class="tpl-detail__facts">
            <span v-for="fact in templateFacts(manifest)" :key="fact">{{ fact }}</span>
          </div>
        </header>

        <section class="tpl-detail__section">
          <h2>适合</h2>
          <p>{{ manifest.bestFor }}</p>
        </section>

        <section class="tpl-detail__section">
          <h2>配色</h2>
          <div class="tpl-detail__swatches" role="radiogroup" aria-label="配色">
            <button
              v-for="palette in manifest.palettes"
              :key="palette.id"
              type="button"
              role="radio"
              :aria-checked="paletteId === palette.id"
              :style="{ '--swatch': palette.accent }"
              @click="paletteId = palette.id"
            >
              <i aria-hidden="true"><Check v-if="paletteId === palette.id" :size="13" :stroke-width="3" /></i>{{ palette.name }}
            </button>
          </div>
        </section>

        <section v-if="manifest.fontPairings.length > 1" class="tpl-detail__section">
          <h2>字体组合</h2>
          <UiSegmented v-model="fontPairing" :items="manifest.fontPairings.map(value => ({ value, label: FONT_LABELS[value] }))" aria-label="字体组合" size="sm" />
        </section>

        <section v-if="manifest.headerVariants.length > 1" class="tpl-detail__section">
          <h2>页头样式</h2>
          <UiSegmented v-model="headerVariant" :items="manifest.headerVariants.map(item => ({ value: item.id, label: item.name }))" aria-label="页头样式" size="sm" />
        </section>

        <section v-if="manifest.paperSizes.length > 1" class="tpl-detail__section">
          <h2>纸张</h2>
          <UiSegmented v-model="paperSize" :items="manifest.paperSizes.map(value => ({ value, label: value === 'LETTER' ? 'US Letter' : 'A4' }))" aria-label="纸张" size="sm" />
        </section>

        <section class="tpl-detail__section">
          <h2>在工作台中还可以调整</h2>
          <ul class="tpl-detail__list">
            <li v-for="item in ADJUSTABLE" :key="item">{{ item }}</li>
            <li v-if="manifest.photo === 'optional'">照片显示与形状</li>
            <li v-if="manifest.regions.length > 1">板块放在主栏或侧栏</li>
            <li v-if="manifest.decorations">底纹与装饰开关</li>
          </ul>
          <p v-if="pageCount" class="tpl-detail__hint"><FileStack :size="13" aria-hidden="true" />示例内容排版后 {{ pageCount }} 页；这套模板最多 {{ manifest.maxPages }} 页。</p>
        </section>

        <div class="tpl-detail__actions">
          <UiButton block :icon-right="ArrowRight" @click="start">用此模板创建简历</UiButton>
          <div v-if="resumeOptions.length" class="tpl-detail__apply">
            <UiSelect v-model="targetMasterId" :options="resumeOptions" aria-label="选择要应用的简历" />
            <UiButton variant="secondary" :pending="applying" :disabled="!targetMasterId" @click="applyToResume">应用到这份简历</UiButton>
          </div>
          <p v-else-if="resumeState.error.value && !resumeState.loaded.value" class="tpl-detail__hint tpl-detail__hint--error" role="alert">
            你的简历列表没有读取成功，暂时不能直接应用到已有简历。
            <UiButton size="sm" variant="link" :icon="RotateCcw" :pending="resumeState.loading.value" @click="resumeState.load">重试</UiButton>
          </p>
          <p class="tpl-detail__hint">换模板只改变版式，内容、字号与间距等通用设置会保留；工作台中 10 秒内可撤销。</p>
        </div>
      </aside>
    </div>
    </PageState>
  </section>
</template>

<style scoped>
.tpl-detail {
  max-width: 1380px;
  margin: 0 auto;
  display: grid;
  gap: var(--space-4);
}

.tpl-detail__back {
  width: fit-content;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.tpl-detail__back:hover {
  color: var(--color-primary-text);
}

.tpl-detail__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--space-6);
  align-items: start;
}

.tpl-detail__stage {
  min-width: 0;
  padding: var(--space-6);
  border-radius: var(--radius-xl);
  background-color: var(--bg-sunken);
  background-image: radial-gradient(var(--dot-color) 1px, transparent 1px);
  background-size: 18px 18px;
}

.tpl-detail__stage :deep(.rr-page) {
  box-shadow: var(--shadow-paper, var(--shadow-lg));
}

.tpl-detail__panel {
  position: sticky;
  top: var(--space-4);
  display: grid;
  gap: var(--space-5);
  padding: var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-1);
}

.tpl-detail__head {
  display: grid;
  gap: var(--space-2);
}

.tpl-detail__head h1 {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--fs-h2);
  font-weight: 750;
}

.tpl-detail__head h1 small {
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  font-weight: 500;
}

.tpl-detail__head p,
.tpl-detail__section p {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.65;
}

.tpl-detail__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tpl-detail__facts span {
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--surface-2);
  color: var(--text-secondary);
  font-size: 11.5px;
}

.tpl-detail__section {
  display: grid;
  gap: var(--space-2);
}

.tpl-detail__section h2 {
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 0.04em;
}

.tpl-detail__swatches {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.tpl-detail__swatches button {
  height: 34px;
  padding: 0 12px 0 6px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-full);
  background: var(--surface-1);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.tpl-detail__swatches i {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--swatch);
  color: var(--text-on-primary);
}

.tpl-detail__swatches button[aria-checked='true'] {
  border-color: var(--color-primary);
  color: var(--color-primary-text);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.tpl-detail__swatches button:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.tpl-detail__list {
  margin: 0;
  padding-left: 1.1em;
  display: grid;
  gap: 3px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.tpl-detail__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-tertiary) !important;
  font-size: var(--fs-xs) !important;
}

.tpl-detail__hint--error {
  flex-wrap: wrap;
  color: var(--color-danger-text) !important;
}

.tpl-detail__actions {
  display: grid;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.tpl-detail__apply {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
}

@media (max-width: 1100px) {
  .tpl-detail__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .tpl-detail__panel {
    position: static;
    order: -1;
  }
}

@media (max-width: 640px) {
  .tpl-detail__stage {
    padding: var(--space-3);
  }
}
</style>
