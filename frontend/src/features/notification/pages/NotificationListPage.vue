<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppDrawer from '@/shared/ui/AppDrawer.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import type { JobProofIconName } from '@/shared/ui/jobProofIcons'
import {
  canMarkRead,
  dataRightsPathForNotice,
  glossNotificationStatus,
  glossNotificationType,
  isProtectedNotice,
  markReadBlockedReason,
} from '../labels'
import {
  fetchTaskDetail,
  fetchUnreadCount,
  listNotifications,
  markNotificationRead,
  markNotificationsReadBatch,
} from '../services/notificationApi'
import type { AsyncTaskView, NotificationView } from '../types'

type TagTone = 'blue' | 'green' | 'orange' | 'red' | 'gray' | 'purple'

type RowMeta = {
  icon: JobProofIconName
  cls: 'blue' | 'green' | 'red' | 'orange' | 'purple' | 'ink'
}

type RowTag = {
  text: string
  tone: TagTone
}

type DecoratedRow = {
  item: NotificationView
  meta: RowMeta
  tag: RowTag | null
  readable: boolean
  blockReason: string
  source: string | null
  taskish: boolean
}

// 任务类通知：点击可打开异步任务详情抽屉
const TASKISH_TYPES = new Set(['TASK_COMPLETED', 'TASK_FAILED', 'DATA_EXPORT_PROGRESS', 'DATA_DELETION_PROGRESS'])

const loading = ref(true)
const router = useRouter()
const pending = ref('')
const pageError = ref('')
const formError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'notification-list-page-error')
useToastFeedback(formError, 'error', 'notification-list-action-error')
useToastFeedback(notice, 'success', 'notification-list-notice')
const forbidden = ref<unknown>(null)
const items = ref<NotificationView[]>([])
const total = ref(0)
const page = ref(0)
const size = 20
const filter = ref<'all' | 'unread'>('all')
const unread = ref(0)
const selected = ref<NotificationView | null>(null)
const selectedTask = ref<AsyncTaskView | null>(null)
const taskLoading = ref(false)
const taskError = ref('')

const readableIds = computed(() => items.value.filter(canMarkRead).map((item) => item.id))
const failedCount = computed(() => items.value.filter((item) => item.status === 'SEND_FAILED').length)
const hasPrev = computed(() => page.value > 0)
const hasNext = computed(() => (page.value + 1) * size < total.value)

function taskStatusTone(status?: string | null): TagTone {
  if (status === 'SUCCEEDED') return 'green'
  if (status === 'FAILED' || status === 'CANCELLED') return 'red'
  if (status === 'PENDING') return 'orange'
  return 'blue'
}

function taskStatusText(status?: string | null): string {
  const labels: Record<string, string> = {
    PENDING: '等待资源调度',
    RUNNING: '任务执行中',
    SUCCEEDED: '任务已完成',
    FAILED: '任务执行失败',
    CANCELLED: '任务已取消',
  }
  return status ? labels[status] ?? status : '—'
}

function typeMeta(type?: string): RowMeta {
  switch (type) {
    case 'TASK_COMPLETED':
      return { icon: 'notification-task-success', cls: 'green' }
    case 'TASK_FAILED':
      return { icon: 'notification-error', cls: 'red' }
    case 'DATA_EXPORT_PROGRESS':
      return { icon: 'notification-export-queued', cls: 'purple' }
    case 'DATA_DELETION_PROGRESS':
      return { icon: 'notification-data-rights', cls: 'purple' }
    case 'SECURITY':
      return { icon: 'notification-permission', cls: 'ink' }
    case 'DATA_RIGHTS':
      return { icon: 'notification-data-rights', cls: 'ink' }
    default:
      return { icon: 'notification-system-update', cls: 'blue' }
  }
}

function statusTag(item: NotificationView): RowTag | null {
  if (item.status === 'SEND_FAILED') {
    return { text: '发送失败', tone: 'red' }
  }
  if (item.status === 'PENDING') {
    return { text: '待发送', tone: 'orange' }
  }
  if (item.status === 'EXPIRED') {
    return { text: '已过期', tone: 'gray' }
  }
  if (item.status === 'ARCHIVED') {
    return { text: '已归档', tone: 'gray' }
  }
  return null
}

function decorate(item: NotificationView): DecoratedRow {
  const readable = canMarkRead(item)
  return {
    item,
    meta: typeMeta(item.type),
    tag: statusTag(item),
    readable,
    blockReason: readable || item.status === 'READ' ? '' : markReadBlockedReason(item),
    source: dataRightsPathForNotice(item),
    taskish: TASKISH_TYPES.has(item.type),
  }
}

