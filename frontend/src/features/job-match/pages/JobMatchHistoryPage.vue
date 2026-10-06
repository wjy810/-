<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ChevronLeft, ChevronRight, FileSearch, LoaderCircle, Search } from 'lucide-vue-next'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import PageState from '@/shared/ui/PageState.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { fetchJobMatchHistory } from '../services/jobMatchApi'
import type { JobMatchSummary } from '../types'
import { displayJobMatchText } from '../utils/reportPresentation'
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

function confidenceLabel(value: string | null | undefined): string {
  if (!value) return '—'
  return /^\d+$/.test(value) ? `${value}%` : displayJobMatchText(value) || '—'
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
    <section class="jm-card jm-history-toolbar"><label class="jm-search"><Search :size="16" /><input v-model="keyword" placeholder="搜索岗位、公司或简历名称"></label><AppSelect v-model="status"><option value="">全部状态</option><option value="COMPLETED">分析完成</option><option value="ANALYZING">分析中</option><option value="NEEDS_CLARIFICATION">需要确认</option><option value="ANALYSIS_PAUSED">分析已暂停</option><option value="EVIDENCE_AUTHORIZED">待开始分析</option></AppSelect><button class="jm-primary" type="button" @click="router.push({ name: 'job-match-new' })">创建新匹配</button></section>
    <section class="jm-card jm-history-table-wrap">
      <PageState :loading="loading" :error="error" :loaded="loaded" :empty="!items.length" compact error-title="历史记录读取失败" @retry="load">
      <template #skeleton><div class="jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在读取历史记录</div></template>
      <template #empty>
        <div class="jm-empty">
          <div v-if="filtered"><span><FileSearch :size="30" /></span><h2>没有符合条件的匹配记录</h2><p>调整搜索词或状态筛选后再试。</p></div>
          <div v-else><span><FileSearch :size="30" /></span><h2>还没有岗位匹配记录</h2><p>创建一次岗位匹配后，记录会出现在这里。</p></div>
        </div>
      </template>
      <table class="jm-history-table"><thead><tr><th>岗位与公司</th><th>投递简历</th><th>匹配度</th><th>可信度</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.title }}</strong><small>{{ item.company || '未填写公司' }}</small></td><td>{{ item.resumeTitle || '尚未选择' }}</td><td><strong>{{ item.score ?? '—' }}<template v-if="item.score != null">%</template></strong></td><td>{{ confidenceLabel(item.confidence) }}</td><td><AppTag :tone="statusTone(item.status)">{{ statusLabel(item.status) }}</AppTag></td><td>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</td><td><button class="jm-secondary" type="button" @click="open(item)">{{ item.status === 'COMPLETED' ? '查看报告' : '继续' }}</button></td></tr></tbody></table>
      <footer v-if="totalPages > 1" class="jm-pagination"><span>共 {{ total }} 条</span><div><button class="jm-secondary" type="button" :disabled="page === 0" aria-label="上一页" @click="page -= 1"><ChevronLeft :size="16" /></button><strong>第 {{ page + 1 }} / {{ totalPages }} 页</strong><button class="jm-secondary" type="button" :disabled="page + 1 >= totalPages" aria-label="下一页" @click="page += 1"><ChevronRight :size="16" /></button></div></footer>
      </PageState>
    </section>
  </div></main>
</template>
