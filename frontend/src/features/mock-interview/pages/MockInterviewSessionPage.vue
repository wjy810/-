<script setup lang="ts">
/**
 * The interview room. Everything shown here is real: the clock is the server's active time,
 * scores appear only after the model evaluated an answer, and voice answers are transcribed by
 * the browser's speech recognition (no audio leaves the device).
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlertTriangle, ArrowLeft, CheckCircle2, Circle, Clock3, FileText, Hourglass, LoaderCircle, Lock, MessageCircle,
  Mic, MicOff, Pause, RefreshCw, Send, Shield, Square, Target, Volume2, WifiOff, X,
} from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { createDraftSaveQueue } from '@/shared/lib/draftSaveQueue'
import { useToastFeedback } from '@/shared/ui/toast'
import {
  abandonMockInterview, completeMockInterview, fetchMockInterview, pauseMockInterview, resumeMockInterview,
  saveMockInterviewTextDraft, saveMockInterviewTranscript, submitMockInterviewAnswer, switchMockInterviewMode,
} from '../services/mockInterviewApi'
import type { MockInterviewAnswer, MockInterviewSession } from '../types'
import {
  interviewerPitch,
  interviewerVoiceProfile,
  mergeSpeechTranscript,
  pickInterviewerVoice,
} from '../utils/voiceInteraction'

type MicrophoneState = 'idle' | 'requesting' | 'recording' | 'finishing' | 'failed'
type TranscriptionState = 'idle' | 'listening' | 'unsupported' | 'error'
type QuestionSpeechState = 'idle' | 'loading' | 'speaking' | 'done' | 'error'
type SpeechRecognitionAlternativeLike = { transcript: string }
type SpeechRecognitionResultLike = {
  isFinal: boolean
  length: number
  [index: number]: SpeechRecognitionAlternativeLike
}
type SpeechRecognitionEventLike = Event & {
  results: { length: number; [index: number]: SpeechRecognitionResultLike }
}
type SpeechRecognitionErrorLike = Event & { error: string }
type SpeechRecognitionLike = {
  continuous: boolean
  interimResults: boolean
  lang: string
  onstart: (() => void) | null
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: SpeechRecognitionErrorLike) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
}
type SpeechRecognitionConstructor = new () => SpeechRecognitionLike

const TYPE_LABELS: Record<string, string> = {
  COMPREHENSIVE: '综合面试', BEHAVIORAL: '行为面试', PROFESSIONAL: '专业面试', PRESSURE: '压力面试', QUICK: '快速热身',
}
const STATUS_LABELS: Record<string, string> = {
  READY: '准备中', PAUSED: '已暂停', COMPLETED: '已完成', ABANDONED: '已放弃',
}
const WAVE_BARS = 32

const route = useRoute()
const router = useRouter()
const sessionId = computed(() => String(route.params.sessionId ?? ''))
const data = ref<MockInterviewSession | null>(null)
const loading = ref(true)
const loadFailed = ref(false)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'mock-interview-session-error')
const answer = ref('')
const answerVersion = ref<number | undefined>()
const saveState = ref<'idle' | 'saving' | 'saved' | 'local'>('idle')
const submitting = ref(false)
const evaluating = ref(false)
const feedbackOpen = ref(false)
const feedbackAnswer = ref<MockInterviewAnswer | null>(null)
const pauseOpen = ref(false)
const endOpen = ref(false)
const abandonConfirmOpen = ref(false)
const permissionOpen = ref(false)
const offline = ref(!navigator.onLine)
const elapsed = ref(0)
const microphoneState = ref<MicrophoneState>('idle')
const transcriptionState = ref<TranscriptionState>('idle')
const questionSpeechState = ref<QuestionSpeechState>('idle')
const recordingSeconds = ref(0)
const waveform = ref<number[]>(Array.from({ length: WAVE_BARS }, () => 6))
const liveTranscript = ref('')
let timer = 0
let recordingTimer = 0
let waveformTimer = 0
let mediaStream: MediaStream | null = null
let audioContext: AudioContext | null = null
let speechRecognition: SpeechRecognitionLike | null = null
let recognitionShouldRestart = false
let recognitionBaseTranscript = ''
let recognitionRestartTimer = 0
let recognitionStopTimer = 0
let recognitionEndResolver: (() => void) | null = null
let questionSpeechTimer = 0
let questionSpeechGeneration = 0
let activeUtterance: SpeechSynthesisUtterance | null = null
let disposed = false
let recordingGeneration = 0
let hydratingAnswer = false

const current = computed(() => data.value?.currentQuestion ?? null)
const summary = computed(() => data.value?.session ?? null)
const mode = computed(() => summary.value?.mode ?? 'TEXT')
const status = computed(() => summary.value?.status ?? 'IN_PROGRESS')
const statusLabel = computed(() => STATUS_LABELS[status.value] ?? '进行中')
const typeLabel = computed(() => TYPE_LABELS[summary.value?.interviewType ?? ''] ?? '模拟面试')
const currentAnswer = computed(() => data.value?.answers.find((item) => item.questionId === current.value?.id) ?? null)
const feedback = computed(() => feedbackAnswer.value?.feedback ?? {})
const feedbackScore = computed(() => {
  const value = feedbackAnswer.value?.scores.overall
  return String(feedback.value.generationMode ?? '') === 'AI_MODEL' && typeof value === 'number' ? value : null
})
const progressPercent = computed(() => summary.value ? Math.round((summary.value.answeredCount / summary.value.questionCount) * 100) : 0)
const limitMinutes = computed(() => Number(summary.value?.durationMinutes ?? data.value?.settingsSnapshot.durationMinutes ?? 30))
const overTime = computed(() => elapsed.value > limitMinutes.value * 60)
const clockRunning = computed(() => !['PAUSED', 'COMPLETED', 'ABANDONED'].includes(status.value) && !pauseOpen.value)
const canSubmit = computed(() => answer.value.trim().length >= 2 && !submitting.value)
const questionNumber = computed(() => current.value?.orderNo ?? Math.min((summary.value?.answeredCount ?? 0) + 1, summary.value?.questionCount ?? 1))
const interviewerProfile = computed(() => interviewerVoiceProfile(questionNumber.value))
const interviewerLabel = computed(() => interviewerProfile.value === 'FEMALE' ? '女声面试官' : '男声面试官')
const microphoneLabel = computed(() => ({
  idle: '麦克风未开启',
  requesting: '正在请求麦克风权限',
  recording: `录音中 ${formatSeconds(recordingSeconds.value)}`,
  finishing: '正在结束转写',
  failed: '麦克风不可用',
})[microphoneState.value])
const transcriptionLabel = computed(() => ({
  idle: '开始录音后自动转写',
  listening: '正在转写',
  unsupported: '当前浏览器不支持',
  error: '转写暂时中断',
})[transcriptionState.value])
const questionSpeechLabel = computed(() => ({
  idle: '准备播放题目',
  loading: '正在准备声线',
  speaking: '正在提问，请仔细听',
  done: '题目播放完毕',
  error: '题目语音播放失败',
})[questionSpeechState.value])
const saveText = computed(() => ({
  idle: '开始输入后自动保存',
  saving: '正在保存',
  saved: '已自动保存',
  local: '本地草稿已保存 · 等待同步',
})[saveState.value])
const localKey = computed(() => `jobproof:mock-interview:${sessionId.value}:${current.value?.id ?? 'none'}`)
const usesAiQuestions = computed(() => String(data.value?.settingsSnapshot.questionGenerationMode ?? 'BASIC_RULES') === 'AI_MODEL')
const resumeTitle = computed(() => String(data.value?.resumeSnapshot.title || '已选简历'))
const careerRecordCount = computed(() => list(data.value?.materialsSnapshot.careerRecordIds).length
  + list(data.value?.materialsSnapshot.careerFileIds).length)
const hasJobDescription = computed(() => Object.keys(data.value?.jdSnapshot ?? {}).length > 0)

// The server's active time is the truth; the page only ticks between responses.
watch(() => data.value?.session, (next) => {
  if (next && typeof next.elapsedSeconds === 'number') elapsed.value = Math.max(0, next.elapsedSeconds)
})

async function load(): Promise<void> {
  const requestedSessionId = sessionId.value
  loading.value = true
  loadFailed.value = false
  pageError.value = ''
  try {
    const loaded = await fetchMockInterview(requestedSessionId)
    if (disposed || sessionId.value !== requestedSessionId) return
    data.value = loaded
    hydrateAnswer()
    if (loaded.session.status === 'PAUSED') pauseOpen.value = true
  } catch (reason) {
    if (!disposed) {
      loadFailed.value = true
      pageError.value = errorMessage(reason, '模拟面试读取失败')
    }
  } finally {
    loading.value = false
    await nextTick()
    if (!disposed) scheduleQuestionSpeech()
  }
}

function hydrateAnswer(): void {
  const saved = currentAnswer.value
  const local = answerSaver.readRecovery()
  hydratingAnswer = true
  answer.value = local ?? saved?.answer ?? saved?.transcript ?? ''
  hydratingAnswer = false
  const hasLocalChanges = local !== null && local !== (saved?.answer ?? saved?.transcript ?? '')
  answerSaver.reset(hasLocalChanges)
  liveTranscript.value = local ?? saved?.transcript ?? answer.value
  answerVersion.value = saved?.version
  saveState.value = hasLocalChanges ? 'local' : saved ? 'saved' : 'idle'
  if (hasLocalChanges && !offline.value) answerSaver.schedule(850)
}

watch(() => current.value?.id, (questionId, previousQuestionId) => {
  hydrateAnswer()
  if (questionId && questionId !== previousQuestionId && !loading.value) scheduleQuestionSpeech()
}, { flush: 'sync' })
watch(answer, () => {
  if (!current.value || loading.value || hydratingAnswer || disposed) return
  answerSaver.changed(submitting.value ? null : 850)
}, { flush: 'sync' })

const answerSaver = createDraftSaveQueue({
  identity: () => `${localKey.value}:${mode.value}`,
  snapshot: () => current.value ? {
    sessionId: sessionId.value, questionId: current.value.id,
    mode: mode.value, answer: answer.value, version: answerVersion.value,
  } : null,
  recovery: { key: () => localKey.value, serialize: (snapshot) => snapshot.answer },
  canSave: () => !offline.value,
  save: (snapshot) => snapshot.mode === 'VOICE'
    ? saveMockInterviewTranscript(snapshot.sessionId, snapshot.questionId, snapshot.answer, snapshot.version)
    : saveMockInterviewTextDraft(snapshot.sessionId, snapshot.questionId, snapshot.answer, snapshot.version),
  accept: (saved, snapshot) => {
    answerVersion.value = saved.version
    const index = data.value?.answers.findIndex((item) => item.questionId === snapshot.questionId) ?? -1
    if (index >= 0) data.value!.answers.splice(index, 1, saved)
    else data.value?.answers.push(saved)
  },
  onState: (state) => {
    saveState.value = state === 'error' || (state === 'waiting' && offline.value) ? 'local'
      : state === 'waiting' ? 'saving' : state
  },
  onError: (reason) => { pageError.value = errorMessage(reason, '服务端草稿暂未保存，本地副本仍保留') },
  onRecoveryError: (operation) => {
    if (operation === 'read') pageError.value = '无法读取本地回答副本，请确认浏览器允许本地存储。'
    if (operation === 'write') pageError.value = '浏览器无法保留本地回答副本，请在离开前确认服务端保存成功。'
  },
})

function persistDraft(): Promise<boolean> {
  return answerSaver.flush()
}

function openReport(): Promise<unknown> {
  return router.replace({ name: 'mock-interview-report', params: { sessionId: sessionId.value } })
}

async function submit(): Promise<void> {
  if (!current.value || !canSubmit.value || offline.value) return
  submitting.value = true
  pageError.value = ''
  await stopRecording(false)
  try {
    if (!(await persistDraft()) || disposed || !current.value) return
    evaluating.value = true
    const questionId = current.value.id
    const submittedSessionId = sessionId.value
    const submittedKey = localKey.value
    const submittedAnswer = answer.value
    const submittedRevision = answerSaver.revision
    const updated = await submitMockInterviewAnswer(submittedSessionId, questionId, submittedAnswer, answerVersion.value)
    if (disposed || sessionId.value !== submittedSessionId || localKey.value !== submittedKey) return
    if (answerSaver.revision === submittedRevision) answerSaver.discardRecovery(submittedAnswer)
    const showFeedback = String(updated.settingsSnapshot.feedbackMode ?? 'AFTER_SESSION') === 'AFTER_EACH'
    const submitted = showFeedback ? updated.answers.find((item) => item.questionId === questionId) : undefined
    data.value = updated
    if (submitted) {
      // The drawer keeps the submitted answer's feedback while the next question loads behind it.
      feedbackAnswer.value = submitted
      feedbackOpen.value = true
    } else if (updated.session.status === 'COMPLETED') {
      await openReport()
    }
  } catch (reason) {
    pageError.value = errorMessage(reason, '回答提交失败')
  } finally {
    evaluating.value = false
    submitting.value = false
    if (!disposed && answerSaver.dirty) {
      saveState.value = offline.value ? 'local' : 'saving'
      answerSaver.schedule(850)
    }
  }
}

async function onKeydown(event: KeyboardEvent): Promise<void> {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); await submit() }
}

function speechRecognitionConstructor(): SpeechRecognitionConstructor | null {
  const speechWindow = window as Window & {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null
}

async function availableVoices(): Promise<SpeechSynthesisVoice[]> {
  const currentVoices = window.speechSynthesis.getVoices()
  if (currentVoices.length) return currentVoices
  return new Promise((resolve) => {
    const finish = () => {
      window.clearTimeout(timeout)
      window.speechSynthesis.removeEventListener('voiceschanged', finish)
      resolve(window.speechSynthesis.getVoices())
    }
    const timeout = window.setTimeout(finish, 400)
    window.speechSynthesis.addEventListener('voiceschanged', finish, { once: true })
  })
}

function stopQuestionSpeech(nextState: QuestionSpeechState = 'idle'): void {
  window.clearTimeout(questionSpeechTimer)
  questionSpeechGeneration += 1
  activeUtterance = null
  if ('speechSynthesis' in window) window.speechSynthesis.cancel()
  questionSpeechState.value = nextState
}

async function speakCurrentQuestion(): Promise<void> {
  if (mode.value !== 'VOICE' || !current.value) return
  if (!('speechSynthesis' in window) || typeof SpeechSynthesisUtterance === 'undefined') {
    questionSpeechState.value = 'error'
    pageError.value = '当前浏览器无法播放面试题语音，可以展开题目文字作答。'
    return
  }
  stopQuestionSpeech('loading')
  const speechGeneration = questionSpeechGeneration
  try {
    const profile = interviewerProfile.value
    const languageCode = String(data.value?.settingsSnapshot.languageCode ?? 'zh-CN')
    const voices = await availableVoices()
    if (speechGeneration !== questionSpeechGeneration || mode.value !== 'VOICE' || !current.value) return
    const utterance = new SpeechSynthesisUtterance(current.value.prompt)
    utterance.lang = languageCode
    utterance.rate = languageCode.toLocaleLowerCase().startsWith('zh') ? 0.94 : 0.9
    utterance.pitch = interviewerPitch(profile)
    utterance.volume = 1
    utterance.voice = pickInterviewerVoice(voices, profile, languageCode) ?? null
    activeUtterance = utterance
    utterance.onstart = () => {
      if (activeUtterance === utterance) questionSpeechState.value = 'speaking'
    }
    utterance.onend = () => {
      if (activeUtterance !== utterance) return
      activeUtterance = null
      questionSpeechState.value = 'done'
    }
    utterance.onerror = () => {
      if (activeUtterance !== utterance) return
      activeUtterance = null
      questionSpeechState.value = 'error'
      pageError.value = '题目语音播放失败，可以点击“重听题目”重试。'
    }
    window.speechSynthesis.speak(utterance)
  } catch (reason) {
    questionSpeechState.value = 'error'
    pageError.value = errorMessage(reason, '题目语音播放失败，可以点击“重听题目”重试。')
  }
}

function scheduleQuestionSpeech(): void {
  window.clearTimeout(questionSpeechTimer)
  if (disposed || loading.value || mode.value !== 'VOICE' || pauseOpen.value || feedbackOpen.value || !current.value) return
  questionSpeechTimer = window.setTimeout(() => void speakCurrentQuestion(), 320)
}

function updateLiveTranscript(event: SpeechRecognitionEventLike): void {
  if (disposed) return
  const confirmed: string[] = []
  const interim: string[] = []
  for (let resultIndex = 0; resultIndex < event.results.length; resultIndex += 1) {
    const result = event.results[resultIndex]
    const text = result?.[0]?.transcript?.trim()
    if (!text) continue
    if (result.isFinal) confirmed.push(text)
    else interim.push(text)
  }
  const nextTranscript = mergeSpeechTranscript(recognitionBaseTranscript, confirmed.join(' '), interim.join(' '))
  liveTranscript.value = nextTranscript
  answer.value = nextTranscript
}

function restartLiveTranscription(): void {
  if (disposed || !recognitionShouldRestart || microphoneState.value !== 'recording' || !speechRecognition) return
  recognitionBaseTranscript = liveTranscript.value.trim()
  try {
    speechRecognition.start()
  } catch {
    transcriptionState.value = 'error'
    recognitionShouldRestart = false
    pageError.value = '浏览器转写意外中断，已识别的文字仍保留；可以手动补充或校正。'
  }
}

function startLiveTranscription(): void {
  const Recognition = speechRecognitionConstructor()
  if (!Recognition) {
    transcriptionState.value = 'unsupported'
    pageError.value = '当前浏览器不支持语音转写，请使用 Chrome 或 Edge，或直接输入文字回答。'
    return
  }
  speechRecognition?.abort()
  speechRecognition = new Recognition()
  recognitionShouldRestart = true
  recognitionBaseTranscript = liveTranscript.value.trim()
  speechRecognition.continuous = true
  speechRecognition.interimResults = true
  speechRecognition.lang = String(data.value?.settingsSnapshot.languageCode ?? 'zh-CN')
  speechRecognition.onstart = () => { transcriptionState.value = 'listening' }
  speechRecognition.onresult = updateLiveTranscript
  speechRecognition.onerror = (event) => {
    if (event.error === 'aborted') return
    if (event.error === 'no-speech') {
      transcriptionState.value = 'listening'
      return
    }
    transcriptionState.value = 'error'
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      recognitionShouldRestart = false
      pageError.value = '浏览器未允许语音识别，请检查麦克风与语音识别权限，或直接输入文字回答。'
    } else {
      pageError.value = '浏览器转写服务暂时不可用，已识别的文字仍保留；可以手动补充或校正。'
    }
  }
  speechRecognition.onend = () => {
    recognitionEndResolver?.()
    recognitionEndResolver = null
    window.clearTimeout(recognitionStopTimer)
    if (recognitionShouldRestart && microphoneState.value === 'recording') {
      recognitionRestartTimer = window.setTimeout(restartLiveTranscription, 180)
      return
    }
    speechRecognition = null
    if (transcriptionState.value === 'listening') transcriptionState.value = 'idle'
  }
  try {
    speechRecognition.start()
  } catch {
    speechRecognition = null
    recognitionShouldRestart = false
    transcriptionState.value = 'error'
    pageError.value = '浏览器转写启动失败，可以直接输入文字回答。'
  }
}

async function stopLiveTranscription(): Promise<void> {
  recognitionShouldRestart = false
  window.clearTimeout(recognitionRestartTimer)
  const activeRecognition = speechRecognition
  if (!activeRecognition) {
    if (transcriptionState.value === 'listening') transcriptionState.value = 'idle'
    return
  }
  await new Promise<void>((resolve) => {
    let completed = false
    const finish = () => {
      if (completed) return
      completed = true
      resolve()
    }
    recognitionEndResolver = finish
    recognitionStopTimer = window.setTimeout(finish, 650)
    try { activeRecognition.stop() } catch { finish() }
  })
  speechRecognition = null
  if (transcriptionState.value === 'listening') transcriptionState.value = 'idle'
}

function abortLiveTranscription(): void {
  recognitionShouldRestart = false
  window.clearTimeout(recognitionRestartTimer)
  window.clearTimeout(recognitionStopTimer)
  recognitionEndResolver?.()
  recognitionEndResolver = null
  speechRecognition?.abort()
  speechRecognition = null
}

function releaseMicrophone(): void {
  mediaStream?.getTracks().forEach((track) => track.stop())
  mediaStream = null
  if (audioContext) void audioContext.close()
  audioContext = null
  window.clearInterval(recordingTimer)
  window.clearInterval(waveformTimer)
  waveform.value = Array.from({ length: WAVE_BARS }, () => 6)
}

/** The microphone only drives the level meter and the browser's recognizer; nothing is recorded. */
async function startRecording(): Promise<void> {
  if (disposed || !current.value || mode.value !== 'VOICE' || !['idle', 'failed'].includes(microphoneState.value)) return
  const generation = ++recordingGeneration
  const recordingKey = localKey.value
  stopQuestionSpeech('done')
  microphoneState.value = 'requesting'
  permissionOpen.value = false
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: false })
    if (disposed || generation !== recordingGeneration || recordingKey !== localKey.value || mode.value !== 'VOICE') {
      stream.getTracks().forEach((track) => track.stop())
      return
    }
    mediaStream = stream
    microphoneState.value = 'recording'
    recordingSeconds.value = 0
    recordingTimer = window.setInterval(() => { recordingSeconds.value += 1 }, 1000)
    startWaveform(stream)
    startLiveTranscription()
  } catch {
    if (disposed || generation !== recordingGeneration) return
    abortLiveTranscription()
    releaseMicrophone()
    microphoneState.value = 'failed'
    permissionOpen.value = true
  }
}

