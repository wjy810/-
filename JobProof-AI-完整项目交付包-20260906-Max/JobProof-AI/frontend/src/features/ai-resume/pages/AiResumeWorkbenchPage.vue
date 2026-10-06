<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ResumeTemplatePreview from '@/features/resume/components/ResumeTemplatePreview.vue'
import { downloadPrivateFile, fetchCurrentResumeLayout } from '@/features/resume/services/resumeApi'
import { pdfDownloadDisabledReason } from '@/features/resume/labels'
import { errorMessage, isApiClientError } from '@/shared/api/types'
import type { TaskView } from '@/shared/api/task'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import { createDraftSaveQueue } from '@/shared/lib/draftSaveQueue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import type { IconName } from '@/shared/ui/icons'
import { useToastFeedback } from '@/shared/ui/toast'
import {
  addAiResumeMessage,
  cancelAiResumeResponse,
  confirmAiResumeLanguageBranch,
  createAiResumeLanguageBranch,
  deleteAiResumeHistory,
  exportAiResumePdf,
  fetchAiResume,
  fetchAiResumeBranchDiff,
  fetchAiResumeWritingPreference,
  generateAiResumeDescriptionSuggestion,
  grantAiResumeConsent,
  listAiResumeBranches,
  listAiResumeRevisions,
  listAiResumeSmartTemplates,
  removeAiResumePhoto,
  revokeAiResumeConsent,
  restoreAiResumeRevision,
  saveAiResumeCardDraft,
  saveAiResumeDesign,
  selectAiResumeTemplate,
  skipAiResumeCard,
  submitAiResumeCard,
  uploadAiResumePhoto,
  setAiResumeWritingPreference,
  setCareerLibraryEvidencePreference,
  switchAiResumeBranch,
  syncAiResumeBranch,
  streamAiResume,
  translateAiResumeLanguageBranch,
} from '../services/aiResumeApi'
import AiAssistantAvatar from '../components/AiAssistantAvatar.vue'
import AiResumeChangeSetCard from '../components/AiResumeChangeSetCard.vue'
import AiCertificateSuggestionPanel from '../components/AiCertificateSuggestionPanel.vue'
import AiDescriptionField from '../components/AiDescriptionField.vue'
import AiSkillSuggestionPanel from '../components/AiSkillSuggestionPanel.vue'
import AiSummarySuggestionPanel from '../components/AiSummarySuggestionPanel.vue'
import JobTaxonomyPicker from '../components/JobTaxonomyPicker.vue'
import MonthInput from '../components/MonthInput.vue'
import UserMessageAvatar from '../components/UserMessageAvatar.vue'
import type {
  AiResumeBranch,
  AiResumeBranchDiff,
  AiResumeCard,
  AiResumeConversation,
  AiResumeChangeSet,
  AiResumeMessage,
  AiResumePdfExportMode,
  AiResumeRevision,
  AiCertificateItemSuggestion,
  AiSkillGroupSuggestion,
  AiSmartTemplate,
  AiWritingStyleCode,
  JobTaxonomySelection,
} from '../types'
import { moveListItem } from '../utils/listEditing'
import { applyPendingChangeOverlay, shouldOverlayCardPayload } from '../utils/changeSetOverlay'
import {
  descriptionFactsFingerprint,
  descriptionInputIssue,
  descriptionSuggestionKey,
  mergeDescription,
  type DescriptionSuggestionState,
} from '../utils/descriptionSuggestion'
import { mergeSkillGroups } from '../utils/skillSuggestions'
import {
  credentialDescriptionCharacters,
  credentialDescriptionIssue,
  mergeCredentialSuggestions,
} from '../utils/certificateSuggestions'
import { suggestGuidedCard } from '../utils/guidedFlow'
import { contactSubmitIssue } from '../utils/contactDetails'
import { needsPdfContactWarning, pdfFallbackFilename } from '../utils/pdfExportMode'
import { formatPdfOverflow, isPdfOverflowBlocked, pdfOverflowItems as layoutOverflowItems } from '../utils/pdfPreflight'
import {
  nextAutoFollowState,
  scrollEdges as measureScrollEdges,
  shouldShowReturnToBottom,
  type ScrollMetrics,
} from '../utils/scrollSurface'
import type { ResumeDesignSettings } from '@/features/resume/types'

type MobilePane = 'conversation' | 'preview'
type WorkbenchView = 'conversation' | 'edit' | 'design' | 'templates'
type DraftState = 'idle' | 'waiting' | 'saving' | 'saved' | 'error'
type WorkbenchTool = 'branches' | 'history' | 'privacy'
type DisplayMessage = AiResumeMessage & { transient?: boolean; phase?: 'sending' | 'loading' | 'streaming' }
type StructuredItem = Record<string, unknown>

const route = useRoute()
const conversation = ref<AiResumeConversation | null>(null)
const loading = ref(true)
const pageError = ref('')
const notice = ref('')
const selectedCardId = ref('')
const mobilePane = ref<MobilePane>('conversation')
const workbenchView = ref<WorkbenchView>('conversation')
const templatePending = ref(false)
const smartTemplates = ref<AiSmartTemplate[]>([])
const templatesPending = ref(false)
const designDraft = ref<ResumeDesignSettings | null>(null)
const designVariant = ref('')
const designVersion = ref(0)
const designSaveState = ref<DraftState>('idle')
const messageText = ref('')
const transientMessages = ref<DisplayMessage[]>([])
const messagePending = ref(false)
const cancelPending = ref(false)
const currentMessageRequestId = ref('')
const moduleEditorOpen = ref(false)
const guidedOverrideCardId = ref('')
const repeatPromptType = ref('')
const consentPending = ref(false)
const activeTool = ref<WorkbenchTool | null>(null)
const toolPending = ref('')
const toolError = ref('')
const revisions = ref<AiResumeRevision[]>([])
const branches = ref<AiResumeBranch[]>([])
const branchDiff = ref<AiResumeBranchDiff | null>(null)
const careerEvidencePending = ref(false)
const writingStyle = ref<AiWritingStyleCode>('SYSTEM_RECOMMENDED')
const historyDeletionArmed = ref(false)
const photoInput = ref<HTMLInputElement | null>(null)
const photoPending = ref(false)
const pdfConfirmOpen = ref(false)
const pdfPending = ref(false)
const pdfExportMode = ref<AiResumePdfExportMode>('STANDARD')
const pdfTask = ref<TaskView | null>(null)
const pdfError = ref('')
useToastFeedback(pageError, 'error', 'ai-resume-workbench-error')
useToastFeedback(pdfError, 'error', 'ai-resume-pdf-error')
useToastFeedback(notice, 'success', 'ai-resume-workbench-notice')
const conversationScroll = ref<HTMLElement | null>(null)
const previewScroll = ref<HTMLElement | null>(null)
const toolScroll = ref<HTMLElement | null>(null)
const workbenchGrid = ref<HTMLElement | null>(null)
const splitRatio = ref(40)
const splitDragging = ref(false)
const conversationEdges = reactive({ canScrollUp: false, canScrollDown: false })
const previewEdges = reactive({ canScrollUp: false, canScrollDown: false })
const toolEdges = reactive({ canScrollUp: false, canScrollDown: false })
const conversationAutoFollow = ref(true)
const showConversationReturn = ref(false)
const payloads = reactive<Record<string, Record<string, unknown>>>({})
const draftStates = reactive<Record<string, DraftState>>({})
const localRevisions = reactive<Record<string, number>>({})
const descriptionSuggestions = reactive<Record<string, DescriptionSuggestionState>>({})
const summaryGenerationPending = ref(false)
const dirty = new Set<string>()
const timers = new Map<string, number>()
const draftFlights = new Map<string, Promise<boolean>>()
const confirming = new Set<string>()
let eventSource: EventSource | null = null
let reloadTimer = 0
const splitStorageKey = 'jobproof.ai-resume.split-ratio'
let scrollFrame = 0
let surfaceFrame = 0
let previousConversationScrollTop = 0
let scrollSurfaceObserver: MutationObserver | null = null
let generationController: AbortController | null = null
const descriptionControllers = new Map<string, AbortController>()
let pdfPollAbort: AbortController | null = null
let disposed = false
let designIdentity = ''

const cardMeta: Record<string, { label: string; description: string; icon: IconName }> = {
  TARGET_JOB: { label: '目标岗位', description: '第一版必填', icon: 'target' },
  EDUCATION: { label: '教育经历', description: '教育或经历至少一项', icon: 'book' },
  EXPERIENCE: { label: '工作与实习', description: '教育或经历至少一项', icon: 'briefcase' },
  PROJECTS: { label: '项目经历', description: '可以稍后补充', icon: 'folder' },
  ORGANIZATIONS: { label: '社团与活动', description: '学生经历可以补充', icon: 'users' },
  SKILLS: { label: '专业技能', description: '只填写真实掌握项', icon: 'sparkles' },
  CERTIFICATES: { label: '证书与资质', description: '只填写已取得证书', icon: 'award' },
  HONORS: { label: '荣誉奖项', description: '注明可确认的名称与时间', icon: 'star' },
  LANGUAGES: { label: '语言能力', description: '注明真实水平或成绩', icon: 'message' },
  CONTACT: { label: '联系方式', description: '导出前建议补全', icon: 'user' },
  SUMMARY: { label: '个人简介', description: '可用 AI 生成三个简介版本', icon: 'file-text' },
}

const degreeOptions = [
  { value: '', label: '请选择' },
  { value: '高中', label: '高中' },
  { value: '专科', label: '专科' },
  { value: '本科', label: '本科' },
  { value: '硕士', label: '硕士' },
  { value: '博士', label: '博士' },
  { value: '交换生', label: '交换生' },
]
const languageOptions = [
  { value: '', label: '请选择语言', disabled: true },
  { value: '英语', label: '英语', keywords: 'English en' },
  { value: '日语', label: '日语', keywords: 'Japanese ja' },
  { value: '韩语', label: '韩语', keywords: 'Korean ko' },
  { value: '法语', label: '法语', keywords: 'French fr' },
  { value: '德语', label: '德语', keywords: 'German de' },
  { value: '西班牙语', label: '西班牙语', keywords: 'Spanish es' },
  { value: '葡萄牙语', label: '葡萄牙语', keywords: 'Portuguese pt' },
  { value: '俄语', label: '俄语', keywords: 'Russian ru' },
  { value: '意大利语', label: '意大利语', keywords: 'Italian it' },
  { value: '阿拉伯语', label: '阿拉伯语', keywords: 'Arabic ar' },
  { value: '泰语', label: '泰语', keywords: 'Thai th' },
  { value: '越南语', label: '越南语', keywords: 'Vietnamese vi' },
  { value: '印度尼西亚语', label: '印度尼西亚语', keywords: '印尼语 Indonesian id' },
  { value: '马来语', label: '马来语', keywords: 'Malay ms' },
  { value: '荷兰语', label: '荷兰语', keywords: 'Dutch nl' },
  { value: '波兰语', label: '波兰语', keywords: 'Polish pl' },
  { value: '土耳其语', label: '土耳其语', keywords: 'Turkish tr' },
  { value: '普通话', label: '普通话', keywords: '中文 Mandarin zh' },
  { value: '粤语', label: '粤语', keywords: 'Cantonese yue' },
]
const languageLevelOptions = [
  { value: '', label: '请选择水平', disabled: true },
  { value: '入门', label: '入门' },
  { value: '基础', label: '基础' },
  { value: '日常交流', label: '日常交流' },
  { value: '工作沟通', label: '工作沟通' },
  { value: '熟练', label: '熟练' },
  { value: '精通', label: '精通' },
  { value: '母语', label: '母语' },
]
const fontPresetOptions = [{ value: 'MODERN_SANS', label: '现代黑体' }, { value: 'CLASSIC_SERIF', label: '经典宋体' }]
const fontScaleOptions = [{ value: 'SMALL', label: '小' }, { value: 'STANDARD', label: '标准' }, { value: 'LARGE', label: '大' }]
const spacingOptions = [{ value: 'COMPACT', label: '紧凑' }, { value: 'STANDARD', label: '标准' }, { value: 'AIRY', label: '舒展' }]
const pageMarginOptions = [{ value: 'NARROW', label: '窄' }, { value: 'STANDARD', label: '标准' }, { value: 'WIDE', label: '宽' }]
const dateFormatOptions = [{ value: 'YYYY_DOT_MM', label: 'YYYY.MM' }, { value: 'YYYY_CN_MM', label: 'YYYY年MM月' }]
const headerLayoutOptions = [{ value: 'MINIMAL', label: '居中简约' }, { value: 'BAND', label: '色带头部' }, { value: 'SPLIT', label: '左右分栏' }, { value: 'COMPACT', label: '紧凑头部' }]
const headingStyleOptions = [{ value: 'RULE', label: '下划线' }, { value: 'BAR', label: '色条' }, { value: 'SIDELINE', label: '侧线' }, { value: 'PLAIN', label: '纯文本' }, { value: 'TABLE', label: '表格' }]
const photoModeOptions = [{ value: 'AUTO', label: '自动' }, { value: 'SHOW', label: '显示' }, { value: 'HIDE', label: '隐藏' }]
const writingStyleOptions = [
  { value: 'SYSTEM_RECOMMENDED', label: '系统推荐' },
  { value: 'PROFESSIONAL_CONCISE', label: '专业简洁' },
  { value: 'RESULTS_ORIENTED', label: '成果导向' },
  { value: 'TECHNICAL_RIGOR', label: '技术严谨' },
  { value: 'STEADY_FORMAL', label: '稳健正式' },
]

const editableCards = computed(() => conversation.value?.cards.filter((card) => card.cardType !== 'IDENTITY') ?? [])
const selectedCard = computed(() => editableCards.value.find((card) => card.id === selectedCardId.value) ?? editableCards.value[0] ?? null)
const selectedPayload = computed(() => selectedCard.value ? payloads[selectedCard.value.id] ?? {} : {})
const ready = computed(() => conversation.value?.onboardingStage === 'READY_FOR_PREVIEW')
const messages = computed<DisplayMessage[]>(() => {
  const persisted = conversation.value?.messages ?? []
  const persistedIds = new Set(persisted.map((message) => message.id))
  return [...persisted, ...transientMessages.value.filter((message) => !persistedIds.has(message.id))]
})
function messageChangeSets(messageId: string): AiResumeChangeSet[] {
  return conversation.value?.changeSets.filter((changeSet) => changeSet.messageId === messageId) ?? []
}

async function onChangeSetUpdated(next: AiResumeChangeSet): Promise<void> {
  if (!conversation.value) return
  const index = conversation.value.changeSets.findIndex((changeSet) => changeSet.id === next.id)
  if (index >= 0) conversation.value.changeSets.splice(index, 1, next)
  else conversation.value.changeSets.push(next)
  await load()
  notice.value = next.status === 'REJECTED'
    ? '该组修改已处理，拒绝项没有改变正式简历。'
    : '修改状态已更新，已接受内容已写入正式简历并生成新修订。'
}
const confirmedCardCount = computed(() => editableCards.value.filter((card) => card.status === 'CONFIRMED').length)
const suggestedCard = computed(() => {
  return suggestGuidedCard(conversation.value?.identityType, editableCards.value)
})
const guidedCard = computed(() => editableCards.value.find((card) => card.id === guidedOverrideCardId.value) ?? suggestedCard.value)
const showGuidedPrompt = computed(() => Boolean(guidedCard.value) && !repeatPromptType.value)
const showIdentityIntro = computed(() => Boolean(conversation.value) && conversation.value?.messages.length === 0)
const identityIntro = computed(() => {
  const identity = conversation.value?.identityType
  if (identity === 'STUDENT') return { answer: '我目前是学生', reply: '很好。接下来会优先梳理教育、实践、项目和技能，并把确认内容同步到右侧预览。' }
  if (identity === 'GRADUATE') return { answer: '我目前是应届生', reply: '明白。接下来会围绕校招目标梳理教育、实习、项目和技能，并实时生成第一版。' }
  return { answer: '我目前是职场人士', reply: '明白。接下来会优先梳理目标岗位、工作成果和项目经历，并保持所有事实可确认。' }
})
const assistantPrompt = computed(() => {
  const type = guidedCard.value?.cardType
  const educationFirst = conversation.value?.identityType === 'STUDENT' || conversation.value?.identityType === 'GRADUATE'
  if (type === 'TARGET_JOB') return educationFirst
    ? '教育背景已经记录。现在请选择一个标准目标岗位，后续技能和内容建议都会以这个岗位为准。'
    : '我们先从目标开始：请从标准岗位分类中选择你准备投递的岗位。'
  if (type === 'EDUCATION') return educationFirst
    ? '接下来先把教育背景梳理清楚。请填写学校、专业、学历和起止时间，确认后会立即同步到右侧预览。'
    : '请告诉我一段教育经历：学校、专业、学历和起止时间。暂时不确定的内容可以稍后补充。'
  if (type === 'EXPERIENCE') return '接下来聊一段工作或实习经历。请告诉我公司、岗位、时间，以及你实际做过的事情。'
  if (type === 'PROJECTS') return '有能证明能力的项目吗？告诉我项目背景、你的职责和真实结果，我会帮你整理表达。'
  if (type === 'SKILLS') return '请告诉我你确实掌握的专业技能。不会的技能不要为了匹配岗位而添加。'
  if (type === 'CONTACT') return '简历内容已经有了基础，接下来可以补充用于投递的姓名、邮箱或手机号。'
  if (type) return `还可以继续补充${cardMeta[type]?.label ?? '简历内容'}。你可以直接在对话里描述，我会基于真实事实整理。`
  return '你可以继续告诉我教育、目标岗位、经历或想修改的内容，我会一次处理一个问题。'
})
const quotaText = computed(() => {
  const quota = conversation.value?.quota
  return quota ? `${quota.remainingUnits} / ${quota.grantedUnits}` : '—'
})
const aiReason = computed(() => {
  if (conversation.value?.aiAvailable) return ''
  return conversation.value?.aiUnavailableReason === 'AI_GATEWAY_DISABLED'
    ? 'AI 通道当前未启用。消息可以保存，但暂时不会生成回复；简历资料、预览和导出仍可使用。'
    : '当前没有通过验收的 AI 通道。消息可以保存，但暂时不会生成回复。'
})
const activeTemplate = computed(() => smartTemplates.value.find((item) => item.templateId === conversation.value?.layout?.templateId)
  ?? conversation.value?.activeTemplate ?? smartTemplates.value[0] ?? null)
const activeDesign = computed(() => designDraft.value ?? conversation.value?.activeDesign?.settings ?? activeTemplate.value?.design.settings ?? null)
const layoutJson = computed(() => activeTemplate.value?.layoutDefinitionJson ?? conversation.value?.layout?.layoutDefinitionJson ?? null)
const rendererProtocol = computed(() => activeTemplate.value?.rendererProtocol ?? conversation.value?.layout?.rendererProtocol ?? 'resume-layout-v3')
const activeVariant = computed(() => designVariant.value || conversation.value?.activeDesign?.variantCode
  || activeTemplate.value?.design.variantCode || conversation.value?.layout?.variantCode || 'MONO')
const layoutDefinition = computed<Record<string, any>>(() => {
  try { return JSON.parse(layoutJson.value || '{}') as Record<string, any> } catch { return {} }
})
const availableAccentColors = computed(() => Array.from(new Set(Object.entries(layoutDefinition.value.tokens ?? {})
  .filter(([key, value]) => key.startsWith('accent.') && typeof value === 'string')
  .map(([, value]) => String(value).toUpperCase()))))
