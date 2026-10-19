<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { Archive, Check, CheckCircle2, Clock, Download, Pencil, Plus, RefreshCw, Send, Upload, X } from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import AppIcon from '@/shared/ui/AppIcon.vue'
import UiBadge, { type BadgeTone } from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiEmptyState from '@/shared/ui/UiEmptyState.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiPageHeader from '@/shared/ui/UiPageHeader.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { glossAssetKind, glossCapability, glossScannerDetail, glossStatus } from '../labels'
import {
  createTemplateDraft,
  downloadTemplateEvidence,
  getMalwareScannerStatus,
  importTemplateAsset,
  listTemplateAssets,
  listTemplateEvidence,
  listTemplateFamilies,
  listTemplateVersions,
  publishTemplateVersion,
  recordTemplateTest,
  retireTemplateVersion,
  rescanTemplateAsset,
  reviewTemplateAsset,
  updateTemplateDraft,
  uploadTemplateEvidence,
  listImportBatches,
  listImportItems,
  pauseImportBatch,
  retryImportBatch,
  startImportBatch,
  listCatalogEntries,
  updateCatalogEntry,
  publishCatalogEntry,
  retireCatalogEntry,
} from '../services/templateAdminApi'
import type {
  MalwareScannerStatus,
  TemplateAsset,
  TemplateEvidence,
  TemplateFamily,
  TemplateVersion,
  TemplateImportBatch,
  TemplateImportItem,
  CatalogAdminItem,
} from '../types'

type Tab = 'families' | 'assets' | 'evidence' | 'imports' | 'catalog'
type ConfirmAction = { kind: 'publish' | 'retire'; version: TemplateVersion }

const EVIDENCE_TYPES = [
  ['LICENSE', '商业授权'],
  ['INDEPENDENT_DESIGN', '独立设计'],
  ['SECURITY_SCAN', '安全扫描'],
  ['RENDER_TEST', '渲染测试'],
  ['WORD_TEST', 'Word 兼容'],
  ['WPS_TEST', 'WPS 兼容'],
  ['ATS_TEST', 'ATS 测试'],
] as const

const GATES = [
  { code: 'AUTHORIZATION', label: '授权', field: 'authorizationVerified', evidenceType: '' },
  { code: 'SECURITY', label: '安全', field: 'securityVerified', evidenceType: 'SECURITY_SCAN' },
  { code: 'RENDER', label: '渲染', field: 'renderVerified', evidenceType: 'RENDER_TEST' },
  { code: 'WORD', label: 'Word', field: 'wordVerified', evidenceType: 'WORD_TEST' },
  { code: 'WPS', label: 'WPS', field: 'wpsVerified', evidenceType: 'WPS_TEST' },
  { code: 'ATS', label: 'ATS', field: 'atsVerified', evidenceType: 'ATS_TEST' },
] as const

const DEFAULT_DEFINITION = JSON.stringify(
  {
    page: { maxPages: 1, capacityUnits: 3200 },
    columns: [{ id: 'main', widthPercent: 100, slotKeys: ['summary', 'experience', 'projects', 'skills'] }],
    slots: [
      { key: 'summary', order: 10, capacityUnits: 400, repeatable: false, hideWhenEmpty: true },
      { key: 'experience', order: 20, capacityUnits: 1000, repeatable: true, hideWhenEmpty: true },
      { key: 'projects', order: 30, capacityUnits: 1000, repeatable: true, hideWhenEmpty: true },
      { key: 'skills', order: 40, capacityUnits: 600, repeatable: true, hideWhenEmpty: true },
    ],
    tokens: { pageSize: 'A4' },
  },
  null,
  2,
)

const activeTab = ref<Tab>('families')
const tabOrder: Tab[] = ['families', 'assets', 'evidence', 'imports', 'catalog']
const activeTabIndex = computed(() => tabOrder.indexOf(activeTab.value))
const families = ref<TemplateFamily[]>([])
const assets = ref<TemplateAsset[]>([])
const evidence = ref<TemplateEvidence[]>([])
const malwareScanner = ref<MalwareScannerStatus | null>(null)
const versions = ref<TemplateVersion[]>([])
const importBatches = ref<TemplateImportBatch[]>([])
const importItems = ref<TemplateImportItem[]>([])
const selectedBatchId = ref('')
const catalogEntries = ref<CatalogAdminItem[]>([])
const catalogTotal = ref(0)
const catalogPage = ref(0)
const catalogTarget = ref<CatalogAdminItem | null>(null)
let refreshTimer: number | undefined
const selectedFamilyId = ref('')
const loading = ref(true)
const versionsLoading = ref(false)
const pending = ref('')
const error = ref('')
const notice = ref('')
useToastFeedback(error, 'error', 'template-admin-error')
useToastFeedback(notice, 'success', 'template-admin-notice')

const evidenceFile = ref<File | null>(null)
const evidenceInput = ref<HTMLInputElement | null>(null)
const evidenceForm = reactive({ evidenceType: 'INDEPENDENT_DESIGN', description: '' })

const assetFile = ref<File | null>(null)
const assetInput = ref<HTMLInputElement | null>(null)
const assetForm = reactive({
  sourceName: '',
  sourceUri: '',
  licenseStatus: 'UNCONFIRMED',
  licenseEvidenceId: '',
})

const reviewTarget = ref<TemplateAsset | null>(null)
const reviewForm = reactive({ decision: 'APPROVED', licenseStatus: 'APPROVED', licenseEvidenceId: '', reason: '' })

const draftOpen = ref(false)
const editingVersion = ref<TemplateVersion | null>(null)
const draftSourceMode = ref<'asset' | 'independent'>('independent')
const draftForm = reactive({
  rendererProtocol: 'resume-layout-v1',
  definitionJson: DEFAULT_DEFINITION,
  thumbnailUri: '',
  sourceAssetId: '',
  independentDesignEvidenceId: '',
})

const testTarget = ref<TemplateVersion | null>(null)
const testForm = reactive({
  gateCode: 'AUTHORIZATION',
  outcome: 'PASSED',
  evidenceId: '',
  environmentJson: '{"runner":"manual-controlled","os":"Windows 11"}',
  summary: '',
})
const confirmAction = ref<ConfirmAction | null>(null)
const catalogForm = reactive({
  title: '', summary: '', assetKind: 'RESUME', languageCode: 'zh-CN', pageCount: '',
  photoPolicy: 'UNSPECIFIED', thumbnailUri: '', occupations: '', jobs: '', styles: '', careerStages: '',
})

const selectedFamily = computed(() => families.value.find((item) => item.id === selectedFamilyId.value) ?? null)
const approvedAssets = computed(() => assets.value.filter(
  (item) => item.status === 'APPROVED' && item.scanStatus === 'PASSED',
))
const independentEvidence = computed(() => evidence.value.filter((item) => item.evidenceType === 'INDEPENDENT_DESIGN'))
const licenseEvidence = computed(() => evidence.value.filter((item) =>
  item.evidenceType === (reviewForm.licenseStatus === 'APPROVED' ? 'LICENSE' : 'INDEPENDENT_DESIGN'),
))
const canCreateDraft = computed(() => !versions.value.some((item) => item.status === 'DRAFT' || item.status === 'TESTING'))
const provenanceLocked = computed(() => Boolean(
  editingVersion.value?.sourceAssetId || editingVersion.value?.independentDesignEvidenceId,
))
const reviewReady = computed(() => {
  if (!reviewTarget.value) return false
  return reviewForm.decision === 'REJECTED'
    ? reviewForm.reason.trim().length > 0
    : reviewForm.licenseEvidenceId.length > 0
})
const draftReady = computed(() => {
  const sourceReady = draftSourceMode.value === 'asset'
    ? draftForm.sourceAssetId.length > 0
    : draftForm.independentDesignEvidenceId.length > 0
  return sourceReady && draftForm.rendererProtocol.trim().length > 0 && draftForm.definitionJson.trim().length > 0
})
const testEvidenceOptions = computed(() => {
  const target = testTarget.value
  if (!target) return []
  if (testForm.gateCode === 'AUTHORIZATION') {
    const evidenceId = target.independentDesignEvidenceId
      || assets.value.find((item) => item.id === target.sourceAssetId)?.licenseEvidenceId
    return evidence.value.filter((item) => item.id === evidenceId)
  }
  const gate = GATES.find((item) => item.code === testForm.gateCode)
  return evidence.value.filter((item) => item.evidenceType === gate?.evidenceType)
})
const testReady = computed(() => testForm.evidenceId.length > 0 && testForm.summary.trim().length > 0)