async function stopRecording(saveTranscript = true): Promise<void> {
  // A pending browser permission prompt cannot be cancelled, but its result can be invalidated.
  if (microphoneState.value === 'requesting') recordingGeneration += 1
  if (microphoneState.value === 'recording') microphoneState.value = 'finishing'
  await stopLiveTranscription()
  releaseMicrophone()
  microphoneState.value = 'idle'
  if (saveTranscript && liveTranscript.value.trim()) answer.value = liveTranscript.value.trim()
}

function toggleRecording(): void {
  void (microphoneState.value === 'recording' ? stopRecording() : startRecording())
}

function startWaveform(stream: MediaStream): void {
  if (typeof AudioContext === 'undefined') return
  if (audioContext) void audioContext.close()
  audioContext = new AudioContext()
  const analyser = audioContext.createAnalyser()
  analyser.fftSize = 128
  audioContext.createMediaStreamSource(stream).connect(analyser)
  const values = new Uint8Array(analyser.frequencyBinCount)
  waveformTimer = window.setInterval(() => {
    analyser.getByteFrequencyData(values)
    waveform.value = Array.from({ length: WAVE_BARS }, (_, index) => Math.max(6, Math.round((values[index % values.length] / 255) * 48)))
  }, 90)
}

async function switchToText(): Promise<void> {
  stopQuestionSpeech()
  await stopRecording()
  try {
    if (!(await persistDraft()) || disposed) return
    data.value = await switchMockInterviewMode(sessionId.value, 'TEXT')
    permissionOpen.value = false
    answer.value = liveTranscript.value || answer.value
  } catch (reason) { pageError.value = errorMessage(reason, '切换文字模式失败') }
}

