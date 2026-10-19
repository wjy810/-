<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CalendarClock, CheckCircle2, Copy, Eye, FilePenLine, Plus, RefreshCw, RotateCcw, Search, Send } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import UiBadge, { type BadgeTone } from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiStatTile from '@/shared/ui/UiStatTile.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { useLoadState } from '@/shared/lib/useLoadState'
import { MODULE_LABELS, STATUS_LABELS, TYPE_LABELS, moduleLabel, statusLabel, typeLabel } from '../labels'
import { copyUpdate, fetchAdminOverview, listAdminUpdates, publishUpdate } from '../services/updatesApi'
import type { ReleaseSummary } from '../types'
import '../updates.css'

const router = useRouter()
const route = useRoute()
const LIMIT = 50
const pending = ref('')
const error = ref('')
const notice = ref('')
useToastFeedback(error, 'error', 'admin-changelog-list-error')
useToastFeedback(notice, 'success', 'admin-changelog-list-notice')
const q = ref(typeof route.query.q === 'string' ? route.query.q : '')
const status = ref(typeof route.query.status === 'string' ? route.query.status : '')
const type = ref(typeof route.query.type === 'string' ? route.query.type : '')
const module = ref(typeof route.query.module === 'string' ? route.query.module : '')
const copyTarget = ref<ReleaseSummary | null>(null)
const copyVersion = ref('')
const { data, error: loadError, loading, loaded, load } = useLoadState(() => Promise.all([
  listAdminUpdates({ q: q.value, status: status.value, type: type.value, module: module.value, size: LIMIT }),
  fetchAdminOverview(),
]))
const items = computed<ReleaseSummary[]>(() => data.value?.[0].items ?? [])
const truncated = computed(() => (data.value?.[0].total ?? 0) > items.value.length)
/** Null until loaded: the stat tiles show a dash, not zeros, when the overview is unavailable. */
const overview = computed(() => data.value?.[1] ?? null)
const DISTRIBUTION_LABELS: Record<string, string> = { PENDING: '等待发送', RUNNING: '发送中', SUCCEEDED: '已发送', CANCELLED: '已取消', FAILED: '发送失败' }
const statusOptions = [{ value: '', label: '全部状态' }, ...Object.keys(STATUS_LABELS).map(value => ({ value, label: statusLabel(value) }))]
const typeOptions = [{ value: '', label: '全部类型' }, ...Object.keys(TYPE_LABELS).map(value => ({ value, label: typeLabel(value) }))]
const moduleOptions = [{ value: '', label: '全部模块' }, ...Object.keys(MODULE_LABELS).map(value => ({ value, label: moduleLabel(value) }))]

function tone(value: string): BadgeTone {
  if (value === 'PUBLISHED') return 'success'
  if (value === 'SCHEDULED') return 'primary'
  if (value === 'DRAFT') return 'accent'
  return 'neutral'
}

async function applyFilters(): Promise<void> {
  q.value = q.value.trim()
  await router.replace({ query: { q: q.value || undefined, status: status.value || undefined,
    type: type.value || undefined, module: module.value || undefined } })
  await load()
}

async function onPublish(item: ReleaseSummary): Promise<void> {
  pending.value = `publish-${item.id}`; error.value = ''; notice.value = ''
  try { await publishUpdate(item.id, item.versionNo); notice.value = `${item.versionLabel} 已正式发布`; await load() }
  catch (reason) { error.value = errorMessage(reason, '发布失败') }
  finally { pending.value = '' }
}

function openCopy(item: ReleaseSummary): void { copyTarget.value = item; copyVersion.value = '' }

async function onCopy(): Promise<void> {
  const item = copyTarget.value; const version = copyVersion.value.trim()
  if (!item || !version) { error.value = '请输入新版本号'; return }
  pending.value = `copy-${item.id}`
  try { const copy = await copyUpdate(item.id, version); copyTarget.value = null; await router.push(`/admin/changelog/${copy.release.id}/edit`) }
  catch (reason) { error.value = errorMessage(reason, '复制版本失败') }
  finally { pending.value = '' }
}

