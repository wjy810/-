import { api, apiDownload } from '@/shared/api/client'
import type { TaskView } from '@/shared/api/task'
import type {
  AnalysisStart, EvidenceCandidate, ImprovementTask, JobMatch, JobMatchDashboard,
  JobMatchHistoryPage, JobMatchReport, JobMatchReportComparison, JobMatchReportVersion, JobMatchSummary,
  MatchCapabilities, RedactionPreview, ResumeImportSession, ResumeOption, SimilarDirections,
} from '../types'

const ROOT = '/api/v1/job-matches'
const encoded = (value: string) => encodeURIComponent(value)
const mutationId = (action: string, id: string, version: number) => `job-match:${action}:${id}:${version}`

export const fetchMatchCapabilities = () => api<MatchCapabilities>(`${ROOT}/capabilities`)
export const fetchMatchDashboard = () => api<JobMatchDashboard>(`${ROOT}/dashboard`)
export const listJobMatches = (filters: { status?: string; query?: string; archived?: boolean } = {}) => {
  const query = new URLSearchParams()
  if (filters.status) query.set('status', filters.status)
  if (filters.query) query.set('query', filters.query)
  if (filters.archived) query.set('archived', 'true')
  return api<JobMatchSummary[]>(`${ROOT}${query.size ? `?${query}` : ''}`)
}
export const fetchJobMatchHistory = (filters: { status?: string; query?: string; archived?: boolean; page?: number; size?: number } = {}) => {
  const query = new URLSearchParams()
  if (filters.status) query.set('status', filters.status)
  if (filters.query) query.set('query', filters.query)
  if (filters.archived) query.set('archived', 'true')
  query.set('page', String(filters.page ?? 0))
  query.set('size', String(filters.size ?? 20))
  return api<JobMatchHistoryPage>(`${ROOT}/history?${query}`)
}
export const fetchJobMatch = (id: string) => api<JobMatch>(`${ROOT}/${encoded(id)}`)
export const createTextMatch = (text: string, requestId: string) => api<JobMatch>(ROOT, { method: 'POST', body: JSON.stringify({ sourceType: 'TEXT', text, requestId }) })
export const createUrlMatch = (url: string, requestId: string) => api<JobMatch>(`${ROOT}/jd/fetch-url`, { method: 'POST', body: JSON.stringify({ url, requestId }) })
export async function uploadJd(file: File, requestId: string) {
  const body = new FormData(); body.append('file', file); body.append('requestId', requestId)
  return api<JobMatch>(`${ROOT}/jd/upload`, { method: 'POST', body })
}
export const updateJdStructure = (id: string, command: Record<string, unknown>) => api<JobMatch>(`${ROOT}/${encoded(id)}/jd-structure`, { method: 'PATCH', body: JSON.stringify(command) })
export const fetchResumeOptions = (id: string) => api<ResumeOption[]>(`${ROOT}/${encoded(id)}/resume-options`)
export const selectMatchResume = (id: string, resumeRevisionId: string, expectedVersion: number, resumeImportId?: string) => api<JobMatch>(`${ROOT}/${encoded(id)}/resume-selection`, { method: 'PUT', body: JSON.stringify({ resumeRevisionId, resumeImportId, expectedVersion, requestId: mutationId('resume', id, expectedVersion) }) })

