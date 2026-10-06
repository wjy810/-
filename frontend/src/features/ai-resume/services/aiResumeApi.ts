import { api, apiSse, type SseEvent } from '@/shared/api/client'
import type { TaskView } from '@/shared/api/task'
import { withQuery } from '@/shared/lib/query'
import type {
  AiConsent,
  AiCareerEvidencePreference,
  AiCancellation,
  AiCertificateSuggestion,
  AiCredentialRecommendation,
  AiHonorSuggestion,
  AiDescriptionSuggestion,
  AiSummarySuggestion,
  AiSkillSuggestion,
  AiSkillSuggestionMode,
  AiIdentity,
  AiHistoryDeletion,
  AiResumeBranch,
  AiResumeBranchDiff,
  AiResumeConversation,
  AiResumeChangeSet,
  AiResumeMessage,
  AiResumePdfExportMode,
  AiQuota,
  AiResumeRevision,
  AiResumeDesignPreference,
  AiSmartTemplate,
  AiWritingPreference,
  AiWritingStyleCode,
  JobTaxonomyNode,
  ResumeImportSession,
  ResumeImportStart,
} from '../types'

const ROOT = '/api/v1/ai-resume'

export function createAiResume(identityType: AiIdentity, title?: string): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(`${ROOT}/conversations`, {
    method: 'POST',
    body: JSON.stringify({ identityType, title }),
  })
}

export function ensureAiResume(masterId: string): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(`${ROOT}/conversations/for-resume/${encodeURIComponent(masterId)}`, {
    method: 'POST',
  })
}

export function fetchAiResume(conversationId: string): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}`)
}

export function saveAiResumeCardDraft(
  conversationId: string,
  cardId: string,
  payload: Record<string, unknown>,
  expectedVersion: number,
): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}/draft`,
    { method: 'PUT', body: JSON.stringify({ payload, expectedVersion }) },
  )
}

export function submitAiResumeCard(
  conversationId: string,
  cardId: string,
  payload: Record<string, unknown>,
  expectedVersion: number,
): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}/submit`,
    { method: 'POST', body: JSON.stringify({ payload, expectedVersion }) },
  )
}

export function skipAiResumeCard(
  conversationId: string,
  cardId: string,
  expectedVersion: number,
): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}/skip`,
    { method: 'POST', body: JSON.stringify({ expectedVersion }) },
  )
}

export function generateAiResumeDescriptionSuggestion(
  conversationId: string,
  cardId: string,
  recordIndex: number,
  recordFacts: Record<string, unknown>,
  clientRequestId: string = crypto.randomUUID(),
  signal?: AbortSignal,
): Promise<AiDescriptionSuggestion> {
  return api<AiDescriptionSuggestion>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}`
      + `/records/${recordIndex}/description-suggestion`,
    { method: 'POST', body: JSON.stringify({ clientRequestId, recordFacts }), signal },
  )
}

export function generateAiResumeSummarySuggestions(
  conversationId: string,
  cardId: string,
  currentSummary: string,
  clientRequestId: string = crypto.randomUUID(),
  signal?: AbortSignal,
): Promise<AiSummarySuggestion> {
  return api<AiSummarySuggestion>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}`
      + '/summary-suggestions',
    { method: 'POST', body: JSON.stringify({ clientRequestId, currentSummary }), signal },
  )
}

export function generateAiResumeSkillSuggestion(
  conversationId: string,
  cardId: string,
  phase: 'NAMES' | 'DETAILS',
  mode: AiSkillSuggestionMode,
  currentSkills: Record<string, unknown>[],
  selectedNames: string[] = [],
  confirmedNames: string[] = [],
  clientRequestId: string = crypto.randomUUID(),
  signal?: AbortSignal,
): Promise<AiSkillSuggestion> {
  return api<AiSkillSuggestion>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}`
      + '/skill-suggestions',
    {
      method: 'POST',
      body: JSON.stringify({ clientRequestId, phase, mode, currentSkills, selectedNames, confirmedNames }),
      signal,
    },
  )
}

export function generateAiResumeCertificateSuggestion(
  conversationId: string,
  cardId: string,
  phase: 'NAMES' | 'DETAILS',
  currentCertificates: Record<string, unknown>[],
  selectedNames: string[] = [],
  confirmedNames: string[] = [],
  clientRequestId: string = crypto.randomUUID(),
  signal?: AbortSignal,
): Promise<AiCertificateSuggestion> {
  return api<AiCertificateSuggestion>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}`
      + '/certificate-suggestions',
    {
      method: 'POST',
      body: JSON.stringify({ clientRequestId, phase, currentCertificates, selectedNames, confirmedNames }),
      signal,
    },
  )
}

