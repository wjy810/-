/**
 * The personal-data export as one state machine: the latest export decides what the panel offers
 * (start, follow, cancel, download, export again), and the recent ones form its history.
 */
import { computed, onScopeDispose, ref, shallowRef } from 'vue'
import { fetchTask, type TaskView } from '@/shared/api/task'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import { saveBlob } from '@/shared/lib/download'
import { exportDownloadDisabledReason, exportPhase, timeLeft } from '../labels'
import {
  cancelExport,
  createExport,
  downloadExportByPath,
  downloadExportBytes,
  fetchExport,
  isSafeHttpDownloadUrl,
  isSessionApiDownloadPath,
  listExports,
  retryExport,
} from '../services/dataRightsApi'
import type { ExportView } from '../types'

export type ExportAction = '' | 'start' | 'cancel' | 'retry' | 'refresh' | 'download'

export function useExportFlow(onForbidden: (error: unknown) => void) {
  const history = shallowRef<ExportView[]>([])
  const latest = shallowRef<ExportView | null>(null)
  const task = shallowRef<TaskView | null>(null)
  const loaded = ref(false)
  const loadError = shallowRef<unknown>(null)
  const pending = ref<ExportAction>('')
  const error = ref('')
  const notice = ref('')
  const now = ref(Date.now())
  let poll: AbortController | null = null
  const clock = window.setInterval(() => { now.value = Date.now() }, 30_000)

  const liveStatus = computed(() => task.value?.status ?? null)
  const phase = computed(() => exportPhase(latest.value, liveStatus.value))
  const progress = computed(() => {
    if (phase.value !== 'running') return 0
    return Math.max(4, Math.min(99, task.value?.progressPercent ?? 0))
  })
  const remaining = computed(() => timeLeft(latest.value?.downloadExpiresAt, now.value))
  const failureReason = computed(() => task.value?.failureReason || latest.value?.failureReason || '')
  const earlier = computed(() => history.value.filter(item => item.id !== latest.value?.id))

  function stopPolling(): void {
    poll?.abort()
    poll = null
  }

  function remember(view: ExportView): void {
    latest.value = view
    history.value = [view, ...history.value.filter(item => item.id !== view.id)].slice(0, 10)
  }

  function fail(reason: unknown, fallback: string): void {
    if (isAbortError(reason)) return
    if (isForbidden(reason)) {
      onForbidden(reason)
      return
    }
    error.value = errorMessage(reason, fallback)
  }

  /** Polls the export's task until it settles, then re-reads the export for its download state. */
  async function follow(view: ExportView): Promise<void> {
    remember(view)
    task.value = null
    if (!view.taskId || exportPhase(view) !== 'running') return
    stopPolling()
    const controller = new AbortController()
    poll = controller
    try {
      task.value = await pollTask(view.taskId, (tick) => { task.value = tick }, controller.signal)
      remember(await fetchExport(view.id))
    } catch (reason) {
      fail(reason, '导出进度读取失败')
    }
  }

  /** Loads the recent exports, or the one a notification linked to, and follows it if it is running. */
  async function load(exportId?: string): Promise<void> {
    stopPolling()
    error.value = ''
    try {
      const [items, linked] = await Promise.all([listExports(), exportId ? fetchExport(exportId) : Promise.resolve(null)])
      history.value = items
      loadError.value = null
      loaded.value = true
      const current = linked ?? items[0] ?? null
      latest.value = current
      task.value = null
      if (current) void follow(current)
    } catch (reason) {
      if (isForbidden(reason)) {
        onForbidden(reason)
        return
      }
      if (!isAbortError(reason)) loadError.value = reason
    }
  }

  async function run(action: ExportAction, work: () => Promise<void>, fallback: string): Promise<void> {
    if (pending.value) return
    error.value = ''
    notice.value = ''
    pending.value = action
    try {
      await work()
    } catch (reason) {
      fail(reason, fallback)
    } finally {
      pending.value = ''
    }
  }

  const start = () => run('start', async () => {
    const created = await createExport(crypto.randomUUID())
    notice.value = '已开始导出，完成后可以在这里限时下载。'
    void follow(created)
  }, '发起导出失败')

  const cancel = () => run('cancel', async () => {
    const current = latest.value
    if (!current) return
    stopPolling()
    const view = await cancelExport(current.id)
    remember(view)
    task.value = view.taskId ? await fetchTask(view.taskId) : null
    notice.value = '已取消这次导出。'
  }, '取消导出失败')

  /** A failed or cancelled export is retried; an expired one starts over. */
  const again = () => run('retry', async () => {
    const current = latest.value
    const next = current && (phase.value === 'failed' || phase.value === 'cancelled')
      ? await retryExport(current.id)
      : await createExport(crypto.randomUUID())
    notice.value = '已重新开始导出。'
    void follow(next)
  }, '重新导出失败')

  const refresh = () => run('refresh', async () => {
    const current = latest.value
    if (!current) return
    const view = await fetchExport(current.id)
    remember(view)
    task.value = view.taskId ? await fetchTask(view.taskId) : null
  }, '导出进度读取失败')

  const download = () => run('download', async () => {
    const view = latest.value
    const blocked = exportDownloadDisabledReason(view, liveStatus.value)
    if (!view || blocked) {
      error.value = blocked ?? '还没有可以下载的导出。'
      return
    }
    const namedUrl = view.downloadUrl?.trim()
    const fileId = view.fileId?.trim()
    let file: { blob: Blob; filename: string | null } | null = null
    if (namedUrl && isSessionApiDownloadPath(namedUrl)) {
      file = await downloadExportByPath(namedUrl)
    } else if (fileId) {
      file = await downloadExportByPath(`/api/v1/files/${encodeURIComponent(fileId)}/download`)
    } else if (view.downloadAvailable) {
      file = await downloadExportBytes(view.id)
    } else if (namedUrl && isSafeHttpDownloadUrl(namedUrl)) {
      window.open(namedUrl, '_blank', 'noopener,noreferrer')
      notice.value = '已打开下载链接；若链接过期，请重新导出。'
      return
    }
    if (!file) {
      error.value = '导出文件暂不可下载，请刷新进度或重新导出。'
      return
    }
    saveBlob(file.blob, file.filename || 'jobproof-export.json')
    notice.value = '已开始下载导出文件。'
  }, '下载失败')

  onScopeDispose(() => {
    stopPolling()
    window.clearInterval(clock)
  })

  return {
    history, earlier, latest, task, loaded, loadError, pending, error, notice,
    phase, progress, remaining, failureReason,
    load, start, cancel, again, refresh, download, stopPolling,
  }
}

export type ExportFlow = ReturnType<typeof useExportFlow>
