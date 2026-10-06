<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, FileText, Globe2, LoaderCircle, ShieldCheck, TriangleAlert, Upload, UserRoundCheck } from 'lucide-vue-next'
import AppSelect from '@/shared/ui/AppSelect.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { fetchTask } from '@/shared/api/task'
import JobMatchContext from '../components/JobMatchContext.vue'
import JobMatchStepper from '../components/JobMatchStepper.vue'
import {
  authorizeEvidence, confirmResumeImport, createResumeImport, createTextMatch, createUrlMatch,
  fetchEvidenceRecommendations, fetchJobMatch, fetchMatchCapabilities, fetchResumeImport,
  fetchResumeOptions, selectMatchResume, startAnalysis, updateJdStructure, uploadJd, uploadMatchResume,
} from '../services/jobMatchApi'
import type { EvidenceCandidate, JobMatch, MatchCapabilities, MatchRequirement, ResumeImportSession, ResumeOption } from '../types'
import { serverStage, stepNumber, type MatchRouteStage } from '../utils/stage'
import { wizardTransitionName } from '../utils/uiPresentation'
import '../job-match.css'

const route = useRoute()
const router = useRouter()
const capabilities = ref<MatchCapabilities | null>(null)
const match = ref<JobMatch | null>(null)
const stage = ref<MatchRouteStage>('jd')
const loading = ref(true)
const pending = ref(false)
const pageError = ref('')
const source = ref<'TEXT' | 'FILE' | 'URL'>('TEXT')
const jdText = ref('')
const jdUrl = ref('')
const selectedJdFile = ref<File | null>(null)
const jdTitle = ref('')
const jdCompany = ref('')
const jdLocation = ref('')
const jdWorkMode = ref('ONSITE')
const requirements = ref<MatchRequirement[]>([])
const resumeSource = ref<'SITE' | 'UPLOAD'>('SITE')
const resumes = ref<ResumeOption[]>([])
const selectedResumeId = ref('')
const selectedResumeFile = ref<File | null>(null)
const resumeImport = ref<ResumeImportSession | null>(null)
const importStatus = ref('')
const evidence = ref<EvidenceCandidate[]>([])
const evidenceMode = ref<'AUTO' | 'MANUAL' | 'NONE'>('AUTO')
const selectedEvidence = ref(new Set<string>())
const consent = ref(false)
const wizardTransition = ref('jm-stage-fade')
const transientPageError = computed({
  get: () => stage.value === 'jd' && !match.value ? '' : pageError.value,
  set: (value: string) => { pageError.value = value },
})
useToastFeedback(transientPageError, 'error', 'job-match-create-error')

const currentStep = computed(() => stepNumber(stage.value))
const wizardStageKey = computed(() => `${stage.value}:${stage.value === 'jd' && match.value ? 'review' : 'entry'}`)
const jdCount = computed(() => jdText.value.replace(/\s/g, '').length)
const selectedEvidenceItems = computed(() => evidence.value.filter(item => selectedEvidence.value.has(`${item.sourceType}:${item.sourceId}`)))

function syncFromMatch(value: JobMatch) {
  match.value = value
  stage.value = serverStage(value)
  jdTitle.value = value.title || ''
  jdCompany.value = value.company || ''
  jdLocation.value = value.location || ''
  jdWorkMode.value = value.workMode || 'ONSITE'
  requirements.value = value.requirements.map(item => ({ ...item }))
  if (value.resume) selectedResumeId.value = value.resume.revisionId
}

async function load() {
  loading.value = true; pageError.value = ''
  try {
    capabilities.value = await fetchMatchCapabilities()
    const id = typeof route.query.id === 'string' ? route.query.id : ''
    if (id) {
      syncFromMatch(await fetchJobMatch(id))
      await loadStageData()
    }
  } catch (reason) { pageError.value = errorMessage(reason, '岗位匹配任务恢复失败') }
  finally { loading.value = false }
}

async function loadStageData() {
  if (!match.value) return
  if (stage.value === 'resume') resumes.value = await fetchResumeOptions(match.value.id)
  if (stage.value === 'evidence' || stage.value === 'confirm') {
    evidence.value = await fetchEvidenceRecommendations(match.value.id)
    if (match.value.evidenceMode === 'NONE') evidenceMode.value = 'NONE'
    else if (match.value.evidenceMode === 'MANUAL') evidenceMode.value = 'MANUAL'
    if (!match.value.authorizationId) setRecommendedEvidence()
  }
}

