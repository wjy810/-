<script setup lang="ts">
/**
 * 投递追踪 (docs/phase2/01): a board of every application, each with its own resume link whose
 * receipts say when the company opened the resume and for how long.
 */
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Archive, ArchiveRestore, Columns3, Eye, Link2, List, Plus, RefreshCw, Search, Send, Trash2 } from 'lucide-vue-next'
import PageState from '@/shared/ui/PageState.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { toast } from '@/shared/ui/toast'
import { confirm } from '@/shared/ui/confirm'
import { errorMessage } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import { formatRelative } from '@/shared/lib/datetime'
import ApplicationBoard from '../components/ApplicationBoard.vue'
import ApplicationDialog, { type ApplicationPrefill } from '../components/ApplicationDialog.vue'
import ApplicationDrawer from '../components/ApplicationDrawer.vue'
import ApplicationFunnel from '../components/ApplicationFunnel.vue'
import ResumeLinksPanel from '../components/ResumeLinksPanel.vue'
import {
  deleteApplication, fetchApplications, fetchArchivedApplications, moveApplication, restoreApplication,
} from '../services/applicationsApi'
import { CHANNELS, OUTCOMES, STAGES, channelLabel, outcomeLabel, stageLabel } from '../labels'
import type { ApplicationOutcome, ApplicationStage, JobApplication } from '../types'
import {
  dueOf, filterApplications, receiptOf, sortApplications, type ApplicationFilter, type ApplicationSort,
} from '../utils/receipts'

type View = 'board' | 'list' | 'links' | 'archive'
const VIEWS: readonly View[] = ['board', 'list', 'links']
const VIEW_KEY = 'jp:applications:view'

const route = useRoute()
const router = useRouter()
const page = useLoadState(() => fetchApplications())
const items = shallowRef<JobApplication[]>([])
const now = ref(new Date())
const view = ref<View>(readView())
const query = ref('')
const dialogOpen = ref(false)
const editing = ref<JobApplication | null>(null)
const prefill = ref<ApplicationPrefill | null>(null)
const drawerId = ref<string | null>(null)
const closingId = ref<string | null>(null)
const closingOutcome = ref<ApplicationOutcome>('REJECTED')
const closingPending = ref(false)
const filter = ref<ApplicationFilter>({ stage: 'ALL', channel: 'ALL', overdue: false, opened: false })
const sort = ref<ApplicationSort>('UPDATED')
const archived = shallowRef<JobApplication[] | null>(null)
const archivePending = ref('')
/** Set when the empty page asks for a general link: the links view opens its form once mounted. */
const createLinkOnOpen = ref(false)
let clock: number | undefined

const stats = computed(() => page.data.value?.stats ?? null)
const filtered = computed(() => {
  const needle = query.value.trim().toLowerCase()
  const matched = needle
    ? items.value.filter(item => `${item.company} ${item.positionTitle} ${item.location ?? ''}`.toLowerCase().includes(needle))
    : items.value
  // The board keeps its own per-column order; the stage filter is for the list.
  const narrowed = filterApplications(matched, view.value === 'board' ? { ...filter.value, stage: 'ALL' } : filter.value, now.value)
  return sortApplications(narrowed, sort.value)
})
const filtering = computed(() => Boolean(query.value.trim()) || filter.value.overdue || filter.value.opened
  || filter.value.channel !== 'ALL' || (view.value === 'list' && filter.value.stage !== 'ALL'))
