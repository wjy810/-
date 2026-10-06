<script setup lang="ts">
import { computed, defineAsyncComponent, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, ArrowRight, Bookmark, Bot, BrainCircuit, BriefcaseBusiness, CalendarCheck2, Check, CheckCircle2,
  ChevronRight, CircleAlert, Compass, Database, GitCompareArrows, Heart, Layers3, Link2,
  LoaderCircle, LockKeyhole, MessageCircle, Pencil, Save, Send, ShieldCheck, Sparkles, Target, Trash2, UserRound, X,
} from 'lucide-vue-next'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiErrorState from '@/shared/ui/UiErrorState.vue'
import { useLoadState } from '@/shared/lib/useLoadState'
import { useToastFeedback } from '@/shared/ui/toast'
import JobTaxonomyPicker from '@/features/ai-resume/components/JobTaxonomyPicker.vue'
import type { JobTaxonomySelection } from '@/features/ai-resume/types'
import { errorMessage } from '@/shared/api/types'
import { cancelTask, fetchTask, isOpenTask, taskStatusLabel } from '@/shared/api/task'
import type { TaskView } from '@/shared/api/task'
import CareerSkillPicker from '../components/CareerSkillPicker.vue'
import CareerProfilePreview from '../components/CareerProfilePreview.vue'
import {
  answerCareerPlanningInterview,
  authorizeCareerPlanningEvidence,
  confirmCareerGoal,
  confirmCareerPlanningProfile,
  fetchCareerPlanningEvidence,
  fetchCareerPlanningOverview,
  fetchCareerPlanningSession,
  prepareCareerGoalConfirmation,
  reviewCareerPlanningProfile,
  saveCareerPlanningInterviewDraft,
  setCareerRecommendationFavorite,
  startCareerPlanning,
  startCareerPlanningInterviewTask,
  startCareerRecommendationTask,
  updateCareerPlanningProfile,
} from '../services/careerPlanningApi'
import type {
  CareerPlanningOverview, CareerPlanningSession, CareerRecommendation, ConfirmationToken,
  EvidenceOption, InterviewRound, PlanningMessage, ProfileItem,
} from '../types'
import type { CareerSkillDraft } from '../utils/skillPicker'
import { selectedCareerComparisons, toggleCareerComparison } from '../utils/comparison'
import {
  EVIDENCE_SCOPE_OPTIONS, evidenceSelectionCount, hasEvidenceScope,
  hydrateEvidenceScopeSelections, setEvidenceScope, toEvidenceAuthorizationSelections,
} from '../utils/evidenceAuthorization'
import type { EvidenceScope, EvidenceScopeSelections } from '../utils/evidenceAuthorization'
import { shouldAcceptCareerPlanningSnapshot } from '../utils/sessionSnapshot'
import {
  careerEvidenceStrengthLabel, careerProfileSectionLabel, careerSourceRefLabels, isCareerExecutionPhase,
} from '../utils/presentation'
import '../career-planning.css'

// The workbench pulls in the canvas renderer; load it only once a plan reaches the canvas.
const CareerPlanningWorkbench = defineAsyncComponent({
  loader: () => import('../components/CareerPlanningWorkbench.vue'),
  delay: 120,
  loadingComponent: { render: () => h('div', { class: 'cp-loading' }, [h(LoaderCircle, { class: 'cp-spin', size: 24 }), h('span', '正在载入能力画布')]) },
  errorComponent: { render: () => h(UiErrorState, { title: '能力画布没有加载成功', onRetry: () => window.location.reload() }) },
  onError: (_error, retry, fail, attempts) => (attempts < 3 ? retry() : fail()),
})

const route = useRoute()
const router = useRouter()
const overview = ref<CareerPlanningOverview | null>(null)
const session = ref<CareerPlanningSession | null>(null)
const pending = ref('')
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'career-planning-page-error')
useToastFeedback(notice, 'success', 'career-planning-page-notice')
const profileTab = ref<'BASICS' | 'SKILLS' | 'PREFERENCES'>('BASICS')
const knownTargetSetup = ref(false)
const selectedTargetName = ref('')
const evidenceOpen = ref(false)
const evidenceLoading = ref(false)
const evidenceOptions = ref<EvidenceOption[]>([])
const evidenceScopeSelections = ref<EvidenceScopeSelections>({})
const confirmation = ref<ConfirmationToken | null>(null)
const confirmationRecommendation = ref<CareerRecommendation | null>(null)
const detailRecommendation = ref<CareerRecommendation | null>(null)
const compareOpen = ref(false)
const compareIds = ref<string[]>([])
const aiConsent = ref(false)
const confirmedItemIds = ref<string[]>([])
const reviewDraftItems = ref<ProfileItem[]>([])
const reviewEditingId = ref('')
const forceProfileEdit = ref(false)
const goalAcknowledged = ref(false)
const interviewAnswers = reactive<Record<string, string>>({})
const interviewQuestionIndex = ref(0)
const interviewDraftState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const selectedSkills = ref<CareerSkillDraft[]>([])
let eventSource: EventSource | null = null
let eventRefreshTimer = 0
let taskPollTimer = 0
let profileAutosaveTimer = 0
let hydratingProfile = false
let profileSaveInFlight: Promise<boolean> | null = null
const activeAiTask = ref<TaskView | null>(null)
const assistantStream = ref('')
const taskCheckpoint = ref('')
const streamConnected = ref(true)
const profileSaveState = ref<'idle' | 'waiting' | 'saving' | 'saved' | 'error'>('idle')
const interviewScroll = ref<HTMLElement | null>(null)
let lastSavedProfileSignature = ''

const form = reactive({
  ageRange: '', identity: '', currentRole: '', education: '', major: '', graduationYear: '',
  experienceYears: '', weeklyLearningHours: '', experienceText: '',
  preferredLocation: '', workMode: '', directionPreference: '', learningGoal: '',
})

const ageOptions = [
  { value: '', label: '请选择年龄段' }, { value: '18岁以下', label: '18岁以下' },
  { value: '18~22岁', label: '18~22岁' }, { value: '23~25岁', label: '23~25岁' },
  { value: '26~30岁', label: '26~30岁' }, { value: '31~35岁', label: '31~35岁' },
  { value: '36岁及以上', label: '36岁及以上' },
]
const identityOptions = [
  { value: '', label: '请选择当前身份' }, { value: '学生', label: '学生' },
  { value: '应届生', label: '应届生' }, { value: '在职，准备转行', label: '在职，准备转行' },
  { value: '在职，寻求发展', label: '在职，寻求发展' }, { value: '待业求职', label: '待业求职' },
]
const educationOptions = [
  { value: '', label: '请选择最高学历' }, { value: '高中/中专', label: '高中/中专' },
  { value: '大专', label: '大专' }, { value: '本科', label: '本科' },
  { value: '硕士', label: '硕士' }, { value: '博士', label: '博士' },
]
const experienceOptions = [
  { value: '', label: '请选择工作年限' }, { value: '暂无正式经验', label: '暂无正式经验' },
  { value: '1年以内', label: '1年以内' }, { value: '1~3年', label: '1~3年' },
  { value: '3~5年', label: '3~5年' }, { value: '5年以上', label: '5年以上' },
]
const weeklyOptions = [
  { value: '', label: '请选择每周投入' }, { value: '3小时以内', label: '3小时以内' },
  { value: '3~5小时', label: '3~5小时' }, { value: '5~10小时', label: '5~10小时' },
  { value: '10~15小时', label: '10~15小时' }, { value: '15小时以上', label: '15小时以上' },
]
const workModeOptions = [
  { value: '', label: '不限工作方式' }, { value: '线下办公', label: '线下办公' },
  { value: '混合办公', label: '混合办公' }, { value: '远程办公', label: '远程办公' },
]
const reviewProficiencyOptions = [
  { value: 'PRACTICED', label: '已实践' },
  { value: 'LEARNING', label: '学习中' },
]

const phase = computed(() => session.value?.phase ?? 'WELCOME')
const openRound = computed<InterviewRound | null>(() => session.value?.interviewRounds.find(item => item.status === 'OPEN') ?? null)
const currentInterviewQuestion = computed(() => openRound.value?.questions[interviewQuestionIndex.value] ?? null)
const completedInterviewQuestions = computed(() => {
  const round = openRound.value
  if (!round) return []
  return round.questions.slice(0, interviewQuestionIndex.value).map(question => ({
    ...question,
    answer: interviewAnswers[question.id] ?? '',
  }))
})
const activePermissions = computed(() => session.value?.permissions.filter(item => item.status === 'ACTIVE') ?? [])
const quotaText = computed(() => overview.value ? `${overview.value.quota.remainingUnits} 次 AI 额度` : 'AI 额度')
const recommendationSet = computed(() => session.value?.recommendationSet ?? null)
const favoriteCount = computed(() => recommendationSet.value?.recommendations.filter(item => item.favorite).length ?? 0)
const comparedRecommendations = computed(() => selectedCareerComparisons(recommendationSet.value?.recommendations ?? [], compareIds.value))
const selectedEvidenceCount = computed(() => evidenceSelectionCount(evidenceScopeSelections.value))
const activeInterviewTask = computed(() => activeAiTask.value?.taskType === 'CAREER_PLANNING_INTERVIEW'
  && isOpenTask(activeAiTask.value.status))
const activeRecommendationTask = computed(() => activeAiTask.value?.taskType === 'CAREER_PLANNING_RECOMMENDATIONS'
  && isOpenTask(activeAiTask.value.status))
const interviewMessages = computed(() => session.value?.messages.filter(message =>
  ['GUIDANCE', 'EVIDENCE_AUTHORIZED', 'INTERVIEW_REQUEST', 'QUESTION_BATCH', 'INTERVIEW_ANSWERS', 'PROFILE_REVIEW_READY']
    .includes(message.type)) ?? [])