const designSections = computed(() => {
  const slots = Array.isArray(layoutDefinition.value.slots) ? layoutDefinition.value.slots as Array<Record<string, unknown>> : []
  return slots.map((slot) => ({ key: String(slot.key ?? ''), label: String(slot.label ?? sectionLabels[String(slot.key ?? '')] ?? slot.key ?? '') }))
    .filter((slot) => slot.key)
})
const previewResume = computed(() => {
  if (!conversation.value) return null
  const resume = { ...conversation.value.resume }
  for (const card of conversation.value.cards) {
    if (!shouldOverlayCardPayload(card.status, dirty.has(card.id))) continue
    const payload = payloads[card.id] ?? card.payload ?? {}
    if (card.cardType === 'EDUCATION') resume.education = formatStructuredItems(card.cardType, structuredItems(card))
    else if (card.cardType === 'EXPERIENCE') resume.experience = formatStructuredItems(card.cardType, structuredItems(card))
    else if (card.cardType === 'PROJECTS') resume.projects = formatStructuredItems(card.cardType, structuredItems(card))
    else if (card.cardType === 'SKILLS') resume.skills = String(payload.text ?? '')
    else if (card.cardType === 'CERTIFICATES') resume.certificates = String(payload.text ?? '')
    else if (card.cardType === 'SUMMARY') resume.selfIntro = String(payload.text ?? '')
  }
  return resume
})
const previewContent = computed<Record<string, unknown>>(() => {
  if (!conversation.value) return {}
  const content = JSON.parse(JSON.stringify(conversation.value.content ?? {})) as Record<string, unknown>
  for (const card of conversation.value.cards) {
    if (!shouldOverlayCardPayload(card.status, dirty.has(card.id))) continue
    const payload = payloads[card.id] ?? card.payload ?? {}
    if (card.cardType === 'TARGET_JOB') content.intentions = { ...objectRecord(content.intentions), targetJob: payload.targetJob ?? '' }
    else if (card.cardType === 'CONTACT') content.basics = { ...objectRecord(content.basics), ...payload }
    else if (card.cardType === 'SUMMARY') content.summary = String(payload.text ?? '')
    else if (card.cardType === 'EDUCATION') content.education = cleanItems(payload.items)
    else if (card.cardType === 'EXPERIENCE') content.experiences = cleanItems(payload.items)
    else if (card.cardType === 'PROJECTS') content.projects = cleanItems(payload.items)
    else if (card.cardType === 'ORGANIZATIONS') content.organizations = cleanItems(payload.items)
    else if (card.cardType === 'SKILLS') content.skills = cleanItems(payload.items)
    else if (card.cardType === 'CERTIFICATES') content.certificates = cleanItems(payload.items)
    else if (card.cardType === 'HONORS') content.honors = cleanItems(payload.items)
    else if (card.cardType === 'LANGUAGES') content.languages = cleanItems(payload.items)
  }
  return applyPendingChangeOverlay(content, conversation.value.changeSets ?? [], conversation.value.activeBranchId)
})
const pdfRiskMessages = computed(() => {
  const result: string[] = []
  if (pdfOverflowBlocked.value) {
    result.push(`${pdfOverflowSummary.value}，必须精简内容、调整设计或更换页数更多的模板后才能导出。`)
  }
  if (!ready.value) result.push('当前内容尚未满足第一版条件，导出的 PDF 可能不完整。')
  const pendingChanges = conversation.value?.changeSets
    .flatMap((changeSet) => changeSet.items).filter((item) => item.status === 'PENDING').length ?? 0
  if (pendingChanges > 0) {
    result.push(`存在 ${pendingChanges} 条待确认修改；它们只显示在预览中，不会进入本次 PDF。`)
  }
  const basics = objectRecord(previewContent.value.basics)
  if (needsPdfContactWarning(pdfExportMode.value, basics)) {
    result.push('尚未填写邮箱或手机号，正式投递前建议至少保留一种联系方式。')
  }
  if (Array.from(dirty).length > 0) result.push('尚未确认的卡片草稿只用于预览，不会进入本次 PDF。')
  return result
})
const pdfOverflowItems = computed(() => layoutOverflowItems(conversation.value?.layout))
const pdfOverflowBlocked = computed(() => isPdfOverflowBlocked(conversation.value?.layout))
const pdfOverflowSummary = computed(() => formatPdfOverflow(pdfOverflowItems.value[0]))
const previewStatusText = computed(() => pdfOverflowBlocked.value
  ? `PDF 溢出 · ${pdfOverflowSummary.value}`
  : ready.value ? '已满足第一版条件' : '还需目标岗位和教育/经历')

const structuredCardTypes = new Set(['EDUCATION', 'EXPERIENCE', 'PROJECTS', 'ORGANIZATIONS', 'SKILLS', 'CERTIFICATES', 'HONORS', 'LANGUAGES'])
const timelineCardTypes = new Set(['EDUCATION', 'EXPERIENCE', 'PROJECTS', 'ORGANIZATIONS'])
const descriptionCardTypes = new Set([...timelineCardTypes, 'LANGUAGES'])
const descriptionFieldLabels: Record<string, string> = {
  school: '学校', major: '专业', degree: '学历', company: '公司或组织', role: '角色', name: '名称',
  department: '部门', startDate: '开始时间', endDate: '结束时间', current: '至今', location: '城市',
  description: '已有描述', targetJob: '目标岗位', language: '语言', level: '水平', score: '成绩或证明',
}
const sectionLabels: Record<string, string> = {
  summary: '个人简介', education: '教育经历', experience: '工作与实习经历', projects: '项目经历',
  organizations: '社团与活动', skills: '专业技能', certificates: '证书与资质', honors: '荣誉奖项', languages: '语言能力',
}

function clonePayload(value: Record<string, unknown> | null | undefined): Record<string, unknown> {
  return value ? JSON.parse(JSON.stringify(value)) as Record<string, unknown> : {}
}

function isStructuredCard(card: AiResumeCard): boolean {
  return structuredCardTypes.has(card.cardType)
}

function blankStructuredItem(type: string): StructuredItem {
  const common = { startDate: '', endDate: '', current: false, location: '', description: '' }
  if (type === 'EDUCATION') return { school: '', major: '', degree: '', ...common }
  if (type === 'EXPERIENCE') return { company: '', role: '', ...common }
  if (type === 'PROJECTS' || type === 'ORGANIZATIONS') return { name: '', role: '', department: '', ...common }
  if (type === 'SKILLS') return { category: '', items: [], description: '' }
  if (type === 'CERTIFICATES') return { name: '', issuer: '', date: '', description: '' }
  if (type === 'HONORS') return { name: '', issuer: '', date: '', description: '' }
  return { language: '', level: '', score: '', description: '' }
}

function languageOptionsFor(value: unknown): typeof languageOptions {
  const current = String(value ?? '').trim()
  if (!current || languageOptions.some((option) => option.value === current)) return languageOptions
  return [...languageOptions, { value: current, label: `${current}（已有）`, keywords: current }]
}

function structuredItems(card: AiResumeCard): StructuredItem[] {
  const value = payloads[card.id]?.items
  if (Array.isArray(value) && value.length) return value as StructuredItem[]
  return [blankStructuredItem(card.cardType)]
}

function updateStructuredField(card: AiResumeCard, index: number, key: string, value: unknown): void {
  const items = structuredItems(card).map((item) => ({ ...item }))
  items[index] = { ...items[index], [key]: value }
  const suggestionKey = descriptionSuggestionKey(card.id, index)
  if (descriptionSuggestions[suggestionKey]?.status !== 'loading') delete descriptionSuggestions[suggestionKey]
  updateField(card, 'items', items)
}

const descriptionGenerationPending = computed(() =>
  Object.values(descriptionSuggestions).some((state) => state.status === 'loading'))
const aiFieldGenerationPending = computed(() => descriptionGenerationPending.value || summaryGenerationPending.value)

function descriptionState(card: AiResumeCard, index: number): DescriptionSuggestionState | undefined {
  return descriptionSuggestions[descriptionSuggestionKey(card.id, index)]
}

function descriptionFacts(card: AiResumeCard, index: number): Record<string, unknown> {
  return { ...structuredItems(card)[index] }
}

function descriptionSourceLabels(card: AiResumeCard, index: number): string[] {
  return descriptionState(card, index)?.candidate?.sourceFields
    .map((field) => descriptionFieldLabels[field] ?? field) ?? []
}

function descriptionDisabledReason(card: AiResumeCard, index: number): string {
  if (!conversation.value?.aiAvailable) return 'AI 通道暂不可用，仍可手动填写'
  if (conversation.value.consent.status !== 'GRANTED') return '请先完成 AI 授权'
  if (messagePending.value) return '正在生成对话回复，请稍后再试'
  if (summaryGenerationPending.value) return '正在生成个人简介，请稍后再试'
  const key = descriptionSuggestionKey(card.id, index)
  if (descriptionGenerationPending.value && descriptionSuggestions[key]?.status !== 'loading') {
    return '另一条经历正在生成，请稍后再试'
  }
  return ''
}

function summaryDisabledReason(): string {
  if (!conversation.value?.aiAvailable) return 'AI 通道暂不可用，仍可手动填写'
  if (conversation.value.consent.status !== 'GRANTED') return '请先完成 AI 授权'
  if (messagePending.value) return '正在生成对话回复，请稍后再试'
  if (descriptionGenerationPending.value) return '另一项内容正在生成，请稍后再试'
  return ''
}

function applySummaryCandidate(card: AiResumeCard, text: string): void {
  updateField(card, 'text', text)
  notice.value = 'AI 个人简介已写入卡片草稿；确认此模块后才进入正式简历和 PDF。'
}

function updateSummaryPending(pending: boolean): void {
  summaryGenerationPending.value = pending
}

function descriptionPlaceholder(cardType: string): string {
  if (cardType === 'LANGUAGES') {
    return '可选：填写真实使用场景；留空时 AI 会生成三条待确认参考'
  }
  return cardType === 'EDUCATION'
    ? '可选：填写真实课程、实践或成果；留空时 AI 将生成待确认参考'
    : '可选：填写真实职责或成果；留空时 AI 将根据基础信息生成待确认参考'
}

async function generateDescription(card: AiResumeCard, index: number): Promise<void> {
  if (!conversation.value) return
  const disabledReason = descriptionDisabledReason(card, index)
  if (disabledReason) {
    pageError.value = disabledReason
    return
  }
  const key = descriptionSuggestionKey(card.id, index)
  const requestId = crypto.randomUUID()
  const facts = descriptionFacts(card, index)
  const fingerprint = descriptionFactsFingerprint(facts)
  const inputIssue = descriptionInputIssue(card.cardType, facts)
  if (inputIssue) {
    descriptionSuggestions[key] = { status: 'error', requestId, factsFingerprint: fingerprint, error: inputIssue }
    return
  }
  const controller = new AbortController()
  descriptionControllers.set(key, controller)
  descriptionSuggestions[key] = { status: 'loading', requestId, factsFingerprint: fingerprint }
  pageError.value = ''
  notice.value = ''
  try {
    const candidate = await generateAiResumeDescriptionSuggestion(
      conversation.value.id, card.id, index, facts, requestId, controller.signal,
    )
    if (descriptionSuggestions[key]?.requestId !== requestId) return
    if (descriptionFactsFingerprint(descriptionFacts(card, index)) !== fingerprint) {
      descriptionSuggestions[key] = {
        status: 'error', requestId, factsFingerprint: fingerprint,
        error: '生成期间这条经历已发生变化，请按最新内容重新生成。',
      }
      return
    }
    descriptionSuggestions[key] = { status: 'ready', requestId, factsFingerprint: fingerprint, candidate }
    if (conversation.value) {
      conversation.value.quota.remainingUnits = candidate.remainingQuota
      conversation.value.quota.usedUnits = Math.max(0,
        conversation.value.quota.grantedUnits - candidate.remainingQuota - conversation.value.quota.heldUnits)
    }
  } catch (reason) {
    if (descriptionSuggestions[key]?.requestId !== requestId) return
    if (isAbortError(reason)) return
    descriptionSuggestions[key] = {
      status: 'error', requestId, factsFingerprint: fingerprint,
      error: errorMessage(reason, 'AI 帮写失败，补充描述没有变化'),
    }
  } finally {
    if (descriptionControllers.get(key) === controller) descriptionControllers.delete(key)
  }
}

async function cancelDescription(card: AiResumeCard, index: number): Promise<void> {
  if (!conversation.value) return
  const key = descriptionSuggestionKey(card.id, index)
  const state = descriptionSuggestions[key]
  if (!state || state.status !== 'loading') return
  try {
    await cancelAiResumeResponse(conversation.value.id, state.requestId)
  } catch {
    // The local request is still aborted; the backend discards late output when cancellation was accepted.
  } finally {
    descriptionControllers.get(key)?.abort()
    if (descriptionSuggestions[key]?.requestId === state.requestId) delete descriptionSuggestions[key]
    notice.value = '已取消 AI 帮写，补充描述没有变化。'
  }
}

function applyDescription(card: AiResumeCard, index: number, mode: 'append' | 'replace'): void {
  const key = descriptionSuggestionKey(card.id, index)
  const candidate = descriptionSuggestions[key]?.candidate
  if (!candidate) return
  const current = String(structuredItems(card)[index]?.description ?? '')
  delete descriptionSuggestions[key]
  updateStructuredField(card, index, 'description', mergeDescription(current, candidate.suggestion, mode))
  notice.value = mode === 'append' && current.trim()
    ? 'AI 建议已追加到卡片草稿；确认此模块后才进入正式简历。'
    : 'AI 建议已写入卡片草稿；确认此模块后才进入正式简历。'
}

function discardDescription(card: AiResumeCard, index: number): void {
  delete descriptionSuggestions[descriptionSuggestionKey(card.id, index)]
}

function clearDescriptionStates(cardId: string): void {
  Object.keys(descriptionSuggestions).filter((key) => key.startsWith(`${cardId}:`)).forEach((key) => {
    const controller = descriptionControllers.get(key)
    const state = descriptionSuggestions[key]
    if (state?.status === 'loading' && conversation.value) {
      void cancelAiResumeResponse(conversation.value.id, state.requestId).finally(() => controller?.abort())
    } else {
      controller?.abort()
    }
    descriptionControllers.delete(key)
    delete descriptionSuggestions[key]
  })
}

function updateSkillItems(card: AiResumeCard, index: number, value: string): void {
  updateStructuredField(card, index, 'items', value.split(/[、,，\n]/).map((item) => item.trim()).filter(Boolean))
}

function skillItemsText(item: StructuredItem): string {
  return Array.isArray(item.items) ? item.items.map(String).join('、') : String(item.items ?? '')
}

function applySkillGroups(card: AiResumeCard, groups: AiSkillGroupSuggestion[]): void {
  updateField(card, 'items', mergeSkillGroups(structuredItems(card), groups))
  notice.value = 'AI 技能建议已合并到草稿；原技能和说明均已保留，确认专业技能模块后才进入正式简历。'
}

function applyCredentialItems(card: AiResumeCard, items: AiCertificateItemSuggestion[]): void {
  const kind = card.cardType === 'HONORS' ? 'HONOR' : 'CERTIFICATE'
  updateField(card, 'items', mergeCredentialSuggestions(structuredItems(card), items, kind))
  const noun = kind === 'CERTIFICATE' ? '证书' : '荣誉'
  notice.value = `AI ${noun}建议已合并到草稿；同名${noun}保留原内容，只补充缺失字段，新${noun}追加到末尾。`
}

function updateSkillQuota(remaining: number): void {
  if (!conversation.value) return
  conversation.value.quota.remainingUnits = remaining
  conversation.value.quota.usedUnits = Math.max(0,
    conversation.value.quota.grantedUnits - remaining - conversation.value.quota.heldUnits)
}

function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function cleanItems(value: unknown): StructuredItem[] {
  if (!Array.isArray(value)) return []
  return value.map((item) => objectRecord(item)).filter((item) => Object.values(item).some((entry) =>
    Array.isArray(entry) ? entry.length > 0 : typeof entry === 'boolean' ? entry : String(entry ?? '').trim().length > 0))
}

function addStructuredItem(card: AiResumeCard): void {
  const existing = Array.isArray(payloads[card.id]?.items) ? structuredItems(card) : []
  updateField(card, 'items', [...existing.map((item) => ({ ...item })), blankStructuredItem(card.cardType)])
  void nextTick(() => scheduleChatScroll(true))
}

function removeStructuredItem(card: AiResumeCard, index: number): void {
  clearDescriptionStates(card.id)
  const items = structuredItems(card).filter((_, itemIndex) => itemIndex !== index)
  updateField(card, 'items', items.length ? items : [blankStructuredItem(card.cardType)])
}

function moveStructuredItem(card: AiResumeCard, index: number, direction: -1 | 1): void {
  clearDescriptionStates(card.id)
  const items = structuredItems(card).map((item) => ({ ...item }))
  updateField(card, 'items', moveListItem(items, index, direction))
}

function contactLinks(card: AiResumeCard): string[] {
  const links = payloads[card.id]?.links
  return Array.isArray(links) ? links.map((link) => String(link ?? '')) : []
}

function addContactLink(card: AiResumeCard): void {
  updateField(card, 'links', [...contactLinks(card), ''])
}

function updateContactLink(card: AiResumeCard, index: number, value: string): void {
  const links = [...contactLinks(card)]
  links[index] = value
  updateField(card, 'links', links)
}

function removeContactLink(card: AiResumeCard, index: number): void {
  updateField(card, 'links', contactLinks(card).filter((_, itemIndex) => itemIndex !== index))
}

function moveContactLink(card: AiResumeCard, index: number, direction: -1 | 1): void {
  updateField(card, 'links', moveListItem(contactLinks(card), index, direction))
}

function formatStructuredItems(type: string, items: StructuredItem[]): string {
  return items.map((item) => {
    const heading = type === 'EDUCATION'
      ? [item.school, item.major, item.degree]
      : type === 'EXPERIENCE' ? [item.company, item.role] : [item.name, item.role]
    const end = item.current ? '至今' : item.endDate
    const meta = [[item.startDate, end].filter(Boolean).join(' - '), item.location].filter(Boolean).join(' · ')
    return [heading.filter(Boolean).join(' · '), meta, item.description].filter(Boolean).join('\n')
  }).filter(Boolean).join('\n\n')
}

function structuredTitle(type: string): string {
  if (type === 'EDUCATION') return '教育经历'
  if (type === 'EXPERIENCE') return '工作或实习经历'
  if (type === 'PROJECTS') return '项目经历'
  if (type === 'ORGANIZATIONS') return '社团或组织经历'
  if (type === 'SKILLS') return '技能类别'
  if (type === 'CERTIFICATES') return '证书'
  if (type === 'HONORS') return '荣誉'
  return '语言能力'
}

function applyConversation(next: AiResumeConversation): void {
  if (disposed) return
  const previous = conversation.value
  if (previous?.id === next.id && previous.activeBranchId === next.activeBranchId) {
    next = { ...next, cards: next.cards.map((card) => {
      const known = previous.cards.find((item) => item.id === card.id)
      return known && known.versionNo > card.versionNo ? known : card
    }) }
    if (previous.layout && previous.layout.version > (next.layout?.version ?? -1)) {
      next = { ...next, layout: previous.layout, activeDesign: previous.activeDesign, activeTemplate: previous.activeTemplate }
    }
    if (previous.activeDesign?.templateId === next.activeDesign?.templateId
      && (previous.activeDesign?.versionNo ?? -1) > (next.activeDesign?.versionNo ?? -1)) {
      next = { ...next, activeDesign: previous.activeDesign }
    }
    if (previous.resume.version > next.resume.version) next = { ...next, resume: previous.resume, content: previous.content }
  }
  conversation.value = next
  const nextDesignIdentity = `${next.id}:${next.layout?.templateId ?? next.activeDesign?.templateId ?? ''}`
  if (designIdentity !== nextDesignIdentity || (!designSaver.dirty && !designSaver.saving)) {
    designIdentity = nextDesignIdentity
    designSaver.reset()
    const settings = next.activeDesign?.settings ?? next.layout?.design ?? null
    designDraft.value = settings ? cloneDesign(settings) : null
    designVersion.value = next.activeDesign?.versionNo ?? 0
    designVariant.value = next.activeDesign?.variantCode ?? next.layout?.variantCode ?? ''
    designSaveState.value = 'idle'
    try {
      const local = designSaver.readRecovery()
      if (local && settings) {
        const backup = JSON.parse(local) as { settings?: ResumeDesignSettings; variantCode?: string }
        if (backup.settings?.schemaVersion === settings.schemaVersion && typeof backup.variantCode === 'string'
          && Array.isArray(backup.settings.hiddenSections) && Array.isArray(backup.settings.sectionOrder)) {
          designDraft.value = cloneDesign({ ...settings, ...backup.settings })
          designVariant.value = backup.variantCode
          designSaver.reset(true)
          designSaveState.value = 'waiting'
        }
      }
    } catch {
      pageError.value = '无法读取本地设计副本，请确认浏览器允许本地存储。'
    }
  }
  for (const card of next.cards) {
    // Whole-conversation responses may contain stale payloads for unrelated editors.
    if (!dirty.has(card.id) || !payloads[card.id]) {
      payloads[card.id] = clonePayload(card.payload)
    }
    draftStates[card.id] ||= 'idle'
    localRevisions[card.id] ||= 0
  }
  if (!selectedCardId.value || !next.cards.some((card) => card.id === selectedCardId.value)) {
    selectedCardId.value = next.cards.find((card) => card.cardType === 'TARGET_JOB')?.id ?? next.cards[0]?.id ?? ''
  }
  void nextTick(queueScrollSurfaceRefresh)
}

async function changeTemplate(templateId: string): Promise<void> {
  if (!conversation.value || templatePending.value || pdfPending.value || disposed) return
  const previous = conversation.value.layout?.templateId
  if (templateId === previous) return
  templatePending.value = true
  pageError.value = ''
  const conversationId = conversation.value.id
  try {
    if (!(await persistDesign()) || disposed || conversation.value?.id !== conversationId) return
    const next = await selectAiResumeTemplate(conversationId, templateId, conversation.value.layout?.version)
    if (disposed || conversation.value?.id !== conversationId) return
    applyConversation(next)
    await loadSmartTemplates()
    notice.value = '模板已切换，简历内容和内容版本保持不变。'
  } catch (reason) {
    pageError.value = errorMessage(reason, '模板切换失败，原版式保持不变')
    await load()
  } finally {
    templatePending.value = false
  }
}

function cloneDesign(value: ResumeDesignSettings): ResumeDesignSettings {
  return JSON.parse(JSON.stringify(value)) as ResumeDesignSettings
}

function designStorageKey(): string {
  return `jobproof:ai-resume:design:${designIdentity}`
}

function openWorkbenchView(view: WorkbenchView): void {
  workbenchView.value = view
  mobilePane.value = 'conversation'
  if (view === 'edit') moduleEditorOpen.value = true
  void nextTick(queueScrollSurfaceRefresh)
}

function selectMobilePane(pane: MobilePane): void {
  mobilePane.value = pane
  void nextTick(queueScrollSurfaceRefresh)
}

function updateDesign<K extends keyof ResumeDesignSettings>(key: K, value: ResumeDesignSettings[K] | string | number): void {
  if (!activeDesign.value || templatePending.value || disposed) return
  designDraft.value = { ...cloneDesign(activeDesign.value), [key]: value as ResumeDesignSettings[K] }
  designSaver.changed(500)
}

