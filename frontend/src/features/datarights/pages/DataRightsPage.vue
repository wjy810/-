<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import ForbidState from '@/shared/ui/ForbidState.vue'
import PageState from '@/shared/ui/PageState.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { fetchTask, isCancellableTask, isManualRetryTask, isOpenTask, type TaskView } from '@/shared/api/task'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import { downloadCareerHistory, fetchCareerHistory } from '@/features/career-library/services/careerLibraryApi'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import {
  deletionLooksFinished,
  deletionProgressNote,
  exportDownloadDisabledReason,
  glossBlocker,
  glossDeletionStatus,
  glossHistoryType,
  glossImpactRelation,
  glossReceiptModule,
  glossReceiptStatus,
  glossTargetType,
  glossTaskStatus,
  impactRowTone,
  isOpenDeletion,
  isPollingDeletion,
  objectSubmitBlockedReason,
  previewIsObjectScoped,
} from '../labels'
import {
  cancelExport,
  createExport,
  downloadExportByPath,
  downloadExportBytes,
  fetchDeletion,
  fetchDeletionPreview,
  fetchExport,
  isSafeHttpDownloadUrl,
  isSessionApiDownloadPath,
  retryExport,
  submitDeletion,
} from '../services/dataRightsApi'
import type { DeletionPreview, DeletionView, ExportView } from '../types'

const route = useRoute()
const pending = ref('')
const formError = ref('')
const notice = ref('')
useToastFeedback(notice, 'success', 'data-rights-page-notice')
const previewState = useLoadState(() => fetchDeletionPreview())
const historyState = useLoadState(() => fetchCareerHistory())
const preview = computed(() => previewState.data.value)
const careerHistory = computed(() => historyState.data.value)
const loading = computed(() => previewState.loading.value || historyState.loading.value)
const actionForbidden = ref<unknown>(null)
const forbidden = computed(() => actionForbidden.value
  ?? [previewState.error.value, historyState.error.value].find((error) => isForbidden(error)) ?? null)

const objectPreview = ref<DeletionPreview | null>(null)
const exportView = ref<ExportView | null>(null)
const exportTask = ref<TaskView | null>(null)
const deletionView = ref<DeletionView | null>(null)

const exportAck = ref(false)
const accountAckImpact = ref(false)
const accountAckIrreversible = ref(false)
const accountConfirmOpen = ref(false)
const objectAckImpact = ref(false)
const objectConfirmOpen = ref(false)
const objectForm = reactive({ targetType: '', targetId: '' })

let exportPoll: AbortController | null = null
let deletionPoll: AbortController | null = null

const objectBlocked = computed(() =>
  objectSubmitBlockedReason(objectPreview.value, objectForm.targetType, objectForm.targetId),
)
const objectImpacts = computed(() => objectPreview.value?.impacts ?? [])
const objectBlockers = computed(() =>
  (objectPreview.value?.blockers ?? []).map((item) => String(item).trim()).filter(Boolean).map(glossBlocker),
)
const accountDeletionOpen = computed(
  () => deletionView.value?.scope !== 'OBJECT' && isOpenDeletion(deletionView.value?.status),
)
const objectDeletionOpen = computed(
  () => deletionView.value?.scope === 'OBJECT' && isOpenDeletion(deletionView.value?.status),
)
const deletionBusy = computed(() => isOpenDeletion(deletionView.value?.status))
const exportBusy = computed(() => isOpenTask(exportView.value?.taskStatus ?? exportTask.value?.status))
const downloadBlock = computed(() =>
  exportDownloadDisabledReason(exportView.value, exportTask.value?.status),
)
const canStartExport = computed(() => exportAck.value && !exportBusy.value && !deletionBusy.value)
// The impact summary must have loaded: the user is confirming they have read it.
const canSubmitAccountDeletion = computed(
  () => Boolean(preview.value) && accountAckImpact.value && accountAckIrreversible.value && !deletionBusy.value,
)
const canSubmitObjectDeletion = computed(
  () => !objectBlocked.value && objectAckImpact.value && !deletionBusy.value,
)
const canDownload = computed(() => !downloadBlock.value && !!exportView.value)
const canRetryExport = computed(() =>
  isManualRetryTask(exportView.value?.taskStatus ?? exportTask.value?.status),
)
const canCancelExport = computed(() =>
  isCancellableTask(exportView.value?.taskStatus ?? exportTask.value?.status),
)

