<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import {
  errorMessage,
  isApiClientError,
  isForbidden,
  isVersionConflict,
  versionConflictMessage,
} from '@/shared/api/types'
import type { TaskView } from '@/shared/api/task'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import { formatWhen } from '@/shared/lib/datetime'
import type { IconName } from '@/shared/ui/icons'
import { fetchCareerRecords } from '@/features/career-library/services/careerLibraryApi'
import type { CareerRecord } from '@/features/career-library/types'
import ResumeCompareModal from '../components/ResumeCompareModal.vue'
import ResumeEvidenceDrawer from '../components/ResumeEvidenceDrawer.vue'
import ResumeFreezeModal from '../components/ResumeFreezeModal.vue'
import {
  glossMasterStatus,
  glossVersionStatus,
  docxDownloadDisabledReason,
  hydrateDraft,
  isPdfExportable,
  pdfDownloadDisabledReason,
  unresolvedOutcomes,
} from '../labels'
import {
  archiveResume,
  archiveResumeVersion,
  confirmResumeVersion,
  downloadByPath,
  downloadDocxByPath,
  downloadPrivateFile,
  downloadPrivateDocxFile,
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
  updateResume,
  waiveOutcome,
} from '../services/resumeApi'
import type {
  CurrentResumeLayout,
  KeyOutcome,
  ResumeDraft,
  ResumeMasterView,
  ResumeTemplateDetail,
  ResumeVersionView,
} from '../types'

type ArchiveTarget = {
  kind: 'master' | 'version'
  id: string
  version: number
  bound: boolean
}

const route = useRoute()
const loading = ref(true)
const saving = ref(false)
const pending = ref('')
const pageError = ref('')
const formError = ref('')
const evidenceError = ref('')
const notice = ref('')
const forbidden = ref<unknown>(null)
const master = ref<ResumeMasterView | null>(null)
const currentLayout = ref<CurrentResumeLayout | null>(null)
const currentTemplateDetail = ref<ResumeTemplateDetail | null>(null)
const layoutReadFailed = ref(false)
const layoutError = ref('')
useToastFeedback(pageError, 'error', 'resume-editor-page-error')
useToastFeedback(formError, 'error', 'resume-editor-form-error')
useToastFeedback(layoutError, 'error', 'resume-editor-layout-error')
useToastFeedback(notice, 'success', 'resume-editor-notice')
const draft = reactive<ResumeDraft>(hydrateDraft())
const evidences = ref<CareerRecord[]>([])
const drawerOpen = ref(false)
const drawerOutcome = ref<KeyOutcome | null>(null)
const freezeOpen = ref(false)
const compareOpen = ref(false)
const archiveTarget = ref<ArchiveTarget | null>(null)
const exportTask = ref<TaskView | null>(null)
const docxExportTask = ref<TaskView | null>(null)
let pollAbort: AbortController | null = null

const archived = computed(() => master.value?.status === 'ARCHIVED')
const unresolved = computed(() => unresolvedOutcomes(draft.outcomes))
const versions = computed(() => master.value?.versions ?? [])
const focusedVersionId = computed(() =>
  typeof route.query.versionId === 'string' ? route.query.versionId : '',
)
const statusTone = computed(() => {
  const status = master.value?.status
  if (status === 'READY_TO_EXPORT') {
    return 'green'
  }
  if (status === 'PENDING_CONFIRMATION') {
    return 'orange'
  }
  if (status === 'ARCHIVED') {
    return 'gray'
  }
  return 'blue'
})
const canMarkReady = computed(() => {
  if (!master.value || archived.value) {
    return false
  }
  if (master.value.status === 'READY_TO_EXPORT') {
    return false
  }
  return unresolved.value.length === 0
})
const readyBlockReason = computed(() => {
  if (archived.value) {
    return '已归档主档不能标为可导出。请先恢复。'
  }
  if (master.value?.status === 'READY_TO_EXPORT') {
    return '主档已是可导出状态。'
  }
  if (unresolved.value.length) {
    return `关键成果须关联求职资料，或逐条确认「暂无资料仍要冻结」。未处理 ${unresolved.value.length} 条。`
  }
  return ''
})
const canFreeze = computed(
  () =>
    master.value?.status === 'READY_TO_EXPORT' &&
    unresolved.value.length === 0 &&
    !layoutReadFailed.value &&
    currentLayout.value?.layout?.status !== 'OVERFLOW' &&
    !archived.value,
)
const freezeBlockReason = computed(() => {
  if (layoutReadFailed.value) {
    return '当前版式状态读取失败。为避免绕过溢出检查，暂时不能冻结。'
  }
  if (currentLayout.value?.layout?.status === 'OVERFLOW') {
    const first = currentLayout.value.layout.overflow.items[0]
    return first
      ? `第 ${first.page} 页的 ${first.slotKey} 区域溢出，不能冻结或导出。`
      : '当前版式存在内容溢出，不能冻结或导出。'
  }
  if (master.value?.status !== 'READY_TO_EXPORT') {
    return '只有可导出主档才能冻结版本。'
  }
  return readyBlockReason.value
})
const pdfDownloadBlockReason = computed(() => pdfDownloadDisabledReason(exportTask.value))
const docxDownloadBlockReason = computed(() => docxDownloadDisabledReason(docxExportTask.value))
const docxAvailable = computed(() => Boolean(currentTemplateDetail.value?.docxAvailable))
const docxUnavailableReason = computed(() => {
  if (!currentLayout.value?.selected || !currentLayout.value.layout?.templateId) {
    return '当前简历没有可导出 DOCX 的结构化模板。'
  }
  return currentTemplateDetail.value?.docxUnavailableReason || '当前模板尚未通过 DOCX 全部门禁。'
})
const outcomeStats = computed(() => {
  const items = draft.outcomes.filter((item) => (item.text ?? '').trim())
  const linked = items.filter((item) => Boolean(item.evidenceId && item.evidenceId.trim())).length
  const waived = items.filter((item) => !item.evidenceId && item.waiveNoEvidence).length
  const total = items.length
  const done = linked + waived
  return {
    total,
    linked,
    waived,
    open: total - done,
    pct: total ? Math.round((done / total) * 100) : 100,
  }
})

