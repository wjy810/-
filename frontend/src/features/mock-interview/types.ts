export type MockInterviewMode = 'TEXT' | 'VOICE'
export type MockInterviewStatus =
  | 'READY' | 'IN_PROGRESS' | 'ANSWERING' | 'ANALYZING' | 'FEEDBACK'
  | 'PAUSED' | 'OFFLINE' | 'TRANSCRIPTION_FAILED' | 'COMPLETED' | 'ABANDONED'

export type MockInterviewSessionSummary = {
  id: string
  title: string
  positionName: string
  companyName?: string | null
  mode: MockInterviewMode
  interviewType: string
  difficulty: string
  status: MockInterviewStatus | string
  answeredCount: number
  questionCount: number
  score?: number | null
  updatedAt: string
  completedAt?: string | null
  /** Active interview time measured by the server, pauses excluded. */
  elapsedSeconds?: number | null
  durationMinutes?: number | null
}

export type MockInterviewDashboard = {
  total: number
  completed: number
  textCount: number
  voiceCount: number
  /** Null until at least one answer has been evaluated by the model. */
  averageScore: number | null
  resumable?: MockInterviewSessionSummary | null
  recent: MockInterviewSessionSummary[]
}

export type MockInterviewDraft = {
  id: string
  step: number
  status: string
  payload: Record<string, unknown>
  version: number
  updatedAt: string
}

export type MockInterviewQuestion = {
  id: string
  orderNo: number
  questionType: string
  prompt: string
  sourceLabel?: string | null
  sourceRefs: string[]
  status: string
}

export type MockInterviewAnswer = {
  id: string
  questionId: string
  mode: MockInterviewMode
  answer: string
  transcript?: string | null
  status: string
  scores: Record<string, number>
  feedback: Record<string, unknown>
  version: number
  updatedAt: string
  submittedAt?: string | null
}

export type MockInterviewSession = {
  session: MockInterviewSessionSummary
  currentQuestion?: MockInterviewQuestion | null
  questions: MockInterviewQuestion[]
  answers: MockInterviewAnswer[]
  resumeSnapshot: Record<string, unknown>
  jdSnapshot: Record<string, unknown>
  materialsSnapshot: Record<string, unknown>
  settingsSnapshot: Record<string, unknown>
}

export type MockInterviewCreate = {
  draftId?: string
  resumeId: string
  taxonomyNodeId?: string
  taxonomyCategoryId?: string
  taxonomyGroupId?: string
  careerRecordIds: string[]
  careerFileIds: string[]
  positionName: string
  companyName?: string
  userNote?: string
  mode: MockInterviewMode
  interviewType: string
  difficulty: string
  durationMinutes: number
  questionCount: number
  languageCode: string
  feedbackMode: string
  followUpEnabled: boolean
  consentConfirmed: boolean
  jobMatchId?: string
}

export type MockInterviewReview = {
  question: MockInterviewQuestion
  answer?: MockInterviewAnswer | null
  feedback: Record<string, unknown>
  scores: Record<string, number>
}

export type MockInterviewReport = {
  id: string
  session: MockInterviewSessionSummary
  /** Null when no answer has been evaluated by the model yet. */
  overallScore: number | null
  dimensions: Record<string, number>
  summary: { strengths?: string[]; risks?: string[] } & Record<string, unknown>
  recommendations: string[]
  questions: MockInterviewReview[]
  generatedAt: string
  evaluatedCount: number
  pendingCount: number
}

export type MockInterviewEvaluation = {
  evaluated: number
  pending: number
  aiAvailable: boolean
}