function changeDesignPreset(variantCode: string): void {
  const preset = activeTemplate.value?.presets.find((item) => item.variantCode === variantCode)
  if (!preset || variantCode === activeVariant.value || templatePending.value || disposed) return
  designVariant.value = variantCode
  designDraft.value = cloneDesign(preset.settings)
  designSaver.changed(500)
}

function toggleDesignSection(key: string, visible: boolean): void {
  const hidden = new Set(activeDesign.value?.hiddenSections ?? [])
  if (visible) hidden.delete(key); else hidden.add(key)
  updateDesign('hiddenSections', Array.from(hidden))
}

function moveDesignSection(key: string, direction: -1 | 1): void {
  const order = [...(activeDesign.value?.sectionOrder ?? designSections.value.map((item) => item.key))]
  const index = order.indexOf(key)
  const target = index + direction
  if (index < 0 || target < 0 || target >= order.length) return
  ;[order[index], order[target]] = [order[target], order[index]]
  updateDesign('sectionOrder', order)
}

const designSaver = createDraftSaveQueue({
  identity: () => designIdentity,
  snapshot: () => {
    if (!conversation.value || !activeTemplate.value || !designDraft.value) return null
    return { conversationId: conversation.value.id,
      masterId: conversation.value.masterId, templateId: activeTemplate.value.templateId, variant: activeVariant.value,
      settings: cloneDesign(designDraft.value), version: designVersion.value }
  },
  recovery: {
    key: designStorageKey,
    serialize: (snapshot) => JSON.stringify({ settings: snapshot.settings, variantCode: snapshot.variant }),
  },
  save: (snapshot) => saveAiResumeDesign(snapshot.conversationId, snapshot.templateId,
    snapshot.variant, snapshot.settings, snapshot.version),
  accept: async (saved, snapshot, context) => {
    if (!conversation.value) return
    designVersion.value = saved.versionNo
    conversation.value.activeDesign = saved
    if (context.isLatest()) {
      designDraft.value = cloneDesign(saved.settings)
      designVariant.value = saved.variantCode
    }
    const index = smartTemplates.value.findIndex((item) => item.templateId === saved.templateId)
    if (index >= 0) smartTemplates.value[index] = { ...smartTemplates.value[index], design: saved }
    try {
      const current = await fetchCurrentResumeLayout(snapshot.masterId)
      if (context.isCurrent() && conversation.value) {
        conversation.value = { ...conversation.value, layout: current.layout ?? null }
      }
    } catch {
      if (context.isCurrent()) {
        pageError.value = '设计已保存，但 PDF 容量状态暂未刷新；重新打开工作台后会自动恢复。'
      }
    }
  },
  onState: (state) => { designSaveState.value = state },
  onError: (reason) => { pageError.value = errorMessage(reason, '设计保存失败，当前修改仍保留，请重试后再导出') },
  onRecoveryError: (operation) => {
    if (operation === 'read') pageError.value = '无法读取本地设计副本，请确认浏览器允许本地存储。'
    if (operation === 'write') pageError.value = '浏览器无法保留本地设计副本，请在离开前确认服务端保存成功。'
  },
})

function persistDesign(): Promise<boolean> {
  return designSaver.flush()
}

async function loadSmartTemplates(): Promise<void> {
  if (!conversation.value || disposed) return
  const conversationId = conversation.value.id
  templatesPending.value = true
  try {
    const templates = await listAiResumeSmartTemplates(conversationId)
    if (!disposed && conversation.value?.id === conversationId) smartTemplates.value = templates
  }
  catch (reason) { pageError.value = errorMessage(reason, '12 款智能模板读取失败') }
  finally { templatesPending.value = false }
}

async function load(showSpinner = false): Promise<void> {
  if (disposed) return
  const conversationId = String(route.params.conversationId)
  if (showSpinner) loading.value = true
  try {
    const next = await fetchAiResume(conversationId)
    if (disposed || String(route.params.conversationId) !== conversationId) return
    applyConversation(next)
    pageError.value = ''
  } catch (reason) {
    pageError.value = errorMessage(reason, 'AI 工作台读取失败')
  } finally {
    loading.value = false
  }
}

function selectCard(card: AiResumeCard): void {
  selectedCardId.value = card.id
  moduleEditorOpen.value = true
  mobilePane.value = 'conversation'
  void nextTick(() => document.querySelector<HTMLElement>('.card-editor__field')?.focus())
}

function updateField(card: AiResumeCard, key: string, value: unknown): void {
  payloads[card.id] ||= {}
  payloads[card.id][key] = value
  dirty.add(card.id)
  localRevisions[card.id] = (localRevisions[card.id] ?? 0) + 1
  draftStates[card.id] = 'waiting'
  scheduleDraft(card.id)
  void nextTick(queueScrollSurfaceRefresh)
}

function scheduleDraft(cardId: string, delay = 1000): void {
  const previous = timers.get(cardId)
  if (previous) window.clearTimeout(previous)
  timers.set(cardId, window.setTimeout(() => void saveDraft(cardId), delay))
}

function saveDraft(cardId: string): Promise<boolean> {
  const timer = timers.get(cardId)
  if (timer) window.clearTimeout(timer)
  timers.delete(cardId)
  const pending = draftFlights.get(cardId)
  if (pending) return pending
  if (confirming.has(cardId)) {
    scheduleDraft(cardId, 250)
    return Promise.resolve(false)
  }
  const flight = drainCardDraft(cardId).finally(() => { draftFlights.delete(cardId) })
  draftFlights.set(cardId, flight)
  return flight
}

async function drainCardDraft(cardId: string): Promise<boolean> {
  while (dirty.has(cardId)) {
    const card = conversation.value?.cards.find((item) => item.id === cardId)
    if (disposed || !card || !conversation.value) return false
    const conversationId = conversation.value.id
    const branchId = conversation.value.activeBranchId
    draftStates[cardId] = 'saving'
    const capturedRevision = localRevisions[cardId]
    const capturedPayload = clonePayload(payloads[cardId])
    try {
      const next = await saveAiResumeCardDraft(conversationId, cardId, capturedPayload, card.versionNo)
      if (disposed || conversation.value?.id !== conversationId || conversation.value.activeBranchId !== branchId) return false
      if (localRevisions[cardId] === capturedRevision) dirty.delete(cardId)
      applyConversation(next)
      draftStates[cardId] = dirty.has(cardId) ? 'waiting' : 'saved'
    } catch (reason) {
      if (!disposed && conversation.value?.id === conversationId && conversation.value.activeBranchId === branchId) {
        draftStates[cardId] = 'error'
        pageError.value = errorMessage(reason, '卡片草稿保存失败')
        if (isApiClientError(reason) && reason.category === 'CONFLICT') await load()
      }
      return false
    }
  }
  return !disposed
}

async function submitCard(card: AiResumeCard): Promise<void> {
  if (disposed || confirming.has(card.id)) return
  pageError.value = ''
  notice.value = ''
  const timer = timers.get(card.id)
  if (timer) window.clearTimeout(timer)
  timers.delete(card.id)
  if ((dirty.has(card.id) || draftFlights.has(card.id)) && !(await saveDraft(card.id))) return
  const fresh = conversation.value?.cards.find((item) => item.id === card.id)
  if (!fresh || !conversation.value || disposed) return
  const conversationId = conversation.value.id
  const branchId = conversation.value.activeBranchId
  const capturedRevision = localRevisions[card.id]
  confirming.add(card.id)
  draftStates[card.id] = 'saving'
  try {
    const next = await submitAiResumeCard(conversationId, card.id, clonePayload(payloads[card.id]), fresh.versionNo)
    if (disposed || conversation.value?.id !== conversationId || conversation.value.activeBranchId !== branchId) return
    if (localRevisions[card.id] === capturedRevision) dirty.delete(card.id)
    applyConversation(next)
    draftStates[card.id] = dirty.has(card.id) ? 'waiting' : 'saved'
    if (dirty.has(card.id)) scheduleDraft(card.id, 250)
    notice.value = `“${cardMeta[card.cardType]?.label ?? card.cardType}”已确认，并生成新的不可变修订。`
    guidedOverrideCardId.value = ''
    if (isStructuredCard(card)) {
      repeatPromptType.value = card.cardType
    } else {
      const following = suggestedCard.value
      if (following && following.id !== card.id) selectedCardId.value = following.id
    }
    await nextTick()
    scheduleChatScroll(true)
  } catch (reason) {
    draftStates[card.id] = 'error'
    pageError.value = errorMessage(reason, '卡片提交失败，正式简历没有变化')
  } finally {
    confirming.delete(card.id)
  }
}

async function skipCard(card: AiResumeCard): Promise<void> {
  if (disposed || !conversation.value || card.cardType !== 'LANGUAGES' || confirming.has(card.id)) return
  pageError.value = ''
  notice.value = ''
  const timer = timers.get(card.id)
  if (timer) window.clearTimeout(timer)
  timers.delete(card.id)
  if ((dirty.has(card.id) || draftFlights.has(card.id)) && !(await saveDraft(card.id))) return
  const fresh = conversation.value?.cards.find((item) => item.id === card.id)
  if (!fresh || !conversation.value || disposed) return
  const conversationId = conversation.value.id
  const branchId = conversation.value.activeBranchId
  const capturedRevision = localRevisions[card.id]
  confirming.add(card.id)
  clearDescriptionStates(card.id)
  draftStates[card.id] = 'saving'
  try {
    const next = await skipAiResumeCard(conversationId, card.id, fresh.versionNo)
    if (disposed || conversation.value?.id !== conversationId || conversation.value.activeBranchId !== branchId) return
    if (localRevisions[card.id] === capturedRevision) dirty.delete(card.id)
    applyConversation(next)
    draftStates[card.id] = dirty.has(card.id) ? 'waiting' : 'saved'
    if (dirty.has(card.id)) scheduleDraft(card.id, 250)
    guidedOverrideCardId.value = ''
    repeatPromptType.value = ''
    notice.value = '已跳过语言能力；之后仍可在“编辑”视图中补充。'
    const following = suggestedCard.value
    if (following) selectedCardId.value = following.id
    await nextTick()
    scheduleChatScroll(true)
  } catch (reason) {
    draftStates[card.id] = 'error'
    pageError.value = errorMessage(reason, '语言能力跳过失败')
  } finally {
    confirming.delete(card.id)
  }
}

function addAnotherFromPrompt(): void {
  const card = editableCards.value.find((item) => item.cardType === repeatPromptType.value)
  if (!card) return
  guidedOverrideCardId.value = card.id
  repeatPromptType.value = ''
  addStructuredItem(card)
}

function continueAfterRepeat(): void {
  repeatPromptType.value = ''
  guidedOverrideCardId.value = ''
  const following = suggestedCard.value
  if (following) selectedCardId.value = following.id
  void nextTick(() => scheduleChatScroll(true))
}

function chooseJob(card: AiResumeCard, selection: JobTaxonomySelection): void {
  updateField(card, 'targetJob', selection.job.displayName)
  updateField(card, 'taxonomyNodeId', selection.job.id)
  updateField(card, 'taxonomyGroupId', selection.groupId)
  updateField(card, 'taxonomyCategoryId', selection.categoryId)
  updateField(card, 'taxonomyCode', selection.job.code)
  updateField(card, 'catalogOccupationCode', selection.job.catalogOccupationCode)
}

function cardSubmitDisabled(card: AiResumeCard): boolean {
  if (card.cardType === 'TARGET_JOB') return !String(payloads[card.id]?.taxonomyNodeId ?? '').trim()
  return Boolean(cardSubmitIssue(card))
}

function cardSubmitIssue(card: AiResumeCard): string {
  if (card.cardType === 'TARGET_JOB' && !String(payloads[card.id]?.taxonomyNodeId ?? '').trim()) {
    return '请先从标准岗位分类中选择一个岗位。'
  }
  if (card.cardType === 'CERTIFICATES' || card.cardType === 'HONORS') {
    return credentialDescriptionIssue(
      structuredItems(card), card.cardType === 'CERTIFICATES' ? '证书与资质' : '荣誉奖项',
    )
  }
  if (card.cardType === 'LANGUAGES') {
    const hasLanguage = structuredItems(card).some((item) => String(item.language ?? '').trim())
    if (!hasLanguage) return '请先选择一种语言，或使用“暂时跳过”。'
  }
  if (card.cardType === 'CONTACT') return contactSubmitIssue(payloads[card.id] ?? {})
  return ''
}

async function grantConsent(): Promise<void> {
  consentPending.value = true
  try {
    await grantAiResumeConsent()
    await load()
    notice.value = 'AI 授权已记录。撤销后会停止新的模型调用。'
  } catch (reason) {
    pageError.value = errorMessage(reason, 'AI 授权失败')
  } finally {
    consentPending.value = false
  }
}

async function sendMessage(): Promise<void> {
  const text = messageText.value.trim()
  if (!text || !conversation.value || messagePending.value || aiFieldGenerationPending.value) return
  if (conversation.value.aiAvailable && conversation.value.consent.status !== 'GRANTED') {
    pageError.value = '首次调用 AI 前需要明确授权。'
    return
  }
  const clientId = crypto.randomUUID()
  const now = new Date().toISOString()
  const localUserId = `local-user-${clientId}`
  const localAssistantId = `local-assistant-${clientId}`
  let accepted = false
  let terminalEvent = false
  let streamError = ''
  messagePending.value = true
  pageError.value = ''
  notice.value = ''
  messageText.value = ''
  currentMessageRequestId.value = clientId
  transientMessages.value.push({
    id: localUserId, sequence: Number.MAX_SAFE_INTEGER - 1, role: 'USER', messageType: 'TEXT', status: 'PENDING',
    content: text, inputTokens: 0, outputTokens: 0, createdAt: now, completedAt: null, transient: true, phase: 'sending',
  })
  if (conversation.value.aiAvailable) {
    transientMessages.value.push({
      id: localAssistantId, sequence: Number.MAX_SAFE_INTEGER, role: 'ASSISTANT', messageType: 'TEXT', status: 'PENDING',
      content: '', inputTokens: 0, outputTokens: 0, createdAt: now, completedAt: null, transient: true, phase: 'loading',
    })
  }
  await nextTick()
  scheduleChatScroll(true)
  try {
    if (conversation.value.aiAvailable) {
      generationController = new AbortController()
      await streamAiResume(conversation.value.id, text, clientId, (event) => {
        const payload = streamPayload(event.data)
        if (event.type === 'user.accepted' && payload.message) {
          accepted = true
          replaceTransient(localUserId, { ...payload.message, transient: true })
        } else if (event.type === 'assistant.started') {
          updateTransient(localAssistantId, { phase: 'loading', status: 'PENDING' })
        } else if (event.type === 'assistant.progress') {
          updateTransient(localAssistantId, {
            phase: 'loading', status: 'PENDING', content: typeof payload.message === 'string' ? payload.message : '',
          })
        } else if (event.type === 'assistant.delta' && typeof payload.delta === 'string') {
          const current = transientMessages.value.find((message) => message.id === localAssistantId)
          updateTransient(localAssistantId, {
            phase: 'streaming', status: 'PENDING', content: `${current?.content ?? ''}${payload.delta}`,
          })
        } else if (event.type === 'assistant.completed' && payload.message) {
          terminalEvent = true
          replaceTransient(localAssistantId, { ...payload.message, transient: true })
        } else if (event.type === 'change-set.created' && payload.changeSet && conversation.value) {
          const next = payload.changeSet as AiResumeChangeSet
          const index = conversation.value.changeSets.findIndex((changeSet) => changeSet.id === next.id)
          if (index >= 0) conversation.value.changeSets.splice(index, 1, next)
          else conversation.value.changeSets.push(next)
        } else if (event.type === 'assistant.cancelled' && payload.message) {
          terminalEvent = true
          replaceTransient(localAssistantId, { ...payload.message, transient: true })
        } else if (event.type === 'assistant.failed') {
          terminalEvent = true
          streamError = typeof payload.detail === 'string' ? payload.detail : 'AI 回复失败，额度已返还'
          if (payload.message) replaceTransient(localAssistantId, { ...payload.message, transient: true })
        } else if (event.type === 'request.failed') {
          terminalEvent = true
          streamError = typeof payload.message === 'string' ? payload.message : '消息发送失败'
        }
        void nextTick(() => scheduleChatScroll(event.type !== 'assistant.delta', false))
      }, generationController.signal)
      if (!terminalEvent) throw new Error('AI 流式响应意外中断，请重试')
      if (streamError) pageError.value = streamError
      await load()
      transientMessages.value = transientMessages.value.filter((message) => !message.id.includes(clientId))
    } else {
      const saved = await addAiResumeMessage(conversation.value.id, text, clientId)
      accepted = true
      replaceTransient(localUserId, { ...saved, transient: true })
      await load()
      transientMessages.value = transientMessages.value.filter((message) => message.id !== saved.id)
      notice.value = '问题已保存到长期对话。AI 不可用，因此没有生成回复或扣除额度。'
    }
  } catch (reason) {
    pageError.value = errorMessage(reason, '消息发送失败，正式简历没有变化')
    transientMessages.value = transientMessages.value.filter((message) => !message.id.includes(clientId))
    if (!accepted && !messageText.value) messageText.value = text
    await load()
  } finally {
    generationController = null
    messagePending.value = false
    cancelPending.value = false
    currentMessageRequestId.value = ''
  }
}

function onMessageComposerKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  void sendMessage()
}

function streamPayload(value: unknown): Record<string, any> {
  return value && typeof value === 'object' ? value as Record<string, any> : {}
}

function replaceTransient(id: string, message: DisplayMessage): void {
  const index = transientMessages.value.findIndex((item) => item.id === id)
  if (index >= 0) transientMessages.value.splice(index, 1, message)
}

function updateTransient(id: string, patch: Partial<DisplayMessage>): void {
  const message = transientMessages.value.find((item) => item.id === id)
  if (message) Object.assign(message, patch)
}

function scrollMetrics(element: HTMLElement): ScrollMetrics {
  return {
    scrollTop: element.scrollTop,
    scrollHeight: element.scrollHeight,
    clientHeight: element.clientHeight,
  }
}

function assignEdges(target: { canScrollUp: boolean; canScrollDown: boolean }, element: HTMLElement | null): void {
  const edges = element ? measureScrollEdges(scrollMetrics(element)) : { canScrollUp: false, canScrollDown: false }
  target.canScrollUp = edges.canScrollUp
  target.canScrollDown = edges.canScrollDown
}

function updateConversationScrollState(): void {
  const element = conversationScroll.value
  if (!element) {
    assignEdges(conversationEdges, null)
    showConversationReturn.value = false
    return
  }
  const metrics = scrollMetrics(element)
  conversationAutoFollow.value = nextAutoFollowState(
    conversationAutoFollow.value,
    previousConversationScrollTop,
    metrics,
  )
  previousConversationScrollTop = element.scrollTop
  assignEdges(conversationEdges, element)
  showConversationReturn.value = shouldShowReturnToBottom(metrics)
}

function updatePreviewScrollState(): void {
  assignEdges(previewEdges, previewScroll.value)
}

function updateToolScrollState(): void {
  assignEdges(toolEdges, toolScroll.value)
}

function refreshScrollSurfaces(): void {
  surfaceFrame = 0
  updateConversationScrollState()
  updatePreviewScrollState()
  updateToolScrollState()
}

function queueScrollSurfaceRefresh(): void {
  if (surfaceFrame) return
  surfaceFrame = window.requestAnimationFrame(refreshScrollSurfaces)
}

function connectScrollSurfaceObserver(): void {
  scrollSurfaceObserver?.disconnect()
  scrollSurfaceObserver ||= new MutationObserver(queueScrollSurfaceRefresh)
  for (const element of [conversationScroll.value, previewScroll.value, toolScroll.value]) {
    if (element) {
      scrollSurfaceObserver.observe(element, { childList: true, subtree: true, characterData: true })
    }
  }
  queueScrollSurfaceRefresh()
}

function scrollBehavior(smooth: boolean): ScrollBehavior {
  return smooth && !window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'auto'
}

function scrollConversationToBottom(smooth = true): void {
  const element = conversationScroll.value
  if (!element) return
  conversationAutoFollow.value = true
  element.scrollTo({ top: element.scrollHeight, behavior: scrollBehavior(smooth) })
  queueScrollSurfaceRefresh()
}

function scheduleChatScroll(smooth = false, force = true): void {
  if (!force && !conversationAutoFollow.value) {
    queueScrollSurfaceRefresh()
    return
  }
  if (scrollFrame) return
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0
    scrollConversationToBottom(smooth)
  })
}

function splitBounds(): { min: number; max: number } {
  const width = workbenchGrid.value?.getBoundingClientRect().width ?? window.innerWidth
  const min = Math.max(34, (420 / width) * 100)
  const max = Math.min(66, ((width - 360) / width) * 100)
  return min <= max ? { min, max } : { min: 50, max: 50 }
}

function setSplitRatio(value: number, persist = false): void {
  const { min, max } = splitBounds()
  splitRatio.value = Math.round(Math.min(max, Math.max(min, value)) * 10) / 10
  if (persist) window.localStorage.setItem(splitStorageKey, String(splitRatio.value))
}

function updateSplitFromPointer(event: PointerEvent): void {
  const rect = workbenchGrid.value?.getBoundingClientRect()
  if (!rect || rect.width <= 0) return
  setSplitRatio(((event.clientX - rect.left) / rect.width) * 100)
}

function startSplitDrag(event: PointerEvent): void {
  if (event.button !== 0 || window.matchMedia('(max-width: 860px)').matches) return
  splitDragging.value = true
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  updateSplitFromPointer(event)
}

function moveSplitDrag(event: PointerEvent): void {
  if (splitDragging.value) updateSplitFromPointer(event)
}