export function generateAiResumeHonorSuggestion(
  conversationId: string,
  cardId: string,
  phase: 'NAMES' | 'DETAILS',
  currentHonors: Record<string, unknown>[],
  selectedNames: string[] = [],
  confirmedNames: string[] = [],
  clientRequestId: string = crypto.randomUUID(),
  signal?: AbortSignal,
): Promise<AiHonorSuggestion> {
  return api<AiHonorSuggestion>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}`
      + '/honor-suggestions',
    {
      method: 'POST',
      body: JSON.stringify({ clientRequestId, phase, currentHonors, selectedNames, confirmedNames }),
      signal,
    },
  )
}

export function generateAiResumeCredentialRecommendations(
  conversationId: string,
  cardId: string,
  clientRequestId: string = crypto.randomUUID(),
  signal?: AbortSignal,
): Promise<AiCredentialRecommendation> {
  return api<AiCredentialRecommendation>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/cards/${encodeURIComponent(cardId)}`
      + '/credential-recommendations',
    {
      method: 'POST',
      body: JSON.stringify({ clientRequestId }),
      signal,
    },
  )
}

export function captureAiResumeConfirmedChange(conversationId: string): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/capture-confirmed-change`,
    { method: 'POST' },
  )
}

export function selectAiResumeTemplate(
  conversationId: string,
  templateId: string,
  expectedLayoutVersion?: number,
): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/template`, {
    method: 'POST',
    body: JSON.stringify({ templateId, expectedLayoutVersion }),
  })
}

export function listAiResumeSmartTemplates(conversationId: string): Promise<AiSmartTemplate[]> {
  return api<AiSmartTemplate[]>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/smart-templates`,
  )
}

export function saveAiResumeDesign(
  conversationId: string,
  templateId: string,
  variantCode: string,
  settings: AiResumeDesignPreference['settings'],
  expectedVersion: number,
): Promise<AiResumeDesignPreference> {
  return api<AiResumeDesignPreference>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/design/${encodeURIComponent(templateId)}`,
    { method: 'PUT', body: JSON.stringify({ variantCode, settings, expectedVersion }) },
  )
}

export type AiResumeExportPreview = {
  pageCount: number
  pageLimit: number
  overflowMm: number
  overflowSection?: string | null
  /** PNG data URL of the first page, rendered by the same service that produces the PDF. */
  firstPageImage: string
}

export function fetchAiResumeExportPreview(
  conversationId: string,
  exportMode: AiResumePdfExportMode = 'STANDARD',
  signal?: AbortSignal,
): Promise<AiResumeExportPreview> {
  return api<AiResumeExportPreview>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/export-preview`, {
    method: 'POST',
    body: JSON.stringify({ exportMode }),
    signal,
  })
}

export function exportAiResumePdf(
  conversationId: string,
  exportMode: AiResumePdfExportMode = 'STANDARD',
): Promise<TaskView> {
  return api<TaskView>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/export-pdf`, {
    method: 'POST',
    body: JSON.stringify({ exportMode }),
  })
}

export function addAiResumeMessage(
  conversationId: string,
  text: string,
  clientMessageId = crypto.randomUUID(),
): Promise<AiResumeMessage> {
  return api<AiResumeMessage>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/messages`, {
    method: 'POST',
    body: JSON.stringify({ clientMessageId, text }),
  })
}

export function askAiResume(
  conversationId: string,
  text: string,
  clientMessageId = crypto.randomUUID(),
): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/messages/respond`, {
    method: 'POST',
    body: JSON.stringify({ clientMessageId, text }),
  })
}

export function streamAiResume(
  conversationId: string,
  text: string,
  clientMessageId: string,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  return apiSse(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/messages/stream`, {
    method: 'POST',
    body: JSON.stringify({ clientMessageId, text }),
    signal,
  }, onEvent)
}

export function cancelAiResumeResponse(conversationId: string, requestId: string): Promise<AiCancellation> {
  return api<AiCancellation>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(requestId)}/cancel`,
    { method: 'POST' },
  )
}

export function decideAiResumeChange(
  conversationId: string,
  changeSetId: string,
  itemId: string,
  decision: 'APPLY' | 'REJECT',
  expectedVersion: number,
  editedValue?: string,
): Promise<AiResumeChangeSet> {
  return api<AiResumeChangeSet>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}`
      + `/change-sets/${encodeURIComponent(changeSetId)}/items/${encodeURIComponent(itemId)}/decision`,
    { method: 'POST', body: JSON.stringify({ decision, editedValue, expectedVersion }) },
  )
}

export function undoAiResumeChange(
  conversationId: string,
  changeSetId: string,
  itemId: string,
  expectedVersion: number,
): Promise<AiResumeChangeSet> {
  return api<AiResumeChangeSet>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}`
      + `/change-sets/${encodeURIComponent(changeSetId)}/items/${encodeURIComponent(itemId)}/undo`,
    { method: 'POST', body: JSON.stringify({ expectedVersion }) },
  )
}

export function grantAiResumeConsent(): Promise<AiConsent> {
  return api<AiConsent>(`${ROOT}/consent`, { method: 'POST' })
}

export function fetchAiResumeConsent(): Promise<AiConsent> {
  return api<AiConsent>(`${ROOT}/consent`)
}

export function revokeAiResumeConsent(): Promise<AiConsent> {
  return api<AiConsent>(`${ROOT}/consent/revoke`, { method: 'POST' })
}

