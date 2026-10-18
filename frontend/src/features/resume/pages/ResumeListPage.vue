<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { useRouter } from 'vue-router'
import { Archive, ArchiveRestore, Copy, LayoutGrid, List, PenLine, Search, Trash2 } from 'lucide-vue-next'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import type { MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSegmented, { type SegmentItem } from '@/shared/ui/UiSegmented.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { errorMessage, isForbidden, isVersionConflict } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import { glossMasterStatus } from '../labels'
import {
  archiveResume,
  copyResume,
  fetchResume,
  listResumes,
  restoreResume,
  updateResume,
} from '../services/resumeApi'
import { RESUME_TITLE_MAX, normalizeResumeTitle, resumeTitleIssue, titleMatches } from '../utils/resumeTitle'
import { RESUME_SORTS, groupUnderMasters, isResumeSort, sortResumes } from '../utils/resumeSort'
import ResumeCardGrid from '../components/ResumeCardGrid.vue'
import ResumeDeleteDialog from '../components/ResumeDeleteDialog.vue'
import ResumeTable from '../components/ResumeTable.vue'
import { buildRows, type ResumeRow, type ResumeRows } from '../utils/resumeRows'

const router = useRouter()
const pending = ref('')
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'resume-list-page-error')
useToastFeedback(notice, 'success', 'resume-list-notice')
const actionForbidden = ref<unknown>(null)
const tab = ref('ALL')
const query = ref('')
const storedSort = useLocalStorage<string>('jp:resumes:sort', 'updated-desc')
const sort = computed({
  get: () => (isResumeSort(storedSort.value) ? storedSort.value : 'updated-desc'),
  set: (value: string) => { storedSort.value = value },
})
const sortOptions = RESUME_SORTS.map(item => ({ value: item.value, label: item.label }))
type ListView = 'grid' | 'list'
const storedView = useLocalStorage<string>('jp:resumes:view', 'grid')
const view = computed<ListView>({
  get: () => (storedView.value === 'list' ? 'list' : 'grid'),
  set: (value) => { storedView.value = value },
})
const VIEWS: SegmentItem<ListView>[] = [
  { value: 'grid', label: '卡片', icon: LayoutGrid },
  { value: 'list', label: '列表', icon: List },
]
const page = ref(0)
const pageSize = ref(10)
const archiveTarget = ref<ResumeRow | null>(null)
const renameTarget = ref<ResumeRow | null>(null)
const renameValue = ref('')
const renameError = ref('')
const renaming = ref(false)
const deleteTarget = ref<ResumeRow | null>(null)

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
  // "全部" keeps every version right under its own resume instead of scattering them by date.
  return tab.value === 'ALL' ? groupUnderMasters(items, sort.value) : sortResumes(items, sort.value)
})

/** Cards fold frozen versions into a chip on their resume (the 冻结版本 tab still lists them one by one). */
const viewItems = computed(() => (view.value === 'grid' && tab.value === 'ALL'
  ? filtered.value.filter((row) => row.rowType !== '冻结版本')
  : filtered.value))
const snapshotCounts = computed(() => {
  const counts = new Map<string, number>()
  for (const row of rows.value) {
    if (row.rowType === '冻结版本' && !row.archived) counts.set(row.masterId, (counts.get(row.masterId) ?? 0) + 1)
  }
  return counts
})
/** Evidence coverage only exists for resumes with key outcomes; without any, the column is noise. */
const hasCoverage = computed(() => filtered.value.some((row) => row.coverage !== null))

const pageCount = computed(() => Math.max(1, Math.ceil(viewItems.value.length / pageSize.value)))
const pageNums = computed(() => {
  const total = pageCount.value
  const start = Math.max(0, Math.min(page.value - 2, total - 5))
  const end = Math.min(total, start + 5)
  return Array.from({ length: end - start }, (_, i) => start + i)
})
const paged = computed(() => viewItems.value.slice(page.value * pageSize.value, (page.value + 1) * pageSize.value))
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

async function loadRows(signal: AbortSignal): Promise<ResumeRows> {
  const masters = await listResumes(signal)
  // 列表接口只回主档摘要；类型统计与覆盖度需要每个主档的版本，小列表内并发拉取
  const settled = await Promise.allSettled(masters.map((item) => fetchResume(item.id, signal)))
  return buildRows(masters, settled.map((result) => (result.status === 'fulfilled' ? result.value : null)))
}

