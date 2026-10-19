<script setup lang="ts">
/**
 * 版本与导出 (/resumes/:id/manual). Content is edited only in the AI workbench (docs/phase2/00 C-1);
 * this page lists frozen versions, exports them and runs the freeze checks.
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Download, GitCompareArrows, LinkIcon, Lock, PenLine, RefreshCw, RotateCcw } from 'lucide-vue-next'
import AppIcon from '@/shared/ui/AppIcon.vue'
import ForbidState from '@/shared/ui/ForbidState.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge, { type BadgeTone } from '@/shared/ui/UiBadge.vue'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCard from '@/shared/ui/UiCard.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { confirm } from '@/shared/ui/confirm'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage, isApiClientError, isForbidden, isVersionConflict } from '@/shared/api/types'
import type { TaskView } from '@/shared/api/task'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import type { IconName } from '@/shared/ui/icons'
import { collectPages } from '@/shared/lib/pages'
import { ensureAiResume, restoreAiResumeVersion } from '@/features/ai-resume/services/aiResumeApi'
import { fetchCareerRecords } from '@/features/career-library/services/careerLibraryApi'
import type { CareerRecord } from '@/features/career-library/types'
import ResumeCompareModal from '../components/ResumeCompareModal.vue'
import ResumeEvidenceDrawer from '../components/ResumeEvidenceDrawer.vue'
import ResumeFreezeModal from '../components/ResumeFreezeModal.vue'
import {
  docxDownloadDisabledReason,
  glossMasterStatus,
  glossVersionStatus,
  isPdfExportable,
  isRestorableVersion,
  masterSourceLabel,
  outcomeResolved,
  pdfDownloadDisabledReason,
  unresolvedOutcomes,
  versionOrdinals,
  versionSourceLabel,
} from '../labels'
import {
  archiveResume,
  archiveResumeVersion,
  confirmResumeVersion,
  downloadByPath,
  downloadDocxByPath,
  downloadPrivateDocxFile,
  downloadPrivateFile,
  exportResumeDocx,
  exportResumePdf,
  fetchCurrentResumeLayout,
  fetchResume,
  fetchResumeTemplate,
  freezeResume,
  isSessionFileDownloadPath,
  linkOutcomeEvidence,
  markResumeReady,
  restoreResume,
  waiveOutcome,
} from '../services/resumeApi'
import type { CurrentResumeLayout, KeyOutcome, ResumeMasterView, ResumeTemplateDetail, ResumeVersionView } from '../types'
import { resumeFileName } from '../utils/downloadName'

type ArchiveTarget = { kind: 'master' | 'version'; id: string; version: number }
type ExportRun = { versionId: string; task: TaskView }

const route = useRoute()
const resumeId = computed(() => String(route.params.id ?? ''))
const page = useLoadState((signal) => fetchResume(resumeId.value, signal))
const master = page.data

const pending = ref('')
const formError = ref('')
const notice = ref('')
const writeForbidden = ref<unknown>(null)
useToastFeedback(formError, 'error', 'resume-versions-error')
useToastFeedback(notice, 'success', 'resume-versions-notice')

const currentLayout = ref<CurrentResumeLayout | null>(null)
const currentTemplateDetail = ref<ResumeTemplateDetail | null>(null)
const layoutReadFailed = ref(false)
const evidences = ref<CareerRecord[]>([])
const evidenceError = ref('')
const evidenceLoading = ref(false)
const drawerOpen = ref(false)
const drawerOutcome = ref<KeyOutcome | null>(null)
const freezeOpen = ref(false)
const compareOpen = ref(false)
const archiveTarget = ref<ArchiveTarget | null>(null)
const pdfRun = ref<ExportRun | null>(null)
const docxRun = ref<ExportRun | null>(null)
let pollAbort: AbortController | null = null

const forbidden = computed(() => writeForbidden.value ?? (isForbidden(page.error.value) ? page.error.value : null))
const archived = computed(() => master.value?.status === 'ARCHIVED')
const outcomes = computed(() => (master.value?.keyOutcomes ?? []).filter((item) => (item.text ?? '').trim()))
const unresolved = computed(() => unresolvedOutcomes(outcomes.value))
const versions = computed(() => master.value?.versions ?? [])
const ordinals = computed(() => versionOrdinals(versions.value))
const focusedVersionId = computed(() => (typeof route.query.versionId === 'string' ? route.query.versionId : ''))
/** Pending AI candidates on this resume; null when the server did not report them. */
const pendingAiCount = computed(() => {
  const ids = master.value?.pendingCandidateIds
  return Array.isArray(ids) ? ids.length : null
})
const statusTone = computed<BadgeTone>(() => {
  const status = master.value?.status
  if (status === 'READY_TO_EXPORT') return 'success'
  if (status === 'PENDING_CONFIRMATION') return 'accent'
  if (status === 'ARCHIVED') return 'neutral'
  return 'primary'
})
const canMarkReady = computed(() => Boolean(master.value) && !archived.value
  && master.value?.status !== 'READY_TO_EXPORT' && unresolved.value.length === 0)
