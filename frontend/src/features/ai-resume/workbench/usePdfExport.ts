/**
 * PDF export: server-rendered first-page preview, real page limit check, design flush, async task
 * polling with server progress, the ATS text check result and the private download.
 */
import { computed, ref, shallowRef, watch } from 'vue'
import { errorMessage } from '@/shared/api/types'
import type { AtsTextCheck, TaskView } from '@/shared/api/task'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import { downloadPrivateFile } from '@/features/resume/services/resumeApi'
import { pdfDownloadDisabledReason } from '@/features/resume/labels'
import { exportAiResumePdf, fetchAiResumeExportPreview, type AiResumeExportPreview } from '../services/aiResumeApi'
import type { AiResumePdfExportMode } from '../types'
import { needsPdfContactWarning, pdfFallbackFilename } from '../utils/pdfExportMode'
import type { DesignDraft } from './useDesignDraft'
import type { WorkbenchSession } from './useWorkbenchSession'

/** What the server is doing, from the task's checkpoint (never an estimate). */
const CHECKPOINT_TEXT: Record<string, string> = {
  STARTING: '已开始处理…',
  RENDER_PREPARED: '已冻结内容，正在准备排版…',
  RENDERING: '正在排版并生成 PDF…',
  CHECKING_TEXT: '正在检查文字能否被招聘系统读取…',
}

const ATS_LABELS: Record<string, string> = {
  TEXT_LAYER: '文字可选中、可复制',
  NAME: '姓名可读取',
  EMAIL: '邮箱可读取',
  PHONE: '电话可读取',
  SECTION_TITLES: '板块标题可读取',
  SECTION_ORDER: '阅读顺序与版面一致',
  ENTRY_TITLES: '经历与学校名称可读取',
}

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

export interface ExportOutcome {
  mode: AiResumePdfExportMode
  pageCount: number | null
  ats: AtsTextCheck | null
  checks: Array<{ code: string; label: string; passed: boolean; detail?: string | null }>
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
  const outcome = shallowRef<ExportOutcome | null>(null)
  const preview = shallowRef<AiResumeExportPreview | null>(null)
  const previewPending = ref(false)
  const previewError = ref('')
  let pollAbort: AbortController | null = null
  let previewAbort: AbortController | null = null

  /** The renderer's numbers win once known; until then the browser's measurement of the same layout. */
  const overflow = computed(() => {
    const server = preview.value
    if (server) {
      return server.pageCount > server.pageLimit
        ? { pageCount: server.pageCount, pageLimit: server.pageLimit, overflowMm: server.overflowMm, section: server.overflowSection ?? null }
        : null
    }
    return deps.design().overflow.value
  })
  const overflowBlocked = computed(() => Boolean(overflow.value))
  const overflowSummary = computed(() => {
    const value = overflow.value
    if (!value) return ''
    const where = value.section ? `，从「${value.section}」开始` : ''
    return `内容超出 ${value.pageLimit} 页上限约 ${Math.max(1, Math.round(value.overflowMm))} 毫米${where}`
  })
  const pendingChangeCount = computed(() => session.conversation.value?.changeSets
    .flatMap(changeSet => changeSet.items).filter(item => item.status === 'PENDING').length ?? 0)

  /** Each risk carries a tone so the dialog can tell blockers from advice. */
  const risks = computed(() => {
    const result: Array<{ tone: 'danger' | 'warning'; text: string }> = []
    if (overflowBlocked.value) result.push({ tone: 'danger', text: `${overflowSummary.value}。请使用预览上方的“一键紧凑”、精简内容或把目标页数改为自动后再导出。` })
    if (!session.ready.value) result.push({ tone: 'warning', text: '当前内容尚未满足第一版条件，导出的 PDF 可能不完整。' })
    if (pendingChangeCount.value > 0) result.push({ tone: 'warning', text: `存在 ${pendingChangeCount.value} 条待确认修改；它们只显示在预览中，不会进入本次 PDF。` })
    if (needsPdfContactWarning(mode.value, deps.previewBasics())) result.push({ tone: 'warning', text: '尚未填写邮箱或手机号，正式投递前建议至少保留一种联系方式。' })
    if (deps.unconfirmedDraftCount() > 0) result.push({ tone: 'warning', text: '尚未确认的卡片草稿只用于预览，不会进入本次 PDF。' })
    return result
  })

  const progressText = computed(() => {
    if (!pending.value) return ''
    const current = task.value
    if (!current) return '正在保存设计…'
    if (current.status === 'QUEUED' || current.status === 'PENDING') return '已进入导出队列…'
    return CHECKPOINT_TEXT[current.checkpointCode ?? ''] ?? '正在生成 PDF…'
  })
  /** Server-reported progress only (HON-02); null hides the bar. */
  const progressPercent = computed(() => (pending.value && typeof task.value?.progressPercent === 'number' ? task.value.progressPercent : null))

  async function loadPreview(): Promise<void> {
    const conversation = session.conversation.value
    if (!conversation || !open.value) return
    previewAbort?.abort()
    const controller = new AbortController()
    previewAbort = controller
    previewPending.value = true
    previewError.value = ''
    try {
      // The server renders the saved design; unsaved edits would make the preview lie.
      if (!(await deps.design().persist())) {
        previewError.value = '设计设置尚未保存，暂时无法生成导出预览。'
        return
      }
      const value = await fetchAiResumeExportPreview(conversation.id, mode.value, controller.signal)
      if (previewAbort === controller) preview.value = value
    } catch (reason) {
      if (!isAbortError(reason) && previewAbort === controller) {
        preview.value = null
        previewError.value = errorMessage(reason, '导出预览生成失败')
      }
    } finally {
      if (previewAbort === controller) previewPending.value = false
    }
  }

  function show(): void {
    error.value = ''
    outcome.value = null
    preview.value = null
    open.value = true
    void loadPreview()
  }

  function close(): void {
    open.value = false
    previewAbort?.abort()
  }

  watch(mode, () => { if (open.value && !pending.value) void loadPreview() })

  function outcomeOf(finished: TaskView, exportMode: AiResumePdfExportMode): ExportOutcome {
    const ats = finished.result?.atsCheck ?? null
    return {
      mode: exportMode,
      pageCount: finished.result?.pageCount ?? null,
      ats,
      checks: (ats?.checks ?? []).map(check => ({ ...check, label: ATS_LABELS[check.code] ?? check.code })),
    }
  }

  async function confirmExport(): Promise<void> {
    const conversation = session.conversation.value
    const design = deps.design()
    if (!conversation || pending.value || design.templatePending.value || session.isDisposed()) return
    const conversationId = conversation.id
    pending.value = true
    error.value = ''
    task.value = null
    outcome.value = null
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
      outcome.value = outcomeOf(finished, exportMode)
      if (!open.value) {
        session.notify(exportMode === 'ANONYMOUS'
          ? '匿名 PDF 已生成并开始下载；姓名、照片和联系方式已从独立快照中移除。'
          : '正式 PDF 已按当前确认内容、模板和设计生成并开始下载。')
      }
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
    previewAbort?.abort()
  }

  return {
    open,
    pending,
    mode,
    task,
    error,
    outcome,
    preview,
    previewPending,
    previewError,
    overflowBlocked,
    overflowSummary,
    risks,
    progressText,
    progressPercent,
    show,
    close,
    loadPreview,
    confirmExport,
    dispose,
  }
}

export type PdfExport = ReturnType<typeof usePdfExport>
