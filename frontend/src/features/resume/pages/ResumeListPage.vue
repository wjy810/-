<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { errorMessage, isForbidden, isVersionConflict } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import type { JobProofIconName } from '@/shared/ui/jobProofIcons'
import {
  fetchDeletion,
  fetchDeletionPreview,
  submitDeletion,
} from '@/features/datarights/services/dataRightsApi'
import type { DeletionPreview, DeletionView } from '@/features/datarights/types'
import { glossMasterStatus, glossVersionStatus, outcomeResolved, versionOrdinals } from '../labels'
import {
  archiveResume,
  copyResume,
  fetchResume,
  listResumes,
  restoreResume,
  updateResume,
} from '../services/resumeApi'
import type { KeyOutcome, ResumeMasterSummary, ResumeMasterView, ResumeVersionView } from '../types'
import { RESUME_TITLE_MAX, normalizeResumeTitle, resumeTitleIssue, titleMatches } from '../utils/resumeTitle'

type RowType = '主简历' | '定制版' | '冻结版本'
type RowTone = 'blue' | 'green' | 'orange'
type StatusTone = 'green' | 'orange' | 'red' | 'gray' | 'blue'

type ResumeRow = {
  key: string
  kind: 'MASTER' | 'VERSION'
  masterId: string
  versionId: string
  title: string
  /** 1, 2, 3… by creation order within its resume; masters have none. */
  ordinal: number | null
  rowType: RowType
  rowTone: RowTone
  rowIcon: JobProofIconName
  statusLabel: string
  statusTone: StatusTone
  statusSub: string
  coverage: number | null
  updatedAt?: string | null
  archived: boolean
  lockNote: boolean
  pendingConfirm: boolean
  expectedVersion: number
}

type ResumeRows = { rows: ResumeRow[]; detailGap: number }

const router = useRouter()
const pending = ref('')
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'resume-list-page-error')
useToastFeedback(notice, 'success', 'resume-list-notice')
const actionForbidden = ref<unknown>(null)
const tab = ref('ALL')
const query = ref('')
const page = ref(0)
const pageSize = ref(10)
const archiveTarget = ref<ResumeRow | null>(null)
const renameTarget = ref<ResumeRow | null>(null)
const renameValue = ref('')
const renameError = ref('')
const renaming = ref(false)
const deleteTarget = ref<ResumeRow | null>(null)
const deletePreview = ref<DeletionPreview | null>(null)
const deleteRequest = ref<DeletionView | null>(null)
const deleteConfirmation = ref('')
const deleteLoading = ref(false)
const deleteError = ref('')
let deletePollGeneration = 0

const list = useLoadState((signal) => loadRows(signal))
const rows = computed(() => list.data.value?.rows ?? [])
const detailGap = computed(() => list.data.value?.detailGap ?? 0)
const forbidden = computed(() => actionForbidden.value ?? (isForbidden(list.error.value) ? list.error.value : null))
const load = list.load

const TABS = [
  { id: 'ALL', label: '全部' },
  { id: 'MASTER', label: '主简历' },
  { id: 'TAILORED', label: '定制简历' },
  { id: 'SNAPSHOT', label: '冻结版本' },
  { id: 'ARCHIVED', label: '已归档' },
] as const
const activeTabIndex = computed(() => Math.max(0, TABS.findIndex((item) => item.id === tab.value)))

const stats = computed(() => ({
  masters: rows.value.filter((row) => row.kind === 'MASTER' && !row.archived).length,
  tailored: rows.value.filter((row) => row.rowType === '定制版' && !row.archived).length,
  snapshots: rows.value.filter((row) => row.rowType === '冻结版本' && !row.archived).length,
}))

const filtered = computed(() => {
  let items = rows.value
  if (tab.value === 'ARCHIVED') {
    items = items.filter((row) => row.archived)
  } else {
    items = items.filter((row) => !row.archived)
    if (tab.value === 'MASTER') {
      items = items.filter((row) => row.kind === 'MASTER')
    } else if (tab.value === 'TAILORED') {
      items = items.filter((row) => row.rowType === '定制版')
    } else if (tab.value === 'SNAPSHOT') {
      items = items.filter((row) => row.rowType === '冻结版本')
    }
  }
  items = items.filter((row) => titleMatches(row.title, query.value))
  return [...items].sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
})