function stopSplitDrag(event: PointerEvent): void {
  if (!splitDragging.value) return
  splitDragging.value = false
  const target = event.currentTarget as HTMLElement
  if (target.hasPointerCapture(event.pointerId)) target.releasePointerCapture(event.pointerId)
  window.localStorage.setItem(splitStorageKey, String(splitRatio.value))
}

function resetSplitRatio(): void {
  setSplitRatio(40, true)
}

function resizeSplitWithKeyboard(event: KeyboardEvent): void {
  if (!['ArrowLeft', 'ArrowRight', 'Home'].includes(event.key)) return
  event.preventDefault()
  if (event.key === 'Home') resetSplitRatio()
  else setSplitRatio(splitRatio.value + (event.key === 'ArrowLeft' ? -2 : 2), true)
}

async function cancelMessage(): Promise<void> {
  if (!conversation.value || !currentMessageRequestId.value || cancelPending.value) return
  cancelPending.value = true
  pageError.value = ''
  try {
    const result = await cancelAiResumeResponse(conversation.value.id, currentMessageRequestId.value)
    notice.value = result.accepted
      ? '已请求停止生成；模型返回的迟到内容会被丢弃，额度将返还。'
      : '本次生成已经结束，无需取消。'
  } catch (reason) {
    pageError.value = errorMessage(reason, '停止生成失败')
    cancelPending.value = false
  }
}

function choosePhoto(): void {
  photoInput.value?.click()
}

async function uploadPhoto(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!conversation.value || !file || photoPending.value) return
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 1024 * 1024) {
    pageError.value = '照片仅支持 1MB 以内的 PNG 或 JPEG。'
    return
  }
  photoPending.value = true
  pageError.value = ''
  try {
    applyConversation(await uploadAiResumePhoto(conversation.value.id, file))
    notice.value = '照片已保存到私有存储，并生成新的不可变修订。'
  } catch (reason) {
    pageError.value = errorMessage(reason, '照片上传失败，原照片保持不变')
  } finally {
    photoPending.value = false
  }
}

async function removePhoto(): Promise<void> {
  if (!conversation.value || photoPending.value) return
  photoPending.value = true
  pageError.value = ''
  try {
    applyConversation(await removeAiResumePhoto(conversation.value.id))
    notice.value = '当前版本已隐藏照片，并生成新的不可变修订。'
  } catch (reason) {
    pageError.value = errorMessage(reason, '照片移除失败')
  } finally {
    photoPending.value = false
  }
}

function previewSection(section: string): void {
  const type = ({ summary: 'SUMMARY', education: 'EDUCATION', experience: 'EXPERIENCE', projects: 'PROJECTS',
    organizations: 'ORGANIZATIONS', skills: 'SKILLS', certificates: 'CERTIFICATES', honors: 'HONORS', languages: 'LANGUAGES' } as Record<string, string>)[section]
  const card = editableCards.value.find((item) => item.cardType === type)
  if (card) {
    openWorkbenchView('edit')
    selectCard(card)
  }
}

function draftLabel(card: AiResumeCard): string {
  const state = draftStates[card.id]
  if (state === 'waiting') return '等待自动保存'
  if (state === 'saving') return '正在保存'
  if (state === 'saved') return '草稿已保存'
  if (state === 'error') return '保存失败'
  if (card.status === 'CONFIRMED') return '已确认'
  if (card.status === 'SKIPPED') return '已跳过'
  return '尚未确认'
}

function connectEvents(): void {
  if (!conversation.value) return
  eventSource?.close()
  const url = `/api/v1/ai-resume/conversations/${encodeURIComponent(conversation.value.id)}/events?afterSequence=${conversation.value.lastSequence}`
  eventSource = new EventSource(url)
  for (const type of ['card.confirmed', 'message.completed', 'message.failed', 'message.cancelled',
    'change-set.created', 'change-item.applied', 'change-item.rejected', 'change-item.stale', 'change-item.undone']) {
    eventSource.addEventListener(type, () => {
      window.clearTimeout(reloadTimer)
      reloadTimer = window.setTimeout(() => void load(), 180)
    })
  }
}

async function openTool(tool: WorkbenchTool): Promise<void> {
  activeTool.value = tool
  toolError.value = ''
  branchDiff.value = null
  if (!conversation.value) return
  toolPending.value = 'load'
  try {
    if (tool === 'history') revisions.value = await listAiResumeRevisions(conversation.value.id)
    if (tool === 'privacy') {
      writingStyle.value = (await fetchAiResumeWritingPreference(conversation.value.id)).code
      historyDeletionArmed.value = false
    }
    if (tool === 'branches') {
      branches.value = await listAiResumeBranches(conversation.value.id)
    }
  } catch (reason) {
    toolError.value = errorMessage(reason, '工具数据读取失败')
  } finally {
    toolPending.value = ''
    void nextTick(queueScrollSurfaceRefresh)
  }
}

async function updateWritingStyle(value: string | number): Promise<void> {
  if (!conversation.value) return
  const code = String(value) as AiWritingStyleCode
  toolPending.value = 'privacy-style'
  toolError.value = ''
  try {
    writingStyle.value = (await setAiResumeWritingPreference(conversation.value.id, code)).code
    notice.value = '写作风格已更新，后续 AI 调用将使用该受控偏好。'
  } catch (reason) {
    toolError.value = errorMessage(reason, '写作风格更新失败')
  } finally {
    toolPending.value = ''
  }
}

async function toggleCareerLibraryEvidence(): Promise<void> {
  if (!conversation.value || careerEvidencePending.value) return
  careerEvidencePending.value = true
  pageError.value = ''
  try {
    const preference = await setCareerLibraryEvidencePreference(
      conversation.value.id,
      !conversation.value.careerLibraryEvidence.enabled,
    )
    conversation.value = { ...conversation.value, careerLibraryEvidence: preference }
    notice.value = preference.enabled
      ? `已允许本会话引用相关的已确认资料（快照 v${preference.snapshotVersion}）。`
      : '已关闭本会话的求职资料引用。'
  } catch (reason) {
    pageError.value = errorMessage(reason, '求职资料引用设置失败')
  } finally {
    careerEvidencePending.value = false
  }
}

async function revokeConsent(): Promise<void> {
  toolPending.value = 'privacy-consent'
  toolError.value = ''
  try {
    await revokeAiResumeConsent()
    await load()
    notice.value = 'AI 授权已撤销；正式简历内容保持不变。'
  } catch (reason) {
    toolError.value = errorMessage(reason, 'AI 授权撤销失败')
  } finally {
    toolPending.value = ''
  }
}

async function deleteHistory(): Promise<void> {
  if (!conversation.value) return
  if (!historyDeletionArmed.value) {
    historyDeletionArmed.value = true
    return
  }
  toolPending.value = 'privacy-delete'
  toolError.value = ''
  try {
    const result = await deleteAiResumeHistory(conversation.value.id)
    await load()
    historyDeletionArmed.value = false
    notice.value = `已清除 ${result.messageBodiesDeleted} 条消息正文和 ${result.pendingCandidatesDeleted} 条旧版未决记录；正式简历未删除。`
  } catch (reason) {
    toolError.value = errorMessage(reason, 'AI 历史清除失败')
  } finally {
    toolPending.value = ''
  }
}

async function refreshBranches(): Promise<void> {
  if (conversation.value) branches.value = await listAiResumeBranches(conversation.value.id)
}

async function createLanguageBranch(): Promise<void> {
  if (!conversation.value) return
  const active = branches.value.find((branch) => branch.active)
  const language = active?.languageCode === 'en-US' ? 'zh-CN' : 'en-US'
  toolPending.value = 'branch-create'
  try {
    await createAiResumeLanguageBranch(conversation.value.id, language)
    await refreshBranches()
    notice.value = '语言子分支已建立并标记待翻译复核；未把原文伪装成已完成翻译。'
  } catch (reason) { toolError.value = errorMessage(reason, '语言分支创建失败') }
  finally { toolPending.value = '' }
}

async function activateBranch(branch: AiResumeBranch): Promise<void> {
  if (!conversation.value || branch.active) return
  toolPending.value = `switch-${branch.id}`
  try {
    await switchAiResumeBranch(conversation.value.id, branch.id)
    await Promise.all([load(), refreshBranches()])
    notice.value = `已切换到“${branch.title}”。`
  } catch (reason) { toolError.value = errorMessage(reason, '分支切换失败') }
  finally { toolPending.value = '' }
}

async function inspectBranch(branch: AiResumeBranch): Promise<void> {
  if (!conversation.value) return
  toolPending.value = `diff-${branch.id}`
  try { branchDiff.value = await fetchAiResumeBranchDiff(conversation.value.id, branch.id) }
  catch (reason) { toolError.value = errorMessage(reason, '分支差异读取失败') }
  finally { toolPending.value = '' }
}

async function syncBranch(branch: AiResumeBranch): Promise<void> {
  if (!conversation.value) return
  toolPending.value = `sync-${branch.id}`
  try {
    await syncAiResumeBranch(conversation.value.id, branch)
    await Promise.all([load(), refreshBranches()])
    branchDiff.value = null
    notice.value = `已显式同步“${branch.title}”并生成新修订。`
  } catch (reason) { toolError.value = errorMessage(reason, '分支同步失败') }
  finally { toolPending.value = '' }
}

async function translateBranch(branch: AiResumeBranch): Promise<void> {
  if (!conversation.value || !conversation.value.aiAvailable || conversation.value.consent.status !== 'GRANTED') return
  toolPending.value = `translate-${branch.id}`
  toolError.value = ''
  try {
    await translateAiResumeLanguageBranch(conversation.value.id, branch.id)
    await refreshBranches()
    notice.value = `“${branch.title}”已生成待确认译文，请核对专有名称和全部事实后确认。`
  } catch (reason) {
    toolError.value = errorMessage(reason, '译文生成失败，语言分支保持不变')
  } finally {
    toolPending.value = ''
  }
}

async function confirmBranchTranslation(branch: AiResumeBranch): Promise<void> {
  if (!conversation.value) return
  toolPending.value = `confirm-translation-${branch.id}`
  toolError.value = ''
  try {
    await confirmAiResumeLanguageBranch(conversation.value.id, branch)
    await Promise.all([load(), refreshBranches()])
    notice.value = `“${branch.title}”译文已确认，并生成新的不可变修订。`
  } catch (reason) {
    toolError.value = errorMessage(reason, '译文确认失败')
  } finally {
    toolPending.value = ''
  }
}

async function restoreRevision(revision: AiResumeRevision): Promise<void> {
  if (!conversation.value) return
  toolPending.value = `restore-${revision.id}`
  try {
    await restoreAiResumeRevision(conversation.value.id, revision.id)
    await Promise.all([load(), listAiResumeRevisions(conversation.value.id).then((value) => { revisions.value = value })])
    notice.value = `已把第 ${revision.revisionNo} 版恢复为新的修订，旧历史仍保留。`
  } catch (reason) { toolError.value = errorMessage(reason, '版本恢复失败') }
  finally { toolPending.value = '' }
}

function triggerBrowserDownload(blob: Blob, filename: string): void {
  const href = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = href
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(href), 1000)
}

async function confirmPdfExport(): Promise<void> {
  if (!conversation.value || pdfPending.value || templatePending.value || disposed) return
  const conversationId = conversation.value.id
  pdfPending.value = true
  pdfError.value = ''
  notice.value = ''
  if (!(await persistDesign()) || disposed || conversation.value?.id !== conversationId) {
    pdfError.value = '设计设置尚未成功保存，本次没有生成 PDF。'
    pdfPending.value = false
    return
  }
  if (pdfOverflowBlocked.value) {
    pdfError.value = `${pdfOverflowSummary.value}，本次没有生成 PDF。`
    pdfConfirmOpen.value = true
    pdfPending.value = false
    return
  }
  pdfConfirmOpen.value = false

  pdfPollAbort?.abort()
  pdfPollAbort = new AbortController()
  try {
    const exportMode = pdfExportMode.value
    const started = await exportAiResumePdf(conversationId, exportMode)
    pdfTask.value = started
    const finished = await pollTask(started.id, (next) => { pdfTask.value = next }, pdfPollAbort.signal)
    pdfTask.value = finished
    const blocked = pdfDownloadDisabledReason(finished)
    if (finished.status !== 'SUCCEEDED' || blocked) {
      pdfError.value = finished.failureReason || blocked || 'PDF 导出任务没有生成可下载文件。'
      return
    }
    const file = await downloadPrivateFile(finished.fileId!.trim())
    triggerBrowserDownload(file.blob, file.filename || pdfFallbackFilename(exportMode))
    notice.value = exportMode === 'ANONYMOUS'
      ? '匿名 PDF 已生成并开始下载；姓名、照片和联系方式已从独立快照中移除，正式简历未改变。'
      : '正式 PDF 已按当前确认内容、模板和设计生成并开始下载。你可以继续编辑后再次导出。'
    await load()
  } catch (reason) {
    if (!isAbortError(reason)) pdfError.value = errorMessage(reason, 'PDF 生成失败，未下载任何文件')
  } finally {
    pdfPending.value = false
  }
}

function contentSummary(content: Record<string, unknown>): string {
  const intentions = content.intentions as Record<string, unknown> | undefined
  return String(intentions?.targetJob || content.summary || '尚未填写摘要').slice(0, 72)
}

function branchTypeLabel(branch: AiResumeBranch): string {
  return branch.branchType === 'BASE' ? '基础' : branch.branchType === 'JOB' ? '岗位' : '语言'
}

function translationStatus(branch: AiResumeBranch): string {
  const value = branch.reviewMetadata?.translationStatus
  return branch.branchType === 'LANGUAGE' && typeof value === 'string' ? value : ''
}

function branchStatusLabel(branch: AiResumeBranch): string {
  if (branch.syncRequired) return '基础版本有更新，待确认同步'
  const translation = translationStatus(branch)
  if (translation === 'NOT_STARTED') {
    return conversation.value?.aiAvailable
      ? '尚未生成译文'
      : '尚未生成译文，等待可用 AI 通道'
  }
  if (translation === 'AWAITING_CONFIRMATION') return '译文已生成，待人工确认'
  if (translation === 'CONFIRMED') return '译文已确认'
  return branch.status === 'REVIEWING' ? '待人工复核' : '已同步'
}

function unconfirmedProperNames(branch: AiResumeBranch): string[] {
  const value = branch.reviewMetadata?.unconfirmedProperNames
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string' && Boolean(item.trim())) : []
}

watch(activeTool, () => void nextTick(connectScrollSurfaceObserver))

onMounted(async () => {
  const savedSplitRatio = Number(window.localStorage.getItem(splitStorageKey))
  if (Number.isFinite(savedSplitRatio) && savedSplitRatio > 0) setSplitRatio(savedSplitRatio)
  await load(true)
  if (disposed) return
  await loadSmartTemplates()
  if (disposed) return
  if (route.query.source === 'existing') {
    notice.value = '请直接在对话框粘贴需要整理的简历内容，并说明希望修改的模块；AI 会生成逐条待确认修改。'
  }
  connectEvents()
  await nextTick()
  if (disposed) return
  connectScrollSurfaceObserver()
  window.addEventListener('resize', queueScrollSurfaceRefresh)
  scheduleChatScroll()
  queueScrollSurfaceRefresh()
})

onBeforeUnmount(() => {
  disposed = true
  generationController?.abort()
  descriptionControllers.forEach((controller) => controller.abort())
  descriptionControllers.clear()
  pdfPollAbort?.abort()
  eventSource?.close()
  scrollSurfaceObserver?.disconnect()
  window.removeEventListener('resize', queueScrollSurfaceRefresh)
  timers.forEach((timer) => window.clearTimeout(timer))
  window.clearTimeout(reloadTimer)
  designSaver.dispose()
  window.cancelAnimationFrame(scrollFrame)
  window.cancelAnimationFrame(surfaceFrame)
})
</script>

