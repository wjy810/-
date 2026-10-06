import { api } from '@/shared/api/client'
import type { TaskView } from '@/shared/api/task'
import type {
  CanvasNodeCreate,
  CanvasSplitItem,
  CanvasNodeUpdate,
  CanvasRelationType,
  CanvasVersionDiff,
  CanvasVersionPage,
  CareerAbilityValidation,
  CareerValidationBatchCommand,
  CareerCanvasProposal,
  CareerExecutionOverview,
  CareerLearningPlan,
  CareerProposalApplyResult,
  CareerCanvas,
  CareerCanvasDashboard,
  CareerPlanningOverview,
  CareerPlanningSession,
  CareerCanvasGenerationScale,
  CareerInferenceDepth,
  CareerInferenceDirection,
  ConfirmationToken,
  EvidenceOption,
  RecommendationSet,
} from '../types'

const ROOT = '/api/v1/career-planning'
const id = (value: string) => encodeURIComponent(value)

export const fetchCareerPlanningOverview = () => api<CareerPlanningOverview>(ROOT)
export const fetchCareerCanvasDashboard = (query = '', status = 'ALL', sort = 'RECENT') => {
  const params = new URLSearchParams({ status, sort })
  if (query.trim()) params.set('q', query.trim())
  return api<CareerCanvasDashboard>(`${ROOT}/canvases?${params.toString()}`)
}
export const createCareerCanvas = (taxonomyNodeId: string, aiConsent = true) =>
  api<CareerPlanningSession>(`${ROOT}/canvases`, {
    method: 'POST', body: JSON.stringify({ taxonomyNodeId, aiConsent }),
  })
export const makeCareerCanvasPrimary = (sessionId: string) =>
  api<CareerCanvasDashboard>(`${ROOT}/canvases/${id(sessionId)}/primary`, { method: 'PUT' })
export const fetchCareerPlanningSession = (sessionId: string) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}`)
export const startCareerPlanning = (entryMode: 'AI_DISCOVERY' | 'KNOWN_TARGET', aiConsent: boolean, objectiveTaxonomyId?: string) =>
  api<CareerPlanningSession>(`${ROOT}/sessions`, {
    method: 'POST', body: JSON.stringify({ entryMode, aiConsent, objectiveTaxonomyId }),
  })
export const updateCareerPlanningProfile = (sessionId: string, command: Record<string, unknown>) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/profile`, {
    method: 'PUT', body: JSON.stringify(command),
  })
export const fetchCareerPlanningEvidence = (sessionId: string) =>
  api<EvidenceOption[]>(`${ROOT}/sessions/${id(sessionId)}/evidence-options`)
export const authorizeCareerPlanningEvidence = (sessionId: string, selections: Array<{ sourceId: string; scopes: string[] }>) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/evidence`, {
    method: 'PUT', body: JSON.stringify({ selections }),
  })
export const startCareerPlanningInterview = (sessionId: string) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/interviews`, {
    method: 'POST', body: JSON.stringify({ requestId: crypto.randomUUID() }),
  })
export const startCareerPlanningInterviewTask = (sessionId: string, requestId = crypto.randomUUID()) =>
  api<TaskView>(`${ROOT}/sessions/${id(sessionId)}/interviews/tasks`, {
    method: 'POST', headers: { 'Idempotency-Key': requestId }, body: JSON.stringify({ requestId }),
  })
export const answerCareerPlanningInterview = (sessionId: string, roundId: string, answers: Array<{ questionId: string; question: string; answer: string }>, expectedProfileVersion: number) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/interviews/${id(roundId)}/answers`, {
    method: 'POST', body: JSON.stringify({ answers, expectedProfileVersion }),
  })
export const saveCareerPlanningInterviewDraft = (sessionId: string, roundId: string, answers: Array<{ questionId: string; question: string; answer: string }>, expectedProfileVersion: number) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/interviews/${id(roundId)}/draft`, {
    method: 'PUT', body: JSON.stringify({ answers, expectedProfileVersion }),
  })
export const reviewCareerPlanningProfile = (sessionId: string, expectedVersion: number) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/profile/review`, {
    method: 'POST', body: JSON.stringify({ expectedVersion }),
  })
export const confirmCareerPlanningProfile = (sessionId: string, confirmedItemIds: string[], expectedVersion: number) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/profile/confirm`, {
    method: 'POST', body: JSON.stringify({ confirmedItemIds, expectedVersion }),
  })
export const generateCareerRecommendations = (sessionId: string) =>
  api<RecommendationSet>(`${ROOT}/sessions/${id(sessionId)}/recommendations`, {
    method: 'POST', body: JSON.stringify({ requestId: crypto.randomUUID() }),
  })
export const startCareerRecommendationTask = (sessionId: string, requestId = crypto.randomUUID()) =>
  api<TaskView>(`${ROOT}/sessions/${id(sessionId)}/recommendations/tasks`, {
    method: 'POST', headers: { 'Idempotency-Key': requestId }, body: JSON.stringify({ requestId }),
  })