const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const pageNums = computed(() => {
  const total = pageCount.value
  const start = Math.max(0, Math.min(page.value - 2, total - 5))
  const end = Math.min(total, start + 5)
  return Array.from({ length: end - start }, (_, i) => start + i)
})
const paged = computed(() => filtered.value.slice(page.value * pageSize.value, (page.value + 1) * pageSize.value))
const searching = computed(() => Boolean(normalizeResumeTitle(query.value)))
const emptyState = computed(() => {
  if (searching.value) {
    return { title: '没有找到匹配的简历', description: `没有名称包含“${normalizeResumeTitle(query.value)}”的简历。` }
  }
  if (tab.value === 'ARCHIVED') {
    return { title: '没有已归档的简历', description: '归档的简历和版本会出现在这里，可以随时恢复。' }
  }
  if (tab.value === 'ALL') {
    return { title: '还没有简历', description: '新建一份简历，或粘贴已有简历导入。' }
  }
  return { title: '此分类下还没有简历', description: '' }
})
const renameCount = computed(() => `${Array.from(normalizeResumeTitle(renameValue.value)).length}/${RESUME_TITLE_MAX}`)

const deleteTargetType = computed(() =>
  deleteTarget.value?.kind === 'MASTER' ? 'RESUME_MASTER' : 'RESUME_VERSION',
)
const deleteCanSubmit = computed(
  () =>
    deleteConfirmation.value.trim() === '永久删除' &&
    deletePreview.value?.canProceed === true &&
    !deleteLoading.value,
)

function coverageOf(outcomes?: KeyOutcome[] | null): number | null {
  const items = (outcomes ?? []).filter((item) => (item.text ?? '').trim())
  if (!items.length) {
    return null
  }
  const done = items.filter((item) => outcomeResolved(item)).length
  return Math.round((done / items.length) * 100)
}

function coverageSub(value: number): string {
  if (value >= 80) {
    return '覆盖充分'
  }
  if (value >= 60) {
    return '覆盖良好'
  }
  return '待补充'
}

function snapshotOutcomes(version: ResumeVersionView): KeyOutcome[] {
  const raw = version.snapshot?.keyOutcomes
  return Array.isArray(raw) ? (raw as KeyOutcome[]) : []
}

function versionTitle(item: ResumeMasterSummary, version: ResumeVersionView): string {
  const raw = version.snapshot?.title
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim()
  }
  return `${item.title?.trim() || '未命名简历'} · 快照`
}

function masterRow(item: ResumeMasterSummary, detail: ResumeMasterView | null): ResumeRow {
  const archived = item.status === 'ARCHIVED'
  const pendingConfirm = item.status === 'PENDING_CONFIRMATION'
  return {
    key: `m-${item.id}`,
    kind: 'MASTER',
    masterId: item.id,
    versionId: '',
    title: item.title?.trim() || '未命名简历',
    ordinal: null,
    rowType: '主简历',
    rowTone: 'blue',
    rowIcon: 'resume-master-resume',
    statusLabel: glossMasterStatus(item.status),
    statusTone: archived ? 'gray' : pendingConfirm ? 'orange' : 'green',
    statusSub: archived ? '恢复后可编辑' : pendingConfirm ? '有待确认的内容' : '可编辑',
    coverage: coverageOf(detail?.keyOutcomes),
    updatedAt: item.updatedAt,
    archived,
    lockNote: false,
    pendingConfirm,
    expectedVersion: item.version,
  }
}

function versionRow(item: ResumeMasterSummary, version: ResumeVersionView, ordinal: number | null): ResumeRow {
  const archived = version.status === 'ARCHIVED'
  const isSnapshot =
    version.status === 'FROZEN' || (archived && version.immutable)
  const pendingConfirm = version.status === 'PENDING_USER_CONFIRMATION'
  return {
    key: `v-${version.id}`,
    kind: 'VERSION',
    masterId: item.id,
    versionId: version.id,
    title: versionTitle(item, version),
    ordinal,
    rowType: isSnapshot ? '冻结版本' : '定制版',
    rowTone: isSnapshot ? 'orange' : 'green',
    rowIcon: isSnapshot ? 'resume-frozen-version' : 'resume-tailored-resume',
    statusLabel: glossVersionStatus(version.status),
    statusTone: archived
      ? 'gray'
      : version.status === 'FROZEN'
          ? 'red'
          : pendingConfirm
            ? 'orange'
            : 'gray',
    statusSub: version.immutable || isSnapshot ? '不可修改' : '确认后冻结',
    coverage: coverageOf(snapshotOutcomes(version)),
    updatedAt: version.frozenAt || version.createdAt,
    archived,
    lockNote: version.immutable,
    pendingConfirm,
    expectedVersion: version.version,
  }
}