const archivedCount = computed(() => page.data.value?.archivedCount ?? 0)
const stageOptions = [{ value: 'ALL', label: '全部阶段' }, ...STAGES.map(item => ({ value: item.value, label: item.label }))]
const channelOptions = [{ value: 'ALL', label: '全部渠道' }, ...CHANNELS.map(item => ({ value: item.value, label: item.label }))]
const sortOptions = [
  { value: 'UPDATED', label: '最近更新' }, { value: 'NEXT_STEP', label: '下一步时间' },
  { value: 'APPLIED', label: '投递日期' }, { value: 'COMPANY', label: '公司名称' },
]
const freshOpens = computed(() => items.value.filter(item => receiptOf(item.link, now.value, item.receipts).tone === 'fresh'))
const viewItems = [
  { value: 'board' as const, label: '看板', icon: Columns3 },
  { value: 'list' as const, label: '列表', icon: List },
  { value: 'links' as const, label: '链接', icon: Link2 },
]
const closingApplication = computed(() => items.value.find(item => item.id === closingId.value) ?? null)
/** 接受 / 婉拒 Offer only for an application that reached an offer. */
const outcomeOptions = computed(() => OUTCOMES
  .filter(item => closingApplication.value?.furthestStage === 'OFFER' || (item.value !== 'ACCEPTED' && item.value !== 'DECLINED'))
  .map(item => ({ value: item.value, label: item.label })))

function readView(): View {
  try {
    const saved = window.localStorage.getItem(VIEW_KEY) as View | null
    return saved && VIEWS.includes(saved) ? saved : 'board'
  } catch {
    return 'board'
  }
}

async function load(): Promise<void> {
  const result = await page.load()
  if (result) items.value = result.items
}

/** Stats come from the server; after a local change they are refreshed quietly. */
async function refreshQuietly(): Promise<void> {
  try {
    const result = await fetchApplications()
    page.data.value = result
    items.value = result.items
  } catch { /* the board keeps what it shows */ }
}

function replace(updated: JobApplication): void {
  const exists = items.value.some(item => item.id === updated.id)
  items.value = exists ? items.value.map(item => (item.id === updated.id ? updated : item)) : [updated, ...items.value]
}

async function move(id: string, stage: ApplicationStage, outcome?: ApplicationOutcome): Promise<void> {
  const current = items.value.find(item => item.id === id)
  if (!current) return
  if (stage === 'CLOSED' && !outcome) {
    closingId.value = id
    closingOutcome.value = 'REJECTED'
    return
  }
  // Optimistic: the card lands at once and goes back if the server refuses.
  replace({ ...current, stage, outcome: outcome ?? null })
  try {
    const updated = await moveApplication(id, stage, current.version, outcome)
    replace(updated)
    void refreshQuietly()
    if (outcome === 'ACCEPTED') await offerToCloseOthers(updated)
    else if (current.stage === 'WISHLIST' && stage !== 'WISHLIST' && stage !== 'CLOSED' && updated.link?.status !== 'ACTIVE') {
      // 想投 got no link; now that it went out, offer one.
      toast.success(`「${updated.company}」已投递，投递日期记为今天`, {
        description: '生成这家公司的专属简历链接，对方打开时提醒你。',
        action: { label: '生成链接', onClick: () => openDrawer(updated.id) },
        duration: 8000,
      })
    }
  } catch (reason) {
    replace(current)
    toast.error(errorMessage(reason, '阶段更新失败'))
  }
}

function clearFilters(): void {
  query.value = ''
  filter.value = { stage: 'ALL', channel: 'ALL', overdue: false, opened: false }
}

/** Accepting an offer usually ends the search: offer to close the other open applications as withdrawn. */
async function offerToCloseOthers(accepted: JobApplication): Promise<void> {
  const others = items.value.filter(item => item.id !== accepted.id && item.stage !== 'CLOSED' && item.stage !== 'WISHLIST')
  if (!others.length || !(await confirm({
    title: `恭喜拿下「${accepted.company}」！`,
    message: `还有 ${others.length} 条进行中的投递。要把它们一起结束（结果记为“主动放弃”）吗？之后仍可以重新打开。`,
    confirmText: '一起结束',
    cancelText: '先保留',
  }))) return
  let failed = 0
  for (const item of others) {
    try { replace(await moveApplication(item.id, 'CLOSED', item.version, 'WITHDRAWN')) } catch { failed++ }
  }
  void refreshQuietly()
  if (failed) toast.error(`${failed} 条没有结束成功，请刷新后再试`)
  else toast.success(`已结束 ${others.length} 条投递`)
}

async function confirmClose(): Promise<void> {
  if (!closingId.value) return
  closingPending.value = true
  await move(closingId.value, 'CLOSED', closingOutcome.value)
  closingPending.value = false
  closingId.value = null
}

