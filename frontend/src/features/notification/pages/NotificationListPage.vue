<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Check, RefreshCw, Settings } from 'lucide-vue-next'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiBadge, { type BadgeTone } from '@/shared/ui/UiBadge.vue'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDrawer from '@/shared/ui/UiDrawer.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiPagination from '@/shared/ui/UiPagination.vue'
import UiSegmented, { type SegmentItem } from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { pageCountOf } from '@/shared/ui/pagination'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatRelative, formatWhen } from '@/shared/lib/datetime'
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

type RowMeta = {
  icon: JobProofIconName
  cls: 'blue' | 'green' | 'red' | 'orange' | 'purple' | 'ink'
}

type RowTag = {
  text: string
  tone: BadgeTone
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
const TASKISH_TYPES = new Set(['TASK_COMPLETED', 'TASK_FAILED'])

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
const pageCount = computed(() => pageCountOf(total.value, size))
const filterItems = computed<SegmentItem<'all' | 'unread'>[]>(() => [
  { value: 'all', label: '全部' },
  { value: 'unread', label: '未读', count: unread.value ? Math.min(unread.value, 99) : null },
])

function taskStatusTone(status?: string | null): BadgeTone {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'danger'
  if (status === 'PENDING') return 'accent'
  return 'primary'
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
    case 'APPLICATION_ACTIVITY':
      return { icon: 'notification-task-success', cls: 'green' }
    case 'REMINDER':
      return { icon: 'notification-interview-reminder', cls: 'purple' }
    case 'MEMBERSHIP':
      return { icon: 'notification-announcement', cls: 'ink' }
    case 'WEEKLY_DIGEST':
      return { icon: 'notification-planning-reminder', cls: 'blue' }
    default:
      return { icon: 'notification-system-update', cls: 'blue' }
  }
}

