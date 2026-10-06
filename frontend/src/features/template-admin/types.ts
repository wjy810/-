export type PageResult<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type TemplateFamily = {
  id: string
  displayName: string
  familyName: string
  languageCode: string
  recommendedPages: string
  atsCandidateLevel: string
  photoPolicy: string
  status: string
  updatedAt: string
}

export type TemplateEvidence = {
  id: string
  evidenceType: string
  originalFilename: string
  contentType: string
  sizeBytes: number
  fileHash: string
  description: string
  uploadedBy: string
  createdAt: string
}

export type TemplateAsset = {
  id: string
  sourceName: string
  sourceUri: string
  licenseStatus: string
  licenseEvidenceId?: string
  fileHash: string
  contentType: string
  sizeBytes: number
  scanStatus: string
  scanReportJson?: string
  stored: boolean
  status: string
  rejectionReason?: string
  uploadedBy: string
  version: number
  reviewedBy?: string
  scannedAt?: string
  reviewedAt?: string
  createdAt: string
}

export type MalwareScannerStatus = {
  ready: boolean
  outcome: string
  engine?: string
  engineVersion?: string
  detailCode?: string
  checkedAt: string
}

export type TemplateVersion = {
  id: string
  templateId: string
  revisionNo: number
  status: string
  rendererProtocol: string
  definitionJson: string
  thumbnailUri?: string
  sourceAssetId?: string
  independentDesignEvidenceId?: string
  testReportJson?: string
  authorizationVerified: boolean
  securityVerified: boolean
  renderVerified: boolean
  wordVerified: boolean
  wpsVerified: boolean
  atsVerified: boolean
  version: number
  publishedAt?: string
  retiredAt?: string
}

export type DraftInput = {
  rendererProtocol: string
  definitionJson: string
  thumbnailUri?: string
  sourceAssetId?: string
  independentDesignEvidenceId?: string
  expectedVersion?: number
}

export type TestInput = {
  gateCode: string
  outcome: string
  evidenceId: string
  environmentJson: string
  summary: string
  expectedVersion: number
}

export type TemplateImportBatch = {
  id: string
  sourceCode: string
  sourceName: string
  sourceUri: string
  distributionMode: string
  status: string
  totalCount: number
  processedCount: number
  readyCount: number
  duplicateCount: number
  rejectedCount: number
  quarantinedCount: number
  unsupportedCount: number
  errorMessage?: string
  startedAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
  version: number
}

export type TemplateImportItem = {
  id: string
  sourceRelativePath: string
  originalFilename: string
  sizeBytes: number
  fileHash?: string
  status: string
  assetId?: string
  duplicateAssetId?: string
  detailCode?: string
  updatedAt: string
}

export type CatalogFacet = { catalogEntryId?: string; type: string; code: string; label: string }
export type CatalogItem = {
  id: string
  entryType: string
  referenceId: string
  title: string
  summary?: string
  capability: string
  assetKind: string
  languageCode: string
  pageCount?: string
  photoPolicy: string
  thumbnailUri?: string
  sourceName: string
  sourceUri: string
  attribution: string
  downloadCount: number
  facets: CatalogFacet[]
  publishedAt?: string
}
export type CatalogAdminItem = {
  item: CatalogItem
  publicationStatus: string
  retiredAt?: string
  updatedAt: string
  version: number
}