const taskProgress = computed(() => Math.max(0, Math.min(100, activeAiTask.value?.progressPercent ?? 0)))
const taskStatusText = computed(() => {
  const checkpointLabels: Record<string, string> = {
    QUEUED: '任务已进入队列', STARTING: '正在启动', PREPARING_INTERVIEW_CONTEXT: '正在核对画像事实',
    VALIDATING_PROFILE: '正在校验画像快照', GENERATING_RECOMMENDATIONS: '正在生成可比较方向',
    PERSISTING_INTERVIEW: '正在保存结构化问题', PERSISTING_RECOMMENDATIONS: '正在保存推荐结果',
  }
  return checkpointLabels[taskCheckpoint.value || activeAiTask.value?.checkpointCode || '']
    ?? taskStatusLabel(activeAiTask.value?.status)
})
const reviewDirty = computed(() => {
  const original = session.value?.profile.items ?? []
  const signature = (items: ProfileItem[]) => JSON.stringify(items.map(item => ({
    id: item.id,
    section: item.section,
    claimType: item.claimType,
    title: item.title,
    payload: item.payload,
    sourceRefs: item.sourceRefs,
    locked: item.locked,
    sortOrder: item.sortOrder,
  })))
  return signature(original) !== signature(reviewDraftItems.value)
})
const confirmationBasicItems = computed(() => {
  const basics = session.value?.profile.basics ?? {}
  return [
    { label: '当前身份', value: basics.identity },
    { label: '当前职业', value: basics.currentRole },
    { label: '最高学历', value: basics.education },
    { label: '专业', value: basics.major },
    { label: '毕业年份', value: basics.graduationYear },
    { label: '工作年限', value: basics.experienceYears },
    { label: '每周投入', value: basics.weeklyLearningHours },
    { label: '年龄段', value: basics.ageRange },
  ].filter(item => typeof item.value === 'string' && item.value.trim()) as Array<{ label: string; value: string }>
})
const reviewSections = computed(() => [
  {
    key: 'INCLUDED',
    title: '将纳入推荐',
    description: '这些事实会在最终确认后进入职业推荐快照。',
    items: reviewDraftItems.value.filter(item => confirmedItemIds.value.includes(item.id)),
  },
  {
    key: 'EXCLUDED',
    title: '待确认或不纳入',
    description: '未勾选内容会保留在当前草稿中，但不会进入推荐。',
    items: reviewDraftItems.value.filter(item => !confirmedItemIds.value.includes(item.id)),
  },
])

function messageText(message: PlanningMessage): string {
  if (message.body?.trim()) return message.body.trim()
  if (message.type !== 'INTERVIEW_ANSWERS') return ''
  const answers = Array.isArray(message.payload.answers) ? message.payload.answers : []
  return answers.map((answer) => {
    if (!answer || typeof answer !== 'object') return ''
    const value = answer as Record<string, unknown>
    return typeof value.answer === 'string' ? value.answer.trim() : ''
  }).filter(Boolean).join('\n')
}

function taskStorageKey(sessionId: string): string {
  return `career-planning:foreground-task:${sessionId}`
}

function stringValue(source: Record<string, unknown>, key: string): string {
  const value = source[key]
  return typeof value === 'string' ? value : ''
}

function hydrateForm(value: CareerPlanningSession, interviewPosition?: number): void {
  hydratingProfile = true
  const basics = value.profile.basics
  const preferences = value.profile.preferences
  form.ageRange = stringValue(basics, 'ageRange')
  form.identity = stringValue(basics, 'identity')
  form.currentRole = stringValue(basics, 'currentRole')
  form.education = stringValue(basics, 'education')
  form.major = stringValue(basics, 'major')
  form.graduationYear = stringValue(basics, 'graduationYear')
  form.experienceYears = stringValue(basics, 'experienceYears')
  form.weeklyLearningHours = stringValue(basics, 'weeklyLearningHours')
  form.preferredLocation = stringValue(preferences, 'preferredLocation')
  form.workMode = stringValue(preferences, 'workMode')
  form.directionPreference = stringValue(preferences, 'directionPreference')
  form.learningGoal = stringValue(preferences, 'learningGoal')
  selectedSkills.value = value.profile.items
    .filter(item => item.section === 'SKILLS' && item.payload.origin === 'PROFILE_FORM')
    .map(item => ({
      name: item.title,
      status: item.payload.proficiency === 'LEARNING' ? 'LEARNING' : 'PRACTICED',
      custom: item.payload.customSkill === true,
    }))
  form.experienceText = value.profile.items.find(item => item.section === 'EXPERIENCE' && item.payload.origin === 'PROFILE_FORM')?.title ?? ''
  confirmedItemIds.value = value.profile.items.filter(item => item.confirmed || item.claimType !== 'INFERENCE').map(item => item.id)
  reviewDraftItems.value = value.profile.items.map(item => ({
    ...item,
    payload: { ...item.payload },
    sourceRefs: [...item.sourceRefs],
  }))
  reviewEditingId.value = ''
  Object.keys(interviewAnswers).forEach(key => delete interviewAnswers[key])
  const round = value.interviewRounds.find(item => item.status === 'OPEN')
  if (round) {
    round.answers.forEach(answer => { interviewAnswers[answer.questionId] = answer.answer })
    const restoredIndex = interviewPosition ?? round.answers.length
    interviewQuestionIndex.value = Math.max(0, Math.min(restoredIndex, Math.max(0, round.questions.length - 1)))
    interviewDraftState.value = round.answers.length ? 'saved' : 'idle'
  } else {
    interviewQuestionIndex.value = 0
    interviewDraftState.value = 'idle'
  }
  const validRecommendationIds = new Set(value.recommendationSet?.recommendations.map(item => item.id) ?? [])
  compareIds.value = compareIds.value.filter(id => validRecommendationIds.has(id))
  lastSavedProfileSignature = profileDraftSignature()
  void nextTick(() => { hydratingProfile = false })
}

const routeSessionId = computed(() => typeof route.params.sessionId === 'string' ? route.params.sessionId : '')
// A failed read is an error with retry, never the welcome page: that would invite a duplicate plan.
const pageLoad = useLoadState(async (signal) => {
  const routeId = routeSessionId.value
  const overviewValue = await fetchCareerPlanningOverview()
  const value = routeId ? await fetchCareerPlanningSession(routeId) : null
  if (!signal.aborted) {
    overview.value = overviewValue
    session.value = value
    forceProfileEdit.value = false
    if (value) hydrateForm(value)
  }
  return value
})
const pageReady = computed(() => pageLoad.loaded.value
  && (!routeSessionId.value || routeSessionId.value === session.value?.id))

async function load(): Promise<void> {
  await pageLoad.load()
}

async function start(mode: 'AI_DISCOVERY' | 'KNOWN_TARGET'): Promise<void> {
  if (mode === 'KNOWN_TARGET' && !session.value && !knownTargetSetup.value) {
    knownTargetSetup.value = true
    return
  }
  if (mode === 'KNOWN_TARGET' && !selectedTargetName.value) {
    pageError.value = '请先从岗位分类中选择目标职业。'
    return
  }
  if (!aiConsent.value) {
    pageError.value = '请先勾选“允许本次规划调用 AI”。'
    return
  }
  pending.value = 'start'; pageError.value = ''
  try {
    const value = await startCareerPlanning(mode, aiConsent.value, mode === 'KNOWN_TARGET' ? selectedTargetId.value : undefined)
    session.value = value
    hydrateForm(value)
    await router.replace({ name: 'career-planning-session', params: { sessionId: value.id } })
  } catch (reason) {
    pageError.value = errorMessage(reason, '无法开始职业规划')
  } finally { pending.value = '' }
}

const selectedTargetId = ref('')
function selectTarget(selection: JobTaxonomySelection): void {
  selectedTargetId.value = selection.job.id
  selectedTargetName.value = selection.job.displayName
}

function itemWrite(item: ProfileItem) {
  return { id: item.id, section: item.section, claimType: item.claimType, title: item.title, payload: item.payload, sourceRefs: item.sourceRefs, locked: item.locked, sortOrder: item.sortOrder }
}

function profileDraftSignature(): string {
  return JSON.stringify({
    form: { ...form },
    skills: selectedSkills.value.map(skill => ({ name: skill.name, status: skill.status, custom: skill.custom === true })),
  })
}

function profileWriteCommand(value: CareerPlanningSession): Record<string, unknown> {
  const retained = value.profile.items.filter(item => item.payload.origin !== 'PROFILE_FORM').map(itemWrite)
  const oldManual = new Map(value.profile.items.filter(item => item.payload.origin === 'PROFILE_FORM')
    .map(item => [`${item.section}:${item.title}`, item]))
  const manual: Array<{ id?: string; section: string; claimType: 'SELF_REPORTED'; title: string; payload: Record<string, unknown>; sourceRefs: unknown[]; locked: boolean; sortOrder: number }> = selectedSkills.value
    .filter(skill => skill.name.trim())
    .map((skill, index) => {
      const name = skill.name.trim()
      const old = oldManual.get(`SKILLS:${name}`)
      return { id: old?.id, section: 'SKILLS', claimType: 'SELF_REPORTED', title: name, payload: { origin: 'PROFILE_FORM', proficiency: skill.status, customSkill: skill.custom === true }, sourceRefs: [], locked: false, sortOrder: 20 + index }
    })
  if (form.experienceText.trim()) {
    const title = form.experienceText.trim()
    const old = oldManual.get(`EXPERIENCE:${title}`)
    manual.push({ id: old?.id, section: 'EXPERIENCE', claimType: 'SELF_REPORTED', title, payload: { origin: 'PROFILE_FORM', description: title }, sourceRefs: [], locked: false, sortOrder: 60 })
  }
  return {
    basics: {
      ageRange: form.ageRange, identity: form.identity, currentRole: form.currentRole,
      education: form.education, major: form.major, graduationYear: form.graduationYear,
      experienceYears: form.experienceYears, weeklyLearningHours: form.weeklyLearningHours,
    },
    preferences: {
      preferredLocation: form.preferredLocation, workMode: form.workMode,
      directionPreference: form.directionPreference, learningGoal: form.learningGoal,
    },
    constraints: { weeklyLearningHours: form.weeklyLearningHours },
    objectiveTaxonomyId: value.profile.objectiveTaxonomyId,
    items: [...retained, ...manual], expectedVersion: value.profile.version,
  }
}