function evidenceLabel(type: string): string {
  return EVIDENCE_TYPES.find(([value]) => value === type)?.[1] ?? type
}

function provenanceLabel(version: TemplateVersion): string {
  if (version.sourceAssetId) return '候选资产'
  if (version.independentDesignEvidenceId) return '独立设计'
  return '来源待绑定'
}

function statusTone(status: string): BadgeTone {
  if (status === 'APPROVED' || status === 'PUBLISHED' || status === 'PASSED') return 'success'
  if (status === 'REJECTED' || status === 'STATIC_REJECTED' || status === 'MALWARE_DETECTED'
    || status === 'STORAGE_INTEGRITY_FAILED' || status === 'FAILED') return 'danger'
  if (status === 'REVIEWING' || status === 'TESTING' || status === 'EXTERNAL_REQUIRED'
    || status === 'MALWARE_SCANNER_UNAVAILABLE' || status === 'MALWARE_SCANNER_ERROR') return 'accent'
  if (status === 'RETIRED') return 'neutral'
  return 'primary'
}

function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function shortHash(hash: string): string {
  return `${hash.slice(0, 10)}…${hash.slice(-6)}`
}

function gatePassed(version: TemplateVersion, field: (typeof GATES)[number]['field']): boolean {
  return version[field]
}

function selectFile(event: Event, target: 'asset' | 'evidence'): void {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  if (target === 'asset') assetFile.value = file
  else evidenceFile.value = file
}

async function loadVersions(): Promise<void> {
  if (!selectedFamilyId.value) {
    versions.value = []
    return
  }
  versionsLoading.value = true
  try {
    versions.value = await listTemplateVersions(selectedFamilyId.value)
  } catch (cause) {
    versions.value = []
    error.value = errorMessage(cause, '模板版本读取失败')
  } finally {
    versionsLoading.value = false
  }
}

async function loadAll(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [nextFamilies, nextAssets, nextEvidence, nextScanner, nextBatches, nextCatalog] = await Promise.all([
      listTemplateFamilies(),
      listTemplateAssets(),
      listTemplateEvidence(),
      getMalwareScannerStatus(),
      listImportBatches(),
      listCatalogEntries(),
    ])
    families.value = nextFamilies
    assets.value = nextAssets.items
    evidence.value = nextEvidence.items
    malwareScanner.value = nextScanner
    importBatches.value = nextBatches.items
    catalogEntries.value = nextCatalog.items
    catalogTotal.value = nextCatalog.total
    catalogPage.value = 0
    if (!importBatches.value.some((item) => item.id === selectedBatchId.value)) {
      selectedBatchId.value = importBatches.value[0]?.id ?? ''
    }
    importItems.value = selectedBatchId.value
      ? (await listImportItems(selectedBatchId.value)).items
      : []
    if (!families.value.some((item) => item.id === selectedFamilyId.value)) {
      selectedFamilyId.value = families.value[0]?.id ?? ''
    }
    await loadVersions()
  } catch (cause) {
    error.value = errorMessage(cause, '模板运营数据读取失败')
  } finally {
    loading.value = false
  }
}

async function refreshImports(): Promise<void> {
  importBatches.value = (await listImportBatches()).items
  if (selectedBatchId.value) importItems.value = (await listImportItems(selectedBatchId.value)).items
}

async function chooseBatch(id: string): Promise<void> {
  selectedBatchId.value = id
  importItems.value = (await listImportItems(id)).items
}

async function runBatchAction(action: 'start' | 'pause' | 'retry', batch?: TemplateImportBatch): Promise<void> {
  pending.value = `batch-${action}`
  error.value = ''
  try {
    const result = action === 'start'
      ? await startImportBatch()
      : action === 'pause'
        ? await pauseImportBatch(batch!.id)
        : await retryImportBatch(batch!.id)
    selectedBatchId.value = result.id
    await refreshImports()
    notice.value = action === 'start' ? '固定 HICV 目录批次已启动' : action === 'pause' ? '批次已暂停' : '失败项已重新排队'
  } catch (cause) {
    error.value = errorMessage(cause, '批次操作失败')
  } finally {
    pending.value = ''
  }
}

function openCatalog(item: CatalogAdminItem): void {
  catalogTarget.value = item
  Object.assign(catalogForm, {
    title: item.item.title,
    summary: item.item.summary ?? '',
    assetKind: item.item.assetKind,
    languageCode: item.item.languageCode,
    pageCount: item.item.pageCount ?? '',
    photoPolicy: item.item.photoPolicy,
    thumbnailUri: item.item.thumbnailUri ?? '',
    occupations: item.item.facets.filter((facet) => facet.type === 'OCCUPATION').map((facet) => `${facet.code}|${facet.label}`).join('\n'),
    jobs: item.item.facets.filter((facet) => facet.type === 'JOB').map((facet) => `${facet.code}|${facet.label}`).join('\n'),
    styles: item.item.facets.filter((facet) => facet.type === 'STYLE').map((facet) => `${facet.code}|${facet.label}`).join('\n'),
    careerStages: item.item.facets.filter((facet) => facet.type === 'CAREER_STAGE').map((facet) => `${facet.code}|${facet.label}`).join('\n'),
  })
}

function parseFacets(type: string, value: string) {
  return value.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
    const [code, ...label] = line.split('|')
    return { type, code: code.trim(), label: (label.join('|') || code).trim() }
  })
}

async function saveCatalog(): Promise<void> {
  const target = catalogTarget.value
  if (!target) return
  pending.value = 'catalog-save'
  error.value = ''
  try {
    const updated = await updateCatalogEntry(target.item.id, {
      title: catalogForm.title,
      summary: catalogForm.summary || undefined,
      assetKind: catalogForm.assetKind,
      languageCode: catalogForm.languageCode,
      pageCount: catalogForm.pageCount || undefined,
      photoPolicy: catalogForm.photoPolicy,
      thumbnailUri: catalogForm.thumbnailUri || undefined,
      facets: [
        ...parseFacets('OCCUPATION', catalogForm.occupations),
        ...parseFacets('JOB', catalogForm.jobs),
        ...parseFacets('STYLE', catalogForm.styles),
        ...parseFacets('CAREER_STAGE', catalogForm.careerStages),
      ],
      expectedVersion: target.version,
    })
    catalogTarget.value = null
    replaceCatalogEntry(updated)
    notice.value = '目录元数据已校正'
  } catch (cause) {
    error.value = errorMessage(cause, '目录保存失败')
  } finally {
    pending.value = ''
  }
}

/** Updates one row in place, so pages loaded with "加载更多" stay loaded. */
function replaceCatalogEntry(updated: CatalogAdminItem): void {
  catalogEntries.value = catalogEntries.value.map((entry) => (entry.item.id === updated.item.id ? updated : entry))
}