async function loadRows(signal: AbortSignal): Promise<ResumeRows> {
  const masters = await listResumes(signal)
  // 列表接口只回主档摘要；类型统计与覆盖度需要每个主档的版本，小列表内并发拉取
  const settled = await Promise.allSettled(masters.map((item) => fetchResume(item.id, signal)))
  const next: ResumeRow[] = []
  let gap = 0
  settled.forEach((result, index) => {
    const master = masters[index]
    if (!master) {
      return
    }
    if (result.status === 'fulfilled') {
      next.push(masterRow(master, result.value))
      const versions = result.value.versions ?? []
      const ordinals = versionOrdinals(versions)
      for (const version of versions) {
        next.push(versionRow(master, version, ordinals.get(version.id) ?? null))
      }
    } else {
      gap += 1
      next.push(masterRow(master, null))
    }
  })
  return { rows: next, detailGap: gap }
}

function openCreate(): void {
  void router.push('/ai-resume/new')
}

function rowLink(row: ResumeRow): string {
  if (row.kind === 'VERSION') {
    return `/resumes/${row.masterId}?versionId=${row.versionId}`
  }
  return row.archived ? `/resumes/${row.masterId}/manual` : `/resumes/${row.masterId}`
}

function rowAction(row: ResumeRow): string {
  if (row.kind === 'MASTER') {
    return row.archived ? '打开' : '编辑'
  }
  return row.pendingConfirm ? '去确认' : '查看版本'
}

function openRename(row: ResumeRow): void {
  renameTarget.value = row
  renameValue.value = row.title
  renameError.value = ''
}

function closeRename(): void {
  if (renaming.value) {
    return
  }
  renameTarget.value = null
}

async function submitRename(): Promise<void> {
  const target = renameTarget.value
  if (!target || renaming.value) {
    return
  }
  const issue = resumeTitleIssue(renameValue.value)
  if (issue) {
    renameError.value = issue
    return
  }
  const title = normalizeResumeTitle(renameValue.value)
  if (title === target.title) {
    renameTarget.value = null
    return
  }
  renaming.value = true
  renameError.value = ''
  try {
    await updateResume(target.masterId, { title, expectedVersion: target.expectedVersion })
    renameTarget.value = null
    notice.value = `已重命名为「${title}」。`
    void load()
  } catch (error) {
    if (isForbidden(error)) {
      actionForbidden.value = error
    } else if (isVersionConflict(error)) {
      renameError.value = '这份简历刚被修改过，列表已刷新，请再保存一次。'
      const refreshed = await load()
      const latest = refreshed?.rows.find((row) => row.key === target.key)
      if (latest) {
        renameTarget.value = latest
      }
    } else {
      renameError.value = errorMessage(error, '重命名失败，请稍后重试。')
    }
  } finally {
    renaming.value = false
  }
}

async function onCopy(row: ResumeRow): Promise<void> {
  pending.value = `${row.key}-copy`
  pageError.value = ''
  try {
    const copy = await copyResume(row.masterId)
    notice.value = `已复制为新简历「${copy.title}」。已冻结的版本不会一起复制。`
    await router.push(`/resumes/${copy.id}`)
  } catch (error) {
    handleError(error)
  } finally {
    pending.value = ''
  }
}

async function onArchiveConfirm(): Promise<void> {
  const target = archiveTarget.value
  if (!target) {
    return
  }
  pending.value = `${target.key}-archive`
  pageError.value = ''
  try {
    await archiveResume(target.masterId, target.expectedVersion)
    notice.value = `「${target.title}」已归档，可以在「已归档」中恢复。`
    archiveTarget.value = null
    await load()
  } catch (error) {
    handleError(error)
  } finally {
    pending.value = ''
  }
}

async function onRestore(row: ResumeRow): Promise<void> {
  pending.value = `${row.key}-restore`
  pageError.value = ''
  try {
    const restored = await restoreResume(row.masterId, row.expectedVersion)
    notice.value = `「${restored.title}」已恢复为${glossMasterStatus(restored.status)}。`
    await load()
  } catch (error) {
    handleError(error)
  } finally {
    pending.value = ''
  }
}

async function openPermanentDelete(row: ResumeRow): Promise<void> {
  deletePollGeneration += 1
  deleteTarget.value = row
  deletePreview.value = null
  deleteRequest.value = null
  deleteConfirmation.value = ''
  deleteError.value = ''
  deleteLoading.value = true
  try {
    deletePreview.value = await fetchDeletionPreview({
      scope: 'OBJECT',
      targetType: row.kind === 'MASTER' ? 'RESUME_MASTER' : 'RESUME_VERSION',
      targetId: row.kind === 'MASTER' ? row.masterId : row.versionId,
    })
  } catch (error) {
    deleteError.value = errorMessage(error, '无法读取永久删除影响范围')
  } finally {
    deleteLoading.value = false
  }
}

