import { api, apiDownload } from '@/shared/api/client'
import type { TaskView } from '@/shared/api/task'
import { withQuery } from '@/shared/lib/query'
import type {
  PageResult,
  CurrentResumeLayout,
  ResumeCandidateView,
  ResumeCompareView,
  ResumeCreateCommand,
  ResumeMasterSummary,
  ResumeMasterView,
  ResumeLayoutView,
  ResumeAiAvailability,
  ResumeAiCandidateCommand,
  ResumeTemplateCatalogFilters,
  ResumeTemplateDetail,
  ResumeTemplatePreview,
  ResumeTemplateSummary,
  TemplateCatalogFacetGroup,
  TemplateCatalogItem,
  TemplateCatalogQuery,
  ResumeUpdateCommand,
  ResumeVersionView,
} from '../types'

export function listTemplateCatalog(
  filters: TemplateCatalogQuery = {},
): Promise<PageResult<TemplateCatalogItem>> {
  return api<PageResult<TemplateCatalogItem>>(
    withQuery('/api/v1/template-catalog', { ...filters }),
  )
}

export function listTemplateCatalogFacets(
  assetKind = 'RESUME',
): Promise<TemplateCatalogFacetGroup[]> {
  return api<TemplateCatalogFacetGroup[]>(
    withQuery('/api/v1/template-catalog/facets', { assetKind }),
  )
}

export function fetchTemplateCatalogItem(catalogId: string): Promise<TemplateCatalogItem> {
  return api<TemplateCatalogItem>(
    `/api/v1/template-catalog/${encodeURIComponent(catalogId)}`,
  )
}

export async function downloadTemplateCatalogDocx(
  catalogId: string,
): Promise<{ blob: Blob; filename: string | null }> {
  const file = await apiDownload(
    `/api/v1/template-catalog/${encodeURIComponent(catalogId)}/download`,
  )
  const magic = new Uint8Array(await file.blob.slice(0, 4).arrayBuffer())
  if (magic[0] !== 0x50 || magic[1] !== 0x4b) {
    throw new Error('下载结果不是有效的 DOCX 文件，未保存。')
  }
  return file
}

export function listResumeTemplates(
  filters: ResumeTemplateCatalogFilters = {},
): Promise<PageResult<ResumeTemplateSummary>> {
  return api<PageResult<ResumeTemplateSummary>>(
    withQuery('/api/v1/resume-templates', { ...filters }),
  )
}

export function fetchResumeTemplate(templateId: string): Promise<ResumeTemplateDetail> {
  return api<ResumeTemplateDetail>(`/api/v1/resume-templates/${encodeURIComponent(templateId)}`)
}

export function previewResumeTemplate(
  templateId: string,
  masterId: string,
  variantCode: string,
): Promise<ResumeTemplatePreview> {
  return api<ResumeTemplatePreview>(
    `/api/v1/resume-templates/${encodeURIComponent(templateId)}/preview`,
    {
      method: 'POST',
      body: JSON.stringify({ masterId, variantCode }),
    },
  )
}

export function applyResumeTemplate(
  templateId: string,
  masterId: string,
  variantCode: string,
  expectedVersion?: number,
): Promise<ResumeLayoutView> {
  return api<ResumeLayoutView>(`/api/v1/resume-templates/${encodeURIComponent(templateId)}/apply`, {
    method: 'POST',
    body: JSON.stringify({ masterId, variantCode, expectedVersion }),
  })
}

export function fetchCurrentResumeLayout(masterId: string): Promise<CurrentResumeLayout> {
  return api<CurrentResumeLayout>(
    withQuery('/api/v1/resume-templates/layouts/current', { masterId }),
  )
}

function versioned(expectedVersion?: number): string {
  return JSON.stringify({ expectedVersion })
}

export function createResume(command: ResumeCreateCommand): Promise<ResumeMasterView> {
  return api<ResumeMasterView>('/api/v1/resumes', {
    method: 'POST',
    body: JSON.stringify(command),
  })
}