onMounted(load)
</script>

<template>
  <section class="page-container admin-update-page">
    <UiPageHeader title="更新日志管理" description="编辑、预览、发布并追踪每个真实版本的更新记录。">
      <template #actions>
        <UiButton class="admin-update-head-action" variant="secondary" :icon="Eye" to="/updates">预览公开页</UiButton>
        <UiButton class="admin-update-head-action" :icon="Plus" to="/admin/changelog/new">新建版本</UiButton>
      </template>
    </UiPageHeader>
    <div class="admin-stats">
      <UiStatTile class="admin-stat" label="已发布" :value="overview?.published ?? '—'" hint="公开可见版本" :icon="CheckCircle2" tone="success" />
      <UiStatTile class="admin-stat" label="草稿" :value="overview?.drafts ?? '—'" hint="待完善版本" :icon="FilePenLine" tone="accent" />
      <UiStatTile class="admin-stat" label="定时发布" :value="overview?.scheduled ?? '—'" hint="已安排版本" :icon="CalendarClock" tone="primary" />
      <UiStatTile class="admin-stat" label="累计阅读" :value="overview?.reads ?? '—'" hint="登录用户显式阅读" :icon="Eye" tone="violet" />
    </div>
    <div class="toolbar">
      <div class="toolbar__grow"><UiInput v-model="q" :icon="Search" placeholder="搜索版本、标题或功能" aria-label="搜索版本、标题或功能" @keyup.enter="applyFilters" /></div>
      <UiSelect v-model="status" :options="statusOptions" aria-label="状态" placeholder="状态" />
      <UiSelect v-model="type" :options="typeOptions" aria-label="类型" placeholder="类型" />
      <UiSelect v-model="module" :options="moduleOptions" aria-label="模块" placeholder="模块" searchable />
      <UiButton variant="secondary" :icon="RefreshCw" :pending="loading" @click="applyFilters">应用筛选</UiButton>
    </div>
    <div class="table-wrap"><table class="data-table admin-update-table"><thead><tr><th>版本</th><th>标题</th><th>类型</th><th>状态</th><th>计划 / 发布时间</th><th>阅读</th><th>分发</th><th>操作</th></tr></thead><tbody><tr v-if="!loaded && loading"><td colspan="8">正在读取…</td></tr><tr v-else-if="loadError"><td colspan="8" class="admin-update-error">{{ loaded ? '刷新失败，下面是上次读取的内容：' : '读取失败：' }}{{ errorMessage(loadError) }}<UiButton class="admin-update-retry" variant="secondary" size="sm" :icon="RotateCcw" :pending="loading" @click="load">重试</UiButton></td></tr><tr v-if="loaded && !items.length" class="admin-update-empty"><td colspan="8"><UiEmptyState size="sm" illustration="empty-updates" title="还没有更新版本" description="新建一个草稿，写好内容后可以预览、定时或立即发布。"><UiButton size="sm" :icon="Plus" to="/admin/changelog/new">新建版本</UiButton></UiEmptyState></td></tr><tr v-for="item in items" :key="item.id"><td>{{ item.versionLabel }}</td><td><strong>{{ item.title }}</strong><div class="fine">{{ item.summary }}</div></td><td><UiBadge tone="primary">{{ typeLabel(item.releaseType) }}</UiBadge></td><td><UiBadge :tone="tone(item.status)">{{ statusLabel(item.status) }}</UiBadge></td><td>{{ formatWhen(item.scheduledAt || item.publishedAt || item.updatedAt) }}</td><td>{{ item.readCount }}</td><td>{{ item.distributionStatus ? DISTRIBUTION_LABELS[item.distributionStatus] ?? item.distributionStatus : '—' }}</td><td><div class="admin-update-actions"><UiButton variant="secondary" size="sm" :icon="FilePenLine" :to="`/admin/changelog/${item.id}/edit`">编辑</UiButton><UiButton v-if="['PUBLISHED','ARCHIVED'].includes(item.status)" variant="secondary" size="sm" :icon="Eye" :to="`/updates/${item.slug}`">查看</UiButton><UiButton variant="secondary" size="sm" :icon="Copy" :disabled="!!pending" @click="openCopy(item)">复制</UiButton><UiButton v-if="item.status==='DRAFT'" size="sm" :icon="Send" :pending="pending === `publish-${item.id}`" :disabled="!!pending && pending !== `publish-${item.id}`" @click="onPublish(item)">发布</UiButton></div></td></tr></tbody></table></div>
    <p v-if="truncated" class="fine">只显示最近 {{ LIMIT }} 个版本，更早的版本请用上方搜索或筛选查找。</p>
  </section>
  <UiDialog :open="!!copyTarget" title="复制更新版本" :width="500" @close="copyTarget=null">
    <div class="editor-dialog"><p>将复制 {{ copyTarget?.versionLabel }} 的结构化内容并创建新草稿，历史统计和发布状态不会复制。</p><UiField id="copy-version" label="新版本号"><UiInput id="copy-version" v-model="copyVersion" placeholder="例如 v1.2.0" @keyup.enter="onCopy" /></UiField></div>
    <template #footer><UiButton variant="secondary" @click="copyTarget=null">取消</UiButton><UiButton :pending="pending.startsWith('copy-')" @click="onCopy">{{ pending.startsWith('copy-') ? '正在复制…' : '创建副本' }}</UiButton></template>
  </UiDialog>
