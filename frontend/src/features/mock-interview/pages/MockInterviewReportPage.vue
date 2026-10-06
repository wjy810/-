<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, ArrowLeft, BarChart3, CheckCircle2, ChevronRight, Download, FileText, LoaderCircle, RefreshCw, Share2, ShieldCheck, Sparkles, Target, TrendingUp } from 'lucide-vue-next'
import { formatWhen } from '@/shared/lib/datetime'
import { errorMessage } from '@/shared/api/types'
import { useToastFeedback } from '@/shared/ui/toast'
import { fetchMockInterviewReport, retryMockInterview } from '../services/mockInterviewApi'
import type { MockInterviewReport, MockInterviewReview } from '../types'
import '../mock-interview.css'

const route = useRoute()
const router = useRouter()
const report = ref<MockInterviewReport | null>(null)
const loading = ref(true)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'mock-interview-report-error')
const retrying = ref(false)
const selectedQuestionId = computed(() => typeof route.query.question === 'string' ? route.query.question : '')
const selectedReview = computed(() => report.value?.questions.find((item) => item.question.id === selectedQuestionId.value) ?? null)

const dimensionItems = computed(() => [
  { key: 'structure', label: '表达结构', color: '#1767ee' },
  { key: 'relevance', label: '岗位相关性', color: '#0ca65e' },
  { key: 'professional', label: '专业深度', color: '#7b48df' },
  { key: 'evidence', label: '证据质量', color: '#ed7a18' },
  { key: 'expression', label: '表达清晰', color: '#18a9a4' },
])

const radarPoints = computed(() => {
  const center = 120
  const radius = 82
  return dimensionItems.value.map((item, index) => {
    const angle = -Math.PI / 2 + (index * Math.PI * 2) / dimensionItems.value.length
    const value = (report.value?.dimensions[item.key] ?? 0) / 100
    return `${center + Math.cos(angle) * radius * value},${center + Math.sin(angle) * radius * value}`
  }).join(' ')
})

const radarAxes = computed(() => dimensionItems.value.map((_, index) => {
  const angle = -Math.PI / 2 + (index * Math.PI * 2) / dimensionItems.value.length
  return { x: 120 + Math.cos(angle) * 88, y: 120 + Math.sin(angle) * 88 }
}))

async function load(): Promise<void> {
  loading.value = true
  try { report.value = await fetchMockInterviewReport(String(route.params.sessionId ?? '')) }
  catch (reason) { pageError.value = errorMessage(reason, '面试报告读取失败') }
  finally { loading.value = false }
}

async function retry(): Promise<void> {
  if (!report.value) return
  retrying.value = true
  try {
    const created = await retryMockInterview(report.value.session.id)
    await router.push({ name: 'mock-interview-session', params: { sessionId: created.session.id } })
  } catch (reason) { pageError.value = errorMessage(reason, '重新训练创建失败') }
  finally { retrying.value = false }
}

function openReview(item: MockInterviewReview): void { void router.push({ query: { question: item.question.id } }) }
function closeReview(): void { void router.push({ query: {} }) }
function list(value: unknown): string[] { return Array.isArray(value) ? value.map(String) : [] }
function feedbackList(item: MockInterviewReview, key: string): string[] { return list(item.feedback[key]) }
function feedbackTitle(item: MockInterviewReview): string { return item.feedback.generationMode === 'AI_MODEL' ? 'AI 逐项点评' : '基础规则点评' }
function label(key: string): string { return dimensionItems.value.find((item) => item.key === key)?.label ?? key }
function typeLabel(value: string): string { return ({ COMPREHENSIVE: '综合面试', BEHAVIORAL: '行为面试', PROFESSIONAL: '专业面试', PRESSURE: '压力面试' } as Record<string, string>)[value] ?? value }
function questionScore(item: MockInterviewReview): number | null { return item.answer ? item.scores.overall ?? null : null }
function printReport(): void { window.print() }
async function shareReport(): Promise<void> {
  if (window.navigator.clipboard) await window.navigator.clipboard.writeText(window.location.href)
}

onMounted(load)
</script>

