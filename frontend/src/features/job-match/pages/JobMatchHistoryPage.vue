<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChevronLeft, ChevronRight, LoaderCircle, Search, Trash2 } from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import PageState from '@/shared/ui/PageState.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import { archiveJobMatch, fetchJobMatchHistory } from '../services/jobMatchApi'
import type { JobMatchSummary } from '../types'
import { confidencePresentation } from '../utils/reportPresentation'
import { statusLabel, statusTone, summaryRoute } from '../utils/stage'
import '../job-match.css'

const router = useRouter()
const keyword = ref('')
const status = ref('')
const page = ref(0)
let searchTimer: number | undefined

const { data, error, loading, loaded, load } = useLoadState(async () => {
  const result = await fetchJobMatchHistory({ status: status.value, query: keyword.value.trim(), page: page.value, size: 20 })
  if (page.value >= result.totalPages && result.totalPages > 0) page.value = result.totalPages - 1
  return result
})
const items = computed(() => data.value?.items ?? [])
const total = computed(() => data.value?.total ?? 0)
const totalPages = computed(() => data.value?.totalPages ?? 0)
const filtered = computed(() => Boolean(keyword.value.trim() || status.value))

/** Only a finished analysis has a score and a confidence worth showing; a paused one has a rule preview at most. */
function scoreText(item: JobMatchSummary): string {
  return item.status === 'COMPLETED' && item.score != null ? `${item.score}%` : '—'
}

function confidenceText(item: JobMatchSummary): string {
  return item.status === 'COMPLETED' ? confidencePresentation(item.confidence)?.label.replace('可信度', '') ?? '—' : '—'
}

const removing = ref('')
async function remove(item: JobMatchSummary): Promise<void> {
  const ok = await confirm({
    title: '删除这条匹配记录？',
    message: '记录会从列表中移除；已经导出的报告文件不受影响。',
    confirmText: '删除',
    tone: 'danger',
  })
  if (!ok) return
  removing.value = item.id
  try {
    await archiveJobMatch(item.id)
    toast.success('已删除匹配记录')
    await load()
  } catch (reason) {
    toast.error(errorMessage(reason, '删除没有成功'))
  } finally {
    removing.value = ''
  }
}

function open(item: JobMatchSummary): void {
  void router.push(summaryRoute(item))
}

watch([keyword, status], () => {
  page.value = 0
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { void load() }, 250)
})
watch(page, () => { void load() })
onMounted(load)
</script>

<template>
  <main class="jm-page"><div class="jm-shell">
    <header class="jm-head"><div><button class="jm-text-btn" type="button" @click="router.push({ name: 'job-match-home' })"><ArrowLeft :size="16" />返回岗位匹配</button><h1>历史匹配记录</h1><p>查看已完成报告、继续未完成任务或恢复安全暂停的分析。</p></div></header>
    <section class="jm-card jm-history-toolbar"><label class="jm-search"><Search :size="16" /><input v-model="keyword" placeholder="搜索岗位、公司或简历名称"></label><UiSelect v-model="status" aria-label="匹配状态"><option value="">全部状态</option><option value="COMPLETED">分析完成</option><option value="ANALYZING">分析中</option><option value="NEEDS_CLARIFICATION">需要确认</option><option value="ANALYSIS_PAUSED">分析已暂停</option><option value="CANCELLED">已取消</option><option value="EVIDENCE_AUTHORIZED">待开始分析</option></UiSelect><button class="jm-primary" type="button" @click="router.push({ name: 'job-match-new' })">创建新匹配</button></section>
    <section class="jm-card jm-history-table-wrap">
      <PageState :loading="loading" :error="error" :loaded="loaded" :empty="!items.length" compact error-title="历史记录读取失败" @retry="load">
      <template #skeleton><div class="jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在读取历史记录</div></template>
      <template #empty>
        <UiEmptyState v-if="filtered" illustration="empty-search" title="没有符合条件的匹配记录" description="调整搜索词或状态筛选后再试。" />
        <UiEmptyState v-else illustration="empty-history" title="还没有岗位匹配记录" description="创建一次岗位匹配后，报告和进行中的分析都会出现在这里。">
          <UiButton @click="router.push({ name: 'job-match-new' })">创建岗位匹配</UiButton>
        </UiEmptyState>
      </template>
      <table class="jm-history-table"><thead><tr><th>岗位与公司</th><th>投递简历</th><th>匹配度</th><th>可信度</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.title || '岗位描述待解析' }}</strong><small>{{ item.company || '未填写公司' }}</small></td><td>{{ item.resumeTitle || '尚未选择' }}</td><td><strong>{{ scoreText(item) }}</strong></td><td>{{ confidenceText(item) }}</td><td><UiBadge :tone="statusTone(item.status)">{{ statusLabel(item.status) }}</UiBadge></td><td>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</td><td class="jm-history-actions"><button class="jm-secondary" type="button" @click="open(item)">{{ item.status === 'COMPLETED' ? '查看报告' : '继续' }}</button><button class="jm-icon-btn" type="button" :aria-label="`删除 ${item.title || '这条匹配记录'}`" title="删除" :disabled="item.status === 'ANALYZING' || removing === item.id" @click="remove(item)"><Trash2 :size="16" /></button></td></tr></tbody></table>
      <footer v-if="totalPages > 1" class="jm-pagination"><span>共 {{ total }} 条</span><div><button class="jm-secondary" type="button" :disabled="page === 0" aria-label="上一页" @click="page -= 1"><ChevronLeft :size="16" /></button><strong>第 {{ page + 1 }} / {{ totalPages }} 页</strong><button class="jm-secondary" type="button" :disabled="page + 1 >= totalPages" aria-label="下一页" @click="page += 1"><ChevronRight :size="16" /></button></div></footer>
      </PageState>
    </section>
  </div></main>
</template>