// 时间分桶：0 今天 / 1 昨天 / 2 更早，按本地日期
function dayBucketIndex(iso?: string | null): number {
  if (!iso) {
    return 2
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return 2
  }
  const startOf = (value: Date) => new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime()
  const diffDays = Math.round((startOf(new Date()) - startOf(date)) / 86400000)
  if (diffDays <= 0) {
    return 0
  }
  return diffDays === 1 ? 1 : 2
}

const groups = computed(() => {
  const buckets: { label: string; rows: DecoratedRow[] }[] = [
    { label: '今天', rows: [] },
    { label: '昨天', rows: [] },
    { label: '更早', rows: [] },
  ]
  for (const item of items.value) {
    buckets[dayBucketIndex(item.createdAt)]?.rows.push(decorate(item))
  }
  return buckets.filter((bucket) => bucket.rows.length > 0)
})

const selectedTag = computed(() => (selected.value ? statusTag(selected.value) : null))
const selectedSource = computed(() => (selected.value ? dataRightsPathForNotice(selected.value) : null))

async function refreshUnread(): Promise<void> {
  try {
    unread.value = await fetchUnreadCount()
  } catch {
    // 角标读取失败不打扰主流程，保持上次值
  }
}

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  forbidden.value = null
  try {
    const result = await listNotifications(page.value, size, filter.value === 'unread' ? 'DELIVERED' : undefined)
    items.value = result.items ?? []
    total.value = result.total ?? 0
  } catch (error) {
    items.value = []
    total.value = 0
    if (isForbidden(error)) {
      forbidden.value = error
      return
    }
    pageError.value = errorMessage(error, '通知读取失败')
  } finally {
    loading.value = false
  }
}

async function onRead(id: string): Promise<void> {
  const item = items.value.find((row) => row.id === id)
  if (!item) {
    return
  }
  if (!canMarkRead(item)) {
    formError.value = markReadBlockedReason(item)
    return
  }
  pending.value = `read-${id}`
  formError.value = ''
  notice.value = ''
  try {
    await markNotificationRead(id)
    notice.value = '已标为已读。'
    if (selected.value?.id === id) {
      selected.value = null
    }
    await Promise.all([load(), refreshUnread()])
  } catch (error) {
    formError.value = errorMessage(error, '标已读失败')
  } finally {
    pending.value = ''
  }
}

async function onReadBatch(): Promise<void> {
  if (!readableIds.value.length) {
    formError.value = '当前页没有已送达、尚未已读的通知。发送失败不能假装已发送。'
    return
  }
  pending.value = 'batch'
  formError.value = ''
  notice.value = ''
  try {
    await markNotificationsReadBatch(readableIds.value)
    notice.value = `已将 ${readableIds.value.length} 条已送达通知标为已读。`
    await Promise.all([load(), refreshUnread()])
  } catch (error) {
    formError.value = errorMessage(error, '批量已读失败')
  } finally {
    pending.value = ''
  }
}

async function onPrev(): Promise<void> {
  if (!hasPrev.value) {
    return
  }
  page.value -= 1
  await load()
}

async function onNext(): Promise<void> {
  if (!hasNext.value) {
    return
  }
  page.value += 1
  await load()
}

async function openItem(row: DecoratedRow): Promise<void> {
  if (row.item.type === 'PRODUCT_UPDATE' && row.source) {
    await openSource(row)
    return
  }
  if (!row.taskish) return
  selected.value = row.item
  selectedTask.value = null
  taskError.value = ''
  const taskId = row.item.eventId?.trim()
  if (!taskId) {
    taskError.value = '通知未携带任务编号，无法读取真实任务详情。'
    return
  }
  taskLoading.value = true
  try {
    selectedTask.value = await fetchTaskDetail(taskId)
  } catch (error) {
    taskError.value = errorMessage(error, '任务详情读取失败')
  } finally {
    taskLoading.value = false
  }
}

async function openSource(row: DecoratedRow): Promise<void> {
  if (!row.source) return
  if (canMarkRead(row.item)) {
    try { await markNotificationRead(row.item.id); await refreshUnread() } catch { /* The destination remains usable. */ }
  }
  await router.push(row.source)
}

async function setFilter(value: 'all' | 'unread'): Promise<void> {
  if (filter.value === value || loading.value) return
  filter.value = value
  page.value = 0
  await load()
}

function closeTaskDrawer(): void {
  selected.value = null
  selectedTask.value = null
  taskError.value = ''
}

onMounted(() => {
  void load()
  void refreshUnread()
})
</script>