function setRecommendedEvidence() {
  selectedEvidence.value = new Set(evidence.value.filter(item => item.recommended).map(item => `${item.sourceType}:${item.sourceId}`))
}

async function importJd() {
  pending.value = true; pageError.value = ''
  try {
    const requestId = crypto.randomUUID()
    let created: JobMatch
    if (source.value === 'TEXT') created = await createTextMatch(jdText.value, requestId)
    else if (source.value === 'URL') created = await createUrlMatch(jdUrl.value, requestId)
    else {
      if (!selectedJdFile.value) throw new Error('请选择 JD 文件或截图')
      created = await uploadJd(selectedJdFile.value, requestId)
    }
    syncFromMatch(created)
    stage.value = 'jd'
    await router.replace({ name: 'job-match-new', query: { id: created.id } })
  } catch (reason) { pageError.value = errorMessage(reason, reason instanceof Error ? reason.message : '岗位 JD 解析失败') }
  finally { pending.value = false }
}

async function confirmJd() {
  if (!match.value) return
  pending.value = true; pageError.value = ''
  try {
    const updated = await updateJdStructure(match.value.id, {
      title: jdTitle.value, company: jdCompany.value, location: jdLocation.value, workMode: jdWorkMode.value,
      requirements: requirements.value.map(item => ({ id: item.id, category: item.category, text: item.text, priority: item.priority, hardGate: item.hardGate, sourceLocator: item.sourceLocator, sourceQuote: item.sourceQuote })),
      expectedVersion: match.value.version,
      requestId: `job-match:jd-structure:${match.value.id}:${match.value.version}`,
    })
    syncFromMatch(updated); stage.value = 'resume'; resumes.value = await fetchResumeOptions(updated.id)
  } catch (reason) { pageError.value = errorMessage(reason, '岗位结构确认失败') }
  finally { pending.value = false }
}

async function confirmSiteResume() {
  if (!match.value || !selectedResumeId.value) return
  pending.value = true; pageError.value = ''
  try {
    const updated = await selectMatchResume(match.value.id, selectedResumeId.value, match.value.version)
    syncFromMatch(updated); stage.value = 'evidence'; evidence.value = await fetchEvidenceRecommendations(updated.id); setRecommendedEvidence()
  } catch (reason) { pageError.value = errorMessage(reason, '简历选择失败') }
  finally { pending.value = false }
}

async function waitTask(taskId: string, label: string) {
  for (let attempt = 0; attempt < 90; attempt += 1) {
    const task = await fetchTask(taskId)
    importStatus.value = `${label} · ${task.status === 'PENDING' ? '排队中' : task.status === 'RUNNING' ? '处理中' : task.status === 'SUCCEEDED' ? '已完成' : '失败'}`
    if (task.status === 'SUCCEEDED') return
    if (task.status === 'FAILED' || task.status === 'CANCELLED') throw new Error(task.failureReason || `${label}失败`)
    await new Promise(resolve => window.setTimeout(resolve, 900))
  }
  throw new Error(`${label}超时，请稍后刷新恢复`)
}

async function processResumeFile() {
  if (!match.value || !selectedResumeFile.value) return
  pending.value = true; pageError.value = ''; importStatus.value = '正在安全上传'
  try {
    const upload = await uploadMatchResume(match.value.id, selectedResumeFile.value)
    await waitTask(upload.upload.task.id, '文件安全处理')
    const started = await createResumeImport(match.value.id, { careerFileId: upload.upload.file.id })
    resumeImport.value = started.importSession
    await waitTask(started.task.id, '简历结构解析')
    resumeImport.value = await fetchResumeImport(started.importSession.id)
    importStatus.value = '解析完成，请确认结构化内容'
  } catch (reason) { pageError.value = errorMessage(reason, reason instanceof Error ? reason.message : '简历处理失败') }
  finally { pending.value = false }
}

async function confirmImportedResume() {
  if (!match.value || !resumeImport.value) return
  pending.value = true; pageError.value = ''
  try {
    const confirmed = await confirmResumeImport(resumeImport.value.id, `${jdTitle.value || '目标岗位'}投递简历`, resumeImport.value.version)
    if (!confirmed.resultRevisionId) throw new Error('简历正式修订未生成')
    const updated = await selectMatchResume(match.value.id, confirmed.resultRevisionId, match.value.version, confirmed.id)
    syncFromMatch(updated); stage.value = 'evidence'; evidence.value = await fetchEvidenceRecommendations(updated.id); setRecommendedEvidence()
  } catch (reason) { pageError.value = errorMessage(reason, reason instanceof Error ? reason.message : '简历确认失败') }
  finally { pending.value = false }
}

