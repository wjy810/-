<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Copy, Eye, FilePenLine, Plus, RefreshCw, Send } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import { MODULE_LABELS, STATUS_LABELS, TYPE_LABELS, moduleLabel, statusLabel, typeLabel } from '../labels'
import { copyUpdate, fetchAdminOverview, listAdminUpdates, publishUpdate } from '../services/updatesApi'
import type { AdminOverview, ReleaseSummary } from '../types'
import '../updates.css'

const router = useRouter()
const route = useRoute()
const loading = ref(true)
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
const items = ref<ReleaseSummary[]>([])
const resultRevision = ref(0)
const overview = ref<AdminOverview>({ published: 0, drafts: 0, scheduled: 0, reads: 0 })
const statusOptions = [{ value: '', label: '全部状态' }, ...Object.keys(STATUS_LABELS).map(value => ({ value, label: statusLabel(value) }))]
const typeOptions = [{ value: '', label: '全部类型' }, ...Object.keys(TYPE_LABELS).map(value => ({ value, label: typeLabel(value) }))]
const moduleOptions = [{ value: '', label: '全部模块' }, ...Object.keys(MODULE_LABELS).map(value => ({ value, label: moduleLabel(value) }))]

function tone(value: string): 'green' | 'orange' | 'gray' | 'blue' {
  if (value === 'PUBLISHED') return 'green'
  if (value === 'SCHEDULED') return 'blue'
  if (value === 'DRAFT') return 'orange'
  return 'gray'
}

async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try {
    const [page, stats] = await Promise.all([listAdminUpdates({ q: q.value, status: status.value, type: type.value, module: module.value, size: 50 }), fetchAdminOverview()])
    items.value = page.items; overview.value = stats
  } catch (reason) { error.value = errorMessage(reason, '更新日志管理数据读取失败') }
  finally { resultRevision.value += 1; loading.value = false }
}