const readyBlockReason = computed(() => {
  if (archived.value) return '已归档的简历不能标为可导出，请先恢复。'
  if (master.value?.status === 'READY_TO_EXPORT') return ''
  if (unresolved.value.length) return `还有 ${unresolved.value.length} 条关键成果没有关联证据，也没有确认暂无证据。`
  return ''
})
const layoutOverflow = computed(() => currentLayout.value?.layout?.status === 'OVERFLOW')
const canFreeze = computed(() => master.value?.status === 'READY_TO_EXPORT' && unresolved.value.length === 0
  && !layoutReadFailed.value && !layoutOverflow.value && !archived.value)
const freezeBlockReason = computed(() => {
  if (layoutReadFailed.value) return '模板状态读取失败，暂时不能冻结。请刷新后重试。'
  if (layoutOverflow.value) {
    const first = currentLayout.value?.layout?.overflow.items[0]
    return first ? `第 ${first.page} 页内容超出页面，请先在工作台中精简后再冻结。` : '内容超出页面，请先在工作台中精简后再冻结。'
  }
  if (master.value?.status !== 'READY_TO_EXPORT') return readyBlockReason.value || '请先把简历标为可导出，再冻结版本。'
  return readyBlockReason.value
})
const docxAvailable = computed(() => Boolean(currentTemplateDetail.value?.docxAvailable))
const docxUnavailableReason = computed(() => {
  if (!currentLayout.value?.selected || !currentLayout.value.layout?.templateId) return '当前简历没有选择支持 Word 的模板。'
  return currentTemplateDetail.value?.docxUnavailableReason || '当前模板暂不支持导出 Word。'
})
const pdfDownloadBlockReason = computed(() => pdfDownloadDisabledReason(pdfRun.value?.task ?? null))
const docxDownloadBlockReason = computed(() => docxDownloadDisabledReason(docxRun.value?.task ?? null))
const outcomeStats = computed(() => {
  const linked = outcomes.value.filter((item) => Boolean(item.evidenceId?.trim())).length
  const waived = outcomes.value.filter((item) => !item.evidenceId?.trim() && item.waiveNoEvidence).length
  return { total: outcomes.value.length, linked, waived, open: outcomes.value.length - linked - waived }
})
const layoutStatusLabel = computed(() => {
  const status = currentLayout.value?.layout?.status
  if (status === 'OVERFLOW') return { tone: 'danger' as const, text: '内容超出页面' }
  if (status === 'FROZEN') return { tone: 'primary' as const, text: '已随版本冻结' }
  return { tone: 'success' as const, text: '未超出页面' }
})

function versionLabel(item: ResumeVersionView): string {
  return `版本 ${ordinals.value.get(item.id) ?? '—'}`
}

function versionIcon(item: ResumeVersionView): IconName {
  if (item.status === 'FROZEN') return 'lock'
  if (item.status === 'ARCHIVED') return 'archive'
  return 'clock'
}

function versionTone(item: ResumeVersionView): 'primary' | 'accent' | 'neutral' {
  if (item.status === 'FROZEN') return 'primary'
  if (item.status === 'PENDING_USER_CONFIRMATION') return 'accent'
  return 'neutral'
}

function taskStateText(task: TaskView): string {
  if (task.status === 'SUCCEEDED') return '已生成'
  if (task.status === 'FAILED') return task.failureReason ? `没有成功：${task.failureReason}` : '没有成功'
  if (task.status === 'CANCELLED') return '已取消'
  return '正在生成…'
}