async function loadMoreCatalog(): Promise<void> {
  pending.value = 'catalog-more'
  error.value = ''
  try {
    const next = await listCatalogEntries(catalogPage.value + 1)
    const known = new Set(catalogEntries.value.map((entry) => entry.item.id))
    catalogEntries.value = [...catalogEntries.value, ...next.items.filter((entry) => !known.has(entry.item.id))]
    catalogTotal.value = next.total
    catalogPage.value += 1
  } catch (cause) {
    error.value = errorMessage(cause, '目录加载失败')
  } finally {
    pending.value = ''
  }
}

async function setCatalogPublication(item: CatalogAdminItem, publish: boolean): Promise<void> {
  pending.value = `catalog-${item.item.id}`
  error.value = ''
  try {
    replaceCatalogEntry(publish
      ? await publishCatalogEntry(item.item.id, item.version)
      : await retireCatalogEntry(item.item.id, item.version))
    notice.value = publish ? '目录项已发布' : '目录项已退休'
  } catch (cause) {
    error.value = errorMessage(cause, publish ? '目录发布失败' : '目录退休失败')
  } finally {
    pending.value = ''
  }
}

async function chooseFamily(id: string): Promise<void> {
  selectedFamilyId.value = id
  await loadVersions()
}

async function submitEvidence(): Promise<void> {
  if (!evidenceFile.value || !evidenceForm.description.trim()) return
  pending.value = 'evidence'
  error.value = ''
  try {
    await uploadTemplateEvidence({
      file: evidenceFile.value,
      evidenceType: evidenceForm.evidenceType,
      description: evidenceForm.description.trim(),
    })
    evidenceForm.description = ''
    evidenceFile.value = null
    if (evidenceInput.value) evidenceInput.value.value = ''
    evidence.value = (await listTemplateEvidence()).items
    notice.value = '不可变证据已登记'
  } catch (cause) {
    error.value = errorMessage(cause, '证据上传失败')
  } finally {
    pending.value = ''
  }
}

async function submitAsset(): Promise<void> {
  if (!assetFile.value || !assetForm.sourceUri.trim()) return
  pending.value = 'asset'
  error.value = ''
  try {
    await importTemplateAsset({
      file: assetFile.value,
      sourceName: assetForm.sourceName,
      sourceUri: assetForm.sourceUri,
      licenseStatus: assetForm.licenseStatus,
      licenseEvidenceId: assetForm.licenseEvidenceId || undefined,
    })
    assetForm.sourceName = ''
    assetForm.sourceUri = ''
    assetForm.licenseStatus = 'UNCONFIRMED'
    assetForm.licenseEvidenceId = ''
    assetFile.value = null
    if (assetInput.value) assetInput.value.value = ''
    assets.value = (await listTemplateAssets()).items
    notice.value = '候选 DOCX 已完成指纹、OOXML 与恶意文件扫描；请核对扫描状态'
  } catch (cause) {
    error.value = errorMessage(cause, '候选资产导入失败')
  } finally {
    pending.value = ''
  }
}

function canRescan(asset: TemplateAsset): boolean {
  return asset.stored && asset.scanStatus !== 'PASSED' && asset.scanStatus !== 'STATIC_REJECTED'
}

async function rescanAsset(asset: TemplateAsset): Promise<void> {
  pending.value = `rescan-${asset.id}`
  error.value = ''
  try {
    await rescanTemplateAsset(asset.id, asset.version)
    assets.value = (await listTemplateAssets()).items
    notice.value = '候选资产复扫已完成，请核对最新状态'
  } catch (cause) {
    error.value = errorMessage(cause, '候选资产复扫失败')
  } finally {
    pending.value = ''
  }
}

function openReview(asset: TemplateAsset, decision: 'APPROVED' | 'REJECTED'): void {
  reviewTarget.value = asset
  reviewForm.decision = decision
  reviewForm.licenseStatus = 'APPROVED'
  reviewForm.licenseEvidenceId = ''
  reviewForm.reason = ''
}

async function submitReview(): Promise<void> {
  const target = reviewTarget.value
  if (!target || !reviewReady.value) return
  pending.value = 'review'
  error.value = ''
  try {
    await reviewTemplateAsset(target.id, {
      decision: reviewForm.decision,
      licenseStatus: reviewForm.decision === 'APPROVED' ? reviewForm.licenseStatus : undefined,
      licenseEvidenceId: reviewForm.decision === 'APPROVED' ? reviewForm.licenseEvidenceId : undefined,
      reason: reviewForm.decision === 'REJECTED' ? reviewForm.reason.trim() : undefined,
      expectedVersion: target.version,
    })
    reviewTarget.value = null
    assets.value = (await listTemplateAssets()).items
    notice.value = reviewForm.decision === 'APPROVED' ? '候选资产已批准' : '候选资产已驳回'
  } catch (cause) {
    error.value = errorMessage(cause, '候选审批失败')
  } finally {
    pending.value = ''
  }
}

function openDraft(version?: TemplateVersion): void {
  editingVersion.value = version ?? null
  draftForm.rendererProtocol = version?.rendererProtocol ?? 'resume-layout-v1'
  draftForm.definitionJson = version?.definitionJson ?? DEFAULT_DEFINITION
  draftForm.thumbnailUri = version?.thumbnailUri ?? ''
  draftForm.sourceAssetId = version?.sourceAssetId ?? approvedAssets.value[0]?.id ?? ''
  draftForm.independentDesignEvidenceId = version?.independentDesignEvidenceId ?? independentEvidence.value[0]?.id ?? ''
  draftSourceMode.value = version?.sourceAssetId ? 'asset' : 'independent'
  draftOpen.value = true
}

async function submitDraft(): Promise<void> {
  if (!selectedFamily.value || !draftReady.value) return
  pending.value = 'draft'
  error.value = ''
  const input = {
    rendererProtocol: draftForm.rendererProtocol.trim(),
    definitionJson: draftForm.definitionJson.trim(),
    thumbnailUri: draftForm.thumbnailUri.trim() || undefined,
    sourceAssetId: draftSourceMode.value === 'asset' ? draftForm.sourceAssetId : undefined,
    independentDesignEvidenceId: draftSourceMode.value === 'independent'
      ? draftForm.independentDesignEvidenceId
      : undefined,
    expectedVersion: editingVersion.value?.version,
  }
  try {
    if (editingVersion.value) await updateTemplateDraft(editingVersion.value.id, input)
    else await createTemplateDraft(selectedFamily.value.id, input)
    draftOpen.value = false
    await loadVersions()
    notice.value = editingVersion.value ? '草稿修订已保存' : '模板草稿已创建'
  } catch (cause) {
    error.value = errorMessage(cause, '模板草稿保存失败')
  } finally {
    pending.value = ''
  }
}

function openTest(version: TemplateVersion): void {
  testTarget.value = version
  testForm.gateCode = 'AUTHORIZATION'
  testForm.outcome = 'PASSED'
  testForm.summary = ''
  testForm.environmentJson = '{"runner":"manual-controlled","os":"Windows 11"}'
  testForm.evidenceId = ''
}

async function submitTest(): Promise<void> {
  const target = testTarget.value
  if (!target || !testReady.value) return
  pending.value = 'test'
  error.value = ''
  try {
    await recordTemplateTest(target.id, {
      gateCode: testForm.gateCode,
      outcome: testForm.outcome,
      evidenceId: testForm.evidenceId,
      environmentJson: testForm.environmentJson,
      summary: testForm.summary.trim(),
      expectedVersion: target.version,
    })
    testTarget.value = null
    await loadVersions()
    notice.value = '门禁测试记录已追加'
  } catch (cause) {
    error.value = errorMessage(cause, '测试记录保存失败')
  } finally {
    pending.value = ''
  }
}