async function applyFilters(): Promise<void> {
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
      <section class="page admin-update-page">
      <header class="page-head"><div><h1 class="page-head__title">更新日志管理</h1><p class="page-head__sub">编辑、预览、发布并追踪每个真实版本的更新记录。</p></div><div class="page-head__actions"><RouterLink class="btn btn--ghost" to="/updates"><Eye :size="16" />预览公开页</RouterLink><RouterLink class="btn btn--primary" to="/admin/changelog/new"><Plus :size="17" />新建版本</RouterLink></div></header>
      <div class="stats admin-stats"><article class="stat"><span class="stat__label">已发布</span><strong class="stat__value">{{ overview.published }}</strong><span class="fine">公开可见版本</span></article><article class="stat"><span class="stat__label">草稿</span><strong class="stat__value">{{ overview.drafts }}</strong><span class="fine">待完善版本</span></article><article class="stat"><span class="stat__label">定时发布</span><strong class="stat__value">{{ overview.scheduled }}</strong><span class="fine">已安排版本</span></article><article class="stat"><span class="stat__label">累计阅读</span><strong class="stat__value">{{ overview.reads }}</strong><span class="fine">登录用户显式阅读</span></article></div>
      <div class="toolbar"><div class="toolbar__grow"><input v-model.trim="q" class="input" placeholder="搜索版本、标题或功能" @keyup.enter="applyFilters" /></div><AppSelect v-model="status" :options="statusOptions" aria-label="状态" /><AppSelect v-model="type" :options="typeOptions" aria-label="类型" /><AppSelect v-model="module" :options="moduleOptions" aria-label="模块" searchable /><button class="btn btn--ghost" :disabled="loading" @click="applyFilters"><RefreshCw :size="15" />应用筛选</button></div>
      <div class="table-wrap"><table class="tbl admin-update-table"><thead><tr><th>版本</th><th>标题</th><th>类型</th><th>状态</th><th>计划 / 发布时间</th><th>阅读</th><th>分发</th><th>操作</th></tr></thead><tbody :key="loading ? 'loading' : `result-${resultRevision}`"><tr v-if="loading"><td colspan="8">正在读取…</td></tr><tr v-else-if="!items.length"><td colspan="8">暂无真实更新版本，请新建草稿。</td></tr><tr v-for="item in items" :key="item.id"><td>{{ item.versionLabel }}</td><td><strong>{{ item.title }}</strong><div class="fine">{{ item.summary }}</div></td><td><AppTag tone="blue">{{ typeLabel(item.releaseType) }}</AppTag></td><td><AppTag :tone="tone(item.status)">{{ statusLabel(item.status) }}</AppTag></td><td>{{ formatWhen(item.scheduledAt || item.publishedAt || item.updatedAt) }}</td><td>{{ item.readCount }}</td><td>{{ item.distributionStatus || '—' }}</td><td><div class="admin-update-actions"><RouterLink class="btn btn--ghost btn--sm" :to="`/admin/changelog/${item.id}/edit`"><FilePenLine :size="14" />编辑</RouterLink><RouterLink v-if="['PUBLISHED','ARCHIVED'].includes(item.status)" class="btn btn--ghost btn--sm" :to="`/updates/${item.slug}`"><Eye :size="14" />查看</RouterLink><button class="btn btn--ghost btn--sm" :disabled="!!pending" @click="openCopy(item)"><Copy :size="14" />复制</button><button v-if="item.status==='DRAFT'" class="btn btn--primary btn--sm" :disabled="!!pending" @click="onPublish(item)"><Send :size="14" />发布</button></div></td></tr></tbody></table></div>
    </section>
  <AppModal :open="!!copyTarget" title="复制更新版本" :width="500" @close="copyTarget=null">
    <div class="editor-dialog"><p>将复制 {{ copyTarget?.versionLabel }} 的结构化内容并创建新草稿，历史统计和发布状态不会复制。</p><label class="editor-field"><span>新版本号</span><input v-model.trim="copyVersion" placeholder="例如 v1.2.0" @keyup.enter="onCopy" /></label></div>
    <template #footer><button class="btn btn--ghost" type="button" @click="copyTarget=null">取消</button><button class="btn btn--primary" type="button" :disabled="pending.startsWith('copy-')" @click="onCopy">{{ pending.startsWith('copy-') ? '正在复制…' : '创建副本' }}</button></template>
  </AppModal>
</template>

<style scoped>
.admin-update-page {
  min-width: 0;
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
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
}
.admin-update-table {
  min-width: 980px;
}
.admin-update-table tbody tr {
  animation: admin-update-results-in var(--motion-base) var(--motion-ease-out) both;
}
.admin-update-table tbody tr:nth-child(2) { animation-delay: 25ms; }
.admin-update-table tbody tr:nth-child(3) { animation-delay: 50ms; }
.admin-update-table tbody tr:nth-child(4) { animation-delay: 75ms; }
.admin-update-table tbody tr:nth-child(n + 5) { animation-delay: 100ms; }
.admin-stats .stat {
  animation: admin-update-stat-in var(--motion-base) var(--motion-ease-out) both;
}
.admin-stats .stat:nth-child(2) { animation-delay: 35ms; }
.admin-stats .stat:nth-child(3) { animation-delay: 70ms; }
.admin-stats .stat:nth-child(4) { animation-delay: 105ms; }
@keyframes admin-update-results-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes admin-update-stat-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.editor-dialog { display: grid; gap: 16px; }
.editor-dialog > p { margin: 0; color: #5d6e88; line-height: 1.7; }

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
  .page-head__actions,
  .page-head__actions > .btn {
    width: 100%;
  }
  .page-head__actions > .btn,
  .toolbar > .btn,
  .toolbar :deep(.app-select__trigger) {
    min-height: 44px;
  }
  .page-head__actions > .btn {
    flex: 1;
  }
  .toolbar {
    grid-template-columns: 1fr;
  }
  .admin-stats {
    gap: 10px;
  }
  .admin-stats .stat {
    padding: 14px;
  }
  .table-wrap {
    scrollbar-width: thin;
  }
}
@media (prefers-reduced-motion: reduce) {
  .admin-update-table tbody tr,
  .admin-stats .stat { animation: none; }
}
</style>
