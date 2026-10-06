<script setup lang="ts">
import { computed } from 'vue'
import { CircleAlert, CircleCheck, EyeOff, FileText, LoaderCircle, TriangleAlert } from 'lucide-vue-next'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import type { AiResumePdfExportMode } from '../../types'
import { useWorkbench } from '../useWorkbench'

const wb = useWorkbench()
const pdf = wb.pdf

const modes: Array<{ value: AiResumePdfExportMode; title: string; description: string; icon: typeof FileText }> = [
  { value: 'STANDARD', title: '正式版', description: '保留姓名、照片与联系方式', icon: FileText },
  { value: 'ANONYMOUS', title: '匿名版', description: '隐藏身份信息，保留职业事实', icon: EyeOff },
]

const confirmLabel = computed(() => {
  if (pdf.overflowBlocked.value) return '存在溢出，暂不可导出'
  return `确认并下载${pdf.mode.value === 'ANONYMOUS' ? '匿名版' : '正式版'}`
})
</script>

<template>
  <UiDialog
    :open="pdf.open.value"
    title="导出 PDF"
    description="将冻结当前已确认内容、模板版本和设计设置，再异步生成 PDF。"
    width="sm"
    @close="pdf.open.value = false"
  >
    <div class="pdf-dialog">
      <div class="pdf-dialog__template">
        <FileText :size="18" aria-hidden="true" />
        <span><strong>{{ wb.design.activeTemplate.value?.displayName ?? '当前智能模板' }}</strong><small>{{ wb.design.activeTemplate.value?.recommendedPages ?? '1' }} 页 · A4</small></span>
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

      <p class="pdf-dialog__note">{{ pdf.mode.value === 'ANONYMOUS'
        ? '匿名版会移除姓名、照片、邮箱、电话、地点和个人链接；岗位、教育、经历、项目和技能仍会保留。'
        : '预览中的未确认草稿和待确认修改不会写入正式 PDF。' }}</p>

      <p v-if="pdf.pending.value" class="pdf-dialog__progress" role="status"><LoaderCircle :size="15" aria-hidden="true" />{{ pdf.progressText.value }}</p>
      <p v-else-if="pdf.error.value" class="pdf-dialog__error" role="alert"><CircleAlert :size="15" aria-hidden="true" />{{ pdf.error.value }}</p>
    </div>

    <template #footer>
      <UiButton variant="ghost" @click="pdf.open.value = false">{{ pdf.pending.value ? '后台继续' : '取消' }}</UiButton>
      <UiButton :pending="pdf.pending.value" :disabled="pdf.overflowBlocked.value" @click="pdf.confirmExport()">{{ confirmLabel }}</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.pdf-dialog {
  display: grid;
  gap: var(--space-4);
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

@keyframes pdf-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
