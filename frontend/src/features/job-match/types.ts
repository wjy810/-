import type { TaskView } from '@/shared/api/task'

export type JobMatchStatus =
  | 'DRAFT' | 'JD_PARSED' | 'RESUME_CONFIRMED' | 'EVIDENCE_AUTHORIZED'
  | 'ANALYZING' | 'NEEDS_CLARIFICATION' | 'COMPLETED'
  | 'JD_PARSE_FAILED' | 'RESUME_UPLOAD_FAILED' | 'ANALYSIS_PAUSED' | 'CANCELLED'

export type MatchRequirement = {
  id: string
  sequence: number
  category: string
  text: string
  priority: string
  hardGate: boolean
  sourceLocator: string
  sourceQuote: string
  confidence: number
  userCorrected: boolean
}

export type ResumeOption = {
  masterId: string
  branchId: string
  revisionId: string
  title: string
  languageCode: string
  contentHash: string
  updatedAt: string
}

export type Clarification = {
  id: string
  requirementId?: string | null
  sequence: number
  question: string
  options: string[]
  evidenceContext: Record<string, unknown>
  answerCode?: string | null
  answerNote?: string | null
  status: string
  version: number
}

export type JobMatch = {
  id: string
  status: JobMatchStatus
  version: number
  progress: number
  checkpoint?: string | null
  errorCode?: string | null
  jobId: string
  jobVersionId: string
  title: string
  company?: string | null
  location?: string | null
  workMode?: string | null
  requirements: MatchRequirement[]
  resume?: ResumeOption | null
  resumeImportId?: string | null
  evidenceMode?: string | null
  authorizationId?: string | null
  reportId?: string | null
  analysisTaskId?: string | null
  clarifications: Clarification[]
  createdAt: string
  updatedAt: string
}

export type JobMatchSummary = {
  id: string
  title: string
  company?: string | null
  resumeTitle?: string | null
  status: JobMatchStatus
  score?: number | null
  confidence?: string | null
  progress: number
  updatedAt: string
}

export type JobMatchDashboard = {
  total: number
  completed: number
  /** Matches still in progress (not completed or cancelled). */
  pending?: number
  /** Absent until at least one report has a score. */
  averageScore?: number | null
  optimized: number
  recent: JobMatchSummary[]
}

export type JobMatchHistoryPage = {
  items: JobMatchSummary[]
  page: number
  size: number
  total: number
  totalPages: number
}

export type MatchCapabilities = {
  enabled: boolean
  ocrAvailable: boolean
  jdSources: string[]
  resumeSources: string[]
  maxFileBytes: number
  minJdChars: number
  maxJdChars: number
  quota: {
    id: string
    periodKey: string
    grantedUnits: number
    usedUnits: number
    heldUnits: number
    remainingUnits: number
    versionNo: number
  }
}

export type EvidenceCandidate = {
  sourceType: 'CAREER_RECORD' | 'CAREER_FILE'
  sourceId: string
  title: string
  excerpt?: string | null
  locator?: string | null
  strength: string
  relevance: number
  recommended: boolean
}

export type RedactionPreview = {
  matchId: string
  resumeRevisionId: string
  includedSources: Array<Record<string, unknown>>
  excludedFields: string[]
  notice: string
}

/** IGNORE withdraws an earlier mark. */
export type ClaimFeedback = 'ACCURATE' | 'INACCURATE' | 'IGNORE'

export type JobMatchClaim = {
  id: string
  requirementId: string
  conclusionType: string
  score?: number | null
  confidence: number
  evidenceIds: string[]
  reasoning: string
  feedback?: string | null
  feedbackRequestId?: string | null
  version: number
}

export type ImprovementTask = {
  id: string
  gapCode: string
  phase: string
  title: string
  task: string
  expectedOutput: string
  acceptanceCriteria: string
  estimatedHours: number
  priority: string
  status: string
  version: number
}

/** A requirement's verdict (job-match-report-v3). */
export type RequirementVerdict = 'MET' | 'PARTIAL' | 'UNCLEAR' | 'NOT_FOUND'

/** job-match-report-v3: how the score was reached (docs/phase2/07). */
export type MatchScoring = {
  method: 'AI_VERDICT' | 'RULE_PREVIEW'
  verdictValues: Record<RequirementVerdict, number>
  priorityWeights: Record<string, number>
  citedCount: number
  requirementCount: number
}

export type MatchReportDocument = {
  score?: number
  /** 'HIGH' | 'MEDIUM' | 'LOW' since v3; a 0-100 number in older reports. */
  confidence?: number | string
  hardGatePassed?: boolean
  /** Null for a dimension with no requirement of that kind. */
  dimensions?: Record<string, number | null>
  requirements?: Array<Record<string, unknown>>
  hardGates?: Array<Record<string, unknown>>
  strengths?: Array<Record<string, unknown>>
  gaps?: Array<Record<string, unknown>>
  recommendation?: { code?: string; rationale?: string; scoredCode?: string; capped?: boolean }
  scoring?: MatchScoring
  /** No model run finished: the rule preview. */
  preview?: boolean
  /** The chosen resume changed after this analysis. */
  resumeChanged?: boolean
  ruleStrengths?: Array<Record<string, unknown>>
  ruleGaps?: Array<Record<string, unknown>>
  notice?: string
  ai?: {
    summary?: string | Record<string, unknown>
    hardGates?: Array<Record<string, unknown>>
    strengths?: Array<Record<string, unknown>>
    gaps?: Array<Record<string, unknown>>
    evidenceMatrix?: Array<Record<string, unknown>>
    clarifications?: Array<Record<string, unknown>>
    learningPlan?: Array<Record<string, unknown>>
    resumeSuggestions?: Array<Record<string, unknown>>
    interviewTopics?: Array<string | Record<string, unknown>>
    recommendation?: { code?: string; rationale?: string }
  }
  generatedAt?: string
}

/** An authorized evidence item that claims and `evidenceIds` refer to. */
export type EvidenceSource = { id: string; sourceType: string; title: string }

export type JobMatchReport = {
  id: string
  matchId: string
  report: MatchReportDocument
  claims: JobMatchClaim[]
  learningPlan: ImprovementTask[]
  evidenceSources?: EvidenceSource[]
  status: JobMatchStatus
  updatedAt: string
}

export type JobMatchReportVersion = {
  id: string
  reportId: string
  version: number
  score: number
  confidence: 'HIGH' | 'MEDIUM' | 'LOW'
  recommendation?: string | null
  current: boolean
  createdAt: string
}

export type JobMatchReportComparison = {
  fromVersion: number
  toVersion: number
  changes: Array<{ label: string; before: number; after: number; delta: number }>
  beforeSuggestions: Array<Record<string, unknown>>
  afterSuggestions: Array<Record<string, unknown>>
}

export type AnalysisStart = { match: JobMatch; task: TaskView; quota: MatchCapabilities['quota'] }
/** A taxonomy direction that names at least one skill from the frozen resume. */
export type CareerDirection = { taxonomyNodeId: string; title: string; category: string; sharedSkills: string[] }
export type SimilarDirections = { resumeSkills: string[]; directions: CareerDirection[] }

export type ResumeImportSession = {
  id: string
  sourceType: string
  careerFileId?: string | null
  sourceFilename?: string | null
  status: string
  structuredDraft?: Record<string, unknown> | null
  sourceMap?: Record<string, unknown> | null
  confidence?: Record<string, unknown> | null
  task?: TaskView | null
  errorCode?: string | null
  resultMasterId?: string | null
  resultBranchId?: string | null
  resultRevisionId?: string | null
  version: number
}