export function listResumes(): Promise<ResumeMasterSummary[]> {
  return api<ResumeMasterSummary[]>('/api/v1/resumes')
}

export function fetchResume(id: string): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${id}`)
}

export function updateResume(id: string, command: ResumeUpdateCommand): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(command),
  })
}

export function copyResume(id: string): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${id}/copy`, { method: 'POST' })
}

export function archiveResume(id: string, expectedVersion?: number): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${id}/archive`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function restoreResume(id: string, expectedVersion?: number): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${id}/restore`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function markResumeReady(id: string, expectedVersion?: number): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${id}/ready`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function freezeResume(id: string, expectedVersion?: number): Promise<ResumeVersionView> {
  return api<ResumeVersionView>(`/api/v1/resumes/${id}/freeze`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function createResumeCandidate(
  masterId: string,
  fieldKey: string,
  proposedValue: unknown,
): Promise<ResumeCandidateView> {
  return api<ResumeCandidateView>(`/api/v1/resumes/${masterId}/candidates`, {
    method: 'POST',
    body: JSON.stringify({ fieldKey, proposedValue }),
  })
}

export function fetchResumeAiAvailability(model?: string): Promise<ResumeAiAvailability> {
  return api<ResumeAiAvailability>(withQuery('/api/v1/resumes/ai/availability', { model }))
}

export function generateResumeAiCandidate(
  masterId: string,
  command: ResumeAiCandidateCommand,
): Promise<ResumeCandidateView> {
  return api<ResumeCandidateView>(`/api/v1/resumes/${masterId}/ai-candidates`, {
    method: 'POST',
    body: JSON.stringify(command),
  })
}

export function listResumeCandidates(
  masterId: string,
  status: string,
  page = 0,
  size = 20,
): Promise<PageResult<ResumeCandidateView>> {
  return api<PageResult<ResumeCandidateView>>(
    withQuery(`/api/v1/resumes/${masterId}/candidates`, { status, page, size }),
  )
}

export function confirmResumeCandidate(
  masterId: string,
  candidateId: string,
  expectedVersion?: number,
): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${masterId}/candidates/${candidateId}/confirm`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function rejectResumeCandidate(
  masterId: string,
  candidateId: string,
  expectedVersion?: number,
): Promise<ResumeCandidateView> {
  return api<ResumeCandidateView>(`/api/v1/resumes/${masterId}/candidates/${candidateId}/reject`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function correctResumeCandidate(
  masterId: string,
  candidateId: string,
  value: unknown,
  expectedVersion?: number,
): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${masterId}/candidates/${candidateId}/correct`, {
    method: 'POST',
    body: JSON.stringify({ value, expectedVersion }),
  })
}

export function linkOutcomeEvidence(
  masterId: string,
  outcomeId: string,
  evidenceId: string,
  expectedVersion?: number,
): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${masterId}/outcomes/${outcomeId}/evidence`, {
    method: 'POST',
    body: JSON.stringify({ evidenceId, expectedVersion }),
  })
}

export function waiveOutcome(
  masterId: string,
  outcomeId: string,
  confirmed: boolean,
  expectedVersion?: number,
): Promise<ResumeMasterView> {
  return api<ResumeMasterView>(`/api/v1/resumes/${masterId}/outcomes/${outcomeId}/waive`, {
    method: 'POST',
    body: JSON.stringify({ confirmed, expectedVersion }),
  })
}

export function fetchResumeVersion(versionId: string): Promise<ResumeVersionView> {
  return api<ResumeVersionView>(`/api/v1/resumes/versions/${versionId}`)
}

