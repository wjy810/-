<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, ArrowRight, BookOpenCheck, Check, CheckCircle2, ChevronRight, Download, FilePenLine, FileSearch, GitCompareArrows, GraduationCap, History, LoaderCircle, MessageCircle, RefreshCw, ShieldCheck, Target, ThumbsUp, TriangleAlert } from 'lucide-vue-next'
import AppDrawer from '@/shared/ui/AppDrawer.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { toast, useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { compareMatchReportVersions, createReportExport, createResumeOptimization, downloadReportExport, fetchJobMatch, fetchMatchReport, fetchMatchReportVersions, updateImprovement } from '../services/jobMatchApi'
import type { ImprovementTask, JobMatch, JobMatchClaim, JobMatchReport, JobMatchReportComparison, JobMatchReportVersion } from '../types'
import { displayJobMatchText, evidenceSourceLabel, jobMatchDisplayLabels, jobMatchStatusTone, recommendationLabel } from '../utils/reportPresentation'
import { clampMatchScore, JOB_MATCH_REPORT_TABS, reportTabFromQuery, type JobMatchReportTab } from '../utils/uiPresentation'
import '../job-match.css'

type Item = Record<string, unknown>
type NextStep = { key: string; title: string; desc: string }

const route = useRoute()
const router = useRouter()
const matchId = computed(() => String(route.params.id))
const match = ref<JobMatch | null>(null)
const report = ref<JobMatchReport | null>(null)
const pending = ref(false)
const actionError = ref('')
const activeTab = ref<JobMatchReportTab>('overview')
const evidenceOpen = ref(false)
const exportOpen = ref(false)
const versionsOpen = ref(false)
const versions = ref<JobMatchReportVersion[]>([])
const versionsError = ref<unknown>(null)
const versionsLoading = ref(false)
const comparison = ref<JobMatchReportComparison | null>(null)
const compareFrom = ref(0)
const compareTo = ref(0)
const selectedClaim = ref<JobMatchClaim | null>(null)
const exportFormat = ref('PDF')
useToastFeedback(actionError, 'error', 'job-match-report-error')
const tabs = JOB_MATCH_REPORT_TABS

const { error: loadError, loading, loaded, load } = useLoadState(async () => {
  const [value, document] = await Promise.all([fetchJobMatch(matchId.value), fetchMatchReport(matchId.value)])
  match.value = value
  report.value = document
  activeTab.value = reportTabFromQuery(route.query.tab)
  return document
})

const doc = computed(() => report.value?.report)
const hasScore = computed(() => typeof doc.value?.score === 'number')
const matchScore = computed(() => clampMatchScore(doc.value?.score))
const confidence = computed(() => (typeof doc.value?.confidence === 'number' ? doc.value.confidence : null))
const dimensionItems = computed(() => {
  const dimensions = doc.value?.dimensions ?? {}
  const hardGate = typeof doc.value?.hardGatePassed === 'boolean' ? (doc.value.hardGatePassed ? 100 : 0) : null
  const value = (key: string) => (typeof dimensions[key] === 'number' ? clampMatchScore(dimensions[key]) : null)
  return [
    { key: 'hardGate', label: '硬性资格', value: hardGate },
    { key: 'coreSkills', label: '核心技能', value: value('coreSkills') },
    { key: 'experience', label: '经历相关度', value: value('experience') },
    { key: 'evidence', label: '证据强度', value: value('evidence') },
    { key: 'resumeExpression', label: '简历表达', value: value('resumeExpression') },
    { key: 'growthCost', label: '成长成本', value: value('growthCost') },
  ]
})
const strengths = computed(() => doc.value?.ai?.strengths ?? doc.value?.ruleStrengths ?? [])
const gaps = computed(() => doc.value?.ai?.gaps ?? doc.value?.ruleGaps ?? [])
const matrix = computed(() => doc.value?.ai?.evidenceMatrix ?? doc.value?.requirements ?? [])
const hardGates = computed(() => doc.value?.ai?.hardGates ?? doc.value?.requirements?.filter(value => value.hardGate) ?? [])
const recommendation = computed(() => doc.value?.ai?.recommendation)
const recommendationText = computed(() => recommendationLabel(recommendation.value?.code))
const requirementsById = computed(() => new Map((doc.value?.requirements ?? []).map(item => [String(item.requirementId ?? ''), item])))
const matchRequirementsById = computed(() => new Map((match.value?.requirements ?? []).map(item => [item.id, item])))
const selectedRequirement = computed(() => matchRequirementsById.value.get(String(selectedClaim.value?.requirementId ?? '')))
const selectedRequirementTitle = computed(() => {
  if (!selectedClaim.value) return '请选择一项结论'
  const fromReport = requirementsById.value.get(String(selectedClaim.value.requirementId ?? ''))
  return (fromReport ? optionalStr(fromReport, 'text') : '') || selectedRequirement.value?.text || '未找到对应岗位要求'
})
const interviewTopics = computed(() => (doc.value?.ai?.interviewTopics ?? [])
  .map(topic => (typeof topic === 'string' ? displayText(topic) : optionalStr(topic, 'topic', 'title', 'question')))
  .filter(Boolean))