function versionIcon(item: ResumeVersionView): IconName {
  if (item.status === 'FROZEN') {
    return 'lock'
  }
  if (item.status === 'PENDING_USER_CONFIRMATION' || item.status === 'GENERATING') {
    return 'copy'
  }
  return 'file-text'
}

function versionTone(item: ResumeVersionView): 'blue' | 'green' | 'orange' | 'gray' {
  if (item.status === 'FROZEN') {
    return 'blue'
  }
  if (item.status === 'PENDING_USER_CONFIRMATION') {
    return 'orange'
  }
  return 'gray'
}

function applyMaster(next: ResumeMasterView): void {
  master.value = next
  const hydrated = hydrateDraft(next)
  draft.title = hydrated.title
  draft.education = hydrated.education
  draft.experience = hydrated.experience
  draft.projects = hydrated.projects
  draft.skills = hydrated.skills
  draft.certificates = hydrated.certificates
  draft.selfIntro = hydrated.selfIntro
  draft.outcomes = hydrated.outcomes
}

function stopPoll(): void {
  pollAbort?.abort()
  pollAbort = null
}

async function scrollToExportSection(): Promise<void> {
  if (route.hash !== '#export') {
    return
  }
  await nextTick()
  const target = document.querySelector(route.hash)
  if (!target) {
    return
  }
  const targetTop = target.getBoundingClientRect().top + window.scrollY
  window.scrollTo({ top: Math.max(0, targetTop - 84) })
  const positioned = target.getBoundingClientRect()
  if (positioned.top >= window.innerHeight || positioned.bottom <= 0) {
    window.scrollTo({ top: document.documentElement.scrollHeight })
  }
}

async function load(): Promise<void> {
  const id = String(route.params.id ?? '')
  loading.value = true
  pageError.value = ''
  evidenceError.value = ''
  layoutError.value = ''
  layoutReadFailed.value = false
  forbidden.value = null
  try {
    const next = await fetchResume(id)
    applyMaster(next)
    try {
      currentLayout.value = await fetchCurrentResumeLayout(id)
      const templateId = currentLayout.value.layout?.templateId
      currentTemplateDetail.value = templateId ? await fetchResumeTemplate(templateId) : null
    } catch (error) {
      currentLayout.value = null
      currentTemplateDetail.value = null
      layoutReadFailed.value = true
      layoutError.value = errorMessage(
        error,
        '当前版式读取失败。冻结已按失败关闭，不能据此绕过溢出检查。',
      )
    }
    try {
      const result = await fetchCareerRecords({ status: 'ACTIVE', page: 0, size: 100 })
      evidences.value = result.items
    } catch (error) {
      evidences.value = []
      evidenceError.value = errorMessage(error, '求职资料列表读取失败。不能据此当成没有资料，也不能默认放行。')
    }
  } catch (error) {
    master.value = null
    currentLayout.value = null
    currentTemplateDetail.value = null
    if (isForbidden(error)) {
      forbidden.value = error
      return
    }
    pageError.value = errorMessage(error, '简历读取失败')
  } finally {
    loading.value = false
    await scrollToExportSection()
  }
}

function handleWriteError(error: unknown, fallback: string): void {
  if (isForbidden(error)) {
    forbidden.value = error
    return
  }
  if (isVersionConflict(error)) {
    formError.value = versionConflictMessage(error)
    void load()
    return
  }
  if (isApiClientError(error) && error.reason === 'RESUME_VERSION_NOT_EXPORTABLE') {
    formError.value = `${error.message}（RESUME_VERSION_NOT_EXPORTABLE）`
    return
  }
  formError.value = errorMessage(error, fallback)
}

