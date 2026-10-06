export type DeletionStatus =
  | 'SUBMITTED'
  | 'PROCESSING'
  | 'PARTIALLY_RESTRICTED'
  | 'COMPLETED'
  | 'FAILED'

export type DeletionReceiptView = {
  moduleCode?: string | null
  status?: string | null
  message?: string | null
}

export type DeletionImpactItem = {
  kind?: string | null
  id?: string | null
  status?: string | null
  relation?: string | null
  label?: string | null
}

export type DeletionPreview = {
  impactSummary?: string | null
  legalExceptionNote?: string | null
  statusHint?: string | null
  scope?: string | null
  targetType?: string | null
  targetId?: string | null
  objectScoped?: boolean | null
  irreversible?: boolean | null
  canProceed?: boolean | null
  impacts?: DeletionImpactItem[] | null
  blockers?: string[] | null
  relatedApplications?: unknown
  relatedResumeVersions?: unknown
  relatedInterviews?: unknown
  relatedReviews?: unknown
}

export type DeletionView = {
  id: string
  scope?: string | null
  targetType?: string | null
  targetId?: string | null
  status: DeletionStatus | string
  impactSummary?: string | null
  legalExceptionNote?: string | null
  version?: number | null
  receipts?: DeletionReceiptView[] | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type ExportView = {
  id: string
  taskId?: string | null
  scope?: string | null
  taskStatus?: string | null
  failureReason?: string | null
  downloadAvailable?: boolean | null
  downloadExpired?: boolean | null
  downloadExpiresAt?: string | null
  createdAt?: string | null
  fileId?: string | null
  downloadUrl?: string | null
}

export type ShareView = {
  id: string
  resourceType: string
  resourceId: string
  token?: string | null
  expiresAt: string
  revokedAt?: string | null
  createdAt: string
}

export type SignedDownloadUrl = {
  url: string
  expiresAt?: string | null
}

export type SubmitDeletionBody = {
  scope: 'ACCOUNT' | 'OBJECT'
  targetType?: string
  targetId?: string
  confirmationAck: boolean
}
