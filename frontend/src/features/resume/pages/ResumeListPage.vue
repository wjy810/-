<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { errorMessage, isApiClientError, isForbidden } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import type { JobProofIconName } from '@/shared/ui/jobProofIcons'
import {
  fetchDeletion,
  fetchDeletionPreview,
  submitDeletion,
} from '@/features/datarights/services/dataRightsApi'
import type { DeletionPreview, DeletionView } from '@/features/datarights/types'
import { TEMPLATE_GLOSS, glossMasterStatus, glossVersionStatus, outcomeResolved } from '../labels'
import {
  archiveResume,
  copyResume,
  createResume,
  fetchResume,
  listResumes,
  restoreResume,
} from '../services/resumeApi'
import type {
  KeyOutcome,
  ResumeCreateMode,
  ResumeMasterSummary,
  ResumeMasterView,
  ResumeTemplateCode,
  ResumeVersionView,
} from '../types'

type RowType = '主简历' | '定制版' | '冻结版本'
type RowTone = 'blue' | 'green' | 'orange'
type StatusTone = 'green' | 'orange' | 'red' | 'gray' | 'blue'

type ResumeRow = {
  key: string
  kind: 'MASTER' | 'VERSION'
  masterId: string
  versionId: string
  title: string
  versionNo: number
  rowType: RowType
  rowTone: RowTone
  rowIcon: JobProofIconName
  statusLabel: string
  statusTone: StatusTone
  statusSub: string
  targetJob: string
  coverage: number | null
  boundText: string
  updatedAt?: string | null
  archived: boolean
  lockNote: boolean
  pendingConfirm: boolean
  expectedVersion: number
}

const router = useRouter()
const loading = ref(true)
const creating = ref(false)
const pending = ref('')
const pageError = ref('')
const createError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'resume-list-page-error')
useToastFeedback(notice, 'success', 'resume-list-notice')
const forbidden = ref<unknown>(null)
const listFailed = ref(false)
const detailGap = ref(0)
const rows = ref<ResumeRow[]>([])
const tab = ref('ALL')
const page = ref(0)
const pageSize = ref(10)
const createOpen = ref(false)
const archiveTarget = ref<ResumeRow | null>(null)
const deleteTarget = ref<ResumeRow | null>(null)
const deletePreview = ref<DeletionPreview | null>(null)
const deleteRequest = ref<DeletionView | null>(null)
const deleteConfirmation = ref('')
const deleteLoading = ref(false)
const deleteError = ref('')
let deletePollGeneration = 0
const draft = reactive({
  mode: 'BLANK' as ResumeCreateMode,
  title: '',
  templateCode: 'SOFTWARE_DEV' as ResumeTemplateCode,
  importText: '',
})

const TABS = [
  { id: 'ALL', label: '全部' },
  { id: 'MASTER', label: '主简历' },
  { id: 'TAILORED', label: '定制简历' },
  { id: 'SNAPSHOT', label: '冻结版本' },
  { id: 'ARCHIVED', label: '已归档' },
] as const
const activeTabIndex = computed(() => Math.max(0, TABS.findIndex((item) => item.id === tab.value)))

const CREATE_MODES: { id: ResumeCreateMode; label: string }[] = [
  { id: 'BLANK', label: '空白' },
  { id: 'TEMPLATE', label: '模板' },
  { id: 'IMPORT', label: '导入文本' },
]

const stats = computed(() => ({
  masters: rows.value.filter((row) => row.kind === 'MASTER' && !row.archived).length,
  tailored: rows.value.filter((row) => row.rowType === '定制版' && !row.archived).length,
  snapshots: rows.value.filter((row) => row.rowType === '冻结版本' && !row.archived).length,
}))

