import { api } from '@/shared/api/client'
import { withQuery } from '@/shared/lib/query'
import type {
  AdminOverview,
  ReleaseDetail,
  ReleaseDraft,
  UpdateFacets,
  UpdateAsset,
  UpdatePage,
  ValidationView,
} from '../types'

export type UpdateQuery = {
  q?: string
  type?: string
  module?: string
  versionFrom?: string
  versionTo?: string
  publishedFrom?: string
  publishedTo?: string
  status?: string
  page?: number
  size?: number
}

export function listUpdates(query: UpdateQuery = {}): Promise<UpdatePage> {
  return api<UpdatePage>(withQuery('/api/v1/updates', query))
}

export function fetchUpdateFacets(): Promise<UpdateFacets> {
  return api<UpdateFacets>('/api/v1/updates/facets')
}

export function fetchLatestUpdate(): Promise<ReleaseDetail | null> {
  return api<ReleaseDetail | null>('/api/v1/updates/latest')
}

export function fetchUpdate(version: string): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/updates/${encodeURIComponent(version)}`)
}

export function fetchWhatsNew(): Promise<ReleaseDetail | null> {
  return api<ReleaseDetail | null>('/api/v1/updates/whats-new')
}

export function markUpdateRead(id: string): Promise<void> {
  return api<void>(`/api/v1/updates/${id}/read`, { method: 'POST' })
}

export function acknowledgeUpdate(id: string): Promise<void> {
  return api<void>(`/api/v1/updates/${id}/acknowledge`, { method: 'POST' })
}

export function remindUpdateLater(id: string): Promise<void> {
  return api<void>(`/api/v1/updates/${id}/remind-later`, { method: 'POST' })
}

export function listAdminUpdates(query: UpdateQuery = {}): Promise<UpdatePage> {
  return api<UpdatePage>(withQuery('/api/v1/admin/changelog', query))
}

export function fetchAdminOverview(): Promise<AdminOverview> {
  return api<AdminOverview>('/api/v1/admin/changelog/overview')
}

export function fetchAdminUpdate(id: string): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}`)
}

export function createUpdate(draft: ReleaseDraft): Promise<ReleaseDetail> {
  return api<ReleaseDetail>('/api/v1/admin/changelog', { method: 'POST', body: JSON.stringify(draft) })
}

export function saveUpdate(id: string, draft: ReleaseDraft): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}`, { method: 'PUT', body: JSON.stringify(draft) })
}

export function validateUpdate(id: string): Promise<ValidationView> {
  return api<ValidationView>(`/api/v1/admin/changelog/${id}/validate`, { method: 'POST' })
}

export function publishUpdate(id: string, expectedVersion: number): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}/publish`, {
    method: 'POST', body: JSON.stringify({ expectedVersion }),
  })
}

export function scheduleUpdate(id: string, expectedVersion: number, scheduledAt: string): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}/schedule`, {
    method: 'POST', body: JSON.stringify({ expectedVersion, scheduledAt }),
  })
}

export function cancelUpdateSchedule(id: string, expectedVersion: number): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}/cancel-schedule`, {
    method: 'POST', body: JSON.stringify({ expectedVersion }),
  })
}

export function archiveUpdate(id: string, expectedVersion: number): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}/archive`, {
    method: 'POST', body: JSON.stringify({ expectedVersion }),
  })
}

export function reviseUpdate(id: string, draft: ReleaseDraft, reason: string): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}/revise`, {
    method: 'POST', body: JSON.stringify({ reason, release: draft }),
  })
}

export function copyUpdate(id: string, versionLabel: string): Promise<ReleaseDetail> {
  return api<ReleaseDetail>(`/api/v1/admin/changelog/${id}/copy`, {
    method: 'POST', body: JSON.stringify({ versionLabel }),
  })
}

export function uploadUpdateAsset(id: string, file: File): Promise<UpdateAsset> {
  const body = new FormData()
  body.append('file', file)
  return api<UpdateAsset>(`/api/v1/admin/changelog/${id}/assets`, { method: 'POST', body })
}