export function confirmResumeVersion(versionId: string, expectedVersion?: number): Promise<ResumeVersionView> {
  return api<ResumeVersionView>(`/api/v1/resumes/versions/${versionId}/confirm`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function archiveResumeVersion(versionId: string, expectedVersion?: number): Promise<ResumeVersionView> {
  return api<ResumeVersionView>(`/api/v1/resumes/versions/${versionId}/archive`, {
    method: 'POST',
    body: versioned(expectedVersion),
  })
}

export function compareResumeVersions(versionId: string, otherId: string): Promise<ResumeCompareView> {
  return api<ResumeCompareView>(withQuery(`/api/v1/resumes/versions/${versionId}/compare`, { with: otherId }))
}

export function exportResumePdf(versionId: string): Promise<TaskView> {
  return api<TaskView>(`/api/v1/resumes/versions/${versionId}/export-pdf`, { method: 'POST' })
}

export function exportResumeDocx(versionId: string): Promise<TaskView> {
  return api<TaskView>(`/api/v1/resumes/versions/${versionId}/export-docx`, { method: 'POST' })
}

const SESSION_FILE_DOWNLOAD = /^\/api\/v1\/files\/[^/]+\/download$/

export function isSessionFileDownloadPath(path?: string | null): boolean {
  return SESSION_FILE_DOWNLOAD.test((path ?? '').trim())
}

export async function downloadPrivateFile(fileId: string): Promise<{ blob: Blob; filename: string | null }> {
  return downloadResumePdfBytes(`/api/v1/files/${encodeURIComponent(fileId)}/download`)
}

export async function downloadPrivateDocxFile(fileId: string): Promise<{ blob: Blob; filename: string | null }> {
  return downloadResumeDocxBytes(`/api/v1/files/${encodeURIComponent(fileId)}/download`)
}

export async function downloadByPath(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const trimmed = path.trim()
  if (!isSessionFileDownloadPath(trimmed)) {
    throw new Error('下载地址不是会话私有文件接口 GET /api/v1/files/{id}/download，未发起请求。')
  }
  return downloadResumePdfBytes(trimmed)
}

export async function downloadDocxByPath(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const trimmed = path.trim()
  if (!isSessionFileDownloadPath(trimmed)) {
    throw new Error('下载地址不是会话私有文件接口 GET /api/v1/files/{id}/download，未发起请求。')
  }
  return downloadResumeDocxBytes(trimmed)
}

async function downloadResumePdfBytes(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const file = await apiDownload(path)
  await assertResumePdfBlob(file.blob)
  return file
}

async function assertResumePdfBlob(blob: Blob): Promise<void> {
  const head = new Uint8Array(await blob.slice(0, 16).arrayBuffer())
  const magic = Array.from(head, (byte) => String.fromCharCode(byte)).join('')
  if (magic.startsWith('%PDF-')) {
    return
  }
  const type = (blob.type || '').toLowerCase()
  if (type.includes('html') || /^\s*</.test(magic)) {
    throw new Error('下载结果是 HTML 页面，不是 PDF 字节。未保存伪造文件。')
  }
  if (type.includes('json') || /^\s*\{/.test(magic)) {
    throw new Error('下载结果是 JSON，不是 PDF 字节。未保存伪造文件。')
  }
  throw new Error('下载结果不是 PDF 字节。未保存伪造文件。')
}


async function downloadResumeDocxBytes(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const file = await apiDownload(path)
  await assertResumeDocxBlob(file.blob)
  return file
}

async function assertResumeDocxBlob(blob: Blob): Promise<void> {
  const head = new Uint8Array(await blob.slice(0, 16).arrayBuffer())
  if (head[0] === 0x50 && head[1] === 0x4b) {
    return
  }
  const magic = Array.from(head, (byte) => String.fromCharCode(byte)).join('')
  const type = (blob.type || '').toLowerCase()
  if (type.includes('html') || /^\s*</.test(magic)) {
    throw new Error('下载结果是 HTML 页面，不是 DOCX 字节。未保存伪造文件。')
  }
  if (type.includes('json') || /^\s*\{/.test(magic)) {
    throw new Error('下载结果是 JSON，不是 DOCX 字节。未保存伪造文件。')
  }
  throw new Error('下载结果不是 ZIP/OOXML DOCX 字节。未保存伪造文件。')
}
