<script setup lang="ts">
/**
 * Interview report built only from what the model actually evaluated. Answers submitted while the
 * model was unavailable are listed as pending and can be evaluated again from here.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, ArrowLeft, CheckCircle2, ChevronRight, Hourglass, Link2, MessageCircle, Printer, RefreshCw, ShieldCheck } from 'lucide-vue-next'
import { formatWhen } from '@/shared/lib/datetime'
import { errorMessage } from '@/shared/api/types'
import { toast } from '@/shared/ui/toast'
import UiBanner from '@/shared/ui/UiBanner.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { evaluateMockInterview, fetchMockInterviewReport, retryMockInterview } from '../services/mockInterviewApi'
import type { MockInterviewReport, MockInterviewReview } from '../types'
import '../mock-interview.css'

const DIMENSIONS = [
  { key: 'structure', label: '表达结构' },
  { key: 'relevance', label: '岗位相关性' },
  { key: 'professional', label: '专业深度' },
  { key: 'evidence', label: '证据质量' },
  { key: 'expression', label: '表达清晰' },
] as const
const TYPE_LABELS: Record<string, string> = {
  COMPREHENSIVE: '综合面试', BEHAVIORAL: '行为面试', PROFESSIONAL: '专业面试', PRESSURE: '压力面试', QUICK: '快速热身',
}
const SOURCE_LABELS: Record<string, string> = {
  RESUME: '简历', POSITION: '目标岗位', CAREER_LIBRARY: '求职资料', JD: '岗位描述', JOB_MATCH: '岗位匹配报告',
}

const route = useRoute()
const router = useRouter()
const sessionId = computed(() => String(route.params.sessionId ?? ''))
const report = ref<MockInterviewReport | null>(null)
const loading = ref(true)
const loadError = ref<unknown>(null)
const retrying = ref(false)
const evaluating = ref(false)
const selectedQuestionId = computed(() => typeof route.query.question === 'string' ? route.query.question : '')
const selectedReview = computed(() => report.value?.questions.find((item) => item.question.id === selectedQuestionId.value) ?? null)

const evaluatedDimensions = computed(() => DIMENSIONS
  .filter((item) => typeof report.value?.dimensions[item.key] === 'number')
  .map((item) => ({ ...item, value: report.value!.dimensions[item.key] })))
const strengths = computed(() => list(report.value?.summary.strengths))
const risks = computed(() => list(report.value?.summary.risks))
const answeredTotal = computed(() => (report.value?.evaluatedCount ?? 0) + (report.value?.pendingCount ?? 0))
// The API omits null fields, so an unscored report has no overallScore at all.
const hasOverall = computed(() => typeof report.value?.overallScore === 'number')
const elapsedMinutes = computed(() => {
  const seconds = report.value?.session.elapsedSeconds ?? 0
  return seconds < 60 ? '<1' : String(Math.round(seconds / 60))
})

/** Radar geometry: five axes on a 240×240 canvas. */
const RADAR_CENTER = 120
const RADAR_RADIUS = 84
function radarPoint(index: number, ratio: number): string {
  const angle = -Math.PI / 2 + (index * Math.PI * 2) / DIMENSIONS.length
  return `${(RADAR_CENTER + Math.cos(angle) * RADAR_RADIUS * ratio).toFixed(1)},${(RADAR_CENTER + Math.sin(angle) * RADAR_RADIUS * ratio).toFixed(1)}`
}
const radarRings = [1, 0.66, 0.33].map((ratio) => DIMENSIONS.map((_, index) => radarPoint(index, ratio)).join(' '))
const radarAxes = DIMENSIONS.map((_, index) => radarPoint(index, 1).split(',').map(Number))
const radarShape = computed(() => DIMENSIONS
  .map((item, index) => radarPoint(index, (report.value?.dimensions[item.key] ?? 0) / 100)).join(' '))
