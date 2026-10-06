import { api } from '@/shared/api/client'
import type {
  MockInterviewAnswer,
  MockInterviewCreate,
  MockInterviewDashboard,
  MockInterviewDraft,
  MockInterviewReport,
  MockInterviewSession,
  MockInterviewSessionSummary,
} from '../types'

const ROOT = '/api/v1/mock-interviews'

function query(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  return params.size ? `?${params.toString()}` : ''
}

export const fetchMockInterviewDashboard = () => api<MockInterviewDashboard>(`${ROOT}/dashboard`)
export const createMockInterviewDraft = () => api<MockInterviewDraft>(`${ROOT}/drafts`, { method: 'POST' })
export const fetchMockInterviewDraft = (id: string) => api<MockInterviewDraft>(`${ROOT}/drafts/${encodeURIComponent(id)}`)
export const saveMockInterviewDraft = (id: string, step: number, payload: Record<string, unknown>, expectedVersion: number) =>
  api<MockInterviewDraft>(`${ROOT}/drafts/${encodeURIComponent(id)}`, {
    method: 'PATCH', body: JSON.stringify({ step, payload, expectedVersion }),
  })
export const createMockInterview = (command: MockInterviewCreate) =>
  api<MockInterviewSession>(`${ROOT}/sessions`, { method: 'POST', body: JSON.stringify(command) })
export const listMockInterviews = (filters: { status?: string; mode?: string; type?: string; limit?: number } = {}) =>
  api<MockInterviewSessionSummary[]>(`${ROOT}/sessions${query({ ...filters, limit: filters.limit ?? 50 })}`)
export const fetchMockInterview = (id: string) => api<MockInterviewSession>(`${ROOT}/sessions/${encodeURIComponent(id)}`)
export const saveMockInterviewTextDraft = (sessionId: string, questionId: string, answer: string, expectedVersion?: number) =>
  api<MockInterviewAnswer>(`${ROOT}/sessions/${encodeURIComponent(sessionId)}/text-drafts/${encodeURIComponent(questionId)}`, {
    method: 'PUT', body: JSON.stringify({ answer, expectedVersion }),
  })
export const saveMockInterviewTranscript = (sessionId: string, questionId: string, answer: string, expectedVersion?: number) =>
  api<MockInterviewAnswer>(`${ROOT}/sessions/${encodeURIComponent(sessionId)}/transcript/${encodeURIComponent(questionId)}`, {
    method: 'PATCH', body: JSON.stringify({ answer, expectedVersion }),
  })
export const submitMockInterviewAnswer = (sessionId: string, questionId: string, answer: string, expectedVersion?: number) =>
  api<MockInterviewSession>(`${ROOT}/sessions/${encodeURIComponent(sessionId)}/answers/${encodeURIComponent(questionId)}`, {
    method: 'POST', body: JSON.stringify({ answer, expectedVersion }),
  })
export async function uploadMockInterviewAudio(sessionId: string, questionId: string, sequence: number, file: Blob, durationMs: number) {
  const body = new FormData()
  body.append('questionId', questionId)
  body.append('sequence', String(sequence))
  body.append('durationMs', String(durationMs))
  body.append('file', file, `answer-${sequence}.webm`)
  return api<{ id: string; sequence: number }>(`${ROOT}/sessions/${encodeURIComponent(sessionId)}/audio/chunks`, { method: 'POST', body })
}
export const switchMockInterviewMode = (id: string, mode: 'TEXT' | 'VOICE') =>
  api<MockInterviewSession>(`${ROOT}/sessions/${encodeURIComponent(id)}/switch-mode`, { method: 'POST', body: JSON.stringify({ mode }) })
export const pauseMockInterview = (id: string) => api<MockInterviewSession>(`${ROOT}/sessions/${encodeURIComponent(id)}/pause`, { method: 'POST' })
export const resumeMockInterview = (id: string) => api<MockInterviewSession>(`${ROOT}/sessions/${encodeURIComponent(id)}/resume`, { method: 'POST' })
export const completeMockInterview = (id: string) => api<MockInterviewReport>(`${ROOT}/sessions/${encodeURIComponent(id)}/complete`, { method: 'POST' })
export const abandonMockInterview = (id: string) => api<MockInterviewSession>(`${ROOT}/sessions/${encodeURIComponent(id)}/abandon`, { method: 'POST' })
export const retryMockInterview = (id: string) => api<MockInterviewSession>(`${ROOT}/sessions/${encodeURIComponent(id)}/retry`, { method: 'POST' })
export const fetchMockInterviewReport = (id: string) => api<MockInterviewReport>(`${ROOT}/reports/${encodeURIComponent(id)}`)
