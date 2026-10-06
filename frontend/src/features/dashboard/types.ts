export type RecentResume = {
  id: string
  title: string
  status: string
  updatedAt: string | null
  conversationId: string | null
  templateName: string | null
  confirmedModules: number
  totalModules: number
}

export type RecentMatch = {
  id: string
  title: string
  company: string | null
  status: string
  score: number | null
  updatedAt: string | null
}

export type ResumableInterview = {
  id: string
  title: string
  positionName: string | null
  answeredCount: number
  questionCount: number
  updatedAt: string | null
}

export type RecentCanvas = {
  sessionId: string
  title: string
  /** Overall ability progress, 0–100. */
  progress: number
  nodeCount: number
  updatedAt: string | null
}

export type WorkspaceOverview = {
  profile: { completeness: number; missing: string[] } | null
  resumes: { total: number; exportable: number; recent: RecentResume[] } | null
  jobMatches: { total: number; completed: number; recent: RecentMatch[] } | null
  mockInterviews: { total: number; completed: number; averageScore: number | null; resumable: ResumableInterview | null } | null
  careerCanvases: { total: number; recent: RecentCanvas[] } | null
  unreadNotifications: number | null
  /** Modules whose data could not be loaded; the rest of the overview is still valid. */
  degraded: string[]
}