const radarLabels = DIMENSIONS.map((item, index) => {
  const [x, y] = radarPoint(index, 1.24).split(',').map(Number)
  return { ...item, x, y }
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try { report.value = await fetchMockInterviewReport(sessionId.value) }
  catch (reason) { loadError.value = reason }
  finally { loading.value = false }
}

async function evaluatePending(): Promise<void> {
  if (!report.value || evaluating.value) return
  evaluating.value = true
  try {
    const result = await evaluateMockInterview(sessionId.value)
    if (!result.aiAvailable && result.evaluated === 0) {
      toast.warning('AI 通道仍不可用，回答已保存，请稍后再试。')
      return
    }
    report.value = await fetchMockInterviewReport(sessionId.value)
    if (result.pending > 0) toast.warning(`已评估 ${result.evaluated} 题，仍有 ${result.pending} 题待评估。`)
    else toast.success(`已评估 ${result.evaluated} 题，报告已更新。`)
  } catch (reason) {
    toast.error(errorMessage(reason, '重新评估失败'))
  } finally {
    evaluating.value = false
  }
}

async function retry(): Promise<void> {
  if (!report.value) return
  retrying.value = true
  try {
    const created = await retryMockInterview(report.value.session.id)
    await router.push({ name: 'mock-interview-session', params: { sessionId: created.session.id } })
  } catch (reason) { toast.error(errorMessage(reason, '再次练习创建失败')) }
  finally { retrying.value = false }
}

async function copyLink(): Promise<void> {
  try {
    await window.navigator.clipboard.writeText(window.location.href)
    toast.success('报告链接已复制，仅你本人登录后可以查看。')
  } catch {
    toast.error('浏览器不允许复制，请手动复制地址栏中的链接。')
  }
}

function printReport(): void { window.print() }
function openReview(item: MockInterviewReview): void { void router.push({ query: { question: item.question.id } }) }
function closeReview(): void { void router.push({ query: {} }) }
function list(value: unknown): string[] { return Array.isArray(value) ? value.map(String).filter(Boolean) : [] }
function isEvaluated(item: MockInterviewReview): boolean { return item.feedback.generationMode === 'AI_MODEL' && typeof item.scores.overall === 'number' }
function dimensionScores(item: MockInterviewReview) {
  return DIMENSIONS.filter((dimension) => typeof item.scores[dimension.key] === 'number')
    .map((dimension) => ({ ...dimension, value: item.scores[dimension.key] }))
}
function sourceLabel(value: string): string { return SOURCE_LABELS[value] ?? value }
function scoreTone(value: number): string { return value >= 80 ? 'is-good' : value >= 60 ? 'is-fair' : 'is-low' }

watch(sessionId, () => void load())
onMounted(load)
</script>

<template>
  <main class="mi-page mi-report">
    <div class="mi-shell">
      <section v-if="loading" class="mi-report__skeleton" aria-busy="true">
        <UiSkeleton height="28px" width="40%" />
        <UiSkeleton :lines="3" />
        <UiSkeleton height="220px" radius="var(--radius-lg)" />
      </section>

      <UiErrorState v-else-if="loadError || !report" :error="loadError" title="面试报告读取失败" @retry="load" />

      <template v-else-if="!selectedReview">
        <nav class="mi-report__crumbs" aria-label="面包屑"><RouterLink to="/mock-interviews">模拟面试</RouterLink><span>/</span><strong>面试报告</strong></nav>
        <header class="mi-report__head">
          <div>
            <h1>{{ report.session.positionName }} · {{ TYPE_LABELS[report.session.interviewType] ?? '模拟面试' }}报告</h1>
            <p>{{ [report.session.companyName, formatWhen(report.generatedAt), report.session.mode === 'VOICE' ? '语音模拟' : '文字模拟', `${report.session.questionCount} 题`].filter(Boolean).join(' · ') }}</p>
          </div>
          <div class="mi-report__actions">
            <UiButton variant="secondary" :icon="Printer" @click="printReport">打印</UiButton>
            <UiButton variant="secondary" :icon="Link2" @click="copyLink">复制链接</UiButton>
            <UiButton :icon="RefreshCw" :pending="retrying" @click="retry">再次练习</UiButton>
          </div>
        </header>

        <UiBanner v-if="report.pendingCount > 0" tone="warning" :title="`${report.pendingCount} 道题尚未评估`" class="mi-report__pending">
          这些回答提交时 AI 通道不可用，回答原文已保存；下方得分只统计已评估的 {{ report.evaluatedCount }} 道题。
          <template #actions><UiButton size="sm" variant="secondary" :icon="RefreshCw" :pending="evaluating" @click="evaluatePending">重新评估</UiButton></template>
        </UiBanner>

        <section class="mi-report__stats">
          <article class="mi-card">
            <small>综合得分</small>
            <strong v-if="hasOverall">{{ report.overallScore }}<em>/ 100</em></strong>
            <strong v-else class="is-pending">待评估</strong>
            <p>{{ hasOverall ? `基于 ${report.evaluatedCount} 道已评估的回答` : '还没有回答被 AI 评估' }}</p>
          </article>
          <article class="mi-card">
            <small>已评估</small>
            <strong>{{ report.evaluatedCount }}<em>/ {{ answeredTotal }} 题</em></strong>
            <p>{{ report.pendingCount ? `${report.pendingCount} 题等待 AI 恢复后评估` : '已提交的回答均已评估' }}</p>
          </article>
          <article class="mi-card">
            <small>完成度</small>
            <strong>{{ report.session.answeredCount }}<em>/ {{ report.session.questionCount }} 题</em></strong>
            <p>{{ report.session.answeredCount === report.session.questionCount ? '全部完成' : '提前结束的阶段报告' }}</p>
          </article>
          <article class="mi-card">
            <small>用时</small>
            <strong>{{ elapsedMinutes }}<em>分钟</em></strong>
            <p>计划 {{ report.session.durationMinutes ?? '—' }} 分钟，暂停期间不计</p>
          </article>
        </section>

        <section class="mi-report__overview">
          <article class="mi-card mi-report__radar">
            <h2>能力维度</h2>
            <template v-if="evaluatedDimensions.length">
              <svg viewBox="-30 -18 300 276" role="img" :aria-label="evaluatedDimensions.map((item) => `${item.label} ${item.value}`).join('，')">
                <polygon v-for="ring in radarRings" :key="ring" class="mi-radar__ring" :points="ring" />
                <line v-for="([x, y], index) in radarAxes" :key="index" class="mi-radar__axis" :x1="RADAR_CENTER" :y1="RADAR_CENTER" :x2="x" :y2="y" />
                <polygon class="mi-radar__shape" :points="radarShape" />
                <text v-for="item in radarLabels" :key="item.key" class="mi-radar__label" :x="item.x" :y="item.y" text-anchor="middle" dominant-baseline="middle">
                  {{ item.label }} {{ report.dimensions[item.key] ?? '—' }}
                </text>
              </svg>
            </template>
            <p v-else class="mi-report__empty"><Hourglass :size="18" />AI 评估后显示各维度得分</p>
          </article>

          <article class="mi-card mi-report__summary">
            <h2>训练总结</h2>
            <template v-if="strengths.length || risks.length">
              <section v-if="strengths.length">
                <h3><CheckCircle2 :size="16" />做得好的</h3>
                <ul><li v-for="item in strengths" :key="item">{{ item }}</li></ul>
              </section>
              <section v-if="risks.length" class="is-risk">
                <h3><AlertTriangle :size="16" />需要加强</h3>
                <ul><li v-for="item in risks" :key="item">{{ item }}</li></ul>
              </section>
            </template>
            <p v-else class="mi-report__empty"><Hourglass :size="18" />总结来自 AI 对每道题的点评，评估后显示</p>
          </article>
        </section>

        <section class="mi-report__lower">
          <article class="mi-card mi-report__questions">
            <h2>逐题复盘</h2>
            <ol>
              <li v-for="item in report.questions" :key="item.question.id">
                <button type="button" @click="openReview(item)">
                  <span class="mi-report__qno">Q{{ item.question.orderNo }}</span>
                  <span class="mi-report__prompt">{{ item.question.prompt }}</span>
                  <span v-if="!item.answer" class="mi-report__score is-muted">未作答</span>
                  <span v-else-if="isEvaluated(item)" class="mi-report__score" :class="scoreTone(item.scores.overall)">{{ item.scores.overall }}</span>
                  <span v-else class="mi-report__score is-pending">待评估</span>
                  <ChevronRight :size="15" aria-hidden="true" />
                </button>
              </li>
            </ol>
          </article>

          <article v-if="report.recommendations.length" class="mi-card mi-report__followups">
            <h2>可能被追问的问题</h2>
            <p>来自 AI 对你回答的点评，可以作为下一次练习的准备方向。</p>
            <ul><li v-for="item in report.recommendations" :key="item"><MessageCircle :size="15" />{{ item }}</li></ul>
            <UiButton variant="secondary" :icon="RefreshCw" :pending="retrying" @click="retry">用同样设置再练一次</UiButton>
          </article>
        </section>

        <p class="mi-report__notice"><ShieldCheck :size="15" />报告只基于本次授权资料与回答，不评估受保护特征，也不代表真实招聘结果或录用概率。</p>
      </template>

      <template v-else>
        <nav class="mi-report__crumbs" aria-label="面包屑"><RouterLink to="/mock-interviews">模拟面试</RouterLink><span>/</span><button type="button" @click="closeReview">面试报告</button><span>/</span><strong>Q{{ selectedReview.question.orderNo }}</strong></nav>
        <header class="mi-report__head">
          <div class="mi-report__review-title">
            <UiButton variant="ghost" size="sm" :icon="ArrowLeft" @click="closeReview">返回报告</UiButton>
            <h1>Q{{ selectedReview.question.orderNo }} 逐题复盘</h1>
            <span class="mi-tag mi-tag--gray">{{ selectedReview.answer?.mode === 'VOICE' ? '语音回答（浏览器转写）' : '文字回答' }}</span>
          </div>
        </header>

        <section class="mi-review__top">
          <article class="mi-card mi-review__question">
            <small>问题</small>
            <h2>{{ selectedReview.question.prompt }}</h2>
            <p v-if="selectedReview.question.sourceRefs.length">依据：<span v-for="source in selectedReview.question.sourceRefs" :key="source" class="mi-tag mi-tag--gray">{{ sourceLabel(source) }}</span></p>
          </article>
          <article v-if="isEvaluated(selectedReview)" class="mi-card mi-review__scores">
            <div class="is-overall"><small>本题得分</small><strong>{{ selectedReview.scores.overall }}</strong></div>
            <div v-for="item in dimensionScores(selectedReview)" :key="item.key"><small>{{ item.label }}</small><strong>{{ item.value }}</strong></div>
          </article>
          <article v-else-if="selectedReview.answer" class="mi-card mi-review__scores is-pending">
            <Hourglass :size="20" />
            <p><strong>本题待评估</strong><small>{{ String(selectedReview.feedback.modelNotice || '提交时 AI 通道不可用。') }}</small></p>
            <UiButton size="sm" variant="secondary" :icon="RefreshCw" :pending="evaluating" @click="evaluatePending">重新评估</UiButton>
          </article>
        </section>

        <section class="mi-review__main" :class="{ 'is-single': !isEvaluated(selectedReview) }">
          <article class="mi-card mi-review__answer">
            <h2>你的回答</h2>
            <p>{{ selectedReview.answer?.answer || '本题未作答' }}</p>
          </article>
          <article v-if="isEvaluated(selectedReview)" class="mi-card mi-review__feedback">
            <h2>AI 点评</h2>
            <section v-if="list(selectedReview.feedback.strengths).length">
              <h3><CheckCircle2 :size="16" />做得好的</h3>
              <ul><li v-for="item in list(selectedReview.feedback.strengths)" :key="item">{{ item }}</li></ul>
            </section>
            <section v-if="list(selectedReview.feedback.improvements).length" class="is-risk">
              <h3><AlertTriangle :size="16" />需要加强</h3>
              <ul><li v-for="item in list(selectedReview.feedback.improvements)" :key="item">{{ item }}</li></ul>
            </section>
            <section v-if="selectedReview.feedback.suggestedFollowUp">
              <h3><MessageCircle :size="16" />可能的追问</h3>
              <p>{{ String(selectedReview.feedback.suggestedFollowUp) }}</p>
            </section>
          </article>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.mi-report__skeleton { display: grid; gap: var(--space-4); }
.mi-report__crumbs { margin-bottom: var(--space-4); display: flex; align-items: center; gap: var(--space-2); color: var(--text-tertiary); font-size: var(--fs-sm); }
.mi-report__crumbs a, .mi-report__crumbs button { padding: 0; border: 0; color: var(--text-secondary); background: transparent; font: inherit; text-decoration: none; }
.mi-report__crumbs a:hover, .mi-report__crumbs button:hover { color: var(--color-primary-text); }
.mi-report__crumbs strong { color: var(--text-primary); font-weight: 600; }
.mi-report__head { margin-bottom: var(--space-4); display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-4); }
.mi-report__head h1 { font-size: var(--fs-h1); line-height: var(--lh-h1); font-weight: 700; }
.mi-report__head p { margin-top: 4px; color: var(--text-secondary); font-size: var(--fs-sm); }
.mi-report__actions { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.mi-report__pending { margin-bottom: var(--space-4); }

.mi-report__stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-4); }
.mi-report__stats article { padding: var(--space-5); display: grid; gap: 6px; }
.mi-report__stats small { color: var(--text-secondary); font-size: var(--fs-xs); }
.mi-report__stats strong { font-size: 30px; font-weight: 700; font-variant-numeric: tabular-nums; line-height: 1.1; }
.mi-report__stats strong em { margin-left: 4px; color: var(--text-tertiary); font-size: var(--fs-sm); font-style: normal; font-weight: 500; }
.mi-report__stats strong.is-pending { color: var(--color-warning-text); font-size: 24px; }
.mi-report__stats p { color: var(--text-tertiary); font-size: var(--fs-xs); }