function runLabel(run: ExportRun): string {
  const version = versions.value.find((item) => item.id === run.versionId)
  return version ? versionLabel(version) : '这个版本'
}

function stopPoll(): void {
  pollAbort?.abort()
  pollAbort = null
}

async function loadLayout(id: string): Promise<void> {
  layoutReadFailed.value = false
  try {
    const layout = await fetchCurrentResumeLayout(id)
    const templateId = layout.layout?.templateId
    const detail = templateId ? await fetchResumeTemplate(templateId) : null
    if (resumeId.value !== id) return
    currentLayout.value = layout
    currentTemplateDetail.value = detail
  } catch {
    if (resumeId.value !== id) return
    currentLayout.value = null
    currentTemplateDetail.value = null
    layoutReadFailed.value = true
  }
}

async function loadEvidence(): Promise<void> {
  evidenceError.value = ''
  evidenceLoading.value = true
  try {
    evidences.value = (await collectPages((page, size) => fetchCareerRecords({ status: 'ACTIVE', page, size }))).items
  } catch (error) {
    evidences.value = []
    evidenceError.value = errorMessage(error, '求职资料读取失败。')
  } finally {
    evidenceLoading.value = false
  }
}

async function reload(): Promise<void> {
  const id = resumeId.value
  const next = await page.load()
  if (!next || resumeId.value !== id) return
  await Promise.all([loadLayout(id), outcomes.value.length ? loadEvidence() : Promise.resolve()])
}

async function scrollToExportSection(): Promise<void> {
  if (route.hash !== '#export') return
  await nextTick()
  document.querySelector('#export')?.scrollIntoView({ block: 'start' })
}

function applyMaster(next: ResumeMasterView): void {
  page.data.value = next
}

function handleWriteError(error: unknown, fallback: string): void {
  if (isForbidden(error)) {
    writeForbidden.value = error
    return
  }
  if (isVersionConflict(error)) {
    formError.value = '这份简历刚在其他页面更新过，已重新读取，请再操作一次。'
    void reload()
    return
  }
  if (isApiClientError(error) && error.reason === 'RESUME_VERSION_NOT_EXPORTABLE') {
    formError.value = '只有已冻结的版本可以导出。'
    return
  }
  formError.value = errorMessage(error, fallback)
}

function openDrawer(item: KeyOutcome): void {
  if (evidenceError.value) {
    formError.value = '求职资料还没有读取成功，暂时不能关联。'
    return
  }
  drawerOutcome.value = item
  drawerOpen.value = true
}

async function onDrawerConfirm(evidenceId: string): Promise<void> {
  const item = drawerOutcome.value
  if (!master.value || !item?.id) return
  pending.value = `${item.id}-link`
  try {
    applyMaster(await linkOutcomeEvidence(master.value.id, item.id, evidenceId, master.value.version))
    notice.value = '已关联证据。'
    drawerOpen.value = false
  } catch (error) {
    handleWriteError(error, '关联证据失败')
  } finally {
    pending.value = ''
  }
}

async function onWaive(item: KeyOutcome): Promise<void> {
  if (!master.value || !item.id || evidenceError.value) return
  pending.value = `waive-${item.id}`
  try {
    applyMaster(await waiveOutcome(master.value.id, item.id, true, master.value.version))
    notice.value = '已确认这条成果暂无证据。'
  } catch (error) {
    handleWriteError(error, '确认暂无证据失败')
  } finally {
    pending.value = ''
  }
}

async function onReady(): Promise<void> {
  if (!master.value || !canMarkReady.value) return
  pending.value = 'ready'
  try {
    applyMaster(await markResumeReady(master.value.id, master.value.version))
    notice.value = '已标为可导出，现在可以冻结版本。'
  } catch (error) {
    handleWriteError(error, '标为可导出失败')
  } finally {
    pending.value = ''
  }
}

async function onFreeze(): Promise<void> {
  if (!master.value || !canFreeze.value) return
  pending.value = 'freeze'
  try {
    await freezeResume(master.value.id, master.value.version)
    freezeOpen.value = false
    notice.value = '已冻结为新版本，可以导出了。'
    await reload()
  } catch (error) {
    handleWriteError(error, '冻结失败')
  } finally {
    pending.value = ''
  }
}

