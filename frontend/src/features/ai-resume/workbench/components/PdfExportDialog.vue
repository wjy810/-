<script setup lang="ts">
import { computed } from 'vue'
import { CircleAlert, CircleCheck, EyeOff, FileText, LoaderCircle, RefreshCw, TriangleAlert } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import type { AiResumePdfExportMode } from '../../types'
import { useWorkbench } from '../useWorkbench'

const wb = useWorkbench()
const pdf = wb.pdf
const manifest = wb.design.manifest

const modes: Array<{ value: AiResumePdfExportMode; title: string; description: string; icon: typeof FileText }> = [
  { value: 'STANDARD', title: '正式版', description: '保留姓名、照片与联系方式', icon: FileText },
  { value: 'ANONYMOUS', title: '匿名版', description: '隐藏身份信息，保留职业事实', icon: EyeOff },
]

const paper = computed(() => (wb.design.activeDesign.value?.paperSize === 'LETTER' ? 'US Letter' : 'A4'))
const pagesText = computed(() => {
  const server = pdf.preview.value
  if (server) return `共 ${server.pageCount} 页 · 上限 ${server.pageLimit} 页`
  const local = wb.design.layoutResult.value
  return local ? `预计 ${local.pageCount} 页 · 上限 ${local.pageLimit} 页` : ''
})
const confirmLabel = computed(() => {
  if (pdf.overflowBlocked.value) return '超出页数，暂不可导出'
  return `确认并下载${pdf.mode.value === 'ANONYMOUS' ? '匿名版' : '正式版'}`
})
</script>

<template>
  <UiDialog
    :open="pdf.open.value"
    title="导出 PDF"
    description="冻结当前已确认的内容、模板与设计，由排版服务生成可选中文字的 PDF。"
    width="md"
    @close="pdf.close()"
  >
    <div v-if="pdf.outcome.value" class="pdf-dialog pdf-dialog--done">
      <p class="pdf-dialog__ready" role="status">
        <CircleCheck :size="16" aria-hidden="true" />
        {{ pdf.outcome.value.mode === 'ANONYMOUS' ? '匿名版' : '正式版' }} PDF 已开始下载{{ pdf.outcome.value.pageCount ? ` · 共 ${pdf.outcome.value.pageCount} 页` : '' }}
      </p>
      <section v-if="pdf.outcome.value.ats" class="ats" aria-label="招聘系统可读性检查">
        <h3>招聘系统可读性检查 <small>{{ pdf.outcome.value.ats.passed ? '全部通过' : '有项目未通过' }}</small></h3>
        <ul>
          <li v-for="check in pdf.outcome.value.checks" :key="check.code" :class="{ 'is-failed': !check.passed }">
            <CircleCheck v-if="check.passed" :size="14" aria-hidden="true" />
            <CircleAlert v-else :size="14" aria-hidden="true" />
            <span>{{ check.label }}<small v-if="check.detail">{{ check.detail }}</small></span>
          </li>
        </ul>
      </section>
      <p class="pdf-dialog__note">导出的版本保存在“版本与导出”中，可随时再次下载。你可以继续编辑，之后再导出新版本。</p>
    </div>

    <div v-else class="pdf-dialog">
      <div class="pdf-dialog__preview">
        <figure class="pdf-thumb" :aria-busy="pdf.previewPending.value">
          <img v-if="pdf.preview.value && !pdf.previewPending.value" :src="pdf.preview.value.firstPageImage" alt="排版服务生成的第一页预览">
          <UiSkeleton v-else-if="pdf.previewPending.value" height="100%" radius="2px" />
          <figcaption v-else class="pdf-thumb__error">
            <TriangleAlert :size="16" aria-hidden="true" />
            <span>{{ pdf.previewError.value || '暂无预览' }}</span>
            <UiButton size="sm" variant="ghost" :icon="RefreshCw" @click="pdf.loadPreview()">重试</UiButton>
          </figcaption>
        </figure>
        <div class="pdf-dialog__side">
          <div class="pdf-dialog__template">
            <FileText :size="18" aria-hidden="true" />
            <span><strong>{{ manifest?.name ?? wb.design.activeTemplate.value?.displayName ?? '当前模板' }}</strong><small>{{ paper }}<template v-if="pagesText"> · {{ pagesText }}</template></small></span>
          </div>

          <div class="pdf-dialog__modes" role="radiogroup" aria-label="PDF 导出版本">
            <button
              v-for="option in modes"
              :key="option.value"
              type="button"
              role="radio"
              class="pdf-mode"
              :aria-checked="pdf.mode.value === option.value"
              :disabled="pdf.pending.value"
              @click="pdf.mode.value = option.value"
            >
              <component :is="option.icon" :size="18" aria-hidden="true" />
              <strong>{{ option.title }}</strong>
              <span>{{ option.description }}</span>
            </button>
          </div>

          <ul v-if="pdf.risks.value.length" class="pdf-dialog__risks">
            <li v-for="risk in pdf.risks.value" :key="risk.text" :class="`is-${risk.tone}`">
              <CircleAlert v-if="risk.tone === 'danger'" :size="15" aria-hidden="true" />
              <TriangleAlert v-else :size="15" aria-hidden="true" />
              <span>{{ risk.text }}</span>
            </li>
          </ul>
          <p v-else class="pdf-dialog__ready"><CircleCheck :size="15" aria-hidden="true" />当前没有发现导出前风险。</p>
        </div>
      </div>

      <p class="pdf-dialog__note">{{ pdf.mode.value === 'ANONYMOUS'
        ? '匿名版会移除姓名、照片、邮箱、电话、地点和个人链接；岗位、教育、经历、项目和技能仍会保留。'
        : '预览中的未确认草稿和待确认修改不会写入正式 PDF。导出后会检查文字能否被招聘系统读取。' }}</p>

      <div v-if="pdf.pending.value" class="pdf-dialog__progress" role="status">
        <LoaderCircle :size="15" aria-hidden="true" />
        <span>{{ pdf.progressText.value }}</span>
        <progress v-if="pdf.progressPercent.value !== null" :value="pdf.progressPercent.value" max="100" :aria-label="`${pdf.progressPercent.value}%`" />
      </div>
      <p v-else-if="pdf.error.value" class="pdf-dialog__error" role="alert"><CircleAlert :size="15" aria-hidden="true" />{{ pdf.error.value }}</p>
    </div>

    <template #footer>
      <template v-if="pdf.outcome.value">
        <UiButton @click="pdf.close()">完成</UiButton>
      </template>
      <template v-else>
        <UiButton variant="ghost" @click="pdf.close()">{{ pdf.pending.value ? '后台继续' : '取消' }}</UiButton>
        <UiButton :pending="pdf.pending.value" :disabled="pdf.overflowBlocked.value" @click="pdf.confirmExport()">{{ confirmLabel }}</UiButton>
      </template>
    </template>
  </UiDialog>