function toggleEvidence(item: EvidenceCandidate) {
  const key = `${item.sourceType}:${item.sourceId}`
  const next = new Set(selectedEvidence.value)
  if (next.has(key)) next.delete(key); else next.add(key)
  selectedEvidence.value = next
}

async function confirmEvidence() {
  if (!match.value) return
  pending.value = true; pageError.value = ''
  try {
    const selected = evidenceMode.value === 'NONE' ? [] : selectedEvidenceItems.value
    const updated = await authorizeEvidence(match.value.id, {
      mode: evidenceMode.value,
      recordIds: selected.filter(item => item.sourceType === 'CAREER_RECORD').map(item => item.sourceId),
      fileIds: selected.filter(item => item.sourceType === 'CAREER_FILE').map(item => item.sourceId),
      scope: 'CURRENT_MATCH', rememberPreference: false, expectedVersion: match.value.version,
    })
    syncFromMatch(updated); stage.value = 'confirm'
  } catch (reason) { pageError.value = errorMessage(reason, '证据授权确认失败') }
  finally { pending.value = false }
}

async function analyze() {
  if (!match.value || !consent.value) return
  pending.value = true; pageError.value = ''
  try {
    const started = await startAnalysis(match.value.id, crypto.randomUUID(), match.value.version)
    syncFromMatch(started.match)
    await router.push({ name: 'job-match-analyzing', params: { id: match.value.id } })
  } catch (reason) { pageError.value = errorMessage(reason, 'AI 深度匹配启动失败') }
  finally { pending.value = false }
}

function goBack() {
  if (stage.value === 'resume') stage.value = 'jd'
  else if (stage.value === 'evidence') stage.value = 'resume'
  else if (stage.value === 'confirm') stage.value = 'evidence'
  else void router.push({ name: 'job-match-home' })
}

function onSourceTabKeydown(event: KeyboardEvent, current: typeof source.value) {
  const sources: Array<typeof source.value> = ['TEXT', 'FILE', 'URL']
  const currentIndex = sources.indexOf(current)
  let nextIndex = currentIndex
  if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % sources.length
  else if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + sources.length) % sources.length
  else if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = sources.length - 1
  else return
  event.preventDefault()
  source.value = sources[nextIndex]
  const tabs = (event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')
  tabs?.[nextIndex]?.focus()
}

watch(evidenceMode, value => { if (value === 'AUTO') setRecommendedEvidence(); if (value === 'NONE') selectedEvidence.value = new Set() })
watch(currentStep, (current, previous) => { wizardTransition.value = wizardTransitionName(previous, current) })
onMounted(load)
</script>

