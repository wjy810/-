<script setup lang="ts">
/**
 * Permanent deletion of a resume or a frozen version: previews what goes, asks for the typed
 * confirmation, then follows the deletion request until it completes before the row disappears.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import { errorMessage } from '@/shared/api/types'
import { fetchDeletion, fetchDeletionPreview, submitDeletion } from '@/features/datarights/services/dataRightsApi'
import type { DeletionPreview, DeletionView } from '@/features/datarights/types'
import type { ResumeRow } from '../utils/resumeRows'

const props = defineProps<{ target: ResumeRow | null }>()
const emit = defineEmits<{ close: []; deleted: [title: string] }>()

const preview = ref<DeletionPreview | null>(null)
const request = ref<DeletionView | null>(null)
const confirmation = ref('')
const loading = ref(false)
const error = ref('')
let generation = 0

const targetType = computed(() => (props.target?.kind === 'MASTER' ? 'RESUME_MASTER' : 'RESUME_VERSION'))
const targetId = computed(() => (props.target?.kind === 'MASTER' ? props.target.masterId : props.target?.versionId ?? ''))
const canSubmit = computed(() => confirmation.value.trim() === '永久删除' && preview.value?.canProceed === true && !loading.value)
/** Submitting or following the deletion (loading with a preview on screen); the button stays focusable while busy. */
const deleting = computed(() => loading.value && preview.value !== null)

function finished(view: DeletionView): boolean {
  return view.status === 'COMPLETED' || view.status === 'FAILED'
}

function reset(): void {
  preview.value = null
  request.value = null
  confirmation.value = ''
  error.value = ''
}

async function loadPreview(): Promise<void> {
  generation += 1
  reset()
  if (!props.target) return
  loading.value = true
  try {
    preview.value = await fetchDeletionPreview({ scope: 'OBJECT', targetType: targetType.value, targetId: targetId.value })
  } catch (reason) {
    error.value = errorMessage(reason, '无法读取永久删除影响范围')
  } finally {
    loading.value = false
  }
}

function close(): void {
  if (loading.value || (request.value && !finished(request.value))) return
  generation += 1
  reset()
  emit('close')
}

async function follow(requestId: string, current: number): Promise<DeletionView> {
  let view = request.value
  for (let attempt = 0; attempt < 120; attempt += 1) {
    if (current !== generation) throw new Error('删除状态检查已取消')
    if (view && finished(view)) return view
    await new Promise((resolve) => window.setTimeout(resolve, 500))
    view = await fetchDeletion(requestId)
    request.value = view
  }
  throw new Error('删除仍在后台处理中，请稍后刷新列表确认结果')
}

async function confirmDelete(): Promise<void> {
  const target = props.target
  if (!target || !canSubmit.value) return
  loading.value = true
  error.value = ''
  const current = ++generation
  try {
    const submitted = await submitDeletion({ scope: 'OBJECT', targetType: targetType.value, targetId: targetId.value, confirmationAck: true })
    request.value = submitted
    const completed = await follow(submitted.id, current)
    if (completed.status !== 'COMPLETED') {
      const receipt = completed.receipts?.find((item) => item.status === 'FAILED')
      throw new Error(receipt?.message || '永久删除失败，数据未从列表移除')
    }
    reset()
    emit('deleted', target.title)
  } catch (reason) {
    error.value = errorMessage(reason, '永久删除失败，数据未从列表移除')
  } finally {
    loading.value = false
  }
}

watch(() => props.target?.key, (key) => { if (key) void loadPreview() }, { immediate: true })
onBeforeUnmount(() => { generation += 1 })
</script>

<template>
  <UiDialog :open="Boolean(target)" :title="target?.kind === 'MASTER' ? '永久删除简历' : '永久删除冻结版本'" :width="520" @close="close">
    <div class="delete-dialog">
      <UiBanner tone="danger">
        删除后不会进入「已归档」，也无法恢复。简历内容和导出的文件都会被清除。
      </UiBanner>
      <div class="delete-dialog__target">
        <span class="delete-dialog__icon"><AppIcon name="trash" :size="18" /></span>
        <div>
          <strong>{{ target?.title }}</strong>
          <span>{{ target?.kind === 'MASTER' ? '这份简历及其全部版本和关联数据' : '仅这个冻结版本及其关联数据' }}</span>
        </div>
      </div>
      <p v-if="loading && !preview" class="muted">正在核对关联数据与运行中任务…</p>
      <template v-else-if="preview">
        <p class="delete-dialog__summary">{{ preview.impactSummary }}</p>
        <ul v-if="preview.impacts?.length" class="delete-impact" aria-label="永久删除影响范围">
          <li v-for="(item, index) in preview.impacts" :key="`${item.kind}-${index}`">
            <AppIcon name="x-circle" :size="14" />
            <span>{{ item.label || '关联数据' }}</span>
          </li>
        </ul>
        <UiBanner v-if="preview.canProceed === false" tone="warning">
          当前不能删除：该简历仍有任务运行中。请等待导出、定制或匹配任务结束后重试。
        </UiBanner>
      </template>
      <UiBanner v-if="error" tone="danger">{{ error }}</UiBanner>
      <UiBanner v-if="request && !finished(request)" tone="warning">
        删除申请已提交，正在清理关联数据。完成前不会从列表移除，请勿关闭此窗口。
      </UiBanner>
      <UiField id="permanent-delete-confirmation" label="输入“永久删除”确认" hint="必须完整输入四个字，避免误操作。">
        <template #default="{ id, describedBy }">
          <UiInput
            :id="id"
            v-model="confirmation"
            type="text"
            autocomplete="off"
            placeholder="永久删除"
            :aria-describedby="describedBy"
            :disabled="loading || preview?.canProceed !== true"
            @keydown.enter.prevent="confirmDelete"
          />
        </template>
      </UiField>
    </div>
    <template #footer>
      <UiButton variant="secondary" :disabled="loading" @click="close">取消</UiButton>
      <UiButton variant="danger" :pending="deleting" :disabled="!canSubmit && !deleting" @click="confirmDelete">
        {{ request ? '正在永久删除…' : '永久删除' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.delete-dialog {
  display: grid;
  gap: 14px;
}

.delete-dialog__target {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.delete-dialog__target > div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.delete-dialog__target strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-dialog__target span:not(.delete-dialog__icon) {
  color: var(--text-tertiary);
  font-size: 12px;
}

.delete-dialog__icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--color-danger);
  background: var(--color-danger-soft);
}

.delete-dialog__summary {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.delete-impact {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.delete-impact li {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xs);
  color: var(--text-secondary);
  font-size: 12px;
}

.delete-impact svg {
  flex: 0 0 auto;
  color: var(--color-danger);
}

@media (max-width: 560px) {
  .delete-impact {
    grid-template-columns: 1fr;
  }
}
</style>