async function onConfirmArchive(): Promise<void> {
  const target = archiveTarget.value
  if (!target) return
  pending.value = `archive-${target.id}`
  try {
    if (target.kind === 'master') {
      applyMaster(await archiveResume(target.id, target.version))
      notice.value = '简历已归档，可以随时恢复。'
    } else {
      await archiveResumeVersion(target.id, target.version)
      notice.value = '版本已归档。'
      await reload()
    }
    archiveTarget.value = null
  } catch (error) {
    handleWriteError(error, '归档失败')
  } finally {
    pending.value = ''
  }
}

async function onRestoreMaster(): Promise<void> {
  if (!master.value) return
  pending.value = 'restore-master'
  try {
    const next = await restoreResume(master.value.id, master.value.version)
    applyMaster(next)
    notice.value = `简历已恢复为「${glossMasterStatus(next.status)}」。`
  } catch (error) {
    handleWriteError(error, '恢复失败')
  } finally {
    pending.value = ''
  }
}

/** Put a frozen version's content back into the resume; what it replaces stays in the workbench history. */
async function onRestoreVersion(item: ResumeVersionView): Promise<void> {
  if (!master.value) return
  const ok = await confirm({
    title: `把简历内容恢复到${versionLabel(item)}？`,
    message: '简历会换成这个版本冻结时的内容，排版与模板不变。现在的内容会保留在工作台的修订历史里，随时可以再恢复回来。',
    confirmText: '恢复内容',
  })
  if (!ok || !master.value) return
  pending.value = `${item.id}-restore`
  try {
    const conversation = await ensureAiResume(master.value.id)
    await restoreAiResumeVersion(conversation.id, item.id, master.value.version)
    notice.value = `已恢复到${versionLabel(item)}，原内容保留在修订历史中。`
    await reload()
  } catch (error) {
    handleWriteError(error, '恢复版本失败')
  } finally {
    pending.value = ''
  }
}

async function onConfirmVersion(item: ResumeVersionView): Promise<void> {
  pending.value = `${item.id}-confirm`
  try {
    await confirmResumeVersion(item.id, item.version)
    notice.value = '已确认并冻结这个版本。'
    await reload()
  } catch (error) {
    handleWriteError(error, '确认版本失败')
  } finally {
    pending.value = ''
  }
}