export async function uploadMatchResume(id: string, file: File) {
  const body = new FormData(); body.append('file', file)
  return api<{ upload: { file: { id: string }; task: TaskView }; resumeImportId?: string | null }>(`${ROOT}/${encoded(id)}/resume-imports`, { method: 'POST', body })
}
export const createResumeImport = (id: string, source: { careerFileId?: string; pastedText?: string }) => api<{ importSession: ResumeImportSession; task: TaskView }>(`${ROOT}/${encoded(id)}/resume-imports`, { method: 'POST', body: JSON.stringify(source) })
export const fetchResumeImport = (id: string) => api<ResumeImportSession>(`/api/v1/resume-imports/${encoded(id)}`)
export const updateResumeImport = (id: string, structuredDraft: Record<string, unknown>, expectedVersion: number) => api<ResumeImportSession>(`/api/v1/resume-imports/${encoded(id)}`, { method: 'PUT', body: JSON.stringify({ structuredDraft, expectedVersion }) })
export const confirmResumeImport = (id: string, title: string, expectedVersion: number) => api<ResumeImportSession>(`/api/v1/resume-imports/${encoded(id)}/confirm`, { method: 'POST', body: JSON.stringify({ title, expectedVersion }) })
export const fetchEvidenceRecommendations = (id: string) => api<EvidenceCandidate[]>(`${ROOT}/${encoded(id)}/evidence-recommendations`)
export const fetchRedactionPreview = (id: string) => api<RedactionPreview>(`${ROOT}/${encoded(id)}/redaction-preview`)
export const authorizeEvidence = (id: string, command: Record<string, unknown>) => api<JobMatch>(`${ROOT}/${encoded(id)}/authorization`, { method: 'POST', body: JSON.stringify({ ...command, requestId: command.requestId ?? mutationId('authorize', id, Number(command.expectedVersion ?? 0)) }) })
export const revokeEvidence = (id: string, expectedVersion: number) => api<JobMatch>(`${ROOT}/${encoded(id)}/authorization`, { method: 'DELETE', body: JSON.stringify({ requestId: mutationId('revoke-authorization', id, expectedVersion), expectedVersion }) })
export const startAnalysis = (id: string, requestId: string, expectedVersion: number) => api<AnalysisStart>(`${ROOT}/${encoded(id)}/analyze`, { method: 'POST', body: JSON.stringify({ requestId, expectedVersion, outputOptions: { redacted: true, language: 'zh-CN' } }) })
export const answerClarifications = (id: string, answers: Array<{ id: string; answerCode: string; note?: string }>, continueWithPending: boolean, expectedVersion: number) => api<JobMatch>(`${ROOT}/${encoded(id)}/clarifications`, { method: 'POST', body: JSON.stringify({ answers, continueWithPending, expectedVersion, requestId: mutationId('clarifications', id, expectedVersion) }) })
export const resumeAnalysis = (id: string, expectedVersion: number) => api<JobMatch>(`${ROOT}/${encoded(id)}/resume-analysis`, { method: 'POST', body: JSON.stringify({ requestId: mutationId('resume-analysis', id, expectedVersion), expectedVersion }) })
export const cancelAnalysis = (id: string, expectedVersion: number) => api<JobMatch>(`${ROOT}/${encoded(id)}/cancel`, { method: 'POST', body: JSON.stringify({ requestId: mutationId('cancel', id, expectedVersion), expectedVersion }) })
export const fetchMatchReport = (id: string) => api<JobMatchReport>(`${ROOT}/${encoded(id)}/report`)
export const fetchMatchReportVersions = (id: string) => api<JobMatchReportVersion[]>(`${ROOT}/${encoded(id)}/report/versions`)
export const compareMatchReportVersions = (id: string, fromVersion: number, toVersion: number) =>
  api<JobMatchReportComparison>(`${ROOT}/${encoded(id)}/report/compare?fromVersion=${fromVersion}&toVersion=${toVersion}`)
export const sendClaimFeedback = (id: string, claimId: string, feedback: string, expectedVersion: number) => api(`${ROOT}/${encoded(id)}/report/claims/${encoded(claimId)}/feedback`, { method: 'POST', body: JSON.stringify({ feedback, expectedVersion, requestId: mutationId(`claim-${claimId}`, id, expectedVersion) }) })
export const updateImprovement = (id: string, taskId: string, status: string, expectedVersion: number) => api<ImprovementTask>(`${ROOT}/${encoded(id)}/learning-tasks/${encoded(taskId)}`, { method: 'PATCH', body: JSON.stringify({ status, expectedVersion, requestId: mutationId(`learning-${taskId}`, id, expectedVersion) }) })
export const createResumeOptimization = (id: string, expectedVersion: number) => api<{ path: string }>(`${ROOT}/${encoded(id)}/actions/resume-optimization`, { method: 'POST', body: JSON.stringify({ expectedVersion, requestId: mutationId('resume-optimization', id, expectedVersion) }) })
export const fetchCareerDirections = (id: string) => api<SimilarDirections>(`${ROOT}/${encoded(id)}/similar-jobs`)
export const createReportExport = (id: string, format: string, expectedVersion: number) => api<{ id: string; taskId?: string; status: string }>(`${ROOT}/${encoded(id)}/exports`, { method: 'POST', body: JSON.stringify({ format, sections: ['ALL'], redacted: true, language: 'zh-CN', expectedVersion, requestId: mutationId(`export-${format}`, id, expectedVersion) }) })
export const fetchReportExport = (exportId: string) => api<{ id: string; status: string; taskId?: string; downloadUrl?: string; errorCode?: string }>(`${ROOT}/exports/${encoded(exportId)}`)
export const downloadReportExport = (exportId: string) => apiDownload(`${ROOT}/exports/${encoded(exportId)}/download`)
