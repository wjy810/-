<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, Circle, LoaderCircle, PauseCircle, ShieldCheck, TriangleAlert } from 'lucide-vue-next'
import AppTag from '@/shared/ui/AppTag.vue'
import PageState from '@/shared/ui/PageState.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { cancelAnalysis, fetchJobMatch, fetchMatchCapabilities, resumeAnalysis } from '../services/jobMatchApi'
import type { JobMatch, MatchCapabilities } from '../types'
import JobMatchContext from '../components/JobMatchContext.vue'
import {
  ANALYSIS_STAGES,
  analysisStageIndex,
  analysisStageState,
  analysisStatusText,
  pauseReasonText,
  serverProgressPercent,
} from '../utils/analysisProgress'
import { statusLabel, statusTone } from '../utils/stage'
import '../job-match.css'

type LogLine = { id: string; message: string; at: string }

const STREAM_EVENTS = ['analysis.progress', 'rule-report.ready', 'clarification.required', 'analysis.completed', 'analysis.paused']
const EVENT_LABELS: Record<string, string> = {
  'rule-report.ready': '规则结果已生成',
  'clarification.required': '需要你确认部分事实',
  'analysis.completed': 'AI 分析完成',
  'analysis.paused': '分析已暂停',
}

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id))
const match = ref<JobMatch | null>(null)
const capabilities = ref<MatchCapabilities | null>(null)
const capabilitiesFailed = ref(false)
const pending = ref(false)
const actionError = ref('')
const refreshError = ref('')
const logs = ref<LogLine[]>([])
useToastFeedback(actionError, 'error', 'job-match-analyzing-error')
useToastFeedback(refreshError, 'warning', 'job-match-analyzing-refresh')

let pollTimer = 0
let eventRefreshTimer = 0
let stream: EventSource | null = null
const seenEventIds = new Set<string>()

const { error: loadError, loading, loaded, load } = useLoadState(async () => {
  const value = await fetchJobMatch(id.value)
  match.value = value
  return value
})

const stageIndex = computed(() => analysisStageIndex(match.value?.checkpoint))
const stageState = (index: number) => analysisStageState(index, stageIndex.value, match.value?.status ?? 'ANALYZING')
const percent = computed(() => serverProgressPercent(match.value?.progress))
const statusText = computed(() => (match.value ? analysisStatusText(match.value.status, match.value.checkpoint) : ''))
const isRunning = computed(() => match.value?.status === 'ANALYZING')
const updatedAt = computed(() => formatTime(match.value?.updatedAt))
const pendingClarifications = computed(() => match.value?.clarifications.filter(item => item.status === 'PENDING').length ?? 0)
const evidenceLabel = computed(() => {
  const mode = match.value?.evidenceMode
  if (mode === 'AUTO') return '自动选择'
  if (mode === 'MANUAL') return '手动选择'
  return '未使用'
})

function formatTime(value: string | null | undefined): string {
  const time = value ? new Date(value) : null
  return time && Number.isFinite(time.getTime()) ? time.toLocaleTimeString('zh-CN', { hour12: false }) : ''
}

async function loadCapabilities() {
  try {
    capabilities.value = await fetchMatchCapabilities()
    capabilitiesFailed.value = false
  } catch (reason) {
    capabilitiesFailed.value = true
    console.warn('job-match capabilities unavailable', reason)
  }
}

function routeTerminal(value: JobMatch) {
  if (value.status === 'COMPLETED') void router.replace({ name: 'job-match-report', params: { id: value.id } })
  else if (value.status === 'NEEDS_CLARIFICATION') void router.replace({ name: 'job-match-clarifications', params: { id: value.id } })
}

async function refresh() {
  try {
    const prior = match.value?.status
    const value = await fetchJobMatch(id.value)
    match.value = value
    refreshError.value = ''
    if (value.status !== prior && ['ANALYSIS_PAUSED', 'COMPLETED', 'CANCELLED'].includes(value.status)) void loadCapabilities()
    routeTerminal(value)
    if (value.status !== 'ANALYZING') stopLive()
  } catch (reason) {
    // Polling keeps going; the last server state stays on screen.
    refreshError.value = `${errorMessage(reason, '分析状态刷新失败')}，正在重试`
  }
}

