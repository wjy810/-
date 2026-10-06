<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, BriefcaseBusiness, Check, CheckCircle2, Circle, FileText, Gauge, Headphones, Info, Languages, LoaderCircle, Mic, MonitorCheck, Save, ShieldCheck, Upload, Volume2 } from 'lucide-vue-next'
import AppChrome from '@/shared/ui/AppChrome.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import JobTaxonomyPicker from '@/features/ai-resume/components/JobTaxonomyPicker.vue'
import { listResumes } from '@/features/resume/services/resumeApi'
import { fetchCareerFiles, fetchCareerRecords, uploadCareerFile } from '@/features/career-library/services/careerLibraryApi'
import type { ResumeMasterSummary } from '@/features/resume/types'
import type { CareerFile, CareerRecord } from '@/features/career-library/types'
import type { JobTaxonomySelection } from '@/features/ai-resume/types'
import { errorMessage } from '@/shared/api/types'
import { useToastFeedback } from '@/shared/ui/toast'
import { createMockInterview, createMockInterviewDraft, saveMockInterviewDraft } from '../services/mockInterviewApi'
import type { MockInterviewCreate, MockInterviewDraft, MockInterviewMode } from '../types'
import { fetchJobMatch, fetchMatchReport } from '@/features/job-match/services/jobMatchApi'
import type { JobMatch } from '@/features/job-match/types'
import '../mock-interview.css'

type CheckState = 'idle' | 'checking' | 'passed' | 'failed'

const router = useRouter()
const route = useRoute()
const step = ref(1)
const draft = ref<MockInterviewDraft | null>(null)
const resumes = ref<ResumeMasterSummary[]>([])
const records = ref<CareerRecord[]>([])
const files = ref<CareerFile[]>([])
const loading = ref(true)
const saving = ref(false)
const starting = ref(false)
const uploading = ref(false)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'mock-interview-create-error')
const savedNotice = ref('')
const linkedMatch = ref<JobMatch | null>(null)
const microphoneState = ref<CheckState>('idle')
const speakerState = ref<CheckState>('idle')
const networkState = ref<CheckState>(navigator.onLine ? 'passed' : 'failed')
let testStream: MediaStream | null = null

const form = reactive<MockInterviewCreate>({
  resumeId: '', taxonomyNodeId: '', taxonomyCategoryId: '', taxonomyGroupId: '',
  careerRecordIds: [], careerFileIds: [], positionName: '', companyName: '', userNote: '',
  mode: (route.query.mode === 'VOICE' ? 'VOICE' : 'TEXT') as MockInterviewMode,
  interviewType: 'COMPREHENSIVE', difficulty: 'STANDARD', durationMinutes: 30, questionCount: 8,
  languageCode: 'zh-CN', feedbackMode: 'AFTER_SESSION', followUpEnabled: true, consentConfirmed: false,
  jobMatchId: typeof route.query.jobMatchId === 'string' ? route.query.jobMatchId : undefined,
})

const selectedResume = computed(() => resumes.value.find((item) => item.id === form.resumeId) ?? null)
const selectedRecords = computed(() => records.value.filter((item) => form.careerRecordIds.includes(item.id)))
const selectedFiles = computed(() => files.value.filter((item) => form.careerFileIds.includes(item.id)))
const deviceReady = computed(() => form.mode === 'TEXT'
  ? networkState.value === 'passed'
  : microphoneState.value === 'passed' && speakerState.value === 'passed' && networkState.value === 'passed')

const steps = [
  { no: 1, label: '选择资料' }, { no: 2, label: '目标岗位' }, { no: 3, label: '面试设置' },
  { no: 4, label: '设备检测' }, { no: 5, label: '确认开始' },
]

async function load(): Promise<void> {
  loading.value = true
  pageError.value = ''
  try {
    const jobMatchId = typeof route.query.jobMatchId === 'string' ? route.query.jobMatchId : ''
    const [resumeItems, recordPage, filePage, draftValue, matchValue, reportValue] = await Promise.all([
      listResumes(), fetchCareerRecords({ status: 'ACTIVE', size: 100 }),
      fetchCareerFiles({ status: 'ACTIVE', processingStatus: 'READY', size: 100 }), createMockInterviewDraft(),
      jobMatchId ? fetchJobMatch(jobMatchId) : Promise.resolve(null),
      jobMatchId ? fetchMatchReport(jobMatchId) : Promise.resolve(null),
    ])
    resumes.value = resumeItems.filter((item) => item.status !== 'ARCHIVED')
    records.value = recordPage.items.filter((item) => item.confirmed)
    files.value = filePage.items.filter((item) => item.status === 'ACTIVE')
    draft.value = draftValue
    linkedMatch.value = matchValue
    if (matchValue) {
      form.jobMatchId = matchValue.id
      form.positionName = matchValue.title
      form.companyName = matchValue.company ?? ''
      form.resumeId = matchValue.resume?.masterId ?? ''
      const topics = reportValue?.report.ai?.interviewTopics ?? []
      form.userNote = topics.length
        ? `优先覆盖岗位匹配报告中的面试主题：${topics.map((item) => typeof item === 'string' ? item : String(item.title ?? item.topic ?? '')).filter(Boolean).join('；')}`
        : '请围绕岗位匹配报告中的优势、缺口和高风险要求生成问题。'
      savedNotice.value = '已载入岗位匹配报告和投递简历'
    }
    if (!form.resumeId && resumes.value[0]) form.resumeId = resumes.value[0].id
  } catch (reason) {
    pageError.value = errorMessage(reason, '创建面试所需资料读取失败')
  } finally {
    loading.value = false
  }
}