async function saveProfile(requireComplete = true, announce = true): Promise<boolean> {
  window.clearTimeout(profileAutosaveTimer)
  if (profileSaveInFlight) await profileSaveInFlight
  const current = session.value
  if (!current) return false
  if (requireComplete && (!form.identity || !form.education || !form.experienceYears || !form.weeklyLearningHours)) {
    pageError.value = '请先完成当前身份、学历、工作年限和每周投入时间。'
    profileTab.value = 'BASICS'
    return false
  }
  const signature = profileDraftSignature()
  if (!requireComplete && signature === lastSavedProfileSignature) {
    profileSaveState.value = 'saved'
    return true
  }
  if (announce) { pending.value = 'profile'; pageError.value = ''; notice.value = '' }
  profileSaveState.value = 'saving'
  const operation = (async () => {
    try {
      const value = await updateCareerPlanningProfile(current.id, profileWriteCommand(current))
      const unchangedWhileSaving = signature === profileDraftSignature()
      updateWorkbenchSession(value)
      lastSavedProfileSignature = signature
      if (unchangedWhileSaving) hydrateForm(value)
      else scheduleProfileAutosave(80)
      profileSaveState.value = 'saved'
      if (announce) notice.value = '职业画像草稿已保存。'
      return true
    } catch (reason) {
      profileSaveState.value = 'error'
      pageError.value = errorMessage(reason, '职业画像保存失败')
      return false
    } finally {
      if (announce && pending.value === 'profile') pending.value = ''
    }
  })()
  profileSaveInFlight = operation
  try { return await operation }
  finally { if (profileSaveInFlight === operation) profileSaveInFlight = null }
}

function scheduleProfileAutosave(delay = 1000): void {
  window.clearTimeout(profileAutosaveTimer)
  if (!session.value || session.value.phase !== 'PROFILE' || hydratingProfile) return
  profileSaveState.value = 'waiting'
  profileAutosaveTimer = window.setTimeout(() => void saveProfile(false, false), delay)
}

async function openEvidence(): Promise<void> {
  if (!(await saveProfile())) return
  if (!session.value) return
  evidenceOpen.value = true; evidenceLoading.value = true; pageError.value = ''
  try {
    evidenceOptions.value = await fetchCareerPlanningEvidence(session.value.id)
    evidenceScopeSelections.value = hydrateEvidenceScopeSelections(evidenceOptions.value)
  } catch (reason) {
    pageError.value = errorMessage(reason, '资料库记录读取失败')
  } finally { evidenceLoading.value = false }
}

function toggleEvidenceScope(sourceId: string, scope: EvidenceScope, selected: boolean): void {
  evidenceScopeSelections.value = setEvidenceScope(evidenceScopeSelections.value, sourceId, scope, selected)
}

async function saveEvidence(): Promise<void> {
  if (!session.value) return
  pending.value = 'evidence'; pageError.value = ''
  try {
    session.value = await authorizeCareerPlanningEvidence(
      session.value.id,
      toEvidenceAuthorizationSelections(evidenceScopeSelections.value),
    )
    evidenceOpen.value = false
    notice.value = selectedEvidenceCount.value ? `已授权 ${selectedEvidenceCount.value} 项结构化资料。` : '本次规划不使用资料库记录。'
  } catch (reason) { pageError.value = errorMessage(reason, '资料授权保存失败') }
  finally { pending.value = '' }
}

function stopTaskPolling(): void {
  window.clearTimeout(taskPollTimer)
}

function clearForegroundTask(): void {
  stopTaskPolling()
  if (session.value) window.localStorage.removeItem(taskStorageKey(session.value.id))
  activeAiTask.value = null
  taskCheckpoint.value = ''
}

async function acceptTaskSnapshot(task: TaskView): Promise<void> {
  activeAiTask.value = task
  taskCheckpoint.value = task.checkpointCode ?? taskCheckpoint.value
  if (session.value && isOpenTask(task.status)) {
    window.localStorage.setItem(taskStorageKey(session.value.id), task.id)
    stopTaskPolling()
    taskPollTimer = window.setTimeout(() => void pollForegroundTask(task.id), 900)
    return
  }
  if (session.value) window.localStorage.removeItem(taskStorageKey(session.value.id))
  stopTaskPolling()
  if (task.status === 'SUCCEEDED' && session.value) {
    const refreshed = await fetchCareerPlanningSession(session.value.id)
    updateWorkbenchSession(refreshed)
    assistantStream.value = ''
  } else if (task.status === 'FAILED') {
    pageError.value = task.failureReason?.trim() || 'AI 任务失败，正式数据未被修改，可以重新生成。'
  }
  activeAiTask.value = null
}

async function pollForegroundTask(taskId: string): Promise<void> {
  if (activeAiTask.value?.id !== taskId) return
  try { await acceptTaskSnapshot(await fetchTask(taskId)) }
  catch (reason) {
    taskPollTimer = window.setTimeout(() => void pollForegroundTask(taskId), 1800)
    if (!streamConnected.value) pageError.value = errorMessage(reason, 'AI 任务状态恢复失败')
  }
}

async function restoreForegroundTask(sessionId: string): Promise<void> {
  const taskId = window.localStorage.getItem(taskStorageKey(sessionId))
  if (!taskId) return
  try { await acceptTaskSnapshot(await fetchTask(taskId)) }
  catch { window.localStorage.removeItem(taskStorageKey(sessionId)) }
}

async function cancelForegroundTask(): Promise<void> {
  const task = activeAiTask.value
  if (!task || !isOpenTask(task.status)) return
  pending.value = 'cancel-ai-task'; pageError.value = ''
  try {
    await cancelTask(task.id)
    clearForegroundTask()
    assistantStream.value = ''
    notice.value = '已取消本次 AI 任务，正式画像和推荐结果没有被修改。'
  } catch (reason) { pageError.value = errorMessage(reason, 'AI 任务取消失败') }
  finally { pending.value = '' }
}

async function startInterview(): Promise<void> {
  if (!session.value || activeAiTask.value) return
  pending.value = 'interview'; pageError.value = ''; notice.value = ''; assistantStream.value = ''
  try {
    const task = await startCareerPlanningInterviewTask(session.value.id)
    session.value = {
      ...session.value,
      messages: [...session.value.messages, {
        id: `optimistic-${task.id}`, sequence: Number.MAX_SAFE_INTEGER, role: 'USER', type: 'INTERVIEW_REQUEST',
        body: '请根据当前职业画像继续补充访谈。', payload: { taskId: task.id }, createdAt: new Date().toISOString(),
      }],
    }
    await acceptTaskSnapshot(task)
    void nextTick(() => interviewScroll.value?.scrollTo({ top: interviewScroll.value.scrollHeight, behavior: 'smooth' }))
  } catch (reason) { pageError.value = errorMessage(reason, 'AI 补充访谈暂不可用') }
  finally { pending.value = '' }
}

function previousInterviewQuestion(): void {
  interviewQuestionIndex.value = Math.max(0, interviewQuestionIndex.value - 1)
  void nextTick(() => interviewScroll.value?.scrollTo({ top: interviewScroll.value.scrollHeight, behavior: 'smooth' }))
}

function interviewAnswerPayload(round: InterviewRound, throughIndex: number) {
  const lastPersistedIndex = Math.max(-1, round.answers.length - 1)
  return round.questions.slice(0, Math.max(throughIndex, lastPersistedIndex) + 1).map(question => ({
    questionId: question.id,
    question: question.text,
    answer: interviewAnswers[question.id]?.trim() ?? '',
  }))
}

async function advanceInterview(skip = false): Promise<void> {
  const active = session.value
  const round = openRound.value
  const question = currentInterviewQuestion.value
  if (!active || !round || !question || pending.value) return
  if (skip) interviewAnswers[question.id] = ''
  else if (!interviewAnswers[question.id]?.trim()) {
    pageError.value = '请输入真实回答，或者选择“跳过这一题”。'
    return
  }
  const isLast = interviewQuestionIndex.value >= round.questions.length - 1
  const nextIndex = Math.min(interviewQuestionIndex.value + 1, round.questions.length - 1)
  const answers = interviewAnswerPayload(round, interviewQuestionIndex.value)
  pending.value = isLast ? 'answers' : 'interview-draft'
  interviewDraftState.value = 'saving'
  pageError.value = ''
  try {
    const updated = isLast
      ? await answerCareerPlanningInterview(active.id, round.id, answers, active.profile.version)
      : await saveCareerPlanningInterviewDraft(active.id, round.id, answers, active.profile.version)
    session.value = updated
    hydrateForm(updated, isLast ? undefined : nextIndex)
    interviewDraftState.value = 'saved'
    void nextTick(() => interviewScroll.value?.scrollTo({ top: interviewScroll.value.scrollHeight, behavior: 'smooth' }))
  } catch (reason) {
    interviewDraftState.value = 'error'
    pageError.value = errorMessage(reason, '访谈回答保存失败')
  } finally { pending.value = '' }
}

