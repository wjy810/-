<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, Circle, LoaderCircle, PauseCircle, ShieldCheck, TriangleAlert } from 'lucide-vue-next'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { cancelAnalysis, fetchJobMatch, fetchMatchCapabilities, resumeAnalysis } from '../services/jobMatchApi'
import type { JobMatch, MatchCapabilities } from '../types'
import JobMatchContext from '../components/JobMatchContext.vue'
import {
  ANALYSIS_CHECKPOINTS,
  analysisCheckpointIndex,
  analysisCheckpointState,
  analysisEtaLabel,
  nextDisplayedProgress,
} from '../utils/analysisProgress'
import { statusLabel, statusTone } from '../utils/stage'
import '../job-match.css'

const route = useRoute(); const router = useRouter()
const match = ref<JobMatch | null>(null); const capabilities = ref<MatchCapabilities | null>(null)
const loading = ref(true); const pending = ref(false); const pageError = ref('')
useToastFeedback(pageError, 'error', 'job-match-analyzing-error')
const logs = ref<Array<{ id: string; message: string; at: string }>>([])
const displayProgress = ref(0); const motionNow = ref(Date.now()); const lastAuthoritativeAt = ref(Date.now())
const prefersReducedMotion = ref(false)
let pollTimer = 0; let motionTimer = 0; let lastMotionAt = Date.now(); let stream: EventSource | null = null
let motionPreference: MediaQueryList | null = null
let lastAuthoritativeProgress = 0; let lastCheckpoint: string | null = null; let lastStatus: JobMatch['status'] | null = null
const seenEventIds = new Set<string>()
const id = computed(() => String(route.params.id))
const checkpoints = ANALYSIS_CHECKPOINTS
const checkpointIndex = computed(() => analysisCheckpointIndex(match.value?.checkpoint, match.value?.status ?? 'ANALYZING', match.value?.progress ?? 0))
const checkpointState = (index: number) => analysisCheckpointState(index, checkpointIndex.value, match.value?.status ?? 'ANALYZING')
const counts = computed(() => ({ requirements: match.value?.requirements.length ?? 0, evidence: match.value?.evidenceMode === 'NONE' ? 0 : 1, clarifications: match.value?.clarifications.filter(item => item.status === 'PENDING').length ?? 0 }))
const displayedPercent = computed(() => Math.floor(displayProgress.value))
const etaLabel = computed(() => analysisEtaLabel(match.value?.checkpoint, match.value?.status ?? 'ANALYZING', motionNow.value - lastAuthoritativeAt.value))
const activityLabel = computed(() => {
  if (match.value?.status === 'ANALYSIS_PAUSED') return '保留规则结果，等待恢复'
  const labels: Record<string, string> = {
    PARSE_JD: '正在拆解岗位要求',
    VALIDATE_RESUME: '正在校验简历结构',
    LINK_EVIDENCE: '正在关联资料库证据',
    RULE_GATE: '正在核对硬性门槛',
    AI_SEMANTIC_ANALYSIS: 'AI 正在分析岗位匹配',
    ANALYSIS_PAUSED: 'AI 正在分析岗位匹配',
    FACT_VALIDATION: 'AI 正在核对事实与证据',
    REPORT_READY: '正在生成行动报告',
  }
  return labels[match.value?.checkpoint ?? ''] ?? '正在建立岗位匹配关系'
})

function syncAuthoritativeProgress(value: JobMatch, initial = false) {
  const now = Date.now()
  const changed = value.progress > lastAuthoritativeProgress
    || value.checkpoint !== lastCheckpoint
    || value.status !== lastStatus
  if (initial) {
    const durableUpdatedAt = Date.parse(value.updatedAt)
    lastAuthoritativeAt.value = Number.isFinite(durableUpdatedAt) && durableUpdatedAt <= now
      ? durableUpdatedAt
      : now
  } else if (changed) lastAuthoritativeAt.value = now
  lastAuthoritativeProgress = value.progress
  lastCheckpoint = value.checkpoint ?? null
  lastStatus = value.status

  const durable = value.status === 'COMPLETED' ? 100 : Math.min(98, Math.max(0, value.progress))
  if (initial || prefersReducedMotion.value) displayProgress.value = durable
  else if (value.status === 'COMPLETED') displayProgress.value = 100
}