function startPolling() {
  window.clearInterval(pollTimer)
  pollTimer = window.setInterval(() => void refresh(), 2000)
}

function startEvents() {
  stream?.close()
  stream = new EventSource(`/api/v1/job-matches/${encodeURIComponent(id.value)}/events`, { withCredentials: true })
  for (const name of STREAM_EVENTS) {
    stream.addEventListener(name, (event) => {
      const message = event as MessageEvent
      const eventId = message.lastEventId
      if (eventId && seenEventIds.has(eventId)) return
      if (eventId) seenEventIds.add(eventId)
      let data: Record<string, unknown> = {}
      try { data = JSON.parse(message.data) as Record<string, unknown> } catch { /* polling stays authoritative */ }
      const text = typeof data.message === 'string' && data.message ? data.message : EVENT_LABELS[name] ?? '分析状态已更新'
      const at = formatTime(typeof data.at === 'string' ? data.at : null) || formatTime(new Date().toISOString())
      logs.value = [{ id: eventId || `${Date.now()}-${name}`, message: text, at }, ...logs.value].slice(0, 8)
      // Replayed history arrives in a burst; read the match once for it.
      window.clearTimeout(eventRefreshTimer)
      eventRefreshTimer = window.setTimeout(() => void refresh(), 300)
    })
  }
}

function startLive() {
  startEvents()
  startPolling()
}

function stopLive() {
  window.clearInterval(pollTimer)
  window.clearTimeout(eventRefreshTimer)
  stream?.close()
  stream = null
}

async function resume() {
  if (!match.value) return
  pending.value = true
  actionError.value = ''
  try {
    match.value = await resumeAnalysis(id.value, match.value.version)
    void loadCapabilities()
    startLive()
  } catch (reason) {
    actionError.value = errorMessage(reason, '恢复分析失败')
  } finally {
    pending.value = false
  }
}

async function cancel() {
  if (!match.value) return
  pending.value = true
  actionError.value = ''
  try {
    match.value = await cancelAnalysis(id.value, match.value.version)
    stopLive()
    void loadCapabilities()
  } catch (reason) {
    actionError.value = errorMessage(reason, '取消分析失败')
  } finally {
    pending.value = false
  }
}

async function start() {
  const value = await load()
  if (!value) return
  routeTerminal(value)
  if (value.status === 'ANALYZING') startLive()
}

onMounted(() => {
  void loadCapabilities()
  void start()
})
onBeforeUnmount(stopLive)
</script>