.mi-report__overview { margin-top: var(--space-4); display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); gap: var(--space-4); }
.mi-report__lower { margin-top: var(--space-4); display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr); gap: var(--space-4); align-items: start; }
.mi-report__radar, .mi-report__summary, .mi-report__questions, .mi-report__followups { padding: var(--space-5); }
.mi-card h2 { margin-bottom: var(--space-3); font-size: var(--fs-h3); font-weight: 650; }
.mi-report__radar svg { display: block; width: 100%; max-width: 320px; margin: 0 auto; overflow: visible; }
.mi-radar__ring { fill: none; stroke: var(--border-default); }
.mi-radar__axis { stroke: var(--border-subtle); }
.mi-radar__shape { fill: color-mix(in srgb, var(--color-primary) 16%, transparent); stroke: var(--color-primary); stroke-width: 2; stroke-linejoin: round; }
.mi-radar__label { fill: var(--text-secondary); font-size: 11px; }
.mi-report__empty { min-height: 160px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-2); color: var(--text-tertiary); font-size: var(--fs-sm); text-align: center; }

.mi-report__summary section, .mi-review__feedback section { padding: var(--space-3) 0; border-top: 1px solid var(--border-subtle); }
.mi-report__summary section:first-of-type, .mi-review__feedback section:first-of-type { border-top: 0; padding-top: 0; }
.mi-report__summary h3, .mi-review__feedback h3 { margin-bottom: var(--space-2); display: flex; align-items: center; gap: 6px; color: var(--color-success-text); font-size: var(--fs-sm); font-weight: 650; }
.is-risk h3 { color: var(--color-warning-text); }
.mi-report__summary ul, .mi-review__feedback ul { margin: 0; padding-left: 22px; display: grid; gap: 6px; font-size: var(--fs-sm); line-height: var(--lh-sm); }
.mi-review__feedback section p { font-size: var(--fs-sm); line-height: var(--lh-sm); }