function closePermanentDelete(): void {
  if (deleteLoading.value || (deleteRequest.value && !deletionFinished(deleteRequest.value))) {
    return
  }
  deletePollGeneration += 1
  deleteTarget.value = null
  deletePreview.value = null
  deleteRequest.value = null
  deleteConfirmation.value = ''
  deleteError.value = ''
}

function deletionFinished(view: DeletionView): boolean {
  return view.status === 'COMPLETED' || view.status === 'FAILED'
}

async function pollDeletion(requestId: string, generation: number): Promise<DeletionView> {
  let current = deleteRequest.value
  for (let attempt = 0; attempt < 120; attempt += 1) {
    if (generation !== deletePollGeneration) {
      throw new Error('删除状态检查已取消')
    }
    if (current && deletionFinished(current)) {
      return current
    }
    await new Promise((resolve) => window.setTimeout(resolve, 500))
    current = await fetchDeletion(requestId)
    deleteRequest.value = current
  }
  throw new Error('删除仍在后台处理中，请稍后刷新列表确认结果')
}

async function onPermanentDeleteConfirm(): Promise<void> {
  const target = deleteTarget.value
  if (!target || !deleteCanSubmit.value) {
    return
  }
  deleteLoading.value = true
  deleteError.value = ''
  pageError.value = ''
  const generation = ++deletePollGeneration
  try {
    const submitted = await submitDeletion({
      scope: 'OBJECT',
      targetType: deleteTargetType.value,
      targetId: target.kind === 'MASTER' ? target.masterId : target.versionId,
      confirmationAck: true,
    })
    deleteRequest.value = submitted
    const completed = await pollDeletion(submitted.id, generation)
    if (completed.status !== 'COMPLETED') {
      const receipt = completed.receipts?.find((item) => item.status === 'FAILED')
      throw new Error(receipt?.message || '永久删除失败，数据未从列表移除')
    }
    const title = target.title
    deleteTarget.value = null
    deletePreview.value = null
    deleteRequest.value = null
    deleteConfirmation.value = ''
    notice.value = `「${title}」及其关联数据已永久删除，无法恢复。`
    await load()
  } catch (error) {
    deleteError.value = errorMessage(error, '永久删除失败，数据未从列表移除')
  } finally {
    deleteLoading.value = false
  }
}

function handleError(error: unknown): void {
  if (isForbidden(error)) {
    actionForbidden.value = error
    return
  }
  if (isVersionConflict(error)) {
    pageError.value = '这份简历刚被修改过，列表已刷新，请重新操作。'
    void load()
    return
  }
  pageError.value = errorMessage(error, '简历操作失败')
}

watch([tab, pageSize, query], () => {
  page.value = 0
})

onMounted(() => {
  void load()
})

onBeforeUnmount(() => {
  deletePollGeneration += 1
})
</script>