function queryValue(key: string): string {
  const raw = route.query[key]
  const value = Array.isArray(raw) ? raw[0] : raw
  return typeof value === 'string' ? value.trim() : ''
}

function stopExportPoll(): void {
  exportPoll?.abort()
  exportPoll = null
}

function stopDeletionPoll(): void {
  deletionPoll?.abort()
  deletionPoll = null
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('轮询已取消', 'AbortError'))
      return
    }
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    const onAbort = (): void => {
      window.clearTimeout(timer)
      reject(new DOMException('轮询已取消', 'AbortError'))
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

async function pollDeletionUntilSettled(id: string): Promise<void> {
  stopDeletionPoll()
  const controller = new AbortController()
  deletionPoll = controller
  const started = Date.now()
  let current = await fetchDeletion(id)
  deletionView.value = current
  while (isPollingDeletion(current.status)) {
    if (controller.signal.aborted) {
      throw new DOMException('轮询已取消', 'AbortError')
    }
    if (Date.now() - started > 180_000) {
      throw new Error('删除仍在处理中，页面已停止自动刷新，可稍后点“刷新删除进度”查看。')
    }
    await wait(1400, controller.signal)
    current = await fetchDeletion(id)
    deletionView.value = current
  }
}

async function followExport(view: ExportView): Promise<void> {
  exportView.value = view
  const taskId = view.taskId
  if (!taskId) {
    return
  }
  stopExportPoll()
  const controller = new AbortController()
  exportPoll = controller
  const finished = await pollTask(
    taskId,
    (tick) => {
      exportTask.value = tick
    },
    controller.signal,
  )
  exportTask.value = finished
  exportView.value = await fetchExport(view.id)
}

async function loadObjectPreview(): Promise<void> {
  const targetType = objectForm.targetType.trim()
  const targetId = objectForm.targetId.trim()
  if (!targetType || !targetId) {
    objectPreview.value = null
    return
  }
  objectPreview.value = await fetchDeletionPreview({
    scope: 'OBJECT',
    targetType,
    targetId,
  })
}

function applyTargetFromQuery(): void {
  const targetType = queryValue('targetType')
  const targetId = queryValue('targetId')
  if (targetType) {
    objectForm.targetType = targetType.toUpperCase()
  }
  if (targetId) {
    objectForm.targetId = targetId
  }
}

/** Follows an export or deletion that a notification linked to. Failures stay inline; the rest of the page works. */
async function hydrateFromQuery(): Promise<void> {
  const exportId = queryValue('exportId')
  const deletionId = queryValue('deletionId')
  const taskId = queryValue('taskId')
  const kind = queryValue('kind')
  try {
    if (exportId) {
      const view = await fetchExport(exportId)
      await followExport(view)
    } else if (taskId && kind !== 'deletion') {
      exportTask.value = await fetchTask(taskId)
    }
    if (deletionId) {
      await pollDeletionUntilSettled(deletionId)
    } else if (taskId && kind === 'deletion') {
      formError.value = '这个链接无法显示删除进度。请从通知中心的“删除进度”通知进入查看。'
    }
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    if (isForbidden(error)) {
      actionForbidden.value = error
      return
    }
    formError.value = errorMessage(error, '处理进度读取失败')
  }
}

/** Previews a record or file the career library sent here for permanent deletion. */
async function loadObjectFromQuery(): Promise<void> {
  if (!objectForm.targetType.trim() || !objectForm.targetId.trim()) {
    objectPreview.value = null
    return
  }
  try {
    await loadObjectPreview()
  } catch (error) {
    objectPreview.value = null
    if (isForbidden(error)) {
      actionForbidden.value = error
      return
    }
    formError.value = errorMessage(error, '所选数据的删除影响读取失败')
  }
  await nextTick()
  document.getElementById('object')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function loadPage(): Promise<void> {
  stopExportPoll()
  stopDeletionPoll()
  formError.value = ''
  notice.value = ''
  actionForbidden.value = null
  applyTargetFromQuery()
  await Promise.all([previewState.load(), historyState.load(), loadObjectFromQuery(), hydrateFromQuery()])
}

async function onDownloadCareerHistory(format: 'json' | 'md'): Promise<void> {
  pending.value = `career-history-${format}`
  formError.value = ''
  try {
    await downloadCareerHistory(format)
  } catch (error) {
    formError.value = errorMessage(error, '历史记录下载失败')
  } finally {
    pending.value = ''
  }
}

async function onRefreshPreview(): Promise<void> {
  formError.value = ''
  notice.value = ''
  objectConfirmOpen.value = false
  pending.value = 'preview'
  try {
    await loadObjectPreview()
    notice.value = previewIsObjectScoped(objectPreview.value)
      ? '已读取删除影响，请确认后再提交。'
      : '尚未确认所选数据的删除范围，暂不能提交，请重新预览删除影响。'
  } catch (error) {
    objectPreview.value = null
    if (isForbidden(error)) {
      actionForbidden.value = error
      return
    }
    formError.value = errorMessage(error, '删除影响读取失败')
  } finally {
    pending.value = ''
  }
}

async function onCreateExport(): Promise<void> {
  formError.value = ''
  notice.value = ''
  if (!exportAck.value) {
    formError.value = '请先勾选确认：导出完成后需在有效期内下载。'
    return
  }
  if (exportBusy.value || deletionBusy.value) {
    formError.value = deletionBusy.value
      ? '账号注销正在处理，暂不能导出。'
      : '已有导出在进行中，请等它完成。'
    return
  }
  pending.value = 'export'
  try {
    const created = await createExport(crypto.randomUUID())
    notice.value = '已开始导出，完成后可以在这里限时下载。'
    await followExport(created)
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    if (isForbidden(error)) {
      actionForbidden.value = error
      return
    }
    formError.value = errorMessage(error, '发起导出失败')
  } finally {
    pending.value = ''
  }
}

async function onCancelExport(): Promise<void> {
  if (!exportView.value) {
    return
  }
  formError.value = ''
  pending.value = 'export-cancel'
  stopExportPoll()
  try {
    exportView.value = await cancelExport(exportView.value.id)
    if (exportView.value.taskId) {
      exportTask.value = await fetchTask(exportView.value.taskId)
    }
    notice.value = '已请求取消导出。'
  } catch (error) {
    formError.value = errorMessage(error, '取消导出失败')
  } finally {
    pending.value = ''
  }
}

async function onRetryExport(): Promise<void> {
  if (!exportView.value) {
    return
  }
  formError.value = ''
  pending.value = 'export-retry'
  try {
    const next = await retryExport(exportView.value.id)
    notice.value = '已重新开始导出。'
    await followExport(next)
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    formError.value = errorMessage(error, '重试导出失败')
  } finally {
    pending.value = ''
  }
}

async function onRefreshExport(): Promise<void> {
  if (!exportView.value && !exportTask.value) {
    return
  }
  formError.value = ''
  pending.value = 'export-refresh'
  try {
    if (exportView.value) {
      exportView.value = await fetchExport(exportView.value.id)
      if (exportView.value.taskId) {
        exportTask.value = await fetchTask(exportView.value.taskId)
      }
    } else if (exportTask.value) {
      exportTask.value = await fetchTask(exportTask.value.id)
    }
  } catch (error) {
    formError.value = errorMessage(error, '导出进度读取失败')
  } finally {
    pending.value = ''
  }
}

function triggerBrowserDownload(blob: Blob, filename: string): void {
  const href = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = href
  link.download = filename
  link.rel = 'noopener'
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(href), 1000)
}

async function onDownloadExport(): Promise<void> {
  const view = exportView.value
  if (!view) {
    return
  }
  const blocked = exportDownloadDisabledReason(view, exportTask.value?.status)
  if (blocked) {
    formError.value = blocked
    return
  }
  formError.value = ''
  pending.value = 'download'
  try {
    const namedUrl = view.downloadUrl?.trim()
    if (namedUrl && isSessionApiDownloadPath(namedUrl)) {
      const file = await downloadExportByPath(namedUrl)
      triggerBrowserDownload(file.blob, file.filename || 'jobproof-export.json')
      notice.value = '已开始下载导出文件。'
      return
    }
    const fileId = view.fileId?.trim()
    if (fileId) {
      const file = await downloadExportByPath(`/api/v1/files/${encodeURIComponent(fileId)}/download`)
      triggerBrowserDownload(file.blob, file.filename || 'jobproof-export.json')
      notice.value = '已开始下载导出文件。'
      return
    }
    if (view.downloadAvailable) {
      const file = await downloadExportBytes(view.id)
      triggerBrowserDownload(file.blob, file.filename || 'jobproof-export.json')
      notice.value = '已开始下载导出文件。'
      return
    }
    if (namedUrl && isSafeHttpDownloadUrl(namedUrl)) {
      window.open(namedUrl, '_blank', 'noopener,noreferrer')
      notice.value = '已打开下载链接；若链接过期，请重新导出。'
      return
    }
    formError.value = namedUrl
      ? '下载链接无效，已停止下载。请刷新进度或重新导出。'
      : '导出文件暂不可下载，请刷新进度或重新导出。'
  } catch (error) {
    formError.value = errorMessage(error, '下载失败')
  } finally {
    pending.value = ''
  }
}

async function onSubmitAccountDeletion(): Promise<void> {
  formError.value = ''
  notice.value = ''
  if (!accountAckImpact.value || !accountAckIrreversible.value) {
    formError.value = '请先阅读删除影响，并勾选两项确认。'
    return
  }
  if (!accountConfirmOpen.value) {
    accountConfirmOpen.value = true
    notice.value = '请再点一次按钮，确认提交注销申请。'
    return
  }
  if (deletionBusy.value) {
    formError.value = '已有删除申请在处理，请等它完成后再提交。'
    return
  }
  pending.value = 'delete-account'
  try {
    const submitted = await submitDeletion({
      scope: 'ACCOUNT',
      confirmationAck: true,
    })
    deletionView.value = submitted
    notice.value = '注销申请已提交，请留意后续处理进度。'
    accountConfirmOpen.value = false
    if (isPollingDeletion(submitted.status)) {
      await pollDeletionUntilSettled(submitted.id)
    }
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    if (isForbidden(error)) {
      actionForbidden.value = error
      return
    }
    formError.value = errorMessage(error, '提交注销申请失败')
  } finally {
    pending.value = ''
  }
}

async function onSubmitObjectDeletion(): Promise<void> {
  formError.value = ''
  notice.value = ''
  const blocked = objectSubmitBlockedReason(objectPreview.value, objectForm.targetType, objectForm.targetId)
  if (blocked) {
    formError.value = blocked
    return
  }
  if (!objectAckImpact.value) {
    formError.value = '请先勾选：已阅读删除影响，确认要永久删除这些数据。'
    return
  }
  if (!objectConfirmOpen.value) {
    objectConfirmOpen.value = true
    notice.value = '请再点一次按钮，确认永久删除所选数据。'
    return
  }
  if (accountDeletionOpen.value) {
    formError.value = '账号注销正在处理，暂不能单独删除数据。'
    return
  }
  if (objectDeletionOpen.value) {
    formError.value = '已有一项数据删除申请在处理，请等它完成后再提交。'
    return
  }
  pending.value = 'delete-object'
  try {
    const submitted = await submitDeletion({
      scope: 'OBJECT',
      targetType: objectForm.targetType.trim(),
      targetId: objectForm.targetId.trim(),
      confirmationAck: true,
    })
    deletionView.value = submitted
    if (submitted.scope !== 'OBJECT') {
      notice.value = '返回的删除范围与所选数据不一致，请保留申请编号并联系支持。'
    } else if (deletionLooksFinished(submitted.status)) {
      notice.value = '所选数据的删除申请已处理完成，具体范围见下方处理结果。'
    } else {
      notice.value = '所选数据的删除申请已提交，请留意后续处理进度。'
    }
    objectConfirmOpen.value = false
    if (isPollingDeletion(submitted.status)) {
      await pollDeletionUntilSettled(submitted.id)
    }
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    if (isForbidden(error)) {
      actionForbidden.value = error
      return
    }
    formError.value = errorMessage(error, '提交删除申请失败')
  } finally {
    pending.value = ''
  }
}

async function onRefreshDeletion(): Promise<void> {
  if (!deletionView.value) {
    return
  }
  formError.value = ''
  pending.value = 'delete-refresh'
  try {
    deletionView.value = await fetchDeletion(deletionView.value.id)
  } catch (error) {
    formError.value = errorMessage(error, '删除进度读取失败')
  } finally {
    pending.value = ''
  }
}

watch(
  () => [route.query.exportId, route.query.deletionId, route.query.taskId, route.query.targetType, route.query.targetId],
  () => {
    void loadPage()
  },
)

watch(
  () => [objectForm.targetType, objectForm.targetId],
  () => {
    objectConfirmOpen.value = false
  },
)

onMounted(() => {
  void loadPage()
})

onUnmounted(() => {
  stopExportPoll()
  stopDeletionPoll()
})
</script>

<template>
  <ForbidState v-if="forbidden" :error="forbidden" />
  <div v-else class="settings-section data-rights-section">
      <header class="settings-section__head">
        <div class="settings-section__title">
          <span class="settings-section__icon"><AppIcon name="shield" :size="18" /></span>
          <div>
            <h2>授权与数据权利</h2>
            <p>导出个人数据、查看保留范围，并管理需要删除的账号数据。</p>
          </div>
        </div>
        <div class="settings-section__actions">
          <AppButton variant="ghost" :pending="loading" @click="loadPage">
            <AppIcon name="refresh" :size="15" />{{ loading ? '正在读取…' : '刷新数据' }}
          </AppButton>
        </div>
      </header>

          <AppBanner v-if="formError" tone="bad">{{ formError }}</AppBanner>
          <AppBanner v-if="deletionBusy" tone="warn">
            删除申请{{ glossDeletionStatus(deletionView?.status) }}，请等待处理结果，暂不能重复提交。
          </AppBanner>

          <div class="rights-layout">
            <div class="rights-main">
              <section id="preview" class="settings-card preview-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">删除前影响说明</h3>
                    <p class="card__sub">提交任何删除申请前，请先确认数据范围和不可逆影响。</p>
                  </div>
                </header>
                <div class="card__body">
          <PageState :loading="previewState.loading.value" :error="previewState.error.value" :loaded="previewState.loaded.value" error-title="删除影响说明读取失败" compact @retry="previewState.load">
            <template v-if="preview">
              <AppBanner tone="warn">
                <p class="verbatim">{{ preview.impactSummary || '暂未取得删除影响说明，请刷新后再确认。' }}</p>
              </AppBanner>
              <p class="fine">依法保留的数据</p>
              <p class="verbatim">{{ preview.legalExceptionNote || '暂未取得法定保留范围说明。' }}</p>
              <p v-if="preview.statusHint" class="fine">{{ preview.statusHint }}</p>
              <AppBanner v-if="!previewIsObjectScoped(preview)" tone="ink">以上是注销整个账号的影响。只想删除某一条资料，请使用下方“删除单项数据”。</AppBanner>
            </template>
          </PageState>
                </div>
              </section>

              <section id="career-history" class="settings-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">历史求职记录</h3>
                    <p class="card__sub">已下线功能留下的投递、面试和复盘记录只读保留，可以下载备份，但不能再编辑。</p>
                  </div>
                  <AppTag v-if="careerHistory" tone="gray">{{ careerHistory.total }} 条</AppTag>
                </header>
                <div class="card__body">
                  <PageState class="history-archive" :loading="historyState.loading.value" :error="historyState.error.value" :loaded="historyState.loaded.value" :empty="!careerHistory?.total" error-title="历史记录读取失败" compact @retry="historyState.load">
                    <template #empty><AppBanner tone="ink">你的账号没有需要保留的历史求职记录。</AppBanner></template>
                    <div v-if="careerHistory" class="history-counts">
                      <div v-for="(count, type) in careerHistory.counts" :key="type"><span>{{ glossHistoryType(String(type)) }}</span><strong>{{ count }}</strong></div>
                    </div>
                    <p v-if="careerHistory?.archivedAt" class="fine">最近归档：{{ formatWhen(careerHistory.archivedAt) }}</p>
                    <div class="btn-row">
                      <AppButton variant="ghost" :pending="pending === 'career-history-json'" @click="onDownloadCareerHistory('json')"><AppIcon name="download" :size="14" />下载 JSON</AppButton>
                      <AppButton variant="ghost" :pending="pending === 'career-history-md'" @click="onDownloadCareerHistory('md')"><AppIcon name="download" :size="14" />下载 Markdown</AppButton>
                    </div>
                  </PageState>
                </div>
              </section>

              <section id="export" class="settings-card export-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">导出我的数据</h3>
                    <p class="card__sub">生成一份包含求职资料、简历和历史求职记录的副本，完成后可限时下载。</p>
                  </div>
                  <AppTag v-if="exportView || exportTask" tone="blue">{{ glossTaskStatus(exportView?.taskStatus || exportTask?.status) }}</AppTag>
                </header>
                <div class="card__body">
          <label class="ack">
            <input v-model="exportAck" type="checkbox" :disabled="pending !== ''" />
            <span>我已知晓：导出完成后需在有效期内下载，过期后需要重新导出。</span>
          </label>
          <div class="btn-row">
            <AppButton
              :disabled="!canStartExport"
              :pending="pending === 'export'"
              @click="onCreateExport"
            >
              {{ pending === 'export' ? '正在发起导出…' : '发起导出' }}
            </AppButton>
            <AppButton
              variant="ghost"
              :disabled="!exportView && !exportTask"
              :pending="pending === 'export-refresh'"
              @click="onRefreshExport"
            >
              重新读取进度
            </AppButton>
            <AppButton
              variant="ink"
              :disabled="!canCancelExport"
              :pending="pending === 'export-cancel'"
              @click="onCancelExport"
            >
              取消导出
            </AppButton>
            <AppButton
              variant="ghost"
              :disabled="!canRetryExport"
              :pending="pending === 'export-retry'"
              @click="onRetryExport"
            >
              重新导出
            </AppButton>
            <AppButton
              variant="wax"
              :disabled="!canDownload"
              :pending="pending === 'download'"
              @click="onDownloadExport"
            >
              {{ pending === 'download' ? '正在下载…' : '限时下载' }}
            </AppButton>
          </div>
          <p v-if="downloadBlock" class="lock-note">{{ downloadBlock }}</p>
          <dl v-if="exportView || exportTask" class="rights-dl">
            <div>
              <dt>状态</dt>
              <dd>{{ glossTaskStatus(exportView?.taskStatus || exportTask?.status) }}</dd>
            </div>
            <div v-if="exportView?.createdAt">
              <dt>发起时间</dt>
              <dd>{{ formatWhen(exportView.createdAt) }}</dd>
            </div>
            <div v-if="exportView?.downloadExpiresAt">
              <dt>下载截止</dt>
              <dd>{{ formatWhen(exportView.downloadExpiresAt) }}</dd>
            </div>
            <div v-if="exportTask?.failureReason || exportView?.failureReason">
              <dt>失败原因</dt>
              <dd>{{ exportTask?.failureReason || exportView?.failureReason }}</dd>
            </div>
          </dl>
          <p v-else class="empty">还没有导出记录。勾选上方说明后即可发起，完成后会开放限时下载。</p>
                </div>
              </section>

              <section id="deletion" class="settings-card danger-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">注销账号</h3>
                    <p class="card__sub">这是高风险操作。提交后进入删除流程，处理期间部分功能可能受限。</p>
                  </div>
                </header>
                <div class="card__body">
          <label class="ack">
            <input v-model="accountAckImpact" type="checkbox" :disabled="pending !== '' || deletionBusy" />
            <span>我已阅读删除影响说明，理解提交后账号会进入处理流程。</span>
          </label>
          <label class="ack">
            <input v-model="accountAckIrreversible" type="checkbox" :disabled="pending !== '' || deletionBusy" />
            <span>我确认处理期间部分功能可能受限，并了解依法需要保留的数据不会提前删除。</span>
          </label>
          <p v-if="!preview" class="lock-note">删除影响说明还没有读取成功，读取后才能提交注销申请。</p>
          <div class="btn-row">
            <AppButton
              variant="danger"
              :disabled="!canSubmitAccountDeletion || pending !== ''"
              :pending="pending === 'delete-account'"
              @click="onSubmitAccountDeletion"
            >
              {{
                pending === 'delete-account'
                  ? '正在提交…'
                  : accountConfirmOpen
                    ? '再次确认：提交注销申请'
                    : '申请注销账号'
              }}
            </AppButton>
            <AppButton
              v-if="deletionView"
              variant="ghost"
              :pending="pending === 'delete-refresh'"
              @click="onRefreshDeletion"
            >
              刷新删除进度
            </AppButton>
          </div>
                </div>
              </section>

              <section id="object" class="settings-card advanced-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">删除单项数据</h3>
                    <p class="card__sub">永久删除一条经历、一个资料文件或整个职业主档。可以在求职资料库的“更多操作 → 永久删除”进入，编号会自动填好。</p>
                  </div>
                </header>
                <div class="card__body">
          <div class="split-fields">
            <AppField id="object-type" label="要删除的数据类型" hint="请选择后再填写对应的数据编号">
              <AppSelect id="object-type" v-model="objectForm.targetType" aria-label="要删除的数据类型" :disabled="pending !== '' || deletionBusy">
                <option value="">请选择数据类型</option>
                <option value="CAREER_RECORD">经历与成果记录</option>
                <option value="CAREER_FILE">资料文件</option>
                <option value="CAREER_PROFILE">职业主档</option>
              </AppSelect>
            </AppField>
            <AppField id="object-id" label="数据编号" hint="从求职资料库进入时会自动填写">
              <input
                id="object-id"
                v-model="objectForm.targetId"
                :disabled="pending !== '' || deletionBusy"
                autocomplete="off"
              />
            </AppField>
          </div>
          <div class="btn-row">
            <AppButton variant="ghost" :pending="pending === 'preview'" @click="onRefreshPreview">
              预览删除影响
            </AppButton>
          </div>
          <p v-if="objectBlocked" class="lock-note">{{ objectBlocked }}</p>
          <template v-if="objectPreview">
            <p class="fine">
              {{ glossTargetType(objectPreview.targetType) }} · {{ objectPreview.canProceed === true ? '可以删除' : '暂时不能删除' }}
            </p>
            <p class="verbatim">{{ objectPreview.impactSummary || '暂未取得删除影响说明，请重新预览。' }}</p>
            <AppBanner v-if="objectBlockers.length || objectPreview.canProceed === false" tone="bad">
              暂时不能删除：{{ objectBlockers.length ? objectBlockers.join('；') : '系统尚未确认这项数据可以删除' }}
            </AppBanner>
            <ul v-if="objectImpacts.length" class="ledger">
              <li
                v-for="(row, index) in objectImpacts"
                :key="`${row.kind}-${row.id}-${index}`"
                class="ledger__row"
                :class="{
                  'ledger__row--wipe': impactRowTone(row.relation) === 'wipe',
                  'ledger__row--keep': impactRowTone(row.relation) === 'keep',
                  'ledger__row--block': impactRowTone(row.relation) === 'block',
                }"
              >
                <header>
                  <strong>{{ row.label || glossTargetType(row.kind) }}</strong>
                  <span class="pill">{{ glossImpactRelation(row.relation) }}</span>
                </header>
              </li>
            </ul>
          </template>
          <label class="ack">
            <input
              v-model="objectAckImpact"
              type="checkbox"
              :disabled="pending !== '' || deletionBusy || Boolean(objectBlocked)"
            />
            <span>我已阅读预览清单，确认删除范围和仍会保留的关联数据。</span>
          </label>
          <AppButton
            variant="danger"
            :disabled="!canSubmitObjectDeletion || pending !== ''"
            :pending="pending === 'delete-object'"
            @click="onSubmitObjectDeletion"
          >
            {{
              pending === 'delete-object'
                ? '正在提交…'
                : objectConfirmOpen
                  ? '再次确认：永久删除'
                  : '永久删除所选数据'
            }}
          </AppButton>
                </div>
              </section>

              <section v-if="deletionView" id="deletion-progress" class="settings-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">删除申请进度</h3>
                    <p class="card__sub">各项数据的处理结果，也会通过通知中心告知你。</p>
                  </div>
                </header>
                <div class="card__body">
          <AppBanner :tone="deletionView.status === 'FAILED' ? 'bad' : deletionLooksFinished(deletionView.status) ? 'ok' : 'warn'">
            {{ deletionProgressNote(deletionView) }}
          </AppBanner>
          <dl class="rights-dl">
            <div>
              <dt>状态</dt>
              <dd>{{ glossDeletionStatus(deletionView.status) }}</dd>
            </div>
            <div>
              <dt>范围</dt>
              <dd>{{ deletionView.scope === 'OBJECT' ? glossTargetType(deletionView.targetType) : '整个账号' }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatWhen(deletionView.updatedAt) }}</dd>
            </div>
            <div>
              <dt>申请编号（联系客服时提供）</dt>
              <dd><code>{{ deletionView.id }}</code></dd>
            </div>
          </dl>
          <p v-if="deletionView.impactSummary" class="verbatim">{{ deletionView.impactSummary }}</p>
          <ul v-if="deletionView.receipts?.length" class="ledger">
            <li v-for="(row, index) in deletionView.receipts" :key="`${row.moduleCode}-${index}`" class="ledger__row">
              <header>
                <strong>{{ glossReceiptModule(row.moduleCode) }}</strong>
                <span class="pill">{{ glossReceiptStatus(row.status) }}</span>
              </header>
              <p v-if="row.message" class="verbatim fine">{{ row.message }}</p>
            </li>
          </ul>
          <p v-else class="empty">各项数据还在排队处理，结果出来后会显示在这里。</p>
                </div>
              </section>
            </div>

            <aside class="rights-rail settings-card">
              <header class="card__head">
                <div>
                  <h3 class="card__title">我的数据权利</h3>
                  <p class="card__sub">你可以根据需要管理或处置你的个人数据。</p>
                </div>
              </header>
              <div class="card__body">
                <a class="rail-row" href="#export">
                  <span class="rail-row__icon is-blue"><AppIcon name="download" :size="16" /></span>
                  <div><strong>导出我的数据</strong><p>下载求职资料、简历和历史求职记录的副本。</p></div>
                  <AppIcon name="chevron-right" :size="14" />
                </a>
                <a class="rail-row" href="#object">
                  <span class="rail-row__icon is-orange"><AppIcon name="trash" :size="16" /></span>
                  <div><strong>删除单项数据</strong><p>永久删除不需要的经历、资料文件或职业主档。</p></div>
                  <AppIcon name="chevron-right" :size="14" />
                </a>
                <a class="rail-row" href="#deletion">
                  <span class="rail-row__icon is-red"><AppIcon name="x-circle" :size="16" /></span>
                  <div><strong>注销账号</strong><p>永久删除账号及相关数据，无法恢复，请谨慎操作。</p></div>
                  <AppIcon name="chevron-right" :size="14" />
                </a>
                <p class="fine" style="margin-top: 12px">处理进度和结果将通过通知中心及时告知你。</p>
              </div>
            </aside>
          </div>
  </div>