</template>

<style scoped>
.pdf-dialog {
  display: grid;
  gap: var(--space-4);
}

.pdf-dialog__preview {
  display: grid;
  grid-template-columns: 168px minmax(0, 1fr);
  gap: var(--space-4);
  align-items: start;
}

.pdf-dialog__side {
  min-width: 0;
  display: grid;
  gap: var(--space-3);
}

.pdf-thumb {
  margin: 0;
  aspect-ratio: 210 / 297;
  overflow: hidden;
  border-radius: 2px;
  background: var(--sheet-bg);
  box-shadow: 0 0 0 1px var(--sheet-edge), var(--shadow-md);
}

.pdf-thumb img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.pdf-thumb__error {
  height: 100%;
  padding: var(--space-3);
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  text-align: center;
}

.ats {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.ats h3 {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.ats h3 small {
  color: var(--text-tertiary);
  font-weight: 500;
}

.ats ul {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ats li {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  gap: 6px;
  color: var(--color-success-text);
  font-size: var(--fs-xs);
}

.ats li span {
  display: grid;
  color: var(--text-secondary);
}

.ats li small {
  color: var(--color-danger-text);
}

.ats li.is-failed {
  color: var(--color-danger-text);
}

.pdf-dialog__progress progress {
  flex: 1;
  height: 6px;
  accent-color: var(--color-primary);
}

.pdf-dialog__template {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  color: var(--color-primary-text);
}

.pdf-dialog__template span {
  display: grid;
}

.pdf-dialog__template strong {
  color: var(--text-primary);
  font-size: var(--fs-sm);
}

.pdf-dialog__template small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.pdf-dialog__modes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.pdf-mode {
  min-width: 0;
  padding: var(--space-3);
  display: grid;
  justify-items: start;
  gap: 4px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
  color: var(--text-secondary);
  text-align: left;
  transition: border-color var(--dur-fast), background-color var(--dur-fast), box-shadow var(--dur-fast);
}

.pdf-mode strong {
  color: var(--text-primary);
  font-size: var(--fs-sm);
}

.pdf-mode span {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.45;
}

.pdf-mode:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.pdf-mode[aria-checked='true'] {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-text);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.pdf-mode:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.pdf-dialog__risks {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pdf-dialog__risks li {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  font-size: var(--fs-xs);
  line-height: 1.55;
}

.pdf-dialog__risks svg {
  margin-top: 1px;
}

.pdf-dialog__risks .is-warning {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.pdf-dialog__risks .is-danger {
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
  font-weight: 600;
}

.pdf-dialog__ready,
.pdf-dialog__progress,
.pdf-dialog__error {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-sm);
}

.pdf-dialog__ready {
  color: var(--color-success-text);
}

.pdf-dialog__progress {
  color: var(--color-primary-text);
}

.pdf-dialog__progress svg {
  animation: pdf-spin 0.9s linear infinite;
}

.pdf-dialog__error {
  color: var(--color-danger-text);
}

.pdf-dialog__note {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

@media (max-width: 560px) {
  .pdf-dialog__preview {
    grid-template-columns: minmax(0, 1fr);
  }

  .pdf-thumb {
    width: 60%;
    justify-self: center;
  }
}

@keyframes pdf-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