export const setCareerRecommendationFavorite = (sessionId: string, recommendationId: string, favorite: boolean) =>
  api<RecommendationSet>(`${ROOT}/sessions/${id(sessionId)}/recommendations/${id(recommendationId)}/favorite`, {
    method: 'PUT', body: JSON.stringify({ favorite }),
  })
export const prepareCareerGoalConfirmation = (sessionId: string, setId: string, recommendationId: string) =>
  api<ConfirmationToken>(`${ROOT}/sessions/${id(sessionId)}/recommendation-sets/${id(setId)}/goal-confirmation`, {
    method: 'POST', body: JSON.stringify({ recommendationId }),
  })
export const confirmCareerGoal = (sessionId: string, setId: string, recommendationId: string, confirmationToken: string, expectedSessionVersion: number) =>
  api<CareerPlanningSession>(`${ROOT}/sessions/${id(sessionId)}/recommendation-sets/${id(setId)}/goal`, {
    method: 'POST', body: JSON.stringify({ recommendationId, confirmationToken, expectedSessionVersion }),
  })

export const generateCareerCanvas = (sessionId: string, expectedVersion: number) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/generate`, {
    method: 'POST', body: JSON.stringify({ requestId: crypto.randomUUID(), expectedVersion }),
  })

export const startCareerCanvasGeneration = (
  sessionId: string,
  expectedVersion: number,
  generationScale: CareerCanvasGenerationScale = 'STANDARD',
  requestId = crypto.randomUUID(),
) =>
  api<TaskView>(`${ROOT}/sessions/${id(sessionId)}/canvas/tasks`, {
    method: 'POST', headers: { 'Idempotency-Key': requestId },
    body: JSON.stringify({ requestId, expectedVersion, generationScale }),
  })

export const createCareerCanvasNode = (sessionId: string, command: CanvasNodeCreate) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/nodes`, {
    method: 'POST', body: JSON.stringify(command),
  })

export const updateCareerCanvasNode = (sessionId: string, nodeId: string, command: CanvasNodeUpdate) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/nodes/${id(nodeId)}`, {
    method: 'PATCH', body: JSON.stringify(command),
  })

export const updateCareerCanvasNodes = (
  sessionId: string,
  command: { nodeIds: string[]; status?: string; locked?: boolean; expectedVersion: number },
) => api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/nodes`, {
  method: 'PATCH', body: JSON.stringify(command),
})

export const deleteCareerCanvasNode = (sessionId: string, nodeId: string, cascade: boolean, expectedVersion: number) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/nodes/${id(nodeId)}`, {
    method: 'DELETE', body: JSON.stringify({ cascade, expectedVersion }),
  })

export const splitCareerCanvasNode = (sessionId: string, nodeId: string, items: CanvasSplitItem[], expectedVersion: number) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/nodes/${id(nodeId)}/split`, {
    method: 'POST', body: JSON.stringify({ items, expectedVersion }),
  })

export const mergeCareerCanvasNodes = (
  sessionId: string,
  nodeIds: string[],
  title: string,
  detail: Record<string, unknown>,
  expectedVersion: number,
) => api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/nodes/merge`, {
  method: 'POST', body: JSON.stringify({ nodeIds, title, detail, expectedVersion }),
})

export const addCareerCanvasRelation = (sessionId: string, fromNodeId: string, toNodeId: string, type: CanvasRelationType, expectedVersion: number) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/relations`, {
    method: 'POST', body: JSON.stringify({ fromNodeId, toNodeId, type, expectedVersion }),
  })

export const fetchCareerCanvasVersions = (sessionId: string, page = 0, size = 20) =>
  api<CanvasVersionPage>(`${ROOT}/sessions/${id(sessionId)}/canvas/versions?page=${page}&size=${size}`)

export const compareCareerCanvasVersions = (sessionId: string, fromVersion: number, toVersion: number) =>
  api<CanvasVersionDiff>(`${ROOT}/sessions/${id(sessionId)}/canvas/versions/compare?from=${fromVersion}&to=${toVersion}`)

