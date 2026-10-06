export type QuotaView = {
  id: string
  periodKey: string
  grantedUnits: number
  usedUnits: number
  heldUnits: number
  remainingUnits: number
  versionNo: number
}

export type ProfileItem = {
  id: string
  section: string
  claimType: 'FACT' | 'SELF_REPORTED' | 'INFERENCE'
  title: string
  payload: Record<string, unknown>
  sourceRefs: Array<Record<string, unknown> | string>
  status: string
  confirmed: boolean
  locked: boolean
  sortOrder: number
  version: number
  updatedAt: string
}

export type CareerPlanningProfile = {
  id: string
  status: 'DRAFT' | 'INTERVIEWING' | 'PENDING_CONFIRMATION' | 'CONFIRMED'
  entryMode: 'AI_DISCOVERY' | 'KNOWN_TARGET'
  objectiveTaxonomyId?: string | null
  basics: Record<string, unknown>
  preferences: Record<string, unknown>
  constraints: Record<string, unknown>
  snapshotHash?: string | null
  snapshotVersion: number
  version: number
  items: ProfileItem[]
  updatedAt: string
  confirmedAt?: string | null
}

export type EvidencePermission = {
  id: string
  sourceType: string
  sourceId: string
  sourceVersion: number
  scopes: string[]
  status: string
  permissionVersion: number
  createdAt: string
  revokedAt?: string | null
}

export type EvidenceOption = {
  sourceId: string
  sourceType: string
  sourceVersion: number
  title: string
  subtitle: string
  excerpt: string
  strength: string
  selected: boolean
  scopes: string[]
}

export type InterviewQuestion = { id: string; text: string; purpose: string; claimType: string }
export type InterviewAnswer = { questionId: string; question: string; answer: string }
export type InterviewRound = {
  id: string
  roundNo: number
  status: 'OPEN' | 'COMPLETED' | 'SKIPPED'
  questions: InterviewQuestion[]
  answers: InterviewAnswer[]
  model?: string | null
  promptVersion: string
  createdAt: string
  completedAt?: string | null
}

export type PlanningMessage = {
  id: string
  sequence: number
  role: 'ASSISTANT' | 'USER' | string
  type: string
  body?: string | null
  payload: Record<string, unknown>
  createdAt: string
}

export type CareerRecommendation = {
  id: string
  taxonomyNodeId: string
  title: string
  tier: 'READY_NOW' | 'AFTER_SMALL_GAP' | 'EXPLORATORY'
  fitSummary: string
  rationale: string[]
  gaps: string[]
  sourceRefs: string[]
  favorite: boolean
  sortOrder: number
}

export type RecommendationSet = {
  id: string
  status: 'READY' | 'INSUFFICIENT' | 'SUPERSEDED'
  profileSnapshotHash: string
  recommendations: CareerRecommendation[]
  insufficientReasons: string[]
  model?: string | null
  promptVersion: string
  createdAt: string
  completedAt?: string | null
}

export type CareerGoal = {
  id: string
  recommendationId: string
  taxonomyNodeId: string
  title: string
  status: string
  version: number
  confirmedAt: string
  archivedAt?: string | null
}

export type CanvasNode = {
  logicalNodeId: string
  type: CanvasNodeType
  status: CanvasNodeStatus
  title: string
  detail: Record<string, unknown>
  sourceRefs: string[]
  x: number
  y: number
  locked: boolean
  sortOrder: number
}

export type CanvasNodeType = 'CAREER' | 'DOMAIN' | 'SKILL' | 'KNOWLEDGE' | 'TASK' | 'EVIDENCE'
export type CanvasNodeStatus = 'NOT_STARTED' | 'PLANNED' | 'LEARNING' | 'PENDING_VALIDATION' | 'MASTERED' | 'PAUSED'
export type CanvasRelationType = 'TREE_PARENT' | 'PREREQUISITE'

export type CanvasRelation = {
  id: string
  fromNodeId: string
  toNodeId: string
  type: CanvasRelationType
}

export type CareerCanvas = {
  versionId: string
  version: number
  parentVersionId?: string | null
  reason: string
  changeSummary?: string | null
  graphHash: string
  promptVersion?: string | null
  schemaVersion?: string | null
  model?: string | null
  nodes: CanvasNode[]
  relations: CanvasRelation[]
  createdAt: string
}

