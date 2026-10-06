import type { ResumeDesignV2 } from '@/resume-render/theme/design'
export type ResumeMasterStatus = 'DRAFT' | 'PENDING_CONFIRMATION' | 'READY_TO_EXPORT' | 'ARCHIVED'

export type ResumeVersionStatus =
  | 'GENERATING'
  | 'PENDING_USER_CONFIRMATION'
  | 'FROZEN'
  | 'ARCHIVED'

export type ResumeCreateMode = 'BLANK' | 'TEMPLATE' | 'IMPORT'

export type ResumeTemplateCode = 'SOFTWARE_DEV' | 'QA' | 'DATA_ANALYSIS' | 'PRODUCT'

export type ResumeFieldKey =
  | 'TITLE'
  | 'EDUCATION'
  | 'EXPERIENCE'
  | 'PROJECTS'
  | 'SKILLS'
  | 'CERTIFICATES'
  | 'SELF_INTRO'
  | 'KEY_OUTCOMES'

export type KeyOutcome = {
  id: string
  text: string
  evidenceId?: string | null
  waiveNoEvidence: boolean
}

export type ResumeVersionView = {
  id: string
  masterId: string
  status: ResumeVersionStatus | string
  statusLabel?: string | null
  source?: string | null
  customizeTaskId?: string | null
  jobVersionId?: string | null
  layoutInstanceId?: string | null
  snapshot?: Record<string, unknown> | null
  immutable: boolean
  version: number
  createdAt?: string | null
  frozenAt?: string | null
  archivedAt?: string | null
}

export type ResumeMasterSummary = {
  id: string
  title?: string | null
  status: ResumeMasterStatus | string
  statusLabel?: string | null
  version: number
  updatedAt?: string | null
}

export type ResumeMasterView = {
  id: string
  title?: string | null
  status: ResumeMasterStatus | string
  statusLabel?: string | null
  source?: string | null
  templateCode?: string | null
  education?: string | null
  experience?: string | null
  projects?: string | null
  skills?: string | null
  certificates?: string | null
  selfIntro?: string | null
  keyOutcomes?: KeyOutcome[] | null
  pendingCandidateIds?: string[] | null
  version: number
  createdAt?: string | null
  updatedAt?: string | null
  archivedAt?: string | null
  versions?: ResumeVersionView[] | null
}

export type ResumeCandidateView = {
  id: string
  masterId: string
  fieldKey: string
  proposedValue: unknown
  status: string
  candidateSource?: string | null
  aiAction?: 'REFINE' | 'POLISH' | string | null
  reason?: string | null
  diff?: unknown
  sourceFacts?: unknown
  generationMetadata?: unknown
  careerLibrarySnapshotVersion?: number | null
  sourceRefs?: unknown
  sourceStale?: boolean
  version: number
  createdAt?: string | null
  decidedAt?: string | null
}

export type ResumeAiAvailability = {
  available: boolean
  model: string
  reason?: string | null
}

export type ResumeAiField = 'SELF_INTRO' | 'EXPERIENCE' | 'PROJECTS'
export type ResumeAiAction = 'REFINE' | 'POLISH'

export type ResumeAiCandidateCommand = {
  fieldKey: ResumeAiField
  action: ResumeAiAction
  model?: string
  expectedVersion?: number
}

export type ResumeCompareView = {
  left: ResumeVersionView
  right: ResumeVersionView
}

export type ResumeCreateCommand = {
  mode: ResumeCreateMode
  title?: string
  templateCode?: ResumeTemplateCode | string
  importText?: string
}

export type ResumeUpdateCommand = {
  title?: string | null
  education?: string | null
  experience?: string | null
  projects?: string | null
  skills?: string | null
  certificates?: string | null
  selfIntro?: string | null
  keyOutcomes?: Array<{
    id?: string
    text?: string
    evidenceId?: string | null
    waiveNoEvidence?: boolean
  }>
  expectedVersion?: number
}

export type ResumeDraft = {
  title: string
  education: string
  experience: string
  projects: string
  skills: string
  certificates: string
  selfIntro: string
  outcomes: KeyOutcome[]
}

export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type ResumeTemplateSummary = {
  id: string
  displayName: string
  familyName: string
  languageCode: string
  recommendedPages: string
  atsCandidateLevel: string
  photoPolicy: string
  variants: string[]
  tags: string[]
  status: 'PUBLISHED' | 'DEMO' | string
  rendererProtocol?: string | null
  layoutDefinitionJson?: string | null
  thumbnailUri?: string | null
}