export const fetchCareerCanvasVersion = (sessionId: string, version: number) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/versions/${version}`)

export const fetchCareerExecution = (sessionId: string) =>
  api<CareerExecutionOverview>(`${ROOT}/sessions/${id(sessionId)}/execution`)

export const generateCareerCanvasProposal = (sessionId: string, instruction: string, expectedVersion: number) =>
  api<CareerCanvasProposal>(`${ROOT}/sessions/${id(sessionId)}/canvas/proposals`, {
    method: 'POST', body: JSON.stringify({ requestId: crypto.randomUUID(), instruction, expectedVersion }),
  })

export const fetchCareerCanvasProposals = (sessionId: string) =>
  api<CareerCanvasProposal[]>(`${ROOT}/sessions/${id(sessionId)}/canvas/proposals`)

export const startCareerNodeInference = (
  sessionId: string,
  command: {
    targetNodeId: string
    direction: CareerInferenceDirection
    depth: CareerInferenceDepth
    instruction?: string
    expectedVersion: number
  },
  requestId = crypto.randomUUID(),
) => api<TaskView>(`${ROOT}/sessions/${id(sessionId)}/canvas/proposals/tasks`, {
  method: 'POST', headers: { 'Idempotency-Key': requestId },
  body: JSON.stringify({ ...command, requestId }),
})

export const fetchCareerCanvasProposal = (sessionId: string, proposalId: string) =>
  api<CareerCanvasProposal>(`${ROOT}/sessions/${id(sessionId)}/canvas/proposals/${id(proposalId)}`)

export const discardCareerCanvasProposal = (sessionId: string, proposalId: string) =>
  api<CareerCanvasProposal>(`${ROOT}/sessions/${id(sessionId)}/canvas/proposals/${id(proposalId)}/discard`, {
    method: 'POST',
  })

export const decideCareerCanvasProposal = (
  sessionId: string,
  proposalId: string,
  decisions: Array<{ itemId: string; decision: 'ACCEPTED' | 'REJECTED'; rejectionReason?: string }>,
  expectedVersion: number,
) => api<CareerProposalApplyResult>(`${ROOT}/sessions/${id(sessionId)}/canvas/proposals/${id(proposalId)}/decide`, {
  method: 'POST', body: JSON.stringify({ decisions, expectedVersion }),
})

export const createCareerLearningPlan = (
  sessionId: string,
  command: { durationWeeks: number; intensity: 'LIGHT' | 'STANDARD' | 'FOCUSED'; weeklyHours: number; learningDays: number[]; startDate: string; expectedCanvasVersion: number },
) => api<CareerLearningPlan>(`${ROOT}/sessions/${id(sessionId)}/plans`, {
  method: 'POST', body: JSON.stringify(command),
})

export const fetchActiveCareerLearningPlan = (sessionId: string) =>
  api<CareerLearningPlan | null>(`${ROOT}/sessions/${id(sessionId)}/plans/active`)

export const updateCareerLearningPlan = (sessionId: string, planId: string, status: string, expectedVersion: number) =>
  api<CareerLearningPlan>(`${ROOT}/sessions/${id(sessionId)}/plans/${id(planId)}`, {
    method: 'PATCH', body: JSON.stringify({ status, expectedVersion }),
  })

export const updateCareerPlanTask = (
  sessionId: string,
  planId: string,
  taskId: string,
  command: { status?: string; dueDate?: string; estimatedMinutes?: number; targetWeek?: number; sortOrder?: number; expectedVersion: number },
) => api<CareerLearningPlan>(`${ROOT}/sessions/${id(sessionId)}/plans/${id(planId)}/tasks/${id(taskId)}`, {
  method: 'PATCH', body: JSON.stringify(command),
})

export const addCareerLearningEvidence = (
  sessionId: string,
  planId: string,
  command: { taskId?: string; nodeId: string; sourceType: string; sourceId?: string; title: string; note?: string; expectedTaskVersion?: number },
) => api<CareerLearningPlan>(`${ROOT}/sessions/${id(sessionId)}/plans/${id(planId)}/evidences`, {
  method: 'POST', body: JSON.stringify(command),
})

export const saveCareerWeeklyReview = (
  sessionId: string,
  planId: string,
  week: number,
  command: { completedSummary: string; blockers: string; adjustment: string; nextWeekFocus: string },
) => api<CareerLearningPlan>(`${ROOT}/sessions/${id(sessionId)}/plans/${id(planId)}/reviews/${week}`, {
  method: 'PUT', body: JSON.stringify(command),
})

export const startCareerAbilityValidation = (
  sessionId: string,
  command: { requestId?: string; nodeId: string; method: string; evidenceIds: string[]; submission: Record<string, unknown>; expectedCanvasVersion: number },
) => api<CareerAbilityValidation>(`${ROOT}/sessions/${id(sessionId)}/validations`, {
  method: 'POST', body: JSON.stringify(command),
})

export const startCareerValidationBatch = (sessionId: string, command: CareerValidationBatchCommand) =>
  api<TaskView>(`${ROOT}/sessions/${id(sessionId)}/validation-batches`, {
    method: 'POST', headers: { 'Idempotency-Key': command.requestId }, body: JSON.stringify(command),
  })

export const fetchCareerAbilityValidations = (sessionId: string, batchId?: string) => {
  const query = batchId ? `?batchId=${id(batchId)}` : ''
  return api<CareerAbilityValidation[]>(`${ROOT}/sessions/${id(sessionId)}/validations${query}`)
}

export const confirmCareerAbilityValidation = (
  sessionId: string,
  validationId: string,
  accepted: boolean,
  expectedCanvasVersion: number,
) => api<CareerAbilityValidation>(`${ROOT}/sessions/${id(sessionId)}/validations/${id(validationId)}/confirm`, {
  method: 'POST', body: JSON.stringify({ accepted, expectedCanvasVersion }),
})

export const restoreCareerCanvasVersion = (sessionId: string, version: number, expectedVersion: number) =>
  api<CareerCanvas>(`${ROOT}/sessions/${id(sessionId)}/canvas/versions/${version}/restore`, {
    method: 'POST', body: JSON.stringify({ expectedVersion }),
  })