</template>

<style scoped>
.rights-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 272px;
  gap: 18px;
  align-items: start;
}
.rights-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}
.rights-rail {
  position: sticky;
  top: 84px;
}
.rail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  color: var(--text);
  cursor: pointer;
  transition: border-color 180ms ease, background-color 180ms ease, transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1), box-shadow 180ms ease;
}
.rail-row:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  box-shadow: 0 5px 13px color-mix(in srgb, var(--color-primary) 8%, transparent);
  transform: translateX(2px);
}
.rail-row + .rail-row {
  margin-top: 10px;
}
.rail-row strong {
  display: block;
  font-size: 13.5px;
}
.rail-row p {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}
.rail-row__icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.rail-row__icon.is-blue {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}
.rail-row__icon.is-orange {
  background: var(--warning-soft);
  color: var(--color-warning);
}
.rail-row__icon.is-red {
  background: var(--danger-soft);
  color: var(--color-danger);
}
.rights-dl {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px 20px;
  margin-top: 14px;
}
.rights-dl dt {
  font-size: 12px;
  color: var(--text-tertiary);
}
.rights-dl dd {
  margin-top: 3px;
  font-size: 13px;
}
.history-archive { display: grid; gap: 12px; }
.history-counts { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 8px; }
.history-counts > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 7px; background: var(--bg); }
.history-counts span { color: var(--text-tertiary); font-size: 12px; }
.history-counts strong { font-size: 15px; }
.lock-note {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--color-warning);
}
.split-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}
.danger-card {
  border-color: var(--border-default);
  box-shadow: 0 3px 14px color-mix(in srgb, var(--color-danger) 4%, transparent);
}
.danger-card .card__head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface-1);
}
.advanced-card {
  border-style: dashed;
  background: var(--surface-1);
}
.advanced-card .card__sub {
  max-width: 680px;
}
.preview-card .banner {
  margin-bottom: 12px;
}
.export-card .btn-row,
.danger-card .btn-row {
  margin-top: 14px;
}
.verbatim {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
}
.ledger {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}
.ledger__row {
  border: 1px solid var(--border);
  border-left-width: 3px;
  border-radius: 10px;
  padding: 12px 14px;
}
.ledger__row--wipe {
  border-left-color: var(--color-danger);
}
.ledger__row--keep {
  border-left-color: var(--color-success);
}
.ledger__row--block {
  border-left-color: var(--color-warning);
}
.ledger__row header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.pill {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--bg);
  border: 1px solid var(--border);
  font-size: 12.5px;
  color: var(--text-secondary);
}
@media (max-width: 1080px) {
  .rights-layout {
    grid-template-columns: 1fr;
  }
  .rights-rail {
    position: static;
  }
}
@media (max-width: 640px) {
  .split-fields {
    grid-template-columns: 1fr;
  }
}
@media (prefers-reduced-motion: reduce) {
  .rail-row {
    transition: none;
  }
}
</style>