<template>
      <ForbidState v-if="forbidden" :error="forbidden" />
    <section v-else class="page">
      <header class="page-head">
        <div>
          <h1 class="page-head__title">通知中心</h1>
          <p class="page-head__sub">重要通知及时处理，不错过任何关键信息。安全、授权和数据权利通知不可完全关闭。</p>
        </div>
        <div class="page-head__actions">
          <button
            class="btn btn--ghost"
            type="button"
            :disabled="!readableIds.length || pending === 'batch'"
            @click="onReadBatch"
          >
            <AppIcon name="check" :size="15" />
            {{ pending === 'batch' ? '正在标已读…' : '全部标记为已读' }}
          </button>
          <button class="icon-btn" type="button" title="重新读取" :disabled="loading" @click="load">
            <AppIcon name="refresh" :size="16" />
          </button>
        </div>
      </header>

      <AppBanner v-if="failedCount" tone="bad">
        有 {{ failedCount }} 条发送失败。业务状态不会因此回滚，本页也不会把它们画成已送达。
      </AppBanner>

      <div class="n-toolbar">
        <div class="seg" role="tablist" aria-label="通知筛选" :style="{ '--seg-index': filter === 'unread' ? 1 : 0 }">
          <button
            type="button"
            class="seg__item"
            :class="{ 'is-active': filter === 'all' }"
            role="tab"
            :aria-selected="filter === 'all'"
            :disabled="loading"
            @click="setFilter('all')"
          >
            全部
          </button>
          <button
            type="button"
            class="seg__item"
            :class="{ 'is-active': filter === 'unread' }"
            role="tab"
            :aria-selected="filter === 'unread'"
            :disabled="loading"
            @click="setFilter('unread')"
          >
            未读<template v-if="unread">（{{ unread > 99 ? '99+' : unread }}）</template>
          </button>
        </div>
        <span class="fine">共 {{ total }} 条 · 本页 {{ items.length }} 条</span>
      </div>

      <div v-if="loading && !items.length" class="n-list" aria-busy="true">
        <div v-for="n in 4" :key="n" class="n-row n-skel">
          <div class="bone" />
          <div class="bone bone--short" />
        </div>
      </div>

      <AppEmpty
        v-else-if="pageError && !items.length"
        text="通知读取失败"
        hint="失败不能当成没有通知，请重试。"
        icon="alert-circle"
      >
        <button class="btn btn--primary" type="button" @click="load">重新读取</button>
      </AppEmpty>

      <AppEmpty
        v-else-if="!groups.length"
        :text="filter === 'unread' ? '没有未读通知' : '还没有站内通知'"
        hint="空是合法状态，不会用假发送记录填充。"
        icon="bell"
      />

      <template v-else>
        <div v-for="group in groups" :key="`${filter}-${group.label}`" class="n-block">
          <p class="n-group">{{ group.label }}</p>
          <ul class="n-list">
            <li
              v-for="row in group.rows"
              :key="row.item.id"
              class="n-row"
              :class="{ 'is-unread': row.item.status === 'DELIVERED', 'is-clickable': row.taskish || row.item.type === 'PRODUCT_UPDATE' }"
              @click="openItem(row)"
            >
              <span class="n-icon" :class="`n-icon--${row.meta.cls}`">
                <JobProofIcon :name="row.meta.icon" :size="18" />
              </span>
              <div class="n-main">
                <div class="n-title">
                  <strong>{{ row.item.title }}</strong>
                  <AppTag v-if="row.tag" :tone="row.tag.tone">{{ row.tag.text }}</AppTag>
                </div>
                <p class="n-body">{{ row.item.body || '（无正文）' }}</p>
                <p class="fine">
                  {{ glossNotificationType(row.item.type) }} · {{ formatWhen(row.item.createdAt) }}
                  <template v-if="row.item.readAt"> · 已读于 {{ formatWhen(row.item.readAt) }}</template>
                  <template v-if="isProtectedNotice(row.item)"> · 安全/数据权利通知不可关闭</template>
                </p>
                <p v-if="row.blockReason" class="fine n-lock">{{ row.blockReason }}</p>
              </div>
              <div class="n-side" @click.stop>
                <span v-if="row.item.status === 'DELIVERED'" class="n-dot" title="未读" />
                <button
                  v-if="row.readable"
                  class="btn btn--ghost btn--sm"
                  type="button"
                  :disabled="pending === `read-${row.item.id}`"
                  @click="onRead(row.item.id)"
                >
                  {{ pending === `read-${row.item.id}` ? '标记中…' : '标为已读' }}
                </button>
                <button v-if="row.source" class="text-link" type="button" @click="openSource(row)">查看来源</button>
                <span v-if="row.taskish" class="fine n-hint">点击查看任务详情</span>
              </div>
            </li>
          </ul>
        </div>
      </template>

      <div v-if="total > size" class="pager">
        <button class="pager__page" type="button" :disabled="!hasPrev || loading" @click="onPrev">上一页</button>
        <span>第 {{ page + 1 }} 页</span>
        <button class="pager__page" type="button" :disabled="!hasNext || loading" @click="onNext">下一页</button>
      </div>

      <AppDrawer :open="!!selected" title="异步任务详情" :width="520" @close="closeTaskDrawer">
        <template v-if="selected">
          <div class="d-status">
            <AppTag v-if="selectedTask" :tone="taskStatusTone(selectedTask.status)">{{ taskStatusText(selectedTask.status) }}</AppTag>
            <AppTag v-else-if="selectedTag" :tone="selectedTag.tone">{{ selectedTag.text }}</AppTag>
            <AppTag v-else tone="blue">{{ glossNotificationStatus(selected.status) }}</AppTag>
          </div>

          <div v-if="taskLoading" class="d-loading" aria-busy="true">
            <div class="bone" />
            <div class="bone bone--short" />
          </div>
          <AppBanner v-else-if="taskError" tone="bad">{{ taskError }}</AppBanner>

          <template v-if="selectedTask">
            <section class="d-sec">
              <h4 class="d-sec__title">任务信息</h4>
              <dl class="kv">
                <dt>任务类型</dt>
                <dd>{{ selectedTask.taskType }}</dd>
                <dt>任务编号</dt>
                <dd class="kv__mono">{{ selectedTask.id }}</dd>
                <dt>创建时间</dt>
                <dd>{{ formatWhen(selectedTask.createdAt) }}</dd>
                <dt>更新时间</dt>
                <dd>{{ formatWhen(selectedTask.updatedAt) }}</dd>
              </dl>
            </section>

            <section class="d-sec">
              <h4 class="d-sec__title">输入与结果版本</h4>
              <dl class="kv">
                <dt>输入版本</dt>
                <dd class="kv__mono">{{ selectedTask.inputVersion || '—' }}</dd>
                <dt>结果版本</dt>
                <dd class="kv__mono">{{ selectedTask.resultVersion || '—' }}</dd>
              </dl>
            </section>

            <section class="d-sec">
              <h4 class="d-sec__title">处理时间线</h4>
              <ol class="d-timeline">
                <li><span />任务已提交 · {{ formatWhen(selectedTask.createdAt) }}</li>
                <li><span />{{ taskStatusText(selectedTask.status) }} · {{ formatWhen(selectedTask.updatedAt) }}</li>
              </ol>
            </section>

            <AppBanner v-if="selectedTask.failureReason" tone="bad">
              <strong>失败原因：</strong>{{ selectedTask.failureReason }}
            </AppBanner>
            <AppBanner v-if="selectedTask.status === 'FAILED'" tone="ok">
              业务数据未丢失；任务失败只影响本次结果产物。
            </AppBanner>
          </template>

          <section class="d-sec">
            <h4 class="d-sec__title">通知内容</h4>
            <p class="d-body">{{ selected.body || '（无正文）' }}</p>
          </section>
        </template>
        <template v-if="selected" #footer>
          <RouterLink v-if="selectedSource" class="btn btn--primary" :to="selectedSource" @click="selected = null">
            查看来源
          </RouterLink>
          <button
            v-if="canMarkRead(selected)"
            class="btn btn--ghost"
            type="button"
            :disabled="pending === `read-${selected.id}`"
            @click="onRead(selected.id)"
          >
            标记已读
          </button>
          <a
            v-if="selectedTask?.downloadAvailable && selectedTask.downloadUrl"
            class="btn btn--primary"
            :href="selectedTask.downloadUrl"
          >下载结果</a>
          <button class="btn btn--ghost" type="button" @click="closeTaskDrawer">关闭</button>
        </template>
      </AppDrawer>
    </section>