async function goToConfirmation(): Promise<void> {
  if (!session.value) return
  if (session.value.profile.items.length === 0 && activePermissions.value.length === 0) {
    pageError.value = '请至少补充一项技能、经历，或授权一项已确认资料。'
    return
  }
  pending.value = 'profile-review'; pageError.value = ''; notice.value = ''
  try {
    session.value = await reviewCareerPlanningProfile(session.value.id, session.value.profile.version)
    hydrateForm(session.value)
    notice.value = '已进入画像核对阶段，确认前不会用于职业推荐。'
  } catch (reason) { pageError.value = errorMessage(reason, '无法进入画像核对阶段') }
  finally { pending.value = '' }
}

function toggleConfirmed(id: string): void {
  confirmedItemIds.value = confirmedItemIds.value.includes(id)
    ? confirmedItemIds.value.filter(value => value !== id)
    : [...confirmedItemIds.value, id]
}

function sourceRefLabel(source: ProfileItem['sourceRefs'][number]): string {
  if (typeof source === 'string') return careerSourceRefLabels([source])[0] ?? '资料来源'
  const type = typeof source.type === 'string' ? source.type : ''
  const version = typeof source.version === 'number' || typeof source.version === 'string'
    ? String(source.version)
    : typeof source.sourceVersion === 'number' || typeof source.sourceVersion === 'string'
      ? String(source.sourceVersion)
      : ''
  const labels: Record<string, string> = { INTERVIEW: 'AI 访谈回答', CAREER_RECORD: '求职资料库', PROFILE: '画像表单' }
  return `${labels[type] ?? '资料来源'}${version ? ` · 第 ${version} 版` : ''}`
}

const profileItemTitles = computed(() => new Map((session.value?.profile.items ?? [])
  .map(item => [`PROFILE_ITEM:${item.id}`, item.title] as [string, string])))

function itemSourceLabels(item: ProfileItem): string[] {
  return [...new Set(item.sourceRefs.map(sourceRefLabel))]
}

function recommendationSourceLabels(item: CareerRecommendation): string[] {
  return careerSourceRefLabels(item.sourceRefs, profileItemTitles.value)
}

function reviewDetailKey(item: ProfileItem): 'answer' | 'description' | null {
  if (item.section === 'CLARIFICATION' || typeof item.payload.answer === 'string') return 'answer'
  if (['EXPERIENCE', 'PROJECT', 'EDUCATION', 'ORGANIZATION'].includes(item.section)
    || typeof item.payload.description === 'string') return 'description'
  return null
}

function reviewPayloadText(item: ProfileItem, key: string | null): string {
  if (!key) return ''
  const value = item.payload[key]
  return typeof value === 'string' ? value : ''
}

function updateReviewPayload(item: ProfileItem, key: string, value: string | number): void {
  item.payload = { ...item.payload, [key]: String(value) }
}

function updateReviewPayloadFromEvent(item: ProfileItem, key: string | null, event: Event): void {
  if (!key) return
  const target = event.target
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
    updateReviewPayload(item, key, target.value)
  }
}

function reviewItemSummary(item: ProfileItem): string {
  const key = reviewDetailKey(item)
  if (!key) {
    if (item.section === 'SKILLS') {
      return reviewPayloadText(item, 'proficiency') === 'LEARNING' ? '学习中' : '已实践'
    }
    return ''
  }
  const value = reviewPayloadText(item, key)
  return value === item.title ? '' : value
}

function removeReviewItem(item: ProfileItem): void {
  if (item.locked) { pageError.value = '锁定条目不能在核对阶段删除。'; return }
  reviewDraftItems.value = reviewDraftItems.value.filter(candidate => candidate.id !== item.id)
  confirmedItemIds.value = confirmedItemIds.value.filter(id => id !== item.id)
  if (reviewEditingId.value === item.id) reviewEditingId.value = ''
}

async function saveReviewChanges(): Promise<void> {
  if (!session.value || !reviewDirty.value) return
  if (reviewDraftItems.value.some(item => !item.title.trim())) {
    pageError.value = '画像条目名称不能为空。'
    return
  }
  if (reviewDraftItems.value.some(item => item.section === 'CLARIFICATION'
    && !reviewPayloadText(item, 'answer').trim())) {
    pageError.value = '访谈回答不能为空；不希望采用的条目可以取消勾选或删除。'
    return
  }
  if (reviewDraftItems.value.some(item => item.section === 'SKILLS'
    && !['PRACTICED', 'LEARNING'].includes(reviewPayloadText(item, 'proficiency') || 'PRACTICED'))) {
    pageError.value = '技能掌握状态无效，请重新选择。'
    return
  }
  pending.value = 'save-review'; pageError.value = ''; notice.value = ''
  try {
    const updated = await updateCareerPlanningProfile(session.value.id, {
      basics: session.value.profile.basics,
      preferences: session.value.profile.preferences,
      constraints: session.value.profile.constraints,
      objectiveTaxonomyId: session.value.profile.objectiveTaxonomyId,
      items: reviewDraftItems.value.map(item => ({ ...itemWrite(item), title: item.title.trim() })),
      expectedVersion: session.value.profile.version,
    })
    session.value = await reviewCareerPlanningProfile(updated.id, updated.profile.version)
    hydrateForm(session.value)
    notice.value = '画像修改已保存，请继续逐项确认。'
  } catch (reason) { pageError.value = errorMessage(reason, '画像修改保存失败') }
  finally { pending.value = '' }
}

async function confirmProfile(): Promise<void> {
  if (!session.value) return
  pending.value = 'confirm-profile'; pageError.value = ''
  try {
    session.value = await confirmCareerPlanningProfile(session.value.id, confirmedItemIds.value, session.value.profile.version)
    forceProfileEdit.value = false
    notice.value = '职业画像已冻结为推荐输入。'
  } catch (reason) { pageError.value = errorMessage(reason, '画像确认失败') }
  finally { pending.value = '' }
}

async function generateRecommendations(): Promise<void> {
  if (!session.value || activeAiTask.value) return
  pending.value = 'recommendations'; pageError.value = ''; notice.value = ''
  try {
    const task = await startCareerRecommendationTask(session.value.id)
    await acceptTaskSnapshot(task)
    compareIds.value = []
  } catch (reason) { pageError.value = errorMessage(reason, 'AI 职业方向生成失败') }
  finally { pending.value = '' }
}

async function toggleFavorite(item: CareerRecommendation): Promise<void> {
  if (!session.value) return
  pending.value = `favorite:${item.id}`
  try {
    const set = await setCareerRecommendationFavorite(session.value.id, item.id, !item.favorite)
    session.value = { ...session.value, recommendationSet: set }
  } catch (reason) { pageError.value = errorMessage(reason, '收藏状态保存失败') }
  finally { pending.value = '' }
}

function toggleComparison(item: CareerRecommendation): void {
  const result = toggleCareerComparison(compareIds.value, item.id)
  if (result.limitReached) {
    pageError.value = '职业方向一次最多比较 3 项，请先移除一项。'
    compareOpen.value = true
    return
  }
  compareIds.value = result.ids
  pageError.value = ''
}

function openComparison(): void {
  if (comparedRecommendations.value.length < 2) {
    pageError.value = '请至少选择 2 个职业方向后再进行比较。'
    return
  }
  compareOpen.value = true
  pageError.value = ''
}

async function prepareGoal(item: CareerRecommendation): Promise<void> {
  if (!session.value || !recommendationSet.value) return
  pending.value = `goal:${item.id}`; pageError.value = ''
  try {
    confirmation.value = await prepareCareerGoalConfirmation(session.value.id, recommendationSet.value.id, item.id)
    confirmationRecommendation.value = item
    goalAcknowledged.value = false
  } catch (reason) { pageError.value = errorMessage(reason, '目标确认准备失败') }
  finally { pending.value = '' }
}

async function commitGoal(): Promise<void> {
  if (!session.value || !recommendationSet.value || !confirmation.value || !confirmationRecommendation.value) return
  pending.value = 'commit-goal'; pageError.value = ''
  try {
    session.value = await confirmCareerGoal(session.value.id, recommendationSet.value.id,
      confirmationRecommendation.value.id, confirmation.value.token, session.value.version)
    confirmation.value = null; confirmationRecommendation.value = null
    goalAcknowledged.value = false
  } catch (reason) { pageError.value = errorMessage(reason, '目标职业确认失败') }
  finally { pending.value = '' }
}

function resetNotice(): void { pageError.value = ''; notice.value = '' }
function editConfirmedProfile(): void {
  forceProfileEdit.value = true
  profileTab.value = 'SKILLS'
  resetNotice()
}

function updateWorkbenchSession(value: CareerPlanningSession): boolean {
  if (!shouldAcceptCareerPlanningSnapshot(session.value, value)) return false
  session.value = value
  overview.value = overview.value ? { ...overview.value, session: value } : overview.value
  return true
}

function eventSequenceKey(sessionId: string): string {
  return `jobproof:career-planning:${sessionId}:last-event`
}

function closeEvents(): void {
  eventSource?.close()
  eventSource = null
  window.clearTimeout(eventRefreshTimer)
}

function eventPayload(event: MessageEvent): Record<string, unknown> {
  try {
    const value = JSON.parse(String(event.data))
    return value && typeof value === 'object' ? value as Record<string, unknown> : {}
  } catch { return {} }
}

function updateTaskFromStream(type: string, payload: Record<string, unknown>): void {
  const task = activeAiTask.value
  if (!task || payload.taskId !== task.id) return
  if (type === 'assistant.delta' && typeof payload.delta === 'string') {
    assistantStream.value += payload.delta
    void nextTick(() => interviewScroll.value?.scrollTo({ top: interviewScroll.value.scrollHeight, behavior: 'smooth' }))
    return
  }
  if (type === 'task.started') activeAiTask.value = { ...task, status: 'RUNNING' }
  if (type === 'task.progress') {
    activeAiTask.value = { ...task, status: 'RUNNING', progressPercent: typeof payload.percent === 'number' ? payload.percent : task.progressPercent }
    if (typeof payload.stage === 'string') taskCheckpoint.value = payload.stage
  }
  if (['task.completed', 'task.failed', 'task.cancelled'].includes(type)) {
    void fetchTask(task.id).then(acceptTaskSnapshot).catch(() => undefined)
  }
}