/** Next steps only from what this report actually produced. */
const nextSteps = computed<NextStep[]>(() => {
  const steps: NextStep[] = []
  const suggestions = doc.value?.ai?.resumeSuggestions?.length ?? 0
  const tasks = report.value?.learningPlan.length ?? 0
  if (suggestions) steps.push({ key: 'resume', title: `${suggestions} 条简历修改建议`, desc: '在简历工作台中逐条确认是否采纳。' })
  if (tasks) steps.push({ key: 'plan', title: `${tasks} 项提升任务`, desc: '针对已确认的能力缺口，附产物与验收标准。' })
  if (interviewTopics.value.length) steps.push({ key: 'interview', title: `${interviewTopics.value.length} 个面试准备主题`, desc: interviewTopics.value.slice(0, 3).join('、') })
  return steps
})

const displayText = displayJobMatchText
function optionalStr(item: Item, ...keys: string[]) {
  for (const key of keys) {
    const value = item[key]
    if (typeof value === 'string' && value.trim()) {
      const text = displayText(value)
      if (text) return text
    }
  }
  return ''
}
function str(item: Item, ...keys: string[]) { return optionalStr(item, ...keys) || '暂无说明' }
function reportItemTitle(item: Item) {
  const direct = optionalStr(item, 'title', 'text')
  if (direct) return direct
  const requirement = requirementsById.value.get(String(item.requirementId ?? ''))
  const requirementText = requirement ? optionalStr(requirement, 'text') : ''
  if (requirementText) return requirementText
  return jobMatchDisplayLabels[String(item.type ?? '')] ?? '岗位要求'
}
function numberOrNull(item: Item, key: string) { const value = item[key]; return typeof value === 'number' && Number.isFinite(value) ? value : null }
function list(item: Item, key: string) { return Array.isArray(item[key]) ? item[key] as unknown[] : [] }
function matrixList(item: Item, key: string) {
  const direct = list(item, key)
  if (direct.length) return direct
  const requirement = requirementsById.value.get(String(item.requirementId ?? ''))
  return requirement ? list(requirement, key) : []
}
function matrixRawStatus(item: Item) {
  const direct = item.status ?? item.factStatus
  if (typeof direct === 'string') return direct
  const requirement = requirementsById.value.get(String(item.requirementId ?? ''))
  return requirement?.status ?? requirement?.factStatus
}
function matrixStatus(item: Item) { return displayText(matrixRawStatus(item)) || '待核实' }
function sourceLabel(id: unknown) { return evidenceSourceLabel(id, report.value?.evidenceSources) }
function claimFor(item: Item, strength: boolean) {
  const requirementId = String(item.requirementId ?? '')
  const claims = report.value?.claims ?? []
  return claims.find(claim => claim.requirementId === requirementId && (claim.conclusionType === 'STRENGTH') === strength)
    ?? claims.find(claim => claim.requirementId === requirementId)
}