function handleMotionPreference(event: MediaQueryListEvent) {
  prefersReducedMotion.value = event.matches
  if (event.matches && match.value) syncAuthoritativeProgress(match.value, true)
}

function startMotion() {
  window.clearInterval(motionTimer)
  lastMotionAt = Date.now()
  motionTimer = window.setInterval(() => {
    const now = Date.now(); const value = match.value
    motionNow.value = now
    if (!value) return
    if (prefersReducedMotion.value) {
      displayProgress.value = value.status === 'COMPLETED' ? 100 : Math.min(98, Math.max(0, value.progress))
    } else {
      displayProgress.value = nextDisplayedProgress({
        current: displayProgress.value,
        authoritative: value.progress,
        checkpoint: value.checkpoint,
        status: value.status,
        deltaMs: now - lastMotionAt,
      })
    }
    lastMotionAt = now
  }, 500)
}

function routeTerminal(value: JobMatch) {
  if (value.status === 'COMPLETED') void router.replace({ name:'job-match-report', params:{ id:value.id } })
  else if (value.status === 'NEEDS_CLARIFICATION') void router.replace({ name:'job-match-clarifications', params:{ id:value.id } })
}
async function refresh() {
  try {
    const priorStatus=match.value?.status; const value=await fetchJobMatch(id.value); syncAuthoritativeProgress(value); match.value=value
    if (value.status!==priorStatus && ['ANALYSIS_PAUSED','COMPLETED','CANCELLED'].includes(value.status)) capabilities.value=await fetchMatchCapabilities()
    routeTerminal(value)
  }
  catch(reason){ pageError.value=errorMessage(reason,'分析状态读取失败') }
}
function startPolling(){ window.clearInterval(pollTimer); pollTimer=window.setInterval(()=>void refresh(),1400) }
function startEvents(){
  stream?.close(); stream=new EventSource(`/api/v1/job-matches/${encodeURIComponent(id.value)}/events`,{withCredentials:true})
  const names=['analysis.progress','rule-report.ready','clarification.required','analysis.completed','analysis.paused']
  names.forEach(name=>stream?.addEventListener(name,(event)=>{
    const eventId=(event as MessageEvent).lastEventId
    if(eventId && seenEventIds.has(eventId)) return
    if(eventId){seenEventIds.add(eventId);if(seenEventIds.size>100){const oldest=seenEventIds.values().next().value;if(oldest)seenEventIds.delete(oldest)}}
    let data:Record<string,unknown>={}; try{data=JSON.parse((event as MessageEvent).data) as Record<string,unknown>}catch{/* polling remains authoritative */}
    const message=String(data.message || ({'rule-report.ready':'规则报告已生成','clarification.required':'需要补充确认','analysis.completed':'AI 分析完成','analysis.paused':'分析已暂停'} as Record<string,string>)[name] || '分析状态已更新')
    logs.value=[{id:eventId||`${Date.now()}-${name}`,message,at:new Date().toLocaleTimeString('zh-CN',{hour12:false})},...logs.value].slice(0,8); void refresh()
  }))
}
async function resume(){if(!match.value)return;pending.value=true;pageError.value='';try{match.value=await resumeAnalysis(id.value,match.value.version);capabilities.value=await fetchMatchCapabilities();startEvents();startPolling()}catch(reason){pageError.value=errorMessage(reason,'恢复分析失败')}finally{pending.value=false}}
async function cancel(){if(!match.value)return;pending.value=true;try{match.value=await cancelAnalysis(id.value,match.value.version);capabilities.value=await fetchMatchCapabilities()}catch(reason){pageError.value=errorMessage(reason,'取消分析失败')}finally{pending.value=false}}
onMounted(async()=>{try{motionPreference=window.matchMedia('(prefers-reduced-motion: reduce)');prefersReducedMotion.value=motionPreference.matches;motionPreference.addEventListener('change',handleMotionPreference);const [value,availableCapabilities]=await Promise.all([fetchJobMatch(id.value),fetchMatchCapabilities()]);syncAuthoritativeProgress(value,true);match.value=value;capabilities.value=availableCapabilities;routeTerminal(value);startEvents();startPolling();startMotion()}catch(reason){pageError.value=errorMessage(reason,'分析任务读取失败')}finally{loading.value=false}})
onBeforeUnmount(()=>{window.clearInterval(pollTimer);window.clearInterval(motionTimer);motionPreference?.removeEventListener('change',handleMotionPreference);stream?.close()})
</script>

