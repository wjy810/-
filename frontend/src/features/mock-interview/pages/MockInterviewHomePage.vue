<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronRight, LoaderCircle, Plus, Search } from 'lucide-vue-next'
import AppSelect from '@/shared/ui/AppSelect.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import { formatWhen } from '@/shared/lib/datetime'
import { errorMessage } from '@/shared/api/types'
import { useToastFeedback } from '@/shared/ui/toast'
import { fetchMockInterviewDashboard, listMockInterviews } from '../services/mockInterviewApi'
import type { MockInterviewDashboard, MockInterviewSessionSummary } from '../types'
import '../mock-interview.css'

const router = useRouter()
const dashboard = ref<MockInterviewDashboard | null>(null)
const sessions = ref<MockInterviewSessionSummary[]>([])
const loading = ref(true)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'mock-interview-home-error')
const showHistory = ref(false)
const modeFilter = ref('')
const statusFilter = ref('')
const typeFilter = ref('')
const keyword = ref('')

const displayed = computed(() => {
  const source = showHistory.value ? sessions.value : (dashboard.value?.recent ?? [])
  const term = keyword.value.trim().toLowerCase()
  return source.filter((item) => !term || `${item.title} ${item.positionName} ${item.companyName ?? ''}`.toLowerCase().includes(term))
})

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    const [summary, history] = await Promise.all([
      fetchMockInterviewDashboard(),
      listMockInterviews({ mode: modeFilter.value || undefined, status: statusFilter.value || undefined, type: typeFilter.value || undefined, limit: 100 }),
    ])
    dashboard.value = summary
    sessions.value = history
  } catch (reason) {
    pageError.value = errorMessage(reason, '模拟面试数据读取失败')
  } finally {
    loading.value = false
  }
}

function create(mode?: 'TEXT' | 'VOICE'): void {
  void router.push({ name: 'mock-interview-create', query: mode ? { mode } : {} })
}

function open(item: MockInterviewSessionSummary): void {
  if (item.status === 'COMPLETED') void router.push({ name: 'mock-interview-report', params: { sessionId: item.id } })
  else if (item.status !== 'ABANDONED') void router.push({ name: 'mock-interview-session', params: { sessionId: item.id } })
}

function modeLabel(value: string): string { return value === 'VOICE' ? '语音' : '文字' }
function typeLabel(value: string): string {
  return ({ COMPREHENSIVE: '综合面试', BEHAVIORAL: '行为面试', PROFESSIONAL: '专业面试', PRESSURE: '压力面试', QUICK: '快速热身' } as Record<string, string>)[value] ?? value
}
function statusLabel(value: string): string {
  return ({ COMPLETED: '已完成', PAUSED: '已暂停', ABANDONED: '已放弃', IN_PROGRESS: '进行中', OFFLINE: '待同步', TRANSCRIPTION_FAILED: '转写待处理' } as Record<string, string>)[value] ?? '进行中'
}

onMounted(load)
</script>