const filtered = computed(() => {
  let list = rows.value
  if (tab.value === 'ARCHIVED') {
    list = list.filter((row) => row.archived)
  } else {
    list = list.filter((row) => !row.archived)
    if (tab.value === 'MASTER') {
      list = list.filter((row) => row.kind === 'MASTER')
    } else if (tab.value === 'TAILORED') {
      list = list.filter((row) => row.rowType === '定制版')
    } else if (tab.value === 'SNAPSHOT') {
      list = list.filter((row) => row.rowType === '冻结版本')
    }
  }
  return [...list].sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
})

const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const pageNums = computed(() => {
  const total = pageCount.value
  const start = Math.max(0, Math.min(page.value - 2, total - 5))
  const end = Math.min(total, start + 5)
  return Array.from({ length: end - start }, (_, i) => start + i)
})
const paged = computed(() => filtered.value.slice(page.value * pageSize.value, (page.value + 1) * pageSize.value))
const emptyHint = computed(() => {
  if (listFailed.value) {
    return '简历列表读取失败，不能当成没有简历。'
  }
  if (tab.value === 'ARCHIVED') {
    return '没有已归档的简历或快照。'
  }
  return '此分类下还没有简历。'
})

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
    versionNo: item.version,
    rowType: '主简历',
    rowTone: 'blue',
    rowIcon: 'resume-master-resume',
    statusLabel: archived
      ? '已归档'
      : pendingConfirm
        ? '待确认'
        : item.status === 'READY_TO_EXPORT'
          ? '可导出'
          : '使用中',
    statusTone: archived ? 'gray' : pendingConfirm ? 'orange' : 'green',
    statusSub: archived ? '不可编辑' : pendingConfirm ? '需先处理待确认内容' : '可编辑',
    targetJob: '通用 / 默认',
    coverage: coverageOf(detail?.keyOutcomes),
    boundText: '—',
    updatedAt: item.updatedAt,
    archived,
    lockNote: false,
    pendingConfirm,
    expectedVersion: item.version,
  }
}