async function runConfirmedAction(): Promise<void> {
  const action = confirmAction.value
  if (!action) return
  pending.value = action.kind
  error.value = ''
  try {
    if (action.kind === 'publish') await publishTemplateVersion(action.version.id, action.version.version)
    else await retireTemplateVersion(action.version.id, action.version.version)
    confirmAction.value = null
    const nextFamilies = await listTemplateFamilies()
    families.value = nextFamilies
    await loadVersions()
    notice.value = action.kind === 'publish' ? '模板版本已发布' : '模板版本已退休'
  } catch (cause) {
    error.value = errorMessage(cause, action.kind === 'publish' ? '发布失败' : '退休失败')
  } finally {
    pending.value = ''
  }
}

async function downloadEvidence(item: TemplateEvidence): Promise<void> {
  pending.value = `download:${item.id}`
  error.value = ''
  try {
    await downloadTemplateEvidence(item.id)
  } catch (cause) {
    error.value = errorMessage(cause, '证据下载失败')
  } finally {
    pending.value = ''
  }
}

watch(
  () => reviewForm.licenseStatus,
  () => { reviewForm.licenseEvidenceId = '' },
)

watch(
  () => testForm.gateCode,
  () => { testForm.evidenceId = testEvidenceOptions.value[0]?.id ?? '' },
)

watch(testEvidenceOptions, (items) => {
  if (!items.some((item) => item.id === testForm.evidenceId)) testForm.evidenceId = items[0]?.id ?? ''
})

onMounted(() => {
  void loadAll()
  refreshTimer = window.setInterval(() => {
    if (importBatches.value.some((item) => item.status === 'QUEUED' || item.status === 'RUNNING')) {
      void refreshImports()
    }
  }, 3000)
})

