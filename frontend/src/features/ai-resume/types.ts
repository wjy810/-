import type { ResumeCandidateView, ResumeDesignSettings, ResumeLayoutView, ResumeMasterView } from '@/features/resume/types'

export type AiIdentity = 'STUDENT' | 'GRADUATE' | 'PROFESSIONAL'
export type AiResumePdfExportMode = 'STANDARD' | 'ANONYMOUS'

export type AiResumeCard = {
  id: string
  cardType: string
  schemaVersion: string
  status: string
  payload: Record<string, unknown>
  validation?: Record<string, unknown> | null
  versionNo: number
  createdAt: string
  updatedAt: string
}

export type AiResumeMessage = {
  id: string
  sequence: number
  role: 'USER' | 'ASSISTANT' | string
  messageType: string
  status: string
  content?: string | null
  errorCode?: string | null
  model?: string | null
  inputTokens: number
  outputTokens: number
  createdAt: string
  completedAt?: string | null
}

export type AiResumeChangeItemStatus = 'PENDING' | 'APPLIED' | 'REJECTED' | 'STALE' | 'UNDONE'

export type AiResumeChangeItem = {
  id: string
  sequence: number
  module: string
  targetPath: string
  operation: 'REPLACE_TEXT' | 'REPLACE_SEGMENT' | 'APPEND_SEGMENT' | string
  beforeValue: unknown
  proposedValue: unknown
  correctedValue?: unknown
  reason: string
  sourceFacts: Array<{ source: string; quote: string }>
  factStatus: string
  quality: Record<string, unknown>
  status: AiResumeChangeItemStatus | string
  version: number
  appliedRevisionId?: string | null
  decidedAt?: string | null
  createdAt: string
  updatedAt: string
}

export type AiResumeChangeSet = {
  id: string
  messageId?: string | null
  branchId: string
  baseRevisionId: string
  status: 'PENDING' | 'PARTIAL' | 'APPLIED' | 'REJECTED' | 'STALE' | 'CANCELLED' | string
  actionCode: string
  summary?: string | null
  qualityPolicyVersion: string
  version: number
  items: AiResumeChangeItem[]
  appliedRevisionId?: string | null
  createdAt: string
  updatedAt: string
}

export type AiQuota = {
  id: string
  periodKey: string
  grantedUnits: number
  usedUnits: number
  heldUnits: number
  remainingUnits: number
  versionNo: number
}

export type AiConsent = {
  status: 'REQUIRED' | 'GRANTED' | 'REVOKED' | string
  policyVersion: string
  grantedAt?: string | null
  revokedAt?: string | null
}

export type AiWritingStyleCode =
  | 'SYSTEM_RECOMMENDED'
  | 'PROFESSIONAL_CONCISE'
  | 'RESULTS_ORIENTED'
  | 'TECHNICAL_RIGOR'
  | 'STEADY_FORMAL'

export type AiWritingPreference = {
  code: AiWritingStyleCode
  label: string
  source: 'SYSTEM' | 'USER' | string
  updatedAt?: string | null
}

export type AiHistoryDeletion = {
  messageBodiesDeleted: number
  pendingCandidatesDeleted: number
  auditMarker: string
  deletedAt: string
}

export type AiResumePhoto = {
  fileId: string
  contentUrl: string
}

export type AiTextImport = {
  parserVersion: string
  aiCalled: boolean
  ignoredSensitiveLines: number
  ambiguousLines: number
  candidates: ResumeCandidateView[]
}

export type AiCancellation = {
  requestId: string
  accepted: boolean
  status: 'CANCEL_REQUESTED' | 'NOT_RUNNING' | string
}

export type AiDescriptionSuggestion = {
  suggestion: string
  reason: string
  sourceFields: string[]
  verificationRequired: boolean
  verificationItems: string[]
  requestId: string
  model: string
  inputTokens: number
  outputTokens: number
  remainingQuota: number
  promptVersion: string
}

export type AiSummarySourceRef = {
  key: string
  label: string
  excerpt: string
}

export type AiSummaryCandidate = {
  style: '专业简洁' | '成果导向' | '稳健正式'
  text: string
  reason: string
  sourceRefs: AiSummarySourceRef[]
}

export type AiSummarySuggestion = {
  candidates: AiSummaryCandidate[]
  requestId: string
  model: string
  inputTokens: number
  outputTokens: number
  remainingQuota: number
  promptVersion: string
}

export type AiSkillSuggestionMode = 'EXTRACT' | 'EXPAND' | 'JOB'
export type AiSkillEvidenceStatus = 'SUPPORTED' | 'NEEDS_CONFIRMATION' | 'GAP'

export type AiSkillSourceRef = {
  key: string
  label: string
  excerpt: string
}

export type AiSkillNameCandidate = {
  name: string
  category: string
  evidenceStatus: AiSkillEvidenceStatus
  reason: string
  sourceRefs: AiSkillSourceRef[]
}

export type AiSkillGroupSuggestion = {
  category: string
  items: string[]
  description: string
  sourceRefs: AiSkillSourceRef[]
  verificationRequired: boolean
  verificationItems: string[]
}

