<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { ChevronRight, Info, Maximize2, ZoomIn, ZoomOut } from 'lucide-vue-next'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import { useScrollSurface } from '@/shared/composables/useScrollSurface'
import ResumeTemplatePreview from '@/features/resume/components/ResumeTemplatePreview.vue'
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
const pagesWidth = computed(() => zoom.value === 'fit' ? 'min(100%, 760px)' : `${Math.round(A4_WIDTH * zoom.value)}px`)
const status = computed(() => {
  if (wb.pdf.overflowBlocked.value) return { tone: 'danger', text: `PDF 溢出 · ${wb.pdf.overflowSummary.value}` }
  if (wb.session.ready.value) return { tone: 'success', text: '已满足第一版条件' }
  return { tone: 'neutral', text: '还需目标岗位和教育/经历' }
})

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

/** After a card is confirmed, briefly highlight where it landed on the page. */
wb.session.onLanded(async (cardType) => {
  const section = CARD_META[cardType as CardType]?.section
  if (!section) return
  await nextTick()
  const targets = stage.value?.querySelectorAll<HTMLElement>(`[data-section="${section}"]`)
  if (!targets?.length) return
  targets[0]!.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
  for (const target of targets) {
    target.classList.remove('is-landed')
    void target.offsetWidth
    target.classList.add('is-landed')
    target.addEventListener('animationend', () => target.classList.remove('is-landed'), { once: true })
  }
})
</script>

<template>
  <section v-if="conversation" class="preview-pane" aria-label="A4 简历预览">
    <header class="preview-pane__bar">
      <div class="preview-pane__status" :class="`is-${status.tone}`">
        <i aria-hidden="true" />
        <span><strong>实时 A4 预览</strong><small>{{ status.text }}</small></span>
      </div>
      <div class="preview-pane__zoom" role="group" aria-label="预览缩放">
        <UiIconButton :icon="ZoomOut" size="sm" label="缩小" @click="step(-1)" />
        <button type="button" class="preview-pane__zoom-label" :title="zoom === 'fit' ? '当前为适应宽度' : '恢复适应宽度'" @click="zoom = 'fit'">{{ zoomLabel }}</button>
        <UiIconButton :icon="ZoomIn" size="sm" label="放大" @click="step(1)" />
        <UiIconButton :icon="Maximize2" size="sm" label="适应宽度" :active="zoom === 'fit'" @click="zoom = 'fit'" />
      </div>
      <button class="preview-template" type="button" @click="wb.openView('templates')">
        <span>当前模板</span>
        <strong>{{ design.activeTemplate.value?.displayName ?? '读取中' }}</strong>
        <ChevronRight :size="15" aria-hidden="true" />
      </button>
    </header>

    <div class="preview-pane__stage-shell scroll-edges" :class="{ 'can-scroll-up': surface.edges.canScrollUp, 'can-scroll-down': surface.edges.canScrollDown }">
      <div ref="stage" class="preview-pane__stage" tabindex="0" aria-label="A4 简历预览滚动区域" :style="{ '--pages-width': pagesWidth }" @scroll.passive="surface.onScroll">
        <ResumeTemplatePreview
          :resume="wb.previewResume.value"
          :content="wb.previewContent.value"
          :design="design.activeDesign.value"
          :template-id="design.activeTemplate.value?.templateId"
          :photo-url="conversation.photo?.contentUrl"
          :layout-definition-json="design.layoutJson.value"
          :renderer-protocol="design.rendererProtocol.value"
          :variant-code="design.activeVariant.value"
          interactive
          @section-click="wb.locateSection"
        />
      </div>
    </div>

    <footer class="preview-pane__foot">
      <span><Info :size="13" aria-hidden="true" />点击页面中的模块可直接编辑；只有确认后的内容会进入导出。</span>
      <RouterLink :to="`/resumes/${conversation.masterId}/manual#export`">查看历史版本</RouterLink>
    </footer>
  </section>
</template>

<style scoped>
.preview-pane {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
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

.preview-pane__stage :deep(.resume-pages) {
  width: var(--pages-width);
  transition: width var(--dur-slow) var(--ease-out);
}

.preview-pane__stage :deep(.resume-sheet) {
  border: 0;
  box-shadow: var(--shadow-paper, var(--shadow-lg));
}

.preview-pane__stage :deep(.resume-sheet__section[role='button']) {
  transition: outline-color var(--dur-fast), background-color var(--dur-fast);
}

.preview-pane__stage :deep(.resume-sheet__section[role='button']:focus-visible) {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}

.preview-pane__stage :deep(.resume-sheet__section.is-landed) {
  animation: section-landed 1.4s var(--ease-out);
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

@keyframes section-landed {
  0% {
    background-color: color-mix(in srgb, var(--color-accent) 26%, transparent);
    box-shadow: 0 0 0 6px color-mix(in srgb, var(--color-accent) 26%, transparent);
  }
  100% {
    background-color: transparent;
    box-shadow: 0 0 0 6px transparent;
  }
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
  .preview-pane__stage :deep(.resume-sheet__section.is-landed) {
    animation: none;
  }
}
</style>