<template>
      <main class="mi-page mi-report-page">
      <div class="mi-shell">
        <section v-if="loading" class="mi-card mi-loading"><span><LoaderCircle class="mi-spin" :size="20" />正在生成可追溯报告</span></section>
        <template v-else-if="report">
          <template v-if="!selectedReview">
            <nav class="mi-breadcrumb"><RouterLink to="/mock-interviews">模拟面试</RouterLink><span>/</span><strong>面试报告</strong></nav>
            <header class="mi-report-head"><div><h1>{{ report.session.positionName }} · {{ typeLabel(report.session.interviewType) }}报告</h1><p>{{ report.session.companyName || '未指定公司' }}　|　{{ formatWhen(report.generatedAt) }}　|　{{ report.session.mode === 'VOICE' ? '语音模拟' : '文字模拟' }}　|　{{ report.session.questionCount }} 题</p></div><div><button class="mi-secondary" type="button" @click="printReport"><Download :size="16" />导出 PDF</button><button class="mi-secondary" type="button" @click="shareReport"><Share2 :size="16" />分享报告</button><button class="mi-primary" type="button" :disabled="retrying" @click="retry"><LoaderCircle v-if="retrying" class="mi-spin" :size="16" /><RefreshCw v-else :size="16" />再次练习</button></div></header>

            <section class="mi-report-stats"><article class="mi-card"><span class="blue"><BarChart3 :size="23" /></span><p><small>综合得分</small><strong>{{ report.overallScore }}</strong><em>基于本次训练</em></p></article><article class="mi-card"><span class="green"><Target :size="23" /></span><p><small>岗位相关性</small><strong>{{ report.dimensions.relevance ?? '—' }}%</strong><em>基于岗位分类与回答</em></p></article><article class="mi-card"><span class="purple"><CheckCircle2 :size="23" /></span><p><small>完成度</small><strong>{{ report.session.answeredCount }}/{{ report.session.questionCount }}</strong><em>{{ report.session.answeredCount === report.session.questionCount ? '全部完成' : '阶段报告' }}</em></p></article><article class="mi-card"><span class="orange"><FileText :size="23" /></span><p><small>回答方式</small><strong>{{ report.session.mode === 'VOICE' ? '语音' : '文字' }}</strong><em>原文完整保留</em></p></article></section>

            <section class="mi-report-overview">
              <article class="mi-card mi-radar"><h2>能力雷达图</h2><div><svg viewBox="0 0 240 240" role="img" aria-label="能力维度雷达图"><polygon points="120,32 203.7,92.8 171.7,191.2 68.3,191.2 36.3,92.8" fill="none" stroke="#dce4f0" /><polygon points="120,54 182.8,99.6 158.8,173.4 81.2,173.4 57.2,99.6" fill="none" stroke="#e7edf5" /><line v-for="(axis,index) in radarAxes" :key="index" x1="120" y1="120" :x2="axis.x" :y2="axis.y" stroke="#e2e8f2" /><polygon :points="radarPoints" fill="rgba(23,103,238,.15)" stroke="#1767ee" stroke-width="2.5" /></svg><div class="mi-radar-labels"><span v-for="(item,index) in dimensionItems" :key="item.key" :style="{ '--i': index }"><strong>{{ item.label }}</strong><em>{{ report.dimensions[item.key] ?? 0 }}</em></span></div></div></article>
              <article class="mi-card mi-ai-summary"><header><h2>训练总结</h2><span class="mi-tag mi-tag--green">建议继续专项训练</span></header><section><h3><CheckCircle2 :size="17" />核心优势</h3><p v-for="(item,index) in list(report.summary.strengths)" :key="item"><strong>{{ index + 1 }}</strong>{{ item }}</p></section><section class="risks"><h3><AlertTriangle :size="17" />主要风险</h3><p v-for="(item,index) in list(report.summary.risks)" :key="item"><strong>{{ index + 1 }}</strong>{{ item }}</p></section></article>
            </section>

            <section class="mi-report-lower">
              <article class="mi-card mi-question-table"><h2>逐题表现</h2><table><thead><tr><th>题号</th><th>主题</th><th>得分</th><th>表现</th></tr></thead><tbody><tr v-for="item in report.questions" :key="item.question.id" :class="{ active: questionScore(item) && questionScore(item)! < 78 }" @click="openReview(item)"><td>Q{{ item.question.orderNo }}</td><td>{{ item.question.prompt.length > 24 ? `${item.question.prompt.slice(0,24)}…` : item.question.prompt }}</td><td :class="{ low: questionScore(item) && questionScore(item)! < 78 }">{{ questionScore(item) ?? '—' }}</td><td><span><i :style="{ width: `${questionScore(item) ?? 0}%` }" /></span><ChevronRight :size="15" /></td></tr></tbody></table></article>
              <article class="mi-card mi-recommendations"><h2>下一步训练建议</h2><div v-for="(item,index) in report.recommendations" :key="item" :class="['tone-'+index]"><span><TrendingUp v-if="index === 0" :size="20" /><FileText v-else-if="index === 1" :size="20" /><Sparkles v-else :size="20" /></span><p><strong>{{ item }}</strong><small>{{ index === 0 ? '补齐关键指标，提升说服力。' : index === 1 ? '掌握完整项目复盘方法。' : '针对高压追问进行专项训练。' }}</small></p><button type="button" @click="retry">开始专项训练</button></div></article>
            </section>
            <p class="mi-report-notice"><ShieldCheck :size="16" />报告基于本次授权资料与回答生成，不评估受保护特征，也不代表真实招聘结果或录用概率</p>
          </template>

          <template v-else>
            <nav class="mi-breadcrumb"><button type="button" @click="closeReview">模拟面试 / 面试报告</button><span>/</span><strong>Q{{ selectedReview.question.orderNo }}</strong></nav>
            <header class="mi-review-head"><div><button type="button" @click="closeReview"><ArrowLeft :size="17" />返回报告</button><h1>Q{{ selectedReview.question.orderNo }} {{ selectedReview.question.questionType === 'PROJECT' ? '项目经验深挖' : '逐题复盘' }}</h1><span class="mi-tag mi-tag--blue">{{ selectedReview.answer?.mode === 'VOICE' ? '语音回答' : '文字回答' }}</span></div><button class="mi-primary" type="button" @click="retry"><RefreshCw :size="16" />重新回答本题</button></header>
            <section class="mi-review-top"><article class="mi-card"><small>问题</small><h2>{{ selectedReview.question.prompt }}</h2><p>来源：<span>{{ selectedReview.question.sourceLabel }}</span></p></article><article class="mi-card mi-review-scores"><div><small>综合得分</small><strong>{{ questionScore(selectedReview) ?? '—' }}<em>分</em></strong></div><div v-for="(value,key) in selectedReview.scores" v-show="key !== 'overall'" :key="key"><small>{{ label(String(key)) }}</small><strong>{{ value }}</strong></div></article></section>
            <section class="mi-review-main"><article class="mi-card mi-answer-review"><header><h2>回答原文与修改记录</h2><span>已自动保存（v{{ selectedReview.answer?.version ?? 0 }}）</span></header><div class="mi-answer-tabs"><button class="active" type="button">{{ selectedReview.answer?.mode === 'VOICE' ? '语音转写' : '文字回答' }}</button></div><p>{{ selectedReview.answer?.answer || '本题未作答' }}</p><footer><span>证据标签：</span><em v-for="source in selectedReview.question.sourceRefs" :key="source">{{ source }}</em></footer></article><article class="mi-card mi-review-feedback"><h2>{{ feedbackTitle(selectedReview) }}</h2><section><h3><CheckCircle2 :size="17" />做得好的</h3><p v-for="item in feedbackList(selectedReview,'strengths')" :key="item">{{ item }}</p></section><section class="risks"><h3><AlertTriangle :size="17" />需要改进</h3><p v-for="item in feedbackList(selectedReview,'improvements')" :key="item">{{ item }}</p></section><section v-if="selectedReview.feedback.suggestedFollowUp"><h3><Sparkles :size="17" />追问信息</h3><p>{{ String(selectedReview.feedback.suggestedFollowUp) }}</p></section></article></section>
            <section class="mi-review-bottom"><article class="mi-card"><h2>原回答结构（STAR）</h2><ol><li class="done"><span>S</span>情境（Situation）</li><li class="done"><span>T</span>任务（Task）</li><li class="done"><span>A</span>行动（Action）</li><li :class="(selectedReview.scores.evidence ?? 0) >= 75 ? 'done' : ''"><span>R</span>结果（Result）</li></ol></article><article class="mi-card"><h2>优化后的回答框架</h2><ol><li>明确背景与目标岗位的关联</li><li>清晰说明个人任务和判断</li><li>详细呈现行动、取舍与协作</li><li>用已确认数据总结结果</li></ol></article><article class="mi-card"><h2>优化示例（框架版）</h2><p>在保留原回答事实的前提下，先用一句话说明情境与目标，再描述个人承担的核心任务；随后按时间顺序解释关键行动、取舍与协作，最后补充可核验结果。没有资料支持的数字和结论应明确留空并由本人确认。</p></article></section>
          </template>
        </template>
      </div>
    </main>
