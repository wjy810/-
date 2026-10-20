/** 投递追踪与简历查看回执 (docs/phase2/01). Shapes mirror JobApplicationService / ResumeLinkService. */

export type ApplicationStage = 'WISHLIST' | 'APPLIED' | 'ASSESSMENT' | 'INTERVIEW' | 'OFFER' | 'CLOSED'
export type ApplicationOutcome = 'ACCEPTED' | 'DECLINED' | 'REJECTED' | 'WITHDRAWN' | 'NO_RESPONSE'
export type ApplicationChannel = 'OFFICIAL_SITE' | 'JOB_BOARD' | 'REFERRAL' | 'CAMPUS' | 'EMAIL' | 'HEADHUNTER' | 'OTHER'
export type LinkStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED'

export type ResumeLink = {
  id: string
  token: string
  path: string
  label: string
  resumeVersionId: string
  resumeTitle?: string | null
  applicationId?: string | null
  hideContact: boolean
  expiresAt?: string | null
  revokedAt?: string | null
  status: LinkStatus
  viewCount: number
  visitorCount: number
  readSeconds: number
  firstViewedAt?: string | null
  lastViewedAt?: string | null
  createdAt: string
}

export type LinkViewRecord = {
  id: string
  linkId: string
  device: 'DESKTOP' | 'MOBILE' | 'TABLET' | 'UNKNOWN' | string
  client: 'WECHAT' | 'WECOM' | 'DINGTALK' | 'FEISHU' | 'QQ' | 'BROWSER' | string
  referrerHost?: string | null
  startedAt: string
  readSeconds: number
}

/** Every link an application has had, added up: replacing a link keeps the receipts. */
export type ApplicationReceipts = {
  linkCount: number
  views: number
  visitors: number
  readSeconds: number
  firstViewedAt?: string | null
  lastViewedAt?: string | null
}

export type JobApplication = {
  id: string
  company: string
  positionTitle: string
  location?: string | null
  channel: ApplicationChannel
  jobUrl?: string | null
  stage: ApplicationStage
  furthestStage: ApplicationStage
  outcome?: ApplicationOutcome | null
  salaryText?: string | null
  resumeVersionId?: string | null
  resumeMasterId?: string | null
  resumeTitle?: string | null
  jobMatchId?: string | null
  matchTitle?: string | null
  matchScore?: number | null
  appliedOn?: string | null
  nextStep?: string | null
  nextStepAt?: string | null
  notes?: string | null
  version: number
  createdAt: string
  updatedAt: string
  archivedAt?: string | null
  link?: ResumeLink | null
  receipts?: ApplicationReceipts | null
}

export type ApplicationStats = {
  total: number
  active: number
  applied: number
  linked: number
  opened: number
  interviews: number
  offers: number
  overdue: number
  upcoming: number
}

export type ApplicationOverview = { items: JobApplication[]; stats: ApplicationStats; archivedCount: number }

export type ApplicationEvent = {
  id: string
  type: 'CREATED' | 'STAGE_CHANGED' | 'LINK_CREATED' | 'LINK_REVOKED' | 'LINK_UPDATED' | 'FIRST_VIEWED' | 'NEW_VISITOR'
    | 'OWN_VIEW_MARKED' | 'NEXT_STEP_DONE' | 'ARCHIVED' | 'RESTORED' | string
  fromStage?: ApplicationStage | null
  toStage?: ApplicationStage | null
  detail?: string | null
  createdAt: string
}

export type ApplicationDetail = {
  application: JobApplication
  events: ApplicationEvent[]
  links: ResumeLink[]
  views: LinkViewRecord[]
}

export type ShareableVersion = {
  versionId: string
  masterId: string
  resumeTitle: string
  ordinal: number
  source?: string | null
  anonymous: boolean
  frozenAt: string
}

export type ApplicationWrite = {
  company: string
  positionTitle: string
  location?: string | null
  channel: ApplicationChannel
  jobUrl?: string | null
  stage?: ApplicationStage
  outcome?: ApplicationOutcome | null
  salaryText?: string | null
  resumeVersionId?: string | null
  jobMatchId?: string | null
  appliedOn?: string | null
  nextStep?: string | null
  nextStepAt?: string | null
  notes?: string | null
  createLink?: boolean
  hideContact?: boolean
  expectedVersion?: number
}

export type SharedResume = {
  templateId: string
  design: unknown
  content: Record<string, unknown>
  photo?: string | null
  pageLimit: number
  sharedAt: string
  expiresAt?: string | null
  ownerPreview: boolean
  hideContact: boolean
}

export type ViewStart = {
  recorded: boolean
  reason?: 'OWNER' | 'AUTOMATED' | 'LIMITED' | null
  viewId?: string | null
  viewKey?: string | null
  readSeconds: number
}