async function pauseSession(): Promise<void> {
  stopQuestionSpeech()
  await stopRecording()
  try { data.value = await pauseMockInterview(sessionId.value); pauseOpen.value = true }
  catch (reason) { pageError.value = errorMessage(reason, '暂停失败') }
}

async function continueSession(): Promise<void> {
  try {
    if (status.value === 'PAUSED') data.value = await resumeMockInterview(sessionId.value)
    pauseOpen.value = false
    scheduleQuestionSpeech()
  } catch (reason) { pageError.value = errorMessage(reason, '恢复失败') }
}

async function finishEarly(): Promise<void> {
  stopQuestionSpeech()
  await stopRecording()
  try { await completeMockInterview(sessionId.value); await openReport() }
  catch (reason) { pageError.value = errorMessage(reason, '阶段报告生成失败') }
}

async function editFeedbackAnswer(): Promise<void> {
  if (!data.value || !feedbackAnswer.value) return
  const question = data.value.questions.find((item) => item.id === feedbackAnswer.value?.questionId)
  if (!question) return
  data.value.currentQuestion = question
  feedbackOpen.value = false
  await nextTick()
  hydrateAnswer()
  scheduleQuestionSpeech()
}

function continueAfterFeedback(): void {
  feedbackOpen.value = false
  if (status.value === 'COMPLETED') {
    void openReport()
    return
  }
  hydrateAnswer()
  scheduleQuestionSpeech()
}