function openCreate(fill: ApplicationPrefill | null = null): void {
  editing.value = null
  prefill.value = fill
  dialogOpen.value = true
}

function onSaved(saved: JobApplication): void {
  const created = !editing.value
  dialogOpen.value = false
  editing.value = null
  replace(saved)
  void refreshQuietly()
  if (created && saved.link) {
    const url = `${window.location.origin}${saved.link.path}`
    toast.success(`已记录「${saved.company}」，专属简历链接已生成`, {
      action: { label: '复制链接', onClick: () => { void navigator.clipboard?.writeText(url) } },
      duration: 8000,
    })
  } else {
    toast.success(created ? `已记录「${saved.company}」` : '已保存')
  }
}

function openDrawer(id: string): void {
  drawerId.value = id
}

function onDrawerChanged(updated: JobApplication): void {
  const before = items.value.find(item => item.id === updated.id)
  replace(updated)
  void refreshQuietly()
  if (updated.outcome === 'ACCEPTED' && before?.outcome !== 'ACCEPTED') void offerToCloseOthers(updated)
}

function onArchived(application: JobApplication): void {
  drawerId.value = null
  items.value = items.value.filter(item => item.id !== application.id)
  archived.value = null
  void refreshQuietly()
  toast.success(`「${application.company}」已移入归档`, {
    description: application.link?.status === 'SUSPENDED' ? '专属链接已停用，不再占用有效链接名额；恢复后自动重新启用。' : undefined,
    action: { label: '撤销', onClick: () => { void restore(application) } },
  })
}

async function openArchive(): Promise<void> {
  view.value = 'archive'
  try {
    archived.value = await fetchArchivedApplications()
  } catch (reason) {
    toast.error(errorMessage(reason, '归档读取失败'))
    view.value = 'board'
  }
}

function announceRestored(restored: JobApplication): void {
  replace(restored)
  archived.value = archived.value?.filter(item => item.id !== restored.id) ?? null
  void refreshQuietly()
  if (restored.link?.status === 'SUSPENDED') {
    toast.warning(`「${restored.company}」已恢复，专属链接还停用着`, {
      description: '有效链接名额已满。撤回不再使用的链接后，在详情里重新启用。',
      action: { label: '查看', onClick: () => openDrawer(restored.id) },
    })
  } else {
    toast.success(`「${restored.company}」已恢复`)
  }
}

async function restore(application: JobApplication): Promise<void> {
  archivePending.value = application.id
  try {
    announceRestored(await restoreApplication(application.id))
  } catch (reason) {
    toast.error(errorMessage(reason, '恢复失败'))
  } finally { archivePending.value = '' }
}

async function deleteForGood(application: JobApplication): Promise<void> {
  if (!(await confirm({
    title: `彻底删除「${application.company}」？`,
    message: '时间线、打开记录会一并删除，专属链接立即失效。这个操作不能撤销。',
    confirmText: '彻底删除',
    tone: 'danger',
  }))) return
  archivePending.value = application.id
  try {
    await deleteApplication(application.id)
    archived.value = archived.value?.filter(item => item.id !== application.id) ?? null
    void refreshQuietly()
  } catch (reason) {
    toast.error(errorMessage(reason, '删除失败'))
  } finally { archivePending.value = '' }
}

function editFromDrawer(application: JobApplication): void {
  editing.value = application
  prefill.value = null
  dialogOpen.value = true
}

function startGeneralLink(): void {
  createLinkOnOpen.value = true
  view.value = 'links'
}

/**
 * `?focus=<id>` from a notification opens that application; `?tab=links` (a general link's notice)
 * shows the links; `?new=1&company=…` starts a record.
 */
function applyQuery(): void {
  const focus = typeof route.query.focus === 'string' ? route.query.focus : ''
  if (focus) drawerId.value = focus
  if (route.query.tab === 'links') view.value = 'links'
  if (route.query.new === '1') {
    const text = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : undefined)
    openCreate({ company: text('company'), positionTitle: text('position'), location: text('location'), jobMatchId: text('jobMatchId') })
  }
  if (focus || route.query.new || route.query.tab) void router.replace({ query: {} })
}