</template>

<style scoped>
.n-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.seg {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 999px;
}

.seg__item {
  min-height: 36px;
  padding: 6px 16px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.seg__item.is-active {
  background: var(--primary);
  color: #fff;
}

.n-block {
  display: grid;
  gap: 8px;
}

.n-group {
  font-size: 12px;
  color: var(--text-3);
}

.n-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
}

.n-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  background: var(--surface);
  border: 0;
  border-bottom: 1px solid var(--border);
  border-radius: 0;
  box-shadow: none;
}

.n-row:last-child { border-bottom: 0; }

.n-row.is-unread {
  box-shadow: inset 3px 0 0 var(--primary);
  background: #f8faff;
}

.n-row.is-clickable {
  cursor: pointer;
  transition: background-color 160ms ease;
}

.n-row.is-clickable:hover {
  background: var(--surface-2);
}

.n-skel {
  display: grid;
  gap: 8px;
}

.n-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.n-icon--blue {
  background: var(--primary-soft);
  color: var(--primary);
}

.n-icon--green {
  background: var(--success-soft);
  color: var(--success);
}

.n-icon--red {
  background: var(--danger-soft);
  color: var(--danger);
}

.n-icon--orange {
  background: var(--warning-soft);
  color: var(--warning);
}

.n-icon--purple {
  background: var(--purple-soft);
  color: var(--purple);
}