async function onSave(): Promise<void> {
  if (!master.value || archived.value) {
    return
  }
  formError.value = ''
  notice.value = ''
  saving.value = true
  try {
    const next = await updateResume(master.value.id, {
      title: draft.title,
      education: draft.education,
      experience: draft.experience,
      projects: draft.projects,
      skills: draft.skills,
      certificates: draft.certificates,
      selfIntro: draft.selfIntro,
      keyOutcomes: draft.outcomes.map((item) => ({
        id: item.id || undefined,
        text: item.text,
        evidenceId: item.evidenceId || undefined,
        waiveNoEvidence: item.waiveNoEvidence,
      })),
      expectedVersion: master.value.version,
    })
    applyMaster(next)
    try {
      currentLayout.value = await fetchCurrentResumeLayout(next.id)
      layoutReadFailed.value = false
      layoutError.value = ''
    } catch (error) {
      layoutReadFailed.value = true
      layoutError.value = errorMessage(error, '内容已保存，但版式状态刷新失败。冻结已暂时关闭。')
    }
    notice.value = '正式字段已写入。冻结后不可原地改版本，要改须再冻一份。'
  } catch (error) {
    handleWriteError(error, '保存简历失败')
  } finally {
    saving.value = false
  }
}

function addOutcome(): void {
  draft.outcomes.push({
    id: '',
    text: '',
    evidenceId: '',
    waiveNoEvidence: false,
  })
}

function removeOutcome(index: number): void {
  draft.outcomes.splice(index, 1)
}

function openDrawer(item: KeyOutcome): void {
  if (evidenceError.value) {
    formError.value = 'ACTIVE 证据列表读取失败。不能据此关联或当成没有证据。'
    return
  }
  if (!item.id) {
    formError.value = '请先保存主档，让关键成果获得编号后再关联证据。'
    return
  }
  formError.value = ''
  drawerOutcome.value = item
  drawerOpen.value = true
}

async function onDrawerConfirm(evidenceId: string): Promise<void> {
  const item = drawerOutcome.value
  if (!master.value || !item?.id) {
    formError.value = '请先保存主档，让关键成果获得编号后再关联证据。'
    return
  }
  pending.value = `${item.id}-link`
  formError.value = ''
  try {
    const next = await linkOutcomeEvidence(master.value.id, item.id, evidenceId, master.value.version)
    applyMaster(next)
    notice.value = '已关联证据。冻结时才会登记有效引用。'
    drawerOpen.value = false
  } catch (error) {
    handleWriteError(error, '关联证据失败')
  } finally {
    pending.value = ''
  }
}

async function onWaive(item: KeyOutcome): Promise<void> {
  if (evidenceError.value) {
    formError.value = 'ACTIVE 证据列表读取失败。不能把空列表当成没有证据，也不能确认暂无证据。'
    return
  }
  if (!master.value || !item.id) {
    formError.value = '请先保存主档，让关键成果获得编号后再确认暂无证据。'
    return
  }
  pending.value = `waive-${item.id}`
  formError.value = ''
  try {
    const next = await waiveOutcome(master.value.id, item.id, true, master.value.version)
    applyMaster(next)
    notice.value = '已逐条确认暂无证据仍要冻结。'
  } catch (error) {
    handleWriteError(error, '确认暂无证据失败')
  } finally {
    pending.value = ''
  }
}

async function onReady(): Promise<void> {
  if (!master.value || !canMarkReady.value) {
    formError.value = readyBlockReason.value || '未确认 AI 或未处理成果，不能标为可导出。'
    return
  }
  pending.value = 'ready'
  formError.value = ''
  try {
    const next = await markResumeReady(master.value.id, master.value.version)
    applyMaster(next)
    notice.value = '主档已标为可导出。现在可以冻结版本。'
  } catch (error) {
    handleWriteError(error, '标为可导出失败')
  } finally {
    pending.value = ''
  }
}

async function onFreeze(): Promise<void> {
  if (!master.value || !canFreeze.value) {
    formError.value = freezeBlockReason.value || '未确认 AI 或主档未标为可导出，不能冻结。'
    return
  }
  pending.value = 'freeze'
  formError.value = ''
  try {
    const version = await freezeResume(master.value.id, master.value.version)
    freezeOpen.value = false
    notice.value = `已冻结版本 ${version.id}。冻结后不可原地修改。`
    await load()
  } catch (error) {
    handleWriteError(error, '冻结失败')
  } finally {
    pending.value = ''
  }
}

function openArchiveMaster(): void {
  if (!master.value) {
    return
  }
  archiveTarget.value = { kind: 'master', id: master.value.id, version: master.value.version, bound: false }
}

function openArchiveVersion(item: ResumeVersionView): void {
  archiveTarget.value = { kind: 'version', id: item.id, version: item.version, bound: false }
}

async function onConfirmArchive(): Promise<void> {
  const target = archiveTarget.value
  if (!target) {
    return
  }
  pending.value = `archive-${target.id}`
  formError.value = ''
  try {
    if (target.kind === 'master') {
      const next = await archiveResume(target.id, target.version)
      applyMaster(next)
      notice.value = '主档已归档，可恢复。没有物理删除。'
    } else {
      await archiveResumeVersion(target.id, target.version)
      notice.value = '版本已归档。'
      await load()
    }
    archiveTarget.value = null
  } catch (error) {
    handleWriteError(error, '归档失败')
  } finally {
    pending.value = ''
  }
}