onUnmounted(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<template>
  <section class="page-container admin-page">
      <UiPageHeader title="模板运营台" description="候选资产、权利证据、测试门禁与版本状态">
        <template #actions>
          <UiIconButton label="刷新" :icon="RefreshCw" variant="secondary" :pending="loading" @click="loadAll" />
        </template>
      </UiPageHeader>

      <div class="ops-summary" aria-label="模板运营状态">
        <div><strong>{{ families.length }}</strong><span>模板家族</span></div>
        <div><strong>{{ assets.filter((item) => item.status === 'REVIEWING').length }}</strong><span>待审批候选</span></div>
        <div><strong>{{ evidence.length }}</strong><span>不可变证据</span></div>
        <div><strong>{{ families.filter((item) => item.status === 'PUBLISHED').length }}</strong><span>已发布家族</span></div>
        <div>
          <strong :class="malwareScanner?.ready ? 'scanner-ready' : 'scanner-blocked'">
            {{ malwareScanner?.ready ? '正常' : '上传已暂停' }}
          </strong>
          <span :title="malwareScanner?.detailCode || undefined">{{ malwareScanner?.ready ? (malwareScanner?.engineVersion || '扫描器在线') : glossScannerDetail(malwareScanner?.detailCode) }}</span>
        </div>
      </div>

      <nav class="ops-tabs" :style="{ '--ops-tab-index': activeTabIndex }" aria-label="运营视图">
        <button type="button" :class="{ active: activeTab === 'families' }" :aria-current="activeTab === 'families' ? 'page' : undefined" @click="activeTab = 'families'">版本</button>
        <button type="button" :class="{ active: activeTab === 'assets' }" :aria-current="activeTab === 'assets' ? 'page' : undefined" @click="activeTab = 'assets'">候选资产</button>
        <button type="button" :class="{ active: activeTab === 'evidence' }" :aria-current="activeTab === 'evidence' ? 'page' : undefined" @click="activeTab = 'evidence'">证据</button>
        <button type="button" :class="{ active: activeTab === 'imports' }" :aria-current="activeTab === 'imports' ? 'page' : undefined" @click="activeTab = 'imports'">全量批次</button>
        <button type="button" :class="{ active: activeTab === 'catalog' }" :aria-current="activeTab === 'catalog' ? 'page' : undefined" @click="activeTab = 'catalog'">公共目录</button>
      </nav>

      <Transition name="ops-panel-swap" mode="out-in">
      <div v-if="loading" key="loading" class="ops-loading" aria-busy="true">
        <UiSkeleton v-for="index in 5" :key="index" />
      </div>

      <section v-else-if="activeTab === 'imports'" key="imports" class="ops-panel">
        <header class="panel-toolbar">
          <div><h2>固定目录导入</h2><p>仅处理服务器配置的 HICV 根目录；每项必须通过 OOXML、ClamAV 和去重门禁。</p></div>
          <UiButton :icon="Send" :pending="pending === 'batch-start'" @click="runBatchAction('start')">启动批次</UiButton>
        </header>
        <div class="batch-layout">
          <aside class="batch-list">
            <button v-for="batch in importBatches" :key="batch.id" type="button" :class="{ active: batch.id === selectedBatchId }" @click="chooseBatch(batch.id)">
              <span><strong>{{ batch.processedCount }} / {{ batch.totalCount }}</strong><small>{{ formatWhen(batch.createdAt) }}</small></span>
              <UiBadge :tone="statusTone(batch.status)"><span :title="batch.status">{{ glossStatus(batch.status) }}</span></UiBadge>
            </button>
          </aside>
          <div v-if="importBatches.find((item) => item.id === selectedBatchId)" class="batch-detail">
            <template v-for="batch in importBatches.filter((item) => item.id === selectedBatchId)" :key="batch.id">
              <div class="batch-metrics"><span><strong>{{ batch.readyCount }}</strong>就绪</span><span><strong>{{ batch.duplicateCount }}</strong>重复</span><span><strong>{{ batch.rejectedCount }}</strong>拒绝</span><span><strong>{{ batch.quarantinedCount }}</strong>隔离</span><span><strong>{{ batch.unsupportedCount }}</strong>不支持</span></div>
              <div class="row-actions"><UiButton v-if="batch.status === 'QUEUED' || batch.status === 'RUNNING'" variant="secondary" size="sm" :icon="Clock" :pending="pending === 'batch-pause'" @click="runBatchAction('pause', batch)">暂停</UiButton><UiButton v-if="batch.status === 'PAUSED' || batch.status === 'FAILED' || batch.quarantinedCount" variant="secondary" size="sm" :icon="RefreshCw" :pending="pending === 'batch-retry'" @click="runBatchAction('retry', batch)">重试失败项</UiButton></div>
            </template>
            <div class="table-scroll"><table class="data-table"><thead><tr><th>源文件</th><th>大小</th><th>状态</th><th>原因</th><th>SHA-256</th></tr></thead><tbody><tr v-for="item in importItems" :key="item.id"><td><strong>{{ item.originalFilename }}</strong><small :title="item.sourceRelativePath">{{ item.sourceRelativePath }}</small></td><td>{{ fileSize(item.sizeBytes) }}</td><td><UiBadge :tone="statusTone(item.status)"><span :title="item.status">{{ glossStatus(item.status) }}</span></UiBadge></td><td>{{ item.detailCode || '—' }}</td><td><code v-if="item.fileHash" :title="item.fileHash">{{ shortHash(item.fileHash) }}</code><span v-else>—</span></td></tr></tbody></table></div>
          </div>
          <UiEmptyState v-else size="sm" title="尚未创建导入批次" illustration="empty-search" />
        </div>
      </section>

      <section v-else-if="activeTab === 'catalog'" key="catalog" class="ops-panel">
        <div class="table-scroll"><table class="data-table"><thead><tr><th>目录项</th><th>能力</th><th>频道</th><th>状态</th><th>下载</th><th>操作</th></tr></thead><tbody><tr v-for="entry in catalogEntries" :key="entry.item.id"><td><strong>{{ entry.item.title }}</strong><small>{{ entry.item.sourceName }} · {{ entry.item.referenceId }}</small></td><td>{{ glossCapability(entry.item.capability) }}</td><td>{{ glossAssetKind(entry.item.assetKind) }}</td><td><UiBadge :tone="statusTone(entry.publicationStatus)"><span :title="entry.publicationStatus">{{ glossStatus(entry.publicationStatus) }}</span></UiBadge></td><td>{{ entry.item.downloadCount }}</td><td><div class="row-actions"><UiIconButton label="校正元数据" :icon="Pencil" @click="openCatalog(entry)" /><UiIconButton v-if="entry.publicationStatus !== 'PUBLISHED'" label="发布" :icon="Send" :pending="pending === `catalog-${entry.item.id}`" @click="setCatalogPublication(entry, true)" /><UiIconButton v-else label="退休" :icon="Archive" :pending="pending === `catalog-${entry.item.id}`" @click="setCatalogPublication(entry, false)" /></div></td></tr></tbody></table></div>
        <UiEmptyState v-if="!catalogEntries.length" size="sm" title="公共目录尚无条目" illustration="empty-search" />
        <div v-else class="catalog-more">
          <span>已显示 {{ catalogEntries.length }} / 共 {{ catalogTotal }} 条</span>
          <UiButton v-if="catalogEntries.length < catalogTotal" variant="secondary" :pending="pending === 'catalog-more'" @click="loadMoreCatalog">加载更多</UiButton>
        </div>
      </section>

      <section v-else-if="activeTab === 'evidence'" key="evidence" class="ops-panel">
        <form class="upload-grid" @submit.prevent="submitEvidence">
          <UiField id="evidence-type" label="证据类型">
            <UiSelect id="evidence-type" v-model="evidenceForm.evidenceType" aria-label="证据类型" placeholder="证据类型">
              <option v-for="[value, label] in EVIDENCE_TYPES" :key="value" :value="value">{{ label }}</option>
            </UiSelect>
          </UiField>
          <UiField id="evidence-description" label="结论说明">
            <UiInput id="evidence-description" v-model="evidenceForm.description" maxlength="512" required />
          </UiField>
          <UiField id="evidence-file" label="证据文件">
            <input id="evidence-file" ref="evidenceInput" class="file-input" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.json" required @change="selectFile($event, 'evidence')" />
          </UiField>
          <UiButton class="form-submit" type="submit" :icon="Upload" :pending="pending === 'evidence'" :disabled="!evidenceFile || !evidenceForm.description.trim()">登记证据</UiButton>
        </form>

        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>类型</th><th>文件</th><th>说明</th><th>指纹</th><th>登记时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in evidence" :key="item.id">
                <td><UiBadge tone="primary">{{ evidenceLabel(item.evidenceType) }}</UiBadge></td>
                <td><strong>{{ item.originalFilename }}</strong><small>{{ fileSize(item.sizeBytes) }}</small></td>
                <td class="description-cell">{{ item.description }}</td>
                <td><code :title="item.fileHash">{{ shortHash(item.fileHash) }}</code></td>
                <td>{{ formatWhen(item.createdAt) }}</td>
                <td>
                  <UiIconButton label="下载证据" :icon="Download" :pending="pending === `download:${item.id}`" @click="downloadEvidence(item)" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <UiEmptyState v-if="!evidence.length" size="sm" title="暂无不可变证据" illustration="empty-folder" />
      </section>

      <section v-else-if="activeTab === 'assets'" key="assets" class="ops-panel">
        <form class="asset-upload" @submit.prevent="submitAsset">
          <UiField id="asset-file" label="候选 DOCX">
            <input id="asset-file" ref="assetInput" class="file-input" type="file" accept=".docx" required @change="selectFile($event, 'asset')" />
          </UiField>
          <UiField id="asset-source" label="来源 URI">
            <UiInput id="asset-source" v-model="assetForm.sourceUri" maxlength="1024" required />
          </UiField>
          <UiField id="asset-name" label="来源名称">
            <UiInput id="asset-name" v-model="assetForm.sourceName" maxlength="255" />
          </UiField>
          <UiField id="asset-license" label="权利状态">
            <UiSelect id="asset-license" v-model="assetForm.licenseStatus" aria-label="权利状态" placeholder="权利状态">
              <option value="UNCONFIRMED">未确认</option>
              <option value="APPROVED">商业授权</option>
              <option value="INDEPENDENT_DESIGN">独立设计</option>
            </UiSelect>
          </UiField>
          <UiField v-if="assetForm.licenseStatus !== 'UNCONFIRMED'" id="asset-license-evidence" label="权利证据">
            <UiSelect id="asset-license-evidence" v-model="assetForm.licenseEvidenceId" aria-label="权利证据" placeholder="权利证据" required searchable search-placeholder="搜索权利证据">
              <option value="">请选择</option>
              <option v-for="item in evidence.filter((entry) => entry.evidenceType === (assetForm.licenseStatus === 'APPROVED' ? 'LICENSE' : 'INDEPENDENT_DESIGN'))" :key="item.id" :value="item.id">
                {{ item.description }}
              </option>
            </UiSelect>
          </UiField>
          <UiButton class="form-submit" type="submit" :icon="Upload" :pending="pending === 'asset'" :disabled="!assetFile || !assetForm.sourceUri.trim()">导入候选</UiButton>
        </form>

        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>候选</th><th>安全扫描</th><th>权利</th><th>状态</th><th>指纹</th><th>时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in assets" :key="item.id">
                <td><strong>{{ item.sourceName }}</strong><small :title="item.sourceUri">{{ item.sourceUri }}</small></td>
                <td><UiBadge :tone="statusTone(item.scanStatus)"><span :title="item.scanStatus">{{ glossStatus(item.scanStatus) }}</span></UiBadge></td>
                <td :title="item.licenseStatus">{{ glossStatus(item.licenseStatus) }}</td>
                <td><UiBadge :tone="statusTone(item.status)"><span :title="item.status">{{ glossStatus(item.status) }}</span></UiBadge><small v-if="item.rejectionReason">{{ item.rejectionReason }}</small></td>
                <td><code :title="item.fileHash">{{ shortHash(item.fileHash) }}</code></td>
                <td>{{ formatWhen(item.createdAt) }}</td>
                <td>
                  <div v-if="item.status === 'REVIEWING' || canRescan(item)" class="row-actions">
                    <UiButton v-if="canRescan(item)" variant="secondary" size="sm" :icon="RefreshCw" :pending="pending === `rescan-${item.id}`" @click="rescanAsset(item)">复扫</UiButton>
                    <UiButton v-if="item.status === 'REVIEWING' && item.scanStatus === 'PASSED'" variant="secondary" size="sm" :icon="Check" @click="openReview(item, 'APPROVED')">批准</UiButton>
                    <UiButton v-if="item.status === 'REVIEWING'" variant="link" size="sm" :icon="X" @click="openReview(item, 'REJECTED')">驳回</UiButton>
                  </div>
                  <span v-else class="fine">已完成</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <UiEmptyState v-if="!assets.length" size="sm" title="暂无候选资产" illustration="empty-resume" />
      </section>

      <section v-else key="families" class="version-layout">
        <aside class="family-list" aria-label="模板家族">
          <button v-for="item in families" :key="item.id" type="button" :class="{ active: item.id === selectedFamilyId }" @click="chooseFamily(item.id)">
            <span><strong>{{ item.displayName }}</strong><small>{{ item.familyName }} · {{ item.recommendedPages }} 页</small></span>
            <UiBadge :tone="statusTone(item.status)"><span :title="item.status">{{ glossStatus(item.status) }}</span></UiBadge>
          </button>
        </aside>

        <div class="version-workspace">
          <header class="workspace-head">
            <div>
              <h2>{{ selectedFamily?.displayName }}</h2>
              <p class="fine"><code>{{ selectedFamily?.id }}</code> · {{ selectedFamily?.languageCode }} · {{ selectedFamily?.atsCandidateLevel }}</p>
            </div>
            <UiButton :icon="Plus" :disabled="!canCreateDraft" @click="openDraft()">新建修订</UiButton>
          </header>

          <div v-if="versionsLoading" class="ops-loading" aria-busy="true"><UiSkeleton v-for="index in 3" :key="index" /></div>
          <div v-else-if="versions.length" class="version-list">
            <article v-for="version in versions" :key="version.id" class="version-row">
              <div class="version-row__main">
                <span class="revision">R{{ version.revisionNo }}</span>
                <div>
                  <strong>{{ version.rendererProtocol }}</strong>
                  <small>{{ provenanceLabel(version) }} · 对象版本 {{ version.version }}</small>
                </div>
                <UiBadge :tone="statusTone(version.status)"><span :title="version.status">{{ glossStatus(version.status) }}</span></UiBadge>
              </div>
              <div class="gate-strip" aria-label="发布门禁">
                <span v-for="gate in GATES" :key="gate.code" :class="{ passed: gatePassed(version, gate.field) }">
                  <AppIcon :name="gatePassed(version, gate.field) ? 'check-circle' : 'clock'" :size="13" />{{ gate.label }}
                </span>
              </div>
              <div class="version-row__actions">
                <UiButton v-if="version.status === 'DRAFT'" variant="secondary" size="sm" :icon="Pencil" @click="openDraft(version)">编辑</UiButton>
                <UiButton v-if="version.status === 'DRAFT' || version.status === 'TESTING'" variant="secondary" size="sm" :icon="CheckCircle2" @click="openTest(version)">记录测试</UiButton>
                <UiButton v-if="version.status === 'TESTING'" size="sm" :icon="Send" @click="confirmAction = { kind: 'publish', version }">发布</UiButton>
                <UiButton v-if="version.status === 'PUBLISHED'" variant="danger" size="sm" :icon="Archive" @click="confirmAction = { kind: 'retire', version }">退休</UiButton>
              </div>
            </article>
          </div>
          <UiEmptyState v-else size="sm" title="该家族暂无版本" illustration="empty-resume">
            <UiButton :disabled="!canCreateDraft" @click="openDraft()">新建修订</UiButton>
          </UiEmptyState>
        </div>
      </section>
      </Transition>
    </section>

    <UiDialog :open="Boolean(catalogTarget)" title="校正目录元数据" :width="760" @close="catalogTarget = null">
      <div class="modal-form">
        <div class="form-grid-2"><UiField id="catalog-title" label="展示标题"><UiInput id="catalog-title" v-model="catalogForm.title" maxlength="512" required /></UiField><UiField id="catalog-kind" label="素材频道"><UiSelect id="catalog-kind" v-model="catalogForm.assetKind" aria-label="素材频道" placeholder="素材频道"><option value="RESUME">求职简历</option><option value="COVER">封面</option><option value="SCHOOL_APPLICATION">升学材料</option><option value="COVER_LETTER">自荐信/范文</option></UiSelect></UiField></div>
        <UiField id="catalog-summary" label="摘要"><UiTextarea id="catalog-summary" v-model="catalogForm.summary" maxlength="1024" /></UiField>
        <div class="form-grid-2"><UiField id="catalog-language" label="语言"><UiSelect id="catalog-language" v-model="catalogForm.languageCode" aria-label="语言" placeholder="语言"><option value="zh-CN">中文</option><option value="en">英文</option><option value="mixed">中英文</option></UiSelect></UiField><UiField id="catalog-pages" label="页数"><UiInput id="catalog-pages" v-model="catalogForm.pageCount" maxlength="16" /></UiField><UiField id="catalog-photo" label="照片"><UiSelect id="catalog-photo" v-model="catalogForm.photoPolicy" aria-label="照片" placeholder="照片"><option value="UNSPECIFIED">未识别</option><option value="DISABLED">无照片</option><option value="OPTIONAL">照片可选</option><option value="REQUIRED">含照片</option></UiSelect></UiField><UiField id="catalog-thumbnail" label="缩略图 URI"><UiInput id="catalog-thumbnail" v-model="catalogForm.thumbnailUri" maxlength="1024" /></UiField></div>
        <p class="fine">标签每行使用“编码|显示名”。职业大类、岗位、风格和职业阶段可分别维护。</p>
        <div class="form-grid-2"><UiField id="catalog-occupations" label="职业大类"><UiTextarea id="catalog-occupations" v-model="catalogForm.occupations" class="code-editor code-editor--small" /></UiField><UiField id="catalog-jobs" label="岗位标签"><UiTextarea id="catalog-jobs" v-model="catalogForm.jobs" class="code-editor code-editor--small" /></UiField><UiField id="catalog-styles" label="版式风格"><UiTextarea id="catalog-styles" v-model="catalogForm.styles" class="code-editor code-editor--small" /></UiField><UiField id="catalog-stages" label="职业阶段"><UiTextarea id="catalog-stages" v-model="catalogForm.careerStages" class="code-editor code-editor--small" /></UiField></div>
      </div>
      <template #footer><UiButton variant="secondary" @click="catalogTarget = null">取消</UiButton><UiButton :pending="pending === 'catalog-save'" :disabled="!catalogForm.title.trim()" @click="saveCatalog">保存校正</UiButton></template>
    </UiDialog>

    <UiDialog :open="Boolean(reviewTarget)" :title="reviewForm.decision === 'APPROVED' ? '批准候选资产' : '驳回候选资产'" :width="520" @close="reviewTarget = null">
      <div class="modal-form">
        <p class="object-line"><strong>{{ reviewTarget?.sourceName }}</strong><code>{{ reviewTarget?.fileHash }}</code></p>
        <template v-if="reviewForm.decision === 'APPROVED'">
          <UiField id="review-license" label="权利结论">
            <UiSelect id="review-license" v-model="reviewForm.licenseStatus" aria-label="权利结论" placeholder="权利结论">
              <option value="APPROVED">商业授权</option>
              <option value="INDEPENDENT_DESIGN">独立设计</option>
            </UiSelect>
          </UiField>
          <UiField id="review-evidence" label="不可变权利证据">
            <UiSelect id="review-evidence" v-model="reviewForm.licenseEvidenceId" aria-label="不可变权利证据" placeholder="不可变权利证据" required searchable search-placeholder="搜索权利证据">
              <option value="">请选择</option>
              <option v-for="item in licenseEvidence" :key="item.id" :value="item.id">{{ item.description }}</option>
            </UiSelect>
          </UiField>
        </template>
        <UiField v-else id="review-reason" label="驳回原因">
          <UiTextarea id="review-reason" v-model="reviewForm.reason" maxlength="1024" required />
        </UiField>
      </div>
      <template #footer>
        <UiButton variant="secondary" @click="reviewTarget = null">取消</UiButton>
        <UiButton :variant="reviewForm.decision === 'APPROVED' ? 'primary' : 'danger'" :pending="pending === 'review'" :disabled="!reviewReady" @click="submitReview">
          {{ reviewForm.decision === 'APPROVED' ? '确认批准' : '确认驳回' }}
        </UiButton>
      </template>
    </UiDialog>

    <UiDialog :open="draftOpen" :title="editingVersion ? `编辑 R${editingVersion.revisionNo}` : '新建模板修订'" :width="760" @close="draftOpen = false">
      <div class="modal-form">
        <div class="form-grid-2">
          <UiField id="draft-protocol" label="渲染协议">
            <UiInput id="draft-protocol" v-model="draftForm.rendererProtocol" required />
          </UiField>
          <UiField id="draft-thumbnail" label="缩略图 URI">
            <UiInput id="draft-thumbnail" v-model="draftForm.thumbnailUri" />
          </UiField>
        </div>
        <div class="source-switch" :class="{ 'is-independent': draftSourceMode === 'independent' }" aria-label="来源类型">
          <button type="button" :class="{ active: draftSourceMode === 'asset' }" :disabled="provenanceLocked" @click="draftSourceMode = 'asset'">已批准候选</button>
          <button type="button" :class="{ active: draftSourceMode === 'independent' }" :disabled="provenanceLocked" @click="draftSourceMode = 'independent'">独立设计</button>
        </div>
        <Transition name="source-field" mode="out-in">
        <UiField v-if="draftSourceMode === 'asset'" id="draft-asset" key="asset" label="来源资产">
          <UiSelect id="draft-asset" v-model="draftForm.sourceAssetId" aria-label="来源资产" placeholder="来源资产" required searchable search-placeholder="搜索来源资产">
            <option value="">请选择</option>
            <option v-for="item in approvedAssets" :key="item.id" :value="item.id">{{ item.sourceName }} · {{ shortHash(item.fileHash) }}</option>
          </UiSelect>
        </UiField>
        <UiField v-else id="draft-independent" key="independent" label="独立设计证据">
          <UiSelect id="draft-independent" v-model="draftForm.independentDesignEvidenceId" aria-label="独立设计证据" placeholder="独立设计证据" required searchable search-placeholder="搜索独立设计证据">
            <option value="">请选择</option>
            <option v-for="item in independentEvidence" :key="item.id" :value="item.id">{{ item.description }}</option>
          </UiSelect>
        </UiField>
        </Transition>
        <UiField id="draft-definition" label="受控版式定义">
          <UiTextarea id="draft-definition" v-model="draftForm.definitionJson" class="code-editor" required />
        </UiField>
      </div>
      <template #footer>
        <UiButton variant="secondary" @click="draftOpen = false">取消</UiButton>
        <UiButton :pending="pending === 'draft'" :disabled="!draftReady" @click="submitDraft">保存修订</UiButton>
      </template>
    </UiDialog>

    <UiDialog :open="Boolean(testTarget)" title="记录发布门禁" :width="620" @close="testTarget = null">
      <div class="modal-form">
        <div class="form-grid-2">
          <UiField id="test-gate" label="门禁">
            <UiSelect id="test-gate" v-model="testForm.gateCode" aria-label="门禁" placeholder="门禁">
              <option v-for="gate in GATES" :key="gate.code" :value="gate.code">{{ gate.label }}</option>
            </UiSelect>
          </UiField>
          <UiField id="test-outcome" label="结论">
            <UiSelect id="test-outcome" v-model="testForm.outcome" aria-label="结论" placeholder="结论">
              <option value="PASSED">通过</option>
              <option value="FAILED">失败</option>
            </UiSelect>
          </UiField>
        </div>
        <UiField id="test-evidence" label="不可变证据">
          <UiSelect id="test-evidence" v-model="testForm.evidenceId" aria-label="不可变证据" placeholder="不可变证据" required searchable search-placeholder="搜索证据">
            <option value="">请选择</option>
            <option v-for="item in testEvidenceOptions" :key="item.id" :value="item.id">{{ item.description }}</option>
          </UiSelect>
        </UiField>
        <UiField id="test-summary" label="测试结论摘要">
          <UiTextarea id="test-summary" v-model="testForm.summary" maxlength="2048" required />
        </UiField>
        <UiField id="test-environment" label="环境 JSON">
          <UiTextarea id="test-environment" v-model="testForm.environmentJson" class="code-editor code-editor--small" required />
        </UiField>
      </div>
      <template #footer>
        <UiButton variant="secondary" @click="testTarget = null">取消</UiButton>
        <UiButton :pending="pending === 'test'" :disabled="!testReady" @click="submitTest">追加记录</UiButton>
      </template>
    </UiDialog>

    <UiDialog :open="Boolean(confirmAction)" :title="confirmAction?.kind === 'publish' ? '发布模板版本' : '退休模板版本'" :width="520" :mobile-sheet="false" @close="confirmAction = null">
      <p class="confirm-copy">
        {{ confirmAction?.kind === 'publish' ? '发布后版本内容不可原地修改。' : '退休后禁止新选择，历史冻结快照保持可用。' }}
      </p>
      <template #footer>
        <UiButton variant="secondary" @click="confirmAction = null">取消</UiButton>
        <UiButton :variant="confirmAction?.kind === 'publish' ? 'primary' : 'danger'" :pending="pending === confirmAction?.kind" @click="runConfirmedAction">
          确认{{ confirmAction?.kind === 'publish' ? '发布' : '退休' }}
        </UiButton>
      </template>
    </UiDialog>
</template>

<style scoped>
.admin-page{display:grid;gap:var(--space-6);align-content:start;max-width:1540px;margin:0 auto}.ops-summary{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));background:var(--surface-1);border:1px solid var(--border-default);border-radius:var(--radius-md);overflow:hidden}.ops-summary>div{min-height:82px;padding:16px 20px;display:grid;align-content:center;border-right:1px solid var(--border-default)}.ops-summary>div:last-child{border-right:0}.ops-summary strong{font-size:24px;line-height:1.15}.ops-summary span{font-size:12px;color:var(--text-tertiary)}.scanner-ready{color:var(--color-success)}.scanner-blocked{color:var(--color-danger)}.ops-tabs,.source-switch{display:inline-flex;width:max-content;padding:3px;background:var(--surface-3);border-radius:var(--radius-md)}.ops-tabs button,.source-switch button{height:32px;padding:0 15px;border:0;border-radius:6px;background:transparent;color:var(--text-secondary);font-size:13px}.ops-tabs button.active,.source-switch button.active{background:var(--surface-1);color:var(--text-primary);box-shadow:var(--shadow-sm);font-weight:600}.source-switch button:disabled{cursor:not-allowed;opacity:.7}.ops-loading{display:grid;gap:12px;padding:24px;background:var(--surface-1);border:1px solid var(--border-default);border-radius:var(--radius-md)}.ops-panel{display:grid;gap:18px;background:var(--surface-1);border:1px solid var(--border-default);border-radius:var(--radius-md);overflow:hidden}.upload-grid{display:grid;grid-template-columns:180px minmax(220px,1fr) minmax(240px,1fr) auto;gap:12px;align-items:end;padding:18px;border-bottom:1px solid var(--border-default)}.asset-upload{display:grid;grid-template-columns:repeat(4,minmax(160px,1fr));gap:12px;align-items:end;padding:18px;border-bottom:1px solid var(--border-default)}.asset-upload>.form-submit{justify-self:start}.table-scroll{overflow:auto}.data-table td{max-width:280px}.data-table td strong,.data-table td small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.data-table td small{color:var(--text-tertiary);font-size:12px;margin-top:2px}.description-cell{min-width:220px}.row-actions{display:flex;gap:4px}.version-layout{display:grid;grid-template-columns:300px minmax(0,1fr);gap:16px;align-items:start}.family-list{display:grid;background:var(--surface-1);border:1px solid var(--border-default);border-radius:var(--radius-md);overflow:hidden;max-height:calc(100vh - 260px);overflow-y:auto}.family-list>button{min-height:64px;border:0;border-bottom:1px solid var(--border-default);background:var(--surface-1);padding:10px 12px;display:flex;align-items:center;gap:10px;text-align:left;color:var(--text-primary)}.family-list>button:last-child{border-bottom:0}.family-list>button:hover{background:var(--surface-2)}.family-list>button.active{background:var(--color-primary-soft);box-shadow:inset 3px 0 var(--color-primary)}.family-list>button>span:first-child{display:grid;flex:1;min-width:0}.family-list strong,.family-list small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.family-list small{font-size:12px;color:var(--text-tertiary)}.version-workspace{min-width:0;background:var(--surface-1);border:1px solid var(--border-default);border-radius:var(--radius-md);overflow:hidden}.workspace-head{min-height:76px;padding:14px 18px;border-bottom:1px solid var(--border-default);display:flex;align-items:center;justify-content:space-between;gap:16px}.workspace-head h2{font-size:17px}.version-list{display:grid}.version-row{display:grid;gap:12px;padding:16px 18px;border-bottom:1px solid var(--border-default)}.version-row:last-child{border-bottom:0}.version-row__main{display:flex;align-items:center;gap:12px}.version-row__main>div{display:grid;flex:1;min-width:0}.version-row__main small{font-size:12px;color:var(--text-tertiary)}.revision{width:42px;height:32px;border-radius:6px;background:var(--surface-2);display:grid;place-items:center;font-family:var(--font-mono);font-size:12px;font-weight:600}.gate-strip{display:flex;flex-wrap:wrap;gap:6px}.gate-strip span{display:inline-flex;align-items:center;gap:4px;height:26px;padding:0 8px;border:1px solid var(--border-default);border-radius:6px;color:var(--text-tertiary);font-size:12px}.gate-strip span.passed{border-color:var(--border-default);background:var(--color-success-soft);color:var(--color-success)}.version-row__actions{display:flex;flex-wrap:wrap;gap:6px}.modal-form{display:grid;gap:16px}.form-grid-2{display:grid;grid-template-columns:1fr 1fr;gap:12px}.modal-form .code-editor{min-height:280px;font-family:var(--font-mono);font-size:12px;white-space:pre;overflow:auto}.modal-form .code-editor--small{min-height:100px}.object-line{display:grid;gap:4px;padding:10px 12px;background:var(--surface-2);border:1px solid var(--border-default);border-radius:var(--radius-md)}.object-line code{word-break:break-all;color:var(--text-tertiary)}.confirm-copy{color:var(--text-secondary)}@media(max-width:1100px){.ops-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.ops-summary>div{border-right:1px solid var(--border-default);border-bottom:1px solid var(--border-default)}.ops-summary>div:nth-child(2n){border-right:0}.ops-summary>div:last-child{grid-column:1/-1;border-right:0;border-bottom:0}.upload-grid,.asset-upload{grid-template-columns:repeat(2,minmax(0,1fr))}.version-layout{grid-template-columns:240px minmax(0,1fr)}}@media(max-width:760px){.admin-page{padding:18px 14px 40px}.upload-grid,.asset-upload,.version-layout,.form-grid-2{grid-template-columns:1fr}.family-list{max-height:300px}.workspace-head{align-items:flex-start;flex-direction:column}.upload-grid>.form-submit,.asset-upload>.form-submit{width:100%}.table-scroll{max-width:calc(100vw - 92px)}}
.panel-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px;border-bottom:1px solid var(--border-default)}.panel-toolbar h2{font-size:16px}.panel-toolbar p{margin-top:3px;color:var(--text-tertiary);font-size:12px}.batch-layout{display:grid;grid-template-columns:250px minmax(0,1fr);min-height:420px}.batch-list{display:grid;align-content:start;border-right:1px solid var(--border-default)}.batch-list button{min-height:58px;display:flex;align-items:center;gap:8px;padding:10px 12px;border:0;border-bottom:1px solid var(--border-default);background:var(--surface-1);color:var(--text-primary);text-align:left}.batch-list button.active{background:var(--color-primary-soft);box-shadow:inset 3px 0 var(--color-primary)}.batch-list button>span{min-width:0;display:grid;flex:1}.batch-list small{color:var(--text-tertiary);font-size:12px}.batch-detail{min-width:0;display:grid;align-content:start;gap:12px;padding:14px}.batch-metrics{display:grid;grid-template-columns:repeat(5,minmax(80px,1fr));border:1px solid var(--border-default)}.batch-metrics span{display:grid;padding:10px 12px;border-right:1px solid var(--border-default);color:var(--text-tertiary);font-size:12px}.batch-metrics span:last-child{border-right:0}.batch-metrics strong{color:var(--text-primary);font-size:18px}@media(max-width:900px){.batch-layout{grid-template-columns:1fr}.batch-list{grid-template-columns:repeat(2,minmax(0,1fr));border-right:0;border-bottom:1px solid var(--border-default)}.batch-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.panel-toolbar{align-items:stretch;flex-direction:column}.batch-list,.batch-metrics{grid-template-columns:1fr}.ops-tabs{max-width:100%;overflow-x:auto}}
@media(max-width:760px){.admin-page{grid-template-columns:minmax(0,1fr)}.admin-page>*{min-width:0}.ops-summary>div{min-width:0}.ops-summary span,.admin-page :deep(.ui-page-header__desc){overflow-wrap:anywhere}}