.mi-report__questions ol { margin: 0; padding: 0; list-style: none; }
.mi-report__questions li + li { border-top: 1px solid var(--border-subtle); }
.mi-report__questions button {
  width: 100%;
  padding: 10px 6px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-3);
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  background: transparent;
  text-align: left;
}
.mi-report__questions button:hover { background: var(--surface-2); }
.mi-report__questions button > svg { color: var(--text-tertiary); }
.mi-report__qno { color: var(--text-tertiary); font-family: var(--font-mono); font-size: var(--fs-xs); font-weight: 600; }
.mi-report__prompt { overflow: hidden; font-size: var(--fs-sm); text-overflow: ellipsis; white-space: nowrap; }
.mi-report__score { min-width: 52px; font-size: var(--fs-sm); font-variant-numeric: tabular-nums; font-weight: 700; text-align: right; }
.mi-report__score.is-good { color: var(--color-success-text); }
.mi-report__score.is-fair { color: var(--color-primary-text); }
.mi-report__score.is-low { color: var(--color-danger-text); }
.mi-report__score.is-pending { color: var(--color-warning-text); font-weight: 600; }
.mi-report__score.is-muted { color: var(--text-tertiary); font-weight: 500; }
.mi-report__followups > p { margin-bottom: var(--space-3); color: var(--text-secondary); font-size: var(--fs-xs); }
.mi-report__followups ul { margin: 0 0 var(--space-4); padding: 0; display: grid; gap: var(--space-2); list-style: none; }
.mi-report__followups li { padding: 10px 12px; display: flex; gap: 8px; border-radius: var(--radius-sm); background: var(--surface-2); font-size: var(--fs-sm); line-height: var(--lh-sm); }
.mi-report__followups li svg { flex: none; margin-top: 3px; color: var(--color-primary-text); }
.mi-report__notice { margin-top: var(--space-4); display: flex; align-items: center; gap: 6px; color: var(--text-tertiary); font-size: var(--fs-xs); }

