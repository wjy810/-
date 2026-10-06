<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, BarChart3, BookOpen, Check, CheckCircle2, ChevronDown, Clock3, FileText, LoaderCircle, Lock, MessageCircle, Mic, MicOff, Pause, Play, RefreshCw, Send, Shield, Square, Volume2, WifiOff, X } from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { createDraftSaveQueue } from '@/shared/lib/draftSaveQueue'
import { useToastFeedback } from '@/shared/ui/toast'
import {
  abandonMockInterview, completeMockInterview, fetchMockInterview, pauseMockInterview, resumeMockInterview,
  saveMockInterviewTextDraft, saveMockInterviewTranscript, submitMockInterviewAnswer,
  switchMockInterviewMode, uploadMockInterviewAudio,
} from '../services/mockInterviewApi'
import type { MockInterviewAnswer, MockInterviewSession } from '../types'
import {
  interviewerPitch,
  interviewerVoiceProfile,
  mergeSpeechTranscript,
  pickInterviewerVoice,
} from '../utils/voiceInteraction'

type RecorderState = 'idle' | 'requesting' | 'recording' | 'uploading' | 'failed'
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

const route = useRoute()
const router = useRouter()
const sessionId = computed(() => String(route.params.sessionId ?? ''))
const data = ref<MockInterviewSession | null>(null)
const loading = ref(true)
const pageError = ref('')
useToastFeedback(pageError, 'error', 'mock-interview-session-error')
const answer = ref('')
const answerVersion = ref<number | undefined>()
const saveState = ref<'idle' | 'saving' | 'saved' | 'local'>('idle')
const submitting = ref(false)
const analyzing = ref(false)
const feedbackOpen = ref(false)
const feedbackAnswer = ref<MockInterviewAnswer | null>(null)
const pauseOpen = ref(false)
const endOpen = ref(false)
const abandonConfirmOpen = ref(false)
const permissionOpen = ref(false)
const offline = ref(!navigator.onLine)
const elapsed = ref(0)
const recorderState = ref<RecorderState>('idle')
const transcriptionState = ref<TranscriptionState>('idle')
const questionSpeechState = ref<QuestionSpeechState>('idle')
const recordingSeconds = ref(0)
const waveform = ref<number[]>(Array.from({ length: 40 }, () => 8))
const liveTranscript = ref('')
const transcriptInput = ref<HTMLTextAreaElement | null>(null)
const materialsOpen = ref(true)
const progressOpen = ref(true)
let timer = 0
let recordingTimer = 0
let waveformTimer = 0
let mediaStream: MediaStream | null = null
let recorder: MediaRecorder | null = null
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
const pendingAudioUploads = new Set<Promise<void>>()
let chunkSequence = 0
let chunkStartedAt = 0
let disposed = false
let recordingGeneration = 0
let hydratingAnswer = false

const current = computed(() => data.value?.currentQuestion ?? null)
const summary = computed(() => data.value?.session ?? null)
const mode = computed(() => summary.value?.mode ?? 'TEXT')
const currentAnswer = computed(() => data.value?.answers.find((item) => item.questionId === current.value?.id) ?? null)
const feedback = computed(() => feedbackAnswer.value?.feedback ?? currentAnswer.value?.feedback ?? {})
const progressPercent = computed(() => summary.value ? Math.round((summary.value.answeredCount / summary.value.questionCount) * 100) : 0)
const timeText = computed(() => formatSeconds(elapsed.value))
const limitText = computed(() => `${Number(data.value?.settingsSnapshot.durationMinutes ?? 30)}:00`)
const canSubmit = computed(() => answer.value.trim().length >= 2 && !submitting.value && !analyzing.value)
const questionNumber = computed(() => current.value?.orderNo ?? Math.min((summary.value?.answeredCount ?? 0) + 1, summary.value?.questionCount ?? 1))
const interviewerProfile = computed(() => interviewerVoiceProfile(questionNumber.value))
const interviewerLabel = computed(() => interviewerProfile.value === 'FEMALE' ? '女声面试官' : '男声面试官')
const transcriptionLabel = computed(() => ({
  idle: '点击录音后启动',
  listening: '正在转写',
  unsupported: '当前浏览器不支持',
  error: '转写暂时不可用',
})[transcriptionState.value])
const questionSpeechLabel = computed(() => ({
  idle: '准备播放题目',
  loading: '正在准备声线',
  speaking: '正在提问，请仔细听',
  done: '题目播放完毕',
  error: '题目语音播放失败',
})[questionSpeechState.value])
const localKey = computed(() => `jobproof:mock-interview:${sessionId.value}:${current.value?.id ?? 'none'}`)
const usesAiQuestions = computed(() => String(data.value?.settingsSnapshot.questionGenerationMode ?? 'BASIC_RULES') === 'AI_MODEL')
const usesAiFeedback = computed(() => String(feedback.value.generationMode ?? 'BASIC_RULES') === 'AI_MODEL')

