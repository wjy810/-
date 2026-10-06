<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { fetchTask, isCancellableTask, isManualRetryTask, isOpenTask, type TaskView } from '@/shared/api/task'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { downloadCareerHistory, fetchCareerHistory } from '@/features/career-library/services/careerLibraryApi'
import type { CareerHistorySummary } from '@/features/career-library/types'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import {
  deletionLooksFinished,
  deletionProgressNote,
  exportDownloadDisabledReason,
  extraPreviewLines,
  glossDeletionStatus,
  glossImpactRelation,
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
const loading = ref(true)
const pending = ref('')
const pageError = ref('')
const formError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'data-rights-page-error')
useToastFeedback(notice, 'success', 'data-rights-page-notice')
const forbidden = ref<unknown>(null)

const preview = ref<DeletionPreview | null>(null)
const objectPreview = ref<DeletionPreview | null>(null)
const exportView = ref<ExportView | null>(null)
const exportTask = ref<TaskView | null>(null)
const deletionView = ref<DeletionView | null>(null)
const careerHistory = ref<CareerHistorySummary | null>(null)

const exportAck = ref(false)
const accountAckImpact = ref(false)
const accountAckIrreversible = ref(false)
const accountConfirmOpen = ref(false)
const objectAckImpact = ref(false)
const objectConfirmOpen = ref(false)
const objectForm = reactive({ targetType: '', targetId: '' })

let exportPoll: AbortController | null = null
let deletionPoll: AbortController | null = null