<template>
  <ForbidState v-if="forbidden" :error="forbidden" />
  <section v-else class="page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">简历工作台</h1>
        <p class="page-head__sub">统一管理简历和冻结版本，随时预览与导出</p>
      </div>
      <div class="page-head__actions">
        <RouterLink class="btn btn--ghost" to="/resume-templates">
          <AppIcon name="book" :size="15" />
          模板中心
        </RouterLink>
        <AppButton variant="ghost" :pending="list.loading.value && list.loaded.value" @click="load">
          <AppIcon name="refresh" :size="15" />
          刷新
        </AppButton>
        <AppButton @click="openCreate">
          <AppIcon name="plus" :size="15" />
          新建简历
        </AppButton>
      </div>
    </header>

    <PageState
      :loading="list.loading.value"
      :error="list.error.value"
      :loaded="list.loaded.value"
      error-title="简历列表读取失败"
      @retry="load"
    >
      <template #skeleton>
        <div class="list-body" aria-busy="true">
          <div class="grid-stats">
            <UiSkeleton height="96px" radius="var(--radius-lg)" />
            <UiSkeleton height="96px" radius="var(--radius-lg)" />
            <UiSkeleton height="96px" radius="var(--radius-lg)" />
          </div>
          <div class="card bones">
            <UiSkeleton height="18px" width="48%" />
            <UiSkeleton :lines="4" />
          </div>
        </div>
      </template>

      <div class="list-body">
        <AppBanner v-if="detailGap" tone="warn">
          有 {{ detailGap }} 份简历的详情没有读取成功，它们的版本和证据覆盖度暂未显示。可以稍后点“刷新”重试。
        </AppBanner>

        <div class="grid-stats">
          <div class="stat">
            <span class="stat__icon"><JobProofIcon name="resume-master-resume" :size="18" /></span>
            <span class="stat__label">主简历</span>
            <span class="stat__value">{{ stats.masters }}</span>
            <span class="stat__sub">草稿 / 待确认 / 可导出</span>
          </div>
          <div class="stat">
            <span class="stat__icon stat__icon--green"><JobProofIcon name="resume-tailored-resume" :size="18" /></span>
            <span class="stat__label">岗位定制版</span>
            <span class="stat__value">{{ stats.tailored }}</span>
            <span class="stat__sub">按岗位生成、待确认的版本</span>
          </div>
          <div class="stat">
            <span class="stat__icon stat__icon--orange"><JobProofIcon name="resume-frozen-version" :size="18" /></span>
            <span class="stat__label">冻结版本</span>
            <span class="stat__value">{{ stats.snapshots }}</span>
            <span class="stat__sub">已冻结，内容不可修改</span>
          </div>
        </div>

        <div class="card">
          <div class="tabs" role="tablist" aria-label="简历类型筛选" :style="{ '--tab-index': activeTabIndex }">
            <button
              v-for="item in TABS"
              :key="item.id"
              type="button"
              role="tab"
              class="tabs__item"
              :class="{ 'is-on': tab === item.id }"
              :aria-selected="tab === item.id"
              @click="tab = item.id"
            >
              {{ item.label }}
            </button>
            <span class="tabs__indicator" aria-hidden="true" />
          </div>

          <div class="list-toolbar">
            <div class="list-toolbar__search">
              <UiInput
                v-model="query"
                size="sm"
                :icon="Search"
                clearable
                type="search"
                placeholder="搜索简历名称"
                aria-label="搜索简历名称"
                data-testid="resume-search"
              />
            </div>
            <span v-if="searching" class="muted">找到 {{ filtered.length }} 条</span>
          </div>

          <Transition name="resume-results" mode="out-in">
            <div :key="`${tab}-${page}-${pageSize}`" class="resume-results">
              <template v-if="paged.length">
                <div class="tbl-wrap">
                  <table class="tbl">
                    <thead>
                      <tr>
                        <th>简历名称</th>
                        <th>状态</th>
                        <th>证据覆盖度</th>
                        <th>更新时间</th>
                        <th class="ops-col">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="row in paged" :key="row.key" data-testid="resume-row">
                        <td data-label="简历名称">
                          <div class="rname">
                            <span class="rtype" :class="`rtype--${row.rowTone}`">
                              <JobProofIcon :name="row.rowIcon" :size="16" />
                            </span>
                            <div class="rname__body">
                              <div class="rname__line">
                                <span class="rname__title" :title="row.title">{{ row.title }}</span>
                                <AppTag :tone="row.rowTone">{{ row.rowType }}<template v-if="row.ordinal"> · 第 {{ row.ordinal }} 版</template></AppTag>
                              </div>
                              <p v-if="row.lockNote" class="rname__lock">
                                <AppIcon name="lock" :size="12" />
                                内容不可修改
                              </p>
                            </div>
                          </div>
                        </td>
                        <td data-label="状态">
                          <div class="rcell">
                            <span class="rcell__line">
                              <span class="dot" :class="`dot--${row.statusTone}`" />
                              {{ row.statusLabel }}
                            </span>
                            <span class="fine">{{ row.statusSub }}</span>
                          </div>
                        </td>
                        <td data-label="证据覆盖度">
                          <div v-if="row.coverage !== null" class="rcell">
                            <span class="rcell__line">
                              <span class="dot" :class="row.coverage >= 60 ? 'dot--green' : 'dot--orange'" />
                              {{ row.coverage }}%
                            </span>
                            <span class="fine">{{ coverageSub(row.coverage) }}</span>
                          </div>
                          <span v-else class="muted">—</span>
                        </td>
                        <td class="muted" data-label="更新时间">{{ formatWhen(row.updatedAt) }}</td>
                        <td data-label="操作">
                          <div class="ops">
                            <RouterLink class="text-link" :to="rowLink(row)">{{ rowAction(row) }}</RouterLink>
                            <template v-if="row.kind === 'MASTER'">
                              <button
                                v-if="!row.archived"
                                class="text-link"
                                type="button"
                                :disabled="Boolean(pending)"
                                data-testid="rename-resume"
                                @click="openRename(row)"
                              >
                                重命名
                              </button>
                              <button class="text-link" type="button" :disabled="Boolean(pending)" @click="onCopy(row)">
                                复制
                              </button>
                              <button
                                v-if="!row.archived"
                                class="text-link text-link--danger"
                                type="button"
                                :disabled="Boolean(pending)"
                                @click="archiveTarget = row"
                              >
                                归档
                              </button>
                              <button v-else class="text-link" type="button" :disabled="Boolean(pending)" @click="onRestore(row)">
                                恢复
                              </button>
                              <button
                                class="text-link text-link--danger"
                                type="button"
                                :disabled="Boolean(pending)"
                                @click="openPermanentDelete(row)"
                              >
                                永久删除
                              </button>
                            </template>
                            <button
                              v-else-if="row.rowType === '冻结版本'"
                              class="text-link text-link--danger"
                              type="button"
                              :disabled="Boolean(pending)"
                              @click="openPermanentDelete(row)"
                            >
                              永久删除
                            </button>
                          </div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <footer class="tbl-foot">
                  <span class="muted">共 {{ filtered.length }} 条</span>
                  <div class="pager">
                    <AppSelect :model-value="pageSize" class="pager__size" ariaLabel="每页条数" @change="pageSize = Number($event)">
                      <option :value="10">10 条 / 页</option>
                      <option :value="20">20 条 / 页</option>
                      <option :value="50">50 条 / 页</option>
                    </AppSelect>
                    <button class="pager__page" type="button" :disabled="page <= 0" aria-label="上一页" @click="page -= 1">
                      <AppIcon name="chevron-left" :size="14" />
                    </button>
                    <button
                      v-for="num in pageNums"
                      :key="num"
                      class="pager__page"
                      :class="{ 'is-active': page === num }"
                      type="button"
                      @click="page = num"
                    >
                      {{ num + 1 }}
                    </button>
                    <button
                      class="pager__page"
                      type="button"
                      :disabled="page >= pageCount - 1"
                      aria-label="下一页"
                      @click="page += 1"
                    >
                      <AppIcon name="chevron-right" :size="14" />
                    </button>
                  </div>
                </footer>
              </template>

              <UiEmptyState
                v-else
                size="sm"
                :illustration="searching ? 'empty-search' : 'empty-resume'"
                :title="emptyState.title"
                :description="emptyState.description"
                data-testid="resume-empty"
              >
                <UiButton v-if="searching" variant="secondary" size="sm" @click="query = ''">清除搜索</UiButton>
                <UiButton v-else-if="tab === 'ALL'" size="sm" @click="openCreate">新建简历</UiButton>
              </UiEmptyState>
            </div>
          </Transition>
        </div>
      </div>
    </PageState>
  </section>

  <UiDialog :open="Boolean(renameTarget)" title="重命名简历" width="sm" :dismissible="!renaming" @close="closeRename">
    <UiField v-slot="{ id, describedBy, invalid }" label="简历名称" :error="renameError" :counter="renameCount">
      <UiInput
        :id="id"
        v-model="renameValue"
        :invalid="invalid"
        :aria-describedby="describedBy"
        :disabled="renaming"
        autocomplete="off"
        data-testid="rename-input"
        @keydown.enter.prevent="submitRename"
      />
    </UiField>
    <template #footer>
      <UiButton variant="secondary" :disabled="renaming" @click="closeRename">取消</UiButton>
      <UiButton :pending="renaming" data-testid="rename-save" @click="submitRename">保存</UiButton>
    </template>
  </UiDialog>

  <AppModal :open="Boolean(archiveTarget)" title="归档简历" :width="440" @close="archiveTarget = null">
    <p class="muted">
      「{{ archiveTarget?.title }}」归档后会移到「已归档」，可以随时恢复，不会删除任何内容。
    </p>
    <template #footer>
      <AppButton variant="ghost" :disabled="Boolean(pending)" @click="archiveTarget = null">取消</AppButton>
      <AppButton variant="danger" :pending="Boolean(pending)" @click="onArchiveConfirm">确认归档</AppButton>
    </template>
  </AppModal>

  <AppModal
    :open="Boolean(deleteTarget)"
    :title="deleteTarget?.kind === 'MASTER' ? '永久删除简历' : '永久删除冻结版本'"
    :width="520"
    @close="closePermanentDelete"
  >
    <div class="delete-dialog">
      <AppBanner tone="bad">
        删除后不会进入「已归档」，也无法恢复。简历内容和导出的文件都会被清除。
      </AppBanner>
      <div class="delete-dialog__target">
        <span class="delete-dialog__icon"><AppIcon name="trash" :size="18" /></span>
        <div>
          <strong>{{ deleteTarget?.title }}</strong>
          <span>{{ deleteTarget?.kind === 'MASTER' ? '这份简历及其全部版本和关联数据' : '仅这个冻结版本及其关联数据' }}</span>
        </div>
      </div>
      <p v-if="deleteLoading && !deletePreview" class="muted">正在核对关联数据与运行中任务…</p>
      <template v-else-if="deletePreview">
        <p class="delete-dialog__summary">{{ deletePreview.impactSummary }}</p>
        <ul v-if="deletePreview.impacts?.length" class="delete-impact" aria-label="永久删除影响范围">
          <li v-for="(item, index) in deletePreview.impacts" :key="`${item.kind}-${index}`">
            <AppIcon name="x-circle" :size="14" />
            <span>{{ item.label || '关联数据' }}</span>
          </li>
        </ul>
        <AppBanner v-if="deletePreview.canProceed === false" tone="warn">
          当前不能删除：该简历仍有任务运行中。请等待导出、定制或匹配任务结束后重试。
        </AppBanner>
      </template>
      <AppBanner v-if="deleteError" tone="bad">{{ deleteError }}</AppBanner>
      <AppBanner v-if="deleteRequest && !deletionFinished(deleteRequest)" tone="warn">
        删除申请已提交，正在清理关联数据。完成前不会从列表移除，请勿关闭此窗口。
      </AppBanner>
      <AppField id="permanent-delete-confirmation" label="输入“永久删除”确认" hint="必须完整输入四个字，避免误操作。">
        <input
          id="permanent-delete-confirmation"
          v-model="deleteConfirmation"
          class="input"
          type="text"
          autocomplete="off"
          placeholder="永久删除"
          :disabled="deleteLoading || deletePreview?.canProceed !== true"
          @keydown.enter.prevent="onPermanentDeleteConfirm"
        />
      </AppField>
    </div>
    <template #footer>
      <AppButton variant="ghost" :disabled="deleteLoading" @click="closePermanentDelete">取消</AppButton>
      <AppButton
        variant="danger"
        :pending="deleteLoading && Boolean(deleteRequest)"
        :disabled="!deleteCanSubmit"
        @click="onPermanentDeleteConfirm"
      >
        {{ deleteRequest ? '正在永久删除…' : '永久删除' }}
      </AppButton>
    </template>
  </AppModal>