function payload(): Record<string, unknown> { return JSON.parse(JSON.stringify(form)) as Record<string, unknown> }

async function persist(targetStep = step.value): Promise<void> {
  if (!draft.value) return
  saving.value = true
  savedNotice.value = ''
  try {
    draft.value = await saveMockInterviewDraft(draft.value.id, targetStep, payload(), draft.value.version)
    savedNotice.value = '草稿已保存'
  } catch (reason) {
    pageError.value = errorMessage(reason, '草稿保存失败')
  } finally {
    saving.value = false
  }
}

function validateCurrent(): boolean {
  pageError.value = ''
  if (step.value === 1 && !form.resumeId) pageError.value = '请先选择一份简历作为主要资料。'
  if (step.value === 2 && !form.positionName.trim()) pageError.value = '请选择目标岗位。'
  if (step.value === 4 && !deviceReady.value) pageError.value = form.mode === 'VOICE' ? '请完成麦克风、扬声器和网络检测。' : '当前网络不可用，请恢复连接后继续。'
  return !pageError.value
}

async function next(): Promise<void> {
  if (!validateCurrent()) return
  const target = Math.min(5, step.value + 1)
  await persist(target)
  step.value = target
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function back(): void {
  if (step.value === 1) void router.push({ name: 'mock-interviews' })
  else { step.value -= 1; window.scrollTo({ top: 0, behavior: 'smooth' }) }
}

function selectTaxonomy(selection: JobTaxonomySelection): void {
  form.positionName = selection.job.displayName
  form.taxonomyNodeId = selection.job.id
  form.taxonomyCategoryId = selection.categoryId
  form.taxonomyGroupId = selection.groupId
}

function toggle(list: string[], id: string): void {
  const index = list.indexOf(id)
  if (index >= 0) list.splice(index, 1)
  else list.push(id)
}

async function onUpload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  pageError.value = ''
  try {
    const uploaded = await uploadCareerFile(file, 'WORK_SAMPLE', file.name)
    files.value.unshift(uploaded.file)
    form.careerFileIds.push(uploaded.file.id)
    savedNotice.value = '资料已上传并加入本次面试'
  } catch (reason) {
    pageError.value = errorMessage(reason, '资料上传失败')
  } finally {
    uploading.value = false
  }
}

async function testMicrophone(): Promise<void> {
  microphoneState.value = 'checking'
  try {
    testStream?.getTracks().forEach((track) => track.stop())
    testStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
    const track = testStream.getAudioTracks()[0]
    microphoneState.value = track?.readyState === 'live' ? 'passed' : 'failed'
    window.setTimeout(() => { testStream?.getTracks().forEach((item) => item.stop()); testStream = null }, 1800)
  } catch {
    microphoneState.value = 'failed'
  }
}

async function testSpeaker(): Promise<void> {
  speakerState.value = 'checking'
  try {
    const Context = window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Context) throw new Error('AudioContext unavailable')
    const context = new Context()
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.frequency.value = 620
    gain.gain.value = 0.055
    oscillator.connect(gain).connect(context.destination)
    oscillator.start()
    oscillator.stop(context.currentTime + .32)
    await new Promise((resolve) => window.setTimeout(resolve, 420))
    await context.close()
    speakerState.value = 'passed'
  } catch {
    speakerState.value = 'failed'
  }
}

async function begin(): Promise<void> {
  if (!form.consentConfirmed) { pageError.value = '请确认本次面试的资料使用范围。'; return }
  if (!draft.value) return
  starting.value = true
  pageError.value = ''
  try {
    const created = await createMockInterview({ ...form, draftId: draft.value.id })
    await router.replace({ name: 'mock-interview-session', params: { sessionId: created.session.id } })
  } catch (reason) {
    pageError.value = errorMessage(reason, '模拟面试创建失败')
  } finally {
    starting.value = false
  }
}

function checkLabel(state: CheckState): string {
  return ({ idle: '尚未检测', checking: '检测中', passed: '检测通过', failed: '检测失败' } as Record<CheckState, string>)[state]
}
function typeLabel(value: string): string { return ({ COMPREHENSIVE: '综合面试', BEHAVIORAL: '行为面试', PROFESSIONAL: '专业面试', PRESSURE: '压力面试', QUICK: '快速热身' } as Record<string, string>)[value] ?? value }
function modeLabel(): string { return form.mode === 'VOICE' ? '语音模拟' : '文字模拟' }

onMounted(() => { window.addEventListener('online', () => { networkState.value = 'passed' }); window.addEventListener('offline', () => { networkState.value = 'failed' }); void load() })
onBeforeUnmount(() => { testStream?.getTracks().forEach((track) => track.stop()) })
</script>

