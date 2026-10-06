<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChevronLeft, ChevronRight, FileSearch, LoaderCircle, Search } from 'lucide-vue-next'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { fetchJobMatchHistory } from '../services/jobMatchApi'
import type { JobMatchSummary } from '../types'
import { statusLabel, statusTone } from '../utils/stage'
import '../job-match.css'

const router = useRouter()
const items = ref<JobMatchSummary[]>([])
const loading = ref(true)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'job-match-history-error')
const keyword = ref('')
const status = ref('')
const page = ref(0)
const total = ref(0)
const totalPages = ref(0)
let searchTimer: number | undefined

function open(item: JobMatchSummary): void {
  if (item.status === 'COMPLETED') void router.push({ name: 'job-match-report', params: { id: item.id } })
  else if (item.status === 'ANALYZING' || item.status === 'ANALYSIS_PAUSED' || item.status === 'CANCELLED') void router.push({ name: 'job-match-analyzing', params: { id: item.id } })
  else if (item.status === 'NEEDS_CLARIFICATION') void router.push({ name: 'job-match-clarifications', params: { id: item.id } })
  else void router.push({ name: 'job-match-new', query: { id: item.id } })
}

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    const result = await fetchJobMatchHistory({ status: status.value, query: keyword.value.trim(), page: page.value, size: 20 })
    items.value = result.items
    total.value = result.total
    totalPages.value = result.totalPages
    if (page.value >= result.totalPages && result.totalPages > 0) page.value = result.totalPages - 1
  } catch (reason) {
    pageError.value = errorMessage(reason, '历史匹配记录读取失败')
  } finally {
    loading.value = false
  }
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
    <section class="jm-card jm-history-toolbar"><label class="jm-search"><Search :size="16" /><input v-model="keyword" placeholder="搜索岗位、公司或简历名称"></label><AppSelect v-model="status"><option value="">全部状态</option><option value="COMPLETED">分析完成</option><option value="ANALYZING">分析中</option><option value="NEEDS_CLARIFICATION">需要确认</option><option value="ANALYSIS_PAUSED">分析已暂停</option><option value="EVIDENCE_AUTHORIZED">待开始分析</option></AppSelect><button class="jm-primary" type="button" @click="router.push({ name: 'job-match-new' })">创建新匹配</button></section>
    <section class="jm-card jm-history-table-wrap">
      <div v-if="loading" class="jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在读取历史记录</div>
      <table v-else-if="items.length" class="jm-history-table"><thead><tr><th>岗位与公司</th><th>投递简历</th><th>匹配度</th><th>可信度</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.title }}</strong><small>{{ item.company || '未填写公司' }}</small></td><td>{{ item.resumeTitle || '尚未选择' }}</td><td><strong>{{ item.score ?? '—' }}<template v-if="item.score != null">%</template></strong></td><td>{{ item.confidence || '—' }}</td><td><AppTag :tone="statusTone(item.status)">{{ statusLabel(item.status) }}</AppTag></td><td>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</td><td><button class="jm-secondary" type="button" @click="open(item)">{{ item.status === 'COMPLETED' ? '查看报告' : '继续' }}</button></td></tr></tbody></table>
      <div v-else class="jm-empty"><div><span><FileSearch :size="30" /></span><h2>没有符合条件的匹配记录</h2><p>调整筛选条件或创建新的岗位匹配。</p></div></div>
      <footer v-if="!loading && totalPages > 1" class="jm-pagination"><span>共 {{ total }} 条</span><div><button class="jm-secondary" type="button" :disabled="page === 0" aria-label="上一页" @click="page -= 1"><ChevronLeft :size="16" /></button><strong>第 {{ page + 1 }} / {{ totalPages }} 页</strong><button class="jm-secondary" type="button" :disabled="page + 1 >= totalPages" aria-label="下一页" @click="page += 1"><ChevronRight :size="16" /></button></div></footer>
    </section>
  </div></main>
</template>
