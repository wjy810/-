<script setup lang="ts">
/**
 * AI resume workbench: conversation / edit / design / template views beside a live A4 preview.
 * This page only assembles the workbench; behaviour lives in ../workbench (docs/03 §4.7).
 */
import { computed, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { FileText, PanelsTopLeft } from 'lucide-vue-next'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useSplitPane } from '@/shared/composables/useSplitPane'
import type { MobilePane } from '../workbench/types'
import { createWorkbench, provideWorkbench } from '../workbench/useWorkbench'
import ConversationView from '../workbench/components/ConversationView.vue'
import DesignView from '../workbench/components/DesignView.vue'
import ModuleEditorView from '../workbench/components/ModuleEditorView.vue'
import PdfExportDialog from '../workbench/components/PdfExportDialog.vue'
import PreviewPane from '../workbench/components/PreviewPane.vue'
import TemplateView from '../workbench/components/TemplateView.vue'
import ToolDrawer from '../workbench/components/ToolDrawer.vue'
import WorkbenchHeader from '../workbench/components/WorkbenchHeader.vue'
import WorkbenchSplitter from '../workbench/components/WorkbenchSplitter.vue'
import WorkbenchTabs from '../workbench/components/WorkbenchTabs.vue'

const route = useRoute()
const wb = createWorkbench(() => String(route.params.conversationId), { source: () => route.query.source })
provideWorkbench(wb)

const conversation = wb.session.conversation
const grid = ref<HTMLElement | null>(null)
const split = useSplitPane({ key: 'ai-resume-workbench', container: grid, defaultRatio: 42, minStart: 420, minEnd: 380, bounds: [30, 66] })

const mobilePanes: Array<{ value: MobilePane; label: string; icon: typeof FileText }> = [
  { value: 'workspace', label: '编辑区', icon: PanelsTopLeft },
  { value: 'preview', label: 'A4 预览', icon: FileText },
]
const showWorkspace = computed(() => !wb.isMobile.value || wb.mobilePane.value === 'workspace')
const showPreview = computed(() => !wb.isMobile.value || wb.mobilePane.value === 'preview')

// Leaving with unsaved drafts: give the save queues a moment instead of dropping keystrokes.
onBeforeRouteLeave(async () => {
  if (!wb.hasUnsavedWork()) return true
  await Promise.race([
    Promise.all([wb.drafts.flushAll(), wb.design.persist()]),
    new Promise(resolve => setTimeout(resolve, 4000)),
  ])
  return true
})
</script>

<template>
  <main class="workbench">
    <WorkbenchHeader />

    <div v-if="wb.isMobile.value && conversation" class="workbench__mobile-switch">
      <UiSegmented v-model="wb.mobilePane.value" :items="mobilePanes" aria-label="切换编辑区与预览" block />
    </div>

    <section v-if="wb.session.loading.value" class="workbench__loading" aria-busy="true" aria-label="正在恢复长期对话与简历版本">
      <div class="workbench__loading-side">
        <UiSkeleton height="40px" radius="var(--radius-md)" />
        <UiSkeleton height="64px" width="78%" radius="var(--radius-lg)" />
        <UiSkeleton height="220px" radius="var(--radius-xl)" />
        <UiSkeleton :lines="3" />
      </div>
      <div class="workbench__loading-paper"><UiSkeleton height="100%" radius="2px" /></div>
    </section>

    <div v-else-if="!conversation" class="workbench__error">
      <UiErrorState :error="wb.session.loadError.value" title="AI 工作台读取失败" @retry="wb.session.load(true)" />
    </div>

    <div
      v-else
      ref="grid"
      class="workbench__grid"
      :class="{ 'is-dragging': split.dragging.value, 'is-mobile': wb.isMobile.value }"
      :style="{ '--split': `${split.ratio.value}%` }"
    >
      <section v-show="showWorkspace" class="workbench__workspace" aria-label="编辑区">
        <div class="workbench__tabs">
          <WorkbenchTabs :model-value="wb.view.value" @update:model-value="wb.openView" />
        </div>
        <div class="workbench__view">
          <Transition name="wb-view" mode="out-in">
            <KeepAlive include="ConversationView">
              <ConversationView v-if="wb.view.value === 'conversation'" />
              <ModuleEditorView v-else-if="wb.view.value === 'edit'" />
              <DesignView v-else-if="wb.view.value === 'design'" />
              <TemplateView v-else />
            </KeepAlive>
          </Transition>
        </div>
      </section>

      <WorkbenchSplitter
        v-if="!wb.isMobile.value"
        :value="split.ratio.value"
        :min="split.limits.value.min"
        :max="split.limits.value.max"
        :dragging="split.dragging.value"
        v-on="split.handlers"
      />

      <PreviewPane v-show="showPreview" />
    </div>

    <ToolDrawer />
    <PdfExportDialog />
  </main>
</template>

<style scoped>
.workbench {
  height: 100vh;
  height: 100dvh;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  overflow: hidden;
  background: var(--bg-app);
}

.workbench__mobile-switch {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface-1);
}

.workbench__grid {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, var(--split, 42%)) auto minmax(0, 1fr);
}

.workbench__grid.is-dragging {
  cursor: col-resize;
  user-select: none;
}

.workbench__grid.is-dragging :deep(.resume-pages) {
  transition: none;
}

.workbench__grid.is-mobile {
  grid-template-columns: minmax(0, 1fr);
}

.workbench__workspace {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  background: var(--surface-1);
}

.workbench__tabs {
  padding: var(--space-3) var(--space-5) var(--space-2);
}

.workbench__view {
  min-height: 0;
  position: relative;
}

.workbench__view > * {
  height: 100%;
}

.workbench__loading {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 42%) minmax(0, 1fr);
}

.workbench__loading-side {
  display: grid;
  align-content: start;
  gap: var(--space-4);
  padding: var(--space-5);
  border-right: 1px solid var(--border-subtle);
  background: var(--surface-1);
}

.workbench__loading-paper {
  padding: var(--space-6) max(var(--space-6), calc((100% - 640px) / 2));
  background: var(--bg-sunken);
}

.workbench__loading-paper :deep(.ui-skeleton) {
  aspect-ratio: 210 / 297;
  height: auto !important;
  max-height: 100%;
}

.workbench__error {
  min-height: 0;
  display: grid;
  place-items: center;
  padding: var(--space-8);
}

.wb-view-enter-active,
.wb-view-leave-active {
  transition: opacity 140ms var(--ease-out), transform 180ms var(--ease-out);
}

.wb-view-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.wb-view-leave-to {
  opacity: 0;
}

@media (max-width: 899px) {
  .workbench__loading {
    grid-template-columns: minmax(0, 1fr);
  }

  .workbench__loading-paper {
    display: none;
  }
}

@media (max-width: 640px) {
  .workbench__tabs {
    padding: var(--space-2) var(--space-3);
  }
}

@media (prefers-reduced-motion: reduce) {
  .wb-view-enter-active,
  .wb-view-leave-active {
    transition: none;
  }
}
</style>