watch(view, value => {
  if (value === 'archive') return
  try { window.localStorage.setItem(VIEW_KEY, value) } catch { /* a remembered view is a convenience */ }
})

onMounted(async () => {
  clock = window.setInterval(() => { now.value = new Date() }, 60_000)
  await load()
  applyQuery()
})
onUnmounted(() => window.clearInterval(clock))
</script>

<template>
  <div class="page-container page-container--wide applications-page">
    <UiPageHeader title="投递追踪" description="每家公司一条专属简历链接——对方什么时候打开、读了多久，你都会知道。">
      <template #actions>
        <UiButton variant="secondary" :icon="RefreshCw" :pending="page.loading.value && page.loaded.value" @click="load">刷新</UiButton>
        <UiButton :icon="Plus" @click="openCreate()">记录投递</UiButton>
      </template>
    </UiPageHeader>

    <PageState :loading="page.loading.value" :error="page.error.value" :loaded="page.loaded.value" error-title="投递记录读取失败" @retry="load">
      <template #skeleton>
        <UiSkeleton height="132px" radius="var(--radius-lg)" />
        <div class="applications-skeleton"><UiSkeleton v-for="n in 4" :key="n" height="300px" radius="var(--radius-lg)" /></div>
      </template>

      <template v-if="items.length || view === 'links' || view === 'archive' || archivedCount">
        <ApplicationFunnel v-if="stats && items.length" :stats="stats" />

        <aside v-if="freshOpens.length" class="fresh-strip" aria-live="polite">
          <span class="fresh-strip__icon"><Eye :size="16" /></span>
          <p>
            <template v-for="(item, index) in freshOpens.slice(0, 3)" :key="item.id"><template v-if="index">、</template><button type="button" class="fresh-strip__company" @click="openDrawer(item.id)">{{ item.company }}</button></template>
            <template v-if="freshOpens.length > 3"> 等 {{ freshOpens.length }} 家公司</template>
            在过去 24 小时打开了你的简历
          </p>
          <UiButton size="sm" variant="ghost" @click="openDrawer(freshOpens[0]!.id)">看回执</UiButton>
        </aside>

        <div class="applications-toolbar" :class="{ 'is-links': view === 'links' || view === 'archive' }">
          <UiInput v-if="view === 'board' || view === 'list'" v-model="query" :icon="Search" clearable placeholder="搜索公司或岗位" aria-label="搜索投递" class="applications-toolbar__search" />
          <UiSegmented v-if="view !== 'archive'" v-model="view" :items="viewItems" aria-label="显示方式" size="sm" />
          <UiButton v-else size="sm" variant="secondary" @click="view = 'board'">返回看板</UiButton>
          <UiButton v-if="archivedCount && view !== 'archive'" size="sm" variant="ghost" :icon="Archive" @click="openArchive">归档 {{ archivedCount }}</UiButton>
        </div>
        <div v-if="view === 'board' || view === 'list'" class="applications-filters" role="group" aria-label="筛选与排序">
          <UiSelect v-if="view === 'list'" v-model="filter.stage" :options="stageOptions" size="sm" aria-label="按阶段筛选" />
          <UiSelect v-model="filter.channel" :options="channelOptions" size="sm" aria-label="按渠道筛选" />
          <button type="button" class="filter-chip" :aria-pressed="filter.overdue" :class="{ 'is-on': filter.overdue }" @click="filter = { ...filter, overdue: !filter.overdue }">下一步已逾期<template v-if="stats?.overdue"> {{ stats.overdue }}</template></button>
          <button type="button" class="filter-chip" :aria-pressed="filter.opened" :class="{ 'is-on': filter.opened }" @click="filter = { ...filter, opened: !filter.opened }">简历被打开过</button>
          <button v-if="filtering" type="button" class="filter-clear" @click="clearFilters">清除筛选</button>
          <UiSelect v-if="view === 'list'" v-model="sort" :options="sortOptions" size="sm" aria-label="排序" class="applications-filters__sort" />
        </div>

        <ResumeLinksPanel
          v-if="view === 'links'"
          :now="now"
          :create-on-mount="createLinkOnOpen"
          @open-application="openDrawer"
          @changed="refreshQuietly"
        />
        <section v-else-if="view === 'archive'" class="archive-list" aria-label="已归档的投递">
          <p class="archive-list__intro">归档的投递不出现在看板、统计和提醒里，可以随时恢复。</p>
          <UiSkeleton v-if="!archived" height="120px" radius="var(--radius-lg)" />
          <p v-else-if="!archived.length" class="applications-none">归档是空的</p>
          <ul v-else>
            <li v-for="item in archived" :key="item.id">
              <div><strong>{{ item.company }}</strong><small>{{ item.positionTitle }} · {{ item.stage === 'CLOSED' ? outcomeLabel(item.outcome) || '已结束' : stageLabel(item.stage) }} · 归档于 {{ formatRelative(item.archivedAt ?? item.updatedAt, now) }}</small></div>
              <UiButton size="sm" variant="secondary" :icon="ArchiveRestore" :pending="archivePending === item.id" @click="restore(item)">恢复</UiButton>
              <UiButton size="sm" variant="ghost" :icon="Trash2" :disabled="Boolean(archivePending)" @click="deleteForGood(item)">彻底删除</UiButton>
            </li>
          </ul>
        </section>
        <ApplicationBoard v-else-if="view === 'board'" :items="filtered" :now="now" @open="openDrawer" @move="move" />

        <div v-else class="applications-table-wrap">
          <table class="applications-table">
            <thead>
              <tr><th>公司 / 岗位</th><th>阶段</th><th>渠道</th><th>简历回执</th><th>下一步</th><th>更新</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in filtered" :key="item.id" tabindex="0" @click="openDrawer(item.id)" @keydown.enter="openDrawer(item.id)">
                <td><strong>{{ item.company }}</strong><small>{{ item.positionTitle }}</small></td>
                <td>
                  <span class="stage-pill" :class="`is-${item.stage.toLowerCase()}`">{{ item.stage === 'CLOSED' ? outcomeLabel(item.outcome) || '已结束' : stageLabel(item.stage) }}</span>
                </td>
                <td>{{ channelLabel(item.channel) }}</td>
                <td><span class="receipt-cell" :class="`is-${receiptOf(item.link, now, item.receipts).tone}`">{{ receiptOf(item.link, now, item.receipts).label }}</span></td>
                <td>
                  <span v-if="dueOf(item.nextStepAt, now) && item.stage !== 'CLOSED'" class="due-cell" :class="`is-${dueOf(item.nextStepAt, now)!.tone}`">{{ item.nextStep || '下一步' }} · {{ dueOf(item.nextStepAt, now)!.label }}</span>
                  <span v-else class="muted">—</span>
                </td>
                <td class="muted">{{ formatRelative(item.updatedAt, now) }}</td>
              </tr>
            </tbody>
          </table>
          <p v-if="!filtered.length" class="applications-none">{{ query.trim() ? `没有匹配“${query}”的投递` : '没有符合筛选条件的投递' }}</p>
        </div>
      </template>

      <UiEmptyState
        v-else
        size="lg"
        illustration="application-board"
        title="把每一次投递放进看板"
        description="记录投到了哪家公司、走到哪一步；附上专属简历链接，对方打开简历的那一刻你就会收到提醒。"
      >
        <UiButton :icon="Send" @click="openCreate()">记录第一条投递</UiButton>
        <UiButton variant="secondary" :icon="Link2" @click="startGeneralLink">先建一条通用链接</UiButton>
      </UiEmptyState>
    </PageState>

    <ApplicationDialog :open="dialogOpen" :application="editing" :prefill="prefill" :existing="items" @close="dialogOpen = false" @saved="onSaved" />
    <ApplicationDrawer
      :open="Boolean(drawerId)"
      :application-id="drawerId"
      @close="drawerId = null"
      @changed="onDrawerChanged"
      @archived="onArchived"
      @restored="announceRestored"
      @edit="editFromDrawer"
    />
    <UiDialog
      :open="Boolean(closingApplication)"
      :title="`结束「${closingApplication?.company ?? ''}」的投递`"
      description="选择结果后移到“已结束”。之后仍可以重新打开。"
      width="sm"
      @close="closingId = null"
    >
      <UiSelect v-model="closingOutcome" :options="outcomeOptions" aria-label="结束原因" />
      <template #footer>
        <UiButton variant="secondary" @click="closingId = null">取消</UiButton>
        <UiButton :pending="closingPending" @click="confirmClose">移到已结束</UiButton>
      </template>
    </UiDialog>
  </div>