async function runExport(item: ResumeVersionView, kind: 'pdf' | 'docx'): Promise<void> {
  if (!isPdfExportable(item.status)) {
    formError.value = '只有已冻结的版本可以导出。'
    return
  }
  if (kind === 'docx' && (!item.layoutInstanceId || !docxAvailable.value)) {
    formError.value = docxUnavailableReason.value
    return
  }
  const target = kind === 'pdf' ? pdfRun : docxRun
  pending.value = `${item.id}-${kind}`
  stopPoll()
  pollAbort = new AbortController()
  try {
    const started = await (kind === 'pdf' ? exportResumePdf(item.id) : exportResumeDocx(item.id))
    target.value = { versionId: item.id, task: started }
    const finished = await pollTask(started.id, (next) => { target.value = { versionId: item.id, task: next } }, pollAbort.signal)
    target.value = { versionId: item.id, task: finished }
    const blocked = kind === 'pdf' ? pdfDownloadDisabledReason(finished) : docxDownloadDisabledReason(finished)
    if (finished.status !== 'SUCCEEDED') formError.value = finished.failureReason || '导出没有成功，可以稍后再试。'
    else if (!blocked) notice.value = kind === 'pdf' ? 'PDF 已生成，可以下载。' : 'Word 文件已生成，可以下载。'
  } catch (error) {
    if (isAbortError(error)) return
    handleWriteError(error, kind === 'pdf' ? '导出 PDF 失败' : '导出 Word 失败')
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

async function onDownload(kind: 'pdf' | 'docx'): Promise<void> {
  const run = kind === 'pdf' ? pdfRun.value : docxRun.value
  const blocked = kind === 'pdf' ? pdfDownloadBlockReason.value : docxDownloadBlockReason.value
  const fileId = run?.task.fileId?.trim()
  if (blocked || !run || !fileId) {
    formError.value = blocked || '没有可下载的文件，请重新导出。'
    return
  }
  pending.value = `${kind}-download`
  try {
    const namedUrl = run.task.downloadUrl?.trim() ?? ''
    const viaPath = isSessionFileDownloadPath(namedUrl)
    const file = kind === 'pdf'
      ? viaPath ? await downloadByPath(namedUrl) : await downloadPrivateFile(fileId)
      : viaPath ? await downloadDocxByPath(namedUrl) : await downloadPrivateDocxFile(fileId)
    triggerBrowserDownload(file.blob, resumeFileName({ title: master.value?.title, extension: kind }))
  } catch (error) {
    handleWriteError(error, '下载失败')
  } finally {
    pending.value = ''
  }
}

watch(resumeId, () => {
  stopPoll()
  pdfRun.value = null
  docxRun.value = null
  void reload()
})
watch(() => route.hash, () => void scrollToExportSection())

onMounted(async () => {
  await reload()
  await scrollToExportSection()
})

onUnmounted(stopPoll)
</script>

<template>
  <ForbidState v-if="forbidden" :error="forbidden" />
  <section v-else class="page-container resume-versions">
    <UiPageHeader :title="master ? master.title || '未命名简历' : '版本与导出'" back="/resumes" back-label="简历列表">
      <template v-if="master" #title-suffix>
        <UiBadge :tone="statusTone">{{ glossMasterStatus(master.status) }}</UiBadge>
      </template>
      <template #description>
        版本与导出<template v-if="master"> · 最后更新 {{ formatWhen(master.updatedAt) }}</template>
      </template>
      <template #actions>
        <UiButton variant="secondary" :icon="GitCompareArrows" :disabled="versions.length < 2" @click="compareOpen = true">
          版本对比
        </UiButton>
        <UiButton variant="secondary" :icon="RefreshCw" :pending="page.loading.value && page.loaded.value" @click="reload">
          刷新
        </UiButton>
        <UiButton v-if="master && !archived" data-testid="open-workbench" :icon="PenLine" :to="`/resumes/${master.id}`">
          在工作台中编辑
        </UiButton>
      </template>
    </UiPageHeader>

    <PageState
      :loading="page.loading.value"
      :error="page.error.value"
      :loaded="page.loaded.value"
      error-title="简历读取失败"
      @retry="reload"
    >
      <template #skeleton>
        <div class="editor-grid" aria-busy="true">
          <UiCard><div class="bones"><UiSkeleton height="22px" width="40%" /><UiSkeleton :lines="4" /></div></UiCard>
          <UiCard><div class="bones"><UiSkeleton :lines="5" /></div></UiCard>
        </div>
      </template>

      <div v-if="master" class="editor-grid">
        <div class="editor-main">
          <UiBanner v-if="archived" tone="warning">这份简历已归档。恢复后才能继续编辑、冻结或导出。</UiBanner>

          <UiCard id="export">
            <header class="panel-head">
              <div>
                <h2 class="panel-head__title">版本</h2>
                <p class="panel-head__sub">每个版本都是一份冻结的快照，导出的文件与它的内容完全一致。</p>
              </div>
              <div class="panel-head__actions">
                <button class="text-link" type="button" :disabled="versions.length < 2" @click="compareOpen = true">版本对比</button>
              </div>
            </header>
            <div v-if="versions.length" class="ver-list">
              <div v-for="item in versions" :key="item.id" class="ver" :class="{ 'is-focus': item.id === focusedVersionId }" data-testid="version-row">
                <span class="ver__icon" :class="`ver__icon--${versionTone(item)}`">
                  <AppIcon :name="versionIcon(item)" :size="15" />
                </span>
                <div class="ver__body">
                  <div class="ver__line">
                    <strong>{{ versionLabel(item) }}</strong>
                    <UiBadge :tone="versionTone(item)">{{ glossVersionStatus(item.status) }}</UiBadge>
                  </div>
                  <p class="fine">{{ versionSourceLabel(item.source) }} · {{ formatWhen(item.frozenAt || item.createdAt) }}</p>
                  <p v-if="item.status === 'GENERATING' || item.status === 'PENDING_USER_CONFIRMATION'" class="fine">确认后才能导出。</p>
                  <div class="ver__ops">
                    <UiButton
                      v-if="item.status === 'PENDING_USER_CONFIRMATION'"
                      :pending="pending === `${item.id}-confirm`"
                      @click="onConfirmVersion(item)"
                    >
                      确认并冻结
                    </UiButton>
                    <UiButton
                      variant="secondary"
                      :icon="Download"
                      :disabled="!isPdfExportable(item.status) || (Boolean(pending) && pending !== `${item.id}-pdf`)"
                      :pending="pending === `${item.id}-pdf`"
                      @click="runExport(item, 'pdf')"
                    >
                      导出 PDF
                    </UiButton>
                    <UiButton
                      v-if="docxAvailable && item.layoutInstanceId"
                      variant="secondary"
                      :icon="Download"
                      :disabled="!isPdfExportable(item.status) || (Boolean(pending) && pending !== `${item.id}-docx`)"
                      :pending="pending === `${item.id}-docx`"
                      @click="runExport(item, 'docx')"
                    >
                      导出 Word
                    </UiButton>
                    <UiButton
                      v-if="isRestorableVersion(item) && !archived"
                      variant="secondary"
                      :icon="RotateCcw"
                      :disabled="Boolean(pending) && pending !== `${item.id}-restore`"
                      :pending="pending === `${item.id}-restore`"
                      data-testid="restore-version"
                      @click="onRestoreVersion(item)"
                    >
                      恢复此版本
                    </UiButton>
                    <button
                      v-if="item.status !== 'ARCHIVED'"
                      class="text-link link-danger"
                      type="button"
                      :disabled="Boolean(pending)"
                      @click="archiveTarget = { kind: 'version', id: item.id, version: item.version }"
                    >
                      归档
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <UiEmptyState
              v-else
              size="sm"
              illustration="empty-resume"
              title="还没有版本"
              description="在工作台导出 PDF，或把当前内容冻结为版本后，会出现在这里。"
            />
            <footer v-if="pdfRun || docxRun" class="panel-foot export-results">
              <div v-if="pdfRun" class="export-box" data-testid="pdf-run">
                <p class="fine">{{ runLabel(pdfRun) }}的 PDF：{{ taskStateText(pdfRun.task) }}</p>
                <UiButton
                  variant="secondary"
                  :icon="Download"
                  :disabled="Boolean(pdfDownloadBlockReason)"
                  :pending="pending === 'pdf-download'"
                  @click="onDownload('pdf')"
                >
                  下载 PDF
                </UiButton>
                <p v-if="pdfDownloadBlockReason && pdfRun.task.status === 'SUCCEEDED'" class="fine">{{ pdfDownloadBlockReason }}</p>
              </div>
              <div v-if="docxRun" class="export-box">
                <p class="fine">{{ runLabel(docxRun) }}的 Word 文件：{{ taskStateText(docxRun.task) }}</p>
                <UiButton
                  variant="secondary"
                  :icon="Download"
                  :disabled="Boolean(docxDownloadBlockReason)"
                  :pending="pending === 'docx-download'"
                  @click="onDownload('docx')"
                >
                  下载 Word
                </UiButton>
                <p v-if="docxDownloadBlockReason && docxRun.task.status === 'SUCCEEDED'" class="fine">{{ docxDownloadBlockReason }}</p>
              </div>
            </footer>
          </UiCard>

          <UiCard v-if="outcomes.length">
            <header class="panel-head">
              <div>
                <h2 class="panel-head__title">关键成果与证据</h2>
                <p class="panel-head__sub">冻结前，每条关键成果需要关联求职资料，或在冻结时逐条确认暂无证据。</p>
              </div>
            </header>
            <p class="fine outcome-stats">
              已关联 {{ outcomeStats.linked }} · 已确认暂无证据 {{ outcomeStats.waived }} · 待处理 {{ outcomeStats.open }} / 共 {{ outcomeStats.total }} 条
            </p>
            <UiBanner v-if="evidenceError" tone="danger" class="outcome-error">
              {{ evidenceError }}读取成功前不能关联证据。
              <button class="text-link" type="button" :disabled="evidenceLoading" @click="loadEvidence">重试</button>
            </UiBanner>
            <div class="outcome-list">
              <div v-for="(item, index) in outcomes" :key="item.id || `outcome-${index}`" class="outcome">
                <div class="outcome__head">
                  <span class="outcome__no">成果 {{ index + 1 }}</span>
                  <UiBadge v-if="item.evidenceId" tone="success">已关联资料</UiBadge>
                  <UiBadge v-else-if="item.waiveNoEvidence" tone="accent">已确认暂无资料</UiBadge>
                  <UiBadge v-else tone="danger">待处理</UiBadge>
                </div>
                <p class="outcome__text">{{ item.text }}</p>
                <div class="outcome__foot">
                  <UiButton
                    variant="secondary"
                    :icon="LinkIcon"
                    :disabled="archived || !item.id || Boolean(evidenceError) || evidenceLoading"
                    @click="openDrawer(item)"
                  >
                    {{ outcomeResolved(item) && item.evidenceId ? '更换证据' : '关联证据' }}
                  </UiButton>
                </div>
              </div>
            </div>
          </UiCard>
        </div>

        <aside class="rail">
          <UiCard>
            <header class="panel-head">
              <div>
                <h2 class="panel-head__title">简历状态</h2>
                <p class="panel-head__sub">标为可导出后，才能把当前内容冻结为新版本。</p>
              </div>
            </header>
            <dl class="facts">
              <div class="facts__row">
                <dt>状态</dt>
                <dd><UiBadge :tone="statusTone">{{ glossMasterStatus(master.status) }}</UiBadge></dd>
              </div>
              <div class="facts__row">
                <dt>来源</dt>
                <dd>{{ masterSourceLabel(master.source) }}</dd>
              </div>
              <div class="facts__row">
                <dt>更新时间</dt>
                <dd>{{ formatWhen(master.updatedAt) }}</dd>
              </div>
            </dl>
            <template v-if="!archived">
              <p v-if="readyBlockReason" class="fine">{{ readyBlockReason }}</p>
              <div class="rail__ops">
                <UiButton
                  v-if="master.status !== 'READY_TO_EXPORT'"
                  :disabled="!canMarkReady"
                  :pending="pending === 'ready'"
                  @click="onReady"
                >
                  标为可导出
                </UiButton>
                <UiButton data-testid="open-freeze" :icon="Lock" @click="freezeOpen = true">
                  冻结当前版本
                </UiButton>
              </div>
            </template>
            <footer class="panel-foot">
              <button
                v-if="!archived"
                class="text-link link-danger"
                type="button"
                @click="archiveTarget = { kind: 'master', id: master.id, version: master.version }"
              >
                归档简历
              </button>
              <UiButton v-else :pending="pending === 'restore-master'" @click="onRestoreMaster">恢复简历</UiButton>
            </footer>
          </UiCard>

          <UiCard>
            <header class="panel-head">
              <div>
                <h2 class="panel-head__title">当前模板</h2>
                <p class="panel-head__sub">冻结时会一并保存当时的模板与设计。</p>
              </div>
            </header>
            <UiBanner v-if="layoutReadFailed" tone="warning">
              模板状态读取失败，暂时不能冻结。
              <button class="text-link" type="button" @click="loadLayout(master.id)">重试</button>
            </UiBanner>
            <div v-else-if="currentLayout?.selected && currentLayout.layout" class="layout-current">
              <div class="layout-current__head">
                <strong>{{ currentLayout.layout.templateName || '历史模板' }}</strong>
                <UiBadge :tone="layoutStatusLabel.tone">{{ layoutStatusLabel.text }}</UiBadge>
              </div>
              <dl class="facts">
                <div class="facts__row">
                  <dt>Word 导出</dt>
                  <dd>{{ docxAvailable ? '支持' : '暂不支持' }}</dd>
                </div>
              </dl>
              <p v-if="layoutOverflow" class="fine layout-overflow">
                <AppIcon name="alert-circle" :size="14" />
                内容超出页面，请在工作台中精简后再冻结。
              </p>
            </div>
            <p v-else class="fine">还没有选择模板。可以在工作台的“模板”里挑选。</p>
          </UiCard>
        </aside>
      </div>
    </PageState>
  </section>

  <ResumeEvidenceDrawer
    :open="drawerOpen"
    :outcome="drawerOutcome"
    :evidences="evidences"
    :evidence-error="evidenceError"
    :linking="Boolean(drawerOutcome && pending === `${drawerOutcome.id}-link`)"
    @close="drawerOpen = false"
    @confirm="onDrawerConfirm"
  />
  <ResumeFreezeModal
    :open="freezeOpen"
    :title="master?.title ?? ''"
    :outcomes="master?.keyOutcomes ?? []"
    :ready-to-apply="master?.status === 'READY_TO_EXPORT'"
    :pending-ai="pendingAiCount"
    :template-name="currentLayout?.layout?.templateName ?? ''"
    :layout-status="currentLayout?.layout?.status ?? ''"
    :docx-available="docxAvailable"
    :can-freeze="canFreeze"
    :freeze-block-reason="freezeBlockReason"
    :freeze-pending="pending === 'freeze'"
    :waive-pending="pending.startsWith('waive-') ? pending.slice(6) : ''"
    :evidence-error="evidenceError"
    @close="freezeOpen = false"
    @freeze="onFreeze"
    @waive="onWaive"
  />
  <ResumeCompareModal :open="compareOpen" :versions="versions" @close="compareOpen = false" @forbidden="writeForbidden = $event" />
  <UiDialog
    :open="Boolean(archiveTarget)"
    :title="archiveTarget?.kind === 'master' ? '归档简历' : '归档版本'"
    width="sm"
    :mobile-sheet="false"
    @close="archiveTarget = null"
  >
    <p class="muted">
      {{ archiveTarget?.kind === 'master' ? '归档后简历会移到「已归档」，可以随时恢复。' : '归档后这个版本不能再导出，记录仍会保留。' }}
    </p>
    <template #footer>
      <UiButton variant="secondary" :disabled="Boolean(pending)" @click="archiveTarget = null">取消</UiButton>
      <UiButton variant="danger" :pending="Boolean(pending)" @click="onConfirmArchive">确认归档</UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.resume-versions {
  display: grid;
  gap: var(--space-6);
  align-content: start;
}

.bones {
  display: grid;
  gap: var(--space-3);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.panel-head__title {
  font-size: var(--fs-h3);
  line-height: var(--lh-h3);
  font-weight: 600;
}

.panel-head__sub {
  margin-top: 2px;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  line-height: var(--lh-xs);
}

.panel-head__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.panel-foot {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
}

.editor-main {
  display: grid;
  gap: 20px;
  align-content: start;
  min-width: 0;
}

.rail {
  display: grid;
  gap: 20px;
  align-content: start;
  position: sticky;
  top: 84px;
}

#export {
  scroll-margin-top: 84px;
}

@media (max-width: 1200px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }

  .rail {
    position: static;
  }
}

.outcome-stats {
  margin-bottom: 12px;
}

.outcome-error {
  margin-bottom: var(--space-3);
}

.outcome-list {
  display: grid;
  gap: 12px;
}

.outcome {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: 12px 14px;
  display: grid;
  gap: 8px;
}

.outcome__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.outcome__no {
  font-size: 13px;
  font-weight: 600;
}

.outcome__text {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.outcome__foot {
  display: flex;
  justify-content: flex-end;
}

.link-danger {
  color: var(--color-danger);
}

.link-danger:hover:not(:disabled) {
  color: var(--color-danger);
}

.facts {
  display: grid;
  gap: 8px;
  margin: 0 0 12px;
}

.facts__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}

.facts__row dt {
  color: var(--text-tertiary);
}

.facts__row dd {
  margin: 0;
}

.rail__ops {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.layout-current {
  display: grid;
  gap: 12px;
}

.layout-current__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 14px;
}

.layout-overflow {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--color-danger);
}

.ver-list {
  display: grid;
  gap: 12px;
}

.ver {
  display: flex;
  gap: 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: 12px;
}

.ver.is-focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.ver__icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.ver__icon--primary {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.ver__icon--accent {
  background: var(--color-accent-soft);
  color: var(--color-accent-text);
}

.ver__icon--neutral {
  background: var(--surface-2);
  color: var(--text-tertiary);
}

.ver__body {
  display: grid;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.ver__line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ver__ops {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.export-results {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.export-box {
  display: grid;
  gap: 8px;
  justify-items: start;
  min-width: 0;
}

@media (max-width: 640px) {
  .export-results {
    grid-template-columns: 1fr;
  }
}
</style>