async function load(): Promise<void> {
  const requestedSessionId = sessionId.value
  loading.value = true
  pageError.value = ''
  try {
    const loaded = await fetchMockInterview(requestedSessionId)
    if (disposed || sessionId.value !== requestedSessionId) return
    data.value = loaded
    elapsed.value = data.value.session.answeredCount * 120
    hydrateAnswer()
    if (data.value.session.status === 'PAUSED') pauseOpen.value = true
  } catch (reason) {
    if (!disposed) pageError.value = errorMessage(reason, '模拟面试读取失败')
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

async function submit(): Promise<void> {
  if (!current.value || !canSubmit.value || offline.value) return
  submitting.value = true
  pageError.value = ''
  await stopRecording(false)
  try {
    if (!(await persistDraft()) || disposed || !current.value) return
    analyzing.value = true
    const questionId = current.value.id
    const submittedSessionId = sessionId.value
    const submittedKey = localKey.value
    const submittedAnswer = answer.value
    const submittedRevision = answerSaver.revision
    const updated = await submitMockInterviewAnswer(submittedSessionId, questionId, submittedAnswer, answerVersion.value)
    if (disposed || sessionId.value !== submittedSessionId || localKey.value !== submittedKey) return
    if (answerSaver.revision === submittedRevision) answerSaver.discardRecovery(submittedAnswer)
    const feedbackMode = String(updated.settingsSnapshot.feedbackMode ?? 'AFTER_SESSION')
    if (feedbackMode === 'AFTER_EACH') {
      const submitted = updated.answers.find((item) => item.questionId === questionId)
      if (submitted) {
        // Keep the submitted question visible behind the feedback long enough to review it.
        feedbackAnswer.value = submitted
        feedbackOpen.value = true
      }
    }
    data.value = updated
    await new Promise((resolve) => window.setTimeout(resolve, 700))
    if (!disposed && updated.session.status === 'COMPLETED') {
      feedbackOpen.value = false
      await router.replace({ name: 'mock-interview-report', params: { sessionId: sessionId.value } })
    }
  } catch (reason) {
    pageError.value = errorMessage(reason, '回答提交失败')
  } finally {
    analyzing.value = false
    submitting.value = false
    if (!disposed && answerSaver.dirty) {
      saveState.value = offline.value ? 'local' : 'saving'
      answerSaver.schedule(850)
    }
  }
}

async function onKeydown(event: KeyboardEvent): Promise<void> {
  if (event.ctrlKey && event.key === 'Enter') { event.preventDefault(); await submit() }
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
    pageError.value = '当前浏览器无法播放面试题语音，请切换到支持语音合成的 Chromium 浏览器。'
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
  if (disposed || !recognitionShouldRestart || recorderState.value !== 'recording' || !speechRecognition) return
  recognitionBaseTranscript = liveTranscript.value.trim()
  try {
    speechRecognition.start()
  } catch {
    transcriptionState.value = 'error'
    recognitionShouldRestart = false
    pageError.value = '实时转写意外中断，录音仍在保存；可以手动补充或校正回答。'
  }
}

function startLiveTranscription(): void {
  const Recognition = speechRecognitionConstructor()
  if (!Recognition) {
    transcriptionState.value = 'unsupported'
    pageError.value = '当前浏览器不支持实时语音转写，录音仍会保存；请使用 Chromium 浏览器或手动输入回答。'
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
      pageError.value = '浏览器未允许语音转写，请检查麦克风和语音识别权限；录音仍可保存。'
    } else {
      pageError.value = '实时转写服务暂时不可用，录音仍在保存；可以手动补充或校正回答。'
    }
  }
  speechRecognition.onend = () => {
    recognitionEndResolver?.()
    recognitionEndResolver = null
    window.clearTimeout(recognitionStopTimer)
    if (recognitionShouldRestart && recorderState.value === 'recording') {
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
    pageError.value = '实时转写启动失败，录音仍会保存；可以手动输入回答。'
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

async function startRecording(): Promise<void> {
  if (disposed || !current.value || mode.value !== 'VOICE' || !['idle', 'failed'].includes(recorderState.value)) return
  const generation = ++recordingGeneration
  const recordingKey = localKey.value
  stopQuestionSpeech('done')
  recorderState.value = 'requesting'
  permissionOpen.value = false
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: false })
    if (disposed || generation !== recordingGeneration || recordingKey !== localKey.value || mode.value !== 'VOICE') {
      stream.getTracks().forEach((track) => track.stop())
      return
    }
    mediaStream = stream
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : 'audio/webm'
    recorder = new MediaRecorder(mediaStream, { mimeType })
    chunkStartedAt = Date.now()
    recorder.ondataavailable = (event) => {
      if (!disposed && generation === recordingGeneration && recordingKey === localKey.value && event.data.size > 0) queueAudioChunk(event.data)
    }
    recorder.start(4000)
    recorderState.value = 'recording'
    recordingSeconds.value = 0
    recordingTimer = window.setInterval(() => { recordingSeconds.value += 1 }, 1000)
    startWaveform(mediaStream)
    startLiveTranscription()
  } catch {
    if (disposed || generation !== recordingGeneration) return
    if (recorder) {
      recorder.ondataavailable = null
      if (recorder.state !== 'inactive') recorder.stop()
      recorder = null
    }
    mediaStream?.getTracks().forEach((track) => track.stop())
    mediaStream = null
    window.clearInterval(recordingTimer)
    window.clearInterval(waveformTimer)
    abortLiveTranscription()
    if (audioContext) void audioContext.close()
    audioContext = null
    recorderState.value = 'failed'
    permissionOpen.value = true
  }
}

function queueAudioChunk(blob: Blob): void {
  const upload = uploadChunk(blob)
  pendingAudioUploads.add(upload)
  void upload.finally(() => pendingAudioUploads.delete(upload))
}

async function uploadChunk(blob: Blob): Promise<void> {
  if (disposed || !current.value || offline.value) return
  const durationMs = Math.max(500, Date.now() - chunkStartedAt)
  chunkStartedAt = Date.now()
  try {
    await uploadMockInterviewAudio(sessionId.value, current.value.id, chunkSequence++, blob, durationMs)
  } catch (reason) {
    pageError.value = errorMessage(reason, '录音分片上传失败，当前转写文本仍保留')
  }
}

async function stopRecording(saveTranscript = true): Promise<void> {
  // A pending browser permission prompt cannot be cancelled, but its result can be invalidated.
  if (recorderState.value === 'requesting') recordingGeneration += 1
  const activeRecorder = recorder
  const transcriptionStopped = stopLiveTranscription()
  if (activeRecorder && activeRecorder.state !== 'inactive') {
    recorderState.value = 'uploading'
    const recorderStopped = new Promise<void>((resolve) => activeRecorder.addEventListener('stop', () => resolve(), { once: true }))
    activeRecorder.stop()
    await recorderStopped
  }
  await transcriptionStopped
  await Promise.allSettled([...pendingAudioUploads])
  mediaStream?.getTracks().forEach((track) => track.stop())
  mediaStream = null
  recorder = null
  if (audioContext) void audioContext.close()
  audioContext = null
  window.clearInterval(recordingTimer)
  window.clearInterval(waveformTimer)
  waveform.value = Array.from({ length: 40 }, () => 8)
  recorderState.value = 'idle'
  if (saveTranscript && liveTranscript.value.trim()) answer.value = liveTranscript.value.trim()
}

function startWaveform(stream: MediaStream): void {
  if (audioContext) void audioContext.close()
  audioContext = new AudioContext()
  const analyser = audioContext.createAnalyser()
  analyser.fftSize = 128
  audioContext.createMediaStreamSource(stream).connect(analyser)
  const values = new Uint8Array(analyser.frequencyBinCount)
  waveformTimer = window.setInterval(() => {
    analyser.getByteFrequencyData(values)
    waveform.value = Array.from({ length: 40 }, (_, index) => Math.max(7, Math.round((values[index % values.length] / 255) * 54)))
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
  try { data.value = await resumeMockInterview(sessionId.value); pauseOpen.value = false; scheduleQuestionSpeech() }
  catch (reason) { pageError.value = errorMessage(reason, '恢复失败') }
}

async function finishEarly(): Promise<void> {
  stopQuestionSpeech()
  await stopRecording()
  try { await completeMockInterview(sessionId.value); await router.replace({ name: 'mock-interview-report', params: { sessionId: sessionId.value } }) }
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
  hydrateAnswer()
  scheduleQuestionSpeech()
}

async function abandon(): Promise<void> {
  stopQuestionSpeech()
  await stopRecording(false)
  try { await abandonMockInterview(sessionId.value); await router.replace({ name: 'mock-interviews' }) }
  catch (reason) { pageError.value = errorMessage(reason, '放弃记录失败') }
}

function onOnline(): void { offline.value = false; void persistDraft() }
function onOffline(): void { offline.value = true; saveState.value = 'local' }
function list(value: unknown): string[] { return Array.isArray(value) ? value.map(String) : [] }
function formatSeconds(value: number): string { const minutes = Math.floor(value / 60); const seconds = value % 60; return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}` }
function modeText(): string { return mode.value === 'VOICE' ? '语音模拟' : '文字模拟' }

onMounted(() => {
  window.addEventListener('online', onOnline)
  window.addEventListener('offline', onOffline)
  timer = window.setInterval(() => { if (!pauseOpen.value && !offline.value) elapsed.value += 1 }, 1000)
  void load()
})
onBeforeUnmount(() => {
  disposed = true
  answerSaver.dispose()
  recordingGeneration += 1
  window.clearInterval(timer); window.clearInterval(recordingTimer); window.clearInterval(waveformTimer); window.clearTimeout(questionSpeechTimer)
  window.removeEventListener('online', onOnline); window.removeEventListener('offline', onOffline)
  stopQuestionSpeech(); abortLiveTranscription()
  if (recorder) {
    recorder.ondataavailable = null
    if (recorder.state !== 'inactive') recorder.stop()
    recorder = null
  }
  mediaStream?.getTracks().forEach((track) => track.stop())
  if (audioContext) void audioContext.close()
})
</script>

<template>
  <main class="mi-session" :class="{ 'has-drawer': feedbackOpen }">
    <header class="mi-session-top">
      <RouterLink to="/mock-interviews" class="mi-session-brand"><span><Shield :size="20" /></span><strong>JobProof AI</strong></RouterLink>
      <div class="mi-session-title"><strong>{{ summary?.positionName || '模拟面试' }} · {{ summary?.interviewType === 'COMPREHENSIVE' ? '综合面试' : '专项面试' }}</strong><span :class="mode === 'VOICE' ? 'voice' : 'text'">{{ modeText() }}</span><em>进行中</em></div>
      <div class="mi-session-tools"><span><Clock3 :size="17" />{{ timeText }} <small>/ {{ limitText }}</small></span><i /><span>第 {{ questionNumber }} / {{ summary?.questionCount ?? 0 }} 题</span><button type="button" @click="pauseSession"><Pause :size="17" />暂停</button><button class="danger" type="button" @click="endOpen = true"><Square :size="14" />结束面试</button></div>
    </header>
    <div v-if="offline" class="mi-offline"><WifiOff :size="18" />网络连接已中断，回答仍在本地保存</div>
    <section v-if="loading" class="mi-session-loading"><LoaderCircle class="mi-spin" :size="26" />正在进入模拟面试</section>
    <section v-else-if="data && current" class="mi-session-grid">
      <aside class="mi-material-panel" :class="{ collapsed: !materialsOpen }"><button class="panel-heading" type="button" @click="materialsOpen = !materialsOpen"><span>本次资料</span><ChevronDown :size="16" /></button><div v-if="materialsOpen"><article><span class="is-blue"><FileText :size="17" /></span><p><strong>主简历</strong><small>{{ String(data.resumeSnapshot.title || '已冻结简历') }}</small></p><ChevronRightIcon /></article><article v-for="id in (data.materialsSnapshot.careerRecordIds as string[] || [])" :key="id"><span class="is-green"><BarChart3 :size="17" /></span><p><strong>求职资料</strong><small>已授权记录 {{ id.slice(0, 8) }}</small></p><ChevronRightIcon /></article></div></aside>

      <section class="mi-question-area">
        <div v-if="mode === 'TEXT'" class="mi-ai-pulse"><span /><span /><span /><em>{{ usesAiQuestions ? 'AI 面试官正在提问' : '基础题库面试官正在提问' }}</em></div>
        <div v-else class="mi-voice-orbit" :class="{ speaking: questionSpeechState === 'speaking' }"><div><Volume2 v-if="questionSpeechState === 'speaking'" :size="35" /><Mic v-else :size="35" /></div><span /><span /><span /></div>
        <p v-if="!usesAiQuestions" class="mi-engine-notice"><AlertTriangle :size="14" />{{ String(data.settingsSnapshot.aiNotice || 'AI 通道暂不可用，本次使用基础题库。') }}</p>
        <article v-if="mode === 'TEXT'" class="mi-question-card"><span>Q{{ current.orderNo }}</span><h1>{{ current.prompt }}</h1><footer>题目来源：<em>{{ current.sourceLabel || '岗位能力模型' }}</em></footer></article>
        <article v-else class="mi-spoken-question-card" :class="{ speaking: questionSpeechState === 'speaking' }"><span class="question-number">Q{{ current.orderNo }}</span><div class="interviewer-voice" :class="interviewerProfile.toLocaleLowerCase()"><Volume2 :size="25" /></div><div><strong>{{ interviewerLabel }}</strong><h1>{{ questionSpeechLabel }}</h1><p>题目正文已隐藏，请听题后点击麦克风回答</p></div><button type="button" :disabled="questionSpeechState === 'loading'" @click="speakCurrentQuestion"><LoaderCircle v-if="questionSpeechState === 'loading'" class="mi-spin" :size="16" /><RefreshCw v-else :size="16" />重听题目</button><span class="sr-only">{{ current.prompt }}</span></article>

        <section v-if="mode === 'TEXT'" class="mi-answer-box">
          <div class="mi-editor-toolbar"><button title="加粗" type="button"><strong>B</strong></button><button title="斜体" type="button"><i>I</i></button><button title="项目符号" type="button">•</button><span /></div>
          <textarea v-model="answer" maxlength="3000" placeholder="在这里输入你的回答。建议用情境、任务、行动、结果组织内容…" @keydown="onKeydown" />
          <div class="mi-answer-status"><span :class="saveState"><Lock :size="13" />{{ saveState === 'saving' ? '正在保存' : saveState === 'local' ? '本地草稿已保存 · 等待同步' : saveState === 'saved' ? '已自动保存' : '开始输入后自动保存' }}</span><em>{{ answer.length }} / 3000</em></div>
          <footer><button type="button"><RefreshCw :size="16" />查看回答提示</button><button type="button"><BookOpen :size="16" />标记稍后复盘</button><button class="submit" type="button" :disabled="!canSubmit || offline" @click="submit"><LoaderCircle v-if="submitting" class="mi-spin" :size="17" /><Send v-else :size="17" />提交回答 <small>Ctrl+Enter</small></button></footer>
        </section>

        <section v-else class="mi-voice-console">
          <div class="mi-voice-controls"><button :class="{ active: recorderState === 'recording' }" type="button" @click="recorderState === 'recording' ? stopRecording() : startRecording()"><MicOff v-if="recorderState === 'recording'" :size="20" /><Mic v-else :size="20" /><span>麦克风<small>{{ recorderState === 'recording' ? '录音中' : recorderState === 'requesting' ? '请求权限' : '已就绪' }}</small></span></button><button type="button" @click="speakCurrentQuestion"><Volume2 :size="20" /><span>面试官语音<small>{{ interviewerLabel }}</small></span></button><button type="button" @click="transcriptInput?.focus()"><FileText :size="20" /><span>实时转写<small :class="`is-${transcriptionState}`">{{ transcriptionLabel }}</small></span></button></div>
          <button class="mi-record-button" :class="{ recording: recorderState === 'recording' }" type="button" @click="recorderState === 'recording' ? stopRecording() : startRecording()"><MicOff v-if="recorderState === 'recording'" :size="33" /><Mic v-else :size="33" /></button>
          <div class="mi-waveform" aria-hidden="true"><i v-for="(height,index) in waveform" :key="index" :style="{ height: `${height}px` }" /></div>
          <p>{{ recorderState === 'recording' ? `正在录音并转写 ${formatSeconds(recordingSeconds)} · 点击结束` : recorderState === 'requesting' ? '正在请求麦克风和语音识别权限' : recorderState === 'uploading' ? '正在完成转写并保存录音' : '点击麦克风开始回答' }}</p>
          <textarea ref="transcriptInput" v-model="liveTranscript" aria-label="语音回答转写文本" placeholder="点击录音后，识别出的文字会实时显示在这里；你也可以手动校正。" @input="answer = liveTranscript" />
          <button class="mi-voice-submit" type="button" :disabled="!canSubmit || recorderState !== 'idle'" @click="submit">完成回答</button>
        </section>
        <p class="mi-prepare-time"><Clock3 :size="16" />剩余准备时间 <strong>00:18</strong></p>
      </section>

      <aside class="mi-progress-panel" :class="{ collapsed: !progressOpen }"><button class="panel-heading" type="button" @click="progressOpen = !progressOpen"><span>答题进度</span><ChevronDown :size="16" /></button><div v-if="progressOpen"><div class="mi-progress-summary"><span><i :style="{ width: `${progressPercent}%` }" /></span><em>{{ summary?.answeredCount }}/{{ summary?.questionCount }}</em></div><button v-for="question in data.questions" :key="question.id" type="button" :class="{ current: question.id === current.id, complete: question.status === 'COMPLETED' }"><span>Q{{ question.orderNo }}</span><strong>{{ mode === 'VOICE' ? `第 ${question.orderNo} 题` : question.prompt.length > 15 ? `${question.prompt.slice(0,15)}…` : question.prompt }}</strong><CheckCircle2 v-if="question.status === 'COMPLETED'" :size="15" /><Play v-else-if="question.id === current.id" :size="14" /><Lock v-else :size="13" /></button><hr /><p><MessageCircle :size="17" />动态追问 <em>{{ data.settingsSnapshot.followUpEnabled ? '已开启' : '未开启' }}</em></p></div></aside>
    </section>

    <section v-if="analyzing" class="mi-analysis-overlay"><article><header><LoaderCircle class="mi-spin" :size="24" /><div><h2>正在分析回答</h2><p>模型通道可用时使用 AI 评估，否则明确降级为基础规则</p></div></header><ol><li class="done"><Check :size="14" />内容理解</li><li class="done"><Check :size="14" />资料证据匹配</li><li class="active"><LoaderCircle class="mi-spin" :size="14" />回答结构评分</li><li>生成动态追问</li></ol></article></section>

    <aside v-if="feedbackOpen" class="mi-feedback-drawer"><header><div><h2>本题即时反馈</h2><p>{{ usesAiFeedback ? 'AI 教练模式' : '基础规则模式' }} · 仅在回答结束后展示</p></div><button type="button" @click="continueAfterFeedback"><X :size="20" /></button></header><div class="mi-feedback-score"><strong>{{ feedbackAnswer?.scores.overall ?? 0 }}</strong><span>分</span><p><em>{{ String(feedback.headline || '方向正确，证据仍可加强') }}</em><small>反馈仅基于本次回答与授权资料</small></p></div><section><h3>做得好的</h3><p v-for="item in list(feedback.strengths)" :key="item"><CheckCircle2 :size="15" />{{ item }}</p></section><section><h3>可以加强</h3><p v-for="item in list(feedback.improvements)" :key="item"><AlertTriangle :size="15" />{{ item }}</p></section><section v-if="feedback.suggestedFollowUp"><h3>建议追问</h3><p>{{ String(feedback.suggestedFollowUp) }}</p></section><p v-if="!usesAiFeedback" class="mi-feedback-mode-notice">{{ String(feedback.modelNotice || '本题使用基础规则反馈，不代表模型评估。') }}</p><footer><button type="button" @click="editFeedbackAnswer">编辑并重答</button><button type="button" @click="continueAfterFeedback">继续下一题</button></footer></aside>

    <div v-if="pauseOpen || endOpen || abandonConfirmOpen || permissionOpen" class="mi-modal-backdrop">
      <section v-if="abandonConfirmOpen" class="mi-session-modal is-compact"><button class="close" type="button" @click="abandonConfirmOpen = false; pauseOpen = true"><X :size="20" /></button><h2>确认放弃本次训练？</h2><p>本次训练会标记为已放弃，不进入报告和完成统计；审计记录仍会保留。</p><div><button type="button" @click="abandonConfirmOpen = false; pauseOpen = true">返回</button><button class="danger-button" type="button" @click="abandon">确认放弃</button></div></section>
      <section v-else-if="pauseOpen" class="mi-session-modal"><button class="close" type="button" @click="pauseOpen = false"><X :size="20" /></button><h2>暂停或结束本次面试</h2><p>第 {{ questionNumber }} / {{ summary?.questionCount }} 题 · 已用时 {{ timeText }} · 已暂停</p><div class="mi-pause-options"><article><span>A</span><h3>暂时离开</h3><p>当前草稿和进度已保存，7 天内可继续。</p><button type="button" @click="router.push('/mock-interviews')">暂停并返回首页</button></article><article><span>B</span><h3>提前结束并生成阶段报告</h3><p>保留已完成回答并生成阶段性报告。</p><button type="button" @click="finishEarly">生成阶段报告</button></article><article class="danger"><span>C</span><h3>放弃本次记录</h3><p>标记为已放弃，不进入报告和完成统计。</p><button type="button" @click="abandonConfirmOpen = true; pauseOpen = false">放弃记录</button></article></div><button class="continue" type="button" @click="continueSession">继续面试</button></section>
      <section v-else-if="endOpen" class="mi-session-modal is-compact"><button class="close" type="button" @click="endOpen = false"><X :size="20" /></button><h2>提前结束本次面试？</h2><p>已完成的回答会进入阶段报告，当前草稿也会保留。</p><div><button type="button" @click="endOpen = false">继续面试</button><button class="danger-button" type="button" @click="finishEarly">结束并生成报告</button></div></section>
      <section v-else class="mi-session-modal is-permission"><button class="close" type="button" @click="permissionOpen = false"><X :size="20" /></button><header><span><AlertTriangle :size="28" /></span><div><h2>无法使用麦克风</h2><p>浏览器未授权麦克风，语音回答暂不可用，当前进度和资料已保存。</p></div></header><ol><li><strong>1</strong>点击地址栏权限图标</li><li><strong>2</strong>允许麦克风访问</li><li><strong>3</strong>返回后重新检测</li></ol><div><button class="mi-primary" type="button" @click="startRecording"><Mic :size="17" />重新检测麦克风</button><button class="mi-secondary" type="button" @click="switchToText"><MessageCircle :size="17" />切换为文字模拟</button></div><small>切换模式会保留当前题目、计时与已选资料</small></section>
    </div>
  </main>
</template>

<script lang="ts">
import { ChevronRight } from 'lucide-vue-next'
export default { components: { ChevronRightIcon: ChevronRight } }
</script>

<style scoped>
.mi-ai-pulse{width:220px}.mi-ai-pulse em{white-space:nowrap}
.mi-engine-notice{width:min(960px,100%);margin:0 0 10px;padding:8px 12px;display:flex;align-items:center;justify-content:center;gap:7px;border:1px solid color-mix(in srgb, var(--color-warning) 36%, transparent);border-radius:6px;color:var(--color-warning);background:color-mix(in srgb, var(--color-warning) 42%, transparent);font-size:12px}.mi-feedback-mode-notice{margin-top:14px;padding:10px 12px;border:1px solid var(--color-warning);border-radius:6px;color:var(--color-warning);background:var(--text-primary);font-size:12px}
.mi-session{min-height:100vh;color:var(--text-on-primary);background:radial-gradient(circle at 50% 45%,var(--color-primary) 0,var(--text-primary) 36%,var(--text-primary) 76%,var(--text-primary) 100%);font-family:var(--sans);overflow:hidden}.mi-session-top{position:relative;z-index:10;height:74px;padding:0 24px;display:grid;grid-template-columns:220px 1fr auto;align-items:center;border-bottom:1px solid color-mix(in srgb, var(--color-primary) 22%, transparent);background:color-mix(in srgb, var(--text-primary) 55%, transparent);backdrop-filter:blur(16px)}.mi-session-brand{display:flex;align-items:center;gap:10px;color:var(--text-on-primary)}.mi-session-brand span{width:38px;height:38px;display:grid;place-items:center;border-radius:8px;background:var(--color-primary)}.mi-session-title{display:flex;align-items:center;justify-content:center;gap:10px}.mi-session-title>strong{font-size:20px}.mi-session-title span,.mi-session-title em{padding:3px 9px;border-radius:5px;font-size:12px;font-style:normal}.mi-session-title span.text{color:var(--color-primary);background:var(--color-primary)}.mi-session-title span.voice{color:var(--color-success);background:var(--color-success)}.mi-session-title em{color:var(--color-success);background:var(--text-primary)}.mi-session-tools{display:flex;align-items:center;gap:16px}.mi-session-tools>span{display:flex;align-items:center;gap:6px;white-space:nowrap}.mi-session-tools small{color:var(--color-primary)}.mi-session-tools i{width:1px;height:28px;background:color-mix(in srgb, var(--color-primary) 25%, transparent)}.mi-session-tools button{height:42px;padding:0 17px;display:flex;align-items:center;gap:7px;border:1px solid color-mix(in srgb, var(--color-primary) 45%, transparent);border-radius:7px;color:var(--text-on-primary);background:transparent}.mi-session-tools button.danger{border-color:var(--color-danger);background:var(--color-danger)}.mi-offline{height:48px;display:flex;align-items:center;justify-content:center;gap:9px;color:var(--color-warning);background:var(--color-warning)}.mi-session-loading{min-height:calc(100vh - 74px);display:flex;align-items:center;justify-content:center;gap:10px}.mi-session-grid{height:calc(100vh - 74px);padding:18px 20px;display:grid;grid-template-columns:280px minmax(520px,1fr) 310px;gap:22px}.mi-material-panel,.mi-progress-panel{align-self:center;max-height:76vh;padding:14px;border:1px solid color-mix(in srgb, var(--color-primary) 30%, transparent);border-radius:9px;background:color-mix(in srgb, var(--text-primary) 65%, transparent);box-shadow:0 18px 45px rgba(0,8,28,.24);overflow:auto}.panel-heading{width:100%;padding:3px 5px 12px;display:flex;align-items:center;justify-content:space-between;border:0;border-bottom:1px solid color-mix(in srgb, var(--color-primary) 22%, transparent);color:var(--text-on-primary);background:transparent;font-weight:700}.panel-heading svg{transition:transform .18s}.collapsed .panel-heading{border:0}.collapsed .panel-heading svg{transform:rotate(-90deg)}.mi-material-panel article{min-height:72px;margin-top:9px;padding:11px;display:grid;grid-template-columns:38px 1fr 14px;align-items:center;gap:8px;border:1px solid color-mix(in srgb, var(--color-primary) 20%, transparent);border-radius:7px;background:color-mix(in srgb, var(--color-primary) 55%, transparent)}.mi-material-panel article>span{width:36px;height:36px;display:grid;place-items:center;border-radius:6px}.mi-material-panel p{display:grid;min-width:0}.mi-material-panel small{overflow:hidden;color:var(--color-primary);text-overflow:ellipsis;white-space:nowrap}.mi-question-area{min-width:0;display:flex;align-items:center;flex-direction:column;justify-content:center}.mi-ai-pulse{position:relative;height:105px;display:flex;align-items:center;justify-content:center}.mi-ai-pulse>span{position:absolute;width:105px;height:58px;border-radius:50% 50% 45% 45%;background:var(--color-primary);filter:drop-shadow(0 0 18px color-mix(in srgb, var(--color-primary) 50%, transparent))}.mi-ai-pulse>span:nth-child(2){margin-left:76px;margin-top:18px;width:76px;height:50px;background:var(--color-primary)}.mi-ai-pulse>span:nth-child(3){z-index:1;width:8px;height:8px;margin-left:54px;border-radius:50%;background:var(--surface-3);box-shadow:18px 0 var(--border-subtle),36px 0 var(--border-subtle)}.mi-ai-pulse em{position:absolute;bottom:0;z-index:2;padding:5px 14px;border:1px solid var(--color-primary);border-radius:7px;color:var(--text-on-primary);background:var(--text-primary);font-style:normal}.mi-voice-orbit{position:relative;width:190px;height:140px;display:grid;place-items:center}.mi-voice-orbit div{position:relative;z-index:2;width:72px;height:72px;display:grid;place-items:center;border:2px solid var(--color-primary);border-radius:50%;color:var(--text-on-primary);background:var(--color-primary);box-shadow:0 0 28px var(--color-primary)}.mi-voice-orbit span{position:absolute;width:105px;height:105px;border:1px solid var(--color-primary);border-radius:50%;animation:voice-pulse 2.4s infinite}.mi-voice-orbit span:nth-child(3){width:140px;height:140px;animation-delay:.4s}.mi-voice-orbit span:nth-child(4){width:175px;height:175px;animation-delay:.8s}.mi-question-card{position:relative;width:min(960px,100%);min-height:165px;padding:30px 34px 24px;border:1px solid color-mix(in srgb, var(--color-primary) 30%, transparent);border-radius:9px;background:color-mix(in srgb, var(--text-primary) 70%, transparent);text-align:center}.mi-question-card>span{position:absolute;top:23px;left:25px;padding:8px 12px;border-radius:7px;color:var(--color-primary);background:var(--color-primary);font-size:24px;font-weight:700}.mi-question-card h1{max-width:780px;margin:0 auto;padding:0 50px;color:var(--text-on-primary);font-size:27px;line-height:1.65;letter-spacing:0}.mi-question-card footer{position:absolute;right:0;bottom:0;left:0;padding:10px 25px;border-top:1px solid color-mix(in srgb, var(--color-primary) 18%, transparent);color:var(--color-primary);text-align:left}.mi-question-card footer em{padding:3px 8px;border:1px solid var(--color-primary);border-radius:4px;color:var(--color-primary);font-style:normal}.mi-answer-box{width:min(960px,100%);margin-top:16px;border:1px solid var(--color-primary);border-radius:9px;background:color-mix(in srgb, var(--text-primary) 84%, transparent);box-shadow:0 0 0 1px var(--color-primary) inset}.mi-editor-toolbar{height:44px;padding:0 18px;display:flex;align-items:center;gap:5px;border-bottom:1px solid color-mix(in srgb, var(--color-primary) 18%, transparent)}.mi-editor-toolbar button{width:36px;height:32px;border:0;color:var(--text-on-primary);background:transparent;font-size:18px}.mi-editor-toolbar span{width:1px;height:23px;margin-left:4px;background:var(--color-primary)}.mi-answer-box textarea{width:100%;height:270px;padding:16px 24px;border:0;outline:0;resize:none;color:var(--text-on-primary);background:transparent;font-size:17px;line-height:1.75}.mi-answer-box textarea::placeholder{color:var(--color-primary-text)}.mi-answer-status{height:35px;padding:0 22px;display:flex;align-items:center;justify-content:space-between;border-top:1px solid color-mix(in srgb, var(--color-primary) 16%, transparent);color:var(--color-primary)}.mi-answer-status span{display:flex;align-items:center;gap:5px}.mi-answer-status span.saved{color:var(--color-success-text)}.mi-answer-status span.local{color:var(--color-warning)}.mi-answer-status em{font-style:normal}.mi-answer-box>footer{padding:10px 18px;display:grid;grid-template-columns:1fr 1fr 1.25fr;gap:12px}.mi-answer-box>footer button{height:50px;display:flex;align-items:center;justify-content:center;gap:8px;border:1px solid var(--color-primary);border-radius:7px;color:var(--text-tertiary);background:var(--color-primary)}.mi-answer-box>footer button.submit{border-color:var(--color-primary);background:var(--color-primary)}.mi-answer-box>footer button:disabled{opacity:.5}.mi-answer-box>footer small{opacity:.8}.mi-prepare-time{margin-top:9px;display:flex;align-items:center;gap:7px;color:var(--color-primary)}.mi-voice-console{width:min(980px,100%);margin-top:18px;padding:20px 28px;display:grid;grid-template-columns:auto auto 1fr;align-items:center;gap:24px;border:1px solid color-mix(in srgb, var(--color-primary) 27%, transparent);border-radius:12px;background:color-mix(in srgb, var(--text-primary) 76%, transparent)}.mi-voice-controls{display:flex;gap:16px}.mi-voice-controls button{display:flex;align-items:center;gap:8px;border:0;color:var(--text-tertiary);background:transparent}.mi-voice-controls button>span{display:grid;text-align:left}.mi-voice-controls small{color:var(--color-success-text)}.mi-record-button{width:92px;height:92px;display:grid;place-items:center;border:5px solid var(--surface-1);border-radius:50%;color:var(--text-on-primary);background:var(--color-primary);box-shadow:0 0 0 7px var(--color-primary),0 0 30px var(--color-primary)}.mi-record-button.recording{background:var(--color-danger);box-shadow:0 0 0 7px var(--color-danger),0 0 30px var(--color-danger)}.mi-waveform{height:70px;display:flex;align-items:center;gap:3px}.mi-waveform i{width:3px;border-radius:3px;background:linear-gradient(var(--color-success),var(--color-primary));transition:height .08s}.mi-voice-console>p{grid-column:1/4;text-align:center;color:var(--text-tertiary)}.mi-voice-console textarea{grid-column:1/4;min-height:100px;padding:12px;border:1px solid var(--color-primary);border-radius:7px;resize:vertical;color:var(--text-on-primary);background:var(--text-primary)}.mi-voice-submit{grid-column:3;min-height:44px;border:0;border-radius:7px;color:var(--text-on-primary);background:var(--color-primary)}.mi-progress-summary{padding:12px 5px;display:flex;align-items:center;gap:9px}.mi-progress-summary>span{height:5px;flex:1;border-radius:3px;background:var(--text-primary);overflow:hidden}.mi-progress-summary i{display:block;height:100%;background:var(--color-success)}.mi-progress-summary em{font-style:normal}.mi-progress-panel>div>button{width:100%;min-height:58px;padding:8px;display:grid;grid-template-columns:34px 1fr 17px;align-items:center;gap:7px;border:0;border-bottom:1px solid color-mix(in srgb, var(--color-primary) 15%, transparent);color:var(--color-primary);background:transparent;text-align:left}.mi-progress-panel>div>button.current{margin:0 -3px;width:calc(100% + 6px);border:1px solid var(--color-primary);border-radius:7px;color:var(--text-on-primary);background:var(--color-primary)}.mi-progress-panel>div>button.complete{color:var(--text-tertiary)}.mi-progress-panel>div>button>span{color:var(--color-primary)}.mi-progress-panel>div>button.current>span{color:var(--color-primary)}.mi-progress-panel hr{height:1px;margin:13px 0;border:0;background:color-mix(in srgb, var(--color-primary) 20%, transparent)}.mi-progress-panel>div>p{display:flex;align-items:center;gap:8px}.mi-progress-panel>div>p em{margin-left:auto;color:var(--color-success-text);font-style:normal}.mi-analysis-overlay,.mi-modal-backdrop{position:fixed;inset:0;z-index:60;display:grid;place-items:center;background:color-mix(in srgb, var(--text-primary) 76%, transparent);backdrop-filter:blur(4px)}.mi-analysis-overlay article{width:min(820px,calc(100% - 30px));padding:28px;border:1px solid var(--color-primary);border-radius:12px;background:var(--text-primary);box-shadow:0 0 45px color-mix(in srgb, var(--color-primary) 33%, transparent)}.mi-analysis-overlay header{display:flex;align-items:center;gap:12px}.mi-analysis-overlay header p{color:var(--color-primary)}.mi-analysis-overlay ol{margin:28px 0 0;padding:0;display:grid;grid-template-columns:repeat(4,1fr);list-style:none}.mi-analysis-overlay li{padding:15px 10px;display:flex;align-items:center;justify-content:center;gap:6px;border-top:2px dotted var(--color-primary);color:var(--color-primary)}.mi-analysis-overlay li.done{border-color:var(--color-success);color:var(--color-success-text)}.mi-analysis-overlay li.active{border-color:var(--color-primary);color:var(--color-primary)}.mi-feedback-drawer{position:fixed;z-index:55;top:0;right:0;bottom:0;width:450px;padding:24px;border-left:1px solid var(--color-primary);color:var(--text-on-primary);background:var(--text-primary);box-shadow:-20px 0 50px color-mix(in srgb, var(--border-strong) 47%, transparent);overflow:auto}.mi-feedback-drawer>header{display:flex;justify-content:space-between}.mi-feedback-drawer>header p{color:var(--text-tertiary)}.mi-feedback-drawer>header button{border:0;color:var(--text-on-primary);background:transparent}.mi-feedback-score{margin:20px 0;padding:18px;display:grid;grid-template-columns:70px 30px 1fr;align-items:center;border:1px solid var(--color-primary);border-radius:8px;background:var(--text-primary)}.mi-feedback-score>strong{font-size:42px}.mi-feedback-score>span{align-self:end;margin-bottom:8px}.mi-feedback-score p{display:grid}.mi-feedback-score em{color:var(--color-success-text);font-style:normal;font-weight:700}.mi-feedback-score small{color:var(--text-tertiary)}.mi-feedback-drawer>section{margin-top:13px;padding:16px;border:1px solid var(--color-primary);border-radius:8px;background:var(--text-primary)}.mi-feedback-drawer h3{margin-bottom:8px}.mi-feedback-drawer>section p{margin-top:7px;display:flex;gap:7px;color:var(--text-tertiary)}.mi-feedback-drawer>section svg{flex:0 0 auto;color:var(--color-success-text)}.mi-feedback-drawer>footer{margin-top:20px;display:grid;grid-template-columns:1fr 1fr;gap:10px}.mi-feedback-drawer>footer button{height:46px;border:1px solid var(--color-primary);border-radius:7px;color:var(--text-on-primary);background:transparent}.mi-feedback-drawer>footer button:last-child{background:var(--color-primary)}.mi-session-modal{position:relative;width:min(840px,calc(100% - 28px));padding:30px;border:1px solid var(--color-primary);border-radius:12px;background:var(--text-primary);box-shadow:0 20px 80px color-mix(in srgb, var(--border-strong) 67%, transparent)}.mi-session-modal>.close{position:absolute;top:18px;right:18px;border:0;color:var(--text-tertiary);background:transparent}.mi-session-modal>h2{text-align:center;font-size:26px}.mi-session-modal>p{text-align:center;color:var(--text-tertiary)}.mi-pause-options{margin-top:24px;display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.mi-pause-options article{padding:20px;border:1px solid var(--color-primary);border-radius:8px;background:var(--text-primary)}.mi-pause-options article>span{width:36px;height:36px;display:grid;place-items:center;border-radius:6px;color:var(--color-primary);background:var(--color-primary);font-size:20px}.mi-pause-options h3{margin:12px 0 5px}.mi-pause-options p{min-height:58px;color:var(--text-tertiary)}.mi-pause-options button{width:100%;height:42px;margin-top:13px;border:1px solid var(--color-primary);border-radius:6px;color:var(--color-primary);background:transparent}.mi-pause-options article.danger>span{color:var(--color-danger);background:var(--color-danger)}.mi-pause-options article.danger button{color:var(--color-danger);border-color:var(--color-danger)}.mi-session-modal>.continue{width:100%;height:50px;margin-top:18px;border:0;border-radius:6px;color:var(--text-on-primary);background:var(--color-primary)}.mi-session-modal.is-compact{width:min(520px,calc(100% - 28px));text-align:center}.mi-session-modal.is-compact>div{margin-top:22px;display:grid;grid-template-columns:1fr 1fr;gap:10px}.mi-session-modal.is-compact>div button{height:44px;border:1px solid var(--color-primary);border-radius:6px;color:var(--text-on-primary);background:transparent}.mi-session-modal.is-compact>div .danger-button{border-color:var(--color-danger);background:var(--color-danger)}.mi-session-modal.is-permission{width:min(720px,calc(100% - 28px))}.mi-session-modal.is-permission>header{display:flex;gap:14px}.mi-session-modal.is-permission>header>span{width:54px;height:54px;display:grid;place-items:center;border-radius:50%;color:var(--text-on-primary);background:var(--color-danger)}.mi-session-modal.is-permission ol{margin:22px 0;padding:0;display:grid;gap:8px;list-style:none}.mi-session-modal.is-permission li{padding:11px;display:flex;align-items:center;gap:9px;border-radius:6px;background:var(--text-primary)}.mi-session-modal.is-permission li strong{width:25px;height:25px;display:grid;place-items:center;border-radius:50%;background:var(--color-primary)}.mi-session-modal.is-permission>div{display:grid;grid-template-columns:1fr 1fr;gap:10px}.mi-session-modal.is-permission>small{display:block;margin-top:12px;color:var(--text-tertiary);text-align:center}@keyframes voice-pulse{0%,100%{opacity:.25;transform:scale(.9)}50%{opacity:1;transform:scale(1.04)}}
.mi-voice-orbit.speaking div{color:var(--color-primary-text);background:var(--color-success);box-shadow:0 0 32px color-mix(in srgb, var(--color-success) 60%, transparent)}.mi-spoken-question-card{width:min(960px,100%);min-height:142px;padding:24px 26px;display:grid;grid-template-columns:auto auto minmax(0,1fr) auto;align-items:center;gap:18px;border:1px solid color-mix(in srgb, var(--color-primary) 36%, transparent);border-radius:8px;background:color-mix(in srgb, var(--text-primary) 78%, transparent);box-shadow:0 18px 45px rgba(0,8,28,.2)}.mi-spoken-question-card.speaking{border-color:var(--color-success);box-shadow:0 0 0 1px color-mix(in srgb, var(--color-success) 27%, transparent),0 18px 45px rgba(0,8,28,.28)}.mi-spoken-question-card .question-number{padding:8px 12px;border-radius:6px;color:var(--color-primary);background:var(--color-primary);font-size:21px;font-weight:700}.interviewer-voice{width:52px;height:52px;display:grid;place-items:center;border-radius:50%;color:var(--color-primary-text);background:var(--color-success)}.interviewer-voice.male{color:var(--text-on-primary);background:var(--color-primary)}.mi-spoken-question-card>div:nth-of-type(2){display:grid;gap:3px}.mi-spoken-question-card strong{color:var(--color-primary);font-size:13px}.mi-spoken-question-card h1{color:var(--text-on-primary);font-size:21px;letter-spacing:0}.mi-spoken-question-card p{color:var(--color-primary)}.mi-spoken-question-card>button{min-height:40px;padding:0 14px;display:flex;align-items:center;justify-content:center;gap:7px;border:1px solid var(--color-primary);border-radius:6px;color:var(--text-on-primary);background:var(--color-primary);white-space:nowrap}.mi-spoken-question-card>button:disabled{opacity:.55}.mi-voice-controls small.is-error,.mi-voice-controls small.is-unsupported{color:var(--color-danger)}.mi-voice-controls small.is-listening{color:var(--color-success-text)}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
@media(max-width:1200px){.mi-session-top{grid-template-columns:170px 1fr}.mi-session-tools>span:first-child,.mi-session-tools>i{display:none}.mi-session-grid{grid-template-columns:220px minmax(450px,1fr)}.mi-progress-panel{display:none}.mi-question-card h1{font-size:22px}.mi-answer-box textarea{height:230px}}
@media(max-width:850px){.mi-session{overflow:auto}.mi-session-top{position:sticky;top:0;height:auto;min-height:64px;padding:10px 14px;grid-template-columns:auto 1fr}.mi-session-brand strong{display:none}.mi-session-title{justify-content:flex-start}.mi-session-title>strong{font-size:14px}.mi-session-title span,.mi-session-title em{display:none}.mi-session-tools>span,.mi-session-tools>i{display:none}.mi-session-tools button{width:40px;padding:0;justify-content:center}.mi-session-tools button svg+*{display:none}.mi-session-grid{height:auto;min-height:calc(100vh - 64px);padding:12px;display:block}.mi-material-panel{display:none}.mi-question-area{justify-content:flex-start}.mi-ai-pulse,.mi-voice-orbit{height:90px;transform:scale(.82)}.mi-question-card{min-height:145px;padding:25px 15px 46px}.mi-question-card>span{top:12px;left:12px;font-size:16px}.mi-question-card h1{padding:0 28px;font-size:18px;line-height:1.6}.mi-answer-box textarea{height:260px;padding:14px;font-size:15px}.mi-answer-box>footer{grid-template-columns:1fr}.mi-answer-box>footer button:not(.submit){display:none}.mi-voice-console{padding:18px;grid-template-columns:1fr}.mi-voice-controls{display:none}.mi-record-button{grid-column:1;margin:auto}.mi-waveform,.mi-voice-console>p,.mi-voice-console textarea,.mi-voice-submit{grid-column:1}.mi-feedback-drawer{top:auto;width:100%;height:82vh;border-top:1px solid var(--color-primary)}.mi-analysis-overlay ol{grid-template-columns:1fr 1fr}.mi-pause-options{grid-template-columns:1fr;max-height:56vh;overflow:auto}.mi-pause-options p{min-height:0}.mi-session-modal.is-permission>div{grid-template-columns:1fr}}
@media(max-width:850px){.mi-session-top{grid-template-columns:38px minmax(0,1fr) 86px;gap:8px}.mi-session-title{min-width:0}.mi-session-title>strong{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mi-session-tools{gap:6px}.mi-session-tools button{font-size:0}.mi-question-card h1{padding-left:62px}}
@media(max-width:850px){.mi-spoken-question-card{padding:18px;grid-template-columns:auto minmax(0,1fr);gap:12px}.mi-spoken-question-card .question-number{font-size:16px}.interviewer-voice{width:44px;height:44px}.mi-spoken-question-card>div:nth-of-type(2){grid-column:1/3}.mi-spoken-question-card h1{font-size:18px}.mi-spoken-question-card>button{grid-column:1/3;width:100%}}
@media(prefers-reduced-motion:reduce){.mi-voice-orbit span{animation:none}}
</style>