function openCreate(): void {
  void router.push('/ai-resume/new')
}

function rowMenu(row: ResumeRow): MenuItem[] {
  if (row.kind === 'VERSION') {
    return row.rowType === '冻结版本'
      ? [{ label: '永久删除', icon: Trash2, danger: true, onSelect: () => openPermanentDelete(row) }]
      : []
  }
  const items: MenuItem[] = []
  if (!row.archived) items.push({ label: '重命名', icon: PenLine, onSelect: () => openRename(row) })
  items.push({ label: '复制一份', icon: Copy, onSelect: () => void onCopy(row) })
  items.push(row.archived
    ? { label: '恢复', icon: ArchiveRestore, onSelect: () => void onRestore(row) }
    : { label: '归档', icon: Archive, onSelect: () => { archiveTarget.value = row } })
  items.push({ type: 'separator' }, { label: '永久删除', icon: Trash2, danger: true, onSelect: () => openPermanentDelete(row) })
  return items
}

function showSnapshots(row: ResumeRow): void {
  tab.value = 'SNAPSHOT'
  query.value = row.title
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

function openPermanentDelete(row: ResumeRow): void {
  deleteTarget.value = row
}

function onDeleted(title: string): void {
  deleteTarget.value = null
  notice.value = `「${title}」及其关联数据已永久删除，无法恢复。`
  void load()
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

watch([tab, pageSize, query, sort, view], () => {
  page.value = 0
})

onMounted(() => {
  void load()
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
            <div class="list-toolbar__sort">
              <UiSelect v-model="sort" :options="sortOptions" size="sm" aria-label="排序方式" data-testid="resume-sort" />
              <UiSegmented v-model="view" :items="VIEWS" size="sm" aria-label="显示方式" />
            </div>
          </div>

          <Transition name="resume-results" mode="out-in">
            <div :key="`${tab}-${page}-${pageSize}`" class="resume-results">
              <template v-if="paged.length">
                <ResumeCardGrid
                  v-if="view === 'grid'"
                  :rows="paged"
                  :menu="rowMenu"
                  :snapshot-counts="tab === 'ALL' ? snapshotCounts : undefined"
                  :busy="Boolean(pending)"
                  @snapshots="showSnapshots"
                />
                <ResumeTable
                  v-else
                  :rows="paged"
                  :nested="tab === 'ALL'"
                  :show-coverage="hasCoverage"
                  :busy="Boolean(pending)"
                  @rename="openRename"
                  @copy="onCopy"
                  @archive="archiveTarget = $event"
                  @restore="onRestore"
                  @delete="openPermanentDelete"
                />
                <footer class="tbl-foot">
                  <span class="muted">共 {{ viewItems.length }} 条</span>
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

  <ResumeDeleteDialog :target="deleteTarget" @close="deleteTarget = null" @deleted="onDeleted" />
</template>

<style scoped>
.page,
.page > * {
  min-width: 0;
}

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
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}

.list-toolbar__search {
  flex: 0 1 320px;
  min-width: 0;
}

.list-toolbar__sort {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
  margin-left: auto;
}

.list-toolbar__sort > :first-child {
  width: 132px;
}

/* ---------- Card view ---------- */
/* Versions listed under their resume in the "全部" table. */
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

.pager__size:deep(.app-select__trigger) {
  height: 34px;
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

  /* Three compact counters in one row, so the list itself starts above the fold. */
  .grid-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .stat {
    grid-template-columns: auto minmax(0, 1fr);
    grid-template-areas: 'icon value' 'label label';
    align-items: center;
    gap: 6px 8px;
    padding: 10px 12px;
  }

  .stat__icon {
    grid-area: icon;
    width: 28px;
    height: 28px;
    margin-bottom: 0;
  }

  .stat__value {
    grid-area: value;
    justify-content: flex-end;
    font-size: 22px;
  }

  .stat__label {
    grid-area: label;
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .stat__sub {
    display: none;
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
  .resume-results-leave-active {
    transition: none;
  }

  .tabs__item:active,
  .resume-results-enter-from,
  .resume-results-leave-to {
    transform: none;
  }

}

@media (max-width: 560px) {
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

}
</style>