export function fetchAiResumeWritingPreference(conversationId: string): Promise<AiWritingPreference> {
  return api<AiWritingPreference>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/preferences/writing-style`,
  )
}

export function setAiResumeWritingPreference(
  conversationId: string,
  styleCode: AiWritingStyleCode,
): Promise<AiWritingPreference> {
  return api<AiWritingPreference>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/preferences/writing-style`,
    { method: 'PUT', body: JSON.stringify({ styleCode }) },
  )
}

export function setCareerLibraryEvidencePreference(
  conversationId: string,
  enabled: boolean,
): Promise<AiCareerEvidencePreference> {
  return api<AiCareerEvidencePreference>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/preferences/career-library-evidence`,
    { method: 'PUT', body: JSON.stringify({ enabled }) },
  )
}

export function deleteAiResumeHistory(conversationId: string): Promise<AiHistoryDeletion> {
  return api<AiHistoryDeletion>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/history`, {
    method: 'DELETE',
  })
}

/** Starts parsing pasted resume text; poll `task`, then read the session for the structured draft. */
export function startResumeTextImport(pastedText: string): Promise<ResumeImportStart> {
  return api<ResumeImportStart>('/api/v1/resume-imports', {
    method: 'POST', body: JSON.stringify({ pastedText }),
  })
}

export function fetchResumeImport(importId: string): Promise<ResumeImportSession> {
  return api<ResumeImportSession>(`/api/v1/resume-imports/${encodeURIComponent(importId)}`)
}

/** Creates the resume (and its workbench conversation) from the parsed draft. */
export function confirmResumeImport(importId: string, title: string | undefined, expectedVersion: number): Promise<ResumeImportSession> {
  return api<ResumeImportSession>(`/api/v1/resume-imports/${encodeURIComponent(importId)}/confirm`, {
    method: 'POST', body: JSON.stringify({ title, expectedVersion }),
  })
}

export function uploadAiResumePhoto(conversationId: string, file: File): Promise<AiResumeConversation> {
  const body = new FormData()
  body.append('file', file)
  return api<AiResumeConversation>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/photo`, {
    method: 'POST', body,
  })
}

export function removeAiResumePhoto(conversationId: string): Promise<AiResumeConversation> {
  return api<AiResumeConversation>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/photo`, {
    method: 'DELETE',
  })
}

export function fetchAiResumeQuota(): Promise<AiQuota> {
  return api<AiQuota>(`${ROOT}/quota`)
}

export function listAiResumeRevisions(conversationId: string): Promise<AiResumeRevision[]> {
  return api<AiResumeRevision[]>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/revisions`)
}

export function restoreAiResumeRevision(conversationId: string, revisionId: string): Promise<AiResumeRevision> {
  return api<AiResumeRevision>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/revisions/${encodeURIComponent(revisionId)}/restore`,
    { method: 'POST' },
  )
}

export function listAiResumeBranches(conversationId: string): Promise<AiResumeBranch[]> {
  return api<AiResumeBranch[]>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/branches`)
}

export function createAiResumeLanguageBranch(
  conversationId: string,
  languageCode: 'zh-CN' | 'en-US',
  title?: string,
): Promise<AiResumeBranch> {
  return api<AiResumeBranch>(`${ROOT}/conversations/${encodeURIComponent(conversationId)}/branches/language`, {
    method: 'POST',
    body: JSON.stringify({ languageCode, title }),
  })
}

export function switchAiResumeBranch(conversationId: string, branchId: string): Promise<AiResumeBranch> {
  return api<AiResumeBranch>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/branches/${encodeURIComponent(branchId)}/switch`,
    { method: 'POST' },
  )
}

export function fetchAiResumeBranchDiff(conversationId: string, branchId: string): Promise<AiResumeBranchDiff> {
  return api<AiResumeBranchDiff>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/branches/${encodeURIComponent(branchId)}/diff`,
  )
}

export function syncAiResumeBranch(conversationId: string, branch: AiResumeBranch): Promise<AiResumeBranch> {
  return api<AiResumeBranch>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/branches/${encodeURIComponent(branch.id)}/sync`,
    { method: 'POST', body: JSON.stringify({ expectedVersion: branch.versionNo }) },
  )
}

export function translateAiResumeLanguageBranch(
  conversationId: string,
  branchId: string,
  requestId = crypto.randomUUID(),
): Promise<AiResumeBranch> {
  return api<AiResumeBranch>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/branches/${encodeURIComponent(branchId)}/translate`,
    { method: 'POST', body: JSON.stringify({ requestId }) },
  )
}

export function confirmAiResumeLanguageBranch(
  conversationId: string,
  branch: AiResumeBranch,
): Promise<AiResumeBranch> {
  return api<AiResumeBranch>(
    `${ROOT}/conversations/${encodeURIComponent(conversationId)}/branches/${encodeURIComponent(branch.id)}/translation/confirm`,
    { method: 'POST', body: JSON.stringify({ expectedVersion: branch.versionNo }) },
  )
}

export function listJobTaxonomy(parentId?: string, keyword?: string): Promise<JobTaxonomyNode[]> {
  return api<JobTaxonomyNode[]>(withQuery('/api/v1/job-taxonomy', { parentId, keyword }))
}
