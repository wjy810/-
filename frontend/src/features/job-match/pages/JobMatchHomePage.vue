<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, FileCheck2, LoaderCircle, Plus, ShieldCheck, Sparkles } from 'lucide-vue-next'
import AppTag from '@/shared/ui/AppTag.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import { errorMessage } from '@/shared/api/types'
import { useToastFeedback } from '@/shared/ui/toast'
import { fetchMatchDashboard } from '../services/jobMatchApi'
import type { JobMatchDashboard, JobMatchSummary } from '../types'
import { statusLabel, statusTone, summaryRoute } from '../utils/stage'
import '../job-match.css'

const router = useRouter()
const dashboard = ref<JobMatchDashboard | null>(null)
const loading = ref(true)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'job-match-home-error')
const pendingCount = computed(() => (dashboard.value?.recent ?? []).filter(item => item.status !== 'COMPLETED').length)

async function load() {
  loading.value = true; pageError.value = ''
  try { dashboard.value = await fetchMatchDashboard() }
  catch (reason) { pageError.value = errorMessage(reason, '岗位匹配数据读取失败') }
  finally { loading.value = false }
}
function open(item: JobMatchSummary) {
  void router.push(summaryRoute(item))
}
onMounted(load)
</script>

<template>
      <main class="jm-page"><div class="jm-shell">
      <header class="jm-head"><div><h1>岗位匹配</h1><p>比较目标岗位 JD 与你的简历，获得有证据的匹配分析和提升建议。</p></div><button class="jm-primary" type="button" @click="router.push({ name: 'job-match-new' })"><Plus :size="17" /><span>创建新的匹配</span></button></header>
      <section v-if="loading" class="jm-card jm-loading"><span><LoaderCircle class="jm-spin" :size="20" /> 正在读取匹配记录</span></section>
      <template v-else>
        <section class="jm-stats" aria-label="岗位匹配统计">
          <article class="jm-card jm-stat"><span class="jm-stat__icon is-blue"><JobProofIcon name="matching-analysis-count" :size="25" /></span><p><small>累计分析</small><strong>{{ dashboard?.total ?? 0 }}</strong><em>全部岗位匹配任务</em></p></article>
          <article class="jm-card jm-stat"><span class="jm-stat__icon is-cyan"><JobProofIcon name="matching-match-rate" :size="25" /></span><p><small>平均匹配度</small><strong>{{ dashboard?.averageScore ?? '—' }}<small v-if="dashboard?.averageScore != null">%</small></strong><em>基于已完成报告</em></p></article>
          <article class="jm-card jm-stat"><span class="jm-stat__icon is-orange"><JobProofIcon name="matching-pending-task" :size="25" /></span><p><small>待处理任务</small><strong>{{ pendingCount }}</strong><em>继续补充或恢复分析</em></p></article>
          <article class="jm-card jm-stat"><span class="jm-stat__icon is-green"><JobProofIcon name="matching-optimized-resume" :size="25" /></span><p><small>已优化简历</small><strong>{{ dashboard?.optimized ?? 0 }}</strong><em>已采纳匹配建议</em></p></article>
        </section>
        <div v-if="(dashboard?.total ?? 0) > 0" class="jm-home-grid">
          <div>
            <section class="jm-card jm-flow"><h2>从岗位要求到行动方案</h2><div class="jm-flow__steps">
              <article class="jm-flow__step"><span><JobProofIcon name="matching-jd-import" :size="27" /></span><strong>导入岗位 JD</strong><p>粘贴或上传岗位信息，提取关键要求</p></article>
              <article class="jm-flow__step"><span><JobProofIcon name="matching-resume-selection" :size="27" /></span><strong>选择简历与证据</strong><p>选择投递版本，明确资料授权范围</p></article>
              <article class="jm-flow__step"><span><JobProofIcon name="matching-analysis-report" :size="27" /></span><strong>AI 证据分析</strong><p>规则评分结合真实 AI 语义判断</p></article>
              <article class="jm-flow__step"><span><JobProofIcon name="matching-next-steps" :size="27" /></span><strong>优化与准备</strong><p>按优先级改简历、补证据和学习</p></article>
            </div><button class="jm-primary" type="button" @click="router.push({ name: 'job-match-new' })">开始岗位匹配</button></section>
            <section class="jm-card jm-recent"><header class="jm-section-head"><h2>最近的匹配任务</h2><button class="jm-text-btn" type="button" @click="router.push({ name: 'job-match-history' })">查看全部 <ArrowRight :size="15" /></button></header>
              <div class="jm-task-list"><article v-for="item in dashboard?.recent" :key="item.id" class="jm-task"><span class="jm-score-ring" :class="{ 'is-pending': item.score == null }">{{ item.score ?? '—' }}</span><p><strong>{{ item.title }}<template v-if="item.company"> · {{ item.company }}</template></strong><small><AppTag :tone="statusTone(item.status)">{{ statusLabel(item.status) }}</AppTag></small></p><p><small>简历版本</small><strong>{{ item.resumeTitle || '尚未选择' }}</strong></p><time>{{ new Date(item.updatedAt).toLocaleDateString('zh-CN') }}</time><button class="jm-secondary" type="button" @click="open(item)">{{ item.status === 'COMPLETED' ? '查看报告' : '继续分析' }}</button></article></div>
            </section>
          </div>
          <aside class="jm-card jm-suggestions"><h2>匹配原则</h2><article class="jm-suggestion"><span><ShieldCheck :size="18" /></span><p><strong>结论必须可溯源</strong><small>每项优势、缺口和建议都关联 JD、简历或授权证据。</small></p></article><article class="jm-suggestion"><span><Sparkles :size="18" /></span><p><strong>AI 不制造分数</strong><small>规则引擎负责稳定评分，AI 负责解释、澄清与行动计划。</small></p></article><article class="jm-suggestion"><span><FileCheck2 :size="18" /></span><p><strong>未写不等于不会</strong><small>证据不足时优先追问，不把简历没有写误判为能力缺口。</small></p></article></aside>
        </div>
        <section v-else class="jm-card jm-empty"><div><span><JobProofIcon name="matching-create-match" :size="32" /></span><h2>创建第一份岗位匹配</h2><p>导入一份真实 JD，选择投递简历，获得带事实来源的分析报告。</p><button class="jm-primary" type="button" @click="router.push({ name: 'job-match-new' })"><Plus :size="17" />创建岗位匹配</button></div></section>
      </template>
    </div></main>
</template>