function versionRow(item: ResumeMasterSummary, version: ResumeVersionView): ResumeRow {
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
    versionNo: version.version,
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
    statusSub: version.immutable || isSnapshot ? '不可编辑' : '确认后冻结',
    targetJob: version.jobVersionId ? '历史定制版本' : '通用 / 默认',
    coverage: coverageOf(snapshotOutcomes(version)),
    boundText: version.status === 'FROZEN' ? '可导出' : '—',
    updatedAt: version.frozenAt || version.createdAt,
    archived,
    lockNote: version.immutable,
    pendingConfirm,
    expectedVersion: version.version,
  }
}

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  forbidden.value = null
  listFailed.value = false
  detailGap.value = 0
  try {
    const masters = await listResumes()
    // 列表接口只回主档摘要；类型统计与覆盖度需要每个主档的版本，小列表内并发拉取
    const settled = await Promise.allSettled(masters.map((item) => fetchResume(item.id)))
    const next: ResumeRow[] = []
    let gap = 0
    settled.forEach((result, index) => {
      const master = masters[index]
      if (!master) {
        return
      }
      if (result.status === 'fulfilled') {
        next.push(masterRow(master, result.value))
        for (const version of result.value.versions ?? []) {
          next.push(versionRow(master, version))
        }
      } else {
        gap += 1
        next.push(masterRow(master, null))
      }
    })
    rows.value = next
    detailGap.value = gap
  } catch (error) {
    rows.value = []
    listFailed.value = true
    if (isForbidden(error)) {
      forbidden.value = error
      return
    }
    pageError.value = errorMessage(error, '简历列表读取失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  void router.push('/ai-resume/new')
}

async function onCreate(): Promise<void> {
  createError.value = ''
  if (draft.mode === 'IMPORT' && !draft.importText.trim()) {
    createError.value = '导入须粘贴已有简历文本。解析结果需要确认，不会直接写成正式事实。'
    return
  }
  creating.value = true
  try {
    const created = await createResume({
      mode: draft.mode,
      title: draft.title.trim() || undefined,
      templateCode: draft.mode === 'TEMPLATE' ? draft.templateCode : undefined,
      importText: draft.mode === 'IMPORT' ? draft.importText : undefined,
    })
    draft.title = ''
    draft.importText = ''
    createOpen.value = false
    await router.push(`/resumes/${created.id}`)
  } catch (error) {
    if (isForbidden(error)) {
      forbidden.value = error
      return
    }
    createError.value = errorMessage(error, '创建简历失败')
  } finally {
    creating.value = false
  }
}

function rowLink(row: ResumeRow): string {
  return row.kind === 'MASTER'
    ? `/resumes/${row.masterId}`
    : `/resumes/${row.masterId}?versionId=${row.versionId}`
}

async function onCopy(row: ResumeRow): Promise<void> {
  pending.value = `${row.key}-copy`
  pageError.value = ''
  try {
    const copy = await copyResume(row.masterId)
    notice.value = `已复制为新草稿「${copy.title}」。冻结历史不会复制。`
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
    notice.value = `「${target.title}」已归档，可恢复。没有物理删除。`
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
  throw new Error('删除仍在后台处理中，请稍后重新打开简历工作台确认结果')
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
    forbidden.value = error
    return
  }
  if (isApiClientError(error) && error.reason === 'VERSION_CONFLICT') {
    pageError.value = error.message || '版本冲突，已拒绝覆盖。请重新读取后再操作。'
    void load()
    return
  }
  pageError.value = errorMessage(error, '简历操作失败')
}

watch([tab, pageSize], () => {
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
          <p class="page-head__sub">统一管理简历主档、岗位分支和冻结版本，随时预览与导出</p>
        </div>
        <div class="page-head__actions">
          <RouterLink class="btn btn--ghost" to="/resume-templates">
            <AppIcon name="book" :size="15" />
            模板中心
          </RouterLink>
          <AppButton variant="ghost" :pending="loading && !rows.length" @click="load">
            <AppIcon name="refresh" :size="15" />
            刷新
          </AppButton>
          <AppButton @click="openCreate">
            <AppIcon name="plus" :size="15" />
            新建简历
          </AppButton>
        </div>
      </header>

      <AppBanner v-if="detailGap" tone="warn">
        有 {{ detailGap }} 份简历的详情读取失败，其定制版 / 快照与证据覆盖度暂未计入。主档列表不受影响，可稍后刷新重试。
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
          <span class="stat__sub">按岗位生成的待确认版本</span>
        </div>
        <div class="stat">
          <span class="stat__icon stat__icon--orange"><JobProofIcon name="resume-frozen-version" :size="18" /></span>
          <span class="stat__label">冻结版本</span>
          <span class="stat__value">{{ stats.snapshots }}</span>
          <span class="stat__sub">已冻结，不可原地覆盖</span>
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

        <Transition name="resume-results" mode="out-in">
          <div :key="`${tab}-${page}-${pageSize}`" class="resume-results">
            <div v-if="loading && !rows.length" class="bones" aria-busy="true">
              <div class="bone" />
              <div class="bone" />
              <div class="bone bone--short" />
            </div>

            <template v-else-if="paged.length">
              <div class="tbl-wrap">
            <table class="tbl">
              <thead>
                <tr>
                  <th>简历名称</th>
                  <th>状态</th>
                  <th>目标岗位</th>
                  <th>证据覆盖度</th>
                  <th>更新时间</th>
                  <th>导出状态</th>
                  <th class="ops-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in paged" :key="row.key">
                  <td data-label="简历名称">
                    <div class="rname">
                      <span class="rtype" :class="`rtype--${row.rowTone}`">
                        <JobProofIcon :name="row.rowIcon" :size="16" />
                      </span>
                      <div class="rname__body">
                        <div class="rname__line">
                          <span class="rname__title" :title="row.title">{{ row.title }}</span>
                          <AppTag :tone="row.rowTone">{{ row.rowType }} · v{{ row.versionNo }}</AppTag>
                        </div>
                        <p v-if="row.lockNote" class="rname__lock">
                          <AppIcon name="lock" :size="12" />
                          不可原地覆盖
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
                  <td class="muted" data-label="目标岗位" :title="row.targetJob">{{ row.targetJob }}</td>
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
                  <td data-label="导出状态">
                    <span v-if="row.boundText === '已绑定'" class="bound-text">{{ row.boundText }}</span>
                    <span v-else class="muted">—</span>
                  </td>
                  <td data-label="操作">
                    <div class="ops">
                      <RouterLink class="text-link" :to="rowLink(row)">
                        {{
                          row.kind === 'MASTER'
                            ? row.archived
                              ? '打开'
                              : '编辑'
                            : row.pendingConfirm
                              ? '去确认'
                              : '查看快照'
                        }}
                      </RouterLink>
                      <template v-if="row.kind === 'MASTER'">
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

            <AppEmpty v-else :text="emptyHint" icon="file-text">
              <AppButton v-if="!listFailed && tab === 'ALL'" variant="ghost" @click="openCreate">
                <AppIcon name="plus" :size="14" />
                新建简历
              </AppButton>
            </AppEmpty>
          </div>
        </Transition>
      </div>
    </section>

    <AppModal :open="createOpen" title="新建简历" :width="560" @close="createOpen = false">
      <div class="form-stack">
        <p class="muted">空白、模板或导入创建。导入与 AI 生成内容确认后才会写进正式简历。</p>
        <AppBanner v-if="createError" tone="bad">{{ createError }}</AppBanner>
        <div class="seg" role="radiogroup" aria-label="创建方式">
          <button
            v-for="mode in CREATE_MODES"
            :key="mode.id"
            type="button"
            role="radio"
            class="seg__btn"
            :class="{ 'is-on': draft.mode === mode.id }"
            :aria-checked="draft.mode === mode.id"
            :disabled="creating"
            @click="draft.mode = mode.id"
          >
            {{ mode.label }}
          </button>
        </div>
        <AppField id="resume-title" label="简历名称" hint="可空。模板会给默认名。">
          <input
            id="resume-title"
            v-model="draft.title"
            class="input"
            type="text"
            placeholder="例如：数据分析实习生 · 定制版"
            :disabled="creating"
          />
        </AppField>
        <AppField v-if="draft.mode === 'TEMPLATE'" id="resume-template" label="模板">
          <AppSelect id="resume-template" v-model="draft.templateCode" ariaLabel="简历模板" :disabled="creating">
            <option v-for="(label, code) in TEMPLATE_GLOSS" :key="code" :value="code">{{ label }} · {{ code }}</option>
          </AppSelect>
        </AppField>
        <AppField
          v-if="draft.mode === 'IMPORT'"
          id="resume-import"
          label="已有简历文本"
          hint="导入结果需要逐项确认。不得虚构公司、时间或数字。"
        >
          <textarea id="resume-import" v-model="draft.importText" class="textarea" rows="8" :disabled="creating" />
        </AppField>
      </div>
      <template #footer>
        <AppButton variant="ghost" :disabled="creating" @click="createOpen = false">取消</AppButton>
        <AppButton :pending="creating" @click="onCreate">{{ creating ? '正在创建…' : '创建简历' }}</AppButton>
      </template>
    </AppModal>

    <AppModal :open="Boolean(archiveTarget)" title="归档简历" :width="440" @close="archiveTarget = null">
      <p class="muted">
        「{{ archiveTarget?.title }}」归档后进入「已归档」，可随时恢复。这是可恢复操作，不是物理删除。
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
          这是物理删除，不会进入归档，也不能恢复。删除完成后，相关正文和导出文件将无法找回。
        </AppBanner>
        <div class="delete-dialog__target">
          <span class="delete-dialog__icon"><AppIcon name="trash" :size="18" /></span>
          <div>
            <strong>{{ deleteTarget?.title }}</strong>
            <span>{{ deleteTarget?.kind === 'MASTER' ? '简历主档及其全部关联数据' : '仅此冻结版本及其关联数据' }}</span>
          </div>
        </div>
        <p v-if="deleteLoading && !deletePreview" class="muted">正在核对关联数据与运行中任务…</p>
        <template v-else-if="deletePreview">
          <p class="delete-dialog__summary">{{ deletePreview.impactSummary }}</p>
          <ul v-if="deletePreview.impacts?.length" class="delete-impact" aria-label="永久删除影响范围">
            <li v-for="(item, index) in deletePreview.impacts" :key="`${item.kind}-${index}`">
              <AppIcon name="x-circle" :size="14" />
              <span>{{ item.label || item.kind }}</span>
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
  color: var(--text-2);
  background: none;
  border: none;
  margin-bottom: -1px;
  white-space: nowrap;
  transition:
    color var(--motion-base) ease,
    background-color var(--motion-fast) ease,
    transform var(--motion-fast) var(--motion-ease);
}

.tabs__item:hover {
  color: var(--primary);
  background: #f7f9fd;
}

.tabs__item.is-on {
  color: var(--primary);
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
  background: var(--primary);
  box-shadow: 0 -2px 7px rgba(37, 99, 235, 0.18);
  pointer-events: none;
  transform: translateX(calc(var(--tab-index) * (100% + 4px)));
  transition: transform var(--motion-slow) var(--motion-ease);
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
  gap: 10px;
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
  background: var(--primary-soft);
  color: var(--primary);
}

.rtype--green {
  background: var(--success-soft);
  color: var(--success);
}

.rtype--orange {
  background: var(--warning-soft);
  color: var(--warning);
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
  color: var(--danger);
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
  background: var(--success);
}

.dot--orange {
  background: var(--warning);
}

.dot--red {
  background: var(--danger);
}

.dot--gray {
  background: var(--text-3);
}

.dot--blue {
  background: var(--primary);
}

.bound-text {
  color: var(--primary);
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
  color: var(--danger);
}

.text-link--danger:hover {
  color: var(--danger);
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

.pager__size :deep(.app-select__trigger) { height: 34px; }

.form-stack {
  display: grid;
  gap: 14px;
}

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
  color: var(--text-3);
  font-size: 12px;
}

.delete-dialog__icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--danger);
  background: var(--danger-soft);
}

