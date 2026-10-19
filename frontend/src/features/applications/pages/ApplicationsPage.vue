<script setup lang="ts">
/**
 * 投递追踪 (docs/phase2/01): a board of every application, each with its own resume link whose
 * receipts say when the company opened the resume and for how long.
 */
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Columns3, Eye, Link2, List, Plus, RefreshCw, Search, Send } from 'lucide-vue-next'
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
import { errorMessage } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import { formatRelative } from '@/shared/lib/datetime'
import ApplicationBoard from '../components/ApplicationBoard.vue'
import ApplicationDialog, { type ApplicationPrefill } from '../components/ApplicationDialog.vue'
import ApplicationDrawer from '../components/ApplicationDrawer.vue'
import ApplicationFunnel from '../components/ApplicationFunnel.vue'
import ResumeLinksPanel from '../components/ResumeLinksPanel.vue'
import { fetchApplications, moveApplication } from '../services/applicationsApi'
import { OUTCOMES, channelLabel, outcomeLabel, stageLabel } from '../labels'
import type { ApplicationOutcome, ApplicationStage, JobApplication } from '../types'
import { dueOf, receiptOf } from '../utils/receipts'

type View = 'board' | 'list' | 'links'
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
/** Set when the empty page asks for a general link: the links view opens its form once mounted. */
const createLinkOnOpen = ref(false)
let clock: number | undefined

const stats = computed(() => page.data.value?.stats ?? null)
const filtered = computed(() => {
  const needle = query.value.trim().toLowerCase()
  if (!needle) return items.value
  return items.value.filter(item => `${item.company} ${item.positionTitle} ${item.location ?? ''}`.toLowerCase().includes(needle))
})
const freshOpens = computed(() => items.value.filter(item => receiptOf(item.link, now.value).tone === 'fresh'))
const viewItems = [
  { value: 'board' as const, label: '看板', icon: Columns3 },
  { value: 'list' as const, label: '列表', icon: List },
  { value: 'links' as const, label: '链接', icon: Link2 },
]
const outcomeOptions = OUTCOMES.map(item => ({ value: item.value, label: item.label }))
const closingApplication = computed(() => items.value.find(item => item.id === closingId.value) ?? null)

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
    replace(await moveApplication(id, stage, current.version, outcome))
    void refreshQuietly()
  } catch (reason) {
    replace(current)
    toast.error(errorMessage(reason, '阶段更新失败'))
  }
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
  replace(updated)
  void refreshQuietly()
}

function onDeleted(id: string): void {
  drawerId.value = null
  items.value = items.value.filter(item => item.id !== id)
  void refreshQuietly()
  toast.success('投递记录已删除')
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

      <template v-if="items.length || view === 'links'">
        <ApplicationFunnel v-if="stats && items.length" :stats="stats" />

        <aside v-if="freshOpens.length" class="fresh-strip" aria-live="polite">
          <span class="fresh-strip__icon"><Eye :size="16" /></span>
          <p>
            <strong>{{ freshOpens.map(item => item.company).slice(0, 3).join('、') }}</strong>
            <template v-if="freshOpens.length > 3"> 等 {{ freshOpens.length }} 家公司</template>
            在过去 24 小时打开了你的简历
          </p>
          <UiButton size="sm" variant="ghost" @click="openDrawer(freshOpens[0]!.id)">看回执</UiButton>
        </aside>

        <div class="applications-toolbar" :class="{ 'is-links': view === 'links' }">
          <UiInput v-if="view !== 'links'" v-model="query" :icon="Search" clearable placeholder="搜索公司或岗位" aria-label="搜索投递" class="applications-toolbar__search" />
          <UiSegmented v-model="view" :items="viewItems" aria-label="显示方式" size="sm" />
        </div>

        <ResumeLinksPanel
          v-if="view === 'links'"
          :now="now"
          :create-on-mount="createLinkOnOpen"
          @open-application="openDrawer"
          @changed="refreshQuietly"
        />
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
                <td><span class="receipt-cell" :class="`is-${receiptOf(item.link, now).tone}`">{{ receiptOf(item.link, now).label }}</span></td>
                <td>
                  <span v-if="dueOf(item.nextStepAt, now) && item.stage !== 'CLOSED'" class="due-cell" :class="`is-${dueOf(item.nextStepAt, now)!.tone}`">{{ item.nextStep || '下一步' }} · {{ dueOf(item.nextStepAt, now)!.label }}</span>
                  <span v-else class="muted">—</span>
                </td>
                <td class="muted">{{ formatRelative(item.updatedAt, now) }}</td>
              </tr>
            </tbody>
          </table>
          <p v-if="!filtered.length" class="applications-none">没有匹配“{{ query }}”的投递</p>
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

    <ApplicationDialog :open="dialogOpen" :application="editing" :prefill="prefill" @close="dialogOpen = false" @saved="onSaved" />
    <ApplicationDrawer
      :open="Boolean(drawerId)"
      :application-id="drawerId"
      @close="drawerId = null"
      @changed="onDrawerChanged"
      @deleted="onDeleted"
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