.mi-report__review-title { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.mi-review__top { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); gap: var(--space-4); }
.mi-review__question { padding: var(--space-5); }
.mi-review__question small { color: var(--text-tertiary); font-size: var(--fs-xs); }
.mi-review__question h2 { margin: 6px 0 var(--space-3); font-size: 17px; line-height: 1.6; }
.mi-review__question p { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; color: var(--text-secondary); font-size: var(--fs-xs); }
.mi-review__scores { padding: var(--space-5); display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-4); }
.mi-review__scores > div { display: grid; gap: 2px; }
.mi-review__scores small { color: var(--text-tertiary); font-size: var(--fs-xs); }
.mi-review__scores strong { font-size: 22px; font-variant-numeric: tabular-nums; }
.mi-review__scores .is-overall strong { color: var(--color-primary-text); font-size: 32px; }
.mi-review__scores.is-pending { display: flex; align-items: center; gap: var(--space-3); color: var(--color-warning-text); }
.mi-review__scores.is-pending p { flex: 1; display: grid; gap: 4px; }
.mi-review__scores.is-pending strong { font-size: var(--fs-body); }
.mi-review__scores.is-pending small { color: var(--text-secondary); }
.mi-review__main { margin-top: var(--space-4); display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: var(--space-4); align-items: start; }
.mi-review__main.is-single { grid-template-columns: 1fr; }
.mi-review__answer, .mi-review__feedback { padding: var(--space-5); }
.mi-review__answer p { min-height: 180px; padding: var(--space-4); border-radius: var(--radius-md); background: var(--surface-2); font-size: var(--fs-body); line-height: 1.8; white-space: pre-wrap; }

@media (max-width: 1100px) {
  .mi-report__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .mi-report__overview, .mi-report__lower, .mi-review__top, .mi-review__main { grid-template-columns: 1fr; }
  .mi-report__head { flex-direction: column; align-items: flex-start; }
}
@media (max-width: 640px) {
  .mi-report__stats { grid-template-columns: 1fr 1fr; gap: var(--space-3); }
  .mi-report__stats article { padding: var(--space-4); }
  .mi-report__stats strong { font-size: 24px; }
  .mi-report__actions { width: 100%; }
  .mi-review__scores { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media print {
  .mi-report__crumbs, .mi-report__actions, .mi-report__pending :deep(.ui-banner__actions) { display: none !important; }
  .mi-page { padding: 0; background: var(--surface-1); }
  .mi-card { break-inside: avoid; box-shadow: none; }
}
</style>