<template>
      <main class="mi-page">
      <div class="mi-shell">
        <header class="mi-head">
          <div><h1>模拟面试</h1><p>基于简历、岗位与求职资料，进行可复盘的 AI 面试训练</p></div>
          <button class="mi-primary" type="button" @click="create()"><Plus :size="17" />创建模拟面试</button>
        </header>

        <section v-if="loading" class="mi-card mi-loading"><span><LoaderCircle class="mi-spin" :size="20" />正在读取训练记录</span></section>
        <template v-else>
          <section class="mi-home-stats" aria-label="训练统计">
            <article class="mi-card"><span class="mi-stat-icon is-blue"><JobProofIcon name="interview-training-count" :size="23" /></span><div><small>累计训练</small><strong>{{ dashboard?.total ?? 0 }}</strong><p>{{ dashboard?.completed ?? 0 }} 次已完成</p></div></article>
            <article class="mi-card"><span class="mi-stat-icon is-green"><JobProofIcon name="interview-average-score" :size="23" /></span><div><small>平均得分</small><strong>{{ dashboard?.averageScore ?? '—' }}</strong><p>基于已完成训练</p></div></article>
            <article class="mi-card"><span class="mi-stat-icon is-purple"><JobProofIcon name="interview-training-mode" :size="23" /></span><div><small>训练方式</small><strong>{{ dashboard?.textCount ?? 0 }} / {{ dashboard?.voiceCount ?? 0 }}</strong><p>文字 / 语音</p></div></article>
          </section>

          <section class="mi-mode-grid">
            <article class="mi-card mi-mode-card">
              <span class="mi-mode-icon is-blue"><JobProofIcon name="interview-text-mode" :size="45" /></span>
              <div><h2>文字模拟</h2><p>无需麦克风，适合安静练习与快速复盘</p><button class="mi-primary" type="button" @click="create('TEXT')">开始文字模拟</button></div>
            </article>
            <article class="mi-card mi-mode-card">
              <span class="mi-mode-icon is-green"><JobProofIcon name="interview-voice-mode" :size="45" /></span>
              <div><h2>语音模拟</h2><p>真实口述回答，支持录音、转写与语音反馈</p><button class="mi-secondary" type="button" @click="create('VOICE')">开始语音模拟</button></div>
            </article>
          </section>

          <button v-if="dashboard?.resumable" class="mi-card mi-continue" type="button" @click="open(dashboard.resumable)">
            <span><JobProofIcon name="interview-recent-interviews" :size="20" /></span><div><strong>继续上次练习</strong><p>{{ dashboard.resumable.positionName }} · {{ modeLabel(dashboard.resumable.mode) }}模拟 · 第 {{ Math.min(dashboard.resumable.answeredCount + 1, dashboard.resumable.questionCount) }} / {{ dashboard.resumable.questionCount }} 题</p></div><em>继续<ChevronRight :size="17" /></em>
          </button>

          <section class="mi-home-bottom">
            <aside class="mi-card mi-suggestions">
              <h2>训练建议</h2>
              <div><span class="is-blue"><JobProofIcon name="interview-expression-structure" :size="18" /></span><p><strong>文字表达结构</strong><small>优化回答结构，提升逻辑清晰度与表达完整性。</small></p></div>
              <div><span class="is-green"><JobProofIcon name="interview-evidence-quantification" :size="18" /></span><p><strong>证据量化</strong><small>用数据与案例支撑观点，增强说服力与可信度。</small></p></div>
              <div><span class="is-orange"><JobProofIcon name="interview-pressure-followup" :size="18" /></span><p><strong>压力追问</strong><small>针对薄弱追问点提前准备，练习稳定表达。</small></p></div>
            </aside>

            <section class="mi-card mi-recent">
              <header><div><h2>{{ showHistory ? '模拟面试记录' : '最近的模拟面试' }}</h2><p v-if="showHistory">查看、继续或复盘全部训练</p></div><button class="mi-secondary" type="button" @click="showHistory = !showHistory">{{ showHistory ? '收起记录' : '查看全部' }}</button></header>
              <div v-if="showHistory" class="mi-filters">
                <label><Search :size="16" /><input v-model="keyword" placeholder="搜索岗位、公司或面试名称" /></label>
                <AppSelect v-model="statusFilter" aria-label="面试状态" @change="load()"><option value="">全部状态</option><option value="IN_PROGRESS">进行中</option><option value="PAUSED">已暂停</option><option value="COMPLETED">已完成</option><option value="ABANDONED">已放弃</option></AppSelect>
                <AppSelect v-model="modeFilter" aria-label="面试方式" @change="load()"><option value="">全部方式</option><option value="TEXT">文字模拟</option><option value="VOICE">语音模拟</option></AppSelect>
                <AppSelect v-model="typeFilter" aria-label="面试类型" @change="load()"><option value="">全部类型</option><option value="COMPREHENSIVE">综合面试</option><option value="BEHAVIORAL">行为面试</option><option value="PROFESSIONAL">专业面试</option><option value="PRESSURE">压力面试</option></AppSelect>
              </div>
              <div class="mi-table-wrap">
                <table v-if="displayed.length" class="mi-history-table">
                  <thead><tr><th>岗位与公司</th><th>方式</th><th>类型</th><th>进度</th><th>得分</th><th>更新时间</th><th>操作</th></tr></thead>
                  <tbody><tr v-for="item in displayed" :key="item.id"><td data-label="岗位与公司"><strong>{{ item.positionName }}</strong><small>{{ item.companyName || '未指定公司' }}</small></td><td data-label="方式"><span class="mi-tag" :class="item.mode === 'VOICE' ? 'mi-tag--green' : 'mi-tag--blue'">{{ modeLabel(item.mode) }}</span></td><td data-label="类型">{{ typeLabel(item.interviewType) }}</td><td data-label="进度">{{ item.answeredCount }}/{{ item.questionCount }} <span class="mi-tag mi-tag--gray">{{ statusLabel(item.status) }}</span></td><td data-label="得分" :class="{ 'is-score': item.score }">{{ item.score ?? '—' }}</td><td data-label="更新时间">{{ formatWhen(item.updatedAt) }}</td><td data-label="操作"><span v-if="item.status === 'ABANDONED'" class="mi-terminal-action">已结束</span><button v-else type="button" @click="open(item)">{{ item.status === 'COMPLETED' ? '查看报告' : '继续面试' }}</button></td></tr></tbody>
                </table>
                <div v-else class="mi-empty">还没有符合条件的训练记录</div>
              </div>
            </section>
          </section>
        </template>
      </div>
    </main>