</template>

<style scoped>
.page,
.page > * { min-width: 0; }

.tabs {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 4px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
}

.tabs__item {
  position: relative;
  z-index: 1;
  min-width: 0;
  padding: 12px 8px;
  font-size: 13px;
  color: var(--text-secondary);
  background: none;
  border: none;
  margin-bottom: -1px;
  white-space: nowrap;
  transition:color var(--motion-base) ease,
    background-color var(--motion-fast) ease,
    transform var(--motion-fast) var(--motion-ease);
}

.tabs__item:hover {
  color: var(--color-primary);
  background: var(--surface-2);
}

.tabs__item.is-on {
  color: var(--color-primary);
  font-weight: 600;
}

.tabs__item:active {
  transform: scale(0.98);
}

.tabs__indicator {
  position: absolute;
  z-index: 2;
  bottom: -1px;
  left: 16px;
  width: calc((100% - 48px) / 5);
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--color-primary);
  box-shadow: 0 -2px 7px color-mix(in srgb, var(--color-primary) 18%, transparent);
  pointer-events: none;
  transform: translateX(calc(var(--tab-index) * (100% + 4px)));
  transition: transform var(--motion-slow) var(--motion-ease);
}

.list-body {
  display: grid;
  gap: var(--space-6);
  min-width: 0;
}

