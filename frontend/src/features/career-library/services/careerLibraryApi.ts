import { api, apiDownload } from '@/shared/api/client'
import type {
  CareerFile,
  CareerAvatarUpload,
  CareerFileFolder,
  CareerFileUpload,
  CareerHistorySummary,
  CareerProfile,
  CareerOverview,
  CareerSearchResult,
  CareerStorage,
  CareerRecord,
  CareerRecordWrite,
  CareerAiAvailability,
  CareerAiCandidate,
  PageResult,
} from '../types'

const ROOT = '/api/v1/career-library'

function query(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  const text = params.toString()
  return text ? `?${text}` : ''
}

export function fetchCareerProfile(): Promise<CareerProfile> {
  return api<CareerProfile>(`${ROOT}/profile`)
}

export function fetchCareerOverview(): Promise<CareerOverview> {
  return api<CareerOverview>(`${ROOT}/overview`)
}

export function searchCareerLibrary(q: string): Promise<CareerSearchResult[]> {
  return api<CareerSearchResult[]>(`${ROOT}/search${query({ q })}`)
}

export function saveCareerProfile(profile: CareerProfile): Promise<CareerProfile> {
  return api<CareerProfile>(`${ROOT}/profile`, {
    method: 'PUT',
    body: JSON.stringify({
      basics: profile.basics,
      intentions: profile.intentions,
      preferences: profile.preferences,
      summary: profile.summary,
      expectedVersion: profile.version,
    }),
  })
}

export function uploadCareerAvatar(file: File): Promise<CareerAvatarUpload> {
  const body = new FormData()
  body.append('file', file)
  return api<CareerAvatarUpload>(`${ROOT}/profile/avatar`, { method: 'POST', body })
}

export function commitCareerAvatar(fileId: string): Promise<CareerProfile> {
  return api<CareerProfile>(`${ROOT}/profile/avatar/${encodeURIComponent(fileId)}`, { method: 'PUT' })
}

export function deleteCareerAvatar(): Promise<CareerProfile> {
  return api<CareerProfile>(`${ROOT}/profile/avatar`, { method: 'DELETE' })
}

export function fetchCareerRecords(filters: { type?: string; status?: string; keyword?: string; page?: number; size?: number } = {}): Promise<PageResult<CareerRecord>> {
  return api<PageResult<CareerRecord>>(`${ROOT}/records${query({ ...filters, page: filters.page ?? 0, size: filters.size ?? 100 })}`)
}

export function createCareerRecord(write: CareerRecordWrite): Promise<CareerRecord> {
  return api<CareerRecord>(`${ROOT}/records`, { method: 'POST', body: JSON.stringify(write) })
}

