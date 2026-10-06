export type CareerProfile = {
  accountId: string
  basics: Record<string, unknown>
  intentions: Record<string, unknown>
  preferences: Record<string, unknown>
  summary?: string | null
  avatarFileId?: string | null
  snapshotVersion: number
  version: number
  updatedAt: string
  completeness: number
  missingItems: CareerMissingItem[]
}

export type CareerMissingItem = { key: string; label: string; fieldId: string }

export type CareerOverview = {
  profileCompleteness: number
  missingItems: CareerMissingItem[]
  totalRecords: number
  inUseRecords: number
  outcomeRecords: number
  pendingRecords: number
  skillKeywords: number
  healthScore: number
  storageUsedBytes: number
  storageQuotaBytes: number
  readyFiles: number
  processingFiles: number
  categoryCounts: Record<string, number>
  updatedAt: string
}

export type CareerSearchResult = {
  type: 'ACTION' | 'RECORD' | 'FILE' | 'FOLDER' | string
  id: string
  title: string
  subtitle: string
  view: 'profile' | 'records' | 'files'
  targetId: string
}

export type CareerRecord = {
  id: string
  type: string
  title: string
  organization?: string | null
  role?: string | null
  startDate?: string | null
  endDate?: string | null
  location?: string | null
  description?: string | null
  coreOutcome?: string | null
  url?: string | null
  payload: Record<string, unknown>
  strength?: string | null
  pendingSupplement: boolean
  sourceType: string
  sourceRefId?: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  confirmed: boolean
  sortOrder: number
  resumeReferenceCount: number
  version: number
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
}

export type CareerFile = {
  id: string
  privateFileId: string
  category: string
  displayName: string
  originalFilename: string
  contentType: string
  sizeBytes: number
  sha256: string
  scanStatus: string
  previewStatus: string
  previewPageCount: number
  previewError?: string | null
  status: 'ACTIVE' | 'ARCHIVED' | 'QUARANTINED'
  folderId?: string | null
  processingTaskId?: string | null
  processingStatus: 'SCANNING' | 'PREVIEWING' | 'READY' | 'SCAN_FAILED' | 'PREVIEW_FAILED' | 'INFECTED' | string
  processingAttempts: number
  version: number
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
}

export type CareerFileFolder = {
  id: string
  name: string
  status: 'ACTIVE' | 'ARCHIVED'
  fileCount: number
  version: number
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
}

export type CareerStorage = {
  usedBytes: number
  quotaBytes: number
  availableBytes: number
  categoryCounts: Record<string, number>
}

export type CareerFileUpload = {
  file: CareerFile
  task: import('@/shared/api/task').TaskView
}

export type CareerAvatarUpload = CareerFileUpload & {
  profile: CareerProfile
}

export type CareerHistorySummary = {
  total: number
  counts: Record<string, number>
  archivedAt?: string | null
}

export type PageResult<T> = { items: T[]; total: number; page: number; size: number }

export type CareerRecordWrite = {
  type: string
  title: string
  organization?: string
  role?: string
  startDate?: string
  endDate?: string
  location?: string
  description?: string
  coreOutcome?: string
  url?: string
  payload?: Record<string, unknown>
  strength?: string
  sortOrder?: number
  expectedVersion?: number
}

export type CareerAiAvailability = {
  available: boolean
  model: string
  reason?: string | null
}

export type CareerAiCandidate = {
  id: string
  recordId: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  proposed: { description?: string; coreOutcome?: string; reason?: string }
  diff: {
    description?: { before: string; after: string; changed: boolean }
    coreOutcome?: { before: string; after: string; changed: boolean }
  }
  sourceRefs: Array<{ recordId: string; type: string; title: string; quote: string }>
  modelName?: string | null
  version: number
  createdAt: string
  updatedAt: string
  decidedAt?: string | null
}