export type AiSkillSuggestion = {
  phase: 'NAMES' | 'DETAILS'
  mode: AiSkillSuggestionMode
  candidates: AiSkillNameCandidate[]
  groups: AiSkillGroupSuggestion[]
  requestId: string
  model: string
  inputTokens: number
  outputTokens: number
  remainingQuota: number
  promptVersion: string
  careerLibrarySnapshotVersion: number
}

export type AiCertificateSourceRef = {
  key: string
  label: string
  excerpt: string
}

export type AiCertificateNameCandidate = {
  name: string
  reason: string
  candidateType: 'OWNED' | 'RECOMMENDED'
  sourceRefs: AiCertificateSourceRef[]
}

export type AiCertificateItemSuggestion = {
  name: string
  issuer: string
  date: string
  description: string
  sourceRefs: AiCertificateSourceRef[]
  verificationRequired: boolean
  verificationItems: string[]
}

export type AiCertificateSuggestion = {
  phase: 'NAMES' | 'DETAILS'
  candidates: AiCertificateNameCandidate[]
  certificates: AiCertificateItemSuggestion[]
  requestId: string
  model: string
  inputTokens: number
  outputTokens: number
  remainingQuota: number
  promptVersion: string
  careerLibrarySnapshotVersion: number
  careerLibraryEvidenceEnabled: boolean
}

export type AiHonorSourceRef = AiCertificateSourceRef
export type AiHonorNameCandidate = AiCertificateNameCandidate
export type AiHonorItemSuggestion = AiCertificateItemSuggestion

export type AiHonorSuggestion = {
  phase: 'NAMES' | 'DETAILS'
  candidates: AiHonorNameCandidate[]
  honors: AiHonorItemSuggestion[]
  requestId: string
  model: string
  inputTokens: number
  outputTokens: number
  remainingQuota: number
  promptVersion: string
  careerLibrarySnapshotVersion: number
  careerLibraryEvidenceEnabled: boolean
}

export type AiCredentialRecommendation = {
  kind: 'CERTIFICATE' | 'HONOR'
  candidates: AiCertificateNameCandidate[]
  requestId: string
  model: string
  inputTokens: number
  outputTokens: number
  remainingQuota: number
  promptVersion: string
}

export type AiResumeConversation = {
  id: string
  masterId: string
  activeBranchId: string
  status: string
  onboardingStage: string
  identityType: AiIdentity
  lastSequence: number
  versionNo: number
  resume: ResumeMasterView
  content: Record<string, unknown>
  layout?: ResumeLayoutView | null
  activeTemplate?: AiSmartTemplate | null
  activeDesign?: AiResumeDesignPreference | null
  photo?: AiResumePhoto | null
  cards: AiResumeCard[]
  messages: AiResumeMessage[]
  changeSets: AiResumeChangeSet[]
  quota: AiQuota
  consent: AiConsent
  careerLibraryEvidence: AiCareerEvidencePreference
  aiAvailable: boolean
  aiUnavailableReason?: string | null
  createdAt: string
  updatedAt: string
}

export type AiCareerEvidencePreference = {
  enabled: boolean
  snapshotVersion: number
  source: string
  updatedAt?: string | null
}

export type AiResumeDesignPreference = {
  id?: string | null
  templateId: string
  variantCode: string
  settings: ResumeDesignSettings
  versionNo: number
  updatedAt?: string | null
}

export type AiResumeDesignPreset = {
  variantCode: string
  displayName: string
  settings: ResumeDesignSettings
}

export type AiSmartTemplate = {
  templateId: string
  displayName: string
  familyName: string
  languageCode: string
  recommendedPages: string
  photoPolicy: string
  variants: string[]
  rendererProtocol: string
  layoutDefinitionJson: string
  thumbnailUri?: string | null
  docxAvailable: boolean
  docxUnavailableReason?: string | null
  presets: AiResumeDesignPreset[]
  design: AiResumeDesignPreference
}

export type JobTaxonomyNode = {
  id: string
  parentId?: string | null
  categoryId?: string | null
  groupId?: string | null
  level: 'CATEGORY' | 'GROUP' | 'JOB' | string
  code: string
  displayName: string
  catalogOccupationCode: string
  status: string
}

export type JobTaxonomySelection = {
  job: JobTaxonomyNode
  categoryId: string
  groupId: string
}

export type AiResumeRevision = {
  id: string
  branchId: string
  revisionNo: number
  source: string
  sourceObjectId?: string | null
  content: Record<string, unknown>
  contentHash: string
  createdAt: string
}

export type AiResumeBranch = {
  id: string
  parentBranchId?: string | null
  branchType: 'BASE' | 'JOB' | 'LANGUAGE' | string
  title: string
  languageCode: string
  jobVersionId?: string | null
  currentRevisionId: string
  sourceRevisionId?: string | null
  status: string
  reviewMetadata?: Record<string, unknown> | null
  versionNo: number
  active: boolean
  syncRequired: boolean
  createdAt: string
  updatedAt: string
}

export type AiResumeFieldDiff = {
  path: string
  before?: unknown
  after?: unknown
}

export type AiResumeBranchDiff = {
  branchId: string
  parentBranchId?: string | null
  syncRequired: boolean
  changes: AiResumeFieldDiff[]
}