export function updateCareerRecord(id: string, write: CareerRecordWrite): Promise<CareerRecord> {
  return api<CareerRecord>(`${ROOT}/records/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(write) })
}

export function archiveCareerRecord(record: CareerRecord): Promise<CareerRecord> {
  return api<CareerRecord>(`${ROOT}/records/${encodeURIComponent(record.id)}/archive`, {
    method: 'POST', body: JSON.stringify({ expectedVersion: record.version }),
  })
}

export function restoreCareerRecord(record: CareerRecord): Promise<CareerRecord> {
  return api<CareerRecord>(`${ROOT}/records/${encodeURIComponent(record.id)}/restore`, {
    method: 'POST', body: JSON.stringify({ expectedVersion: record.version }),
  })
}

export function copyCareerRecord(record: CareerRecord): Promise<CareerRecord> {
  return api<CareerRecord>(`${ROOT}/records/${encodeURIComponent(record.id)}/copy`, { method: 'POST' })
}

export function fetchCareerAiAvailability(): Promise<CareerAiAvailability> {
  return api<CareerAiAvailability>(`${ROOT}/records/ai-availability`)
}

export function fetchCareerAiCandidates(recordId: string, status = 'PENDING'): Promise<CareerAiCandidate[]> {
  return api<CareerAiCandidate[]>(`${ROOT}/records/${encodeURIComponent(recordId)}/ai-candidates${query({ status })}`)
}

export function generateCareerAiCandidate(recordId: string): Promise<CareerAiCandidate> {
  return api<CareerAiCandidate>(`${ROOT}/records/${encodeURIComponent(recordId)}/ai-candidates`, {
    method: 'POST', body: JSON.stringify({ clientRequestId: crypto.randomUUID() }),
  })
}

export function acceptCareerAiCandidate(candidate: CareerAiCandidate, record: CareerRecord): Promise<CareerAiCandidate> {
  return api<CareerAiCandidate>(`${ROOT}/records/ai-candidates/${encodeURIComponent(candidate.id)}/accept`, {
    method: 'POST',
    body: JSON.stringify({ expectedCandidateVersion: candidate.version, expectedRecordVersion: record.version }),
  })
}

export function rejectCareerAiCandidate(candidate: CareerAiCandidate): Promise<CareerAiCandidate> {
  return api<CareerAiCandidate>(`${ROOT}/records/ai-candidates/${encodeURIComponent(candidate.id)}/reject`, {
    method: 'POST', body: JSON.stringify({ expectedCandidateVersion: candidate.version }),
  })
}

export function fetchCareerFiles(filters: { category?: string; status?: string; keyword?: string; folderId?: string; processingStatus?: string; sort?: string; page?: number; size?: number } = {}): Promise<PageResult<CareerFile>> {
  return api<PageResult<CareerFile>>(`${ROOT}/files${query({ ...filters, page: filters.page ?? 0, size: filters.size ?? 100 })}`)
}

export function uploadCareerFile(file: File, category: string, displayName?: string, folderId?: string): Promise<CareerFileUpload> {
  const body = new FormData()
  body.append('file', file)
  body.append('category', category)
  if (displayName?.trim()) body.append('displayName', displayName.trim())
  if (folderId?.trim()) body.append('folderId', folderId.trim())
  return api<CareerFileUpload>(`${ROOT}/files`, { method: 'POST', body })
}

export function updateCareerFile(file: CareerFile, values: { displayName?: string; category?: string; folderId?: string | null }): Promise<CareerFile> {
  return api<CareerFile>(`${ROOT}/files/${encodeURIComponent(file.id)}`, {
    method: 'PUT', body: JSON.stringify({ ...values, expectedVersion: file.version }),
  })
}

export function retryCareerFile(file: CareerFile): Promise<CareerFileUpload> {
  return api<CareerFileUpload>(`${ROOT}/files/${encodeURIComponent(file.id)}/retry`, { method: 'POST' })
}

export function fetchCareerStorage(): Promise<CareerStorage> {
  return api<CareerStorage>(`${ROOT}/storage`)
}

export function fetchCareerFolders(status = 'ACTIVE'): Promise<CareerFileFolder[]> {
  return api<CareerFileFolder[]>(`${ROOT}/folders${query({ status })}`)
}

export function createCareerFolder(name: string): Promise<CareerFileFolder> {
  return api<CareerFileFolder>(`${ROOT}/folders`, { method: 'POST', body: JSON.stringify({ name }) })
}

export function updateCareerFolder(folder: CareerFileFolder, name: string): Promise<CareerFileFolder> {
  return api<CareerFileFolder>(`${ROOT}/folders/${encodeURIComponent(folder.id)}`, {
    method: 'PUT', body: JSON.stringify({ name, expectedVersion: folder.version }),
  })
}

export function archiveCareerFolder(folder: CareerFileFolder): Promise<CareerFileFolder> {
  return api<CareerFileFolder>(`${ROOT}/folders/${encodeURIComponent(folder.id)}/archive`, {
    method: 'POST', body: JSON.stringify({ expectedVersion: folder.version }),
  })
}

export function archiveCareerFile(file: CareerFile): Promise<CareerFile> {
  return api<CareerFile>(`${ROOT}/files/${encodeURIComponent(file.id)}/archive`, {
    method: 'POST', body: JSON.stringify({ expectedVersion: file.version }),
  })
}

export function restoreCareerFile(file: CareerFile): Promise<CareerFile> {
  return api<CareerFile>(`${ROOT}/files/${encodeURIComponent(file.id)}/restore`, {
    method: 'POST', body: JSON.stringify({ expectedVersion: file.version }),
  })
}

export function fetchCareerHistory(): Promise<CareerHistorySummary> {
  return api<CareerHistorySummary>(`${ROOT}/history`)
}

export async function downloadCareerFile(file: CareerFile): Promise<void> {
  await saveDownload(`${ROOT}/files/${encodeURIComponent(file.id)}/download`, file.originalFilename)
}

export async function downloadCareerHistory(format: 'json' | 'md'): Promise<void> {
  await saveDownload(`${ROOT}/history/export.${format}`, `career-history.${format}`)
}

async function saveDownload(path: string, fallbackName: string): Promise<void> {
  const result = await apiDownload(path)
  const url = URL.createObjectURL(result.blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = result.filename || fallbackName
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