function statusTag(item: NotificationView): RowTag | null {
  if (item.status === 'SEND_FAILED') {
    return { text: '发送失败', tone: 'danger' }
  }
  if (item.status === 'PENDING') {
    return { text: '待发送', tone: 'accent' }
  }
  if (item.status === 'EXPIRED') {
    return { text: '已过期', tone: 'neutral' }
  }
  if (item.status === 'ARCHIVED') {
    return { text: '已归档', tone: 'neutral' }
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

// 时间分桶：0 今天 / 1 昨天 / 2 更早；与时间文字一样按北京时间（Asia/Shanghai）分日
const shanghaiDay = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' })
function dayBucketIndex(iso?: string | null): number {
  if (!iso) {
    return 2
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return 2
  }
  const day = (value: Date) => Date.parse(shanghaiDay.format(value))
  const diffDays = Math.round((day(new Date()) - day(date)) / 86400000)
  return diffDays <= 0 ? 0 : diffDays === 1 ? 1 : 2
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

async function goTo(target: number): Promise<void> {
  if (target === page.value || loading.value) return
  page.value = target
  await load()
}

/** Task notifications are keyed "<taskId>:<status>"; the drawer needs the task id alone. */
function taskIdOf(item: NotificationView): string {
  if (item.type !== 'TASK_COMPLETED' && item.type !== 'TASK_FAILED') return ''
  return (item.eventId ?? '').split(':')[0]?.trim() ?? ''
}

/** A notice with somewhere to go opens it; a task notice without one opens its details. */
async function openItem(row: DecoratedRow): Promise<void> {
  if (row.source) {
    await openSource(row)
    return
  }
  if (row.taskish) await openDetails(row)
}

async function openDetails(row: DecoratedRow): Promise<void> {
  selected.value = row.item
  selectedTask.value = null
  taskError.value = ''
  const taskId = taskIdOf(row.item)
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
  <section v-else class="page-container notifications">
    <UiPageHeader title="通知中心" description="任务进度、数据导出和账号安全等消息会汇总在这里。">
      <template #actions>
        <UiButton variant="secondary" :icon="Check" :disabled="!readableIds.length" :pending="pending === 'batch'" @click="onReadBatch">
          {{ pending === 'batch' ? '正在标已读…' : '全部标记为已读' }}
        </UiButton>
        <UiIconButton label="通知设置" :icon="Settings" variant="secondary" to="/account/notifications" />
        <UiIconButton label="重新读取" :icon="RefreshCw" variant="secondary" :pending="loading && loaded" @click="load" />
      </template>
    </UiPageHeader>

    <UiBanner v-if="failedCount" tone="danger">
      本页有 {{ failedCount }} 条通知发送失败。这不影响对应的业务结果，可到相关页面查看最新状态。
    </UiBanner>

    <div class="n-toolbar">
      <UiSegmented :model-value="filter" :items="filterItems" aria-label="通知筛选" @update:model-value="setFilter" />
      <span v-if="loaded" class="n-count">共 {{ total }} 条 · 本页 {{ items.length }} 条</span>
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
            <UiSkeleton circle width="40px" height="40px" />
            <div class="n-skel__lines"><UiSkeleton width="40%" /><UiSkeleton width="72%" height="12px" /></div>
          </div>
        </div>
      </template>
      <template #empty>
        <UiEmptyState
          :title="filter === 'unread' ? '没有未读通知' : '还没有站内通知'"
          :description="filter === 'unread' ? '所有通知都已读。' : '任务完成、数据导出和账号安全等消息会出现在这里。'"
          illustration="empty-notification"
          size="sm"
        />
      </template>
      <div v-for="group in groups" :key="`${filter}-${group.label}`" class="n-block">
        <p class="n-group">{{ group.label }}</p>
        <ul class="n-list">
          <li
            v-for="row in group.rows"
            :key="row.item.id"
            class="n-row"
            :class="{ 'is-unread': row.item.status === 'DELIVERED', 'is-clickable': Boolean(row.source) || row.taskish }"
            :tabindex="row.source || row.taskish ? 0 : undefined"
            @click="openItem(row)"
            @keydown.enter.self="openItem(row)"
          >
            <span class="n-icon" :class="`n-icon--${row.meta.cls}`">
              <JobProofIcon :name="row.meta.icon" :size="18" />
            </span>
            <div class="n-main">
              <div class="n-title">
                <strong>{{ row.item.title }}</strong>
                <UiBadge v-if="row.tag" :tone="row.tag.tone" size="sm">{{ row.tag.text }}</UiBadge>
              </div>
              <p v-if="row.item.body" class="n-body">{{ row.item.body }}</p>
              <p class="n-meta">
                <span>{{ glossNotificationType(row.item.type) }}</span>
                <time :datetime="row.item.createdAt ?? undefined" :title="formatWhen(row.item.createdAt)">{{ formatRelative(row.item.createdAt) }}</time>
                <span v-if="row.item.readAt">已读</span>
              </p>
              <p v-if="row.blockReason" class="n-lock">{{ row.blockReason }}</p>
            </div>
            <div class="n-side" @click.stop>
              <span v-if="row.item.status === 'DELIVERED'" class="n-dot" title="未读" />
              <div class="n-actions">
                <button
                  v-if="row.readable"
                  class="n-action"
                  type="button"
                  title="标为已读"
                  aria-label="标为已读"
                  :disabled="pending === `read-${row.item.id}`"
                  @click="onRead(row.item.id)"
                >
                  <AppIcon name="check" :size="15" />
                </button>
                <button v-if="row.taskish && row.source" class="n-action" type="button" title="任务详情" aria-label="任务详情" @click="openDetails(row)">
                  <AppIcon name="info" :size="15" />
                </button>
              </div>
              <AppIcon v-if="row.source || row.taskish" class="n-go" name="chevron-right" :size="16" />
            </div>
          </li>
        </ul>
      </div>
    </PageState>

    <UiPagination v-if="loaded && total > size" :page="page" :page-count="pageCount" :disabled="loading" aria-label="通知分页" @update:page="goTo" />

    <UiDrawer :open="!!selected" title="任务详情" :width="520" @close="closeTaskDrawer">
      <template v-if="selected">
        <div class="d-status">
          <UiBadge v-if="selectedTask" :tone="taskStatusTone(selectedTask.status)">{{ taskStatusText(selectedTask.status) }}</UiBadge>
          <UiBadge v-else-if="selectedTag" :tone="selectedTag.tone">{{ selectedTag.text }}</UiBadge>
          <UiBadge v-else tone="primary">{{ glossNotificationStatus(selected.status) }}</UiBadge>
        </div>

        <div v-if="taskLoading" class="d-loading" aria-busy="true">
          <UiSkeleton />
          <UiSkeleton width="55%" />
        </div>
        <UiBanner v-else-if="taskError" tone="danger">{{ taskError }}</UiBanner>

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

          <UiBanner v-if="selectedTask.failureReason" tone="danger">
            <strong>失败原因：</strong>{{ selectedTask.failureReason }}
          </UiBanner>
          <UiBanner v-if="selectedTask.status === 'FAILED'" tone="success">
            你已保存的资料和简历不受影响，只是这次任务没有产出结果，可以回到对应页面重新发起。
          </UiBanner>

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
        <UiButton v-if="selectedSource" :to="selectedSource" @click="selected = null">查看来源</UiButton>
        <UiButton v-if="canMarkRead(selected)" variant="secondary" :pending="pending === `read-${selected.id}`" @click="onRead(selected.id)">
          标记已读
        </UiButton>
        <UiButton v-if="selectedTask?.downloadAvailable && selectedTask.downloadUrl" :href="selectedTask.downloadUrl">下载结果</UiButton>
        <UiButton variant="secondary" @click="closeTaskDrawer">关闭</UiButton>
      </template>
    </UiDrawer>
  </section>
</template>

<style scoped>
.notifications {
  display: grid;
  gap: var(--space-6);
  align-content: start;
}

.n-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.n-count {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.n-block {
  display: grid;
  gap: 8px;
  animation: notification-block-in var(--dur-base) var(--ease-out) both;
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
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--surface-1);
}

.n-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  background: var(--surface-1);
  border: 0;
  border-bottom: 1px solid var(--border-default);
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
  align-items: center;
}

.n-skel__lines {
  flex: 1;
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
  background: var(--color-success-soft);
  color: var(--color-success);
}

.n-icon--red {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.n-icon--orange {
  background: var(--color-warning-soft);
  color: var(--color-warning);
}

.n-icon--purple {
  background: var(--color-primary-soft);
  color: var(--violet-400);
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
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.n-lock {
  color: var(--color-warning-text);
  font-size: var(--fs-xs);
}

.n-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.n-side {
  align-self: center;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.n-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
}

.n-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease-out);
}

.n-row:hover .n-actions,
.n-row:focus-within .n-actions {
  opacity: 1;
}

.n-action {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--surface-1);
  color: var(--text-secondary);
  cursor: pointer;
}

.n-action:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary-text);
}

.n-go {
  color: var(--text-tertiary);
  transition: transform var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.n-row.is-clickable:hover .n-go {
  color: var(--color-primary-text);
  transform: translateX(2px);
}

.n-row:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

@media (hover: none) {
  .n-actions {
    opacity: 1;
  }
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
  border-bottom: 1px solid var(--border-default);
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
  font-family: var(--font-mono);
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

@keyframes notification-block-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 640px) {
  .n-row { padding: 14px 12px; gap: 10px; }
}

@media (prefers-reduced-motion: reduce) {
  .n-row.is-clickable { transition: none; }
  .n-block { animation: none; }
}
</style>