/* Native file picker, drawn like the UiInput beside it. */
.file-input {
  width: 100%;
  height: var(--control-md);
  padding: 4px 8px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: var(--text-primary);
  font-size: var(--fs-sm);
  box-shadow: var(--shadow-xs);
  transition: border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-base) var(--ease-out);
}
.file-input:hover {
  border-color: var(--border-strong);
}
.file-input:focus-visible {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: var(--focus-ring);
}
.file-input::file-selector-button {
  height: 100%;
  margin-right: var(--space-2);
  padding: 0 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  color: var(--text-primary);
  font: inherit;
  cursor: pointer;
}

.catalog-more {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 18px 16px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.ops-tabs {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(84px, 1fr));
  isolation: isolate;
}
.ops-tabs::before {
  position: absolute;
  z-index: 0;
  inset: 3px auto 3px 3px;
  width: calc((100% - 6px) / 5);
  border-radius: 6px;
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  content: '';
  transform: translateX(calc(var(--ops-tab-index) * 100%));
  transition: transform var(--dur-slow) var(--ease-standard);
}
.ops-tabs button,
.source-switch button {
  position: relative;
  z-index: 1;
  transition: color var(--dur-base) ease, transform var(--dur-fast) var(--ease-standard);
}
.ops-tabs button:active,
.source-switch button:active:not(:disabled) { transform: scale(.97); }
.ops-tabs button.active,
.source-switch button.active {
  background: transparent;
  box-shadow: none;
}
.ops-panel-swap-enter-active,
.ops-panel-swap-leave-active,
.source-field-enter-active,
.source-field-leave-active {
  transition: opacity var(--dur-base) ease, transform var(--dur-base) var(--ease-standard);
}
.ops-panel-swap-enter-from,
.source-field-enter-from { opacity: 0; transform: translateY(7px); }
.ops-panel-swap-leave-to,
.source-field-leave-to { opacity: 0; transform: translateY(-3px); }
.family-list > button,
.batch-list button {
  transition: background-color var(--dur-base) ease, box-shadow var(--dur-base) ease, color var(--dur-fast) ease;
}
.version-list,
.batch-detail { animation: ops-detail-in var(--dur-base) var(--ease-out) both; }
@keyframes ops-detail-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}
.source-switch {
  position: relative;
  isolation: isolate;
}
.source-switch::before {
  position: absolute;
  z-index: 0;
  inset: 3px auto 3px 3px;
  width: calc((100% - 6px) / 2);
  border-radius: 6px;
  background: var(--surface-1);
  box-shadow: var(--shadow-sm);
  content: '';
  transition: transform var(--dur-slow) var(--ease-standard);
}
.source-switch.is-independent::before { transform: translateX(100%); }
@media (prefers-reduced-motion: reduce) {
  .ops-tabs::before,
  .ops-tabs button,
  .source-switch::before,
  .source-switch button,
  .ops-panel-swap-enter-active,
  .ops-panel-swap-leave-active,
  .source-field-enter-active,
  .source-field-leave-active,
  .family-list > button,
  .batch-list button { transition: none; }
  .version-list,
  .batch-detail { animation: none; }
}
</style>