<template>
  <AppChrome>
    <main class="mi-create-page">
      <header class="mi-create-top"><strong>创建模拟面试</strong><span v-if="saving"><LoaderCircle class="mi-spin" :size="14" />正在保存</span><span v-else-if="savedNotice"><Check :size="14" />{{ savedNotice }}</span></header>
      <div class="mi-create-shell">
        <ol class="mi-stepper" aria-label="创建步骤">
          <li v-for="item in steps" :key="item.no" :class="{ active: step === item.no, done: step > item.no }"><span><Check v-if="step > item.no" :size="15" /><template v-else>{{ item.no }}</template></span><strong>{{ item.label }}</strong></li>
        </ol>
        <div v-if="linkedMatch" class="mi-linked-match"><ShieldCheck :size="17" /><span><strong>已关联岗位匹配报告</strong><small>{{ linkedMatch.title }}<template v-if="linkedMatch.company"> · {{ linkedMatch.company }}</template>，出题将使用已冻结的要求、优势、缺口和面试主题。</small></span></div>
        <section v-if="loading" class="mi-card mi-loading"><span><LoaderCircle class="mi-spin" :size="20" />正在准备创建流程</span></section>

        <template v-else>
          <section v-if="step === 1" class="mi-step-content">
            <header><h1>选择本次面试资料</h1><p>资料仅用于生成个性化问题与追问，可在开始前调整</p></header>
            <div class="mi-material-layout">
              <div class="mi-material-main">
                <div class="mi-source-grid">
                  <article class="mi-card mi-source-card"><header><span class="source-a">A</span><div><h2>从简历工作台选择</h2><p>选择已有简历作为主要资料</p></div></header><label>主简历<AppSelect v-model="form.resumeId" aria-label="主简历" searchable search-placeholder="搜索简历"><option value="">请选择简历</option><option v-for="item in resumes" :key="item.id" :value="item.id">{{ item.title || '未命名简历' }} · {{ item.statusLabel || item.status }}</option></AppSelect></label><RouterLink v-if="!resumes.length" class="mi-secondary" to="/ai-resume/new">先创建简历</RouterLink></article>
                  <article class="mi-card mi-source-card"><header><span class="source-b">B</span><div><h2>上传截图或文件</h2><p>支持加入作品、项目和证明材料</p></div></header><label class="mi-upload-zone"><Upload :size="26" /><strong>{{ uploading ? '正在上传…' : '点击选择文件' }}</strong><small>PNG / JPEG / PDF / DOCX，单文件不超过 10 MiB</small><input type="file" accept=".png,.jpg,.jpeg,.pdf,.docx" :disabled="uploading" @change="onUpload" /></label></article>
                  <article class="mi-card mi-source-card"><header><span class="source-c">C</span><div><h2>从求职资料库选择</h2><p>可多选经历、成果与文件资料</p></div></header><p class="mi-source-count">已选 {{ form.careerRecordIds.length + form.careerFileIds.length }} 项资料</p><RouterLink class="mi-secondary" to="/career-library">管理求职资料</RouterLink></article>
                </div>
                <section class="mi-card mi-selected-materials"><header><h2>已选资料 {{ 1 + selectedRecords.length + selectedFiles.length }} 项</h2></header><div class="mi-material-row"><span class="material-icon is-blue"><FileText :size="17" /></span><div><strong>{{ selectedResume?.title || '请选择主简历' }}</strong><small>简历工作台 · 将冻结当前版本</small></div><span class="mi-tag mi-tag--blue">主简历</span></div><button v-for="item in records" :key="item.id" class="mi-material-row is-selectable" :class="{ selected: form.careerRecordIds.includes(item.id) }" type="button" @click="toggle(form.careerRecordIds, item.id)"><span class="material-icon is-purple"><BriefcaseBusiness :size="17" /></span><div><strong>{{ item.title }}</strong><small>{{ item.type }} · {{ item.organization || item.role || '已确认资料' }}</small></div><span><CheckCircle2 v-if="form.careerRecordIds.includes(item.id)" :size="18" /><Circle v-else :size="18" /></span></button><button v-for="item in files" :key="item.id" class="mi-material-row is-selectable" :class="{ selected: form.careerFileIds.includes(item.id) }" type="button" @click="toggle(form.careerFileIds, item.id)"><span class="material-icon is-green"><FileText :size="17" /></span><div><strong>{{ item.displayName }}</strong><small>{{ item.category }} · {{ Math.max(1, Math.round(item.sizeBytes / 1024)) }} KB</small></div><span><CheckCircle2 v-if="form.careerFileIds.includes(item.id)" :size="18" /><Circle v-else :size="18" /></span></button></section>
              </div>
              <aside class="mi-card mi-use-note"><h2><Info :size="18" />资料使用说明</h2><div><ShieldCheck :size="19" /><p><strong>本次会话授权</strong><small>资料仅用于本次模拟面试生成问题与追问。</small></p></div><div><Save :size="19" /><p><strong>可随时撤回</strong><small>开始前可以调整或移除已选择资料。</small></p></div><div><FileText :size="19" /><p><strong>不自动修改原资料</strong><small>系统不会覆盖简历或求职资料库内容。</small></p></div></aside>
            </div>
          </section>

          <section v-else-if="step === 2" class="mi-step-content">
            <header><h1>选择本次模拟的目标岗位</h1><p>系统根据标准岗位分类、简历和已授权资料生成针对性问题</p></header>
            <div class="mi-job-layout">
              <section class="mi-card mi-job-form"><h2>目标岗位</h2><label>标准岗位分类<JobTaxonomyPicker :selected-name="form.positionName" :selected-node-id="form.taxonomyNodeId" @select="selectTaxonomy" /></label><label>岗位名称<input v-model="form.positionName" class="mi-input" maxlength="160" placeholder="请选择或输入目标岗位" /></label><label>目标公司<input v-model="form.companyName" class="mi-input" maxlength="160" placeholder="可选" /></label></section>
              <aside class="mi-card mi-use-note"><h2><Info :size="18" />生成依据</h2><div><BriefcaseBusiness :size="19" /><p><strong>标准岗位分类</strong><small>用于确定面试方向和专业问题范围。</small></p></div><div><FileText :size="19" /><p><strong>已确认的个人事实</strong><small>问题只引用简历和本次授权的求职资料。</small></p></div></aside>
            </div>
          </section>

          <section v-else-if="step === 3" class="mi-step-content">
            <header><h1>设置面试方式与难度</h1><p>配置会冻结到本次训练，可在权限失败时由语音切换为文字</p></header>
            <div class="mi-settings-layout">
              <section class="mi-card mi-settings-modes"><h2>面试模式</h2><div class="mi-choice-grid is-two"><button type="button" :class="{ selected: form.mode === 'TEXT' }" @click="form.mode = 'TEXT'"><span class="radio"><Check v-if="form.mode === 'TEXT'" :size="11" /></span><MessageCircleIcon /><strong>文字模拟 <em>推荐</em></strong><small>纯文字问答，支持草稿、快捷键与逐题反馈</small></button><button type="button" :class="{ selected: form.mode === 'VOICE' }" @click="form.mode = 'VOICE'"><span class="radio"><Check v-if="form.mode === 'VOICE'" :size="11" /></span><Mic :size="31" /><strong>语音模拟</strong><small>口述回答，支持录音、实时转写和文字回退</small></button></div><hr /><h2>面试类型</h2><div class="mi-choice-grid is-four"><button v-for="item in [{v:'COMPREHENSIVE',n:'综合面试',d:'综合能力与岗位相关性'},{v:'BEHAVIORAL',n:'行为面试',d:'沟通动机与职业发展'},{v:'PROFESSIONAL',n:'专业面试',d:'专业技能与技术能力'},{v:'PRESSURE',n:'压力面试',d:'高压追问与稳定表达'}]" :key="item.v" type="button" :class="{ selected: form.interviewType === item.v }" @click="form.interviewType = item.v"><span class="radio"><Check v-if="form.interviewType === item.v" :size="11" /></span><BriefcaseBusiness :size="25" /><strong>{{ item.n }}</strong><small>{{ item.d }}</small></button></div></section>
              <section class="mi-card mi-parameters"><h2>训练参数</h2><label><span>面试时长</span><AppSelect v-model="form.durationMinutes" aria-label="面试时长"><option :value="15">15 分钟 · 快速热身</option><option :value="30">30 分钟 · 标准训练</option><option :value="45">45 分钟 · 深度训练</option><option :value="60">60 分钟 · 完整模拟</option></AppSelect></label><label><span>问题数量</span><AppSelect v-model="form.questionCount" aria-label="问题数量"><option :value="5">5 题</option><option :value="8">8 题</option><option :value="10">10 题</option><option :value="15">15 题</option></AppSelect></label><fieldset><legend>面试难度</legend><div class="mi-segment"><button type="button" :class="{ active: form.difficulty === 'FOUNDATION' }" @click="form.difficulty = 'FOUNDATION'">入门</button><button type="button" :class="{ active: form.difficulty === 'STANDARD' }" @click="form.difficulty = 'STANDARD'">标准</button><button type="button" :class="{ active: form.difficulty === 'ADVANCED' }" @click="form.difficulty = 'ADVANCED'">挑战</button></div></fieldset><label><span>语言</span><AppSelect v-model="form.languageCode" aria-label="面试语言"><option value="zh-CN">中文</option><option value="en-US">English</option></AppSelect></label><fieldset><legend>AI 反馈时机</legend><div class="mi-segment"><button type="button" :class="{ active: form.feedbackMode === 'AFTER_EACH' }" @click="form.feedbackMode = 'AFTER_EACH'">每题后</button><button type="button" :class="{ active: form.feedbackMode === 'AFTER_SESSION' }" @click="form.feedbackMode = 'AFTER_SESSION'">全部结束后</button></div></fieldset><label class="mi-toggle"><span><strong>动态追问</strong><small>根据回答生成后续追问</small></span><input v-model="form.followUpEnabled" type="checkbox" /><i /></label></section>
            </div>
            <div class="mi-config-preview"><MessageCircleIcon v-if="form.mode === 'TEXT'" /><Mic v-else :size="17" /><strong>{{ modeLabel() }}</strong><span>·</span><BriefcaseBusiness :size="16" /><strong>{{ typeLabel(form.interviewType) }}</strong><span>·</span><Gauge :size="16" /><strong>{{ form.difficulty === 'ADVANCED' ? '挑战' : form.difficulty === 'FOUNDATION' ? '入门' : '标准' }}</strong><span>·</span><Clock3Icon /><strong>{{ form.durationMinutes }} 分钟</strong><span>·</span><FileText :size="16" /><strong>{{ form.questionCount }} 题</strong></div>
          </section>

          <section v-else-if="step === 4" class="mi-step-content">
            <header><h1>{{ form.mode === 'VOICE' ? '检查语音设备' : '检查文字模拟环境' }}</h1><p>{{ form.mode === 'VOICE' ? '仅申请麦克风权限，不会请求摄像头' : '确认浏览器与网络状态，回答会同时保存在本地和服务端' }}</p></header>
            <div class="mi-device-layout">
              <section class="mi-card mi-device-card"><span class="mi-device-hero"><MonitorCheck v-if="form.mode === 'TEXT'" :size="48" /><Mic v-else :size="48" /></span><h2>{{ form.mode === 'TEXT' ? '文字回答环境' : '语音回答环境' }}</h2><p>{{ form.mode === 'TEXT' ? '可使用 Ctrl + Enter 提交；断网时继续保留本地草稿。' : '录音采用分片上传；转写失败时可以校正字幕或切换文字回答。' }}</p><div v-if="form.mode === 'VOICE'" class="mi-device-actions"><button class="mi-primary" type="button" :disabled="microphoneState === 'checking'" @click="testMicrophone"><Mic :size="17" />{{ microphoneState === 'passed' ? '重新检测麦克风' : '检测麦克风' }}</button><button class="mi-secondary" type="button" :disabled="speakerState === 'checking'" @click="testSpeaker"><Volume2 :size="17" />测试扬声器</button></div></section>
              <section class="mi-card mi-check-list"><h2>环境检测结果</h2><div><span><MonitorCheck :size="20" /></span><p><strong>浏览器能力</strong><small>自动保存、键盘操作与本地恢复可用</small></p><em class="passed">通过</em></div><div v-if="form.mode === 'VOICE'"><span><Mic :size="20" /></span><p><strong>麦克风权限</strong><small>只采集音频，不请求相机或视频</small></p><em :class="microphoneState">{{ checkLabel(microphoneState) }}</em></div><div v-if="form.mode === 'VOICE'"><span><Headphones :size="20" /></span><p><strong>扬声器播放</strong><small>用于播放提示音和检测输出设备</small></p><em :class="speakerState">{{ checkLabel(speakerState) }}</em></div><div><span><Gauge :size="20" /></span><p><strong>网络连接</strong><small>断网时保留本地草稿，恢复后自动同步</small></p><em :class="networkState">{{ checkLabel(networkState) }}</em></div><p v-if="form.mode === 'VOICE' && microphoneState === 'failed'" class="mi-device-warning">浏览器未授权麦克风。你可以在地址栏权限设置中重新允许，或返回上一步改为文字模拟。</p></section>
            </div>
          </section>

          <section v-else class="mi-step-content">
            <header><h1>确认本次模拟面试</h1><p>开始后将冻结所选资料与设置，保证复盘结果可追溯</p></header>
            <div class="mi-confirm-layout">
              <section class="mi-card mi-confirm-main"><header><span><ShieldCheck :size="24" /></span><div><h2>{{ form.positionName }} · {{ typeLabel(form.interviewType) }}</h2><p>{{ form.companyName || '未指定公司' }} · {{ modeLabel() }}</p></div></header><dl><div><dt>主简历</dt><dd>{{ selectedResume?.title || '—' }}</dd></div><div><dt>目标岗位</dt><dd>{{ form.positionName || '—' }}</dd></div><div><dt>补充资料</dt><dd>{{ selectedRecords.length + selectedFiles.length }} 项</dd></div><div><dt>训练参数</dt><dd>{{ form.durationMinutes }} 分钟 · {{ form.questionCount }} 题 · {{ form.difficulty === 'ADVANCED' ? '挑战' : form.difficulty === 'FOUNDATION' ? '入门' : '标准' }}</dd></div><div><dt>反馈策略</dt><dd>{{ form.feedbackMode === 'AFTER_EACH' ? '每题结束后反馈' : '全部结束后统一反馈' }} · {{ form.followUpEnabled ? '动态追问已开启' : '固定题目' }}</dd></div></dl><label class="mi-consent"><input v-model="form.consentConfirmed" type="checkbox" /><span><strong>我确认本次资料授权范围</strong><small>系统只使用上述已确认资料生成问题、追问和复盘，不评估年龄、性别、民族、外貌、口音、残障等受保护特征，也不会给出录用概率。</small></span></label></section>
              <aside class="mi-card mi-confirm-side"><h2>开始前提示</h2><div><CheckCircle2 :size="18" /><p><strong>回答只需基于真实经历</strong><small>不确定的数字和结论可以明确说明。</small></p></div><div><Save :size="18" /><p><strong>全程自动保存</strong><small>可暂停、恢复或提前生成阶段报告。</small></p></div><div><Languages :size="18" /><p><strong>语音可切换文字</strong><small>权限或转写失败不会丢失当前进度。</small></p></div><button class="mi-primary" type="button" :disabled="starting || !form.consentConfirmed" @click="begin"><LoaderCircle v-if="starting" class="mi-spin" :size="17" /><Mic v-else-if="form.mode === 'VOICE'" :size="17" /><MessageCircleIcon v-else />{{ starting ? '正在创建面试' : '开始模拟面试' }}</button></aside>
            </div>
          </section>
        </template>
      </div>
      <footer v-if="!loading" class="mi-create-footer"><button class="mi-secondary" type="button" @click="back"><ArrowLeft :size="17" />{{ step === 1 ? '返回' : '上一步' }}</button><div><button class="mi-secondary" type="button" :disabled="saving" @click="persist()"><Save :size="16" />保存草稿</button><button v-if="step < 5" class="mi-primary" type="button" @click="next">下一步：{{ steps[step]?.label }}<ArrowRight :size="17" /></button></div></footer>
    </main>
  </AppChrome>