async function setActiveTab(tab: JobMatchReportTab, focusContent = false) {
  activeTab.value = tab
  await router.replace({ query: { ...route.query, tab: tab === 'overview' ? undefined : tab } })
  if (focusContent) {
    await nextTick()
    document.querySelector<HTMLElement>('.jm-report-content [data-report-heading]')?.focus()
  }
}
function onReportTabKeydown(event: KeyboardEvent, current: JobMatchReportTab) {
  const index = tabs.findIndex(tab => tab.id === current)
  let next = index
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length
  else if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = tabs.length - 1
  else return
  event.preventDefault()
  void setActiveTab(tabs[next].id)
  ;(event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus()
}
function openClaim(claim?: JobMatchClaim | null) {
  selectedClaim.value = claim ?? report.value?.claims[0] ?? null
  evidenceOpen.value = true
}
function openStep(step: NextStep) {
  if (step.key === 'resume') void optimize()
  else if (step.key === 'plan') void setActiveTab('plan', true)
  else void router.push({ name: 'mock-interview-create', query: { jobMatchId: match.value?.id } })
}
async function optimize() {
  if (!match.value) return
  pending.value = true
  try {
    const link = await createResumeOptimization(match.value.id, match.value.version)
    await router.push(link.path)
  } catch (reason) {
    actionError.value = errorMessage(reason, '简历优化会话创建失败')
  } finally {
    pending.value = false
  }
}
async function exportReport() {
  if (!match.value) return
  pending.value = true
  actionError.value = ''
  try {
    const created = await createReportExport(match.value.id, exportFormat.value, match.value.version)
    if (created.status === 'COMPLETED') {
      const downloaded = await downloadReportExport(created.id)
      const url = URL.createObjectURL(downloaded.blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = downloaded.filename || `JobProof-岗位匹配报告.${exportFormat.value.toLowerCase()}`
      anchor.click()
      URL.revokeObjectURL(url)
    } else {
      toast.info('导出仍在处理中，请稍后再试一次。')
    }
    exportOpen.value = false
  } catch (reason) {
    actionError.value = errorMessage(reason, '报告导出失败')
  } finally {
    pending.value = false
  }
}
async function setTask(task: ImprovementTask, status: string) {
  if (!match.value) return
  try {
    const updated = await updateImprovement(match.value.id, task.id, status, task.version)
    const index = report.value?.learningPlan.findIndex(item => item.id === task.id) ?? -1
    if (report.value && index >= 0) report.value.learningPlan[index] = updated
  } catch (reason) {
    actionError.value = errorMessage(reason, '学习任务更新失败')
  }
}
async function loadVersions() {
  versionsLoading.value = true
  try {
    versions.value = await fetchMatchReportVersions(matchId.value)
    versionsError.value = null
  } catch (reason) {
    versionsError.value = reason
  } finally {
    versionsLoading.value = false
  }
}
async function openVersions() {
  versionsOpen.value = true
  comparison.value = null
  if (versionsError.value || !versions.value.length) await loadVersions()
  if (versions.value.length > 1) {
    compareFrom.value = versions.value[1].version
    compareTo.value = versions.value[0].version
    await compareVersions()
  }
}
async function compareVersions() {
  if (!match.value || !compareFrom.value || !compareTo.value) return
  pending.value = true
  try {
    comparison.value = await compareMatchReportVersions(match.value.id, compareFrom.value, compareTo.value)
  } catch (reason) {
    actionError.value = errorMessage(reason, '报告版本比较失败')
  } finally {
    pending.value = false
  }
}
onMounted(() => {
  void load()
  void loadVersions()
})
</script>

<template><main class="jm-page"><div class="jm-shell">
  <PageState :loading="loading" :error="loadError" :loaded="loaded" error-title="岗位匹配报告读取失败" @retry="load">
    <template #skeleton>
      <section class="jm-card jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在读取岗位匹配报告</section>
    </template>

    <header class="jm-report-head">
      <div class="jm-report-head__top">
        <div class="jm-report-title">
          <h1>岗位匹配报告</h1>
          <p>
            <strong>{{ match?.title }}<template v-if="match?.company"> · {{ match.company }}</template></strong>
            <AppTag :tone="match?.status === 'COMPLETED' ? 'green' : 'orange'">{{ match?.status === 'COMPLETED' ? '分析完成' : '历史报告可用' }}</AppTag>
            <AppTag v-if="confidence !== null" tone="green">可信度 {{ confidence }}%</AppTag>
            <AppTag v-if="report?.updatedAt" tone="gray">{{ new Date(report.updatedAt).toLocaleDateString('zh-CN') }}</AppTag>
          </p>
        </div>
        <div class="jm-report-actions">
          <button class="jm-secondary" type="button" aria-label="重新分析" @click="router.push({ name: 'job-match-new', query: { id: match?.id } })"><RefreshCw :size="16" /><span>重新分析</span></button>
          <button class="jm-secondary" type="button" aria-label="版本记录" @click="openVersions"><History :size="16" /><span>版本</span></button>
          <button class="jm-secondary" type="button" aria-label="导出报告" @click="exportOpen = true"><Download :size="16" /><span>导出报告</span></button>
          <button class="jm-primary" type="button" :disabled="pending" @click="optimize"><FilePenLine :size="16" />优化简历</button>
        </div>
      </div>
      <nav class="jm-tabs" role="tablist" aria-label="报告视图">
        <button
          v-for="tab in tabs"
          :id="`jm-report-tab-${tab.id}`"
          :key="tab.id"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.id"
          :aria-controls="`jm-report-panel-${tab.id}`"
          :tabindex="activeTab === tab.id ? 0 : -1"
          :class="{ 'is-active': activeTab === tab.id }"
          @click="setActiveTab(tab.id)"
          @keydown="onReportTabKeydown($event, tab.id)"
        >{{ tab.label }}</button>
      </nav>
    </header>

    <Transition name="jm-report-switch" mode="out-in">
      <div :id="`jm-report-panel-${activeTab}`" :key="activeTab" class="jm-report-content" role="tabpanel" :aria-labelledby="`jm-report-tab-${activeTab}`">
        <template v-if="activeTab === 'overview'">
          <section class="jm-card jm-report-overview">
            <div class="jm-report-score">
              <div class="jm-score-large" :class="{ 'is-empty': !hasScore }" :style="{ '--score': String(hasScore ? matchScore : 0) }">
                <p>
                  <strong><span class="jm-score-value">{{ hasScore ? matchScore : '—' }}</span><small v-if="hasScore">%</small></strong>
                  <span class="jm-score-label">综合匹配度</span>
                </p>
              </div>
            </div>
            <div class="jm-recommendation">
              <h3>匹配结论</h3>
              <strong v-if="recommendationText"><ThumbsUp :size="16" />{{ recommendationText }}</strong>
              <p v-if="recommendation?.rationale">{{ displayText(recommendation.rationale) }}</p>
              <p v-if="!recommendationText && !recommendation?.rationale">这份报告没有给出投递结论。</p>
            </div>
            <article v-for="item in dimensionItems" :key="item.key" class="jm-dimension">
              <small>{{ item.label }}</small>
              <strong>{{ item.value ?? '—' }}</strong>
              <i :style="{ '--bar': String(item.value ?? 0) }" />
            </article>
          </section>

          <section class="jm-overview-grid">
            <article class="jm-card jm-report-panel">
              <h2>硬性条件</h2>
              <div class="jm-list">
                <div
                  v-for="(item, index) in hardGates"
                  :key="index"
                  class="jm-list__row"
                  :class="{ 'is-unmet': jobMatchStatusTone(matrixRawStatus(item)) !== 'green' }"
                >
                  <span><Check v-if="jobMatchStatusTone(matrixRawStatus(item)) === 'green'" :size="16" /><TriangleAlert v-else :size="16" /></span>
                  <p><strong>{{ reportItemTitle(item) }}</strong><small v-if="optionalStr(item, 'explanation')">{{ optionalStr(item, 'explanation') }}</small></p>
                  <AppTag :tone="jobMatchStatusTone(matrixRawStatus(item))">{{ matrixStatus(item) }}</AppTag>
                </div>
                <div v-if="!hardGates.length" class="jm-list__row">
                  <span><ShieldCheck :size="16" /></span>
                  <p><strong>JD 中没有识别出单独的硬性门槛</strong><small>这不代表岗位没有要求，请结合原文确认。</small></p>
                </div>
              </div>
            </article>

            <article class="jm-card jm-report-panel">
              <h2>关键结论</h2>
              <div class="jm-conclusion-list">
                <button class="jm-conclusion-item is-strength" type="button" @click="setActiveTab('strengths', true)">
                  <span><ThumbsUp :size="17" /></span>
                  <p><strong>{{ strengths.length }} 项匹配优势</strong><small>在简历或授权资料中找到依据的要求</small></p>
                  <ChevronRight :size="18" />
                </button>
                <button class="jm-conclusion-item is-gap" type="button" @click="setActiveTab('gaps', true)">
                  <span><TriangleAlert :size="17" /></span>
                  <p><strong>{{ gaps.length }} 项需要处理</strong><small>能力、证据、表达与客观限制分别处理</small></p>
                  <ChevronRight :size="18" />
                </button>
                <button class="jm-conclusion-item is-evidence" type="button" @click="setActiveTab('evidence', true)">
                  <span><FileSearch :size="17" /></span>
                  <p><strong>{{ report?.claims.length ?? 0 }} 项可溯源结论</strong><small>逐条查看岗位要求、简历和资料库引用</small></p>
                  <ChevronRight :size="18" />
                </button>
              </div>
            </article>

            <article class="jm-card jm-report-panel">
              <h2>下一步</h2>
              <div v-if="nextSteps.length" class="jm-list">
                <button v-for="(step, index) in nextSteps" :key="step.key" class="jm-list__row" type="button" @click="openStep(step)">
                  <span class="jm-step-index">{{ index + 1 }}</span>
                  <p><strong>{{ step.title }}</strong><small>{{ step.desc }}</small></p>
                </button>
              </div>
              <p v-else class="jm-field-hint">这份报告没有附带简历修改建议、提升任务或面试主题。</p>
              <button class="jm-primary jm-report-cta" type="button" :disabled="pending" @click="optimize"><FilePenLine :size="16" />开始优化简历</button>
              <button class="jm-secondary jm-report-cta" type="button" @click="router.push({ name: 'mock-interview-create', query: { jobMatchId: match?.id } })"><MessageCircle :size="16" />生成模拟面试</button>
            </article>
          </section>

          <div v-if="report?.claims.length" class="jm-privacy">
            <FileSearch :size="18" />
            <p><strong>每项结论都可以查看依据</strong><span>查看对应的岗位要求原文、分析理由和引用的资料。</span></p>
            <button class="jm-secondary" type="button" @click="openClaim()">查看依据 <ArrowRight :size="15" /></button>
          </div>
        </template>

        <section v-else class="jm-card jm-report-body">
          <header class="jm-report-body__head">
            <div>
              <h2 data-report-heading tabindex="-1">{{ tabs.find(item => item.id === activeTab)?.label }}</h2>
              <p>{{ activeTab === 'strengths' ? '确认哪些能力已经被事实支撑' : activeTab === 'gaps' ? '区分真正能力缺口与材料表达问题' : activeTab === 'evidence' ? '逐条查看岗位要求、简历和资料证据' : '将确认的能力缺口拆成可验收任务' }}</p>
            </div>
            <button v-if="activeTab === 'gaps'" class="jm-secondary" type="button" @click="router.push({ name: 'job-match-similar', params: { id: match?.id } })"><Target :size="16" />查看相似方向</button>
          </header>

          <div v-if="activeTab === 'strengths'" class="jm-detail-list">
            <article v-for="(item, index) in strengths" :key="index" class="jm-detail-item">
              <span><CheckCircle2 :size="19" /></span>
              <div>
                <h3>{{ reportItemTitle(item) }}</h3>
                <p>{{ str(item, 'explanation', 'reasoning') }}</p>
                <div v-if="list(item, 'evidenceIds').length" class="jm-chip-row">
                  <span v-for="source in list(item, 'evidenceIds')" :key="String(source)" class="jm-chip">{{ sourceLabel(source) }}</span>
                </div>
              </div>
              <aside>
                <AppTag v-if="numberOrNull(item, 'score') !== null" tone="green">{{ numberOrNull(item, 'score') }} 分</AppTag>
                <button v-if="claimFor(item, true)" class="jm-text-btn" type="button" @click="openClaim(claimFor(item, true))">查看依据</button>
              </aside>
            </article>
            <div v-if="!strengths.length" class="jm-empty">
              <div><span><FileSearch :size="30" /></span><h3>暂无有充分证据的匹配优势</h3><p>这不代表没有能力，可以在“证据覆盖”中查看还缺哪些材料。</p></div>
            </div>
          </div>

          <div v-else-if="activeTab === 'gaps'" class="jm-detail-list">
            <article v-for="(item, index) in gaps" :key="index" class="jm-detail-item">
              <span class="is-gap"><AlertTriangle :size="19" /></span>
              <div>
                <h3>{{ reportItemTitle(item) }}</h3>
                <p>{{ str(item, 'impact', 'explanation') }}</p>
                <div class="jm-chip-row">
                  <span v-if="optionalStr(item, 'type')" class="jm-chip">{{ optionalStr(item, 'type') }}</span>
                  <span v-if="optionalStr(item, 'priority')" class="jm-chip">{{ optionalStr(item, 'priority') }}</span>
                  <span v-if="optionalStr(item, 'estimatedTime')" class="jm-chip">{{ optionalStr(item, 'estimatedTime') }}</span>
                </div>
              </div>
              <aside><button v-if="claimFor(item, false)" class="jm-text-btn" type="button" @click="openClaim(claimFor(item, false))">查看依据</button></aside>
            </article>
            <div v-if="!gaps.length" class="jm-empty">
              <div><span><CheckCircle2 :size="30" /></span><h3>这份报告没有列出需要处理的缺口</h3><p>仍建议对照岗位原文确认关键要求。</p></div>
            </div>
          </div>

          <div v-else-if="activeTab === 'evidence'" class="jm-history-table-wrap">
            <table class="jm-matrix">
              <thead><tr><th>岗位要求</th><th>简历证据</th><th>资料库证据</th><th>事实状态</th><th>可信度</th><th></th></tr></thead>
              <tbody>
                <tr v-for="(item, index) in matrix" :key="index">
                  <td>{{ reportItemTitle(item) }}</td>
                  <td>{{ matrixList(item, 'resumeRefs').length ? `${matrixList(item, 'resumeRefs').length} 处简历引用` : '暂未发现证据' }}</td>
                  <td>{{ matrixList(item, 'evidenceIds').length ? `${matrixList(item, 'evidenceIds').length} 项授权资料` : '未授权或未发现' }}</td>
                  <td><AppTag :tone="jobMatchStatusTone(matrixRawStatus(item))">{{ matrixStatus(item) }}</AppTag></td>
                  <td>{{ numberOrNull(item, 'confidence') !== null ? `${numberOrNull(item, 'confidence')}%` : '—' }}</td>
                  <td><button v-if="claimFor(item, true)" class="jm-text-btn" type="button" @click="openClaim(claimFor(item, true))">查看依据</button></td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-else class="jm-detail-list">
            <article v-for="task in report?.learningPlan" :key="task.id" class="jm-detail-item">
              <span class="is-plan"><GraduationCap :size="19" /></span>
              <div>
                <h3>{{ task.title }}</h3>
                <p>{{ task.task }}</p>
                <div class="jm-source-block"><strong>产物</strong><small>{{ task.expectedOutput }}</small><strong>验收标准</strong><small>{{ task.acceptanceCriteria }}</small></div>
                <div class="jm-chip-row">
                  <span class="jm-chip">{{ task.estimatedHours }} 小时</span>
                  <span v-if="displayText(task.phase)" class="jm-chip">{{ displayText(task.phase) }}</span>
                  <span v-if="displayText(task.priority)" class="jm-chip">{{ displayText(task.priority) }}</span>
                </div>
              </div>
              <aside>
                <button v-if="task.status !== 'DONE'" class="jm-secondary" type="button" @click="setTask(task, 'DONE')"><Check :size="15" />完成</button>
                <AppTag v-else tone="green">已完成</AppTag>
              </aside>
            </article>
            <div v-if="!report?.learningPlan.length" class="jm-empty">
              <div><span><BookOpenCheck :size="30" /></span><h3>没有需要生成的能力学习任务</h3><p>学习计划只针对已确认的能力缺口，不会为证据不足项虚构任务。</p></div>
            </div>
          </div>
        </section>
      </div>
    </Transition>
  </PageState>

  <AppDrawer :open="evidenceOpen" title="结论依据" :width="500" @close="evidenceOpen = false">
    <div class="jm-evidence-drawer-content">
      <section class="jm-source-block">
        <AppTag tone="blue">岗位要求</AppTag>
        <h3>{{ selectedRequirementTitle }}</h3>
        <small v-if="selectedClaim">
          结论类型：{{ displayText(selectedClaim.conclusionType) || '—' }}<template v-if="typeof selectedClaim.confidence === 'number'"> · 可信度 {{ selectedClaim.confidence }}%</template>
        </small>
        <small v-if="selectedRequirement">原文位置：第 {{ selectedRequirement.sequence }} 条岗位要求</small>
        <p v-if="selectedRequirement?.sourceQuote" class="jm-source-quote">{{ selectedRequirement.sourceQuote }}</p>
      </section>
      <section class="jm-source-block">
        <AppTag tone="green">分析理由</AppTag>
        <small>{{ displayText(selectedClaim?.reasoning) || '这条结论没有附带分析理由。' }}</small>
      </section>
      <section class="jm-source-block">
        <AppTag tone="purple">引用的资料</AppTag>
        <ul v-if="selectedClaim?.evidenceIds.length" class="jm-source-list">
          <li v-for="evidence in selectedClaim.evidenceIds" :key="evidence">{{ sourceLabel(evidence) }}</li>
        </ul>
        <small v-else>这条结论没有引用资料库内容。</small>
      </section>
      <div class="jm-privacy"><ShieldCheck :size="17" /><span>技能标签不会被当作强证据；联系方式和敏感字段已从模型输入排除。</span></div>
    </div>
  </AppDrawer>

  <AppModal :open="versionsOpen" title="报告版本与变化" @close="versionsOpen = false">
    <div class="jm-version-dialog">
      <UiErrorState v-if="versionsError" compact title="版本记录读取失败" :error="versionsError" :retrying="versionsLoading" @retry="openVersions" />
      <div v-else-if="versionsLoading" class="jm-loading"><LoaderCircle class="jm-spin" :size="18" /> 正在读取版本记录</div>
      <template v-else>
        <div v-if="versions.length > 1" class="jm-version-selectors">
          <label>较早版本<AppSelect v-model="compareFrom" @change="compareVersions"><option v-for="item in versions" :key="item.id" :value="item.version">版本 {{ item.version }} · {{ new Date(item.createdAt).toLocaleString('zh-CN') }}</option></AppSelect></label>
          <GitCompareArrows :size="20" />
          <label>较新版本<AppSelect v-model="compareTo" @change="compareVersions"><option v-for="item in versions" :key="item.id" :value="item.version">版本 {{ item.version }} · {{ new Date(item.createdAt).toLocaleString('zh-CN') }}</option></AppSelect></label>
        </div>
        <div v-if="pending" class="jm-loading"><LoaderCircle class="jm-spin" :size="18" /> 正在比较版本</div>
        <div v-else-if="comparison" class="jm-version-deltas">
          <div v-for="item in comparison.changes" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.before }} → {{ item.after }}</strong>
            <AppTag :tone="item.delta > 0 ? 'green' : item.delta < 0 ? 'orange' : 'gray'">{{ item.delta > 0 ? '+' : '' }}{{ item.delta }}</AppTag>
          </div>
        </div>
        <div v-else class="jm-empty jm-version-empty">
          <div><span><History :size="28" /></span><h3>{{ versions.length === 1 ? '当前只有一个报告版本' : '暂无可比较版本' }}</h3><p>回答澄清问题或重新分析后，新结果会作为不可变版本保存。</p></div>
        </div>
      </template>
    </div>
  </AppModal>

  <AppModal :open="exportOpen" title="导出岗位匹配报告" @close="exportOpen = false">
    <div class="jm-export-options">
      <label v-for="format in ['PDF', 'DOCX', 'JSON']" :key="format" class="jm-export-option">
        <input v-model="exportFormat" type="radio" :value="format">
        <span><strong>{{ format }}</strong><small>{{ format === 'PDF' ? '适合分享与打印' : format === 'DOCX' ? '可在 Word 或 WPS 中打开' : '结构化数据，适合归档' }}</small></span>
      </label>
      <div class="jm-privacy"><ShieldCheck :size="17" /><span>默认隐藏联系方式和个人资料，导出文件使用限时下载。</span></div>
    </div>
    <template #footer>
      <button class="jm-secondary" type="button" @click="exportOpen = false">取消</button>
      <button class="jm-primary" type="button" :disabled="pending" @click="exportReport"><LoaderCircle v-if="pending" class="jm-spin" :size="16" /><Download v-else :size="16" />生成 {{ exportFormat }}</button>
    </template>
  </AppModal>
</div></main></template>