.delete-dialog__summary {
  color: var(--text-2);
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
  color: var(--text-2);
  font-size: 12px;
}

.delete-impact svg {
  flex: 0 0 auto;
  color: var(--danger);
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

  .page-head__actions :deep(.btn),
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
    color: var(--text-3);
    font-size: 11px;
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

.seg {
  position: relative;
  isolation: isolate;
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  width: fit-content;
}

.seg::before {
  position: absolute;
  z-index: 0;
  inset: 3px auto 3px 3px;
  width: calc((100% - 14px) / 3);
  border-radius: var(--radius-s);
  background: var(--surface);
  box-shadow: var(--shadow-s);
  content: '';
  pointer-events: none;
  transition: transform var(--motion-slow) var(--motion-ease);
}

.seg:has(.seg__btn:nth-child(2).is-on)::before { transform: translateX(calc(100% + 4px)); }
.seg:has(.seg__btn:nth-child(3).is-on)::before { transform: translateX(calc(200% + 8px)); }

.seg__btn {
  position: relative;
  z-index: 1;
  padding: 6px 16px;
  font-size: 13px;
  border: none;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--text-2);
  transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease);
}

.seg__btn.is-on {
  background: transparent;
  color: var(--primary);
  font-weight: 600;
  box-shadow: none;
}

.seg__btn:active { transform: scale(.96); }

.form-stack > .field { animation: resume-form-field-in var(--motion-base) var(--motion-ease-out) both; }
@keyframes resume-form-field-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }

@media (prefers-reduced-motion: reduce) {
  .seg::before, .seg__btn { transition: none; }
  .seg__btn:active { transform: none; }
  .form-stack > .field { animation: none; }
}
</style>