async function abandon(): Promise<void> {
  stopQuestionSpeech()
  await stopRecording(false)
  try { await abandonMockInterview(sessionId.value); await router.replace({ name: 'mock-interviews' }) }
  catch (reason) { pageError.value = errorMessage(reason, '放弃记录失败') }
}

function questionState(question: { id: string; status: string }): 'done' | 'current' | 'upcoming' {
  if (question.status === 'COMPLETED') return 'done'
  return question.id === current.value?.id ? 'current' : 'upcoming'
}

function onOnline(): void { offline.value = false; void persistDraft() }
function onOffline(): void { offline.value = true; saveState.value = 'local' }
function list(value: unknown): string[] { return Array.isArray(value) ? value.map(String).filter(Boolean) : [] }
function formatSeconds(value: number): string { const minutes = Math.floor(value / 60); const seconds = value % 60; return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}` }

onMounted(() => {
  window.addEventListener('online', onOnline)
  window.addEventListener('offline', onOffline)
  timer = window.setInterval(() => { if (data.value && clockRunning.value) elapsed.value += 1 }, 1000)
  void load()
})
onBeforeUnmount(() => {
  disposed = true
  answerSaver.dispose()
  recordingGeneration += 1
  window.clearInterval(timer); window.clearTimeout(questionSpeechTimer)
  window.removeEventListener('online', onOnline); window.removeEventListener('offline', onOffline)
  stopQuestionSpeech(); abortLiveTranscription()
  releaseMicrophone()
})
</script>

<template>
  <main class="mi-stage" :class="{ 'has-drawer': feedbackOpen }">
    <header class="mi-stage__bar">
      <RouterLink to="/mock-interviews" class="mi-stage__back"><ArrowLeft :size="17" /><span>模拟面试</span></RouterLink>
      <div class="mi-stage__title">
        <strong>{{ summary?.positionName || '模拟面试' }} · {{ typeLabel }}</strong>
        <span class="mi-chip">{{ mode === 'VOICE' ? '语音模拟' : '文字模拟' }}</span>
        <span class="mi-chip" :class="status === 'PAUSED' ? 'mi-chip--warning' : 'mi-chip--live'">{{ statusLabel }}</span>
      </div>
      <div class="mi-stage__tools">
        <span class="mi-clock" :class="{ 'is-over': overTime }" title="服务端计时，暂停期间不计入">
          <Clock3 :size="16" />{{ formatSeconds(elapsed) }}<small>/ {{ limitMinutes }} 分钟</small>
        </span>
        <span class="mi-stage__count">第 {{ questionNumber }} / {{ summary?.questionCount ?? 0 }} 题</span>
        <button class="mi-tool" type="button" :disabled="!data || !current" @click="pauseSession"><Pause :size="16" /><span>暂停</span></button>
        <button class="mi-tool mi-tool--danger" type="button" :disabled="!data || !current" @click="endOpen = true"><Square :size="13" /><span>结束</span></button>
      </div>
    </header>

    <div v-if="offline" class="mi-stage__offline" role="status"><WifiOff :size="17" />网络已断开，回答会先保存在本机，恢复后自动同步</div>

    <section v-if="loading" class="mi-stage__state" aria-busy="true"><LoaderCircle class="mi-spin" :size="26" />正在进入模拟面试</section>

    <section v-else-if="data && current" class="mi-stage__grid">
      <aside class="mi-panel mi-panel--materials" aria-label="本次资料">
        <h2>本次资料</h2>
        <ul class="mi-materials">
          <li><span class="mi-materials__icon"><FileText :size="16" /></span><p><strong>简历</strong><small>{{ resumeTitle }}</small></p></li>
          <li v-if="careerRecordCount"><span class="mi-materials__icon"><Shield :size="16" /></span><p><strong>求职资料</strong><small>{{ careerRecordCount }} 条，已授权用于本次面试</small></p></li>
          <li v-if="hasJobDescription"><span class="mi-materials__icon"><Target :size="16" /></span><p><strong>岗位描述</strong><small>来自岗位匹配报告</small></p></li>
        </ul>
        <p class="mi-panel__note">题目与评估只基于这些资料和你的回答。</p>
      </aside>

      <section class="mi-stage__main">
        <div class="mi-mobile-progress" aria-hidden="true"><i :style="{ width: `${progressPercent}%` }" /></div>
        <p v-if="!usesAiQuestions" class="mi-notice"><AlertTriangle :size="14" />{{ String(data.settingsSnapshot.aiNotice || 'AI 通道暂不可用，本次题目来自通用题库。') }}</p>

        <article v-if="mode === 'TEXT'" class="mi-question">
          <header><span class="mi-question__no">Q{{ current.orderNo }}</span><span class="mi-question__source">来源：{{ current.sourceLabel || '岗位能力模型' }}</span></header>
          <h1>{{ current.prompt }}</h1>
        </article>
        <article v-else class="mi-question mi-question--voice" :class="{ 'is-speaking': questionSpeechState === 'speaking' }">
          <header><span class="mi-question__no">Q{{ current.orderNo }}</span><span class="mi-question__source">{{ interviewerLabel }}</span></header>
          <div class="mi-question__voice">
            <span class="mi-question__orb"><Volume2 :size="24" /></span>
            <div><h1>{{ questionSpeechLabel }}</h1><p>听完题目后点击麦克风作答</p></div>
            <button class="mi-btn mi-btn--ghost" type="button" :disabled="questionSpeechState === 'loading'" @click="speakCurrentQuestion">
              <LoaderCircle v-if="questionSpeechState === 'loading'" class="mi-spin" :size="15" /><RefreshCw v-else :size="15" />重听题目
            </button>
          </div>
          <details class="mi-question__text"><summary>显示题目文字</summary><p>{{ current.prompt }}</p></details>
        </article>

        <section v-if="mode === 'TEXT'" class="mi-answer">
          <label class="sr-only" for="mi-answer-text">你的回答</label>
          <textarea id="mi-answer-text" v-model="answer" maxlength="3000" placeholder="在这里输入你的回答。可以按情境、任务、行动、结果的顺序组织，并给出具体数字。" @keydown="onKeydown" />
          <div class="mi-answer-status"><span :class="saveState"><Lock :size="13" />{{ saveText }}</span><em>{{ answer.length }} / 3000</em></div>
          <footer>
            <p>提交后进入下一题，已提交的回答会完整保留在报告中。</p>
            <button class="mi-btn mi-btn--primary submit" type="button" :disabled="!canSubmit || offline" @click="submit">
              <LoaderCircle v-if="submitting" class="mi-spin" :size="16" /><Send v-else :size="16" />提交回答<kbd>Ctrl+Enter</kbd>
            </button>
          </footer>
        </section>

        <section v-else class="mi-voice">
          <div class="mi-voice__states">
            <span :class="`is-${microphoneState}`"><Mic :size="14" />{{ microphoneLabel }}</span>
            <span :class="`is-${transcriptionState}`"><FileText :size="14" />浏览器转写 · {{ transcriptionLabel }}</span>
          </div>
          <button
            class="mi-record-button"
            :class="{ recording: microphoneState === 'recording' }"
            type="button"
            :aria-label="microphoneState === 'recording' ? '结束录音' : '开始录音'"
            :disabled="microphoneState === 'requesting' || microphoneState === 'finishing'"
            @click="toggleRecording"
          >
            <MicOff v-if="microphoneState === 'recording'" :size="30" /><Mic v-else :size="30" />
          </button>
          <div class="mi-waveform" aria-hidden="true"><i v-for="(height, index) in waveform" :key="index" :style="{ height: `${height}px` }" /></div>
          <label class="sr-only" for="mi-transcript">语音回答转写文本</label>
          <textarea id="mi-transcript" v-model="liveTranscript" placeholder="开始录音后，浏览器识别出的文字会显示在这里；提交前可以手动校正。" @input="answer = liveTranscript" />
          <p class="mi-voice__privacy"><Shield :size="13" />语音由浏览器的语音识别转成文字，录音不会上传或保存。提交的是这里的文字。</p>
          <div class="mi-answer-status"><span :class="saveState"><Lock :size="13" />{{ saveText }}</span><em>{{ answer.length }} / 3000</em></div>
          <footer>
            <button class="mi-btn mi-btn--ghost" type="button" @click="switchToText"><MessageCircle :size="15" />改用文字作答</button>
            <button class="mi-btn mi-btn--primary submit" type="button" :disabled="!canSubmit || offline || microphoneState !== 'idle'" @click="submit">
              <LoaderCircle v-if="submitting" class="mi-spin" :size="16" /><Send v-else :size="16" />完成回答
            </button>
          </footer>
        </section>
      </section>

      <aside class="mi-panel mi-panel--progress" aria-label="答题进度">
        <h2>答题进度 <em>{{ summary?.answeredCount }}/{{ summary?.questionCount }}</em></h2>
        <div class="mi-progress" aria-hidden="true"><i :style="{ width: `${progressPercent}%` }" /></div>
        <ol class="mi-steps">
          <li v-for="question in data.questions" :key="question.id" :class="`is-${questionState(question)}`">
            <span class="mi-steps__no">Q{{ question.orderNo }}</span>
            <p>{{ questionState(question) === 'upcoming' ? '待作答' : mode === 'VOICE' ? `第 ${question.orderNo} 题` : question.prompt }}</p>
            <CheckCircle2 v-if="questionState(question) === 'done'" :size="15" />
            <Circle v-else :size="13" />
          </li>
        </ol>
        <p class="mi-panel__note"><MessageCircle :size="14" />动态追问{{ data.settingsSnapshot.followUpEnabled ? '已开启' : '未开启' }}</p>
      </aside>
    </section>

    <section v-else-if="data && (status === 'COMPLETED' || status === 'ABANDONED')" class="mi-stage__state">
      <CheckCircle2 :size="28" />
      <p>{{ status === 'COMPLETED' ? '本次面试已结束。' : '本次记录已放弃。' }}</p>
      <button v-if="status === 'COMPLETED'" class="mi-btn mi-btn--primary" type="button" @click="openReport">查看报告</button>
      <RouterLink v-else class="mi-btn mi-btn--ghost" to="/mock-interviews">返回模拟面试</RouterLink>
    </section>

    <section v-else class="mi-stage__state" role="alert">
      <AlertTriangle :size="28" />
      <p>{{ loadFailed ? '模拟面试读取失败，请检查网络后重试。' : '没有找到当前题目。' }}</p>
      <button class="mi-btn mi-btn--primary" type="button" @click="load"><RefreshCw :size="15" />重试</button>
    </section>

    <div v-if="evaluating" class="mi-busy" role="status">
      <LoaderCircle class="mi-spin" :size="22" />
      <p><strong>正在提交回答</strong><small>AI 可用时会同时评估本题，通常需要几秒。</small></p>
    </div>

    <aside v-if="feedbackOpen" class="mi-drawer" role="dialog" aria-modal="false" aria-labelledby="mi-drawer-title">
      <header>
        <div><h2 id="mi-drawer-title">本题反馈</h2><p>{{ feedbackScore === null ? '尚未评估' : 'AI 评估 · 仅基于本次回答与授权资料' }}</p></div>
        <button class="mi-icon-button" type="button" aria-label="关闭反馈" @click="continueAfterFeedback"><X :size="18" /></button>
      </header>
      <div v-if="feedbackScore !== null" class="mi-drawer__score">
        <strong>{{ feedbackScore }}</strong><span>/ 100</span>
        <p v-if="feedback.headline">{{ String(feedback.headline) }}</p>
      </div>
      <div v-else class="mi-drawer__pending">
        <Hourglass :size="20" />
        <p><strong>本题待评估</strong><small>{{ String(feedback.modelNotice || '提交时 AI 通道不可用；回答已保存，可在报告页重新评估。') }}</small></p>
      </div>
      <section v-if="list(feedback.strengths).length"><h3>做得好的</h3><ul><li v-for="item in list(feedback.strengths)" :key="item"><CheckCircle2 :size="14" />{{ item }}</li></ul></section>
      <section v-if="list(feedback.improvements).length"><h3>可以加强</h3><ul><li v-for="item in list(feedback.improvements)" :key="item"><AlertTriangle :size="14" />{{ item }}</li></ul></section>
      <section v-if="feedback.suggestedFollowUp"><h3>可能的追问</h3><p>{{ String(feedback.suggestedFollowUp) }}</p></section>
      <footer>
        <button v-if="status !== 'COMPLETED'" class="mi-btn mi-btn--ghost" type="button" @click="editFeedbackAnswer">修改并重答</button>
        <button class="mi-btn mi-btn--primary" type="button" @click="continueAfterFeedback">{{ status === 'COMPLETED' ? '查看报告' : '继续下一题' }}</button>
      </footer>
    </aside>

    <div v-if="pauseOpen || endOpen || abandonConfirmOpen || permissionOpen" class="mi-scrim">
      <section v-if="abandonConfirmOpen" class="mi-modal mi-modal--compact" role="dialog" aria-modal="true" aria-labelledby="mi-abandon-title">
        <h2 id="mi-abandon-title">确认放弃本次训练？</h2>
        <p>本次训练会标记为已放弃，不生成报告，也不计入完成统计。</p>
        <footer>
          <button class="mi-btn mi-btn--ghost" type="button" @click="abandonConfirmOpen = false; pauseOpen = true">返回</button>
          <button class="mi-btn mi-btn--danger" type="button" @click="abandon">确认放弃</button>
        </footer>
      </section>

      <section v-else-if="pauseOpen" class="mi-modal" role="dialog" aria-modal="true" aria-labelledby="mi-pause-title">
        <button class="mi-icon-button mi-modal__close" type="button" aria-label="继续面试" @click="continueSession"><X :size="18" /></button>
        <h2 id="mi-pause-title">已暂停</h2>
        <p>第 {{ questionNumber }} / {{ summary?.questionCount }} 题 · 已用时 {{ formatSeconds(elapsed) }}，暂停期间不计时。</p>
        <div class="mi-modal__options">
          <article>
            <h3>暂时离开</h3>
            <p>草稿和进度已保存，可随时从模拟面试首页继续。</p>
            <button class="mi-btn mi-btn--ghost" type="button" @click="router.push('/mock-interviews')">返回首页</button>
          </article>
          <article>
            <h3>提前结束</h3>
            <p>用已提交的回答生成阶段报告。</p>
            <button class="mi-btn mi-btn--ghost" type="button" @click="finishEarly">生成阶段报告</button>
          </article>
          <article class="is-danger">
            <h3>放弃本次记录</h3>
            <p>不生成报告，也不计入完成统计。</p>
            <button class="mi-btn mi-btn--ghost" type="button" @click="abandonConfirmOpen = true; pauseOpen = false">放弃记录</button>
          </article>
        </div>
        <footer><button class="mi-btn mi-btn--primary" type="button" @click="continueSession">继续面试</button></footer>
      </section>

      <section v-else-if="endOpen" class="mi-modal mi-modal--compact" role="dialog" aria-modal="true" aria-labelledby="mi-end-title">
        <h2 id="mi-end-title">提前结束本次面试？</h2>
        <p>已提交的回答会进入阶段报告，未提交的草稿不会被评估。</p>
        <footer>
          <button class="mi-btn mi-btn--ghost" type="button" @click="endOpen = false">继续面试</button>
          <button class="mi-btn mi-btn--danger" type="button" @click="finishEarly">结束并生成报告</button>
        </footer>
      </section>

      <section v-else class="mi-modal mi-modal--compact" role="dialog" aria-modal="true" aria-labelledby="mi-permission-title">
        <button class="mi-icon-button mi-modal__close" type="button" aria-label="关闭" @click="permissionOpen = false"><X :size="18" /></button>
        <h2 id="mi-permission-title"><AlertTriangle :size="20" />无法使用麦克风</h2>
        <p>浏览器没有授予麦克风权限。当前进度已保存。</p>
        <ol class="mi-modal__steps">
          <li>点击地址栏左侧的权限图标</li>
          <li>允许此网站使用麦克风</li>
          <li>回到这里重新检测</li>
        </ol>
        <footer>
          <button class="mi-btn mi-btn--ghost" type="button" @click="switchToText"><MessageCircle :size="15" />改用文字作答</button>
          <button class="mi-btn mi-btn--primary" type="button" @click="startRecording"><Mic :size="15" />重新检测</button>
        </footer>
      </section>
    </div>
  </main>
</template>

<style scoped>
.mi-stage {
  --stage-gutter: 24px;
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  color: var(--stage-text);
  background: var(--stage-glow), var(--stage-bg);
  font-family: var(--font-sans);
  color-scheme: dark;
}

/* The stage sets its own ink; global heading colours belong to the light/dark app surfaces. */
.mi-stage h1, .mi-stage h2, .mi-stage h3 { color: inherit; }

/* ---------- Top bar ---------- */
.mi-stage__bar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  min-height: 64px;
  padding: 10px var(--stage-gutter);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--stage-border);
  background: color-mix(in srgb, var(--stage-bg) 82%, transparent);
  backdrop-filter: blur(14px);
}
.mi-stage__back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--stage-text-secondary);
  font-size: var(--fs-sm);
  text-decoration: none;
}
.mi-stage__back:hover { color: var(--stage-text); }
.mi-stage__title { min-width: 0; display: flex; align-items: center; justify-content: center; gap: 10px; }
.mi-stage__title strong { overflow: hidden; font-size: var(--fs-body); font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.mi-chip {
  flex: none;
  padding: 2px 9px;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-full);
  color: var(--stage-text-secondary);
  font-size: var(--fs-xs);
}
.mi-chip--live { border-color: color-mix(in srgb, var(--stage-success) 40%, transparent); color: var(--stage-success); background: var(--stage-success-soft); }
.mi-chip--warning { border-color: color-mix(in srgb, var(--stage-warning) 40%, transparent); color: var(--stage-warning); background: var(--stage-warning-soft); }
.mi-stage__tools { display: flex; align-items: center; gap: 10px; }
.mi-clock {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: var(--fs-sm);
  font-variant-numeric: tabular-nums;
}
.mi-clock small { color: var(--stage-text-tertiary); font-family: var(--font-sans); font-size: var(--fs-xs); }
.mi-clock.is-over { color: var(--stage-warning); }
.mi-stage__count { padding-left: 10px; border-left: 1px solid var(--stage-border); color: var(--stage-text-secondary); font-size: var(--fs-sm); }
.mi-tool {
  min-height: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-sm);
  color: var(--stage-text);
  background: var(--stage-surface);
  font-size: var(--fs-sm);
}
.mi-tool:hover:not(:disabled) { border-color: var(--stage-border-strong); background: var(--stage-surface-raised); }
.mi-tool--danger { color: var(--stage-danger); }
.mi-tool:disabled { opacity: 0.5; }

.mi-stage__offline {
  margin: 12px var(--stage-gutter) 0;
  padding: 9px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid color-mix(in srgb, var(--stage-warning) 40%, transparent);
  border-radius: var(--radius-sm);
  color: var(--stage-warning);
  background: var(--stage-warning-soft);
  font-size: var(--fs-sm);
}
.mi-stage__state {
  flex: 1;
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--stage-text-secondary);
}

/* ---------- Layout ---------- */
.mi-stage__grid {
  flex: 1;
  width: min(1440px, 100%);
  margin: 0 auto;
  padding: 24px var(--stage-gutter) 40px;
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr) 260px;
  align-items: start;
  gap: 24px;
}
.mi-stage__main { min-width: 0; display: flex; flex-direction: column; gap: 16px; }
.mi-panel {
  position: sticky;
  top: 88px;
  padding: 18px;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-lg);
  background: var(--stage-surface);
}
.mi-panel h2 {
  margin: 0 0 14px;
  display: flex;
  justify-content: space-between;
  color: var(--stage-text-secondary);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
}
.mi-panel h2 em { color: var(--stage-text); font-style: normal; font-variant-numeric: tabular-nums; }
.mi-panel__note {
  margin: 16px 0 0;
  padding-top: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  border-top: 1px solid var(--stage-border);
  color: var(--stage-text-tertiary);
  font-size: var(--fs-xs);
  line-height: var(--lh-xs);
}
.mi-materials { margin: 0; padding: 0; display: grid; gap: 12px; list-style: none; }
.mi-materials li { display: flex; align-items: flex-start; gap: 10px; }
.mi-materials__icon {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  color: var(--stage-accent-text);
  background: var(--stage-accent-soft);
}
.mi-materials p { min-width: 0; margin: 0; display: grid; gap: 2px; }
.mi-materials strong { font-size: var(--fs-sm); font-weight: 600; }
.mi-materials small { overflow: hidden; color: var(--stage-text-tertiary); font-size: var(--fs-xs); text-overflow: ellipsis; white-space: nowrap; }

.mi-progress, .mi-mobile-progress { height: 4px; overflow: hidden; border-radius: var(--radius-full); background: var(--stage-surface-raised); }
.mi-progress i, .mi-mobile-progress i { display: block; height: 100%; border-radius: inherit; background: var(--stage-accent); transition: width var(--dur-slow) var(--ease-out); }
.mi-mobile-progress { display: none; }
.mi-steps { margin: 14px 0 0; padding: 0; display: grid; gap: 4px; list-style: none; }
.mi-steps li {
  padding: 8px 10px;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border-radius: var(--radius-sm);
  color: var(--stage-text-tertiary);
  font-size: var(--fs-xs);
}
.mi-steps p { margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mi-steps__no { font-family: var(--font-mono); font-weight: 600; }
.mi-steps li.is-current { color: var(--stage-text); background: var(--stage-accent-soft); }
.mi-steps li.is-current .mi-steps__no { color: var(--stage-accent-text); }
.mi-steps li.is-done { color: var(--stage-text-secondary); }
.mi-steps li.is-done svg { color: var(--stage-success); }

/* ---------- Question ---------- */
.mi-notice {
  margin: 0;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 7px;
  border: 1px solid color-mix(in srgb, var(--stage-warning) 34%, transparent);
  border-radius: var(--radius-sm);
  color: var(--stage-warning);
  background: var(--stage-warning-soft);
  font-size: var(--fs-xs);
}
.mi-question {
  padding: 26px 28px;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-lg);
  background: var(--stage-surface);
  box-shadow: var(--stage-shadow);
}
.mi-question header { margin-bottom: 14px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.mi-question__no {
  padding: 3px 10px;
  border-radius: var(--radius-full);
  color: var(--stage-accent-text);
  background: var(--stage-accent-soft);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.mi-question__source { color: var(--stage-text-tertiary); font-size: var(--fs-xs); }
.mi-question h1 { margin: 0; font-size: 22px; font-weight: 600; line-height: 1.55; letter-spacing: 0; }
.mi-question--voice.is-speaking { border-color: color-mix(in srgb, var(--stage-accent) 55%, transparent); }
.mi-question__voice { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 16px; }
.mi-question__voice h1 { font-size: 19px; }
.mi-question__voice p { margin: 4px 0 0; color: var(--stage-text-tertiary); font-size: var(--fs-sm); }
.mi-question__orb {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--stage-accent-text);
  background: var(--stage-accent-soft);
}
.is-speaking .mi-question__orb { color: var(--stage-on-accent); background: var(--stage-accent-strong); animation: mi-pulse 1.6s var(--ease-standard) infinite; }
.mi-question__text { margin-top: 16px; color: var(--stage-text-secondary); font-size: var(--fs-sm); }
.mi-question__text summary { width: max-content; color: var(--stage-text-tertiary); cursor: pointer; }
.mi-question__text p { margin: 8px 0 0; line-height: 1.7; }

/* ---------- Answer ---------- */
.mi-answer, .mi-voice {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-lg);
  background: var(--stage-surface);
}
.mi-answer textarea, .mi-voice textarea {
  width: 100%;
  min-height: 260px;
  padding: 14px 16px;
  resize: vertical;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-md);
  color: var(--stage-text);
  background: var(--stage-input);
  font: inherit;
  font-size: var(--fs-body-lg);
  line-height: 1.75;
}
.mi-voice textarea { min-height: 150px; font-size: var(--fs-body); }
.mi-answer textarea::placeholder, .mi-voice textarea::placeholder { color: var(--stage-text-tertiary); }
.mi-answer textarea:focus, .mi-voice textarea:focus { outline: 0; border-color: var(--stage-accent); box-shadow: 0 0 0 3px var(--stage-accent-soft); }
.mi-answer-status { display: flex; justify-content: space-between; color: var(--stage-text-tertiary); font-size: var(--fs-xs); }
.mi-answer-status span { display: inline-flex; align-items: center; gap: 5px; }
.mi-answer-status span.saved { color: var(--stage-success); }
.mi-answer-status span.local { color: var(--stage-warning); }
.mi-answer-status em { font-style: normal; font-variant-numeric: tabular-nums; }
.mi-answer footer, .mi-voice footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.mi-answer footer p { margin: 0; color: var(--stage-text-tertiary); font-size: var(--fs-xs); }
kbd {
  margin-left: 4px;
  padding: 1px 5px;
  border: 1px solid color-mix(in srgb, var(--stage-on-accent) 30%, transparent);
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 10px;
  opacity: 0.85;
}

.mi-voice { align-items: stretch; }
.mi-voice__states { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.mi-voice__states span {
  padding: 4px 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-full);
  color: var(--stage-text-secondary);
  font-size: var(--fs-xs);
}
.mi-voice__states .is-recording, .mi-voice__states .is-listening { border-color: color-mix(in srgb, var(--stage-success) 40%, transparent); color: var(--stage-success); }
.mi-voice__states .is-failed, .mi-voice__states .is-error, .mi-voice__states .is-unsupported { border-color: color-mix(in srgb, var(--stage-warning) 40%, transparent); color: var(--stage-warning); }
.mi-record-button {
  width: 76px;
  height: 76px;
  margin: 6px auto 0;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: var(--stage-on-accent);
  background: var(--stage-accent-strong);
  box-shadow: 0 0 0 8px var(--stage-accent-soft);
  transition: transform var(--dur-fast) var(--ease-standard);
}
.mi-record-button:hover:not(:disabled) { transform: scale(1.04); }
.mi-record-button.recording { background: var(--stage-danger); box-shadow: 0 0 0 8px var(--stage-danger-soft); }
.mi-record-button:disabled { opacity: 0.6; }
.mi-waveform { height: 52px; display: flex; align-items: center; justify-content: center; gap: 3px; }
.mi-waveform i { width: 3px; border-radius: 2px; background: var(--stage-accent); opacity: 0.75; transition: height 90ms linear; }
.mi-voice__privacy { margin: 0; display: flex; align-items: center; gap: 6px; color: var(--stage-text-tertiary); font-size: var(--fs-xs); }

/* ---------- Buttons ---------- */
.mi-btn {
  min-height: 38px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition: background var(--dur-fast) var(--ease-standard), border-color var(--dur-fast) var(--ease-standard);
}
.mi-btn--primary { color: var(--stage-on-accent); background: var(--stage-accent-strong); }
.mi-btn--primary:hover:not(:disabled) { background: var(--stage-accent); }
.mi-btn--ghost { border-color: var(--stage-border); color: var(--stage-text); background: var(--stage-surface); }
.mi-btn--ghost:hover:not(:disabled) { border-color: var(--stage-border-strong); background: var(--stage-surface-raised); }
.mi-btn--danger { color: var(--stage-on-accent); background: var(--stage-danger); }
.mi-btn:disabled { cursor: not-allowed; opacity: 0.5; }
.mi-icon-button {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--radius-sm);
  color: var(--stage-text-secondary);
  background: transparent;
}
.mi-icon-button:hover { color: var(--stage-text); background: var(--stage-surface-raised); }
.mi-stage :focus-visible { outline: 2px solid var(--stage-accent); outline-offset: 2px; }

/* ---------- Overlays ---------- */
.mi-busy {
  position: fixed;
  left: 50%;
  bottom: 28px;
  z-index: var(--z-drawer);
  padding: 12px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--stage-border-strong);
  border-radius: var(--radius-lg);
  background: var(--stage-surface-solid);
  box-shadow: var(--stage-shadow);
  transform: translateX(-50%);
}
.mi-busy p { margin: 0; display: grid; gap: 2px; }
.mi-busy strong { font-size: var(--fs-sm); }
.mi-busy small { color: var(--stage-text-tertiary); font-size: var(--fs-xs); }

.mi-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: var(--z-drawer);
  width: min(400px, 100%);
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow-y: auto;
  border-left: 1px solid var(--stage-border-strong);
  background: var(--stage-surface-solid);
  box-shadow: var(--stage-shadow);
}
.mi-drawer header { display: flex; justify-content: space-between; gap: 12px; }
.mi-drawer h2 { margin: 0; font-size: var(--fs-h3); }
.mi-drawer header p { margin: 4px 0 0; color: var(--stage-text-tertiary); font-size: var(--fs-xs); }
.mi-drawer__score { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px; }
.mi-drawer__score strong { color: var(--stage-accent-text); font-size: 44px; font-variant-numeric: tabular-nums; line-height: 1; }
.mi-drawer__score span { color: var(--stage-text-tertiary); }
.mi-drawer__score p { width: 100%; margin: 6px 0 0; color: var(--stage-text-secondary); font-size: var(--fs-sm); }
.mi-drawer__pending {
  padding: 14px;
  display: flex;
  gap: 12px;
  border: 1px solid color-mix(in srgb, var(--stage-warning) 34%, transparent);
  border-radius: var(--radius-md);
  color: var(--stage-warning);
  background: var(--stage-warning-soft);
}
.mi-drawer__pending p { margin: 0; display: grid; gap: 4px; }
.mi-drawer__pending small { color: var(--stage-text-secondary); font-size: var(--fs-xs); line-height: var(--lh-xs); }
.mi-drawer h3 { margin: 0 0 8px; color: var(--stage-text-secondary); font-size: var(--fs-xs); font-weight: 600; }
.mi-drawer ul { margin: 0; padding: 0; display: grid; gap: 8px; list-style: none; }
.mi-drawer li { display: flex; gap: 8px; font-size: var(--fs-sm); line-height: var(--lh-sm); }
.mi-drawer li svg { flex: none; margin-top: 3px; }
.mi-drawer section:nth-of-type(1) li svg { color: var(--stage-success); }
.mi-drawer section:nth-of-type(2) li svg { color: var(--stage-warning); }
.mi-drawer section p { margin: 0; font-size: var(--fs-sm); line-height: var(--lh-sm); }
.mi-drawer footer { margin-top: auto; display: flex; justify-content: flex-end; gap: 10px; }

.mi-scrim {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  padding: 16px;
  display: grid;
  place-items: center;
  background: var(--stage-scrim);
  backdrop-filter: blur(4px);
}
.mi-modal {
  position: relative;
  width: min(720px, 100%);
  padding: 26px;
  border: 1px solid var(--stage-border-strong);
  border-radius: var(--radius-xl);
  background: var(--stage-surface-solid);
  box-shadow: var(--stage-shadow);
}
.mi-modal--compact { width: min(460px, 100%); }
.mi-modal h2 { margin: 0; display: flex; align-items: center; gap: 8px; font-size: var(--fs-h2); }
.mi-modal > p { margin: 8px 0 0; color: var(--stage-text-secondary); font-size: var(--fs-sm); }
.mi-modal__close { position: absolute; top: 14px; right: 14px; }
.mi-modal__options { margin-top: 20px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.mi-modal__options article {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--stage-border);
  border-radius: var(--radius-md);
  background: var(--stage-surface);
}
.mi-modal__options h3 { margin: 0; font-size: var(--fs-sm); }
.mi-modal__options p { flex: 1; margin: 0; color: var(--stage-text-tertiary); font-size: var(--fs-xs); line-height: var(--lh-xs); }
.mi-modal__options .is-danger h3 { color: var(--stage-danger); }
.mi-modal__steps { margin: 16px 0 0; padding-left: 20px; display: grid; gap: 6px; color: var(--stage-text-secondary); font-size: var(--fs-sm); }
.mi-modal footer { margin-top: 22px; display: flex; justify-content: flex-end; gap: 10px; }

.mi-spin { animation: mi-spin 0.9s linear infinite; }
@keyframes mi-spin { to { transform: rotate(360deg); } }
@keyframes mi-pulse { 50% { box-shadow: 0 0 0 10px var(--stage-accent-soft); } }

/* ---------- Responsive ---------- */
@media (max-width: 1200px) {
  .mi-stage__grid { grid-template-columns: 220px minmax(0, 1fr); }
  .mi-panel--progress { display: none; }
  .mi-mobile-progress { display: block; }
}
@media (max-width: 860px) {
  .mi-stage { --stage-gutter: 14px; }
  .mi-stage__bar { grid-template-columns: auto minmax(0, 1fr) auto; gap: 10px; }
  .mi-stage__back span, .mi-stage__title .mi-chip, .mi-stage__count, .mi-tool span, .mi-clock small { display: none; }
  .mi-stage__title { justify-content: flex-start; }
  .mi-tool { width: 36px; padding: 0; justify-content: center; }
  .mi-stage__grid { padding-top: 14px; display: block; }
  .mi-panel--materials { display: none; }
  .mi-question { padding: 20px; }
  .mi-question h1 { font-size: 18px; }
  .mi-question__voice { grid-template-columns: auto minmax(0, 1fr); }
  .mi-question__voice .mi-btn { grid-column: 1 / -1; }
  .mi-answer textarea { min-height: 220px; }
  .mi-answer footer { flex-direction: column; align-items: stretch; }
  kbd { display: none; }
  .mi-modal__options { grid-template-columns: 1fr; }
  .mi-drawer { top: auto; max-height: 85vh; width: 100%; border-left: 0; border-top: 1px solid var(--stage-border-strong); border-radius: var(--radius-xl) var(--radius-xl) 0 0; }
}
@media (prefers-reduced-motion: reduce) {
  .mi-spin, .is-speaking .mi-question__orb { animation: none; }
  .mi-record-button, .mi-progress i, .mi-mobile-progress i { transition: none; }
}
</style>