<template>
  <main class="ai-workbench">
    <header class="workbench-bar">
      <RouterLink class="icon-btn" to="/resumes" title="返回简历列表"><AppIcon name="chevron-left" :size="18" /></RouterLink>
      <div class="workbench-bar__title">
        <strong>{{ conversation?.resume.title || 'AI 简历工作台' }}</strong>
        <span v-if="conversation">{{ ready ? '第一版已就绪' : '正在收集事实' }} · v{{ conversation.resume.version }}</span>
      </div>
      <div class="workbench-bar__tools">
        <span class="quota" title="本月剩余 AI 次数"><AppIcon name="sparkles" :size="14" />{{ quotaText }}</span>
        <button v-if="conversation" class="icon-btn tool-trigger" type="button" title="简历分支" @click="openTool('branches')"><AppIcon name="folder" :size="15" /></button>
        <button v-if="conversation" class="icon-btn tool-trigger" type="button" title="版本历史" @click="openTool('history')"><AppIcon name="clock" :size="15" /></button>
        <button v-if="conversation" class="icon-btn tool-trigger" type="button" title="AI 隐私与偏好" @click="openTool('privacy')"><AppIcon name="shield" :size="15" /></button>
        <RouterLink v-if="conversation" class="btn btn--ghost manual-link" :to="`/resumes/${conversation.masterId}/manual`">
          <AppIcon name="edit" :size="14" />手动编辑
        </RouterLink>
        <button v-if="conversation" class="btn btn--primary" type="button" :disabled="pdfPending" @click="pdfConfirmOpen = true">
          <AppIcon :name="pdfPending ? 'loader' : 'download'" :size="14" />{{ pdfPending ? '正在生成' : '导出 PDF' }}
        </button>
      </div>
    </header>

    <div class="mobile-switch" role="tablist" aria-label="工作台视图">
      <button type="button" role="tab" :aria-selected="mobilePane === 'conversation'" :class="{ active: mobilePane === 'conversation' }" @click="selectMobilePane('conversation')">AI 对话</button>
      <button type="button" role="tab" :aria-selected="mobilePane === 'preview'" :class="{ active: mobilePane === 'preview' }" @click="selectMobilePane('preview')">A4 预览</button>
    </div>

    <section v-if="loading" class="workbench-loading" aria-busy="true"><AppIcon name="loader" :size="24" /><span>正在恢复长期对话与简历版本…</span></section>
    <section v-else-if="!conversation" class="workbench-loading"><AppIcon name="alert-circle" :size="24" /><span>{{ pageError || '会话不存在' }}</span></section>

    <div v-else ref="workbenchGrid" class="workbench-grid" :class="{ 'is-split-dragging': splitDragging }" :style="{ '--conversation-width': `${splitRatio}%` }">
      <section class="conversation-pane" :class="{ 'is-mobile-hidden': mobilePane !== 'conversation' }">
        <nav class="workbench-tabs" role="tablist" aria-label="工作台功能">
          <button v-for="item in ([['conversation', '对话', 'message'], ['edit', '编辑', 'edit'], ['design', '设计', 'settings'], ['templates', '模板', 'grid']] as const)"
            :key="item[0]" type="button" role="tab" :aria-selected="workbenchView === item[0]" :class="{ active: workbenchView === item[0] }" @click="openWorkbenchView(item[0])">
            <AppIcon :name="item[2]" :size="15" />{{ item[1] }}
          </button>
        </nav>
        <div class="conversation-scroll-shell">
        <div
          ref="conversationScroll"
          class="conversation-scroll scroll-surface"
          :class="{ 'can-scroll-up': conversationEdges.canScrollUp, 'can-scroll-down': conversationEdges.canScrollDown }"
          tabindex="0"
          aria-label="AI 对话与简历编辑滚动区域"
          @scroll.passive="updateConversationScrollState"
        >
          <div v-if="workbenchView === 'conversation' && aiReason" class="ai-status"><AppIcon name="info" :size="16" /><span>{{ aiReason }}</span></div>
          <div v-else-if="workbenchView === 'conversation' && conversation.consent.status !== 'GRANTED'" class="consent-strip">
            <div><strong>首次使用 AI 前需要授权</strong><span>只发送已确认事实和当前问题；照片不会发送给模型。</span></div>
            <AppButton variant="ghost" :pending="consentPending" @click="grantConsent">阅读并同意</AppButton>
          </div>

          <section v-if="workbenchView === 'conversation'" class="chat-thread" aria-label="AI 对话">
            <article class="message message--assistant message--welcome">
              <AiAssistantAvatar />
              <div class="message__content">
                <span>AI 简历助手</span>
                <p>你好，我们可以像聊天一样完成简历。我会一次只问一个问题，把修改拆成可核对的条目；只有你点对勾后才会写入正式简历。</p>
              </div>
            </article>
            <template v-if="showIdentityIntro">
              <article class="message message--user message--setup"><div class="message__content"><span>你</span><p>{{ identityIntro.answer }}</p></div><UserMessageAvatar /></article>
              <article class="message message--assistant message--setup">
                <AiAssistantAvatar />
                <div class="message__content"><span>AI 简历助手</span><p>{{ identityIntro.reply }}</p></div>
              </article>
            </template>
            <template v-for="message in messages" :key="message.id">
              <article class="message" :class="[`message--${message.role.toLowerCase()}`, { 'message--transient': message.transient }]">
                <AiAssistantAvatar v-if="message.role === 'ASSISTANT'" :active="Boolean(message.transient || message.phase === 'loading' || message.phase === 'streaming')" />
                <div class="message__content">
                  <span>{{ message.role === 'ASSISTANT' ? 'AI 简历助手' : '你' }}</span>
                  <p v-if="message.phase === 'loading' && message.content" class="assistant-progress" role="status">{{ message.content }}<i /><i /><i /></p>
                  <p v-else-if="message.phase === 'loading'" class="typing-indicator" aria-label="AI 正在思考" role="status"><i /><i /><i /></p>
                  <p v-else-if="message.status === 'FAILED'">本次调用失败：{{ message.errorCode }}</p>
                  <p v-else-if="message.status === 'CANCELLED'">本次生成已取消，未采用模型返回内容。</p>
                  <p v-else-if="message.content">{{ message.content }}</p>
                  <p v-else>正文已删除</p>
                  <small v-if="message.phase === 'streaming'">正在生成…</small>
                  <small v-if="message.role === 'ASSISTANT' && message.model">{{ message.model }} · {{ message.inputTokens }}/{{ message.outputTokens }} Token</small>
                </div>
                <UserMessageAvatar v-if="message.role === 'USER'" />
              </article>
              <AiResumeChangeSetCard
                v-for="changeSet in messageChangeSets(message.id)"
                :key="changeSet.id"
                :conversation-id="conversation.id"
                :change-set="changeSet"
                @updated="onChangeSetUpdated"
              />
            </template>
            <article v-if="showGuidedPrompt" :key="`prompt-${guidedCard?.id}`" class="message message--assistant message--prompt">
              <AiAssistantAvatar active />
              <div class="message__content"><span>AI 简历助手</span><p>{{ assistantPrompt }}</p></div>
            </article>

            <section v-if="showGuidedPrompt && guidedCard" :key="`card-${guidedCard.id}`" class="guided-card" :aria-label="`${cardMeta[guidedCard.cardType]?.label}结构化填写`">
              <header><div><strong>{{ cardMeta[guidedCard.cardType]?.label }}</strong><span>{{ cardMeta[guidedCard.cardType]?.description }}</span></div><small>{{ draftLabel(guidedCard) }}</small></header>
              <div v-if="guidedCard.cardType === 'TARGET_JOB'" class="guided-fields">
                <div class="target-job-field"><span>主目标岗位</span><JobTaxonomyPicker :selected-name="String(payloads[guidedCard.id]?.targetJob ?? '')" :selected-node-id="String(payloads[guidedCard.id]?.taxonomyNodeId ?? '')" @select="chooseJob(guidedCard, $event)" /></div>
              </div>
              <div v-else-if="isStructuredCard(guidedCard)" class="structured-records">
                <AiSkillSuggestionPanel
                  v-if="guidedCard.cardType === 'SKILLS' && conversation"
                  :conversation-id="conversation.id"
                  :card-id="guidedCard.id"
                  :current-items="structuredItems(guidedCard)"
                  :disabled-reason="descriptionDisabledReason(guidedCard, 0)"
                  @apply="applySkillGroups(guidedCard, $event)"
                  @quota="updateSkillQuota"
                />
                <AiCertificateSuggestionPanel
                  v-if="['CERTIFICATES', 'HONORS'].includes(guidedCard.cardType) && conversation"
                  :conversation-id="conversation.id"
                  :card-id="guidedCard.id"
                  :current-items="structuredItems(guidedCard)"
                  :kind="guidedCard.cardType === 'HONORS' ? 'HONOR' : 'CERTIFICATE'"
                  :career-library-evidence-enabled="conversation.careerLibraryEvidence.enabled"
                  :disabled-reason="descriptionDisabledReason(guidedCard, 0)"
                  @apply="applyCredentialItems(guidedCard, $event)"
                  @quota="updateSkillQuota"
                />
                <fieldset v-for="(item, index) in structuredItems(guidedCard)" :key="index" class="structured-record">
                  <legend>
                    <span>{{ structuredTitle(guidedCard.cardType) }} {{ index + 1 }}</span>
                    <div v-if="structuredItems(guidedCard).length > 1" class="record-actions">
                      <button class="icon-btn" type="button" title="上移此条" :aria-label="`上移${structuredTitle(guidedCard.cardType)} ${index + 1}`" :disabled="index === 0" @click="moveStructuredItem(guidedCard, index, -1)"><AppIcon name="chevron-up" :size="14" /></button>
                      <button class="icon-btn" type="button" title="下移此条" :aria-label="`下移${structuredTitle(guidedCard.cardType)} ${index + 1}`" :disabled="index === structuredItems(guidedCard).length - 1" @click="moveStructuredItem(guidedCard, index, 1)"><AppIcon name="chevron-down" :size="14" /></button>
                      <button class="icon-btn" type="button" title="删除此条" :aria-label="`删除${structuredTitle(guidedCard.cardType)} ${index + 1}`" @click="removeStructuredItem(guidedCard, index)"><AppIcon name="trash" :size="14" /></button>
                    </div>
                  </legend>
                  <div class="structured-grid">
                    <label v-if="guidedCard.cardType === 'EDUCATION'" class="field-wide"><span>学校名称</span><input class="input" :value="String(item.school ?? '')" @input="updateStructuredField(guidedCard, index, 'school', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="guidedCard.cardType === 'EDUCATION'"><span>专业</span><input class="input" :value="String(item.major ?? '')" @input="updateStructuredField(guidedCard, index, 'major', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="guidedCard.cardType === 'EDUCATION'"><span>学历</span><AppSelect :model-value="String(item.degree ?? '')" ariaLabel="学历" :options="degreeOptions" @change="updateStructuredField(guidedCard, index, 'degree', String($event))" /></label>
                    <label v-if="guidedCard.cardType === 'EXPERIENCE'" class="field-wide"><span>公司或组织</span><input class="input" :value="String(item.company ?? '')" @input="updateStructuredField(guidedCard, index, 'company', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="['EXPERIENCE', 'PROJECTS', 'ORGANIZATIONS'].includes(guidedCard.cardType)"><span>{{ guidedCard.cardType === 'EXPERIENCE' ? '岗位' : '你的角色' }}</span><input class="input" :value="String(item.role ?? '')" @input="updateStructuredField(guidedCard, index, 'role', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="['PROJECTS', 'ORGANIZATIONS'].includes(guidedCard.cardType)"><span>{{ guidedCard.cardType === 'PROJECTS' ? '项目名称' : '组织或活动名称' }}</span><input class="input" :value="String(item.name ?? '')" @input="updateStructuredField(guidedCard, index, 'name', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="guidedCard.cardType === 'SKILLS'"><span>技能类别</span><input class="input" :value="String(item.category ?? '')" placeholder="例如：后端开发" @input="updateStructuredField(guidedCard, index, 'category', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="guidedCard.cardType === 'SKILLS'" class="field-wide"><span>技能条目</span><input class="input" :value="skillItemsText(item)" placeholder="Java、Spring Boot、MySQL" @input="updateSkillItems(guidedCard, index, ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="['CERTIFICATES', 'HONORS'].includes(guidedCard.cardType)"><span>{{ guidedCard.cardType === 'CERTIFICATES' ? '证书名称' : '荣誉名称' }}</span><input class="input" :value="String(item.name ?? '')" @input="updateStructuredField(guidedCard, index, 'name', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="['CERTIFICATES', 'HONORS'].includes(guidedCard.cardType)"><span>{{ guidedCard.cardType === 'CERTIFICATES' ? '颁发机构' : '授予机构' }}</span><input class="input" :value="String(item.issuer ?? '')" @input="updateStructuredField(guidedCard, index, 'issuer', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="['CERTIFICATES', 'HONORS'].includes(guidedCard.cardType)"><span>取得时间</span><MonthInput :model-value="String(item.date ?? '')" label="取得时间" @update:model-value="updateStructuredField(guidedCard, index, 'date', $event)" /></label>
                    <label v-if="guidedCard.cardType === 'LANGUAGES'"><span>语言</span><AppSelect :model-value="String(item.language ?? '')" ariaLabel="语言" searchable search-placeholder="搜索语言" :options="languageOptionsFor(item.language)" @change="updateStructuredField(guidedCard, index, 'language', String($event))" /></label>
                    <label v-if="guidedCard.cardType === 'LANGUAGES'"><span>水平</span><AppSelect :model-value="String(item.level ?? '')" ariaLabel="语言水平" :options="languageLevelOptions" @change="updateStructuredField(guidedCard, index, 'level', String($event))" /></label>
                    <label v-if="guidedCard.cardType === 'LANGUAGES'"><span>成绩或证明</span><input class="input" :value="String(item.score ?? '')" placeholder="例如：CET-6 520" @input="updateStructuredField(guidedCard, index, 'score', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="timelineCardTypes.has(guidedCard.cardType)"><span>开始时间</span><MonthInput :model-value="String(item.startDate ?? '')" label="开始时间" @update:model-value="updateStructuredField(guidedCard, index, 'startDate', $event)" /></label>
                    <label v-if="timelineCardTypes.has(guidedCard.cardType)"><span>结束时间</span><MonthInput :model-value="String(item.endDate ?? '')" :disabled="Boolean(item.current)" label="结束时间" @update:model-value="updateStructuredField(guidedCard, index, 'endDate', $event)" /></label>
                    <label v-if="timelineCardTypes.has(guidedCard.cardType)"><span>城市</span><input class="input" :value="String(item.location ?? '')" @input="updateStructuredField(guidedCard, index, 'location', ($event.target as HTMLInputElement).value)" /></label>
                    <label v-if="timelineCardTypes.has(guidedCard.cardType)" class="current-field"><input type="checkbox" :checked="Boolean(item.current)" @change="updateStructuredField(guidedCard, index, 'current', ($event.target as HTMLInputElement).checked)" /><span>至今</span></label>
                    <AiDescriptionField
                      v-if="descriptionCardTypes.has(guidedCard.cardType)"
                      class="field-wide"
                      :model-value="String(item.description ?? '')"
                      :state="descriptionState(guidedCard, index)"
                      :disabled-reason="descriptionDisabledReason(guidedCard, index)"
                      :placeholder="descriptionPlaceholder(guidedCard.cardType)"
                      :source-labels="descriptionSourceLabels(guidedCard, index)"
                      @update:model-value="updateStructuredField(guidedCard, index, 'description', $event)"
                      @generate="generateDescription(guidedCard, index)"
                      @cancel="cancelDescription(guidedCard, index)"
                      @discard="discardDescription(guidedCard, index)"
                      @apply="applyDescription(guidedCard, index, $event)"
                    />
                    <label v-else-if="['CERTIFICATES', 'HONORS'].includes(guidedCard.cardType)" class="field-wide credential-description" :class="{ 'is-short': credentialDescriptionCharacters(item.description) < 60 }">
                      <span><span>补充说明</span><small>{{ credentialDescriptionCharacters(item.description) }} / 至少 60 个有效字符</small></span>
                      <textarea class="textarea" rows="5" :value="String(item.description ?? '')" placeholder="填写取得过程、评审范围、实践内容或可核实成果，也可使用上方 AI 补充说明" @input="updateStructuredField(guidedCard, index, 'description', ($event.target as HTMLTextAreaElement).value)" />
                    </label>
                    <label v-else class="field-wide"><span>补充描述</span><textarea class="textarea" rows="4" :value="String(item.description ?? '')" placeholder="只填写可以确认的内容" @input="updateStructuredField(guidedCard, index, 'description', ($event.target as HTMLTextAreaElement).value)" /></label>
                  </div>
                </fieldset>
                <button class="add-record" type="button" @click="addStructuredItem(guidedCard)"><AppIcon name="plus" :size="14" />添加一段{{ structuredTitle(guidedCard.cardType) }}</button>
              </div>
              <div v-else-if="guidedCard.cardType === 'CONTACT'" class="guided-fields guided-contact">
                <div class="structured-grid">
                  <label><span>姓名 <small>必填</small></span><input class="input" autocomplete="name" maxlength="50" :value="String(payloads[guidedCard.id]?.name ?? '')" placeholder="填写用于简历的姓名" @input="updateField(guidedCard, 'name', ($event.target as HTMLInputElement).value)" /></label>
                  <label><span>所在城市 <small>选填</small></span><input class="input" autocomplete="address-level2" maxlength="60" :value="String(payloads[guidedCard.id]?.location ?? '')" placeholder="例如：杭州" @input="updateField(guidedCard, 'location', ($event.target as HTMLInputElement).value)" /></label>
                  <label><span>邮箱 <small>与手机号至少填一项</small></span><input class="input" type="email" inputmode="email" autocomplete="email" maxlength="254" :value="String(payloads[guidedCard.id]?.email ?? '')" placeholder="name@example.com" @input="updateField(guidedCard, 'email', ($event.target as HTMLInputElement).value)" /></label>
                  <label><span>手机号 <small>与邮箱至少填一项</small></span><input class="input" type="tel" inputmode="tel" autocomplete="tel" maxlength="30" :value="String(payloads[guidedCard.id]?.phone ?? '')" placeholder="例如：138 0000 0000" @input="updateField(guidedCard, 'phone', ($event.target as HTMLInputElement).value)" /></label>
                  <section class="contact-links guided-contact__links">
                    <header>
                      <div><strong>个人链接</strong><small>选填作品集、GitHub 或个人主页</small></div>
                      <button class="icon-btn" type="button" title="添加个人链接" aria-label="添加个人链接" @click="addContactLink(guidedCard)"><AppIcon name="plus" :size="15" /></button>
                    </header>
                    <div v-for="(link, index) in contactLinks(guidedCard)" :key="index" class="contact-link-row">
                      <label><span>链接 {{ index + 1 }}</span><input class="input" type="url" inputmode="url" autocomplete="url" :value="link" placeholder="https://" @input="updateContactLink(guidedCard, index, ($event.target as HTMLInputElement).value)" /></label>
                      <div class="record-actions">
                        <button class="icon-btn" type="button" title="上移链接" :aria-label="`上移链接 ${index + 1}`" :disabled="index === 0" @click="moveContactLink(guidedCard, index, -1)"><AppIcon name="chevron-up" :size="14" /></button>
                        <button class="icon-btn" type="button" title="下移链接" :aria-label="`下移链接 ${index + 1}`" :disabled="index === contactLinks(guidedCard).length - 1" @click="moveContactLink(guidedCard, index, 1)"><AppIcon name="chevron-down" :size="14" /></button>
                        <button class="icon-btn" type="button" title="删除链接" :aria-label="`删除链接 ${index + 1}`" @click="removeContactLink(guidedCard, index)"><AppIcon name="trash" :size="14" /></button>
                      </div>
                    </div>
                  </section>
                </div>
              </div>
              <div v-else-if="guidedCard.cardType === 'SUMMARY'" class="guided-text summary-writing-area">
                <AiSummarySuggestionPanel
                  v-if="conversation"
                  :conversation-id="conversation.id"
                  :card-id="guidedCard.id"
                  :current-summary="String(payloads[guidedCard.id]?.text ?? '')"
                  :disabled-reason="summaryDisabledReason()"
                  @apply="applySummaryCandidate(guidedCard, $event)"
                  @quota="updateSkillQuota"
                  @busy="updateSummaryPending"
                />
                <label><span>个人简介草稿</span><textarea class="textarea" rows="6" :value="String(payloads[guidedCard.id]?.text ?? '')" placeholder="填写个人简介，或从上方三个 AI 建议中选择一个版本" @input="updateField(guidedCard, 'text', ($event.target as HTMLTextAreaElement).value)" /></label>
              </div>
              <label v-else class="guided-text"><span>确认事实</span><textarea class="textarea" rows="5" :value="String(payloads[guidedCard.id]?.text ?? '')" :placeholder="`填写${cardMeta[guidedCard.cardType]?.label ?? '内容'}，只写可以确认的事实`" @input="updateField(guidedCard, 'text', ($event.target as HTMLTextAreaElement).value)" /></label>
              <footer>
                <span :class="{ bad: cardSubmitIssue(guidedCard) }">{{ cardSubmitIssue(guidedCard) || '填写会实时投影到右侧；确认后才进入正式版本。' }}</span>
                <div class="guided-card__actions">
                  <AppButton v-if="guidedCard.cardType === 'LANGUAGES'" variant="text" :pending="draftStates[guidedCard.id] === 'saving'" @click="skipCard(guidedCard)"><AppIcon name="chevron-right" :size="15" />暂时跳过</AppButton>
                  <AppButton :pending="draftStates[guidedCard.id] === 'saving'" :disabled="cardSubmitDisabled(guidedCard)" @click="submitCard(guidedCard)"><AppIcon name="check" :size="15" />确认并继续</AppButton>
                </div>
              </footer>
            </section>

            <template v-if="repeatPromptType">
              <article class="message message--user message--setup"><div class="message__content"><span>你</span><p>这段{{ structuredTitle(repeatPromptType) }}已经补充好了</p></div><UserMessageAvatar /></article>
              <article class="message message--assistant message--setup message--prompt">
                <AiAssistantAvatar active />
                <div class="message__content"><span>AI 简历助手</span><p>还要再添加一段{{ structuredTitle(repeatPromptType) }}吗？可以继续补充，也可以进入下一步。</p></div>
              </article>
              <section class="choice-inline"><button type="button" @click="continueAfterRepeat">不用了，继续下一步<AppIcon name="chevron-right" :size="14" /></button><button type="button" @click="addAnotherFromPrompt">继续添加<AppIcon name="plus" :size="14" /></button></section>
            </template>
          </section>

          <section v-if="workbenchView === 'edit'" class="module-tools">
            <button type="button" class="module-tools__toggle" :aria-expanded="moduleEditorOpen" @click="moduleEditorOpen = !moduleEditorOpen">
              <span><AppIcon name="file-text" :size="16" /><strong>简历资料</strong><small>{{ confirmedCardCount }} / {{ editableCards.length }} 项已确认</small></span>
              <AppIcon :name="moduleEditorOpen ? 'chevron-down' : 'chevron-right'" :size="15" />
            </button>

          <nav v-if="moduleEditorOpen" class="card-nav" aria-label="简历模块">
            <button v-for="card in editableCards" :key="card.id" type="button" :class="{ active: selectedCard?.id === card.id }" @click="selectCard(card)">
              <AppIcon :name="cardMeta[card.cardType]?.icon ?? 'file-text'" :size="16" />
              <span><strong>{{ cardMeta[card.cardType]?.label ?? card.cardType }}</strong><small>{{ cardMeta[card.cardType]?.description }}</small></span>
              <AppIcon :name="card.status === 'CONFIRMED' ? 'check-circle' : 'chevron-right'" :size="15" />
            </button>
          </nav>

          <section v-if="moduleEditorOpen && selectedCard" class="card-editor">
            <header><div><h2>{{ cardMeta[selectedCard.cardType]?.label }}</h2><p>{{ cardMeta[selectedCard.cardType]?.description }}</p></div><span :class="{ bad: draftStates[selectedCard.id] === 'error' }">{{ draftLabel(selectedCard) }}</span></header>

            <div v-if="selectedCard.cardType === 'TARGET_JOB'" class="card-editor__fields">
              <div class="target-job-field"><span>主目标岗位</span><JobTaxonomyPicker :selected-name="String(selectedPayload.targetJob ?? '')" :selected-node-id="String(selectedPayload.taxonomyNodeId ?? '')" @select="chooseJob(selectedCard, $event)" /></div>
            </div>
            <div v-else-if="isStructuredCard(selectedCard)" class="structured-records structured-records--module">
              <AiSkillSuggestionPanel
                v-if="selectedCard.cardType === 'SKILLS' && conversation"
                :conversation-id="conversation.id"
                :card-id="selectedCard.id"
                :current-items="structuredItems(selectedCard)"
                :disabled-reason="descriptionDisabledReason(selectedCard, 0)"
                @apply="applySkillGroups(selectedCard, $event)"
                @quota="updateSkillQuota"
              />
              <AiCertificateSuggestionPanel
                v-if="['CERTIFICATES', 'HONORS'].includes(selectedCard.cardType) && conversation"
                :conversation-id="conversation.id"
                :card-id="selectedCard.id"
                :current-items="structuredItems(selectedCard)"
                :kind="selectedCard.cardType === 'HONORS' ? 'HONOR' : 'CERTIFICATE'"
                :career-library-evidence-enabled="conversation.careerLibraryEvidence.enabled"
                :disabled-reason="descriptionDisabledReason(selectedCard, 0)"
                @apply="applyCredentialItems(selectedCard, $event)"
                @quota="updateSkillQuota"
              />
              <fieldset v-for="(item, index) in structuredItems(selectedCard)" :key="index" class="structured-record">
                <legend>
                  <span>{{ structuredTitle(selectedCard.cardType) }} {{ index + 1 }}</span>
                  <div v-if="structuredItems(selectedCard).length > 1" class="record-actions">
                    <button class="icon-btn" type="button" title="上移此条" :aria-label="`上移${structuredTitle(selectedCard.cardType)} ${index + 1}`" :disabled="index === 0" @click="moveStructuredItem(selectedCard, index, -1)"><AppIcon name="chevron-up" :size="14" /></button>
                    <button class="icon-btn" type="button" title="下移此条" :aria-label="`下移${structuredTitle(selectedCard.cardType)} ${index + 1}`" :disabled="index === structuredItems(selectedCard).length - 1" @click="moveStructuredItem(selectedCard, index, 1)"><AppIcon name="chevron-down" :size="14" /></button>
                    <button class="icon-btn" type="button" title="删除此条" :aria-label="`删除${structuredTitle(selectedCard.cardType)} ${index + 1}`" @click="removeStructuredItem(selectedCard, index)"><AppIcon name="trash" :size="14" /></button>
                  </div>
                </legend>
                <div class="structured-grid">
                  <label v-if="selectedCard.cardType === 'EDUCATION'" class="field-wide"><span>学校名称</span><input class="input card-editor__field" :value="String(item.school ?? '')" @input="updateStructuredField(selectedCard, index, 'school', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="selectedCard.cardType === 'EDUCATION'"><span>专业</span><input class="input" :value="String(item.major ?? '')" @input="updateStructuredField(selectedCard, index, 'major', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="selectedCard.cardType === 'EDUCATION'"><span>学历</span><AppSelect :model-value="String(item.degree ?? '')" ariaLabel="学历" :options="degreeOptions" @change="updateStructuredField(selectedCard, index, 'degree', String($event))" /></label>
                  <label v-if="selectedCard.cardType === 'EXPERIENCE'" class="field-wide"><span>公司或组织</span><input class="input card-editor__field" :value="String(item.company ?? '')" @input="updateStructuredField(selectedCard, index, 'company', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="['EXPERIENCE', 'PROJECTS', 'ORGANIZATIONS'].includes(selectedCard.cardType)"><span>{{ selectedCard.cardType === 'EXPERIENCE' ? '岗位' : '你的角色' }}</span><input class="input" :value="String(item.role ?? '')" @input="updateStructuredField(selectedCard, index, 'role', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="['PROJECTS', 'ORGANIZATIONS'].includes(selectedCard.cardType)"><span>{{ selectedCard.cardType === 'PROJECTS' ? '项目名称' : '组织或活动名称' }}</span><input class="input" :value="String(item.name ?? '')" @input="updateStructuredField(selectedCard, index, 'name', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="selectedCard.cardType === 'SKILLS'"><span>技能类别</span><input class="input" :value="String(item.category ?? '')" placeholder="例如：后端开发" @input="updateStructuredField(selectedCard, index, 'category', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="selectedCard.cardType === 'SKILLS'" class="field-wide"><span>技能条目</span><input class="input" :value="skillItemsText(item)" placeholder="Java、Spring Boot、MySQL" @input="updateSkillItems(selectedCard, index, ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="['CERTIFICATES', 'HONORS'].includes(selectedCard.cardType)"><span>{{ selectedCard.cardType === 'CERTIFICATES' ? '证书名称' : '荣誉名称' }}</span><input class="input" :value="String(item.name ?? '')" @input="updateStructuredField(selectedCard, index, 'name', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="['CERTIFICATES', 'HONORS'].includes(selectedCard.cardType)"><span>{{ selectedCard.cardType === 'CERTIFICATES' ? '颁发机构' : '授予机构' }}</span><input class="input" :value="String(item.issuer ?? '')" @input="updateStructuredField(selectedCard, index, 'issuer', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="['CERTIFICATES', 'HONORS'].includes(selectedCard.cardType)"><span>取得时间</span><MonthInput :model-value="String(item.date ?? '')" label="取得时间" @update:model-value="updateStructuredField(selectedCard, index, 'date', $event)" /></label>
                  <label v-if="selectedCard.cardType === 'LANGUAGES'"><span>语言</span><AppSelect :model-value="String(item.language ?? '')" ariaLabel="语言" searchable search-placeholder="搜索语言" :options="languageOptionsFor(item.language)" @change="updateStructuredField(selectedCard, index, 'language', String($event))" /></label>
                  <label v-if="selectedCard.cardType === 'LANGUAGES'"><span>水平</span><AppSelect :model-value="String(item.level ?? '')" ariaLabel="语言水平" :options="languageLevelOptions" @change="updateStructuredField(selectedCard, index, 'level', String($event))" /></label>
                  <label v-if="selectedCard.cardType === 'LANGUAGES'"><span>成绩或证明</span><input class="input" :value="String(item.score ?? '')" placeholder="例如：CET-6 520" @input="updateStructuredField(selectedCard, index, 'score', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="timelineCardTypes.has(selectedCard.cardType)"><span>开始时间</span><MonthInput :model-value="String(item.startDate ?? '')" label="开始时间" @update:model-value="updateStructuredField(selectedCard, index, 'startDate', $event)" /></label>
                  <label v-if="timelineCardTypes.has(selectedCard.cardType)"><span>结束时间</span><MonthInput :model-value="String(item.endDate ?? '')" :disabled="Boolean(item.current)" label="结束时间" @update:model-value="updateStructuredField(selectedCard, index, 'endDate', $event)" /></label>
                  <label v-if="timelineCardTypes.has(selectedCard.cardType)"><span>城市</span><input class="input" :value="String(item.location ?? '')" @input="updateStructuredField(selectedCard, index, 'location', ($event.target as HTMLInputElement).value)" /></label>
                  <label v-if="timelineCardTypes.has(selectedCard.cardType)" class="current-field"><input type="checkbox" :checked="Boolean(item.current)" @change="updateStructuredField(selectedCard, index, 'current', ($event.target as HTMLInputElement).checked)" /><span>至今</span></label>
                  <AiDescriptionField
                    v-if="descriptionCardTypes.has(selectedCard.cardType)"
                    class="field-wide"
                    :model-value="String(item.description ?? '')"
                    :state="descriptionState(selectedCard, index)"
                      :disabled-reason="descriptionDisabledReason(selectedCard, index)"
                      :placeholder="descriptionPlaceholder(selectedCard.cardType)"
                    :source-labels="descriptionSourceLabels(selectedCard, index)"
                    @update:model-value="updateStructuredField(selectedCard, index, 'description', $event)"
                    @generate="generateDescription(selectedCard, index)"
                    @cancel="cancelDescription(selectedCard, index)"
                    @discard="discardDescription(selectedCard, index)"
                    @apply="applyDescription(selectedCard, index, $event)"
                  />
                  <label v-else-if="['CERTIFICATES', 'HONORS'].includes(selectedCard.cardType)" class="field-wide credential-description" :class="{ 'is-short': credentialDescriptionCharacters(item.description) < 60 }">
                    <span><span>补充说明</span><small>{{ credentialDescriptionCharacters(item.description) }} / 至少 60 个有效字符</small></span>
                    <textarea class="textarea" rows="5" :value="String(item.description ?? '')" placeholder="填写取得过程、评审范围、实践内容或可核实成果，也可使用上方 AI 补充说明" @input="updateStructuredField(selectedCard, index, 'description', ($event.target as HTMLTextAreaElement).value)" />
                  </label>
                  <label v-else class="field-wide"><span>补充描述</span><textarea class="textarea" rows="4" :value="String(item.description ?? '')" placeholder="只填写可以确认的内容" @input="updateStructuredField(selectedCard, index, 'description', ($event.target as HTMLTextAreaElement).value)" /></label>
                </div>
              </fieldset>
              <button class="add-record" type="button" @click="addStructuredItem(selectedCard)"><AppIcon name="plus" :size="14" />添加一段{{ structuredTitle(selectedCard.cardType) }}</button>
            </div>
            <div v-else-if="selectedCard.cardType === 'CONTACT'" class="card-editor__fields card-editor__fields--two">
              <label><span>姓名</span><input class="input card-editor__field" :value="String(selectedPayload.name ?? '')" @input="updateField(selectedCard, 'name', ($event.target as HTMLInputElement).value)" /></label>
              <label><span>所在城市</span><input class="input" :value="String(selectedPayload.location ?? '')" @input="updateField(selectedCard, 'location', ($event.target as HTMLInputElement).value)" /></label>
              <label><span>邮箱</span><input class="input" type="email" :value="String(selectedPayload.email ?? '')" @input="updateField(selectedCard, 'email', ($event.target as HTMLInputElement).value)" /></label>
              <label><span>手机号</span><input class="input" :value="String(selectedPayload.phone ?? '')" @input="updateField(selectedCard, 'phone', ($event.target as HTMLInputElement).value)" /></label>
              <section class="contact-links">
                <header>
                  <div><strong>个人链接</strong><small>作品集、GitHub 或个人主页</small></div>
                  <button class="icon-btn" type="button" title="添加个人链接" aria-label="添加个人链接" @click="addContactLink(selectedCard)"><AppIcon name="plus" :size="15" /></button>
                </header>
                <p v-if="!contactLinks(selectedCard).length">尚未添加个人链接</p>
                <div v-for="(link, index) in contactLinks(selectedCard)" :key="index" class="contact-link-row">
                  <label><span>链接 {{ index + 1 }}</span><input class="input" type="url" :value="link" placeholder="https://" @input="updateContactLink(selectedCard, index, ($event.target as HTMLInputElement).value)" /></label>
                  <div class="record-actions">
                    <button class="icon-btn" type="button" title="上移链接" :aria-label="`上移链接 ${index + 1}`" :disabled="index === 0" @click="moveContactLink(selectedCard, index, -1)"><AppIcon name="chevron-up" :size="14" /></button>
                    <button class="icon-btn" type="button" title="下移链接" :aria-label="`下移链接 ${index + 1}`" :disabled="index === contactLinks(selectedCard).length - 1" @click="moveContactLink(selectedCard, index, 1)"><AppIcon name="chevron-down" :size="14" /></button>
                    <button class="icon-btn" type="button" title="删除链接" :aria-label="`删除链接 ${index + 1}`" @click="removeContactLink(selectedCard, index)"><AppIcon name="trash" :size="14" /></button>
                  </div>
                </div>
              </section>
              <div class="photo-control">
                <img v-if="conversation.photo" :src="conversation.photo.contentUrl" alt="当前简历照片" />
                <span v-else class="photo-control__empty"><AppIcon name="user" :size="20" /></span>
                <div><strong>简历照片</strong><small>PNG/JPEG · 最多 1MB</small></div>
                <input ref="photoInput" class="photo-control__input" type="file" accept="image/png,image/jpeg" @change="uploadPhoto" />
                <AppButton variant="ghost" :pending="photoPending" @click="choosePhoto"><AppIcon name="upload" :size="14" />{{ conversation.photo ? '更换' : '上传' }}</AppButton>
                <button v-if="conversation.photo" class="icon-btn" type="button" title="移除照片" :disabled="photoPending" @click="removePhoto"><AppIcon name="trash" :size="15" /></button>
              </div>
            </div>
            <div v-else-if="selectedCard.cardType === 'SUMMARY'" class="card-editor__textarea summary-writing-area">
              <AiSummarySuggestionPanel
                v-if="conversation"
                :conversation-id="conversation.id"
                :card-id="selectedCard.id"
                :current-summary="String(selectedPayload.text ?? '')"
                :disabled-reason="summaryDisabledReason()"
                @apply="applySummaryCandidate(selectedCard, $event)"
                @quota="updateSkillQuota"
                @busy="updateSummaryPending"
              />
              <label><span>个人简介草稿</span><textarea class="textarea card-editor__field" rows="7" :value="String(selectedPayload.text ?? '')" placeholder="填写个人简介，或从上方三个 AI 建议中选择一个版本" @input="updateField(selectedCard, 'text', ($event.target as HTMLTextAreaElement).value)" /></label>
            </div>
            <label v-else class="card-editor__textarea"><span>确认事实</span><textarea class="textarea card-editor__field" rows="7" :value="String(selectedPayload.text ?? '')" :placeholder="`填写${cardMeta[selectedCard.cardType]?.label ?? '内容'}，只写可以确认的事实`" @input="updateField(selectedCard, 'text', ($event.target as HTMLTextAreaElement).value)" /></label>

            <footer><span :class="{ bad: cardSubmitIssue(selectedCard) }">{{ cardSubmitIssue(selectedCard) || '停止输入约 1 秒后保存草稿；只有确认才生成正式修订。' }}</span><AppButton :pending="draftStates[selectedCard.id] === 'saving'" :disabled="cardSubmitDisabled(selectedCard)" @click="submitCard(selectedCard)"><AppIcon name="check" :size="15" />确认此模块</AppButton></footer>
          </section>
          </section>

          <section v-if="workbenchView === 'design'" class="design-panel">
            <header><div><strong>当前模板设计</strong><span>{{ activeTemplate?.displayName }}</span></div><small :class="{ bad: designSaveState === 'error' }">{{ designSaveState === 'saving' ? '保存中…' : designSaveState === 'waiting' ? '等待保存' : designSaveState === 'saved' ? '已保存' : designSaveState === 'error' ? '未保存，可重试' : '按模板单独记忆' }}</small></header>
            <fieldset v-if="activeTemplate?.presets.length" class="design-presets">
              <legend>设计预设</legend>
              <button v-for="preset in activeTemplate.presets" :key="preset.variantCode" type="button"
                :class="{ active: activeVariant === preset.variantCode }" @click="changeDesignPreset(preset.variantCode)">
                {{ preset.displayName }}
              </button>
            </fieldset>
            <div v-if="activeDesign" class="design-controls">
              <label><span>字体</span><AppSelect :model-value="activeDesign.fontPreset" ariaLabel="字体" :options="fontPresetOptions" @change="updateDesign('fontPreset', $event)" /></label>
              <label><span>字号</span><AppSelect :model-value="activeDesign.fontScale" ariaLabel="字号" :options="fontScaleOptions" @change="updateDesign('fontScale', $event)" /></label>
              <label><span>行距</span><AppSelect :model-value="activeDesign.lineHeight" ariaLabel="行距" :options="spacingOptions" @change="updateDesign('lineHeight', $event)" /></label>
              <label><span>页边距</span><AppSelect :model-value="activeDesign.pageMargin" ariaLabel="页边距" :options="pageMarginOptions" @change="updateDesign('pageMargin', $event)" /></label>
              <label><span>内容密度</span><AppSelect :model-value="activeDesign.density" ariaLabel="内容密度" :options="spacingOptions" @change="updateDesign('density', $event)" /></label>
              <label><span>日期格式</span><AppSelect :model-value="activeDesign.dateFormat" ariaLabel="日期格式" :options="dateFormatOptions" @change="updateDesign('dateFormat', $event)" /></label>
              <label><span>头部布局</span><AppSelect :model-value="activeDesign.headerLayout" ariaLabel="头部布局" :options="headerLayoutOptions" @change="updateDesign('headerLayout', $event)" /></label>
              <label><span>标题样式</span><AppSelect :model-value="activeDesign.headingStyle" ariaLabel="标题样式" :options="headingStyleOptions" @change="updateDesign('headingStyle', $event)" /></label>
              <label><span>照片</span><AppSelect :model-value="activeDesign.photoMode" ariaLabel="照片" :options="photoModeOptions" @change="updateDesign('photoMode', $event)" /></label>
              <fieldset class="design-palette"><legend>主题色</legend><button v-for="color in availableAccentColors" :key="color" type="button" :class="{ active: activeDesign.accentColor.toUpperCase() === color }" :style="{ backgroundColor: color }" :title="color" @click="updateDesign('accentColor', color)"><span>{{ color }}</span></button></fieldset>
            </div>
            <section v-if="activeDesign" class="section-config"><header><strong>模块显示与顺序</strong><span>隐藏只影响当前模板</span></header><article v-for="section in designSections" :key="section.key"><label><input type="checkbox" :checked="!activeDesign.hiddenSections.includes(section.key)" @change="toggleDesignSection(section.key, ($event.target as HTMLInputElement).checked)" /><span>{{ section.label }}</span></label><div><button class="icon-btn" type="button" title="上移" @click="moveDesignSection(section.key, -1)"><AppIcon name="chevron-up" :size="14" /></button><button class="icon-btn" type="button" title="下移" @click="moveDesignSection(section.key, 1)"><AppIcon name="chevron-down" :size="14" /></button></div></article></section>
          </section>

          <section v-if="workbenchView === 'templates'" class="template-panel">
            <header><div><strong>12 款智能模板</strong><span>使用同一份结构化内容，即时换版</span></div><small v-if="templatesPending">读取中…</small></header>
            <div class="template-gallery">
              <button v-for="template in smartTemplates" :key="template.templateId" type="button" :class="{ active: activeTemplate?.templateId === template.templateId }" :disabled="templatePending" @click="changeTemplate(template.templateId)">
                <div class="template-thumb"><ResumeTemplatePreview :resume="previewResume" :content="previewContent" :design="template.design.settings" :template-id="template.templateId" :layout-definition-json="template.layoutDefinitionJson" :renderer-protocol="template.rendererProtocol" :variant-code="template.design.variantCode" compact /></div>
                <span><strong>{{ template.displayName }}</strong><small>{{ template.recommendedPages }} 页 · {{ template.languageCode }}</small></span><AppIcon v-if="activeTemplate?.templateId === template.templateId" name="check-circle" :size="16" />
              </button>
            </div>
          </section>

        </div>
        <Transition name="scroll-return">
          <button
            v-if="workbenchView === 'conversation' && showConversationReturn"
            class="conversation-return"
            type="button"
            title="回到底部"
            aria-label="回到底部"
            @click="scrollConversationToBottom(true)"
          ><AppIcon name="chevron-down" :size="18" /></button>
        </Transition>
        </div>

        <form v-if="workbenchView === 'conversation'" class="composer" aria-label="AI 对话输入" @submit.prevent="sendMessage">
          <label
            class="career-evidence-toggle"
            :class="{
              'is-enabled': conversation.careerLibraryEvidence.enabled,
              'is-pending': careerEvidencePending,
            }"
          >
            <input
              type="checkbox"
              :checked="conversation.careerLibraryEvidence.enabled"
              :disabled="careerEvidencePending"
              aria-describedby="career-evidence-description"
              @change="toggleCareerLibraryEvidence"
              @keydown.space.prevent="toggleCareerLibraryEvidence"
            />
            <span class="career-evidence-toggle__check" aria-hidden="true">
              <AppIcon v-if="conversation.careerLibraryEvidence.enabled" name="check" :size="12" />
            </span>
            <span class="career-evidence-toggle__icon" aria-hidden="true"><AppIcon name="folder" :size="15" /></span>
            <span class="career-evidence-toggle__copy">
              <strong>使用求职资料库作为证据</strong>
              <small id="career-evidence-description">AI 仅引用相关且已确认的结构化资料</small>
            </span>
            <span class="career-evidence-toggle__status">
              <AppIcon v-if="careerEvidencePending" name="loader" :size="11" />
              <i v-else aria-hidden="true" />
              {{ careerEvidencePending
                ? '保存中'
                : conversation.careerLibraryEvidence.enabled
                  ? `已启用 · v${conversation.careerLibraryEvidence.snapshotVersion}`
                  : '未启用' }}
            </span>
          </label>
          <div class="composer__head">
            <span><i :class="{ online: conversation.aiAvailable }" />AI 对话</span>
            <small>{{ conversation.aiAvailable ? 'AI 会先追问，不会编造事实' : '通道暂不可用，消息只会保存' }}</small>
          </div>
          <div class="composer__input">
            <textarea v-model="messageText" rows="3" maxlength="8000" :placeholder="conversation.aiAvailable ? '直接告诉 AI 你的目标、经历，或提出简历问题…' : 'AI 通道暂不可用，可先记录想问的问题…'" @keydown="onMessageComposerKeydown" />
            <button
              class="icon-btn"
              :class="messagePending ? 'icon-btn--stop' : 'icon-btn--send'"
              :type="messagePending ? 'button' : 'submit'"
              :disabled="messagePending ? cancelPending || !currentMessageRequestId : !messageText.trim() || aiFieldGenerationPending"
              :title="messagePending ? (cancelPending ? '正在停止' : '停止生成') : aiFieldGenerationPending ? '结构化内容正在生成，请稍后发送' : (conversation.aiAvailable ? '发送给 AI' : '保存到对话')"
              @click="messagePending ? cancelMessage() : undefined"
            >
              <AppIcon :name="cancelPending ? 'loader' : messagePending ? 'stop' : 'send'" :size="17" />
            </button>
          </div>
        </form>
      </section>

      <div
        class="workbench-splitter"
        role="separator"
        aria-label="调整对话与预览区域宽度"
        aria-orientation="vertical"
        :aria-valuenow="Math.round(splitRatio)"
        aria-valuemin="34"
        aria-valuemax="66"
        tabindex="0"
        title="拖动调整左右区域，双击恢复默认"
        @pointerdown="startSplitDrag"
        @pointermove="moveSplitDrag"
        @pointerup="stopSplitDrag"
        @pointercancel="stopSplitDrag"
        @dblclick="resetSplitRatio"
        @keydown="resizeSplitWithKeyboard"
      ><span><i /><i /><i /></span></div>

      <section class="preview-pane" :class="{ 'is-mobile-hidden': mobilePane !== 'preview' }">
        <header class="preview-tools">
          <div><strong>实时 A4 预览</strong><span :class="{ 'preview-status--bad': pdfOverflowBlocked }">{{ previewStatusText }}</span></div>
          <button class="preview-template" type="button" @click="openWorkbenchView('templates')"><span>当前模板</span><strong>{{ activeTemplate?.displayName ?? '读取中' }}</strong><AppIcon name="chevron-right" :size="14" /></button>
        </header>
        <div
          ref="previewScroll"
          class="preview-stage scroll-surface"
          :class="{ 'can-scroll-up': previewEdges.canScrollUp, 'can-scroll-down': previewEdges.canScrollDown }"
          tabindex="0"
          aria-label="A4 简历预览滚动区域"
          @scroll.passive="updatePreviewScrollState"
        >
          <ResumeTemplatePreview :resume="previewResume" :content="previewContent" :design="activeDesign" :template-id="activeTemplate?.templateId" :photo-url="conversation.photo?.contentUrl" :layout-definition-json="layoutJson" :renderer-protocol="rendererProtocol" :variant-code="activeVariant" interactive @section-click="previewSection" />
        </div>
        <footer class="preview-foot"><span><AppIcon name="info" :size="14" />填写内容会实时预览；只有确认后的结构化内容进入导出。</span><RouterLink :to="`/resumes/${conversation.masterId}/manual#export`">查看历史版本</RouterLink></footer>
      </section>
    </div>

    <div v-if="activeTool" class="tool-mask" @click.self="activeTool = null">
      <aside class="tool-panel" :aria-busy="toolPending === 'load'">
        <header class="tool-panel__head">
          <div><strong>{{ activeTool === 'branches' ? '简历分支' : activeTool === 'history' ? '版本历史' : 'AI 隐私与偏好' }}</strong><span v-if="activeTool === 'branches'">更新只在用户确认后同步</span><span v-else-if="activeTool === 'history'">恢复会生成新修订，不删除历史</span><span v-else>授权、资料引用、风格与历史正文</span></div>
          <button class="icon-btn" type="button" title="关闭" @click="activeTool = null"><AppIcon name="x" :size="17" /></button>
        </header>
        <div
          ref="toolScroll"
          class="tool-panel__body scroll-surface"
          :class="{ 'can-scroll-up': toolEdges.canScrollUp, 'can-scroll-down': toolEdges.canScrollDown }"
          tabindex="0"
          aria-label="工作台工具内容滚动区域"
          @scroll.passive="updateToolScrollState"
        >
          <p v-if="toolError" class="inline-alert inline-alert--bad">{{ toolError }}</p>
          <div v-if="toolPending === 'load'" class="tool-loading"><AppIcon name="loader" :size="20" />正在读取…</div>

          <template v-else-if="activeTool === 'branches'">
            <section class="tool-section branch-create">
              <h3>创建语言分支</h3>
              <p>从当前确认版本建立另一语言版本，原内容保持不变。</p>
              <button class="language-action" type="button" :disabled="toolPending === 'language-create'" @click="createLanguageBranch"><AppIcon name="plus" :size="14" />建立另一语言分支</button>
            </section>
            <section class="branch-list">
              <article v-for="branch in branches" :key="branch.id" :class="{ active: branch.active }">
                <div class="branch-main"><span>{{ branchTypeLabel(branch) }} · {{ branch.languageCode }}</span><strong>{{ branch.title }}</strong><small>{{ branchStatusLabel(branch) }}</small><small v-if="unconfirmedProperNames(branch).length" class="proper-name-review">待确认专有名称：{{ unconfirmedProperNames(branch).join('、') }}</small></div>
                <div class="branch-actions"><AppButton v-if="!branch.active" variant="ghost" :pending="toolPending === `switch-${branch.id}`" @click="activateBranch(branch)">切换</AppButton><AppButton v-if="branch.parentBranchId" variant="ghost" :pending="toolPending === `diff-${branch.id}`" @click="inspectBranch(branch)">差异</AppButton><AppButton v-if="branch.syncRequired" :pending="toolPending === `sync-${branch.id}`" @click="syncBranch(branch)">确认同步</AppButton><AppButton v-if="branch.branchType === 'LANGUAGE' && !branch.syncRequired && translationStatus(branch) === 'NOT_STARTED' && conversation?.aiAvailable && conversation?.consent.status === 'GRANTED'" variant="ghost" :pending="toolPending === `translate-${branch.id}`" @click="translateBranch(branch)">翻译</AppButton><AppButton v-if="translationStatus(branch) === 'AWAITING_CONFIRMATION'" :pending="toolPending === `confirm-translation-${branch.id}`" @click="confirmBranchTranslation(branch)">确认译文</AppButton></div>
              </article>
            </section>
            <section v-if="branchDiff" class="diff-list"><header><strong>字段差异</strong><span>{{ branchDiff.changes.length }} 项</span></header><p v-if="!branchDiff.changes.length">当前没有字段差异。</p><dl v-else><div v-for="change in branchDiff.changes" :key="change.path"><dt>{{ change.path }}</dt><dd><span>{{ String(change.before ?? '空').slice(0, 100) }}</span><AppIcon name="arrow-right" :size="13" /><strong>{{ String(change.after ?? '空').slice(0, 100) }}</strong></dd></div></dl></section>
          </template>

          <template v-else-if="activeTool === 'history'">
            <div v-if="!revisions.length" class="tool-empty">还没有修订历史。</div>
            <section v-else class="revision-list"><article v-for="revision in revisions" :key="revision.id"><div><span>第 {{ revision.revisionNo }} 版 · {{ revision.source }}</span><strong>{{ contentSummary(revision.content) }}</strong><small>{{ new Date(revision.createdAt).toLocaleString('zh-CN') }}</small></div><AppButton variant="ghost" :pending="toolPending === `restore-${revision.id}`" :disabled="revision.branchId !== conversation?.activeBranchId" @click="restoreRevision(revision)">恢复为新版本</AppButton></article></section>
          </template>

          <template v-else-if="activeTool === 'privacy'">
            <section class="privacy-section">
              <div><h3>写作风格</h3><p>仅使用受控风格，不允许任意提示词覆盖事实规则。</p></div>
              <AppSelect :model-value="writingStyle" ariaLabel="写作风格" :options="writingStyleOptions" :disabled="toolPending === 'privacy-style'" @change="updateWritingStyle" />
            </section>
            <section class="privacy-section privacy-section--row">
              <div><h3>AI 授权</h3><p>{{ conversation?.consent.status === 'GRANTED' ? '已授权' : '未授权' }}</p></div>
              <AppButton v-if="conversation?.consent.status === 'GRANTED'" variant="ghost" :pending="toolPending === 'privacy-consent'" @click="revokeConsent">撤销授权</AppButton>
              <AppButton v-else variant="ghost" :pending="consentPending" @click="grantConsent">授权使用</AppButton>
            </section>
            <section class="privacy-section">
              <div><h3>AI 历史正文</h3><p>保留正式简历和不含正文的最小审计标记。</p></div>
              <div v-if="historyDeletionArmed" class="privacy-confirm">
                <p>同时删除所有消息正文和未决 AI 修改，此操作不能恢复。</p>
                <div><AppButton variant="ghost" @click="historyDeletionArmed = false">取消</AppButton><AppButton variant="danger" :pending="toolPending === 'privacy-delete'" @click="deleteHistory">确认清除</AppButton></div>
              </div>
              <AppButton v-else variant="danger" @click="deleteHistory"><AppIcon name="trash" :size="14" />清除 AI 历史</AppButton>
            </section>
          </template>

        </div>
      </aside>
    </div>

    <AppModal :open="pdfConfirmOpen" title="生成 PDF" :width="500" @close="!pdfPending && (pdfConfirmOpen = false)">
      <div class="pdf-confirm">
        <div class="pdf-confirm__summary">
          <AppIcon name="file-text" :size="20" />
          <div><strong>{{ activeTemplate?.displayName ?? '当前智能模板' }}</strong><span>将冻结当前已确认内容、模板版本和设计设置，再异步生成 PDF。</span></div>
        </div>
        <div class="pdf-mode-control" role="group" aria-label="PDF 导出版本">
          <button type="button" :class="{ active: pdfExportMode === 'STANDARD' }" :aria-pressed="pdfExportMode === 'STANDARD'" @click="pdfExportMode = 'STANDARD'">
            <strong>正式版</strong><span>保留姓名、照片与联系方式</span>
          </button>
          <button type="button" :class="{ active: pdfExportMode === 'ANONYMOUS' }" :aria-pressed="pdfExportMode === 'ANONYMOUS'" @click="pdfExportMode = 'ANONYMOUS'">
            <strong>匿名版</strong><span>隐藏身份信息，保留职业事实</span>
          </button>
        </div>
        <ul v-if="pdfRiskMessages.length" class="pdf-risk-list" :class="{ 'pdf-risk-list--bad': pdfOverflowBlocked }">
          <li v-for="message in pdfRiskMessages" :key="message">{{ message }}</li>
        </ul>
        <p v-else class="pdf-ready"><AppIcon name="check-circle" :size="15" />当前没有发现导出前风险。</p>
        <p class="pdf-confirm__note">{{ pdfExportMode === 'ANONYMOUS'
          ? '匿名版会移除姓名、照片、邮箱、电话、地点和个人链接；岗位、教育、经历、项目和技能仍会保留。'
          : '预览中的未确认草稿和待确认修改不会写入正式 PDF。' }}</p>
      </div>
      <template #footer>
        <AppButton variant="ghost" :disabled="pdfPending" @click="pdfConfirmOpen = false">取消</AppButton>
        <AppButton :pending="pdfPending" :disabled="pdfOverflowBlocked" @click="confirmPdfExport">{{ pdfOverflowBlocked ? '存在溢出，暂不可导出' : `确认并下载${pdfExportMode === 'ANONYMOUS' ? '匿名版' : '正式版'}` }}</AppButton>
      </template>
    </AppModal>
  </main>
</template>

<style scoped>
.ai-workbench { min-height: 100vh; height: 100vh; display: grid; grid-template-rows: 56px 1fr; overflow: hidden; background: #eef1f5; }
.workbench-bar { padding: 0 16px; display: grid; grid-template-columns: 36px minmax(0, 1fr) auto; gap: 10px; align-items: center; background: #fff; border-bottom: 1px solid var(--border); z-index: 4; }
.workbench-bar__title { min-width: 0; display: grid; }
.workbench-bar__title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.workbench-bar__title span { color: var(--text-3); font-size: 11px; }
.workbench-bar__tools { display: flex; align-items: center; gap: 8px; }
.quota { height: 32px; padding: 0 9px; display: inline-flex; align-items: center; gap: 5px; color: #146c43; background: #eaf6ef; border: 1px solid #cdebd9; border-radius: 6px; font-size: 12px; font-weight: 600; }
.workbench-grid { min-height: 0; display: grid; grid-template-columns: minmax(420px, var(--conversation-width, 40%)) 8px minmax(360px, 1fr); }
.workbench-grid.is-split-dragging { cursor: col-resize; user-select: none; }
.conversation-pane { min-width: 0; min-height: 0; display: grid; grid-template-rows: auto 1fr auto; background: #fff; }
.workbench-splitter { min-width: 0; position: relative; z-index: 3; display: grid; place-items: center; cursor: col-resize; touch-action: none; background: #f8fafc; border-right: 1px solid #e3e8ef; border-left: 1px solid #e3e8ef; outline: 0; }
.workbench-splitter::before { content: ''; width: 2px; height: 100%; background: transparent; transition: background-color .18s ease; }
.workbench-splitter > span { width: 14px; height: 42px; position: absolute; display: grid; place-content: center; gap: 3px; background: #fff; border: 1px solid #d7dee8; border-radius: 6px; box-shadow: 0 3px 9px rgba(15, 23, 42, .08); opacity: 0; transform: scale(.92); transition: opacity .18s ease, transform .18s ease, border-color .18s ease; }
.workbench-splitter i { width: 2px; height: 2px; display: block; background: #8190a5; border-radius: 50%; }
.workbench-splitter:hover::before, .workbench-splitter:focus-visible::before, .is-split-dragging .workbench-splitter::before { background: #4f86df; }
.workbench-splitter:hover > span, .workbench-splitter:focus-visible > span, .is-split-dragging .workbench-splitter > span { opacity: 1; transform: scale(1); border-color: #9bb7e4; }
.workbench-splitter:focus-visible { box-shadow: 0 0 0 2px rgba(37, 99, 235, .14); }
.workbench-tabs { min-height: 46px; padding: 5px 10px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; background: #fff; border-bottom: 1px solid var(--border); }
.workbench-tabs button { min-width: 0; display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--text-2); background: transparent; border: 0; border-radius: 5px; font-size: 12px; }
.workbench-tabs button:hover { color: var(--text); background: #f5f7fa; }
.workbench-tabs button.active { color: #174ea6; background: #edf4ff; font-weight: 700; }
.conversation-scroll-shell { min-height: 0; position: relative; overflow: hidden; }
.conversation-scroll { height: 100%; min-height: 0; padding: 18px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; }
.conversation-scroll > * { flex: none; }
.scroll-surface {
  --scroll-edge-top: rgba(15, 23, 42, 0);
  --scroll-edge-bottom: rgba(15, 23, 42, 0);
  overscroll-behavior: contain;
  scrollbar-width: none;
  -ms-overflow-style: none;
  box-shadow:
    inset 0 14px 12px -16px var(--scroll-edge-top),
    inset 0 -14px 12px -16px var(--scroll-edge-bottom);
  transition: box-shadow .18s ease;
}
.scroll-surface.can-scroll-up { --scroll-edge-top: rgba(15, 23, 42, .16); }
.scroll-surface.can-scroll-down { --scroll-edge-bottom: rgba(15, 23, 42, .12); }
.scroll-surface::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}
.conversation-return { position: absolute; right: 16px; bottom: 14px; z-index: 4; width: 36px; height: 36px; display: grid; place-items: center; color: #315f9f; background: rgba(255,255,255,.96); border: 1px solid #cbd8e9; border-radius: 50%; box-shadow: 0 5px 16px rgba(15,23,42,.14); }
.conversation-return:hover { color: #174ea6; border-color: #8eb0ed; background: #fff; }
.conversation-return:active { transform: translateY(1px); }
.scroll-return-enter-active, .scroll-return-leave-active { transition: opacity .16s ease, transform .16s ease; }
.scroll-return-enter-from, .scroll-return-leave-to { opacity: 0; transform: translateY(6px); }
.inline-alert { padding: 9px 11px; border: 1px solid; border-radius: 6px; font-size: 12px; }
.inline-alert--bad { color: #991b1b; background: #fff1f2; border-color: #fecdd3; }
.inline-alert--ok { color: #166534; background: #f0fdf4; border-color: #bbf7d0; }
.pdf-confirm { display: grid; gap: 14px; }
.pdf-confirm__summary { padding: 12px; display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 10px; color: #174ea6; background: #f4f8ff; border: 1px solid #d9e6fb; border-radius: 7px; }
.pdf-confirm__summary > div { min-width: 0; display: grid; gap: 3px; }
.pdf-confirm__summary strong { color: var(--text); font-size: 13px; }
.pdf-confirm__summary span, .pdf-confirm__note { color: var(--text-2); font-size: 11px; line-height: 1.55; }
.pdf-mode-control { min-width: 0; padding: 3px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 3px; background: #f2f4f7; border: 1px solid #d9dee7; border-radius: 7px; }
.pdf-mode-control button { min-width: 0; min-height: 56px; padding: 8px 10px; display: grid; align-content: center; gap: 2px; text-align: left; color: var(--text-2); background: transparent; border: 1px solid transparent; border-radius: 5px; }
.pdf-mode-control button:hover { color: var(--text); background: #f8fafc; }
.pdf-mode-control button.active { color: #174ea6; background: #fff; border-color: #c9d8ee; box-shadow: 0 1px 2px rgb(16 24 40 / 8%); }
.pdf-mode-control strong { font-size: 12px; }
.pdf-mode-control span { overflow-wrap: anywhere; color: var(--text-3); font-size: 10px; line-height: 1.35; }
.pdf-risk-list { padding: 10px 12px 10px 30px; display: grid; gap: 6px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: 7px; font-size: 11px; line-height: 1.5; }
.pdf-risk-list--bad { color: #991b1b; background: #fff1f2; border-color: #fecdd3; }
.pdf-ready { display: flex; align-items: center; gap: 6px; color: #166534; font-size: 12px; }
.ai-status, .consent-strip { padding: 10px 12px; display: flex; gap: 9px; align-items: center; color: #475467; background: #f8fafc; border: 1px solid #dbe2ea; border-radius: 7px; font-size: 12px; }
.consent-strip { justify-content: space-between; }
.consent-strip > div { display: grid; gap: 2px; }
.consent-strip span { color: var(--text-2); }
.chat-thread { display: grid; gap: 14px; padding: 2px 0 8px; }
.message { max-width: 92%; min-width: 0; }
.message__content { min-width: 0; display: grid; gap: 4px; }
.message__content > span { color: var(--text-3); font-size: 11px; }
.message p { padding: 11px 13px; white-space: pre-wrap; overflow-wrap: anywhere; background: #f3f5f8; border: 1px solid transparent; border-radius: 7px; font-size: 13px; line-height: 1.6; }
.message small { color: var(--text-3); font-size: 10px; }
.message--user { max-width: 92%; justify-self: end; display: grid; grid-template-columns: minmax(0, 1fr) 32px; gap: 9px; align-items: start; }
.message--user .message__content > span { text-align: right; }
.message--user p { color: #fff; background: #2563eb; }
.message--assistant { justify-self: start; display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: 9px; align-items: start; }
.message--transient { animation: message-enter .3s cubic-bezier(.2, .8, .2, 1) both; }
.message--welcome p { background: #eef5ff; border: 1px solid #d7e6ff; }
.message--prompt { padding-top: 2px; animation: assistant-prompt-enter .46s cubic-bezier(.2, .8, .2, 1) both; }
.typing-indicator { width: 52px; min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 5px; }
.typing-indicator i { width: 6px; height: 6px; border-radius: 50%; background: #7d8b9e; animation: typing-dot 1.05s ease-in-out infinite; }
.typing-indicator i:nth-child(2) { animation-delay: .14s; }
.typing-indicator i:nth-child(3) { animation-delay: .28s; }
.structured-answer { width: fit-content; padding: 5px 0; display: inline-flex; align-items: center; gap: 5px; color: var(--primary); background: transparent; border: 0; font-size: 11px; }
.structured-answer:hover { text-decoration: underline; }
.guided-card { width: calc(100% - 41px); margin-left: 41px; display: grid; background: #fff; border: 1px solid #d6e0ec; border-radius: 8px; box-shadow: 0 10px 28px rgba(15, 23, 42, .07); overflow: hidden; animation: guided-card-enter .68s .24s cubic-bezier(.16, 1, .3, 1) both; }
.guided-card > header { min-height: 58px; padding: 12px 14px; display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; border-bottom: 1px solid var(--border); }
.guided-card > header > div { display: grid; gap: 3px; }
.guided-card > header strong { font-size: 14px; }
.guided-card > header span, .guided-card > header small { color: var(--text-3); font-size: 10px; }
.guided-fields, .guided-text { padding: 14px; display: grid; gap: 8px; }
.summary-writing-area { gap: 12px; }
.summary-writing-area > label { min-width: 0; display: grid; gap: 6px; color: var(--text-2); font-size: 11px; font-weight: 600; }
.guided-card label { display: grid; gap: 5px; color: var(--text-2); font-size: 11px; font-weight: 600; }
.target-job-field { min-width: 0; display: grid; gap: 6px; }
.target-job-field > span { color: var(--text-2); font-size: 11px; font-weight: 600; }
.guided-card > footer { padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; gap: 12px; border-top: 1px solid var(--border); }
.guided-card > footer > span { color: var(--text-3); font-size: 10px; }
.guided-card > footer > span.bad, .card-editor footer > span.bad { color: var(--danger); }
.guided-card__actions { flex: none; display: inline-flex; align-items: center; gap: 6px; }
.structured-records { padding: 0 14px 14px; display: grid; }
.structured-records--module { padding: 14px; }
.structured-record { min-width: 0; padding: 14px 0; border: 0; border-top: 1px solid var(--border); }
.structured-record:first-child { border-top: 0; }
.structured-record legend { width: 100%; display: flex; justify-content: space-between; align-items: center; color: var(--text); font-size: 12px; font-weight: 700; }
.record-actions { flex: none; display: inline-flex; align-items: center; gap: 2px; }
.record-actions .icon-btn { width: 28px; height: 28px; }
.record-actions .icon-btn:disabled { cursor: not-allowed; opacity: .35; }
.record-actions .icon-btn:disabled:hover { color: var(--text-2); background: transparent; }
.structured-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.structured-grid .field-wide { grid-column: 1 / -1; }
.structured-grid .current-field { align-self: end; min-height: 36px; display: flex; grid-template-columns: 16px auto; align-items: center; }
.structured-grid .current-field input { width: 16px; height: 16px; }
.credential-description > span { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.credential-description > span small { color: var(--success); font-size: 10px; font-weight: 600; }
.credential-description.is-short > span small { color: var(--danger); }
.credential-description.is-short .textarea { border-color: #e4a5a5; background: #fffafa; }
.add-record { min-height: 38px; display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--primary); background: #f7faff; border: 1px dashed #a9c2ef; border-radius: 6px; font-size: 11px; font-weight: 600; }
.choice-inline { width: min(calc(100% - 41px), 430px); margin-left: 41px; padding: 14px; display: grid; gap: 8px; background: #fff; border: 1px solid var(--border); border-radius: 8px; animation: guided-card-enter .62s .2s cubic-bezier(.16, 1, .3, 1) both; }
.choice-inline button { min-height: 40px; padding: 0 12px; display: flex; align-items: center; justify-content: space-between; color: var(--text); background: #fff; border: 1px solid var(--border-strong); border-radius: 6px; font-weight: 600; }
.choice-inline button:hover { color: var(--primary); border-color: #8aacec; background: #f7faff; }
.module-tools { padding-top: 4px; display: grid; gap: 10px; border-top: 1px solid var(--border); }
.module-tools__toggle { width: 100%; min-height: 42px; padding: 5px 2px; display: flex; justify-content: space-between; align-items: center; color: var(--text); background: transparent; border: 0; }
.module-tools__toggle > span { min-width: 0; display: grid; grid-template-columns: 20px auto 1fr; gap: 7px; align-items: center; text-align: left; }
.module-tools__toggle strong { font-size: 12px; }
.module-tools__toggle small { color: var(--text-3); font-size: 10px; }
.card-nav { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; }
.card-nav button { min-width: 0; min-height: 58px; padding: 9px 10px; display: grid; grid-template-columns: 20px 1fr 16px; gap: 7px; align-items: center; text-align: left; color: var(--text); background: #fff; border: 1px solid var(--border); border-radius: 7px; }
.card-nav button.active { color: #174ea6; border-color: #8eb0ed; background: #f5f8ff; }
.card-nav button > span { min-width: 0; display: grid; }
.card-nav strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.card-nav small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-3); font-size: 10px; }
.card-editor { border: 1px solid #d8e0ea; border-radius: 8px; background: #fff; }
.card-editor > header { padding: 13px 14px; display: flex; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--border); }
.card-editor h2 { font-size: 14px; }
.card-editor header p { color: var(--text-3); font-size: 11px; }
.card-editor header > span { color: var(--success); font-size: 11px; white-space: nowrap; }
.card-editor header > span.bad { color: var(--danger); }
.card-editor__fields, .card-editor__textarea { padding: 14px; display: grid; gap: 10px; }
.card-editor__fields--two { grid-template-columns: 1fr 1fr; }
.contact-links { grid-column: 1 / -1; min-width: 0; padding-top: 10px; display: grid; gap: 8px; border-top: 1px solid var(--border); }
.contact-links > header { min-height: 34px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.contact-links > header > div { min-width: 0; display: grid; gap: 2px; }
.contact-links > header strong { font-size: 12px; }
.contact-links > header small, .contact-links > p { color: var(--text-3); font-size: 10px; }
.contact-link-row { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: end; }
.photo-control { grid-column: 1 / -1; min-width: 0; padding-top: 10px; display: grid; grid-template-columns: 48px minmax(0, 1fr) auto 34px; gap: 9px; align-items: center; border-top: 1px solid var(--border); }
.photo-control img, .photo-control__empty { width: 48px; height: 58px; border: 1px solid var(--border); border-radius: 5px; background: #f8fafc; }
.photo-control img { object-fit: cover; }
.photo-control__empty { display: grid; place-items: center; color: var(--text-3); }
.photo-control > div { min-width: 0; display: grid; gap: 2px; }
.photo-control small { color: var(--text-3); font-size: 10px; }
.photo-control__input { display: none; }
.card-editor label { display: grid; gap: 6px; }
.card-editor label > span { color: var(--text-2); font-size: 11px; font-weight: 600; }
.card-editor footer { padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; gap: 12px; border-top: 1px solid var(--border); }
.card-editor footer > span { color: var(--text-3); font-size: 10px; }
.assistant-progress { display: flex; align-items: center; gap: 3px; color: var(--text-2); }
.assistant-progress i { width: 3px; height: 3px; display: inline-block; background: #6f87a6; border-radius: 50%; animation: typing 1.05s infinite ease-in-out; }
.assistant-progress i:nth-child(2) { animation-delay: .14s; }
.assistant-progress i:nth-child(3) { animation-delay: .28s; }
.design-panel, .template-panel { display: grid; gap: 14px; align-content: start; }
.design-panel > header, .template-panel > header { min-height: 46px; display: flex; justify-content: space-between; align-items: center; gap: 12px; border-bottom: 1px solid var(--border); }
.design-panel > header > div, .template-panel > header > div { display: grid; gap: 2px; }
.design-panel > header span, .template-panel > header span, .design-panel > header small, .template-panel > header small { color: var(--text-3); font-size: 10px; }
.design-panel > header small.bad { color: var(--danger); }
.design-presets { min-width: 0; padding: 8px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; border: 1px solid var(--border); border-radius: 6px; }
.design-presets legend { padding: 0 4px; color: var(--text-2); font-size: 11px; font-weight: 600; }
.design-presets button { min-width: 0; min-height: 36px; padding: 7px 9px; border: 1px solid var(--border); border-radius: 5px; background: #fff; color: var(--text-2); font-size: 11px; font-weight: 600; }
.design-presets button.active { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); }
.design-controls { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.design-controls > label { display: grid; gap: 5px; color: var(--text-2); font-size: 11px; font-weight: 600; }
.design-palette { grid-column: 1 / -1; padding: 10px; display: flex; flex-wrap: wrap; gap: 9px; border: 1px solid var(--border); border-radius: 6px; }
.design-palette legend { padding: 0 4px; color: var(--text-2); font-size: 11px; font-weight: 600; }
.design-palette button { width: 34px; height: 34px; position: relative; border: 2px solid #fff; border-radius: 50%; box-shadow: 0 0 0 1px #cbd2dc; }
.design-palette button.active { box-shadow: 0 0 0 2px #2563eb; }
.design-palette button span { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
.section-config { display: grid; border: 1px solid var(--border); border-radius: 7px; overflow: hidden; }
.section-config > header, .section-config article { min-height: 42px; padding: 7px 10px; display: flex; align-items: center; justify-content: space-between; gap: 10px; border-bottom: 1px solid var(--border); }
.section-config article:last-child { border-bottom: 0; }
.section-config header span { color: var(--text-3); font-size: 10px; }
.section-config label, .section-config article > div { display: flex; align-items: center; gap: 8px; }
.section-config label { font-size: 11px; font-weight: 600; }
.section-config input { width: 16px; height: 16px; }
.section-config .icon-btn { width: 28px; height: 28px; }
.template-gallery { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.template-gallery > button { min-width: 0; padding: 8px; display: grid; grid-template-columns: minmax(0, 1fr) 18px; gap: 7px; text-align: left; color: var(--text); background: #fff; border: 1px solid var(--border); border-radius: 7px; }
.template-gallery > button:hover { border-color: #8dacdf; }
.template-gallery > button.active { border-color: #3b73cc; box-shadow: 0 0 0 2px rgba(37,99,235,.08); }
.template-gallery > button > span { min-width: 0; display: grid; gap: 2px; }
.template-gallery > button strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.template-gallery > button small { color: var(--text-3); font-size: 9px; }
.template-gallery > button > .app-icon { color: var(--primary); }
.template-thumb { grid-column: 1 / -1; height: 185px; padding: 6px; overflow: hidden; background: #e8ebef; border-radius: 4px; pointer-events: none; }
.template-thumb :deep(.resume-pages) { width: 128px; margin: 0 auto; }
.template-thumb :deep(.resume-sheet) { border: 0; }
.composer { min-height: 144px; padding: 9px 12px 12px; display: grid; gap: 7px; background: #fff; border-top: 1px solid var(--border); box-shadow: 0 -6px 18px rgba(15, 23, 42, .04); }
.career-evidence-toggle {
  position: relative;
  min-width: 0;
  min-height: 46px;
  display: grid;
  grid-template-columns: 18px 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid #d8dee8;
  border-radius: 7px;
  background: #fff;
  color: var(--text-2);
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease, background-color .18s ease;
}
.career-evidence-toggle:hover { border-color: #b9c4d3; background: #fbfcfe; }
.career-evidence-toggle.is-enabled {
  border-color: #9bbcf2;
  background: #f8fbff;
  box-shadow: inset 3px 0 0 var(--primary);
}
.career-evidence-toggle.is-pending { cursor: wait; }
.career-evidence-toggle input {
  position: absolute;
  z-index: 2;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  cursor: inherit;
}
.career-evidence-toggle__check {
  width: 18px;
  height: 18px;
  display: inline-grid;
  place-items: center;
  border: 1.5px solid #a8b3c2;
  border-radius: 5px;
  background: #fff;
  color: #fff;
  transition: border-color .16s ease, background-color .16s ease, box-shadow .16s ease, transform .16s ease;
}
.career-evidence-toggle:hover .career-evidence-toggle__check { border-color: #7890ad; }
.career-evidence-toggle.is-enabled .career-evidence-toggle__check {
  border-color: var(--primary);
  background: var(--primary);
  box-shadow: 0 2px 6px rgba(37, 99, 235, .2);
}
.career-evidence-toggle input:focus-visible + .career-evidence-toggle__check {
  outline: 2px solid #80aaff;
  outline-offset: 2px;
}
.career-evidence-toggle__icon {
  width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  border-radius: 6px;
  background: #f0f3f7;
  color: #53657b;
  transition: background-color .18s ease, color .18s ease;
}
.career-evidence-toggle.is-enabled .career-evidence-toggle__icon { background: #e7f0ff; color: var(--primary); }
.career-evidence-toggle__copy { min-width: 0; display: grid; gap: 1px; }
.career-evidence-toggle__copy strong {
  overflow: hidden;
  color: var(--text-1);
  font-size: 11.5px;
  font-weight: 700;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.career-evidence-toggle__copy small {
  min-width: 0;
  overflow: hidden;
  color: var(--text-3);
  font-size: 9.5px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.career-evidence-toggle__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #667085;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}
.career-evidence-toggle__status i { width: 6px; height: 6px; border-radius: 50%; background: #98a2b3; }
.career-evidence-toggle.is-enabled .career-evidence-toggle__status { color: #28704f; }
.career-evidence-toggle.is-enabled .career-evidence-toggle__status i { background: #2b8a5f; }
.career-evidence-toggle__status .app-icon { animation: evidence-loader-spin .8s linear infinite; }
.composer__head { min-width: 0; display: flex; justify-content: space-between; gap: 10px; align-items: center; }
.composer__head span { display: inline-flex; align-items: center; gap: 6px; color: var(--text-2); font-size: 11px; font-weight: 600; }
.composer__head i { width: 7px; height: 7px; border-radius: 50%; background: #98a2b3; }
.composer__head i.online { background: #16a34a; }
.composer__head small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-3); font-size: 10px; }
.composer__input { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) 36px; gap: 8px; align-items: end; }
.composer textarea { width: 100%; max-height: 120px; resize: none; padding: 10px 11px; border: 1px solid var(--border-strong); border-radius: 7px; outline: none; font-size: 13px; line-height: 1.45; }
.composer textarea:focus { border-color: #7ca2e8; box-shadow: 0 0 0 2px rgba(37, 99, 235, .09); }
.icon-btn--send { color: #fff; background: var(--primary); border-color: var(--primary); }
.icon-btn--stop { color: #fff; background: #b42318; border-color: #b42318; }
.input, .textarea { transition: border-color .18s ease, box-shadow .18s ease, background-color .18s ease; }
@keyframes typing-dot { 0%, 60%, 100% { opacity: .35; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-3px); } }
@keyframes message-enter { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
@keyframes assistant-prompt-enter { from { opacity: 0; transform: translateX(-12px); filter: blur(2px); } to { opacity: 1; transform: translateX(0); filter: blur(0); } }
@keyframes guided-card-enter { from { opacity: 0; transform: translate3d(-18px, 6px, 0) scale(.992); } to { opacity: 1; transform: translate3d(0, 0, 0) scale(1); } }
@keyframes evidence-loader-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  .message--transient, .message--prompt, .guided-card, .choice-inline, .typing-indicator i { animation: none; }
  .scroll-surface, .scroll-return-enter-active, .scroll-return-leave-active { transition: none; }
  .career-evidence-toggle, .career-evidence-toggle__check, .career-evidence-toggle__icon, .career-evidence-toggle__status .app-icon { transition: none; animation: none; }
}
.preview-pane { min-width: 0; min-height: 0; display: grid; grid-template-rows: auto 1fr auto; background: #eef1f5; }
.preview-tools { min-height: 60px; padding: 10px 18px; display: flex; justify-content: space-between; align-items: center; gap: 16px; background: #f8fafc; border-bottom: 1px solid var(--border); }
.preview-tools > div { display: grid; }
.preview-tools span { color: var(--text-3); font-size: 11px; }
.preview-tools span.preview-status--bad { color: #b42318; font-weight: 600; }
.preview-tools label { display: flex; align-items: center; gap: 7px; color: var(--text-2); font-size: 11px; }
.preview-template { min-width: 170px; height: 38px; padding: 0 9px; display: grid; grid-template-columns: 1fr auto; align-items: center; text-align: left; color: var(--text); background: #fff; border: 1px solid var(--border-strong); border-radius: 6px; }
.preview-template span { grid-column: 1; font-size: 9px; }
.preview-template strong { grid-column: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.preview-template .app-icon { grid-column: 2; grid-row: 1 / 3; }
.preview-stage { min-height: 0; padding: 22px; overflow: auto; }
.preview-stage :deep(.resume-sheet) { width: min(100%, 690px); }
.preview-foot { min-height: 40px; padding: 8px 16px; display: flex; justify-content: space-between; gap: 16px; align-items: center; color: var(--text-3); background: #f8fafc; border-top: 1px solid var(--border); font-size: 11px; }
.preview-foot span { display: flex; align-items: center; gap: 5px; }
.mobile-switch { display: none; }
.workbench-loading { min-height: 0; display: grid; place-content: center; justify-items: center; gap: 10px; color: var(--text-2); }
.tool-trigger { width: 32px; height: 32px; }
.tool-mask { position: fixed; inset: 0; z-index: 80; display: flex; justify-content: flex-end; background: rgba(15, 23, 42, .36); }
.tool-panel { width: min(480px, 100%); height: 100%; display: grid; grid-template-rows: auto 1fr; background: #fff; box-shadow: -12px 0 30px rgba(15, 23, 42, .14); }
.tool-panel__head { min-height: 64px; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; gap: 14px; border-bottom: 1px solid var(--border); }
.tool-panel__head > div { min-width: 0; display: grid; gap: 2px; }
.tool-panel__head strong { font-size: 15px; }
.tool-panel__head span { color: var(--text-3); font-size: 11px; }
.tool-panel__body { min-height: 0; padding: 16px; display: grid; align-content: start; gap: 14px; overflow-y: auto; }
.tool-loading, .tool-empty { min-height: 120px; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--text-3); text-align: center; font-size: 12px; }
.tool-section { display: grid; gap: 8px; }
.tool-section h3, .interview-setup h3 { font-size: 13px; }
.tool-section p { color: var(--text-3); font-size: 11px; }
.branch-create > div { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.branch-create .branch-select { min-width: 0; }
.tool-link { width: fit-content; color: var(--primary); font-size: 12px; }
.language-action { width: fit-content; padding: 4px 0; display: inline-flex; align-items: center; gap: 5px; color: var(--primary); background: transparent; border: 0; font-size: 12px; }
.branch-list, .revision-list { display: grid; border-top: 1px solid var(--border); }
.branch-list article, .revision-list article { padding: 13px 0; display: flex; justify-content: space-between; align-items: center; gap: 12px; border-bottom: 1px solid var(--border); }
.branch-list article.active { box-shadow: inset 3px 0 var(--primary); padding-left: 10px; }
.branch-main, .revision-list article > div { min-width: 0; display: grid; gap: 3px; }
.branch-main span, .revision-list span { color: var(--primary); font-size: 10px; font-weight: 600; }
.branch-main strong, .revision-list strong { overflow-wrap: anywhere; font-size: 12px; }
.branch-main small, .revision-list small { color: var(--text-3); font-size: 10px; }
.branch-main .proper-name-review { color: #9a3412; }
.branch-actions { flex: none; display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 5px; }
.branch-actions .btn, .revision-list .btn { min-height: 30px; padding: 0 8px; font-size: 11px; }
.diff-list { display: grid; gap: 8px; padding-top: 4px; }
.diff-list header { display: flex; justify-content: space-between; }
.diff-list header span, .diff-list > p { color: var(--text-3); font-size: 11px; }
.diff-list dl { display: grid; }
.diff-list dl > div { padding: 9px 0; display: grid; gap: 5px; border-top: 1px solid var(--border); }
.diff-list dt { color: var(--text-3); font-size: 10px; }
.diff-list dd { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) 14px minmax(0, 1fr); gap: 5px; align-items: center; font-size: 10px; }
.diff-list dd span, .diff-list dd strong { overflow-wrap: anywhere; }
.privacy-section { padding-bottom: 14px; display: grid; gap: 10px; border-bottom: 1px solid var(--border); }
.privacy-section:last-child { border-bottom: 0; }
.privacy-section > div:first-child { display: grid; gap: 3px; }
.privacy-section h3 { font-size: 13px; }
.privacy-section p { color: var(--text-3); font-size: 11px; }
.privacy-section--row { grid-template-columns: minmax(0, 1fr) auto; align-items: center; }
.privacy-confirm { padding: 10px; display: grid; gap: 9px; background: #fff7f7; border: 1px solid #fecaca; border-radius: 7px; }
.privacy-confirm > div { display: flex; justify-content: flex-end; gap: 7px; }
.interview-setup { display: grid; gap: 12px; }
.interview-setup label { display: grid; gap: 6px; }
.interview-setup label span { color: var(--text-2); font-size: 11px; font-weight: 600; }
.interview-session { display: grid; gap: 12px; }
.interview-session > header { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.interview-session > header > div { display: grid; gap: 2px; }
.interview-session > header span { color: var(--text-3); font-size: 11px; }
.interview-turn, .interview-answer { padding-top: 12px; display: grid; gap: 8px; border-top: 1px solid var(--border); }
.interview-turn > span, .interview-answer > span { color: var(--primary); font-size: 10px; font-weight: 600; }
.interview-turn strong, .interview-answer strong { font-size: 12px; line-height: 1.55; }
.interview-turn p { padding: 9px 10px; background: #f6f8fb; font-size: 12px; white-space: pre-wrap; }
.interview-turn small { color: var(--text-2); font-size: 10px; }
@media (max-width: 860px) {
  .ai-workbench { grid-template-rows: 56px 42px 1fr; }
  .workbench-bar__title span, .quota, .workbench-bar__tools .manual-link { display: none; }
  .workbench-bar__tools .btn { padding: 0 10px; }
  .mobile-switch { display: grid; grid-template-columns: 1fr 1fr; padding: 5px; gap: 4px; background: #fff; border-bottom: 1px solid var(--border); }
  .mobile-switch button { border: 0; border-radius: 5px; background: transparent; color: var(--text-2); }
  .mobile-switch button.active { color: var(--primary); background: var(--primary-soft); font-weight: 600; }
  .workbench-grid { grid-template-columns: 1fr; }
  .workbench-splitter { display: none; }
  .conversation-pane, .preview-pane { grid-column: 1; grid-row: 1; border-right: 0; }
  .is-mobile-hidden { display: none; }
  .conversation-scroll { padding: 12px; }
  .conversation-return { right: 10px; bottom: 10px; }
  .preview-stage { padding: 12px; }
}
@media (max-width: 520px) {
  .workbench-bar { padding: 0 9px; }
  .workbench-bar__tools .btn { font-size: 0; width: 36px; padding: 0; }
  .workbench-bar__tools .btn .app-icon { margin: auto; }
  .career-evidence-toggle { min-height: 42px; grid-template-columns: 18px 26px minmax(0, 1fr) auto; gap: 7px; padding: 6px 8px; }
  .career-evidence-toggle__icon { width: 26px; height: 26px; }
  .career-evidence-toggle__copy small { display: none; }
  .card-nav { grid-template-columns: 1fr 1fr; }
  .card-editor__fields--two { grid-template-columns: 1fr; }
  .photo-control { grid-column: 1; }
  .card-editor footer { align-items: stretch; flex-direction: column; }
  .card-editor footer .btn { width: 100%; }
  .preview-tools { padding: 8px 10px; }
  .preview-tools > div span { display: none; }
  .preview-tools > div span.preview-status--bad { display: block; max-width: 190px; line-height: 1.3; }
  .preview-foot { align-items: flex-start; }
  .tool-panel__body { padding: 12px; }
  .branch-list article, .revision-list article { align-items: stretch; flex-direction: column; }
  .branch-actions { justify-content: flex-start; }
  .structured-grid { grid-template-columns: 1fr; }
  .structured-grid .field-wide { grid-column: 1; }
  .guided-card > footer { align-items: stretch; flex-direction: column; }
  .guided-card__actions { width: 100%; display: grid; grid-template-columns: minmax(0, .72fr) minmax(0, 1fr); }
  .guided-card > footer .btn { width: 100%; }
  .design-controls, .template-gallery { grid-template-columns: 1fr; }
}
.workbench-tabs { position: relative; isolation: isolate; }
.workbench-tabs::before { position: absolute; z-index: 0; top: 5px; bottom: 5px; left: 10px; width: calc((100% - 32px) / 4); border-radius: 5px; background: var(--primary-soft); content: ''; pointer-events: none; transform: translateX(0); transition: transform var(--motion-slow) var(--motion-ease), box-shadow var(--motion-base) var(--motion-ease); }
.workbench-tabs:has(button:nth-child(2).active)::before { transform: translateX(calc(100% + 4px)); }
.workbench-tabs:has(button:nth-child(3).active)::before { transform: translateX(calc(200% + 8px)); }
.workbench-tabs:has(button:nth-child(4).active)::before { transform: translateX(calc(300% + 12px)); }
.workbench-tabs button { position: relative; z-index: 1; transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease); }
.workbench-tabs button.active { background: transparent; }
.workbench-tabs button:active { transform: scale(.97); }
.chat-thread, .module-tools, .design-panel, .template-panel { animation: workbench-content-in var(--motion-base) var(--motion-ease-out) both; }
@keyframes workbench-content-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 860px) {
  .mobile-switch { position: relative; isolation: isolate; }
  .mobile-switch::before { position: absolute; z-index: 0; inset: 5px auto 5px 5px; width: calc((100% - 14px) / 2); border-radius: 5px; background: var(--primary-soft); content: ''; pointer-events: none; transition: transform var(--motion-slow) var(--motion-ease); }
  .mobile-switch:has(button:nth-child(2).active)::before { transform: translateX(calc(100% + 4px)); }
  .mobile-switch button { position: relative; z-index: 1; transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease); }
  .mobile-switch button.active { background: transparent; }
}
@media (prefers-reduced-motion: reduce) { .workbench-tabs::before, .workbench-tabs button, .mobile-switch::before, .mobile-switch button { transition: none; } .workbench-tabs button:active { transform: none; } .chat-thread, .module-tools, .design-panel, .template-panel { animation: none; } }
</style>