</template>

<style scoped>
.applications-page {
  display: grid;
  gap: var(--space-5);
}

.applications-page :deep(.page-state) {
  display: grid;
  gap: var(--space-4);
}

.applications-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.applications-filters :deep(.ui-select-trigger) { min-width: 128px; }
.applications-filters__sort { margin-left: auto; }

.filter-chip {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  color: var(--text-secondary);
  background: var(--surface-1);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.filter-chip.is-on {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.filter-clear {
  padding: 0 6px;
  border: 0;
  color: var(--color-primary);
  background: none;
  font-size: var(--fs-sm);
  cursor: pointer;
}

.fresh-strip__company {
  padding: 0;
  border: 0;
  color: inherit;
  background: none;
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.archive-list { display: grid; gap: var(--space-3); }
.archive-list__intro { margin: 0; color: var(--text-secondary); font-size: var(--fs-sm); }
.archive-list ul { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
.archive-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: 12px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}
.archive-list li > div { min-width: 0; flex: 1 1 240px; display: grid; gap: 2px; }
.archive-list small { color: var(--text-tertiary); }

.applications-skeleton {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.fresh-strip {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--color-success) 35%, var(--border-subtle));
  border-radius: var(--radius-lg);
  color: var(--color-success-text);
  background: var(--color-success-soft);
  font-size: var(--fs-sm);
  animation: fresh-in var(--dur-base) var(--ease-out);
}

.fresh-strip p {
  flex: 1;
  min-width: 0;
}

.fresh-strip__icon {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-1);
}

.applications-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.applications-toolbar__search {
  width: min(320px, 100%);
}

.applications-toolbar.is-links {
  justify-content: flex-end;
}

.applications-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-1);
}

