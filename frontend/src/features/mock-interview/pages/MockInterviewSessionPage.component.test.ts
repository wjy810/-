import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MockInterviewAnswer, MockInterviewSession } from '../types'
import { ApiClientError } from '@/shared/api/types'

const api = vi.hoisted(() => ({
  fetchMockInterview: vi.fn(),
  saveMockInterviewTextDraft: vi.fn(),
  saveMockInterviewTranscript: vi.fn(),
  submitMockInterviewAnswer: vi.fn(),
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
})