async function onRestoreMaster(): Promise<void> {
  if (!master.value) {
    return
  }
  pending.value = 'restore-master'
  formError.value = ''
  try {
    const next = await restoreResume(master.value.id, master.value.version)
    applyMaster(next)
    notice.value = `主档已恢复为${glossMasterStatus(next.status)}。`
  } catch (error) {
    handleWriteError(error, '恢复失败')
  } finally {
    pending.value = ''
  }
}

async function onConfirmVersion(item: ResumeVersionView): Promise<void> {
  pending.value = `${item.id}-confirm`
  formError.value = ''
  try {
    await confirmResumeVersion(item.id, item.version)
    notice.value = '定制版本已确认并冻结。任务不得自动冻结。'
    await load()
  } catch (error) {
    handleWriteError(error, '确认版本失败')
  } finally {
    pending.value = ''
  }
}

async function onExport(item: ResumeVersionView): Promise<void> {
  if (!isPdfExportable(item.status)) {
    formError.value = '仅可从已冻结版本导出 PDF。（RESUME_VERSION_NOT_EXPORTABLE）'
    return
  }
  pending.value = `${item.id}-pdf`
  formError.value = ''
  stopPoll()
  pollAbort = new AbortController()
  try {
    const started = await exportResumePdf(item.id)
    exportTask.value = started
    const finished = await pollTask(
      started.id,
      (next) => {
        exportTask.value = next
      },
      pollAbort.signal,
    )
    exportTask.value = finished
    if (finished.status === 'SUCCEEDED') {
      if (pdfDownloadDisabledReason(finished)) {
        notice.value = 'PDF 导出任务已成功，但任务查询未给出 fileId。下载按钮已禁用，本页不会伪造下载。'
      } else {
        notice.value = 'PDF 导出任务已成功。可按下方限时下载取回文件；这不是分享链接，也不是物理删除。'
      }
    } else {
      formError.value = finished.failureReason || 'PDF 导出未成功。可稍后对同一冻结版本再发起。'
    }
  } catch (error) {
    if (isAbortError(error)) {
      return
    }
    handleWriteError(error, '导出 PDF 失败')
  } finally {
    pending.value = ''
  }
}