</template>

<style scoped>
.admin-update-page {
  display: grid;
  gap: var(--space-6);
  align-content: start;
  max-width: 1500px;
  min-width: 0;
}
.admin-update-error {
  color: var(--color-danger-text);
}
.admin-update-error .admin-update-retry {
  margin-left: 8px;
}
.admin-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) repeat(3, minmax(150px, 190px)) auto;
  gap: 10px;
  align-items: center;
}
.toolbar__grow,
.table-wrap {
  min-width: 0;
}
.table-wrap {
  max-width: 100%;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--surface-1);
}
.admin-update-table {
  min-width: 980px;
}
.admin-update-table tbody tr {
  animation: admin-update-results-in var(--dur-base) var(--ease-out) both;
}
.admin-update-table tbody tr:nth-child(2) { animation-delay: 25ms; }
.admin-update-table tbody tr:nth-child(3) { animation-delay: 50ms; }
.admin-update-table tbody tr:nth-child(4) { animation-delay: 75ms; }
.admin-update-table tbody tr:nth-child(n + 5) { animation-delay: 100ms; }
.admin-stats .admin-stat {
  animation: admin-update-stat-in var(--dur-base) var(--ease-out) both;
}
.admin-stats .admin-stat:nth-child(2) { animation-delay: 35ms; }
.admin-stats .admin-stat:nth-child(3) { animation-delay: 70ms; }
.admin-stats .admin-stat:nth-child(4) { animation-delay: 105ms; }
@keyframes admin-update-results-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes admin-update-stat-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.editor-dialog { display: grid; gap: 16px; }
.editor-dialog > p { margin: 0; color: var(--text-secondary); line-height: 1.7; }

@media (max-width: 1050px) {
  .admin-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .toolbar {
    grid-template-columns: minmax(0, 1fr) minmax(150px, 190px);
  }
}

@media (max-width: 700px) {
  .admin-update-page {
    padding: 20px 14px 40px;
  }
  .admin-update-page :deep(.ui-page-header__actions) {
    width: 100%;
  }
  .admin-update-page .admin-update-head-action {
    flex: 1;
  }
  .admin-update-page .admin-update-head-action,
  .toolbar > .ui-btn,
  .toolbar :deep(.ui-select-trigger) {
    min-height: 44px;
  }
  .toolbar {
    grid-template-columns: 1fr;
  }
  .admin-stats {
    gap: 10px;
  }
  .admin-stats .admin-stat {
    padding: 14px;
  }
  .table-wrap {
    scrollbar-width: thin;
  }
}
@media (prefers-reduced-motion: reduce) {
  .admin-update-table tbody tr,
  .admin-stats .admin-stat { animation: none; }
}
</style>