.applications-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.applications-table th {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
  background: var(--surface-2);
  font-size: var(--fs-xs);
  font-weight: 600;
  text-align: left;
}

.applications-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-subtle);
  vertical-align: middle;
}

.applications-table tbody tr {
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.applications-table tbody tr:hover,
.applications-table tbody tr:focus-visible {
  outline: none;
  background: var(--surface-2);
}

.applications-table td strong,
.applications-table td small {
  display: block;
}

.applications-table td small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.stage-pill,
.receipt-cell {
  display: inline-block;
  padding: 2px 9px;
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  background: var(--surface-3);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.stage-pill.is-applied { color: var(--color-primary-text); background: var(--color-primary-soft); }
.stage-pill.is-assessment { color: var(--color-warning-text); background: var(--color-warning-soft); }
.stage-pill.is-interview { color: var(--color-primary-text); background: var(--color-primary-soft); }
.stage-pill.is-offer { color: var(--color-success-text); background: var(--color-success-soft); }

.receipt-cell.is-fresh { color: var(--color-success-text); background: var(--color-success-soft); }
.receipt-cell.is-seen { color: var(--color-primary-text); background: var(--color-primary-soft); }

.due-cell { font-size: var(--fs-xs); }
.due-cell.is-soon { color: var(--color-warning-text); }
.due-cell.is-overdue { color: var(--color-danger-text); font-weight: 600; }

.muted {
  color: var(--text-tertiary);
}

.applications-none {
  padding: var(--space-6);
  color: var(--text-tertiary);
  text-align: center;
}

@keyframes fresh-in {
  from { opacity: 0; transform: translateY(-4px); }
}

@media (max-width: 720px) {
  .applications-toolbar { flex-wrap: wrap; }
  .applications-toolbar__search { width: 100%; }
  .applications-skeleton { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (prefers-reduced-motion: reduce) {
  .fresh-strip { animation: none; }
}
</style>