</template>

<script lang="ts">
import { Clock3, MessageCircle } from 'lucide-vue-next'
export default { components: { MessageCircleIcon: MessageCircle, Clock3Icon: Clock3 } }
</script>

<style scoped>
.mi-create-page{min-height:calc(100vh - 60px);padding-bottom:88px;background:#fbfcfe;color:#15213b}.mi-create-top{height:52px;padding:0 28px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e4e9f1;background:#fff}.mi-create-top>span{display:flex;align-items:center;gap:5px;color:#178b55;font-size:12px}.mi-create-shell{width:min(1360px,calc(100% - 48px));margin:0 auto}.mi-stepper{height:80px;margin:0;padding:0 70px;display:grid;grid-template-columns:repeat(5,1fr);align-items:center;list-style:none}.mi-stepper li{position:relative;display:flex;align-items:center;justify-content:center;gap:8px;color:#717d92}.mi-stepper li:not(:last-child)::after{content:"";position:absolute;left:calc(50% + 54px);right:calc(-50% + 54px);height:1px;background:#b9c5d7}.mi-stepper li>span{position:relative;z-index:1;width:28px;height:28px;display:grid;place-items:center;border-radius:50%;color:#fff;background:#aab4c4}.mi-stepper li.active,.mi-stepper li.done{color:#0d61e9}.mi-stepper li.active>span,.mi-stepper li.done>span{background:#1264ef}.mi-stepper li.done::after{background:#4384ed}.mi-stepper strong{font-size:14px;white-space:nowrap}.mi-step-content>header{margin:5px 0 16px}.mi-step-content>header h1{font-size:24px}.mi-step-content>header p{color:#68758d}.mi-material-layout,.mi-job-layout{display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:22px}.mi-source-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.mi-source-card{min-height:248px;padding:18px;display:flex;flex-direction:column;gap:15px}.mi-source-card>header{display:flex;gap:11px}.mi-source-card>header>span{width:34px;height:34px;display:grid;place-items:center;flex:0 0 auto;border-radius:6px;color:#fff;font-size:20px}.source-a{background:#1464f6}.source-b{background:#09aa63}.source-c{background:#8249e8}.mi-source-card h2,.mi-selected-materials h2,.mi-use-note h2,.mi-job-form h2,.mi-jd-preview h2,.mi-settings-layout h2,.mi-device-layout h2,.mi-confirm-layout h2{font-size:15px}.mi-source-card p{color:#748096;font-size:12px}.mi-source-card label:not(.mi-upload-zone),.mi-job-form label{display:grid;gap:6px;color:#4f5d76;font-size:12px}.mi-source-card>a{margin-top:auto}.mi-upload-zone{min-height:124px;padding:14px;display:grid;place-items:center;align-content:center;gap:4px;border:1px dashed #aebdd2;border-radius:7px;color:#61708a;text-align:center}.mi-upload-zone strong{font-size:13px}.mi-upload-zone small{font-size:11px}.mi-upload-zone input{position:absolute;width:1px;height:1px;opacity:0}.mi-source-count{padding:24px 12px;border-radius:7px;background:#f7f4ff;text-align:center}.mi-selected-materials{margin-top:14px;padding:14px 18px}.mi-selected-materials>header{margin-bottom:5px}.mi-material-row{width:100%;min-height:58px;padding:9px 10px;display:grid;grid-template-columns:38px minmax(0,1fr) auto;align-items:center;gap:10px;border:0;border-top:1px solid #edf0f4;color:#172442;background:#fff;text-align:left}.mi-material-row.is-selectable:hover{background:#f7faff}.mi-material-row.selected{color:#105bd1;background:#f2f7ff}.material-icon{width:34px;height:34px;display:grid;place-items:center;border-radius:7px}.mi-material-row div{display:grid}.mi-material-row small{color:#7b879a;font-size:11px}.mi-use-note{align-self:start;padding:18px;color:#175ac1;background:#f4f8ff;border-color:#acd0ff}.mi-use-note>h2{display:flex;align-items:center;gap:8px;color:#1262e8}.mi-use-note>div{padding:18px 0;display:flex;gap:11px;border-bottom:1px solid #cfe0fa}.mi-use-note>div:last-child{border:0}.mi-use-note p{display:grid}.mi-use-note small{margin-top:3px;color:#667792;font-size:11px}.mi-job-layout{grid-template-columns:1fr 1.35fr 240px}.mi-job-form,.mi-jd-preview{padding:20px}.mi-job-form{display:grid;align-content:start;gap:14px}.mi-linked{padding:10px;display:flex;align-items:center;gap:6px;border-radius:6px;color:#0b8b50;background:#edfaf3}.mi-jd-preview>header{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #e6eaf0;padding-bottom:13px}.mi-jd-preview>header p{color:#7d8899;font-size:11px}.mi-jd-preview h3{margin:15px 0 5px;font-size:13px}.mi-jd-preview>p{color:#4f5d76;white-space:pre-wrap}.mi-keywords{display:flex;flex-wrap:wrap;gap:6px}.mi-keywords span{padding:4px 8px;border-radius:4px;color:#1160df;background:#edf4ff;font-size:11px}.mi-jd-preview dl{display:grid;grid-template-columns:1fr 1fr;gap:10px}.mi-jd-preview dl div{padding:10px;border-radius:6px;background:#f7f9fc}.mi-jd-preview dt{color:#7a879b;font-size:11px}.mi-jd-preview dd{margin:2px 0 0}.mi-settings-layout{display:grid;grid-template-columns:1.2fr 1fr;gap:22px}.mi-settings-modes,.mi-parameters{padding:22px}.mi-choice-grid{display:grid;gap:12px}.mi-choice-grid.is-two{grid-template-columns:repeat(2,1fr)}.mi-choice-grid.is-four{grid-template-columns:repeat(4,1fr)}.mi-choice-grid button{position:relative;min-height:145px;padding:23px 14px 14px;display:flex;align-items:center;flex-direction:column;gap:6px;border:1px solid #d3dbe7;border-radius:7px;color:#51607a;background:#fff}.mi-choice-grid button.selected{color:#0d61e9;border-color:#1464f6;background:#f7faff;box-shadow:inset 0 0 0 1px #1464f6}.mi-choice-grid .radio{position:absolute;top:13px;left:13px;width:18px;height:18px;display:grid;place-items:center;border:1px solid #aeb9ca;border-radius:50%}.mi-choice-grid button.selected .radio{color:#fff;border-color:#1464f6;background:#1464f6}.mi-choice-grid strong{font-size:14px}.mi-choice-grid em{padding:1px 5px;border-radius:4px;color:#07934f;background:#e7f7ee;font-size:9px;font-style:normal}.mi-choice-grid small{max-width:160px;color:#748096;line-height:1.5;text-align:center}.mi-settings-modes hr{height:1px;margin:18px 0;border:0;background:#e4e9f1}.mi-parameters{display:grid;align-content:start;gap:14px}.mi-parameters>label:not(.mi-toggle){display:grid;grid-template-columns:110px minmax(0,1fr);align-items:center}.mi-parameters fieldset{margin:0;padding:0;display:grid;grid-template-columns:110px minmax(0,1fr);align-items:center;border:0}.mi-parameters legend{grid-column:1;padding:0}.mi-parameters fieldset>.mi-segment{grid-column:2;min-width:0}.mi-segment{display:grid;grid-auto-flow:column;grid-auto-columns:minmax(0,1fr);border:1px solid #d2dae6;border-radius:6px;overflow:hidden}.mi-segment button{min-width:0;height:38px;border:0;border-right:1px solid #d2dae6;color:#4c5b73;background:#fff;white-space:nowrap}.mi-segment button:last-child{border:0}.mi-segment button.active{color:#fff;background:#1464f6}.mi-toggle{display:flex;align-items:center;justify-content:space-between;gap:15px}.mi-toggle>span{display:grid}.mi-toggle small{color:#7f8a9c}.mi-toggle input{position:absolute;opacity:0}.mi-toggle i{position:relative;width:42px;height:24px;border-radius:12px;background:#c9d1dc}.mi-toggle i::after{content:"";position:absolute;top:3px;left:3px;width:18px;height:18px;border-radius:50%;background:#fff;transition:transform .16s}.mi-toggle input:checked+i{background:#1464f6}.mi-toggle input:checked+i::after{transform:translateX(18px)}.mi-config-preview{margin-top:16px;min-height:66px;padding:0 20px;display:flex;align-items:center;gap:10px;border:1px solid #bcd5fa;border-radius:7px;color:#135fcf;background:#f2f7ff}.mi-device-layout{display:grid;grid-template-columns:1.1fr 1fr;gap:22px}.mi-device-card,.mi-check-list{padding:28px}.mi-device-card{text-align:center}.mi-device-hero{width:106px;height:106px;margin:0 auto 16px;display:grid;place-items:center;border-radius:50%;color:#1264ef;background:#ebf3ff}.mi-device-card>p{max-width:560px;margin:7px auto 20px;color:#66738b}.mi-device-actions{display:flex;justify-content:center;gap:10px}.mi-check-list>div{min-height:75px;padding:12px 0;display:grid;grid-template-columns:38px 1fr auto;align-items:center;gap:10px;border-bottom:1px solid #e9edf3}.mi-check-list>div>span{width:36px;height:36px;display:grid;place-items:center;border-radius:7px;color:#1264ef;background:#edf4ff}.mi-check-list p{display:grid}.mi-check-list small{color:#7b879b}.mi-check-list em{font-style:normal;font-size:12px}.mi-check-list em.passed{color:#079b56}.mi-check-list em.failed{color:#db3445}.mi-check-list em.checking{color:#1264ef}.mi-check-list em.idle{color:#7b879b}.mi-device-warning{margin-top:15px;padding:10px;border-radius:6px;color:#a95d00;background:#fff4df}.mi-confirm-layout{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:22px}.mi-confirm-main,.mi-confirm-side{padding:24px}.mi-confirm-main>header{padding-bottom:18px;display:flex;align-items:center;gap:13px;border-bottom:1px solid #e5e9f0}.mi-confirm-main>header>span{width:46px;height:46px;display:grid;place-items:center;border-radius:9px;color:#1464f6;background:#eaf2ff}.mi-confirm-main>header p{color:#6f7b90}.mi-confirm-main dl{display:grid;grid-template-columns:repeat(2,1fr);gap:0 30px}.mi-confirm-main dl div{padding:14px 0;border-bottom:1px solid #edf0f4}.mi-confirm-main dt{color:#7d889c;font-size:12px}.mi-confirm-main dd{margin:3px 0 0;font-weight:600}.mi-consent{margin-top:18px;padding:15px;display:flex;gap:11px;border:1px solid #bed3f4;border-radius:7px;background:#f6f9ff}.mi-consent input{width:17px;height:17px;accent-color:#1464f6}.mi-consent span{display:grid}.mi-consent small{margin-top:4px;color:#6c7a91}.mi-confirm-side{align-self:start}.mi-confirm-side>div{padding:14px 0;display:flex;gap:10px;border-bottom:1px solid #edf0f4;color:#0b9152}.mi-confirm-side p{display:grid;color:#18243f}.mi-confirm-side small{color:#7b879a}.mi-confirm-side button{width:100%;margin-top:22px}.mi-create-footer{position:fixed;z-index:25;right:0;bottom:0;left:232px;height:76px;padding:0 28px;display:flex;align-items:center;justify-content:space-between;border-top:1px solid #dfe5ee;background:rgba(255,255,255,.96);backdrop-filter:blur(10px)}.mi-create-footer>div{display:flex;gap:12px}
.mi-job-layout{grid-template-columns:minmax(0,1fr) 320px}
@media(max-width:1100px){.mi-stepper{padding:0}.mi-material-layout,.mi-job-layout,.mi-settings-layout,.mi-device-layout,.mi-confirm-layout{grid-template-columns:1fr}.mi-use-note{display:none}.mi-source-grid{grid-template-columns:1fr 1fr}.mi-choice-grid.is-four{grid-template-columns:1fr 1fr}}
@media(max-width:900px){.mi-create-footer{left:64px}}
@media(max-width:700px){.mi-create-shell{width:calc(100% - 28px)}.mi-create-top{padding:0 14px}.mi-stepper{height:64px;grid-template-columns:repeat(5,1fr)}.mi-stepper strong{display:none}.mi-stepper li:not(:last-child)::after{left:calc(50% + 20px);right:calc(-50% + 20px)}.mi-source-grid,.mi-choice-grid.is-two,.mi-choice-grid.is-four{grid-template-columns:1fr}.mi-job-layout{display:block}.mi-job-layout>*{margin-bottom:12px}.mi-settings-layout,.mi-device-layout,.mi-confirm-layout{gap:12px}.mi-parameters>label:not(.mi-toggle),.mi-parameters fieldset{grid-template-columns:1fr;gap:6px}.mi-parameters legend,.mi-parameters fieldset>.mi-segment{grid-column:1}.mi-config-preview{overflow:auto;white-space:nowrap}.mi-confirm-main dl{grid-template-columns:1fr}.mi-create-footer{left:64px;height:70px;padding:0 14px}.mi-create-footer .mi-secondary:nth-child(1){padding:0 10px}.mi-create-footer>div>.mi-secondary{display:none}}
.mi-linked-match{min-height:52px;margin:0 0 14px;padding:10px 14px;display:flex;align-items:center;gap:10px;border:1px solid #b8d4fb;border-radius:7px;color:#125fcf;background:#f3f8ff}.mi-linked-match>span{display:grid}.mi-linked-match small{margin-top:2px;color:#5d6d86}
</style>