async function refreshFromEvent(eventType: string): Promise<void> {
  const active = session.value
  if (!active) return
  try {
    const refreshed = await fetchCareerPlanningSession(active.id)
    if (session.value?.id !== refreshed.id) return
    const accepted = updateWorkbenchSession(refreshed)
    if (accepted && (eventType.startsWith('profile.') || eventType.startsWith('evidence.') || eventType.startsWith('interview.'))
      && !forceProfileEdit.value && !pending.value) hydrateForm(refreshed)
  } catch (reason) {
    pageError.value = errorMessage(reason, '职业规划实时状态恢复失败')
  }
}

function connectEvents(sessionId: string): void {
  closeEvents()
  const stored = Number.parseInt(sessionStorage.getItem(eventSequenceKey(sessionId)) ?? '0', 10)
  const afterSequence = Number.isFinite(stored) && stored > 0 ? stored : 0
  eventSource = new EventSource(`/api/v1/career-planning/sessions/${encodeURIComponent(sessionId)}/events?afterSequence=${afterSequence}`)
  eventSource.onopen = () => { streamConnected.value = true }
  eventSource.onerror = () => { streamConnected.value = false }
  const types = [
    'session.created', 'profile.updated', 'profile.review-ready', 'profile.confirmed',
    'evidence.updated', 'interview.started', 'interview.completed', 'recommendations.updated',
    'task.queued', 'task.started', 'task.progress', 'task.completed', 'task.failed', 'task.cancelled',
    'assistant.started', 'assistant.delta', 'assistant.completed', 'assistant.failed', 'assistant.cancelled',
    'question.batch', 'recommendation.progress',
    'goal.confirmed', 'goal.changed', 'canvas.generated', 'canvas.updated', 'canvas.restored',
    'proposal.created', 'proposal.decided', 'plan.created', 'plan.updated',
    'plan.task-updated', 'plan.evidence-added', 'plan.review-updated',
    'validation.evaluated', 'validation.confirmed',
  ]
  for (const type of types) {
    eventSource.addEventListener(type, (raw) => {
      const event = raw as MessageEvent
      if (event.lastEventId) sessionStorage.setItem(eventSequenceKey(sessionId), event.lastEventId)
      updateTaskFromStream(type, eventPayload(event))
      if (type === 'assistant.delta' || type === 'task.progress' || type === 'task.started'
        || type === 'assistant.started') return
      window.clearTimeout(eventRefreshTimer)
      eventRefreshTimer = window.setTimeout(() => void refreshFromEvent(type), 140)
    })
  }
}

function openMobileProfile(): void {
  forceProfileEdit.value = true
  profileTab.value = 'BASICS'
  resetNotice()
}

function openMobileDirection(): void {
  if (!recommendationSet.value) return
  forceProfileEdit.value = false
  resetNotice()
}

function showCanvasError(message: string): void {
  pageError.value = message
  notice.value = ''
}

function showCanvasNotice(message: string): void {
  notice.value = message
  pageError.value = ''
}

watch(() => route.params.sessionId, (value, old) => { if (value && value !== old && value !== session.value?.id) void load() })
watch(() => session.value?.id, (value, old) => {
  if (value === old) return
  stopTaskPolling()
  activeAiTask.value = null
  taskCheckpoint.value = ''
  assistantStream.value = ''
  if (typeof old === 'string') window.localStorage.removeItem(taskStorageKey(old))
  if (value) {
    connectEvents(value)
    void restoreForegroundTask(value)
  } else closeEvents()
})
watch([form, selectedSkills], () => scheduleProfileAutosave(), { deep: true })
onMounted(load)
onBeforeUnmount(() => {
  closeEvents()
  stopTaskPolling()
  window.clearTimeout(profileAutosaveTimer)
})
</script>

