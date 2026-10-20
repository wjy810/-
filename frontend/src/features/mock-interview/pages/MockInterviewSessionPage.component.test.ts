import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MockInterviewAnswer, MockInterviewSession } from '../types'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  fetchMockInterview: vi.fn(),
  saveMockInterviewTextDraft: vi.fn(),
  saveMockInterviewTranscript: vi.fn(),
  submitMockInterviewAnswer: vi.fn(),
  pauseMockInterview: vi.fn(),
  heartbeatMockInterview: vi.fn(),
  resumeMockInterview: vi.fn(),
}))
vi.mock('../services/mockInterviewApi', async () => ({
  ...(await vi.importActual<typeof import('../services/mockInterviewApi')>('../services/mockInterviewApi')),
  ...api,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { sessionId: 'session-1' } }),
  useRouter: () => ({ replace: vi.fn() }),
}))
import MockInterviewSessionPage from './MockInterviewSessionPage.vue'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function savedAnswer(answer: string, version: number): MockInterviewAnswer {
  return { id: 'answer-1', questionId: 'question-1', mode: 'TEXT', answer, status: 'DRAFT', scores: {}, feedback: {}, version, updatedAt: '' }
}

function session(mode: 'TEXT' | 'VOICE' = 'TEXT'): MockInterviewSession {
  const question = { id: 'question-1', orderNo: 1, questionType: 'BASIC', prompt: '请介绍你的项目', sourceRefs: [], status: 'CURRENT' }
  return {
    session: { id: 'session-1', title: '面试', positionName: '工程师', mode, interviewType: 'COMPREHENSIVE', difficulty: 'NORMAL', status: 'IN_PROGRESS', answeredCount: 0, questionCount: 2, updatedAt: '' },
    currentQuestion: question, questions: [question], answers: [savedAnswer('旧回答', 1)],
    resumeSnapshot: {}, jdSnapshot: {}, materialsSnapshot: {}, settingsSnapshot: {},
  }
}

/** A browser whose speech recognition exists but never hears anything. */
class FakeRecognition {
  continuous = false
  interimResults = false
  lang = ''
  onstart: (() => void) | null = null
  onresult = null
  onerror = null
  onend: (() => void) | null = null
  start() { this.onstart?.() }
  stop() { this.onend?.() }
  abort() {}
}

function paused(loaded: MockInterviewSession): MockInterviewSession {
  return { ...loaded, session: { ...loaded.session, status: 'PAUSED' } }
}

function mountSession() {
  return mount(MockInterviewSessionPage, { global: { stubs: { RouterLink: true, ChevronRightIcon: true } } })
}

