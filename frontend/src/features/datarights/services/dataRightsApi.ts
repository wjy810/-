import { api, apiDownload } from '@/shared/api/client'
import type {
  DeletionPreview,
  DeletionView,
  ExportView,
  ShareView,
  SignedDownloadUrl,
  SubmitDeletionBody,
} from '../types'

export function fetchShares(): Promise<ShareView[]> {
  return api<ShareView[]>('/api/v1/data-rights/shares')
}

export function revokeShare(id: string): Promise<null> {
  return api<null>(`/api/v1/data-rights/shares/${encodeURIComponent(id)}/revoke`, { method: 'POST' })
}

export function fetchDeletionPreview(query?: {
  scope?: string
  targetType?: string
  targetId?: string
}): Promise<DeletionPreview> {
  const params = new URLSearchParams()
  if (query?.scope) {
    params.set('scope', query.scope)
  }
  if (query?.targetType) {
    params.set('targetType', query.targetType)
  }
  if (query?.targetId) {
    params.set('targetId', query.targetId)
  }
  const suffix = params.toString()
  return api<DeletionPreview>(`/api/v1/data-rights/deletions/preview${suffix ? `?${suffix}` : ''}`)
}

export function submitDeletion(body: SubmitDeletionBody): Promise<DeletionView> {
  return api<DeletionView>('/api/v1/data-rights/deletions', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function fetchDeletion(id: string): Promise<DeletionView> {
  return api<DeletionView>(`/api/v1/data-rights/deletions/${encodeURIComponent(id)}`)
}

export function createExport(idempotencyKey?: string): Promise<ExportView> {
  const headers = new Headers()
  if (idempotencyKey) {
    headers.set('Idempotency-Key', idempotencyKey)
  }
  return api<ExportView>('/api/v1/data-rights/exports', {
    method: 'POST',
    headers,
  })
}

export function fetchExport(id: string): Promise<ExportView> {
  return api<ExportView>(`/api/v1/data-rights/exports/${encodeURIComponent(id)}`)
}

export function cancelExport(id: string): Promise<ExportView> {
  return api<ExportView>(`/api/v1/data-rights/exports/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
  })
}

export function retryExport(id: string): Promise<ExportView> {
  return api<ExportView>(`/api/v1/data-rights/exports/${encodeURIComponent(id)}/retry`, {
    method: 'POST',
  })
}

export function fetchFileDownloadUrl(fileId: string): Promise<SignedDownloadUrl> {
  return api<SignedDownloadUrl>(`/api/v1/files/${encodeURIComponent(fileId)}/download-url`)
}

/** Same-origin session download. Reject protocol-relative and `javascript:` 等带 scheme 的串. */
export function isSessionApiDownloadPath(path?: string | null): boolean {
  const trimmed = path?.trim() ?? ''
  if (!trimmed.startsWith('/api/') || trimmed.startsWith('//')) {
    return false
  }
  if (trimmed.includes('\\') || trimmed.includes('://') || /[\u0000-\u001F\s]/.test(trimmed)) {
    return false
  }
  return true
}

/** Allow only http(s) absolute URLs. Blocks `javascript:`, `data:`, `local:`. */
export function isSafeHttpDownloadUrl(raw?: string | null): boolean {
  const trimmed = raw?.trim() ?? ''
  if (!trimmed || trimmed.includes('\\') || /[\u0000-\u001F\s]/.test(trimmed)) {
    return false
  }
  try {
    const url = new URL(trimmed)
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      return false
    }
    if (url.username || url.password) {
      return false
    }
    return Boolean(url.hostname)
  } catch {
    return false
  }
}

export async function downloadExportBytes(exportId: string): Promise<{ blob: Blob; filename: string | null }> {
  return downloadExportJson(`/api/v1/data-rights/exports/${encodeURIComponent(exportId)}/download`)
}

export async function downloadExportByPath(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const trimmed = path.trim()
  if (!isSessionApiDownloadPath(trimmed)) {
    throw new Error('下载链接无效，已停止下载。请重新导出。')
  }
  return downloadExportJson(trimmed)
}

async function downloadExportJson(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const file = await apiDownload(path)
  await assertExportJsonBlob(file.blob)
  return {
    blob: file.blob,
    filename: file.filename || 'jobproof-export.json',
  }
}

async function assertExportJsonBlob(blob: Blob): Promise<void> {
  const head = new Uint8Array(await blob.slice(0, 16).arrayBuffer())
  const magic = Array.from(head, (byte) => String.fromCharCode(byte)).join('')
  const type = (blob.type || '').toLowerCase()
  if (type.includes('html') || /^\s*</.test(magic)) {
    throw new Error('未取得有效的导出文件，请重新登录后再试。')
  }
  if (magic.startsWith('%PDF-')) {
    throw new Error('导出文件格式不正确，已停止保存。请重新导出。')
  }
  if (/^\s*[{\[]/.test(magic)) {
    return
  }
  throw new Error('导出文件格式不正确，已停止保存。请重新导出。')
}
