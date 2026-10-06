/** PDF export: preflight risks, design flush, async task polling and the private download. */
import { computed, ref } from 'vue'
import { errorMessage } from '@/shared/api/types'
import type { TaskView } from '@/shared/api/task'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import { downloadPrivateFile } from '@/features/resume/services/resumeApi'
import { pdfDownloadDisabledReason } from '@/features/resume/labels'
import { exportAiResumePdf } from '../services/aiResumeApi'
import type { AiResumePdfExportMode } from '../types'
import { needsPdfContactWarning, pdfFallbackFilename } from '../utils/pdfExportMode'
import { formatPdfOverflow, isPdfOverflowBlocked, pdfOverflowItems } from '../utils/pdfPreflight'
import type { DesignDraft } from './useDesignDraft'
import type { WorkbenchSession } from './useWorkbenchSession'

function triggerBrowserDownload(blob: Blob, filename: string): void {
  const href = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = href
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}

export function usePdfExport(session: WorkbenchSession, deps: {
  /** Late-bound: the design draft itself asks whether an export is running. */
  design: () => DesignDraft
  previewBasics: () => Record<string, unknown>
  unconfirmedDraftCount: () => number
}) {
  const open = ref(false)
  const pending = ref(false)
  const mode = ref<AiResumePdfExportMode>('STANDARD')
  const task = ref<TaskView | null>(null)
  const error = ref('')
  let pollAbort: AbortController | null = null

  const layout = computed(() => session.conversation.value?.layout)
  const overflowItems = computed(() => pdfOverflowItems(layout.value))
  const overflowBlocked = computed(() => isPdfOverflowBlocked(layout.value))
  const overflowSummary = computed(() => formatPdfOverflow(overflowItems.value[0]))
  const pendingChangeCount = computed(() => session.conversation.value?.changeSets
    .flatMap(changeSet => changeSet.items).filter(item => item.status === 'PENDING').length ?? 0)

  /** Each risk carries a tone so the dialog can tell blockers from advice. */
  const risks = computed(() => {
    const result: Array<{ tone: 'danger' | 'warning'; text: string }> = []
    if (overflowBlocked.value) result.push({ tone: 'danger', text: `${overflowSummary.value}，必须精简内容、调整设计或更换页数更多的模板后才能导出。` })
    if (!session.ready.value) result.push({ tone: 'warning', text: '当前内容尚未满足第一版条件，导出的 PDF 可能不完整。' })
    if (pendingChangeCount.value > 0) result.push({ tone: 'warning', text: `存在 ${pendingChangeCount.value} 条待确认修改；它们只显示在预览中，不会进入本次 PDF。` })
    if (needsPdfContactWarning(mode.value, deps.previewBasics())) result.push({ tone: 'warning', text: '尚未填写邮箱或手机号，正式投递前建议至少保留一种联系方式。' })
    if (deps.unconfirmedDraftCount() > 0) result.push({ tone: 'warning', text: '尚未确认的卡片草稿只用于预览，不会进入本次 PDF。' })
    return result
  })

  const progressText = computed(() => {
    if (!pending.value) return ''
    const status = task.value?.status
    if (!status) return '正在保存设计并冻结当前内容…'
    if (status === 'QUEUED' || status === 'PENDING') return '已进入导出队列…'
    return '正在排版并生成 PDF…'
  })

  function show(): void {
    error.value = ''
    open.value = true
  }

  async function confirmExport(): Promise<void> {
    const conversation = session.conversation.value
    const design = deps.design()
    if (!conversation || pending.value || design.templatePending.value || session.isDisposed()) return
    const conversationId = conversation.id
    pending.value = true
    error.value = ''
    task.value = null
    try {
      if (!(await design.persist()) || session.isDisposed() || session.conversation.value?.id !== conversationId) {
        error.value = '设计设置尚未成功保存，本次没有生成 PDF。'
        return
      }
      if (overflowBlocked.value) {
        error.value = `${overflowSummary.value}，本次没有生成 PDF。`
        return
      }
      pollAbort?.abort()
      pollAbort = new AbortController()
      const exportMode = mode.value
      const started = await exportAiResumePdf(conversationId, exportMode)
      task.value = started
      const finished = await pollTask(started.id, (next) => { task.value = next }, pollAbort.signal)
      task.value = finished
      const blocked = pdfDownloadDisabledReason(finished)
      if (finished.status !== 'SUCCEEDED' || blocked) {
        error.value = finished.failureReason || blocked || 'PDF 导出任务没有生成可下载文件。'
        return
      }
      const file = await downloadPrivateFile(finished.fileId!.trim())
      triggerBrowserDownload(file.blob, file.filename || pdfFallbackFilename(exportMode))
      open.value = false
      session.notify(exportMode === 'ANONYMOUS'
        ? '匿名 PDF 已生成并开始下载；姓名、照片和联系方式已从独立快照中移除，正式简历未改变。'
        : '正式 PDF 已按当前确认内容、模板和设计生成并开始下载。你可以继续编辑后再次导出。')
      await session.load()
    } catch (reason) {
      if (!isAbortError(reason)) error.value = errorMessage(reason, 'PDF 生成失败，未下载任何文件')
    } finally {
      pending.value = false
      // Surface failures even when the dialog was closed while the task was running.
      if (error.value && !open.value) session.fail(error.value)
    }
  }

  function dispose(): void {
    pollAbort?.abort()
  }

  return {
    open,
    pending,
    mode,
    task,
    error,
    overflowItems,
    overflowBlocked,
    overflowSummary,
    risks,
    progressText,
    show,
    confirmExport,
    dispose,
  }
}

export type PdfExport = ReturnType<typeof usePdfExport>