describe('interview draft persistence', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    window.localStorage.clear()
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
    Object.values(api).forEach((mock) => mock.mockReset())
    api.fetchMockInterview.mockResolvedValue(session())
    api.pauseMockInterview.mockImplementation(async () => paused(session()))
    api.heartbeatMockInterview.mockImplementation(async () => session().session)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
  })
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

  it.each(['TEXT', 'VOICE'] as const)('keeps the newest %s answer while a previous save completes, then drains it with the acknowledged version', async (mode) => {
    api.fetchMockInterview.mockResolvedValue(session(mode))
    const saveApi = mode === 'VOICE' ? api.saveMockInterviewTranscript : api.saveMockInterviewTextDraft
    const first = deferred<MockInterviewAnswer>()
    const second = deferred<MockInterviewAnswer>()
    saveApi.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const wrapper = mountSession()
    await flushPromises()
    await wrapper.get('textarea').setValue('第一版回答')
    await vi.advanceTimersByTimeAsync(850)
    await wrapper.get('textarea').setValue('第二版更完整的回答')
    await vi.advanceTimersByTimeAsync(850)
    expect(saveApi).toHaveBeenCalledTimes(1)

    first.resolve(savedAnswer('第一版回答', 2))
    await flushPromises()
    expect(window.localStorage.getItem('jobproof:mock-interview:session-1:question-1')).toBe('第二版更完整的回答')
    expect(wrapper.get('textarea').element.value).toBe('第二版更完整的回答')
    if (mode === 'TEXT') expect(wrapper.get('.mi-answer-status').text()).not.toContain('已自动保存')
    expect(saveApi).toHaveBeenLastCalledWith('session-1', 'question-1', '第二版更完整的回答', 2)

    second.resolve(savedAnswer('第二版更完整的回答', 3))
    await flushPromises()
    expect(window.localStorage.getItem('jobproof:mock-interview:session-1:question-1')).toBeNull()
    if (mode === 'TEXT') expect(wrapper.get('.mi-answer-status').text()).toContain('已自动保存')
  })

  it('stops a microphone permission result that arrives after leaving the session, without starting', async () => {
    api.fetchMockInterview.mockResolvedValue(session('VOICE'))
    vi.stubGlobal('webkitSpeechRecognition', FakeRecognition)
    const permission = deferred<MediaStream>()
    const stop = vi.fn()
    const start = vi.fn()
    const construct = vi.fn()
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn(() => permission.promise) } })
    vi.stubGlobal('MediaRecorder', class {
      static isTypeSupported() { return true }
      start = start
      constructor() { construct() }
    })
    const wrapper = mountSession()
    await flushPromises()
    await wrapper.get('.mi-record-button').trigger('click')
    wrapper.unmount()
    permission.resolve({ getTracks: () => [{ stop }] } as unknown as MediaStream)
    await flushPromises()

    expect(stop).toHaveBeenCalledTimes(1)
    expect(construct).not.toHaveBeenCalled()
    expect(start).not.toHaveBeenCalled()
  })

  it('retains the latest answer after a conflict and restores it on re-entry', async () => {
    const first = deferred<MockInterviewAnswer>()
    api.saveMockInterviewTextDraft.mockReturnValue(first.promise)
    const wrapper = mountSession()
    await flushPromises()
    await wrapper.get('textarea').setValue('发送中的回答')
    await vi.advanceTimersByTimeAsync(850)
    await wrapper.get('textarea').setValue('冲突发生前补充的最新回答')
    first.reject(new ApiClientError({ category: 'CONFLICT', reason: 'VERSION_CONFLICT', message: '回答版本冲突' }, 409))
    await flushPromises()
    expect(window.localStorage.getItem('jobproof:mock-interview:session-1:question-1')).toBe('冲突发生前补充的最新回答')
    expect(wrapper.get('.mi-answer-status').text()).toContain('本地草稿已保存')
    wrapper.unmount()
    const restored = mountSession()
    await flushPromises()
    expect(restored.get('textarea').element.value).toBe('冲突发生前补充的最新回答')
  })

  it('waits for an in-flight draft before submitting with its acknowledged version and hydrates the next question', async () => {
    const draft = deferred<MockInterviewAnswer>()
    api.saveMockInterviewTextDraft.mockReturnValue(draft.promise)
    const next = session()
    next.currentQuestion = { ...next.currentQuestion!, id: 'question-2', orderNo: 2 }
    next.questions.push(next.currentQuestion)
    api.submitMockInterviewAnswer.mockResolvedValue(next)
    window.localStorage.setItem('jobproof:mock-interview:session-1:question-2', '第二题已有本地回答')
    const wrapper = mountSession()
    await flushPromises()
    await wrapper.get('textarea').setValue('准备提交的回答')
    await vi.advanceTimersByTimeAsync(850)
    await wrapper.get('.submit').trigger('click')
    await flushPromises()
    expect(api.submitMockInterviewAnswer).not.toHaveBeenCalled()
    draft.resolve(savedAnswer('准备提交的回答', 2))
    await flushPromises()
    expect(api.submitMockInterviewAnswer).toHaveBeenCalledWith('session-1', 'question-1', '准备提交的回答', 2)
    await vi.advanceTimersByTimeAsync(700)
    expect(wrapper.get('textarea').element.value).toBe('第二题已有本地回答')
  })

  it('keeps a newer session copy when an old component save returns after unmount', async () => {
    const old = deferred<MockInterviewAnswer>()
    api.saveMockInterviewTextDraft.mockReturnValue(old.promise)
    const wrapper = mountSession()
    await flushPromises()
    await wrapper.get('textarea').setValue('旧页面提交的草稿')
    await vi.advanceTimersByTimeAsync(850)
    wrapper.unmount()
    const replacement = mountSession()
    await flushPromises()
    await replacement.get('textarea').setValue('新页面继续输入的回答')
    old.resolve(savedAnswer('旧页面提交的草稿', 2))
    await flushPromises()
    expect(window.localStorage.getItem('jobproof:mock-interview:session-1:question-1')).toBe('新页面继续输入的回答')
  })

  it('uses the microphone only for the level meter and browser transcription, never recording audio', async () => {
    api.fetchMockInterview.mockResolvedValue(session('VOICE'))
    vi.stubGlobal('webkitSpeechRecognition', FakeRecognition)
    const stopTrack = vi.fn()
    const recorder = vi.fn()
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: {
      getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop: stopTrack }] }),
    } })
    vi.stubGlobal('MediaRecorder', class { constructor() { recorder() } })
    vi.stubGlobal('AudioContext', class {
      createAnalyser() { return { fftSize: 128, frequencyBinCount: 64, getByteFrequencyData() {} } }
      createMediaStreamSource() { return { connect() {} } }
      close() { return Promise.resolve() }
    })
    const wrapper = mountSession()
    await flushPromises()
    await wrapper.get('.mi-record-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('.mi-voice__states').text()).toContain('录音中')
    await wrapper.get('.mi-record-button').trigger('click')
    await flushPromises()
    expect(stopTrack).toHaveBeenCalledOnce()
    expect(recorder).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('录音不会上传或保存')
  })

  it('shows the server clock and no score for an answer the model has not evaluated', async () => {
    const loaded = session()
    loaded.session.elapsedSeconds = 125
    loaded.session.durationMinutes = 20
    loaded.settingsSnapshot = { feedbackMode: 'AFTER_EACH' }
    api.fetchMockInterview.mockResolvedValue(loaded)
    const next = session()
    next.session.elapsedSeconds = 190
    next.settingsSnapshot = { feedbackMode: 'AFTER_EACH' }
    next.currentQuestion = { ...next.currentQuestion!, id: 'question-2', orderNo: 2 }
    next.questions.push(next.currentQuestion)
    next.answers = [{ ...savedAnswer('提交的回答', 2), status: 'SUBMITTED', feedback: { generationMode: 'PENDING', modelNotice: 'AI 通道暂不可用' } }]
    api.submitMockInterviewAnswer.mockResolvedValue(next)
    api.saveMockInterviewTextDraft.mockResolvedValue(savedAnswer('提交的回答', 2))
    const wrapper = mountSession()
    await flushPromises()
    expect(wrapper.get('.mi-clock').text()).toContain('02:05')
    expect(wrapper.get('.mi-clock').text()).toContain('20 分钟')
    await vi.advanceTimersByTimeAsync(3000)
    expect(wrapper.get('.mi-clock').text()).toContain('02:08')
    await wrapper.get('textarea').setValue('提交的回答')
    await wrapper.get('.submit').trigger('click')
    await flushPromises()
    expect(wrapper.get('.mi-clock').text()).toContain('03:10')
    expect(wrapper.get('.mi-drawer').text()).toContain('本题待评估')
    expect(wrapper.get('.mi-drawer').text()).toContain('AI 通道暂不可用')
    expect(wrapper.find('.mi-drawer__score').exists()).toBe(false)
  })

  it('labels a follow-up asked live as 追问 instead of a new numbered question', async () => {
    const data = session()
    const followUp = { id: 'question-2', orderNo: 2, questionType: 'FOLLOW_UP', prompt: '你提到的样本是怎么选出来的？', sourceRefs: ['ANSWER'], status: 'PENDING', parentQuestionId: 'question-1' }
    api.fetchMockInterview.mockResolvedValue({ ...data, currentQuestion: followUp, questions: [...data.questions, followUp], answers: [] })
    const wrapper = mountSession()
    await flushPromises()
    expect(wrapper.get('.mi-question__no').text()).toBe('追问')
    expect(wrapper.get('.mi-question__source').text()).toContain('针对你刚才的回答')
    expect(wrapper.text()).toContain('你提到的样本是怎么选出来的')
    wrapper.unmount()
  })

  it('does not offer recording where the browser cannot transcribe, and a typed answer can be submitted', async () => {
    api.fetchMockInterview.mockResolvedValue(session('VOICE'))
    api.submitMockInterviewAnswer.mockResolvedValue({ ...session('VOICE'), session: { ...session('VOICE').session, answeredCount: 1 } })
    api.saveMockInterviewTranscript.mockImplementation(async (_s: string, _q: string, text: string) => ({ ...savedAnswer(text, 2), mode: 'VOICE' }))
    const getUserMedia = vi.fn()
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia } })
    const wrapper = mountSession()
    await flushPromises()
    expect(wrapper.get('[data-testid="recognition-unsupported"]').text()).toContain('可以直接在下面输入回答')
    expect(wrapper.get('.mi-record-button').attributes('disabled')).toBeDefined()
    await wrapper.get('#mi-transcript').setValue('我负责了订单服务的接口重构')
    await wrapper.get('.mi-voice button.submit').trigger('click')
    await flushPromises()
    expect(getUserMedia).not.toHaveBeenCalled()
    expect(api.submitMockInterviewAnswer).toHaveBeenCalledWith('session-1', 'question-1', '我负责了订单服务的接口重构', expect.anything())
  })

  it('says which microphone problem it is, and still submits what was typed after it failed', async () => {
    api.fetchMockInterview.mockResolvedValue(session('VOICE'))
    vi.stubGlobal('webkitSpeechRecognition', FakeRecognition)
    api.submitMockInterviewAnswer.mockResolvedValue({ ...session('VOICE'), session: { ...session('VOICE').session, answeredCount: 1 } })
    api.saveMockInterviewTranscript.mockImplementation(async (_s: string, _q: string, text: string) => ({ ...savedAnswer(text, 2), mode: 'VOICE' }))
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: {
      getUserMedia: vi.fn().mockRejectedValue(Object.assign(new Error('none'), { name: 'NotFoundError' })),
    } })
    const wrapper = mountSession()
    await flushPromises()
    await wrapper.get('.mi-record-button').trigger('click')
    await flushPromises()
    expect(wrapper.get('#mi-permission-title').text()).toContain('没有找到可用的麦克风')
    expect(wrapper.find('.mi-modal__steps').exists()).toBe(false)
    await wrapper.get('.mi-modal__close').trigger('click')
    await wrapper.get('#mi-transcript').setValue('麦克风坏了，我直接打字回答')
    const submit = wrapper.get('.mi-voice button.submit')
    expect(submit.attributes('disabled')).toBeUndefined()
    await submit.trigger('click')
    await flushPromises()
    expect(api.submitMockInterviewAnswer).toHaveBeenCalledWith('session-1', 'question-1', '麦克风坏了，我直接打字回答', expect.anything())
  })

  it('pauses the interview when the page is hidden, and says why when the user is back', async () => {
    const wrapper = mountSession()
    await flushPromises()
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(api.pauseMockInterview).toHaveBeenCalledWith('session-1', { keepalive: true })
    expect(wrapper.get('[data-testid="pause-reason"]').text()).toContain('切换到了其他页面')
  })

  it('reports in every minute, and shows the pause the server made after the page went quiet', async () => {
    api.heartbeatMockInterview.mockResolvedValueOnce({ ...session().session, elapsedSeconds: 300 })
      .mockResolvedValueOnce({ ...session().session, status: 'PAUSED', elapsedSeconds: 320 })
    const wrapper = mountSession()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(api.heartbeatMockInterview).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.mi-clock').text()).toContain('05:0')
    await vi.advanceTimersByTimeAsync(60_000)
    await flushPromises()
    expect(wrapper.get('[data-testid="pause-reason"]').text()).toContain('页面有一段时间没有响应')
    expect(wrapper.get('.mi-clock').text()).toContain('05:20')
  })

  it('asks once when the set length is reached, offering to finish', async () => {
    const loaded = session()
    loaded.session.durationMinutes = 15
    loaded.session.elapsedSeconds = 15 * 60 - 2
    api.fetchMockInterview.mockResolvedValue(loaded)
    window.sessionStorage.clear()
    const wrapper = mountSession()
    await flushPromises()
    expect(wrapper.find('[data-testid="time-up"]').exists()).toBe(false)
    await vi.advanceTimersByTimeAsync(3_000)
    expect(wrapper.get('[data-testid="time-up"]').text()).toContain('已到设定的 15 分钟')
    await wrapper.findAll('[data-testid="time-up"] button').find((button) => button.text() === '继续作答')!.trigger('click')
    expect(wrapper.find('[data-testid="time-up"]').exists()).toBe(false)
    expect(window.sessionStorage.getItem('jobproof:mock-interview:session-1:time-up')).toBe('1')
  })

  it('pauses a running interview when leaving the room', async () => {
    const wrapper = mountSession()
    await flushPromises()
    wrapper.unmount()
    expect(api.pauseMockInterview).toHaveBeenCalledWith('session-1', { keepalive: true })
  })
})