</template>

<style scoped>
.mi-report-page{background:#f7f9fc}.mi-breadcrumb{margin-bottom:16px;display:flex;align-items:center;gap:10px;color:#68758b}.mi-breadcrumb a,.mi-breadcrumb button{padding:0;border:0;color:#24324e;background:transparent}.mi-report-head,.mi-review-head{margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;gap:16px}.mi-report-head h1,.mi-review-head h1{font-size:25px}.mi-report-head p{color:#718097}.mi-report-head>div:last-child{display:flex;gap:10px}.mi-report-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}.mi-report-stats article{min-height:106px;padding:20px;display:flex;align-items:center;gap:16px}.mi-report-stats article>span{width:48px;height:48px;display:grid;place-items:center;border-radius:10px}.mi-report-stats .blue{color:#1767ee;background:#eaf2ff}.mi-report-stats .green{color:#0ca65e;background:#e8f8ef}.mi-report-stats .purple{color:#7b48df;background:#f1ebff}.mi-report-stats .orange{color:#e27913;background:#fff0df}.mi-report-stats p{display:grid}.mi-report-stats small{color:#69768c}.mi-report-stats strong{font-size:26px}.mi-report-stats em{color:#7c899c;font-size:11px;font-style:normal}.mi-report-overview{margin-top:16px;display:grid;grid-template-columns:1fr 1.45fr;gap:16px}.mi-radar,.mi-ai-summary{padding:18px}.mi-radar h2,.mi-ai-summary h2,.mi-question-table h2,.mi-recommendations h2{font-size:15px}.mi-radar>div{position:relative;height:270px}.mi-radar svg{display:block;width:250px;height:250px;margin:5px auto}.mi-radar-labels span{position:absolute;display:grid;text-align:center;font-size:11px}.mi-radar-labels span:nth-child(1){top:0;left:50%;transform:translateX(-50%)}.mi-radar-labels span:nth-child(2){top:75px;right:8px}.mi-radar-labels span:nth-child(3){right:33px;bottom:5px}.mi-radar-labels span:nth-child(4){bottom:5px;left:33px}.mi-radar-labels span:nth-child(5){top:75px;left:8px}.mi-radar-labels em{font-style:normal;font-weight:700}.mi-ai-summary>header{display:flex;gap:10px;align-items:center}.mi-ai-summary section{margin-top:12px;padding:14px;border:1px solid #e2e8f0;border-radius:7px}.mi-ai-summary h3{display:flex;align-items:center;gap:7px;color:#1261e5;font-size:14px}.mi-ai-summary section.risks h3{color:#d16b09}.mi-ai-summary section p{margin-top:9px;display:flex;gap:9px}.mi-ai-summary section p strong{width:20px;height:20px;display:grid;place-items:center;flex:0 0 auto;border-radius:50%;color:#fff;background:#1767ee;font-size:11px}.mi-ai-summary section.risks p strong{background:#ed7a18}.mi-report-lower{margin-top:16px;display:grid;grid-template-columns:1fr 1fr;gap:16px}.mi-question-table,.mi-recommendations{padding:18px}.mi-question-table table{width:100%;margin-top:10px;border-collapse:collapse}.mi-question-table th,.mi-question-table td{padding:8px;border-bottom:1px solid #edf0f4;text-align:left}.mi-question-table th{color:#728096;background:#f8fafc;font-size:11px}.mi-question-table tbody tr{cursor:pointer}.mi-question-table tbody tr:hover,.mi-question-table tbody tr.active{background:#f3f7ff}.mi-question-table td:nth-child(3){color:#0ca65e;font-weight:700}.mi-question-table td.low{color:#e17812}.mi-question-table td:last-child{display:flex;align-items:center;gap:8px}.mi-question-table td:last-child span{width:100px;height:5px;border-radius:3px;background:#e3e9f1;overflow:hidden}.mi-question-table td:last-child i{display:block;height:100%;background:#1767ee}.mi-recommendations>div{margin-top:10px;padding:12px;display:grid;grid-template-columns:38px 1fr auto;align-items:center;gap:10px;border:1px solid #dfe6f0;border-radius:7px}.mi-recommendations>div>span{width:36px;height:36px;display:grid;place-items:center;border-radius:7px;color:#1767ee;background:#eaf2ff}.mi-recommendations .tone-1>span{color:#0ca65e;background:#e8f8ef}.mi-recommendations .tone-2>span{color:#e17812;background:#fff0df}.mi-recommendations p{display:grid}.mi-recommendations small{color:#758197}.mi-recommendations button{height:34px;padding:0 11px;border:0;border-radius:5px;color:#fff;background:#1767ee}.mi-report-notice{margin-top:16px;padding:10px 14px;display:flex;align-items:center;gap:7px;border-radius:6px;color:#175dbb;background:#eaf2ff}.mi-review-head>div{display:flex;align-items:center;gap:10px}.mi-review-head>div>button{height:36px;padding:0 10px;display:flex;align-items:center;gap:5px;border:1px solid #c9d3e1;border-radius:6px;background:#fff}.mi-review-top{display:grid;grid-template-columns:1.2fr 1fr;gap:16px}.mi-review-top>article{padding:18px}.mi-review-top>article>small{color:#748096}.mi-review-top>article h2{margin:6px 0;font-size:17px}.mi-review-top>article p{color:#758197}.mi-review-top>article p span{padding:2px 7px;border-radius:4px;color:#1767ee;background:#eaf2ff}.mi-review-scores{display:grid;grid-template-columns:repeat(6,1fr);align-items:center;text-align:center}.mi-review-scores>div{display:grid;border-right:1px solid #e5eaf2}.mi-review-scores>div:last-child{border:0}.mi-review-scores small{color:#758197}.mi-review-scores strong{font-size:22px}.mi-review-scores>div:first-child strong{color:#1767ee;font-size:32px}.mi-review-scores em{font-size:13px;font-style:normal}.mi-review-main{margin-top:16px;display:grid;grid-template-columns:1.1fr 1fr;gap:16px}.mi-answer-review,.mi-review-feedback{padding:18px}.mi-answer-review>header{display:flex;justify-content:space-between}.mi-answer-review>header h2,.mi-review-feedback>h2,.mi-review-bottom h2{font-size:15px}.mi-answer-review>header span{color:#758197;font-size:11px}.mi-answer-tabs{margin:12px 0;border-bottom:1px solid #e2e8f1}.mi-answer-tabs button{padding:8px 14px;border:0;border-bottom:2px solid #1767ee;color:#1767ee;background:transparent}.mi-answer-review>p{min-height:210px;padding:12px;border-radius:6px;background:#f7f9fc;white-space:pre-wrap;line-height:1.8}.mi-answer-review footer{margin-top:10px;display:flex;gap:6px}.mi-answer-review footer em{padding:2px 7px;border-radius:4px;color:#1767ee;background:#eaf2ff;font-style:normal}.mi-review-feedback section{margin-top:10px;padding:12px;border-bottom:1px solid #e8edf3}.mi-review-feedback h3{display:flex;align-items:center;gap:6px;color:#0a9a56;font-size:13px}.mi-review-feedback section.risks h3{color:#df7310}.mi-review-feedback section p{margin-top:6px;padding-left:23px}.mi-review-bottom{margin-top:16px;display:grid;grid-template-columns:.75fr .95fr 1.4fr;gap:16px}.mi-review-bottom article{padding:18px}.mi-review-bottom ol{margin:12px 0 0;padding:0;display:grid;gap:7px;list-style:none}.mi-review-bottom li{padding:8px;display:flex;align-items:center;gap:7px;border-radius:6px;background:#f7f9fc}.mi-review-bottom li span{width:24px;height:24px;display:grid;place-items:center;border-radius:50%;color:#fff;background:#a9b4c5}.mi-review-bottom li.done span{background:#1767ee}.mi-review-bottom article>p{margin-top:12px;line-height:1.8}.mi-review-bottom article:last-child{background:#f8fbff}
@media(max-width:1100px){.mi-report-stats{grid-template-columns:1fr 1fr}.mi-report-overview,.mi-report-lower,.mi-review-main,.mi-review-bottom{grid-template-columns:1fr}.mi-review-top{grid-template-columns:1fr}.mi-review-scores{grid-template-columns:repeat(3,1fr);gap:12px}.mi-review-scores>div{border:0}.mi-report-head{align-items:flex-start;flex-direction:column}}
@media(max-width:680px){.mi-report-stats{grid-template-columns:1fr}.mi-report-head>div:last-child{width:100%;display:grid;grid-template-columns:1fr 1fr}.mi-report-head .mi-primary{grid-column:1/3}.mi-report-overview{grid-template-columns:1fr}.mi-radar{display:none}.mi-report-lower{display:block}.mi-recommendations{margin-top:12px}.mi-question-table{overflow:auto}.mi-review-head{align-items:flex-start;flex-direction:column}.mi-review-head>div{align-items:flex-start;flex-wrap:wrap}.mi-review-head>div h1{font-size:20px}.mi-review-head>.mi-primary{width:100%}.mi-review-scores{grid-template-columns:repeat(2,1fr)}}
@media print{.sidebar,.topbar,.mi-breadcrumb,.mi-report-head>div:last-child{display:none!important}.main{margin-left:0!important}.mi-page{padding:0;background:#fff}.mi-card{break-inside:avoid}.mi-report-notice{margin-top:8px}}
</style>
