<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppDrawer from '@/shared/ui/AppDrawer.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import PageState from '@/shared/ui/PageState.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import type { JobProofIconName } from '@/shared/ui/jobProofIcons'
import {
  canMarkRead,
  dataRightsPathForNotice,
  glossNotificationStatus,
  glossNotificationType,
  glossTaskType,
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

// 任务类通知：点击可打开任务详情抽屉
const TASKISH_TYPES = new Set(['TASK_COMPLETED', 'TASK_FAILED', 'DATA_EXPORT_PROGRESS', 'DATA_DELETION_PROGRESS'])

const router = useRouter()
const pending = ref('')
const formError = ref('')
const notice = ref('')
useToastFeedback(formError, 'error', 'notification-list-action-error')
useToastFeedback(notice, 'success', 'notification-list-notice')
const page = ref(0)
const size = 20
const filter = ref<'all' | 'unread'>('all')
const { data, error: loadError, loading, loaded, load } = useLoadState(() =>
  listNotifications(page.value, size, filter.value === 'unread' ? 'DELIVERED' : undefined))
const forbidden = computed(() => (isForbidden(loadError.value) ? loadError.value : null))
const items = computed<NotificationView[]>(() => data.value?.items ?? [])
const total = computed(() => data.value?.total ?? 0)
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
    PENDING: '排队中',
    RUNNING: '处理中',
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
    formError.value = '当前页没有未读通知。'
    return
  }
  pending.value = 'batch'
  formError.value = ''
  notice.value = ''
  try {
    await markNotificationsReadBatch(readableIds.value)
    notice.value = `已将 ${readableIds.value.length} 条通知标为已读。`
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
    taskError.value = '这条通知没有关联的任务，无法查看任务详情。'
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
          <p class="page-head__sub">任务进度、数据导出和账号安全等消息会汇总在这里。</p>
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
        本页有 {{ failedCount }} 条通知发送失败。这不影响对应的业务结果，可到相关页面查看最新状态。
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
        <span v-if="loaded" class="fine">共 {{ total }} 条 · 本页 {{ items.length }} 条</span>
      </div>

      <PageState
        class="n-groups"
        :loading="loading"
        :error="loadError"
        :loaded="loaded"
        :empty="!groups.length"
        error-title="通知读取失败"
        @retry="load"
      >
        <template #skeleton>
          <div class="n-list" aria-busy="true">
            <div v-for="n in 4" :key="n" class="n-row n-skel">
              <div class="bone" />
              <div class="bone bone--short" />
            </div>
          </div>
        </template>
        <template #empty>
          <AppEmpty
            :text="filter === 'unread' ? '没有未读通知' : '还没有站内通知'"
            :hint="filter === 'unread' ? '所有通知都已读。' : '任务完成、数据导出和账号安全等消息会出现在这里。'"
            icon="bell"
          />
        </template>
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
      </PageState>

      <div v-if="loaded && total > size" class="pager">
        <button class="pager__page" type="button" :disabled="!hasPrev || loading" @click="onPrev">上一页</button>
        <span>第 {{ page + 1 }} 页</span>
        <button class="pager__page" type="button" :disabled="!hasNext || loading" @click="onNext">下一页</button>
      </div>

      <AppDrawer :open="!!selected" title="任务详情" :width="520" @close="closeTaskDrawer">
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
                <dd>{{ glossTaskType(selectedTask.taskType) }}</dd>
                <dt>创建时间</dt>
                <dd>{{ formatWhen(selectedTask.createdAt) }}</dd>
                <dt>更新时间</dt>
                <dd>{{ formatWhen(selectedTask.updatedAt) }}</dd>
              </dl>
            </section>

            <section class="d-sec">
              <h4 class="d-sec__title">处理进度</h4>
              <ol class="d-timeline">
                <li><span />任务已提交 · {{ formatWhen(selectedTask.createdAt) }}</li>
                <li><span />{{ taskStatusText(selectedTask.status) }} · {{ formatWhen(selectedTask.updatedAt) }}</li>
              </ol>
            </section>

            <AppBanner v-if="selectedTask.failureReason" tone="bad">
              <strong>失败原因：</strong>{{ selectedTask.failureReason }}
            </AppBanner>
            <AppBanner v-if="selectedTask.status === 'FAILED'" tone="ok">
              你已保存的资料和简历不受影响，只是这次任务没有产出结果，可以回到对应页面重新发起。
            </AppBanner>

            <details class="d-tech">
              <summary>联系客服时可提供</summary>
              <p>任务编号 <code class="kv__mono">{{ selectedTask.id }}</code></p>
            </details>
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
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.seg__item.is-active {
  background: var(--color-primary);
  color: var(--text-on-primary);
}

.n-block {
  display: grid;
  gap: 8px;
}

.n-group {
  font-size: 12px;
  color: var(--text-tertiary);
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
  box-shadow: inset 3px 0 0 var(--color-primary);
  background: var(--surface-1);
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
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.n-icon--green {
  background: var(--success-soft);
  color: var(--color-success);
}

.n-icon--red {
  background: var(--danger-soft);
  color: var(--color-danger);
}

.n-icon--orange {
  background: var(--warning-soft);
  color: var(--color-warning);
}

.n-icon--purple {
  background: var(--purple-soft);
  color: var(--purple);
}

.n-icon--ink {
  background: var(--surface-2);
  color: var(--text-secondary);
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
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.n-lock {
  color: var(--color-warning);
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
  background: var(--color-primary);
}

.n-hint {
  color: var(--text-tertiary);
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
  color: var(--text-secondary);
}

.kv {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.kv dt {
  color: var(--text-tertiary);
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
  color: var(--text-secondary);
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
  background: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-primary-soft);
}

.n-groups {
  display: grid;
  gap: 16px;
}

.d-tech {
  padding: 14px 0 0;
  font-size: 12px;
  color: var(--text-tertiary);
}

.d-tech summary {
  cursor: pointer;
}

.d-tech p {
  margin-top: 6px;
  user-select: all;
}

.d-body {
  font-size: 13px;
  color: var(--text-secondary);
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
.seg::before { position: absolute; z-index: 0; inset: 3px auto 3px 3px; width: calc((100% - 10px) / 2); border-radius: inherit; background: var(--color-primary); box-shadow: 0 5px 12px color-mix(in srgb, var(--color-primary) 18%, transparent); content: ''; pointer-events: none; transform: translateX(calc(var(--seg-index) * (100% + 4px))); transition: transform var(--motion-slow) var(--motion-ease), box-shadow var(--motion-base) var(--motion-ease); }
.seg__item { position: relative; z-index: 1; padding: 5px 16px; border: 1px solid transparent; border-radius: 999px; color: var(--text-secondary); background: transparent; font-size: 13px; cursor: pointer; transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease); }
.seg__item.is-active { color: var(--text-on-primary); background: transparent; }
.seg__item:active { transform: scale(.97); }
.n-block { animation: notification-block-in var(--motion-base) var(--motion-ease-out) both; }
@keyframes notification-block-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
.n-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.n-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px 16px; border: 1px solid var(--border); border-radius: var(--radius-l); background: var(--surface); box-shadow: var(--shadow-s); }
.n-row.is-unread { border-left: 3px solid var(--color-primary); }
.n-row.is-clickable { cursor: pointer; transition: border-color .15s, box-shadow .15s; }
.n-row.is-clickable:hover { border-color: var(--color-primary); box-shadow: var(--shadow-l); }
.n-icon { width: 40px; height: 40px; flex-shrink: 0; display: grid; place-items: center; border-radius: 10px; }
.n-main { min-width: 0; flex: 1; display: grid; gap: 4px; }
.n-body { color: var(--text-secondary); font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
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
