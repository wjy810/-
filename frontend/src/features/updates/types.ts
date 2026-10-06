export type ReleaseType = 'FEATURE' | 'IMPROVEMENT' | 'FIX' | 'SECURITY' | 'DEPRECATED' | 'PLANNED'
export type ReleaseStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'ARCHIVED'

export type UpdateSection = {
  id?: string
  sectionType: string
  title: string
  body: string
  items: string[]
  sortOrder?: number
  imageAssetId?: string | null
  imageAlt?: string | null
}

export type UpdateAsset = {
  id: string
  releaseId: string
  filename: string
  contentType: string
  sizeBytes: number
  url: string
  status: string
}

export type ReleaseSummary = {
  id: string
  versionLabel: string
  slug: string
  title: string
  summary: string
  releaseType: ReleaseType | string
  status: ReleaseStatus | string
  audience: string
  modules: string[]
  ctaLabel?: string | null
  ctaPath?: string | null
  showWhatsNew: boolean
  sendNotification: boolean
  scheduledAt?: string | null
  publishedAt?: string | null
  archivedAt?: string | null
  currentRevision: number
  versionNo: number
  createdAt: string
  updatedAt: string
  readCount: number
  distributionStatus?: string | null
}

export type VersionLink = { versionLabel: string; title: string; path: string }
export type ReleaseDetail = {
  release: ReleaseSummary
  sections: UpdateSection[]
  previousVersion?: VersionLink | null
  nextVersion?: VersionLink | null
}

export type UpdatePage = { items: ReleaseSummary[]; total: number; page: number; size: number }
export type UpdateFacets = { types: Record<string, number>; modules: Record<string, number>; versions: string[] }
export type AdminOverview = { published: number; drafts: number; scheduled: number; reads: number }
export type ValidationView = { valid: boolean; issues: string[] }

export type ReleaseDraft = {
  versionLabel: string
  title: string
  summary: string
  releaseType: string
  audience: string
  modules: string[]
  ctaLabel: string
  ctaPath: string
  showWhatsNew: boolean
  sendNotification: boolean
  sections: UpdateSection[]
  expectedVersion: number
}
