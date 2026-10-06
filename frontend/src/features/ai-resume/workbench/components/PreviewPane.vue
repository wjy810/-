<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { ChevronRight, Info, Maximize2, Minimize2, Undo2, ZoomIn, ZoomOut } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import ResumeDocument from '@/resume-render/components/ResumeDocument.vue'
import { CARD_META, type CardType } from '../cardConfig'
import { useWorkbench } from '../useWorkbench'

/** A4 width at 96 dpi; zoom levels are fractions of it, "fit" follows the pane width. */
const A4_WIDTH = 794
const ZOOM_LEVELS = [0.5, 0.65, 0.8, 1, 1.2, 1.4]

const wb = useWorkbench()
const design = wb.design
const conversation = wb.session.conversation

const stage = ref<HTMLElement | null>(null)
const surface = useScrollSurface(stage)
const zoom = useLocalStorage<number | 'fit'>('jp:ai-resume:preview-zoom', 'fit')

const zoomLabel = computed(() => zoom.value === 'fit' ? '适应' : `${Math.round(zoom.value * 100)}%`)
const overflow = design.overflow
const result = design.layoutResult
const status = computed(() => {
  if (overflow.value) {
    const where = overflow.value.section ? `，从「${overflow.value.section}」开始` : ''
    return { tone: 'danger', text: `超出 ${overflow.value.pageLimit} 页上限约 ${Math.max(1, Math.round(overflow.value.overflowMm))} 毫米${where}` }
  }
  const pages = result.value ? `共 ${result.value.pageCount} 页 · ` : ''
  if (wb.session.ready.value) return { tone: 'success', text: `${pages}已满足第一版条件` }
  return { tone: 'neutral', text: `${pages}还需目标岗位和教育/经历` }
})
const accentNote = computed(() => result.value?.accentAdjusted
  ? `自定义强调色在白纸上对比度不足，预览与导出已自动加深为 ${result.value.accentAdjusted}` : '')

function currentScale(): number {
  if (zoom.value !== 'fit') return zoom.value
  const width = stage.value?.clientWidth ?? A4_WIDTH
  return Math.min(760, width - 48) / A4_WIDTH
}

function step(direction: 1 | -1): void {
  const current = currentScale()
  const next = direction > 0
    ? ZOOM_LEVELS.find(level => level > current + 0.01)
    : [...ZOOM_LEVELS].reverse().find(level => level < current - 0.01)
  if (next) zoom.value = next
}

async function compact(): Promise<void> {
  const outcome = await design.compact()
  if (outcome === 'fit') wb.session.notify('已收紧间距与字号，内容已放进目标页数。')
  else if (outcome === 'partial') wb.session.fail('已收紧到最紧凑的排版仍然超出；请精简内容或把目标页数改为自动。')
}

/** After a card is confirmed, briefly highlight where it landed on the page. */
wb.session.onLanded(async (cardType) => {
  const section = CARD_META[cardType as CardType]?.section
  if (!section) return
  await nextTick()
  const targets = stage.value?.querySelectorAll<HTMLElement>(`.rr-pages [data-section="${section}"]`)
  if (!targets?.length) return
  targets[0]!.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
  for (const target of targets) {
    target.classList.remove('rr-flash')
    void target.offsetWidth
    target.classList.add('rr-flash')
    target.addEventListener('animationend', () => target.classList.remove('rr-flash'), { once: true })
  }
})
</script>

<template>
  <section v-if="conversation" class="preview-pane" aria-label="简历预览">
    <header class="preview-pane__bar">
      <div class="preview-pane__status" :class="`is-${status.tone}`" role="status">
        <i aria-hidden="true" />
        <span><strong>实时预览</strong><small>{{ status.text }}</small></span>
      </div>
      <UiButton v-if="overflow && !design.compactUndo.value" size="sm" variant="secondary" :icon="Minimize2" :pending="design.compacting.value" @click="compact">一键紧凑</UiButton>
      <UiButton v-if="design.compactUndo.value && !design.compacting.value" size="sm" variant="ghost" :icon="Undo2" @click="design.undoCompact()">撤销紧凑</UiButton>
      <div class="preview-pane__zoom" role="group" aria-label="预览缩放">
        <UiIconButton :icon="ZoomOut" size="sm" label="缩小" @click="step(-1)" />
        <button type="button" class="preview-pane__zoom-label" :title="zoom === 'fit' ? '当前为适应宽度' : '恢复适应宽度'" @click="zoom = 'fit'">{{ zoomLabel }}</button>
        <UiIconButton :icon="ZoomIn" size="sm" label="放大" @click="step(1)" />
        <UiIconButton :icon="Maximize2" size="sm" label="适应宽度" :active="zoom === 'fit'" @click="zoom = 'fit'" />
      </div>
      <button class="preview-template" type="button" @click="wb.openView('templates')">
        <span>当前模板</span>
        <strong>{{ design.manifest.value?.name ?? design.activeTemplate.value?.displayName ?? '读取中' }}</strong>
        <ChevronRight :size="15" aria-hidden="true" />
      </button>
    </header>
    <p v-if="accentNote" class="preview-pane__note"><Info :size="13" aria-hidden="true" />{{ accentNote }}</p>

    <div class="preview-pane__stage-shell scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
      <div ref="stage" class="preview-pane__stage" tabindex="0" aria-label="简历预览滚动区域" @scroll.passive="surface.onScroll">
        <div class="preview-pane__paper" :class="{ 'is-fit': zoom === 'fit' }">
          <ResumeDocument
            v-if="design.templateModule.value && design.activeDesign.value"
            :template="design.templateModule.value"
            :content="wb.previewContent.value"
            :design="design.activeDesign.value"
            :photo="conversation.photo?.contentUrl ?? null"
            :fit="zoom === 'fit'"
            :zoom="zoom === 'fit' ? 1 : zoom"
            :debounce-ms="120"
            interactive
            @layout="design.reportLayout"
            @section-click="wb.locateSection"
          />
          <p v-else-if="design.templateLoadError.value" class="preview-pane__error" role="alert">{{ design.templateLoadError.value }}</p>
          <UiSkeleton v-else height="720px" radius="var(--radius-sm)" />
        </div>
      </div>
    </div>

    <footer class="preview-pane__foot">
      <span><Info :size="13" aria-hidden="true" />点击页面中的模块可直接编辑；预览与导出的 PDF 使用同一排版引擎。</span>
      <RouterLink :to="`/resumes/${conversation.masterId}/manual#export`">版本与导出</RouterLink>
    </footer>
  </section>