.n-icon--ink {
  background: #f3f4f6;
  color: var(--text-2);
}

.n-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 4px;
}

.n-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.n-title strong {
  font-size: 14px;
}

.n-body {
  font-size: 14px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.n-lock {
  color: var(--warning);
}

.n-side {
  display: grid;
  justify-items: end;
  gap: 8px;
  flex-shrink: 0;
}

.n-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
}

.n-hint {
  color: var(--text-3);
}

.d-status {
  margin-bottom: 16px;
}

.d-loading {
  display: grid;
  gap: 8px;
  padding: 16px 0;
}

.d-sec {
  display: grid;
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}

.d-sec__title {
  font-size: 13px;
  color: var(--text-2);
}

.kv {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.kv dt {
  color: var(--text-3);
}

.kv dd {
  margin: 0;
  word-break: break-word;
}

.kv__mono {
  font-family: var(--mono);
  font-size: 12px;
}

.d-timeline {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: var(--text-2);
}

.d-timeline li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.d-timeline span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 0 4px var(--primary-soft);
}

.d-body {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.7;
  word-break: break-word;
}

@media (max-width: 640px) {
  .seg { display: flex; width: 100%; border-radius: 8px; }
  .seg__item { flex: 1; min-height: 44px; padding-inline: 8px; border-radius: 6px; }
  .n-row { padding: 16px 12px; gap: 10px; }
  .n-icon { width: 32px; height: 32px; border-radius: 8px; }
  .n-body { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; white-space: normal; }
}
@media (prefers-reduced-motion: reduce) {
  .n-row.is-clickable, .seg__item { transition: none; }
}

/* Match the released notification surface: each item is its own card. */
.n-toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; }
.seg { position: relative; isolation: isolate; display: inline-flex; gap: 4px; padding: 3px; border: 1px solid var(--border); border-radius: 999px; background: var(--surface); }
.seg::before { position: absolute; z-index: 0; inset: 3px auto 3px 3px; width: calc((100% - 10px) / 2); border-radius: inherit; background: var(--primary); box-shadow: 0 5px 12px rgba(37,99,235,.18); content: ''; pointer-events: none; transform: translateX(calc(var(--seg-index) * (100% + 4px))); transition: transform var(--motion-slow) var(--motion-ease), box-shadow var(--motion-base) var(--motion-ease); }
.seg__item { position: relative; z-index: 1; padding: 5px 16px; border: 1px solid transparent; border-radius: 999px; color: var(--text-2); background: transparent; font-size: 13px; cursor: pointer; transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease); }
.seg__item.is-active { color: #fff; background: transparent; }
.seg__item:active { transform: scale(.97); }
.n-block { animation: notification-block-in var(--motion-base) var(--motion-ease-out) both; }
@keyframes notification-block-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
.n-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.n-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 16px; border: 1px solid var(--border); border-radius: var(--radius-l); background: var(--surface); box-shadow: var(--shadow-s); }
.n-row.is-unread { border-left: 3px solid var(--primary); }
.n-row.is-clickable { cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.n-row.is-clickable:hover { border-color: var(--primary); box-shadow: var(--shadow-l); }
.n-icon { width: 40px; height: 40px; flex-shrink: 0; display: grid; place-items: center; border-radius: 10px; }
.n-main { min-width: 0; flex: 1; display: grid; gap: 4px; }
.n-body { color: var(--text-2); font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
@media (max-width: 640px) {
  .seg { width: auto; border-radius: 999px; }
  .seg__item { min-height: 40px; padding-inline: 12px; }
  .n-row { padding: 14px 12px; gap: 10px; }
  .n-icon { width: 40px; height: 40px; border-radius: 10px; }
  .n-body { white-space: nowrap; }
}
@media (prefers-reduced-motion: reduce) {
  .seg::before, .seg__item { transition: none; }
  .seg__item:active { transform: none; }
  .n-block { animation: none; }
}
</style>