<template><main class="jm-page"><div class="jm-shell">
  <header class="jm-head"><div><h1>正在分析岗位匹配</h1><p>{{ match?.title || '目标岗位' }}<template v-if="match?.company"> · {{ match.company }}</template></p></div><AppTag v-if="match" :tone="statusTone(match.status)">{{ statusLabel(match.status) }}</AppTag></header>
  <section v-if="loading" class="jm-card jm-loading"><LoaderCircle class="jm-spin" :size="20" /> 正在恢复分析任务</section>
  <div v-else class="jm-analysis-grid"><div>
    <section class="jm-card jm-analysis-card">
      <div class="jm-progress-ring" :style="{ '--progress': String(displayProgress) }" role="progressbar" aria-label="岗位匹配分析进度" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="displayedPercent"><p><strong>{{ displayedPercent }}<small>%</small></strong><span>{{ activityLabel }}</span><small class="jm-progress-eta" aria-live="polite">{{ etaLabel }}</small></p></div>
      <div class="jm-checkpoints"><article v-for="(item,index) in checkpoints" :key="item[0]" class="jm-checkpoint" :class="{ 'is-done':checkpointState(index)==='done','is-active':checkpointState(index)==='active','is-paused':checkpointState(index)==='paused' }"><span><Check v-if="checkpointState(index)==='done'" :size="15" /><LoaderCircle v-else-if="checkpointState(index)==='active'" class="jm-spin" :size="15" /><PauseCircle v-else-if="checkpointState(index)==='paused'" :size="15" /><Circle v-else :size="11" /></span><strong>{{ index+1 }}　{{ item[1] }}</strong><small>{{ checkpointState(index)==='done' ? '已完成' : checkpointState(index)==='active' ? '进行中' : checkpointState(index)==='paused' ? '已暂停' : '等待中' }}</small></article></div>
      <aside class="jm-discovery"><h3>当前输入</h3><div><span>岗位要求</span><strong>{{ counts.requirements }}</strong></div><div><span>正式简历</span><strong>1</strong></div><div><span>资料授权</span><strong>{{ match?.evidenceMode==='NONE' ? 0 : 1 }}</strong></div><div><span>待确认</span><strong>{{ counts.clarifications }}</strong></div></aside>
    </section>
    <section v-if="match?.status==='ANALYSIS_PAUSED'" class="jm-card jm-progress-log"><h2><TriangleAlert :size="18" /> 分析已安全暂停</h2><div class="jm-log-line"><span><PauseCircle :size="15" /></span><p>规则结果和输入快照已保留，失败额度已释放。可使用原任务恢复，不会重复扣费。</p></div></section>
    <section v-else class="jm-card jm-progress-log"><h2>分析进度日志</h2><div v-if="!logs.length" class="jm-log-line"><span><LoaderCircle class="jm-spin" :size="15" /></span><p>正在建立 JD、简历与证据的逐条对应关系</p><time>实时更新</time></div><div v-for="line in logs" :key="line.id" class="jm-log-line"><span><Check :size="15" /></span><p>{{ line.message }}</p><time>{{ line.at }}</time></div></section>
    <div class="jm-privacy"><ShieldCheck :size="18" /><p><strong>可安全离开此页</strong><span>任务在后台执行，刷新或重新登录后会从服务端检查点恢复。</span></p></div>
    <div class="jm-analysis-actions"><button v-if="match?.status==='ANALYSIS_PAUSED'" class="jm-primary" type="button" :disabled="pending" @click="resume"><LoaderCircle v-if="pending" class="jm-spin" :size="16" />恢复原分析</button><button v-else-if="match?.status==='ANALYZING'" class="jm-secondary" type="button" :disabled="pending" @click="cancel">取消分析</button><button v-else class="jm-secondary" type="button" @click="router.push({name:'job-match-home'})">返回岗位匹配</button></div>
  </div><JobMatchContext :match="match" :capabilities="capabilities" /></div>
</div></main></template>