</template>

<style scoped>
.preview-pane {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  background: var(--bg-sunken);
}

.preview-pane__bar {
  min-height: 56px;
  padding: var(--space-2) var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
  background: color-mix(in srgb, var(--surface-1) 82%, var(--bg-sunken));
}

.preview-pane__status {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.preview-pane__status i {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: var(--text-disabled);
}

.preview-pane__status.is-success i {
  background: var(--color-success);
  box-shadow: 0 0 0 3px var(--color-success-soft);
}

.preview-pane__status.is-danger i {
  background: var(--color-danger);
  box-shadow: 0 0 0 3px var(--color-danger-soft);
}

.preview-pane__status span {
  min-width: 0;
  display: grid;
}

.preview-pane__status strong {
  font-size: var(--fs-sm);
  font-weight: 680;
}

.preview-pane__status small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-pane__status.is-danger small {
  color: var(--color-danger-text);
  font-weight: 600;
}

.preview-pane__zoom {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  padding: 2px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-1);
}

.preview-pane__zoom-label {
  min-width: 46px;
  height: 26px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 11.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.preview-pane__zoom-label:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.preview-template {
  max-width: 220px;
  height: 40px;
  padding: 0 10px 0 12px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  column-gap: 8px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-primary);
  text-align: left;
  transition: border-color var(--dur-fast), box-shadow var(--dur-base);
}

.preview-template:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.preview-template:focus-visible,
.preview-pane__zoom-label:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.preview-template span {
  grid-column: 1;
  color: var(--text-tertiary);
  font-size: 10.5px;
  line-height: 1.2;
}

.preview-template strong {
  grid-column: 1;
  overflow: hidden;
  font-size: var(--fs-sm);
  font-weight: 650;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-template svg {
  grid-column: 2;
  grid-row: 1 / 3;
  color: var(--text-tertiary);
}

.preview-pane__stage-shell {
  min-height: 0;
}

.preview-pane__stage {
  height: 100%;
  overflow: auto;
  overscroll-behavior: contain;
  padding: var(--space-6);
  background-color: var(--bg-sunken);
  background-image: radial-gradient(var(--dot-color) 1px, transparent 1px);
  background-size: 18px 18px;
}

.preview-pane__stage:focus-visible {
  outline: none;
  box-shadow: inset var(--focus-ring);
}

.preview-pane__paper {
  margin: 0 auto;
}

.preview-pane__paper.is-fit {
  max-width: 760px;
}

.preview-pane__paper :deep(.rr-page) {
  box-shadow: var(--shadow-paper, var(--shadow-lg));
}

.preview-pane__note {
  margin: 0;
  padding: 6px var(--space-4);
  display: flex;
  align-items: center;
  gap: 6px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--color-info-soft, var(--surface-2));
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}

.preview-pane__error {
  padding: var(--space-6);
  color: var(--color-danger-text);
  text-align: center;
}

.preview-pane__foot {
  min-height: 38px;
  padding: 6px var(--space-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border-top: 1px solid var(--border-subtle);
  background: color-mix(in srgb, var(--surface-1) 82%, var(--bg-sunken));
  color: var(--text-tertiary);
  font-size: 11.5px;
}

.preview-pane__foot span {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.preview-pane__foot a {
  flex: none;
  color: var(--color-primary-text);
  font-weight: 600;
}

@media (max-width: 1180px) {
  .preview-pane__zoom :deep(.ui-icon-btn:last-child) {
    display: none;
  }
}

@media (max-width: 640px) {
  .preview-pane__bar {
    padding: var(--space-2) var(--space-3);
  }

  .preview-pane__status small,
  .preview-pane__zoom {
    display: none;
  }

  .preview-pane__stage {
    padding: var(--space-3);
  }

  .preview-pane__foot span {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .preview-pane__stage :deep(.rr-flash) {
    animation: none;
  }
}
</style>