export type CanvasVersion = {
  versionId: string
  version: number
  parentVersionId?: string | null
  reason: string
  changeSummary?: string | null
  graphHash: string
  createdBy: 'AI' | 'USER' | 'SYSTEM' | string
  promptVersion?: string | null
  schemaVersion?: string | null
  model?: string | null
  createdAt: string
}

export type CanvasVersionPage = {
  items: CanvasVersion[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type CanvasVersionFieldChange = {
  field: string
  beforeValue: unknown
  afterValue: unknown
}

export type CanvasVersionNodeDiff = {
  logicalNodeId: string
  changeTypes: Array<'ADDED' | 'REMOVED' | 'UPDATE' | 'MOVE' | string>
  beforeTitle?: string | null
  afterTitle?: string | null
  fields: CanvasVersionFieldChange[]
}

export type CanvasVersionRelationDiff = {
  changeType: 'ADDED' | 'REMOVED' | string
  type: CanvasRelationType
  fromNodeId: string
  toNodeId: string
}

export type CanvasVersionDiff = {
  fromVersion: number
  toVersion: number
  addedNodes: number
  removedNodes: number
  updatedNodes: number
  movedNodes: number
  addedRelations: number
  removedRelations: number
  nodes: CanvasVersionNodeDiff[]
  relations: CanvasVersionRelationDiff[]
}

export type CanvasNodeCreate = {
  type: Exclude<CanvasNodeType, 'CAREER'>
  title: string
  status: Exclude<CanvasNodeStatus, 'MASTERED'>
  parentNodeId: string
  detail: Record<string, unknown>
  sourceRefs: string[]
  locked: boolean
  expectedVersion: number
}

export type CanvasNodeUpdate = Partial<Pick<CanvasNode, 'title' | 'status' | 'detail' | 'sourceRefs' | 'locked' | 'x' | 'y'>> & {
  parentNodeId?: string
  expectedVersion: number
}

export type CanvasSplitItem = {
  type: Exclude<CanvasNodeType, 'CAREER'>
  title: string
  status: Exclude<CanvasNodeStatus, 'MASTERED'>
  detail: Record<string, unknown>
  sourceRefs: string[]
  locked: boolean
}

export type CareerPlanningSession = {
  id: string
  status: string
  phase: string
  entryMode: 'AI_DISCOVERY' | 'KNOWN_TARGET'
  aiConsent: boolean
  version: number
  profile: CareerPlanningProfile
  permissions: EvidencePermission[]
  interviewRounds: InterviewRound[]
  messages: PlanningMessage[]
  recommendationSet?: RecommendationSet | null
  activeGoal?: CareerGoal | null
  canvas?: CareerCanvas | null
  createdAt: string
  updatedAt: string
}

export type CareerPlanningOverview = {
  enabled: boolean
  session?: CareerPlanningSession | null
  quota: QuotaView
}

export type CareerCanvasStats = {
  canvasCount: number
  primaryCount: number
  abilityNodeCount: number
  pendingValidationCount: number
  versionCount: number
}

export type CareerCanvasSummary = {
  sessionId: string
  goalId: string
  title: string
  status: 'ACTIVE' | 'PAUSED' | string
  primary: boolean
  overallProgress: number
  nodeCount: number
  domainCount: number
  pendingValidationCount: number
  canvasVersion: number
  planStatus: string
  currentWeek?: number | null
  durationWeeks?: number | null
  planRevision: number
  currentFocus?: string | null
  recentChanges: string[]
  createdAt: string
  updatedAt: string
}

export type CareerCanvasDashboard = {
  stats: CareerCanvasStats
  items: CareerCanvasSummary[]
}

export type ConfirmationToken = {
  setId: string
  recommendationId: string
  token: string
  expiresAt: string
}

export type CareerProposalDecision = 'PENDING' | 'ACCEPTED' | 'REJECTED'

export type CareerProposalItem = {
  id: string
  sequence: number
  proposalKey: string
  operation: 'ADD' | 'UPDATE' | 'DELETE' | 'MOVE' | 'ADD_RELATION'
  targetNodeId?: string | null
  parentNodeId?: string | null
  before: Record<string, unknown>
  after: Record<string, unknown>
  reason: string
  sourceRefs: string[]
  impactNodeIds: string[]
  decision: CareerProposalDecision
  rejectionReason?: string | null
  decidedAt?: string | null
}

export type CareerCanvasProposal = {
  id: string
  sessionId: string
  goalId: string
  baseVersionId: string
  baseVersion: number
  status: 'DRAFT' | 'STALE' | 'PARTIALLY_ACCEPTED' | 'ACCEPTED' | 'REJECTED' | 'SUPERSEDED'
  proposalType: 'GLOBAL_OPTIMIZATION' | 'NODE_INFERENCE' | string
  targetNodeId?: string | null
  direction?: CareerInferenceDirection | null
  depth?: CareerInferenceDepth | null
  taskId?: string | null
  instruction: string
  promptVersion: string
  schemaVersion: string
  model?: string | null
  items: CareerProposalItem[]
  createdAt: string
  decidedAt?: string | null
}

export type CareerCanvasGenerationScale = 'COMPACT' | 'STANDARD' | 'DEEP'
export type CareerInferenceDirection = 'DOWNWARD' | 'PREREQUISITES' | 'SIBLINGS' | 'TARGET_GAP'
export type CareerInferenceDepth = 'ONE_LEVEL' | 'FULL_BRANCH'

export type CareerProposalApplyResult = {
  proposal: CareerCanvasProposal
  canvas: CareerCanvas
}

export type CareerPlanTaskStatus = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE' | 'SKIPPED'

export type CareerPlanTask = {
  id: string
  nodeId?: string | null
  taskType: 'LEARNING' | 'CHECKPOINT' | string
  week: number
  title: string
  description?: string | null
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string
  estimatedMinutes: number
  dueDate: string
  status: CareerPlanTaskStatus
  evidenceRequired: boolean
  sortOrder: number
  version: number
  createdAt: string
  updatedAt: string
  completedAt?: string | null
}

export type CareerLearningEvidence = {
  id: string
  planId: string
  taskId?: string | null
  nodeId: string
  sourceType: 'CAREER_FILE' | 'CAREER_RECORD' | 'USER_NOTE' | string
  sourceId?: string | null
  title: string
  note?: string | null
  verificationStatus: string
  createdAt: string
  confirmedAt?: string | null
}

export type CareerWeeklyReview = {
  id: string
  week: number
  completedSummary?: string | null
  blockers?: string | null
  adjustment?: string | null
  nextWeekFocus?: string | null
  createdAt: string
  updatedAt: string
}

export type CareerLearningPlan = {
  id: string
  sessionId: string
  goalId: string
  canvasVersionId: string
  canvasVersion: number
  durationWeeks: number
  intensity: 'LIGHT' | 'STANDARD' | 'FOCUSED'
  weeklyHours: number
  learningDays: number[]
  startDate: string
  targetDate: string
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED'
  version: number
  generationMethod: string
  promptVersion?: string | null
  schemaVersion?: string | null
  model?: string | null
  tasks: CareerPlanTask[]
  evidences: CareerLearningEvidence[]
  reviews: CareerWeeklyReview[]
  createdAt: string
  updatedAt: string
  completedAt?: string | null
  archivedAt?: string | null
}

export type CareerAbilityValidation = {
  id: string
  batchId?: string | null
  sessionId: string
  goalId: string
  planId?: string | null
  nodeId: string
  canvasVersionId: string
  method: 'CODE_REVIEW' | 'PROJECT_CHECK' | 'QUIZ' | 'SCENARIO' | 'MOCK_INTERVIEW' | 'EVIDENCE_REVIEW' | string
  status: 'EVALUATED' | 'CONFIRMED' | 'REJECTED' | string
  submission: Record<string, unknown>
  score: Record<string, unknown>
  result?: 'PASSED' | 'NEEDS_WORK' | 'INSUFFICIENT' | string | null
  feedback: Record<string, unknown>
  userConfirmed: boolean
  evidenceIds: string[]
  promptVersion?: string | null
  schemaVersion?: string | null
  model?: string | null
  createdAt: string
  evaluatedAt?: string | null
  confirmedAt?: string | null
}

export type CareerValidationBatchItem = {
  nodeId: string
  evidenceIds: string[]
  submission: Record<string, unknown>
}

export type CareerValidationBatchCommand = {
  requestId: string
  expectedCanvasVersion: number
  method: string
  items: CareerValidationBatchItem[]
}

export type CareerExecutionOverview = {
  pendingProposal?: CareerCanvasProposal | null
  activePlan?: CareerLearningPlan | null
  validations: CareerAbilityValidation[]
}