<template>
  <main class="jm-page"><div class="jm-shell">
    <header class="jm-head"><div><h1>创建岗位匹配</h1><p>四步确认分析输入，任何 AI 结论都能回到事实来源。</p></div><button class="jm-text-btn" type="button" @click="router.push({ name: 'job-match-home' })">保存并退出</button></header>
    <JobMatchStepper :current="currentStep" />
    <section v-if="pageError && stage === 'jd' && !match" class="jm-card jm-recovery" role="alert">
      <TriangleAlert :size="20" />
      <div><strong>岗位信息还没有成功解析</strong><p>{{ pageError }}</p><small>已输入内容仍保留在当前页面。请补全“岗位职责、任职要求、加分项”等规范区块后重试。</small></div>
      <div class="jm-recovery__actions"><button class="jm-secondary" type="button" :disabled="pending" @click="importJd">重新解析</button><button class="jm-text-btn" type="button" @click="source='FILE'">改用文件或截图</button></div>
    </section>
    <section v-if="loading" class="jm-card jm-loading"><span><LoaderCircle class="jm-spin" :size="20" /> 正在恢复创建进度</span></section>
    <div v-else class="jm-create-grid">
      <section class="jm-card jm-wizard">
        <Transition :name="wizardTransition" mode="out-in">
        <div :key="wizardStageKey" class="jm-wizard__stage">
        <template v-if="stage === 'jd' && !match">
          <div class="jm-wizard__body"><header><h2>导入目标岗位 JD</h2><p>系统会提取硬性门槛、核心技能、职责与加分项，解析后仍由你确认。</p></header>
            <div class="jm-source-tabs" :class="`is-${source.toLowerCase()}`" role="tablist" aria-label="JD 导入方式">
              <button id="jm-source-text" :class="{ 'is-active': source === 'TEXT' }" type="button" role="tab" :aria-selected="source === 'TEXT'" aria-controls="jm-source-panel" :tabindex="source === 'TEXT' ? 0 : -1" @click="source='TEXT'" @keydown="onSourceTabKeydown($event,'TEXT')">粘贴文本</button>
              <button id="jm-source-file" :class="{ 'is-active': source === 'FILE' }" type="button" role="tab" :aria-selected="source === 'FILE'" aria-controls="jm-source-panel" :tabindex="source === 'FILE' ? 0 : -1" @click="source='FILE'" @keydown="onSourceTabKeydown($event,'FILE')">文件或截图</button>
              <button id="jm-source-url" :class="{ 'is-active': source === 'URL' }" type="button" role="tab" :aria-selected="source === 'URL'" aria-controls="jm-source-panel" :tabindex="source === 'URL' ? 0 : -1" @click="source='URL'" @keydown="onSourceTabKeydown($event,'URL')">网页链接</button>
            </div>
            <Transition name="jm-switch" mode="out-in">
              <div id="jm-source-panel" :key="source" class="jm-source-panel" role="tabpanel" :aria-labelledby="`jm-source-${source.toLowerCase()}`">
                <div v-if="source === 'TEXT'" class="jm-textarea-wrap"><span class="jm-counter">{{ jdCount.toLocaleString() }} / 10,000</span><textarea v-model="jdText" maxlength="10000" placeholder="请粘贴完整职位描述，建议保留以下规范区块：&#10;&#10;岗位职责：&#10;1. ...&#10;2. ...&#10;&#10;任职要求：&#10;1. ...&#10;2. ...&#10;&#10;加分项：&#10;1. ...&#10;2. ..." /></div>
                <label v-else-if="source === 'FILE'" class="jm-dropzone"><input hidden type="file" accept=".pdf,.docx,.png,.jpg,.jpeg" @change="selectedJdFile=($event.target as HTMLInputElement).files?.[0] || null"><div><span><Upload :size="26" /></span><strong>{{ selectedJdFile?.name || '选择 JD 文件或截图' }}</strong><small>PDF、DOCX、PNG、JPEG，最大 10 MiB</small><em class="jm-secondary">浏览文件</em></div></label>
                <div v-else class="jm-url-form"><input v-model="jdUrl" type="url" placeholder="https://example.com/jobs/123"><button class="jm-secondary" type="button" @click="importJd"><Globe2 :size="17" />读取网页</button></div>
              </div>
            </Transition>
          </div><footer class="jm-wizard__footer"><button class="jm-secondary" type="button" @click="goBack">返回</button><button class="jm-primary" type="button" :disabled="pending || (source==='TEXT' && jdCount<300) || (source==='FILE' && !selectedJdFile) || (source==='URL' && !jdUrl)" @click="importJd"><LoaderCircle v-if="pending" class="jm-spin" :size="16" />解析岗位要求</button></footer>
        </template>
        <template v-else-if="stage === 'jd' && match">
          <div class="jm-wizard__body"><header><h2>确认岗位解析结果</h2><p>请逐条校正岗位信息和要求。冲突、误分类或缺失项处理后才能继续。</p></header>
            <div class="jm-form-grid"><label class="jm-field"><span>岗位名称</span><input v-model="jdTitle"></label><label class="jm-field"><span>公司名称</span><input v-model="jdCompany" placeholder="可选"></label><label class="jm-field"><span>工作地点</span><input v-model="jdLocation" placeholder="可选"></label><label class="jm-field"><span>办公方式</span><AppSelect v-model="jdWorkMode"><option value="ONSITE">现场办公</option><option value="HYBRID">混合办公</option><option value="REMOTE">远程办公</option></AppSelect></label></div>
            <div class="jm-requirements"><article v-for="(item,index) in requirements" :key="item.id" class="jm-requirement"><span class="jm-requirement__num">{{ index+1 }}</span><input v-model="item.text" type="text"><AppSelect v-model="item.priority"><option value="MUST">必须</option><option value="IMPORTANT">重要</option><option value="BONUS">加分</option></AppSelect><label class="jm-check"><input v-model="item.hardGate" type="checkbox">硬性</label></article></div>
          </div><footer class="jm-wizard__footer"><button class="jm-secondary" type="button" @click="goBack">返回</button><button class="jm-primary" type="button" :disabled="pending || requirements.length<3 || !jdTitle.trim()" @click="confirmJd"><LoaderCircle v-if="pending" class="jm-spin" :size="16" />确认并选择简历</button></footer>
        </template>
        <template v-else-if="stage === 'resume'">
          <div class="jm-wizard__body"><header><h2>选择本次投递简历</h2><p>匹配会冻结选中的正式修订；本地简历确认后会成为可编辑的站内结构化简历。</p></header>
            <div class="jm-select-card-grid"><button class="jm-select-card" :class="{ 'is-selected':resumeSource==='SITE' }" type="button" :aria-pressed="resumeSource==='SITE'" @click="resumeSource='SITE'"><span><UserRoundCheck :size="22" /></span><p><strong>选择站内简历</strong><small>使用简历工作台中的正式修订，匹配后可直接应用优化建议。</small></p></button><button class="jm-select-card" :class="{ 'is-selected':resumeSource==='UPLOAD' }" type="button" :aria-pressed="resumeSource==='UPLOAD'" @click="resumeSource='UPLOAD'"><span><Upload :size="22" /></span><p><strong>上传本地简历</strong><small>支持文本型 PDF 和 DOCX；扫描件首版不支持。</small></p></button></div>
            <Transition name="jm-switch" mode="out-in">
              <div :key="resumeSource" class="jm-resume-source-panel">
                <div v-if="resumeSource==='SITE'" class="jm-resume-list"><label v-for="item in resumes" :key="item.revisionId" class="jm-resume-option" :class="{ 'is-selected':selectedResumeId===item.revisionId }"><input v-model="selectedResumeId" type="radio" :value="item.revisionId"><p><strong>{{ item.title }}</strong><small>{{ item.languageCode }} · 更新于 {{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</small></p><Check v-if="selectedResumeId===item.revisionId" :size="18" /></label><p v-if="!resumes.length" class="jm-error">还没有可用的正式简历，请先在简历工作台创建一份，或上传本地简历。</p></div>
                <div v-else><label class="jm-dropzone"><input hidden type="file" accept=".pdf,.docx" @change="selectedResumeFile=($event.target as HTMLInputElement).files?.[0] || null; resumeImport=null"><div><span><FileText :size="26" /></span><strong>{{ selectedResumeFile?.name || '选择文本型 PDF 或 DOCX' }}</strong><small>上传后执行安全扫描、文本提取和结构化解析</small><em class="jm-secondary">浏览简历</em></div></label><p v-if="importStatus" class="jm-privacy"><LoaderCircle v-if="pending" class="jm-spin" :size="17" /><ShieldCheck v-else :size="17" /><span>{{ importStatus }}</span></p><section v-if="resumeImport?.structuredDraft" class="jm-confirm-list" style="margin-top:14px"><div><span>解析状态</span><strong>结构化简历可确认</strong><em>resume-content-v3</em></div><div><span>教育经历</span><strong>{{ Array.isArray(resumeImport.structuredDraft.education) ? resumeImport.structuredDraft.education.length : 0 }} 项</strong><em>已提取</em></div><div><span>工作与项目</span><strong>{{ (Array.isArray(resumeImport.structuredDraft.experiences) ? resumeImport.structuredDraft.experiences.length : 0) + (Array.isArray(resumeImport.structuredDraft.projects) ? resumeImport.structuredDraft.projects.length : 0) }} 项</strong><em>可继续编辑</em></div></section></div>
              </div>
            </Transition>
          </div><footer class="jm-wizard__footer"><button class="jm-secondary" type="button" @click="goBack">返回</button><button v-if="resumeSource==='SITE'" class="jm-primary" type="button" :disabled="pending || !selectedResumeId" @click="confirmSiteResume">确认使用此简历</button><button v-else-if="!resumeImport?.structuredDraft" class="jm-primary" type="button" :disabled="pending || !selectedResumeFile" @click="processResumeFile"><LoaderCircle v-if="pending" class="jm-spin" :size="16" />上传并解析</button><button v-else class="jm-primary" type="button" :disabled="pending" @click="confirmImportedResume">确认并创建站内简历</button></footer>
        </template>
        <template v-else-if="stage === 'evidence'">
          <div class="jm-wizard__body"><header><h2>补充求职资料证据</h2><p>资料库默认不授权。你可以自动选择相关项、手动选择，或完全不使用。</p></header>
            <div class="jm-choice-row"><button class="jm-choice" :class="{ 'is-selected':evidenceMode==='AUTO' }" type="button" :aria-pressed="evidenceMode==='AUTO'" @click="evidenceMode='AUTO'"><strong>自动选择相关证据</strong><small>按 JD 相关度推荐，但仍由你确认范围。</small></button><button class="jm-choice" :class="{ 'is-selected':evidenceMode==='MANUAL' }" type="button" :aria-pressed="evidenceMode==='MANUAL'" @click="evidenceMode='MANUAL'"><strong>手动选择</strong><small>逐项选择项目、成果和文件资料。</small></button><button class="jm-choice" :class="{ 'is-selected':evidenceMode==='NONE' }" type="button" :aria-pressed="evidenceMode==='NONE'" @click="evidenceMode='NONE'"><strong>不使用资料库</strong><small>只基于 JD 与本次投递简历分析。</small></button></div>
            <Transition name="jm-switch" mode="out-in"><div :key="evidenceMode" class="jm-evidence-panel"><div v-if="evidenceMode!=='NONE'" class="jm-evidence-list"><label v-for="item in evidence" :key="`${item.sourceType}:${item.sourceId}`" class="jm-evidence-option" :class="{ 'is-selected':selectedEvidence.has(`${item.sourceType}:${item.sourceId}`) }"><input type="checkbox" :checked="selectedEvidence.has(`${item.sourceType}:${item.sourceId}`)" @change="toggleEvidence(item)"><p><strong>{{ item.title }}</strong><small>{{ item.excerpt || item.locator }}</small></p><em>相关度 {{ item.relevance }}%</em></label><p v-if="!evidence.length" class="jm-privacy"><ShieldCheck :size="17" /><span>资料库暂无可用证据，可以选择“不使用资料库”继续。</span></p></div><div v-else class="jm-evidence-none"><ShieldCheck :size="20" /><p><strong>本次不使用资料库</strong><small>分析只读取已确认的 JD 和投递简历，之后仍可重新创建匹配并授权证据。</small></p></div></div></Transition>
            <div class="jm-privacy"><ShieldCheck :size="18" /><p><strong>敏感信息已排除</strong><span>手机号、邮箱、详细地址、身份证及敏感属性不会进入模型输入；授权只作用于当前任务。</span></p></div>
          </div><footer class="jm-wizard__footer"><button class="jm-secondary" type="button" @click="goBack">返回</button><button class="jm-primary" type="button" :disabled="pending" @click="confirmEvidence">确认资料范围</button></footer>
        </template>
        <template v-else>
          <div class="jm-wizard__body"><header><h2>确认并开始完整分析</h2><p>系统将冻结当前输入，先运行规则引擎，再由真实 AI 完成语义判断、澄清和行动计划。</p></header>
            <section class="jm-confirm-list"><div><span>目标岗位</span><strong>{{ match?.title }} · {{ match?.company || '未填写公司' }}</strong><em>{{ requirements.length }} 条要求</em></div><div><span>投递简历</span><strong>{{ match?.resume?.title }}</strong><em>正式修订</em></div><div><span>证据范围</span><strong>{{ match?.evidenceMode==='NONE' ? '不使用资料库' : match?.evidenceMode==='AUTO' ? '自动选择相关证据' : '手动选择证据' }}</strong><em>敏感字段排除</em></div><div><span>AI 额度</span><strong>成功分析消耗 1 次</strong><em>剩余 {{ capabilities?.quota.remainingUnits ?? '—' }} 次</em></div></section>
            <label class="jm-consent"><input v-model="consent" type="checkbox"><span>我确认本次分析读取已确认的 JD、当前简历修订和上述授权证据；AI 不会自动修改简历，也不会生成录用概率。</span></label>
          </div><footer class="jm-wizard__footer"><button class="jm-secondary" type="button" @click="goBack">返回</button><button class="jm-primary" type="button" :disabled="pending || !consent || (capabilities?.quota.remainingUnits ?? 0)<1" @click="analyze"><LoaderCircle v-if="pending" class="jm-spin" :size="16" />开始 AI 深度匹配 · 1 次</button></footer>
        </template>
        </div>
        </Transition>
      </section>
      <JobMatchContext :match="match" :capabilities="capabilities" />
    </div>
  </div></main>
</template>
