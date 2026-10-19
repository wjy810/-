import { api, apiDownload } from '@/shared/api/client'
import { saveBlob } from '@/shared/lib/download'
import { collectPages, type Collected } from '@/shared/lib/pages'
import type {
  DraftInput,
  MalwareScannerStatus,
  PageResult,
  TemplateAsset,
  TemplateEvidence,
  TemplateFamily,
  TemplateVersion,
  TestInput,
  TemplateImportBatch,
  TemplateImportItem,
  CatalogAdminItem,
  CatalogFacet,
} from '../types'

const BASE = '/api/v1/admin/resume-templates'

export function listTemplateFamilies(): Promise<TemplateFamily[]> {
  return api<TemplateFamily[]>(`${BASE}/families`)
}

export function getMalwareScannerStatus(): Promise<MalwareScannerStatus> {
  return api<MalwareScannerStatus>(`${BASE}/malware-scanner/status`)
}

/** Every asset, page by page (the server serves at most 100 per page). */
export function listTemplateAssets(): Promise<Collected<TemplateAsset>> {
  return collectPages((page, size) => api<PageResult<TemplateAsset>>(`${BASE}/assets?page=${page}&size=${size}`), 1000)
}

export function listTemplateEvidence(): Promise<Collected<TemplateEvidence>> {
  return collectPages((page, size) => api<PageResult<TemplateEvidence>>(`${BASE}/evidence-artifacts?page=${page}&size=${size}`), 1000)
}

export function uploadTemplateEvidence(input: {
  file: File
  evidenceType: string
  description: string
}): Promise<TemplateEvidence> {
  const body = new FormData()
  body.set('file', input.file)
  body.set('evidenceType', input.evidenceType)
  body.set('description', input.description)
  return api<TemplateEvidence>(`${BASE}/evidence-artifacts`, { method: 'POST', body })
}

export async function downloadTemplateEvidence(id: string): Promise<void> {
  const result = await apiDownload(`${BASE}/evidence-artifacts/${id}/download`)
  saveBlob(result.blob, result.filename || 'template-evidence')
}

export function importTemplateAsset(input: {
  file: File
  sourceName: string
  sourceUri: string
  licenseStatus: string
  licenseEvidenceId?: string
}): Promise<TemplateAsset> {
  const body = new FormData()
  body.set('file', input.file)
  if (input.sourceName.trim()) body.set('sourceName', input.sourceName.trim())
  body.set('sourceUri', input.sourceUri.trim())
  body.set('licenseStatus', input.licenseStatus)
  if (input.licenseEvidenceId) body.set('licenseEvidenceId', input.licenseEvidenceId)
  return api<TemplateAsset>(`${BASE}/assets/import`, { method: 'POST', body })
}

export function reviewTemplateAsset(
  id: string,
  input: {
    decision: string
    licenseStatus?: string
    licenseEvidenceId?: string
    reason?: string
    expectedVersion: number
  },
): Promise<TemplateAsset> {
  return api<TemplateAsset>(`${BASE}/assets/${id}/review`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function rescanTemplateAsset(id: string, expectedVersion: number): Promise<TemplateAsset> {
  return api<TemplateAsset>(`${BASE}/assets/${id}/rescan`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  })
}

export function listTemplateVersions(templateId: string): Promise<TemplateVersion[]> {
  return api<TemplateVersion[]>(`${BASE}/${encodeURIComponent(templateId)}/versions`)
}

export function createTemplateDraft(templateId: string, input: DraftInput): Promise<TemplateVersion> {
  return api<TemplateVersion>(`${BASE}/${encodeURIComponent(templateId)}/versions`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateTemplateDraft(versionId: string, input: DraftInput): Promise<TemplateVersion> {
  return api<TemplateVersion>(`${BASE}/versions/${versionId}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function recordTemplateTest(versionId: string, input: TestInput): Promise<TemplateVersion> {
  return api<TemplateVersion>(`${BASE}/versions/${versionId}/test`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function publishTemplateVersion(versionId: string, expectedVersion: number): Promise<TemplateVersion> {
  return api<TemplateVersion>(`${BASE}/versions/${versionId}/publish`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  })
}

export function retireTemplateVersion(versionId: string, expectedVersion: number): Promise<TemplateVersion> {
  return api<TemplateVersion>(`${BASE}/versions/${versionId}/retire`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  })
}

export function listImportBatches(): Promise<Collected<TemplateImportBatch>> {
  return collectPages((page, size) => api<PageResult<TemplateImportBatch>>(`${BASE}/import-batches?page=${page}&size=${size}`), 500)
}

export function startImportBatch(): Promise<TemplateImportBatch> {
  return api<TemplateImportBatch>(`${BASE}/import-batches`, { method: 'POST' })
}

export function listImportItems(batchId: string): Promise<Collected<TemplateImportItem>> {
  return collectPages((page, size) =>
    api<PageResult<TemplateImportItem>>(`${BASE}/import-batches/${encodeURIComponent(batchId)}/items?page=${page}&size=${size}`), 2000)
}

export function pauseImportBatch(batchId: string): Promise<TemplateImportBatch> {
  return api<TemplateImportBatch>(`${BASE}/import-batches/${encodeURIComponent(batchId)}/pause`, { method: 'POST' })
}

export function retryImportBatch(batchId: string): Promise<TemplateImportBatch> {
  return api<TemplateImportBatch>(`${BASE}/import-batches/${encodeURIComponent(batchId)}/retry`, { method: 'POST' })
}

/** One page of the public catalog (the Word library has thousands of entries; the server caps pages at 100). */
export function listCatalogEntries(page = 0): Promise<PageResult<CatalogAdminItem>> {
  return api<PageResult<CatalogAdminItem>>(`${BASE}/catalog?page=${page}&size=100`)
}

export function updateCatalogEntry(id: string, input: {
  title: string
  summary?: string
  assetKind: string
  languageCode: string
  pageCount?: string
  photoPolicy: string
  thumbnailUri?: string
  facets: CatalogFacet[]
  expectedVersion: number
}): Promise<CatalogAdminItem> {
  return api<CatalogAdminItem>(`${BASE}/catalog/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function publishCatalogEntry(id: string, expectedVersion: number): Promise<CatalogAdminItem> {
  return api<CatalogAdminItem>(`${BASE}/catalog/${encodeURIComponent(id)}/publish`, { method: 'POST', body: JSON.stringify({ expectedVersion }) })
}

export function retireCatalogEntry(id: string, expectedVersion: number): Promise<CatalogAdminItem> {
  return api<CatalogAdminItem>(`${BASE}/catalog/${encodeURIComponent(id)}/retire`, { method: 'POST', body: JSON.stringify({ expectedVersion }) })
}