</template>

<style scoped>
.mi-home-stats{grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-bottom:16px;display:grid}
.mi-home-stats article{align-items:center;gap:18px;min-height:104px;padding:20px;display:flex}
.mi-home-stats article>div{gap:1px;display:grid}
.mi-home-stats small{color:var(--text-secondary)}
.mi-home-stats strong{color:var(--text-primary);font-size:27px;line-height:1.2}
.mi-home-stats p{color:var(--text-tertiary);font-size:12px}
.mi-stat-icon,.mi-mode-icon{flex:none;place-items:center;display:grid}
.mi-stat-icon{border-radius:10px;width:52px;height:52px}
.is-blue{color:var(--color-primary-text);background:var(--surface-2)}.is-green{color:var(--color-success-text);background:var(--surface-2)}.is-purple{color:var(--color-primary);background:var(--surface-2)}.is-orange{color:var(--color-warning-text);background:var(--surface-3)}
.mi-mode-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-bottom:16px;display:grid}
.mi-mode-card{align-items:center;gap:30px;min-height:178px;padding:28px;display:flex}
.mi-mode-icon{border-radius:26px;width:112px;height:112px}
.mi-mode-card>div{min-width:0}.mi-mode-card h2{margin-bottom:4px;font-size:24px}.mi-mode-card p{color:var(--text-secondary);margin-bottom:16px}
.mi-continue{color:var(--text-primary);text-align:left;grid-template-columns:42px 1fr auto;align-items:center;gap:14px;width:100%;min-height:70px;padding:13px 20px;display:grid}
.mi-continue>span{color:var(--color-primary-text);background:var(--surface-2);border-radius:8px;place-items:center;width:42px;height:42px;display:grid}.mi-continue strong{font-size:15px}.mi-continue p{color:var(--text-secondary);font-size:12px}.mi-continue em{color:var(--color-primary-text);align-items:center;gap:4px;font-style:normal;font-weight:600;display:flex}
.mi-home-bottom{grid-template-columns:330px minmax(0,1fr);gap:16px;margin-top:16px;display:grid}
.mi-suggestions{padding:18px}.mi-suggestions h2,.mi-recent h2{font-size:15px}.mi-suggestions>div{border:1px solid var(--border-subtle);border-radius:7px;gap:11px;margin-top:10px;padding:10px;display:flex}.mi-suggestions>div>span{border-radius:7px;flex:none;place-items:center;width:38px;height:38px;display:grid}.mi-suggestions p{display:grid}.mi-suggestions small{color:var(--text-secondary);font-size:12px;line-height:1.5}
.mi-recent{min-width:0;overflow:hidden}.mi-recent>header{justify-content:space-between;align-items:center;gap:12px;padding:17px 18px 11px;display:flex}.mi-recent>header p{color:var(--text-secondary);font-size:12px}.mi-recent .mi-secondary{min-height:32px;padding:0 12px;font-size:12px}
.mi-filters{grid-template-columns:minmax(220px,1.5fr) repeat(3,minmax(120px,.7fr));gap:8px;padding:8px 18px 14px;display:grid}.mi-filters label{color:var(--text-secondary);border:1px solid var(--border-default);border-radius:6px;align-items:center;gap:7px;height:42px;padding:0 11px;display:flex}.mi-filters input{color:var(--text-primary);background:0 0;border:0;outline:0;width:100%;min-width:0}.mi-table-wrap{overflow:auto}.mi-history-table{border-collapse:collapse;width:100%;font-size:12px}.mi-history-table th{color:var(--text-secondary);text-align:left;white-space:nowrap;background:var(--surface-2);border-top:1px solid var(--border-subtle);border-bottom:1px solid var(--border-subtle);padding:9px 13px}.mi-history-table td{white-space:nowrap;border-bottom:1px solid var(--border-subtle);padding:10px 13px}.mi-history-table td:first-child{min-width:150px}.mi-history-table td strong,.mi-history-table td small{display:block}.mi-history-table td small{color:var(--text-secondary)}.mi-history-table td button{color:var(--color-primary-text);background:var(--surface-1);border:1px solid var(--color-primary-border);border-radius:4px;padding:4px 9px}.mi-terminal-action{color:var(--text-tertiary)}.mi-history-table .is-score{color:var(--color-success-text);font-weight:700}
@media (width<=1100px){.mi-home-bottom{grid-template-columns:1fr}.mi-suggestions{display:none}.mi-filters{grid-template-columns:1fr 1fr}.mi-mode-card{gap:18px;padding:22px}.mi-mode-icon{width:82px;height:82px}}
@media (width<=760px){.mi-home-stats,.mi-mode-grid{grid-template-columns:1fr}.mi-home-stats{gap:10px}.mi-home-stats article{min-height:88px;padding:15px}.mi-mode-card{min-height:150px}.mi-mode-card h2{font-size:20px}.mi-filters{grid-template-columns:1fr}.mi-continue{grid-template-columns:38px 1fr}.mi-continue em{display:none}.mi-table-wrap{padding:0 12px 12px;overflow:visible}.mi-history-table{width:100%;display:block}.mi-history-table thead{display:none}.mi-history-table tbody{gap:10px;display:grid}.mi-history-table tr{background:var(--surface-1);border:1px solid var(--border-subtle);border-radius:8px;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px 14px;padding:14px;display:grid;box-shadow:0 4px 12px color-mix(in srgb, var(--border-strong) 4%, transparent)}.mi-history-table td,.mi-history-table td:first-child{white-space:normal;border:0;align-content:start;gap:4px;min-width:0;padding:0;display:grid}.mi-history-table td:before{content:attr(data-label);color:var(--text-secondary);font-size:11.5px;font-weight:500}.mi-history-table td:first-child{border-bottom:1px solid var(--border-subtle);grid-column:1/-1;padding-bottom:10px}.mi-history-table td:first-child:before{display:none}.mi-history-table td strong{overflow-wrap:anywhere}.mi-history-table td:last-child{grid-column:1/-1}.mi-history-table td:last-child button{border-radius:7px;width:100%;min-height:38px;font-weight:600}.mi-terminal-action{background:var(--surface-2);border-radius:7px;place-items:center;min-height:38px;display:grid}}
@media (prefers-reduced-motion: reduce){.mi-home-stats,.mi-mode-card,.mi-continue,.mi-secondary,.mi-primary{transition:none!important}.mi-spin{animation:none}}
</style>