const extraPreview = computed(() => extraPreviewLines(preview.value))
const objectBlocked = computed(() =>
  objectSubmitBlockedReason(objectPreview.value, objectForm.targetType, objectForm.targetId),
)
const objectImpacts = computed(() => objectPreview.value?.impacts ?? [])
const objectBlockerCodes = computed(() =>
  (objectPreview.value?.blockers ?? []).map((item) => String(item).trim()).filter(Boolean),
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
const canSubmitAccountDeletion = computed(
  () => accountAckImpact.value && accountAckIrreversible.value && !deletionBusy.value,
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
      throw new Error('删除仍在处理，已停止自动刷新。请稍后点重新读取，不会自动重试删除。')
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

async function loadAccountPreview(): Promise<void> {
  preview.value = await fetchDeletionPreview()
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

async function hydrateFromQuery(): Promise<void> {
  const exportId = queryValue('exportId')
  const deletionId = queryValue('deletionId')
  const taskId = queryValue('taskId')
  const kind = queryValue('kind')
  const targetType = queryValue('targetType')
  const targetId = queryValue('targetId')
  if (targetType) {
    objectForm.targetType = targetType
  }
  if (targetId) {
    objectForm.targetId = targetId
  }
  if (exportId) {
    const view = await fetchExport(exportId)
    await followExport(view)
  } else if (taskId && kind !== 'deletion') {
    exportTask.value = await fetchTask(taskId)
  }
  if (deletionId) {
    await pollDeletionUntilSettled(deletionId)
  } else if (taskId && kind === 'deletion') {
    const task = await fetchTask(taskId)
    notice.value =
      `任务创建结果只给了任务 ${task.id}（${task.status}），没有 deletionId。无法读取删除回执与模块影响，也不会假装对象/账号已删除。请从通知「删除进度」进入。`
  }
}

async function loadPage(): Promise<void> {
  stopExportPoll()
  stopDeletionPoll()
  loading.value = true
  pageError.value = ''
  formError.value = ''
  notice.value = ''
  forbidden.value = null
  const targetType = queryValue('targetType')
  const targetId = queryValue('targetId')
  if (targetType) {
    objectForm.targetType = targetType
  }
  if (targetId) {
    objectForm.targetId = targetId
  }
  try {
    await Promise.all([
      loadAccountPreview(),
      fetchCareerHistory().then((value) => { careerHistory.value = value }),
    ])
    if (objectForm.targetType.trim() && objectForm.targetId.trim()) {
      try {
        await loadObjectPreview()
      } catch (error) {
        if (isForbidden(error)) {
          throw error
        }
        objectPreview.value = null
        formError.value = errorMessage(error, '对象级预览读取失败')
      }
    } else {
      objectPreview.value = null
    }
    await hydrateFromQuery()
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    if (isForbidden(error)) {
      forbidden.value = error
      return
    }
    pageError.value = errorMessage(error, '数据权利页读取失败')
  } finally {
    loading.value = false
  }
}

async function onDownloadCareerHistory(format: 'json' | 'md'): Promise<void> {
  pending.value = `career-history-${format}`
  formError.value = ''
  try {
    await downloadCareerHistory(format)
  } catch (error) {
    formError.value = errorMessage(error, '历史归档下载失败')
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
      ? '已按对象参数读取对象级预览。impacts / canProceed / blockers 原文照录，未改写。'
      : '尚未确认所选数据的删除范围，暂不能提交，请刷新影响预览。'
  } catch (error) {
    objectPreview.value = null
    if (isForbidden(error)) {
      forbidden.value = error
      return
    }
    formError.value = errorMessage(error, '对象级预览读取失败')
  } finally {
    pending.value = ''
  }
}

async function onCreateExport(): Promise<void> {
  formError.value = ''
  notice.value = ''
  if (!exportAck.value) {
    formError.value = '请先确认：导出是异步任务，下载限时，过期作废。'
    return
  }
  if (exportBusy.value || deletionBusy.value) {
    formError.value = deletionBusy.value
      ? '账号删除进行中，不能再发起导出。'
      : '已有导出任务进行中，不能重复提交。'
    return
  }
  pending.value = 'export'
  try {
    const created = await createExport(crypto.randomUUID())
    notice.value = '导出任务已创建。进度来自任务查询，成功后才开放限时下载。'
    await followExport(created)
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    if (isForbidden(error)) {
      forbidden.value = error
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
    notice.value = '已按任务契约手动重试导出。'
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
    formError.value = errorMessage(error, '限时下载失败')
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
    notice.value = '请再次确认。第二次点击才会提交账号删除/注销申请。'
    return
  }
  if (deletionBusy.value) {
    formError.value = '已有删除申请在处理，不能重复提交。'
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
      forbidden.value = error
      return
    }
    formError.value = errorMessage(error, '提交删除失败')
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
    formError.value = '对象级提交前必须确认：已阅读对象预览清单；TARGET/CASCADE 会物理删，BOUND/RELATED 只展示。'
    return
  }
  if (!objectConfirmOpen.value) {
    objectConfirmOpen.value = true
    notice.value = '请再次确认对象删除。第二次点击才会提交 OBJECT，不会拿账号级预览去删整号。'
    return
  }
  if (accountDeletionOpen.value) {
    formError.value = '账号级删除进行中，不能再提交对象级删除。'
    return
  }
  if (objectDeletionOpen.value) {
    formError.value = '已有对象级删除申请在处理，不能重复提交。'
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
      notice.value = '所选数据的删除申请已处理完成，具体范围见处理回执。'
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
      forbidden.value = error
      return
    }
    formError.value = errorMessage(error, '提交对象删除失败')
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
          <div v-if="loading && !preview" aria-busy="true">
            <div class="bone" />
            <div class="bone bone--short" />
          </div>
          <template v-else-if="preview">
            <AppBanner tone="warn">
              <p class="verbatim">{{ preview.impactSummary || '暂未取得删除影响说明，请刷新后再确认。' }}</p>
            </AppBanner>
            <p class="fine">法定例外入口</p>
            <p class="verbatim">{{ preview.legalExceptionNote || '暂未取得法定保留范围说明。' }}</p>
            <p v-if="preview.statusHint" class="fine">{{ preview.statusHint }}</p>
            <details v-if="preview.scope || preview.targetType || preview.targetId || extraPreview.length" class="technical-details">
              <summary>查看技术回执</summary>
              <p v-if="preview.scope || preview.targetType || preview.targetId" class="fine">范围：<code>{{ preview.scope || '—' }}</code> / <code>{{ preview.targetType || '—' }}</code> / <code>{{ preview.targetId || '—' }}</code></p>
              <div v-if="extraPreview.length" class="rights-extras">
                <pre v-for="row in extraPreview" :key="row.key" class="verbatim extras-block">{{ row.key }}：{{ row.value }}</pre>
              </div>
            </details>
            <AppBanner v-if="!previewIsObjectScoped(preview)" tone="ink">当前显示账号整体的影响说明。删除单项数据请使用下方“高级数据删除”，系统确认范围后才允许提交。</AppBanner>
          </template>
          <p v-else class="empty">暂未取得影响预览，请刷新后再确认删除范围。</p>
                </div>
              </section>

              <section id="career-history" class="settings-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">历史求职流程归档</h3>
                    <p class="card__sub">退役的投递、阶段、面试和复盘数据只读保留，可下载但不能恢复业务操作。</p>
                  </div>
                  <AppTag tone="gray">{{ careerHistory?.total ?? 0 }} 条</AppTag>
                </header>
                <div class="card__body history-archive">
                  <div v-if="careerHistory && careerHistory.total" class="history-counts">
                    <div v-for="(count, type) in careerHistory.counts" :key="type"><span>{{ type }}</span><strong>{{ count }}</strong></div>
                  </div>
                  <AppBanner v-else tone="ink">当前账号没有需要保留的历史投递链数据。</AppBanner>
                  <p v-if="careerHistory?.archivedAt" class="fine">最近归档：{{ formatWhen(careerHistory.archivedAt) }}</p>
                  <div class="btn-row">
                    <AppButton variant="ghost" :pending="pending === 'career-history-json'" @click="onDownloadCareerHistory('json')"><AppIcon name="download" :size="14" />下载 JSON</AppButton>
                    <AppButton variant="ghost" :pending="pending === 'career-history-md'" @click="onDownloadCareerHistory('md')"><AppIcon name="download" :size="14" />下载 Markdown</AppButton>
                  </div>
                </div>
              </section>

              <section id="export" class="settings-card export-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">导出我的数据</h3>
                    <p class="card__sub">异步生成求职资料、简历及已退役功能的历史归档副本，成功后限时下载。</p>
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
              手动重试
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
            <div v-if="exportView">
              <dt>导出 ID</dt>
              <dd><code>{{ exportView.id }}</code></dd>
            </div>
            <div>
              <dt>任务</dt>
              <dd>
                <code>{{ exportView?.taskId || exportTask?.id || '—' }}</code>
                · {{ glossTaskStatus(exportView?.taskStatus || exportTask?.status) }}
              </dd>
            </div>
            <div v-if="exportView?.scope">
              <dt>范围</dt>
              <dd>{{ exportView.scope }}</dd>
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
          <p v-else class="empty">还没有导出任务。确认上方说明后即可发起，任务完成时会开放限时下载。</p>
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
                    ? '第二次确认：提交账号删除申请'
                    : '申请账号删除 / 注销'
              }}
            </AppButton>
            <AppButton
              variant="ghost"
              :disabled="!deletionView"
              :pending="pending === 'delete-refresh'"
              @click="onRefreshDeletion"
            >
              重新读取删除进度
            </AppButton>
          </div>
                </div>
              </section>

              <section id="object" class="settings-card advanced-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">高级数据删除</h3>
                    <p class="card__sub">仅用于单独删除一条职业记录、资料文件或职业主档。</p>
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
            <AppField id="object-id" label="数据编号" hint="可以从对应资料详情页复制编号">
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
              删除范围回执：
              <code>{{ objectPreview.scope || '—' }}</code>
              /
              <code>{{ objectPreview.targetType || '—' }}</code>
              /
              <code>{{ objectPreview.targetId || '—' }}</code>
              · {{ objectPreview.canProceed === true ? '允许提交' : '暂不可提交' }}
            </p>
            <p class="verbatim">{{ objectPreview.impactSummary || '（对象预览未返回 impactSummary）' }}</p>
            <AppBanner v-if="objectBlockerCodes.length || objectPreview.canProceed === false" tone="bad">
              当前不能提交。阻断原因：{{ objectBlockerCodes.length ? objectBlockerCodes.join('；') : '服务端尚未确认该数据可以删除' }}
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
                  <strong>{{ row.kind || '未命名' }}</strong>
                  <span class="pill">{{ glossImpactRelation(row.relation) }}</span>
                </header>
                <p class="verbatim">{{ row.label || '（无 label）' }}</p>
                <p class="fine">
                  <code>{{ row.id || '—' }}</code>
                  · {{ row.status || '—' }}
                  · {{ row.relation || '—' }}
                </p>
              </li>
            </ul>
            <p v-else class="empty">对象预览 impacts 为空。空清单不是“已删除成功”。</p>
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
                  ? '第二次确认：提交对象删除申请'
                  : '提交单项数据删除申请'
            }}
          </AppButton>
                </div>
              </section>

              <section v-if="deletionView" id="deletion-progress" class="settings-card">
                <header class="card__head">
                  <div>
                    <h3 class="card__title">删除申请进度</h3>
                    <p class="card__sub">模块回执照录，处理结果同步到通知中心。</p>
                  </div>
                </header>
                <div class="card__body">
          <AppBanner :tone="deletionView.status === 'FAILED' ? 'bad' : deletionLooksFinished(deletionView.status) ? 'ok' : 'warn'">
            {{ deletionProgressNote(deletionView) }}
          </AppBanner>
          <dl class="rights-dl">
            <div>
              <dt>申请 ID</dt>
              <dd><code>{{ deletionView.id }}</code></dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ glossDeletionStatus(deletionView.status) }}</dd>
            </div>
            <div>
              <dt>范围</dt>
              <dd>
                {{ deletionView.scope || '—' }}
                <template v-if="deletionView.targetType || deletionView.targetId">
                  · {{ deletionView.targetType || '—' }} / {{ deletionView.targetId || '—' }}
                </template>
              </dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatWhen(deletionView.updatedAt) }}</dd>
            </div>
          </dl>
          <p class="fine">申请回执中的影响原文</p>
          <p class="verbatim">{{ deletionView.impactSummary || '（无 impactSummary）' }}</p>
          <ul v-if="deletionView.receipts?.length" class="ledger">
            <li v-for="(row, index) in deletionView.receipts" :key="`${row.moduleCode}-${index}`" class="ledger__row">
              <header>
                <strong>{{ row.moduleCode || '模块未命名' }}</strong>
                <span class="pill">{{ row.status || '—' }}</span>
              </header>
              <p class="verbatim">{{ row.message || '（无 message）' }}</p>
            </li>
          </ul>
          <p v-else class="empty">还没有模块回执。提交当时通常仍是已提交。</p>
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
                  <div><strong>导出我的数据</strong><p>下载求职资料、简历及已退役功能的历史归档副本。</p></div>
                  <AppIcon name="chevron-right" :size="14" />
                </a>
                <a class="rail-row" href="#object">
                  <span class="rail-row__icon is-orange"><AppIcon name="trash" :size="16" /></span>
                  <div><strong>删除部分数据</strong><p>选择并删除不需要的职业记录、资料文件或职业主档。</p></div>
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
.technical-details {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-1);
}
.technical-details summary {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.technical-details[open] summary {
  margin-bottom: 10px;
  color: var(--color-primary);
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
.extras-block {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
  margin-top: 8px;
  font-family: inherit;
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