<template>
  <main class="cp-page">
    <PageState :loading="pageLoad.loading.value" :error="pageLoad.error.value" :loaded="pageReady" error-title="职业规划没有读取成功" @retry="load">
      <template #skeleton><div class="cp-loading"><LoaderCircle class="cp-spin" :size="24" /><span>正在恢复职业规划</span></div></template>
        <section v-if="!session" class="cp-welcome">
          <div class="cp-welcome__intro">
            <span class="cp-welcome__mark"><Sparkles :size="28" aria-hidden="true" /></span>
            <h1>先确定职业方向，再生成你的能力树</h1>
            <p>结合你确认的经历、技能与限制，给出 3–6 个可比较方向；最终选择始终由你确认。</p>
          </div>
          <div class="cp-entry-grid">
            <article class="cp-entry cp-entry--primary">
              <header class="cp-entry__head">
                <span class="cp-entry__icon"><BrainCircuit :size="25" aria-hidden="true" /></span>
                <div><small>AI 职业方向判定</small><h2>让 AI 帮我判断职业方向</h2><p>适合暂时还没有明确目标，希望先比较再决定的用户</p></div>
              </header>
              <ol class="cp-discovery-steps" aria-label="AI 职业方向判定流程">
                <li><span><UserRound :size="17" aria-hidden="true" /></span><div><strong>整理真实经历</strong><small>归纳已确认的技能与限制</small></div></li>
                <li><span><MessageCircle :size="17" aria-hidden="true" /></span><div><strong>补充关键问题</strong><small>只追问影响方向判断的信息</small></div></li>
                <li><span><Compass :size="17" aria-hidden="true" /></span><div><strong>比较职业方向</strong><small>生成 3–6 个可核对选项</small></div></li>
              </ol>
              <footer class="cp-entry__action">
                <button class="cp-primary" type="button" :disabled="!aiConsent || pending === 'start'" :aria-describedby="aiConsent ? undefined : 'cp-consent-hint-discovery'" @click="start('AI_DISCOVERY')"><LoaderCircle v-if="pending === 'start'" class="cp-spin" :size="17" /><Compass v-else :size="17" />开始职业方向判定 <ArrowRight :size="16" aria-hidden="true" /></button>
                <p v-if="!aiConsent" id="cp-consent-hint-discovery" class="cp-consent-hint"><LockKeyhole :size="14" aria-hidden="true" />需要先勾选下方“允许本次规划调用 AI”</p>
              </footer>
            </article>
            <article class="cp-entry cp-entry--known">
              <header class="cp-entry__head">
                <span class="cp-entry__icon"><Target :size="24" aria-hidden="true" /></span>
                <div><small>已有明确目标</small><h2>我已经确定目标职业</h2><p>直接进入目标确认与能力画布</p></div>
              </header>
              <p class="cp-entry__copy">从完整标准岗位库选择职业，AI 仍会检查已确认信息是否足够，不会替你改变目标。</p>
              <div class="cp-known-checks"><p><Check :size="15" aria-hidden="true" />使用标准岗位分类</p><p><Check :size="15" aria-hidden="true" />创建独立能力画布</p></div>
              <div class="cp-known-target-action">
                <Transition name="cp-target" mode="out-in">
                  <button v-if="!knownTargetSetup" key="known-start" class="cp-secondary cp-known-target-start" type="button" @click="start('KNOWN_TARGET')"><Target :size="17" aria-hidden="true" />填写目标职业 <ArrowRight :size="16" aria-hidden="true" /></button>
                  <div v-else key="known-picker" class="cp-target-setup">
                    <JobTaxonomyPicker :selected-name="selectedTargetName" :selected-node-id="selectedTargetId" @select="selectTarget" />
                    <button class="cp-primary" type="button" :disabled="!aiConsent || !selectedTargetId || pending === 'start'" :aria-describedby="aiConsent ? undefined : 'cp-consent-hint-target'" @click="start('KNOWN_TARGET')">确认并开始 <ArrowRight :size="16" aria-hidden="true" /></button>
                    <p v-if="!aiConsent" id="cp-consent-hint-target" class="cp-consent-hint"><LockKeyhole :size="14" aria-hidden="true" />能力画布由 AI 生成，需要先勾选下方“允许本次规划调用 AI”</p>
                  </div>
                </Transition>
              </div>
            </article>
          </div>
          <label class="cp-consent"><input v-model="aiConsent" type="checkbox"><span><strong>允许本次规划调用 AI</strong><small>开始前需要你主动勾选。只发送你填写并确认的结构化资料，不发送原始文件或图片。</small></span><LockKeyhole :size="18" aria-hidden="true" /></label>
          <section class="cp-outcomes" aria-label="职业规划产出">
            <header><strong>你将得到</strong><small>每一步都可追溯、可确认</small></header>
            <div class="cp-outcome-list">
              <div class="is-blue"><span><UserRound :size="18" aria-hidden="true" /></span><p><strong>画像</strong><small>事实可追溯</small></p></div>
              <div class="is-cyan"><span><Compass :size="18" aria-hidden="true" /></span><p><strong>方向</strong><small>多个选项</small></p></div>
              <div class="is-purple"><span><Layers3 :size="18" aria-hidden="true" /></span><p><strong>画布</strong><small>持续演进</small></p></div>
              <div class="is-green"><span><CalendarCheck2 :size="18" aria-hidden="true" /></span><p><strong>计划</strong><small>后续行动</small></p></div>
            </div>
          </section>
        </section>

        <section v-else-if="!activeInterviewTask && (phase === 'PROFILE' || phase === 'EVIDENCE' || forceProfileEdit)" class="cp-workspace">
          <div class="cp-main-card">
            <header class="cp-ai-guidance"><span><Bot :size="22" /></span><p><strong>我会先了解你的基本情况</strong><small>所有 AI 归纳都需要你确认，带有推断标记的内容不会直接用于推荐。</small></p><em>{{ quotaText }}</em></header>
            <nav class="cp-profile-tabs" role="tablist" aria-label="画像填写步骤" :style="{ '--profile-tab': profileTab === 'BASICS' ? 0 : profileTab === 'SKILLS' ? 1 : 2 }">
              <button :class="{ active: profileTab === 'BASICS' }" type="button" role="tab" :aria-selected="profileTab === 'BASICS'" @click="profileTab = 'BASICS'">1 基础信息</button>
              <button :class="{ active: profileTab === 'SKILLS' }" type="button" role="tab" :aria-selected="profileTab === 'SKILLS'" @click="profileTab = 'SKILLS'">2 技能与经历</button>
              <button :class="{ active: profileTab === 'PREFERENCES' }" type="button" role="tab" :aria-selected="profileTab === 'PREFERENCES'" @click="profileTab = 'PREFERENCES'">3 个人倾向与限制</button>
              <i aria-hidden="true" />
            </nav>
            <form class="cp-profile-form" @submit.prevent="saveProfile()">
              <Transition name="cp-form-view" mode="out-in"><section v-if="profileTab === 'BASICS'" key="basics" class="cp-form-section">
                <header><div><h1>基础信息</h1><p>用于判断规划节奏，不用于年龄歧视或敏感推断。</p></div><UserRound :size="23" /></header>
                <div class="cp-form-grid">
                  <label><span>年龄段 <em>选填</em></span><AppSelect v-model="form.ageRange" :options="ageOptions" aria-label="年龄段" /></label>
                  <label><span>当前身份 <b>*</b></span><AppSelect v-model="form.identity" :options="identityOptions" aria-label="当前身份" /></label>
                  <label><span>当前职业</span><input v-model.trim="form.currentRole" class="cp-input" placeholder="例如：机械设计助理"></label>
                  <label><span>最高学历 <b>*</b></span><AppSelect v-model="form.education" :options="educationOptions" aria-label="最高学历" /></label>
                  <label><span>所学专业</span><input v-model.trim="form.major" class="cp-input" placeholder="例如：软件工程"></label>
                  <label><span>毕业时间</span><input v-model.trim="form.graduationYear" class="cp-input" inputmode="numeric" maxlength="4" placeholder="例如：2026"></label>
                  <label><span>工作年限 <b>*</b></span><AppSelect v-model="form.experienceYears" :options="experienceOptions" aria-label="工作年限" /></label>
                  <label><span>每周可投入学习时间 <b>*</b></span><AppSelect v-model="form.weeklyLearningHours" :options="weeklyOptions" aria-label="每周学习时间" /></label>
                </div>
              </section>
              <section v-else-if="profileTab === 'SKILLS'" key="skills" class="cp-form-section">
                <header><div><h1>技能与经历</h1><p>填写真实掌握或正在学习的内容，AI 不会把“正在学习”当作已掌握。</p></div><Layers3 :size="23" /></header>
                <div class="cp-wide-field"><span>专业技能</span><CareerSkillPicker v-model="selectedSkills" /></div>
                <label class="cp-wide-field"><span>代表性经历或项目</span><textarea v-model="form.experienceText" class="cp-textarea" rows="5" placeholder="只写真实发生的经历，建议包含你的职责、行动和结果。"></textarea></label>
              </section>
              <section v-else key="preferences" class="cp-form-section">
                <header><div><h1>个人倾向与工作限制</h1><p>这些信息用于排序和规划，不会变成歧视性筛选条件。</p></div><Compass :size="23" /></header>
                <div class="cp-form-grid">
                  <label><span>期望地点</span><input v-model.trim="form.preferredLocation" class="cp-input" placeholder="例如：上海、杭州"></label>
                  <label><span>办公方式</span><AppSelect v-model="form.workMode" :options="workModeOptions" aria-label="办公方式" /></label>
                  <label class="cp-grid-full"><span>方向倾向</span><input v-model.trim="form.directionPreference" class="cp-input" placeholder="例如：希望从机械方向转向软件开发，同时保留行业经验"></label>
                  <label class="cp-grid-full"><span>阶段目标</span><textarea v-model="form.learningGoal" class="cp-textarea" rows="4" placeholder="例如：未来 3 个月完成方向验证，并形成可展示的项目证据"></textarea></label>
                </div>
              </section></Transition>
              <footer class="cp-form-actions">
                <span class="cp-autosave-state" :class="`is-${profileSaveState}`" role="status">
                  <LoaderCircle v-if="profileSaveState === 'saving'" class="cp-spin" :size="14" />
                  <Check v-else-if="profileSaveState === 'saved'" :size="14" />
                  <Save v-else :size="14" />
                  {{ profileSaveState === 'waiting' ? '等待自动保存' : profileSaveState === 'saving' ? '正在自动保存' : profileSaveState === 'saved' ? '草稿已保存' : profileSaveState === 'error' ? '保存失败' : '修改后自动保存' }}
                </span>
                <button class="cp-secondary" type="submit" :disabled="pending === 'profile' || profileSaveState === 'saving'"><LoaderCircle v-if="pending === 'profile'" class="cp-spin" :size="16" />保存草稿</button>
                <button v-if="profileTab !== 'PREFERENCES'" class="cp-primary" type="button" @click="profileTab = profileTab === 'BASICS' ? 'SKILLS' : 'PREFERENCES'">下一步 <ArrowRight :size="16" /></button>
                <button v-else class="cp-primary" type="button" @click="openEvidence"><Database :size="16" />选择资料授权</button>
              </footer>
            </form>
            <section v-if="phase === 'EVIDENCE'" class="cp-next-actions">
              <div><CheckCircle2 :size="19" /><p><strong>资料范围已保存</strong><small>可继续 AI 补充访谈，也可直接确认当前画像。</small></p></div>
              <button class="cp-secondary" type="button" @click="openEvidence">调整授权</button>
              <button v-if="session.aiConsent" class="cp-primary" type="button" :disabled="pending === 'interview'" @click="startInterview"><LoaderCircle v-if="pending === 'interview'" class="cp-spin" :size="16" /><Sparkles v-else :size="16" />AI 补充访谈</button>
              <button class="cp-text-button" type="button" @click="goToConfirmation">直接确认画像</button>
            </section>
          </div>
          <CareerProfilePreview :profile="session.profile" :permissions="session.permissions" />
        </section>

        <section v-else-if="phase === 'INTERVIEW' || activeInterviewTask" class="cp-interview-layout">
          <div class="cp-interview">
            <header><span class="cp-ai-avatar"><Bot :size="24" /></span><div><strong>AI 补充访谈</strong><p>只追问当前画像中缺失或存在歧义的内容，可以跳过不想回答的问题。</p></div><em :class="{ disconnected: !streamConnected }">{{ streamConnected ? '实时连接正常' : '正在重新连接' }}</em></header>
            <div ref="interviewScroll" class="cp-conversation-stream" role="log" aria-label="AI 补充访谈消息" aria-live="polite" tabindex="0">
              <article v-for="message in interviewMessages" :key="message.id" class="cp-chat-row" :class="message.role === 'USER' ? 'is-user' : 'is-assistant'">
                <span class="cp-chat-avatar"><UserRound v-if="message.role === 'USER'" :size="17" /><Bot v-else :size="17" /></span>
                <div v-if="messageText(message)" class="cp-chat-bubble">{{ messageText(message) }}</div>
              </article>
              <article v-if="activeInterviewTask" class="cp-chat-row is-assistant is-streaming">
                <span class="cp-chat-avatar"><Bot :size="17" /></span>
                <div class="cp-chat-bubble">
                  <p v-if="assistantStream">{{ assistantStream }}</p>
                  <div v-else class="cp-thinking-dots" aria-label="AI 正在思考"><i></i><i></i><i></i></div>
                  <small>{{ taskStatusText }}<template v-if="taskProgress"> · {{ taskProgress }}%</template></small>
                </div>
              </article>
              <article v-for="question in completedInterviewQuestions" :key="`draft-${question.id}`" class="cp-chat-row is-user cp-interview-answer-row">
                <span class="cp-chat-avatar"><UserRound :size="17" /></span>
                <div class="cp-chat-bubble"><small>{{ question.text }}</small><p>{{ question.answer || '已跳过这一题' }}</p></div>
              </article>
              <section v-if="openRound" class="cp-question-card">
                <header><div><strong>请确认这一项信息</strong><small>每题单独保存，刷新页面后可以继续</small></div><span>{{ interviewQuestionIndex + 1 }} / {{ openRound.questions.length }}</span></header>
                <div v-if="currentInterviewQuestion" class="cp-question-list">
                  <article>
                    <span>{{ interviewQuestionIndex + 1 }}</span><div><h2>{{ currentInterviewQuestion.text }}</h2><small>{{ currentInterviewQuestion.purpose }}</small><textarea v-model="interviewAnswers[currentInterviewQuestion.id]" class="cp-textarea" rows="4" maxlength="2000" placeholder="输入真实情况；不确定时可以跳过"></textarea><em>{{ (interviewAnswers[currentInterviewQuestion.id] || '').length }} / 2000</em></div>
                  </article>
                </div>
              </section>
              <p v-if="!interviewMessages.length && !activeInterviewTask && !openRound" class="cp-conversation-empty">还没有访谈消息。</p>
            </div>
            <footer v-if="openRound" class="cp-interview-actions">
              <span class="cp-interview-save-state" :class="`is-${interviewDraftState}`"><LoaderCircle v-if="interviewDraftState === 'saving'" class="cp-spin" :size="14" />{{ interviewDraftState === 'saving' ? '正在保存' : interviewDraftState === 'saved' ? '本题前进度已保存' : interviewDraftState === 'error' ? '草稿保存失败' : '尚未保存回答' }}</span>
              <button class="cp-text-button" type="button" :disabled="Boolean(pending)" @click="goToConfirmation">结束访谈</button>
              <button class="cp-secondary" type="button" :disabled="Boolean(pending) || interviewQuestionIndex === 0" @click="previousInterviewQuestion"><ArrowLeft :size="16" />上一题</button>
              <button class="cp-secondary" type="button" :disabled="Boolean(pending)" @click="advanceInterview(true)">跳过这一题</button>
              <button class="cp-primary" type="button" :disabled="Boolean(pending)" @click="advanceInterview(false)"><LoaderCircle v-if="pending === 'answers' || pending === 'interview-draft'" class="cp-spin" :size="16" /><Send v-else :size="16" />{{ interviewQuestionIndex === openRound.questions.length - 1 ? '提交并核对画像' : '保存并继续' }}</button>
            </footer>
            <footer v-else-if="activeInterviewTask" class="cp-task-footer"><span><LoaderCircle class="cp-spin" :size="15" />{{ taskStatusText }}</span><button class="cp-secondary" type="button" :disabled="pending === 'cancel-ai-task'" @click="cancelForegroundTask">取消生成</button></footer>
            <footer v-else><button class="cp-secondary" type="button" @click="goToConfirmation">返回画像核对</button><button class="cp-primary" type="button" @click="startInterview"><Sparkles :size="16" />重新生成问题</button></footer>
          </div>
          <CareerProfilePreview :profile="session.profile" :permissions="session.permissions" />
        </section>

        <section v-else-if="phase === 'PROFILE_CONFIRMATION'" class="cp-confirm-layout">
          <div class="cp-confirm-card">
            <header><div><span><ShieldCheck :size="22" /></span><div class="cp-confirm-heading"><h1>确认职业画像</h1><small>逐项编辑、删除或查看来源；只有勾选的事实会进入正式推荐。</small></div></div><em>{{ confirmedItemIds.length }} / {{ reviewDraftItems.length }} 项确认</em></header>
            <section class="cp-confirm-basics">
              <h2>基础信息</h2>
              <dl v-if="confirmationBasicItems.length">
                <div v-for="item in confirmationBasicItems" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div>
              </dl>
              <p v-else>尚未填写基础信息。</p>
            </section>
            <div class="cp-confirm-items">
              <section v-for="group in reviewSections" :key="group.key" class="cp-confirm-group" :class="`is-${group.key.toLowerCase()}`">
                <header><div><h2>{{ group.title }}</h2><small>{{ group.description }}</small></div><span>{{ group.items.length }} 项</span></header>
                <div class="cp-confirm-group__items">
                  <article v-for="item in group.items" :key="item.id" :class="{ inference: item.claimType === 'INFERENCE' }">
                    <input class="cp-confirm-check" type="checkbox" :checked="confirmedItemIds.includes(item.id)" :aria-label="`确认 ${item.title}`" @change="toggleConfirmed(item.id)">
                    <div class="cp-confirm-content">
                      <div v-if="reviewEditingId === item.id" class="cp-confirm-editor">
                        <label><span>条目名称</span><input v-model.trim="item.title" class="cp-confirm-title-input" maxlength="255" autofocus></label>
                        <label v-if="reviewDetailKey(item)"><span>{{ reviewDetailKey(item) === 'answer' ? '访谈回答' : '补充描述' }}</span><textarea class="cp-confirm-detail-input" rows="3" maxlength="2000" :value="reviewPayloadText(item, reviewDetailKey(item))" @input="updateReviewPayloadFromEvent(item, reviewDetailKey(item), $event)"></textarea><small>{{ reviewPayloadText(item, reviewDetailKey(item)).length }} / 2000</small></label>
                        <label v-if="item.section === 'SKILLS'"><span>掌握状态</span><AppSelect :model-value="reviewPayloadText(item, 'proficiency') || 'PRACTICED'" :options="reviewProficiencyOptions" aria-label="技能掌握状态" @update:model-value="updateReviewPayload(item, 'proficiency', $event)" /></label>
                      </div>
                      <template v-else>
                        <strong>{{ item.title }}</strong>
                        <p v-if="reviewItemSummary(item)" class="cp-confirm-summary">{{ reviewItemSummary(item) }}</p>
                      </template>
                      <small>{{ careerProfileSectionLabel(item.section) }} · {{ item.claimType === 'INFERENCE' ? 'AI 推断，需谨慎确认' : item.claimType === 'SELF_REPORTED' ? '用户自述事实' : '已确认事实' }}</small>
                      <div v-if="item.sourceRefs.length" class="cp-confirm-sources"><Link2 :size="12" /><span v-for="label in itemSourceLabels(item)" :key="label">{{ label }}</span></div>
                      <div v-else class="cp-confirm-sources is-empty"><Link2 :size="12" /><span>当前表单直接填写</span></div>
                    </div>
                    <div class="cp-confirm-actions">
                      <button type="button" :title="reviewEditingId === item.id ? '完成编辑' : '编辑条目'" @click="reviewEditingId = reviewEditingId === item.id ? '' : item.id"><Check v-if="reviewEditingId === item.id" :size="15" /><Pencil v-else :size="15" /></button>
                      <button type="button" title="删除条目" :disabled="item.locked" @click="removeReviewItem(item)"><Trash2 :size="15" /></button>
                    </div>
                    <em>{{ group.key === 'INCLUDED' ? '将用于推荐' : '不进入推荐' }}</em>
                  </article>
                  <p v-if="!group.items.length" class="cp-confirm-group__empty">{{ group.key === 'INCLUDED' ? '尚未选择用于推荐的事实。' : '没有待确认内容。' }}</p>
                </div>
              </section>
              <p v-if="!reviewDraftItems.length" class="cp-confirm-empty">没有可确认的画像条目，请返回编辑或授权一项求职资料。</p>
            </div>
            <footer><button class="cp-secondary" type="button" @click="editConfirmedProfile"><ArrowLeft :size="16" />返回完整编辑</button><button v-if="reviewDirty" class="cp-secondary cp-save-review" type="button" :disabled="pending === 'save-review'" @click="saveReviewChanges"><LoaderCircle v-if="pending === 'save-review'" class="cp-spin" :size="16" /><Save v-else :size="16" />保存本页修改</button><button class="cp-primary" type="button" :disabled="pending === 'confirm-profile' || reviewDirty || !confirmedItemIds.length" @click="confirmProfile"><LoaderCircle v-if="pending === 'confirm-profile'" class="cp-spin" :size="16" /><Check v-else :size="16" />确认画像并进入推荐</button></footer>
          </div>
          <CareerProfilePreview :profile="session.profile" :permissions="session.permissions" />
        </section>

        <section v-else-if="phase === 'RECOMMENDATIONS' || phase === 'RECOMMENDATION_INSUFFICIENT'" class="cp-recommend-layout">
          <aside class="cp-recommend-assistant"><span><Bot :size="24" /></span><p v-if="!recommendationSet">职业画像已经冻结。现在可以让 AI 基于已确认事实生成多个可比较方向。</p><p v-else-if="recommendationSet.status === 'INSUFFICIENT'">目前的确认事实不足以形成可靠推荐。补充资料后再生成，不会硬凑结果。</p><p v-else>我整理了 {{ recommendationSet.recommendations.length }} 个职业方向。推荐只是建议，目标职业由你确认。</p><div><strong>推荐原则</strong><small>仅用已确认事实</small><small>不包含薪酬信息</small><small>不输出成功概率</small></div></aside>
          <div class="cp-recommendations">
            <header><div><h1>职业方向推荐</h1><p>比较现有优势、能力缺口和证据来源后再做决定。</p></div><div v-if="recommendationSet?.status === 'READY'" class="cp-recommend-summary"><span>已收藏 {{ favoriteCount }}</span><button type="button" :disabled="comparedRecommendations.length < 2" @click="openComparison"><GitCompareArrows :size="15" />对比 {{ comparedRecommendations.length }}/3</button></div></header>
            <section v-if="!recommendationSet && activeRecommendationTask" class="cp-generate-card cp-generate-card--running"><span><LoaderCircle class="cp-spin" :size="30" /></span><h2>正在生成可比较的职业方向</h2><p>{{ taskStatusText }}。你可以离开本页，任务完成后会从服务端恢复。</p><div class="cp-task-progress"><i :style="{ width: `${taskProgress}%` }"></i></div><button class="cp-secondary" type="button" :disabled="pending === 'cancel-ai-task'" @click="cancelForegroundTask">取消生成</button></section>
            <section v-else-if="!recommendationSet" class="cp-generate-card"><span><Sparkles :size="32" /></span><h2>生成真实职业方向</h2><p>由 AI 基于已确认的画像生成，成功后消耗 1 次额度。AI 暂不可用时会如实提示，不会给出模拟结果。</p><button class="cp-primary" type="button" :disabled="pending === 'recommendations'" @click="generateRecommendations"><LoaderCircle v-if="pending === 'recommendations'" class="cp-spin" :size="17" /><Sparkles v-else :size="17" />生成职业方向 · 1 次</button></section>
            <section v-else-if="recommendationSet.status === 'INSUFFICIENT'" class="cp-insufficient"><CircleAlert :size="30" /><h2>资料不足，暂不生成推荐</h2><p v-for="reason in recommendationSet.insufficientReasons" :key="reason">{{ reason }}</p><button class="cp-secondary" type="button" @click="editConfirmedProfile">返回补充画像</button></section>
            <div v-else class="cp-recommendation-list">
              <article v-for="(item, index) in recommendationSet.recommendations" :key="item.id" class="cp-recommendation">
                <span class="cp-recommendation__rank">{{ index + 1 }}</span>
                <div class="cp-recommendation__body"><header><h2>{{ item.title }}</h2><em :class="`tier-${item.tier}`">{{ item.tier === 'READY_NOW' ? '较匹配' : item.tier === 'AFTER_SMALL_GAP' ? '可拓展' : '探索方向' }}</em></header><p>{{ item.fitSummary }}</p><div class="cp-recommendation__facts"><span><strong>推荐依据</strong>{{ item.rationale[0] }}</span><span><strong>能力缺口</strong>{{ item.gaps[0] || '暂无明确缺口' }}</span><span><strong>证据引用</strong>{{ item.sourceRefs.length }} 项</span></div></div>
                <div class="cp-recommendation__actions"><button type="button" @click="detailRecommendation = item">详情</button><button type="button" :class="{ active: item.favorite }" :disabled="pending === `favorite:${item.id}`" @click="toggleFavorite(item)"><Heart :size="15" :fill="item.favorite ? 'currentColor' : 'none'" />{{ item.favorite ? '已收藏' : '收藏' }}</button><button type="button" :class="{ active: compareIds.includes(item.id) }" @click="toggleComparison(item)"><GitCompareArrows :size="15" />{{ compareIds.includes(item.id) ? '已加入' : '对比' }}</button><button class="goal" type="button" :disabled="pending === `goal:${item.id}`" @click="prepareGoal(item)">设为目标</button></div>
              </article>
            </div>
          </div>
          <aside class="cp-recommend-meta"><h2>推荐说明</h2><dl><div><dt>基于画像</dt><dd>职业画像 v{{ session.profile.snapshotVersion }}</dd></div><div><dt>使用资料</dt><dd>{{ activePermissions.length }} 项</dd></div><div><dt>生成方式</dt><dd>{{ recommendationSet ? 'AI 辅助生成' : '等待生成' }}</dd></div><div><dt>AI 额度</dt><dd>{{ quotaText }}</dd></div></dl><section><Database :size="20" /><p>补充项目经历、作品和证书，可帮助模型减少跨领域猜测。</p><button type="button" @click="openEvidence">调整资料授权</button></section><footer><LockKeyhole :size="15" />推荐仅供决策参考，系统不会替你确认目标。</footer></aside>
        </section>

        <CareerPlanningWorkbench
          v-else-if="isCareerExecutionPhase(phase) && session.canvas && session.activeGoal"
          :session="session"
          @session-updated="updateWorkbenchSession"
          @error="showCanvasError"
          @notice="showCanvasNotice"
        />
    </PageState>
  </main>

    <nav v-if="pageReady && session && !isCareerExecutionPhase(phase)" class="cp-mobile-nav" aria-label="职业规划移动导航">
      <button type="button" :class="{ active: !phase.includes('RECOMMEND') || forceProfileEdit }" @click="openMobileProfile"><UserRound :size="20" /><span>画像</span></button>
      <button type="button" :class="{ active: phase.includes('RECOMMEND') && !forceProfileEdit }" :disabled="!recommendationSet" @click="openMobileDirection"><Compass :size="20" /><span>方向</span></button>
    </nav>

    <AppModal :open="evidenceOpen" title="选择本次规划可使用的资料" :width="760" mobile-sheet @close="evidenceOpen = false">
      <div class="cp-evidence-dialog">
        <p><ShieldCheck :size="18" />每项资料都需要明确选择用途。只发送已确认的结构化摘要，原始 DOCX、PDF、图片和预览内容不会发送给模型。</p>
        <div v-if="evidenceLoading" class="cp-dialog-loading"><LoaderCircle class="cp-spin" :size="19" />正在读取可授权资料</div>
        <div v-else-if="!evidenceOptions.length" class="cp-dialog-empty"><Database :size="30" /><strong>暂无可授权的确认资料</strong><small>你可以继续使用当前职业画像，或先到求职资料库补充经历成果。</small></div>
        <div v-else class="cp-evidence-list">
          <article v-for="option in evidenceOptions" :key="option.sourceId" :class="{ selected: (evidenceScopeSelections[option.sourceId]?.length ?? 0) > 0 }">
            <header>
              <span><Check v-if="(evidenceScopeSelections[option.sourceId]?.length ?? 0) > 0" :size="15" /></span>
              <p><strong>{{ option.title }}</strong><small>{{ option.subtitle || option.excerpt }}</small></p>
              <em v-if="careerEvidenceStrengthLabel(option.strength)">{{ careerEvidenceStrengthLabel(option.strength) }}</em>
            </header>
            <fieldset>
              <legend>允许用于</legend>
              <label v-for="scope in EVIDENCE_SCOPE_OPTIONS" :key="scope.value" :class="{ active: hasEvidenceScope(evidenceScopeSelections, option.sourceId, scope.value) }">
                <input
                  type="checkbox"
                  :checked="hasEvidenceScope(evidenceScopeSelections, option.sourceId, scope.value)"
                  @change="toggleEvidenceScope(option.sourceId, scope.value, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ scope.label }}</span>
              </label>
            </fieldset>
          </article>
        </div>
      </div>
      <template #footer><button class="cp-secondary" type="button" @click="evidenceOpen = false">取消</button><button class="cp-primary" type="button" :disabled="pending === 'evidence'" @click="saveEvidence">确认授权 {{ selectedEvidenceCount }} 项</button></template>
    </AppModal>

    <AppModal :open="Boolean(detailRecommendation)" title="职业方向详情" :width="620" mobile-sheet @close="detailRecommendation = null">
      <article v-if="detailRecommendation" class="cp-detail-dialog"><header><span><BriefcaseBusiness :size="23" /></span><div><h2>{{ detailRecommendation.title }}</h2><p>{{ detailRecommendation.fitSummary }}</p></div></header><section><h3>推荐依据</h3><ul><li v-for="value in detailRecommendation.rationale" :key="value">{{ value }}</li></ul></section><section><h3>能力缺口</h3><ul v-if="detailRecommendation.gaps.length"><li v-for="value in detailRecommendation.gaps" :key="value">{{ value }}</li></ul><p v-else>暂无明确能力缺口。</p></section><section><h3>事实引用</h3><div class="cp-ref-list"><span v-for="value in recommendationSourceLabels(detailRecommendation)" :key="value">{{ value }}</span><small v-if="!detailRecommendation.sourceRefs.length">没有可展示的来源引用</small></div></section></article>
      <template #footer><button class="cp-secondary" type="button" @click="detailRecommendation = null">关闭</button><button v-if="detailRecommendation" class="cp-primary" type="button" @click="prepareGoal(detailRecommendation); detailRecommendation = null">设为目标 <ChevronRight :size="16" /></button></template>
    </AppModal>

    <AppModal :open="compareOpen" title="职业方向对比" :width="1120" mobile-sheet @close="compareOpen = false">
      <div class="cp-compare-dialog">
        <header><span><GitCompareArrows :size="22" /></span><div><h2>并排核对职业方向</h2><p>比较推荐依据、能力缺口与事实来源。收藏和对比彼此独立，最终仍需二次确认目标。</p></div><em>{{ comparedRecommendations.length }} / 3</em></header>
        <section class="cp-compare-grid" :style="{ '--compare-columns': String(Math.max(1, comparedRecommendations.length)) }">
          <article v-for="item in comparedRecommendations" :key="item.id">
            <header><div><Bookmark :size="18" /><span><strong>{{ item.title }}</strong><small>{{ item.tier === 'READY_NOW' ? '当前较匹配' : item.tier === 'AFTER_SMALL_GAP' ? '补齐小缺口后可拓展' : '探索方向' }}</small></span></div><button type="button" title="移出对比" @click="toggleComparison(item)"><X :size="16" /></button></header>
            <p>{{ item.fitSummary }}</p>
            <section><h3>推荐依据</h3><ul><li v-for="value in item.rationale" :key="value">{{ value }}</li></ul></section>
            <section><h3>能力缺口</h3><ul v-if="item.gaps.length"><li v-for="value in item.gaps" :key="value">{{ value }}</li></ul><p v-else>暂无明确能力缺口</p></section>
            <section><h3>事实来源</h3><div class="cp-ref-list"><span v-for="value in recommendationSourceLabels(item)" :key="value">{{ value }}</span><small v-if="!item.sourceRefs.length">没有可展示的来源引用</small></div></section>
            <footer><button type="button" @click="detailRecommendation = item">查看完整详情</button><button type="button" @click="prepareGoal(item); compareOpen = false">设为目标 <ChevronRight :size="15" /></button></footer>
          </article>
          <p v-if="!comparedRecommendations.length" class="cp-compare-empty">对比清单为空，请从推荐列表选择 2–3 个方向。</p>
        </section>
      </div>
      <template #footer><button class="cp-secondary" type="button" @click="compareOpen = false">返回推荐列表</button></template>
    </AppModal>

    <AppModal :open="Boolean(confirmation)" title="确认目标职业" :width="560" mobile-sheet @close="confirmation = null; confirmationRecommendation = null">
      <div v-if="confirmationRecommendation" class="cp-goal-dialog"><span><Target :size="30" /></span><h2>{{ confirmationRecommendation.title }}</h2><p>确认后将为这个职业创建一张独立能力画布。推荐结果不会自动替你做决定。</p><section><CircleAlert :size="18" /><div><strong>这是一项明确的用户确认</strong><small>该画布会独立保存能力树、学习计划、能力验证和版本记录，其他画布不受影响。</small></div></section><label><input v-model="goalAcknowledged" type="checkbox"> 我已阅读推荐依据和能力缺口，确认将此职业设为本画布目标</label></div>
      <template #footer><button class="cp-secondary" type="button" @click="confirmation = null; confirmationRecommendation = null; goalAcknowledged = false">再比较一下</button><button class="cp-primary" type="button" :disabled="pending === 'commit-goal' || !goalAcknowledged" @click="commitGoal"><LoaderCircle v-if="pending === 'commit-goal'" class="cp-spin" :size="16" /><Target v-else :size="16" />确认目标并创建画布</button></template>
    </AppModal>
</template>