async function onExportDocx(item: ResumeVersionView): Promise<void> {
  if (!isPdfExportable(item.status)) {
    formError.value = '仅可从已冻结版本导出 DOCX。（RESUME_VERSION_NOT_EXPORTABLE）'
    return
  }
  if (!item.layoutInstanceId || !docxAvailable.value) {
    formError.value = docxUnavailableReason.value
    return
  }
  pending.value = `${item.id}-docx`
  formError.value = ''
  stopPoll()
  pollAbort = new AbortController()
  try {
    const started = await exportResumeDocx(item.id)
    docxExportTask.value = started
    const finished = await pollTask(
      started.id,
      (next) => {
        docxExportTask.value = next
      },
      pollAbort.signal,
    )
    docxExportTask.value = finished
    if (finished.status === 'SUCCEEDED') {
      notice.value = docxDownloadDisabledReason(finished)
        ? 'DOCX 导出任务成功，但没有可用 fileId，下载已禁用。'
        : 'DOCX 已通过服务端 OOXML、安全和文本顺序校验，可限时下载。'
    } else {
      formError.value = finished.failureReason || 'DOCX 导出未成功。'
    }
  } catch (error) {
    if (isAbortError(error)) return
    handleWriteError(error, '导出 DOCX 失败')
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

async function onDownloadPdf(): Promise<void> {
  const blocked = pdfDownloadBlockReason.value
  if (blocked) {
    formError.value = blocked
    return
  }
  const task = exportTask.value
  const fileId = task?.fileId?.trim()
  if (!task || !fileId) {
    formError.value = '没有 fileId，不能下载。本页不会伪造文件。'
    return
  }
  pending.value = 'pdf-download'
  formError.value = ''
  try {
    const namedUrl = task.downloadUrl?.trim() ?? ''
    const useSessionPath = isSessionFileDownloadPath(namedUrl)
    const file = useSessionPath ? await downloadByPath(namedUrl) : await downloadPrivateFile(fileId)
    triggerBrowserDownload(file.blob, file.filename || 'resume.pdf')
    notice.value = useSessionPath
      ? '已按任务 downloadUrl 走会话限时下载 PDF。'
      : '已从私有文件限时下载接口取回 PDF。'
  } catch (error) {
    handleWriteError(error, 'PDF 限时下载失败')
  } finally {
    pending.value = ''
  }
}

async function onDownloadDocx(): Promise<void> {
  const blocked = docxDownloadBlockReason.value
  if (blocked) {
    formError.value = blocked
    return
  }
  const task = docxExportTask.value
  const fileId = task?.fileId?.trim()
  if (!task || !fileId) {
    formError.value = '没有 fileId，不能下载 DOCX。'
    return
  }
  pending.value = 'docx-download'
  formError.value = ''
  try {
    const namedUrl = task.downloadUrl?.trim() ?? ''
    const useSessionPath = isSessionFileDownloadPath(namedUrl)
    const file = useSessionPath ? await downloadDocxByPath(namedUrl) : await downloadPrivateDocxFile(fileId)
    triggerBrowserDownload(file.blob, file.filename || 'resume.docx')
    notice.value = '已校验 ZIP/OOXML 魔数并下载 DOCX。'
  } catch (error) {
    handleWriteError(error, 'DOCX 限时下载失败')
  } finally {
    pending.value = ''
  }
}

watch(
  () => route.params.id,
  () => {
    void load()
  },
)

watch(
  () => route.hash,
  () => {
    void scrollToExportSection()
  },
)

onMounted(() => {
  void load()
})

onUnmounted(() => {
  stopPoll()
})
</script>

<template>
      <ForbidState v-if="forbidden" :error="forbidden" />
    <section v-else class="page">
      <header class="page-head">
        <div class="page-head__left">
          <RouterLink class="text-link" to="/resumes">
            <AppIcon name="chevron-left" :size="14" />
            简历列表
          </RouterLink>
          <div class="page-head__titleline">
            <h1 class="page-head__title">{{ draft.title || '未命名简历' }}</h1>
            <AppTag v-if="master" :tone="statusTone">{{ glossMasterStatus(master.status) }}</AppTag>
          </div>
          <p v-if="master" class="page-head__sub">
            最后保存 {{ formatWhen(master.updatedAt) }} · 版本 v{{ master.version }} · 来源
            {{ master.source || '—' }}
          </p>
        </div>
        <div class="page-head__actions">
          <AppButton variant="ghost" :disabled="versions.length < 2" @click="compareOpen = true">
            <AppIcon name="copy" :size="15" />
            版本对比
          </AppButton>
          <AppButton variant="ghost" :pending="loading && !master" @click="load">
            <AppIcon name="refresh" :size="15" />
            刷新
          </AppButton>
          <AppButton v-if="master && !archived" :pending="saving" @click="onSave">
            <AppIcon name="check" :size="15" />
            {{ saving ? '正在写入…' : '保存' }}
          </AppButton>
        </div>
      </header>

      <AppBanner v-if="master && unresolved.length" tone="warn">
        有 {{ unresolved.length }} 条关键成果尚未关联证据，也未逐条确认暂无证据。
      </AppBanner>

      <div v-if="loading && !master" class="card bones" aria-busy="true">
        <div class="bone" />
        <div class="bone" />
        <div class="bone bone--short" />
      </div>

      <div v-else-if="master" class="editor-grid">
        <div class="editor-main">
          <AppCard title="基本信息" sub="简历名称用于列表与快照标识。">
            <AppField id="resume-edit-title" label="简历名称">
              <input
                id="resume-edit-title"
                v-model="draft.title"
                class="input"
                type="text"
                :disabled="saving || archived"
              />
            </AppField>
          </AppCard>

          <AppCard title="内容分节" sub="不得虚构公司、时间、项目或成果数字；无来源数字请改成定性描述。">
            <div class="form-stack">
              <AppField id="resume-edu" label="教育经历">
                <textarea
                  id="resume-edu"
                  v-model="draft.education"
                  class="textarea"
                  rows="3"
                  :disabled="saving || archived"
                />
              </AppField>
              <AppField id="resume-exp" label="实习 / 工作经历">
                <textarea
                  id="resume-exp"
                  v-model="draft.experience"
                  class="textarea"
                  rows="4"
                  :disabled="saving || archived"
                />
              </AppField>
              <AppField id="resume-proj" label="项目经历">
                <textarea
                  id="resume-proj"
                  v-model="draft.projects"
                  class="textarea"
                  rows="4"
                  :disabled="saving || archived"
                />
              </AppField>
              <AppField id="resume-skills" label="技能">
                <textarea
                  id="resume-skills"
                  v-model="draft.skills"
                  class="textarea"
                  rows="3"
                  :disabled="saving || archived"
                />
              </AppField>
              <AppField id="resume-certs" label="证书" hint="没有则留空，不要编造。">
                <textarea
                  id="resume-certs"
                  v-model="draft.certificates"
                  class="textarea"
                  rows="3"
                  :disabled="saving || archived"
                />
              </AppField>
              <AppField id="resume-intro" label="自我介绍">
                <textarea
                  id="resume-intro"
                  v-model="draft.selfIntro"
                  class="textarea"
                  rows="4"
                  :disabled="saving || archived"
                />
              </AppField>
            </div>
            <template #footer>
              <AppButton :disabled="archived" :pending="saving" @click="onSave">
                {{ saving ? '正在写入…' : '保存正式字段' }}
              </AppButton>
            </template>
          </AppCard>

          <AppCard
            title="关键成果"
            sub="每条成果须关联使用中的求职资料；确无资料的在冻结弹窗中逐条确认。"
          >
            <template #actions>
              <AppButton variant="ghost" :disabled="archived" @click="addOutcome">
                <AppIcon name="plus" :size="14" />
                增加成果
              </AppButton>
            </template>
            <div v-if="draft.outcomes.length" class="outcome-list">
              <div v-for="(item, index) in draft.outcomes" :key="item.id || `new-${index}`" class="outcome">
                <div class="outcome__head">
                  <span class="outcome__no">成果 {{ index + 1 }}</span>
                  <AppTag v-if="item.evidenceId" tone="green">已关联资料</AppTag>
                  <AppTag v-else-if="item.waiveNoEvidence" tone="orange">已确认暂无资料</AppTag>
                  <AppTag v-else-if="item.text.trim()" tone="red">未处理</AppTag>
                  <AppTag v-else tone="gray">待填写</AppTag>
                </div>
                <AppField :id="`outcome-text-${index}`" label="成果描述">
                  <textarea
                    :id="`outcome-text-${index}`"
                    v-model="item.text"
                    class="textarea"
                    rows="2"
                    :disabled="saving || archived"
                  />
                </AppField>
                <div class="outcome__foot">
                  <p class="fine">
                    {{ item.evidenceId ? `已关联资料 ${item.evidenceId}` : '尚未关联资料' }}
                    <template v-if="!item.id"> · 保存后获得编号才能关联</template>
                  </p>
                  <div class="outcome__ops">
                    <AppButton
                      variant="ghost"
                      :disabled="archived || !item.id || Boolean(evidenceError)"
                      @click="openDrawer(item)"
                    >
                      <AppIcon name="link" :size="14" />
                      关联证据
                    </AppButton>
                    <AppButton variant="text" class="link-danger" :disabled="archived" @click="removeOutcome(index)">
                      移除
                    </AppButton>
                  </div>
                </div>
              </div>
            </div>
            <AppEmpty v-else text="还没有关键成果" hint="空列表可以通过冻结闸；有条目就必须逐条处理。" icon="award" />
          </AppCard>

          <div class="ai-workbench-entry">
            <div><AppIcon name="sparkles" :size="18" /><span><strong>需要 AI 帮你修改内容？</strong><small>AI 工作台会把建议拆成逐条修改，由你确认后写入。</small></span></div>
            <RouterLink class="btn btn--ghost" :to="`/resumes/${master.id}`">前往 AI 工作台<AppIcon name="arrow-right" :size="14" /></RouterLink>
          </div>
        </div>

        <aside class="rail">
          <AppCard title="当前模板" sub="版式与内容分离；冻结时在同一事务中生成不可变快照。">
            <template v-if="currentLayout?.selected && currentLayout.layout">
              <div class="layout-current">
                <div class="layout-current__head">
                  <div>
                    <strong>{{ currentLayout.layout.templateName || '历史模板' }}</strong>
                    <p class="fine">{{ currentLayout.layout.templateId || currentLayout.layout.templateVersionId }}</p>
                  </div>
                  <AppTag
                    :tone="
                      currentLayout.layout.status === 'OVERFLOW'
                        ? 'red'
                        : currentLayout.layout.status === 'FROZEN'
                          ? 'blue'
                          : 'green'
                    "
                  >
                    {{
                      currentLayout.layout.status === 'OVERFLOW'
                        ? '内容溢出'
                        : currentLayout.layout.status === 'FROZEN'
                          ? '已随版本冻结'
                          : '容量有效'
                    }}
                  </AppTag>
                </div>
                <dl class="facts">
                  <div class="facts__row">
                    <dt>变体</dt>
                    <dd>{{ currentLayout.layout.variantCode }}</dd>
                  </div>
                  <div class="facts__row">
                    <dt>容量单位</dt>
                    <dd>{{ currentLayout.layout.overflow.consumedUnits }}</dd>
                  </div>
                  <div class="facts__row">
                    <dt>DOCX</dt>
                    <dd>
                      <AppTag :tone="docxAvailable ? 'green' : 'orange'">
                        {{ docxAvailable ? '六项门禁已通过' : '暂未开放' }}
                      </AppTag>
                    </dd>
                  </div>
                </dl>
                <p v-if="!docxAvailable" class="fine">{{ docxUnavailableReason }}</p>
                <div v-if="currentLayout.layout.overflow.items.length" class="layout-overflow">
                  <p
                    v-for="item in currentLayout.layout.overflow.items.slice(0, 3)"
                    :key="`${item.page}-${item.slotKey}`"
                  >
                    <AppIcon name="alert-circle" :size="14" />
                    第 {{ item.page }} 页 · {{ item.slotKey }} · 超出 {{ item.excessUnits }}
                  </p>
                </div>
              </div>
            </template>
            <AppEmpty
              v-else
              text="尚未选择结构化模板"
              hint="旧简历仍可按原链路冻结；选择模板后会增加页面容量检查。"
              icon="book"
            >
              <RouterLink class="btn btn--ghost" :to="{ name: 'resume-templates', query: { masterId: master.id } }">
                浏览模板
              </RouterLink>
            </AppEmpty>
            <template v-if="currentLayout?.selected && currentLayout.layout?.templateId" #footer>
              <RouterLink
                class="btn btn--ghost"
                :to="{
                  name: 'resume-template-detail',
                  params: { templateId: currentLayout.layout.templateId },
                  query: { masterId: master.id },
                }"
              >
                <AppIcon name="refresh" :size="14" />
                更换模板或变体
              </RouterLink>
            </template>
          </AppCard>

          <AppCard title="简历状态" sub="草稿 → 待确认 → 可导出 → 已归档。">
            <dl class="facts">
              <div class="facts__row">
                <dt>状态</dt>
                <dd><AppTag :tone="statusTone">{{ glossMasterStatus(master.status) }}</AppTag></dd>
              </div>
              <div class="facts__row">
                <dt>来源</dt>
                <dd>{{ master.source || '—' }}</dd>
              </div>
              <div class="facts__row">
                <dt>版本号</dt>
                <dd>v{{ master.version }}</dd>
              </div>
              <div class="facts__row">
                <dt>更新时间</dt>
                <dd>{{ formatWhen(master.updatedAt) }}</dd>
              </div>
            </dl>
            <AppBanner v-if="archived" tone="warn">主档已归档。正式字段只读，可恢复为归档前状态。</AppBanner>
            <template v-else>
              <p v-if="!canMarkReady" class="fine">{{ readyBlockReason }}</p>
              <div class="rail__ops">
                <AppButton variant="ink" :disabled="!canMarkReady" :pending="pending === 'ready'" @click="onReady">
                  {{ pending === 'ready' ? '正在标记…' : '标为可导出' }}
                </AppButton>
                <AppButton @click="freezeOpen = true">
                  <AppIcon name="lock" :size="14" />
                  冻结当前版本
                </AppButton>
              </div>
              <p class="fine">冻结弹窗会先做证据检查；未确认 AI 事实或未处理成果会拦截。</p>
            </template>
            <template #footer>
              <button v-if="!archived" class="text-link link-danger" type="button" @click="openArchiveMaster">
                归档主档
              </button>
              <AppButton v-else :pending="pending === 'restore-master'" @click="onRestoreMaster">
                {{ pending === 'restore-master' ? '正在恢复…' : '恢复主档' }}
              </AppButton>
            </template>
          </AppCard>

          <AppCard title="证据覆盖" sub="关键成果的证据关联情况。">
            <div class="cover">
              <span class="cover__num">{{ outcomeStats.pct }}%</span>
              <div class="progress">
                <div class="progress__bar" :style="{ width: `${outcomeStats.pct}%` }" />
              </div>
              <p class="fine">
                已关联 {{ outcomeStats.linked }} · 已确认暂无证据 {{ outcomeStats.waived }} · 待处理
                {{ outcomeStats.open }} / 共 {{ outcomeStats.total }} 条
              </p>
            </div>
          </AppCard>

          <AppCard id="export" title="冻结版本" sub="快照不可原地修改；可用于 PDF/DOCX 导出与版本对比。">
            <template #actions>
              <button class="text-link" type="button" :disabled="versions.length < 2" @click="compareOpen = true">
                版本对比
              </button>
            </template>
            <div v-if="versions.length" class="ver-list">
              <div
                v-for="item in versions"
                :key="item.id"
                class="ver"
                :class="{ 'is-focus': item.id === focusedVersionId }"
              >
                <span class="ver__icon" :class="`ver__icon--${versionTone(item)}`">
                  <AppIcon :name="versionIcon(item)" :size="15" />
                </span>
                <div class="ver__body">
                  <div class="ver__line">
                    <strong>v{{ item.version }}</strong>
                    <AppTag :tone="versionTone(item)">{{ glossVersionStatus(item.status) }}</AppTag>
                  </div>
                  <p class="fine">
                    {{ item.immutable ? '不可变' : '待确认' }} · {{ formatWhen(item.frozenAt || item.createdAt) }}
                  </p>
                  <p v-if="item.layoutInstanceId" class="fine">包含不可变版式快照</p>
                  <p v-if="item.status === 'GENERATING' || item.status === 'PENDING_USER_CONFIRMATION'" class="fine">
                    此状态不能导出文件。
                  </p>
                  <div class="ver__ops">
                    <AppButton
                      v-if="item.status === 'PENDING_USER_CONFIRMATION'"
                      :pending="pending === `${item.id}-confirm`"
                      @click="onConfirmVersion(item)"
                    >
                      确认定制并冻结
                    </AppButton>
                    <AppButton
                      variant="ghost"
                      :disabled="!isPdfExportable(item.status)"
                      :pending="pending === `${item.id}-pdf`"
                      @click="onExport(item)"
                    >
                      <AppIcon name="download" :size="13" />
                      导出 PDF
                    </AppButton>
                    <AppButton
                      variant="ghost"
                      :disabled="!isPdfExportable(item.status) || !item.layoutInstanceId || !docxAvailable"
                      :pending="pending === `${item.id}-docx`"
                      :title="docxAvailable ? '导出可编辑 DOCX' : docxUnavailableReason"
                      @click="onExportDocx(item)"
                    >
                      <AppIcon name="download" :size="13" />
                      导出 DOCX
                    </AppButton>
                    <button
                      v-if="item.status !== 'ARCHIVED'"
                      class="text-link link-danger"
                      type="button"
                      @click="openArchiveVersion(item)"
                    >
                      归档
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <AppEmpty v-else text="尚无冻结版本" hint="主档可导出后才能冻结第一份版本。" icon="file-text" />
            <template v-if="exportTask || docxExportTask" #footer>
              <div class="export-results">
              <div v-if="exportTask" class="export-box">
                <p class="fine">
                  最近导出任务 {{ exportTask.taskType }} · {{ exportTask.status }}
                  <template v-if="exportTask.failureReason"> · {{ exportTask.failureReason }}</template>
                </p>
                <AppButton
                  variant="ghost"
                  :disabled="Boolean(pdfDownloadBlockReason)"
                  :pending="pending === 'pdf-download'"
                  @click="onDownloadPdf"
                >
                  <AppIcon name="download" :size="13" />
                  下载 PDF
                </AppButton>
                <p v-if="pdfDownloadBlockReason" class="fine">{{ pdfDownloadBlockReason }}</p>
              </div>
              <div v-if="docxExportTask" class="export-box">
                <p class="fine">
                  最近 DOCX 任务 {{ docxExportTask.status }}
                  <template v-if="docxExportTask.failureReason"> · {{ docxExportTask.failureReason }}</template>
                </p>
                <AppButton
                  variant="ghost"
                  :disabled="Boolean(docxDownloadBlockReason)"
                  :pending="pending === 'docx-download'"
                  @click="onDownloadDocx"
                >
                  <AppIcon name="download" :size="13" />
                  下载 DOCX
                </AppButton>
                <p v-if="docxDownloadBlockReason" class="fine">{{ docxDownloadBlockReason }}</p>
              </div>
              </div>
            </template>
          </AppCard>
        </aside>
      </div>
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
      :title="draft.title"
      :version-no="master?.version ?? 0"
      :outcomes="draft.outcomes"
      :ready-to-apply="master?.status === 'READY_TO_EXPORT'"
      :pending-ai="false"
      :template-name="currentLayout?.layout?.templateName ?? ''"
      :variant-code="currentLayout?.layout?.variantCode ?? ''"
      :layout-status="currentLayout?.layout?.status ?? ''"
      :consumed-units="currentLayout?.layout?.overflow.consumedUnits ?? 0"
      :can-freeze="canFreeze"
      :freeze-block-reason="freezeBlockReason"
      :freeze-pending="pending === 'freeze'"
      :waive-pending="pending.startsWith('waive-') ? pending.slice(6) : ''"
      :evidence-error="evidenceError"
      @close="freezeOpen = false"
      @freeze="onFreeze"
      @waive="onWaive"
    />
    <ResumeCompareModal
      :open="compareOpen"
      :versions="versions"
      @close="compareOpen = false"
      @forbidden="forbidden = $event"
    />
    <AppModal :open="Boolean(archiveTarget)" title="确认归档" :width="440" @close="archiveTarget = null">
      <p class="muted">
        {{
          archiveTarget?.kind === 'master'
            ? '主档归档后可随时恢复，不是物理删除。彻底删除走数据权利。'
            : '归档版本不是物理删除。'
        }}
      </p>
      <template #footer>
        <AppButton variant="ghost" :disabled="Boolean(pending)" @click="archiveTarget = null">取消</AppButton>
        <AppButton variant="danger" :pending="Boolean(pending)" @click="onConfirmArchive">确认归档</AppButton>
      </template>
    </AppModal>
</template>

<style scoped>
.page-head__left {
  display: grid;
  gap: 6px;
  justify-items: start;
}

.page-head__titleline {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.bones {
  padding: 20px;
  display: grid;
  gap: 10px;
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
}

.ai-workbench-entry {
  min-height: 68px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--border-subtle);
  border-left: 3px solid var(--color-primary);
  background: var(--surface-2);
}

.ai-workbench-entry > div, .ai-workbench-entry span { display: flex; align-items: center; gap: 9px; }
.ai-workbench-entry span { align-items: flex-start; flex-direction: column; gap: 2px; }
.ai-workbench-entry small { color: var(--text-tertiary); }

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

.form-stack {
  display: grid;
  gap: 14px;
}

.outcome-list {
  display: grid;
  gap: 12px;
}

.outcome {
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  padding: 12px 14px;
  display: grid;
  gap: 10px;
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

.outcome__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.outcome__ops {
  display: flex;
  align-items: center;
  gap: 8px;
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
  margin: 0;
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
}

.layout-current {
  display: grid;
  gap: 12px;
}

.layout-current__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.layout-current__head strong {
  display: block;
  font-size: 14px;
}

.layout-overflow {
  display: grid;
  gap: 6px;
}

.layout-overflow p {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--color-danger);
  font-size: 12px;
}

.cover {
  display: grid;
  gap: 8px;
}

.cover__num {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-primary);
}

.ver-list {
  display: grid;
  gap: 12px;
}

.ver {
  display: flex;
  gap: 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
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

.ver__icon--blue {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.ver__icon--green {
  background: var(--success-soft);
  color: var(--color-success);
}

.ver__icon--orange {
  background: var(--warning-soft);
  color: var(--color-warning);
}

.ver__icon--gray {
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
  width: 100%;
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