.list-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}

.list-toolbar__search {
  width: min(320px, 100%);
}

.resume-results {
  min-height: 132px;
}

.resume-results-enter-active {
  transition: opacity var(--motion-base) ease, transform var(--motion-base) var(--motion-ease);
}

.resume-results-leave-active {
  transition: opacity 100ms ease, transform 100ms ease;
}

.resume-results-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.resume-results-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}

.bones {
  display: grid;
  gap: 12px;
  padding: 20px;
}

.tbl-wrap {
  overflow-x: auto;
}

.rname {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 220px;
}

.rtype {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.rtype--blue {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.rtype--green {
  background: var(--success-soft);
  color: var(--color-success);
}

.rtype--orange {
  background: var(--warning-soft);
  color: var(--color-warning);
}

.rname__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.rname__line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.rname__title {
  font-weight: 500;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rname__lock {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-danger);
}

.rcell {
  display: grid;
  gap: 2px;
  justify-items: start;
}

.rcell__line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot--green {
  background: var(--color-success);
}

.dot--orange {
  background: var(--color-warning);
}

.dot--red {
  background: var(--color-danger);
}

.dot--gray {
  background: var(--text-tertiary);
}

.dot--blue {
  background: var(--color-primary);
}

.bound-text {
  color: var(--color-primary);
  font-size: 13px;
}

.ops {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.ops-col {
  width: 1%;
}

.text-link--danger {
  color: var(--color-danger);
}

.text-link--danger:hover {
  color: var(--color-danger);
}

.text-link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tbl-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  font-size: 13px;
  flex-wrap: wrap;
}

.pager__size {
  width: 132px;
  padding: 0;
}

.pager__size:deep(.app-select__trigger) { height: 34px; }

.delete-dialog {
  display: grid;
  gap: 14px;
}

.delete-dialog__target {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
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
  background: var(--danger-soft);
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
  border: 1px solid var(--border);
  border-radius: var(--radius-s);
  color: var(--text-secondary);
  font-size: 12px;
}

.delete-impact svg {
  flex: 0 0 auto;
  color: var(--color-danger);
}

@media (max-width: 560px) {
  .page {
    min-width: 0;
    padding: 16px 12px 32px;
    gap: 14px;
  }

  .page-head,
  .page-head > div:first-child,
  .page-head__actions,
  .grid-stats,
  .card,
  .tbl-wrap,
  .tbl-foot {
    min-width: 0;
    max-width: 100%;
  }

  .page-head__sub {
    display: block;
    line-height: 1.6;
  }

  .page-head__actions {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .page-head__actions:deep(.btn),
  .page-head__actions > .btn {
    width: 100%;
    min-width: 0;
    padding-inline: 8px;
  }

  .grid-stats {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .stat {
    grid-template-columns: 36px minmax(0, 1fr) auto;
    align-items: center;
    column-gap: 10px;
    padding: 13px 14px;
  }

  .stat__icon {
    grid-row: 1 / span 2;
    margin-bottom: 0;
  }

  .stat__value {
    grid-column: 3;
    grid-row: 1 / span 2;
    font-size: 24px;
  }

  .stat__sub {
    grid-column: 2;
  }

  .tabs {
    padding-inline: 4px;
    scrollbar-width: none;
  }

  .tabs__item {
    padding-inline: 3px;
    font-size: 12px;
  }

  .tabs__indicator {
    left: 4px;
    width: calc((100% - 24px) / 5);
  }

  .tabs::-webkit-scrollbar {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tabs__item,
  .tabs__indicator,
  .resume-results-enter-active,
  .resume-results-leave-active { transition: none; }

  .tabs__item:active,
  .resume-results-enter-from,
  .resume-results-leave-to { transform: none; }
}

@media (max-width: 560px) {
  .tbl-wrap {
    overflow: visible;
    padding: 10px;
  }

  .tbl,
  .tbl tbody {
    width: 100%;
    min-width: 0;
    display: grid;
    gap: 10px;
  }

  .tbl thead {
    display: none;
  }

  .tbl tbody tr {
    min-width: 0;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    padding: 14px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
  }

  .tbl td {
    min-width: 0;
    display: grid;
    align-content: start;
    gap: 4px;
    padding: 0;
    border: 0;
    overflow-wrap: anywhere;
  }

  .tbl td::before {
    content: attr(data-label);
    color: var(--text-tertiary);
    font-size: 12px;
    line-height: 1.4;
  }

  .tbl td:first-child,
  .tbl td:last-child {
    grid-column: 1 / -1;
  }

  .tbl td:first-child::before,
  .tbl td:last-child::before {
    display: none;
  }

  .rname {
    min-width: 0;
  }

  .rname__title {
    max-width: 100%;
    white-space: normal;
    overflow-wrap: anywhere;
  }

  .ops {
    width: 100%;
    flex-wrap: wrap;
    gap: 8px 14px;
    padding-top: 2px;
    white-space: normal;
  }

  .tbl-foot {
    display: grid;
    align-items: start;
    padding: 12px;
  }

  .pager {
    width: 100%;
    min-width: 0;
    justify-content: flex-start;
    flex-wrap: wrap;
    padding-bottom: 2px;
  }

  .delete-impact {
    grid-template-columns: 1fr;
  }
}
</style>
