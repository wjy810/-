import { api } from '@/shared/api/client'
import type {
  ApplicationDetail, ApplicationOutcome, ApplicationOverview, ApplicationStage, ApplicationWrite, JobApplication,
  LinkViewRecord, ResumeLink, SharedResume, ShareableVersion, ViewStart,
} from '../types'

const ROOT = '/api/v1/job-applications'
const LINKS = '/api/v1/resume-links'
const PUBLIC = '/api/v1/public/resume-links'
const encoded = (value: string) => encodeURIComponent(value)
const json = (body: unknown) => ({ body: JSON.stringify(body) })

export const fetchApplications = () => api<ApplicationOverview>(ROOT)
export const fetchApplication = (id: string) => api<ApplicationDetail>(`${ROOT}/${encoded(id)}`)
export const fetchShareableVersions = () => api<ShareableVersion[]>(`${ROOT}/resume-versions`)
export const createApplication = (write: ApplicationWrite) => api<JobApplication>(ROOT, { method: 'POST', ...json(write) })
export const updateApplication = (id: string, write: ApplicationWrite) =>
  api<JobApplication>(`${ROOT}/${encoded(id)}`, { method: 'PUT', ...json(write) })
export const moveApplication = (id: string, stage: ApplicationStage, expectedVersion: number, outcome?: ApplicationOutcome | null) =>
  api<JobApplication>(`${ROOT}/${encoded(id)}/stage`, { method: 'POST', ...json({ stage, outcome: outcome ?? null, expectedVersion }) })
/** Permanent; the page only offers it for archived applications. */
export const deleteApplication = (id: string) => api<{ deleted: boolean }>(`${ROOT}/${encoded(id)}`, { method: 'DELETE' })
export const archiveApplication = (id: string) => api<JobApplication>(`${ROOT}/${encoded(id)}/archive`, { method: 'POST' })
export const restoreApplication = (id: string) => api<JobApplication>(`${ROOT}/${encoded(id)}/restore`, { method: 'POST' })
export const fetchArchivedApplications = () => api<JobApplication[]>(`${ROOT}/archived`)
export const completeNextStep = (id: string, expectedVersion: number, note?: string) =>
  api<JobApplication>(`${ROOT}/${encoded(id)}/next-step/done`, { method: 'POST', ...json({ note: note || null, expectedVersion }) })

export const fetchResumeLinks = () => api<ResumeLink[]>(LINKS)
export const createResumeLink = (command: { resumeVersionId: string; applicationId?: string | null; label?: string | null; hideContact?: boolean; expiresInDays?: number | null }) =>
  api<ResumeLink>(LINKS, { method: 'POST', ...json(command) })
export const revokeResumeLink = (id: string) => api<ResumeLink>(`${LINKS}/${encoded(id)}/revoke`, { method: 'POST' })
export type LinkPatch = { expiresInDays?: number; neverExpires?: boolean; hideContact?: boolean; label?: string; reactivate?: boolean }
/**
 * Changes a link in place — expiry, contact visibility, name — or turns a suspended one back on (reactivate);
 * the address and receipts stay.
 */
export const updateResumeLink = (id: string, patch: LinkPatch) =>
  api<ResumeLink>(`${LINKS}/${encoded(id)}`, { method: 'PATCH', ...json(patch) })
/** 这是我自己打开的: removes that reader's visits and leaves them out from now on. */
export const markOwnView = (linkId: string, viewId: string) =>
  api<ResumeLink>(`${LINKS}/${encoded(linkId)}/views/${encoded(viewId)}/own`, { method: 'POST' })
export const fetchLinkViews = (id: string) => api<LinkViewRecord[]>(`${LINKS}/${encoded(id)}/views`)

export const fetchSharedResume = (token: string) => api<SharedResume>(`${PUBLIC}/${encoded(token)}`)
export const startResumeView = (token: string, referrer: string) =>
  api<ViewStart>(`${PUBLIC}/${encoded(token)}/views`, { method: 'POST', ...json({ referrer }) })

/** Reading time goes out with sendBeacon when possible, so it survives the tab closing. */
export function sendReadTime(token: string, viewId: string, viewKey: string, seconds: number): void {
  const url = `${PUBLIC}/${encoded(token)}/views/${encoded(viewId)}/heartbeat`
  const body = JSON.stringify({ viewKey, seconds })
  try {
    if (typeof navigator !== 'undefined' && navigator.sendBeacon?.(url, new Blob([body], { type: 'application/json' }))) return
  } catch { /* fall back to fetch */ }
  void fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true, credentials: 'include' }).catch(() => {})
}