export type ResumeTemplateDetail = {
  template: ResumeTemplateSummary
  templateVersionId: string
  revisionNo: number
  rendererProtocol: string
  layoutDefinitionJson: string
  variants: string[]
  thumbnailUri?: string | null
  atsNotice: string
  docxAvailable: boolean
  docxUnavailableReason?: string | null
}

export type ResumeOverflowItem = {
  page: number
  slotKey: string
  excessUnits: number
  suggestedAction: string
}

export type ResumeOverflowReport = {
  valid: boolean
  consumedUnits: number
  items: ResumeOverflowItem[]
}

export type ResumeTemplatePreview = {
  templateId: string
  templateVersionId: string
  variantCode: string
  overflow: ResumeOverflowReport
  variantValid: boolean
  docxAvailable: boolean
  docxUnavailableReason?: string | null
}

export type ResumeLayoutView = {
  id: string
  masterId: string
  templateVersionId: string
  templateId?: string | null
  templateName?: string | null
  variantCode: string
  rendererProtocol?: string | null
  layoutDefinitionJson?: string | null
  /** resume-design-v2 for built-in templates (resume-render-v4), v1 for layouts frozen with a retired template. */
  design?: ResumeDesignV2 | ResumeDesignSettings | null
  status: 'VALID' | 'OVERFLOW' | 'FROZEN' | 'ARCHIVED' | string
  overflow: ResumeOverflowReport
  version: number
  frozenAt?: string | null
}

export type ResumeDesignSettings = {
  schemaVersion: 'resume-design-v1' | string
  fontPreset: 'MODERN_SANS' | 'CLASSIC_SERIF' | string
  fontScale: 'SMALL' | 'STANDARD' | 'LARGE' | string
  lineHeight: 'COMPACT' | 'STANDARD' | 'AIRY' | string
  pageMargin: 'NARROW' | 'STANDARD' | 'WIDE' | string
  accentColor: string
  dateFormat: 'YYYY_DOT_MM' | 'YYYY_CN_MM' | string
  headerLayout: 'MINIMAL' | 'BAND' | 'SPLIT' | 'COMPACT' | string
  headingStyle: 'RULE' | 'BAR' | 'SIDELINE' | 'PLAIN' | 'TABLE' | string
  photoMode: 'AUTO' | 'SHOW' | 'HIDE' | string
  density: 'COMPACT' | 'STANDARD' | 'AIRY' | string
  hiddenSections: string[]
  sectionOrder: string[]
}

export type CurrentResumeLayout = {
  selected: boolean
  layout?: ResumeLayoutView | null
}

export type ResumeTemplateCatalogFilters = {
  keyword?: string
  family?: string
  tag?: string
  language?: string
  pages?: string
  photoPolicy?: string
  atsLevel?: string
  page?: number
  size?: number
}

export type TemplateCatalogCapability = 'SMART_EDITABLE' | 'DOCX_DOWNLOAD'

export type TemplateCatalogAssetKind =
  | 'RESUME'
  | 'COVER'
  | 'SCHOOL_APPLICATION'
  | 'COVER_LETTER'

export type TemplateCatalogFacet = {
  catalogEntryId: string
  type: string
  code: string
  label: string
}

export type TemplateCatalogPreviewPage = {
  pageNumber: number
  imageUri: string
}

export type TemplateCatalogItem = {
  id: string
  entryType: 'SMART_TEMPLATE' | 'DOCX_ASSET' | string
  referenceId: string
  title: string
  summary?: string | null
  capability: TemplateCatalogCapability | string
  assetKind: TemplateCatalogAssetKind | string
  languageCode: string
  pageCount?: string | null
  photoPolicy: string
  thumbnailUri?: string | null
  sourceName: string
  sourceUri: string
  attribution: string
  downloadCount: number
  facets: TemplateCatalogFacet[]
  publishedAt?: string | null
  previewStatus: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'NOT_APPLICABLE' | string
  previewPageCount: number
  previewPages: TemplateCatalogPreviewPage[]
  previewError?: string | null
}

export type TemplateCatalogFacetOption = {
  code: string
  label: string
  count: number
}

export type TemplateCatalogFacetGroup = {
  type: string
  options: TemplateCatalogFacetOption[]
}

export type TemplateCatalogQuery = {
  keyword?: string
  capability?: TemplateCatalogCapability | ''
  assetKind?: TemplateCatalogAssetKind | ''
  occupation?: string
  jobTag?: string
  style?: string
  language?: string
  pages?: string
  photoPolicy?: string
  careerStage?: string
  page?: number
  size?: number
}