<template>
  <main class="jm-page"><div class="jm-shell">
    <header class="jm-head">
      <div>
        <h1>{{ match?.status === 'ANALYZING' || !match ? '正在分析岗位匹配' : '岗位匹配分析' }}</h1>
        <p>{{ match?.title || '目标岗位' }}<template v-if="match?.company"> · {{ match.company }}</template></p>
      </div>
      <AppTag v-if="match" :tone="statusTone(match.status)">{{ statusLabel(match.status) }}</AppTag>
    </header>

    <PageState :loading="loading" :error="loadError" :loaded="loaded" error-title="分析状态读取失败" @retry="start">
      <template #skeleton>
        <section class="jm-card jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在读取分析任务</section>
      </template>

      <div v-if="match" class="jm-analysis-grid">
        <div>
          <section class="jm-card jm-analysis-card">
            <div
              class="jm-progress-ring"
              :class="{ 'is-indeterminate': percent === null }"
              :style="{ '--progress': String(percent ?? 0) }"
              role="progressbar"
              aria-label="岗位匹配分析进度"
              aria-valuemin="0"
              aria-valuemax="100"
              :aria-valuenow="percent ?? undefined"
              :aria-valuetext="statusText"
            >
              <p>
                <strong v-if="percent !== null">{{ percent }}<small>%</small></strong>
                <LoaderCircle v-else-if="isRunning" class="jm-spin" :size="34" />
                <PauseCircle v-else :size="34" />
                <span aria-live="polite">{{ statusText }}</span>
                <small v-if="updatedAt" class="jm-progress-eta">服务端更新于 {{ updatedAt }}</small>
              </p>
            </div>

            <ol v-if="isRunning" class="jm-checkpoints" aria-label="分析阶段">
              <li
                v-for="(stage, index) in ANALYSIS_STAGES"
                :key="stage.code"
                class="jm-checkpoint"
                :class="{ 'is-done': stageState(index) === 'done', 'is-active': stageState(index) === 'active' }"
              >
                <span>
                  <Check v-if="stageState(index) === 'done'" :size="15" />
                  <LoaderCircle v-else-if="stageState(index) === 'active'" class="jm-spin" :size="15" />
                  <Circle v-else :size="11" />
                </span>
                <strong>{{ index + 1 }}　{{ stage.label }}</strong>
                <small>{{ stageState(index) === 'done' ? '已完成' : stageState(index) === 'active' ? '进行中' : '等待中' }}</small>
              </li>
            </ol>
            <div v-else class="jm-analysis-stopped">
              <p v-if="match.status === 'ANALYSIS_PAUSED'">分析在后台停止了，可以从原任务恢复。</p>
              <p v-else-if="match.status === 'CANCELLED'">这次分析已取消，没有生成新报告。</p>
              <p v-else>当前没有正在运行的分析。</p>
            </div>

            <aside class="jm-discovery">
              <h3>本次输入</h3>
              <div><span>岗位要求</span><strong>{{ match.requirements.length }}</strong></div>
              <div><span>投递简历</span><strong class="jm-discovery__text">{{ match.resume ? '已冻结' : '—' }}</strong></div>
              <div><span>资料授权</span><strong class="jm-discovery__text">{{ evidenceLabel }}</strong></div>
              <div><span>待确认</span><strong>{{ pendingClarifications }}</strong></div>
            </aside>
          </section>

          <section v-if="match.status === 'ANALYSIS_PAUSED'" class="jm-card jm-progress-log">
            <h2><TriangleAlert :size="18" /> 分析已暂停</h2>
            <div class="jm-log-line">
              <span><PauseCircle :size="15" /></span>
              <p>{{ pauseReasonText(match.errorCode) }}输入快照已保留，本次占用的额度已释放；恢复后只有成功完成才会扣除额度。</p>
            </div>
            <details v-if="match.errorCode" class="jm-tech-details">
              <summary>技术详情</summary>
              <code>{{ match.errorCode }}</code>
            </details>
          </section>
          <section v-else-if="isRunning" class="jm-card jm-progress-log">
            <h2>进度记录</h2>
            <div v-if="!logs.length" class="jm-log-line is-empty"><p>服务端推送的进度会显示在这里。</p></div>
            <div v-for="line in logs" :key="line.id" class="jm-log-line">
              <span><Check :size="15" /></span>
              <p>{{ line.message }}</p>
              <time>{{ line.at }}</time>
            </div>
          </section>

          <div v-if="isRunning" class="jm-privacy">
            <ShieldCheck :size="18" />
            <p><strong>可以离开此页</strong><span>分析在后台进行，回到此页会读取服务端的最新状态。</span></p>
          </div>
          <div class="jm-analysis-actions">
            <button v-if="match.status === 'ANALYSIS_PAUSED'" class="jm-primary" type="button" :disabled="pending" @click="resume">
              <LoaderCircle v-if="pending" class="jm-spin" :size="16" />恢复原分析
            </button>
            <button v-else-if="isRunning" class="jm-secondary" type="button" :disabled="pending" @click="cancel">取消分析</button>
            <button v-else class="jm-secondary" type="button" @click="router.push({ name: 'job-match-home' })">返回岗位匹配</button>
          </div>
        </div>
        <JobMatchContext :match="match" :capabilities="capabilities" :capabilities-failed="capabilitiesFailed" />
      </div>
    </PageState>
  </div></main>
</template>
