<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { errorMessage } from '@/shared/api/types'
import { formatWhen } from '@/shared/lib/datetime'
import AppButton from '@/shared/ui/AppButton.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppField from '@/shared/ui/AppField.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
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

function statusTone(status: string): 'green' | 'orange' | 'red' | 'blue' | 'gray' {
  if (status === 'APPROVED' || status === 'PUBLISHED' || status === 'PASSED') return 'green'
  if (status === 'REJECTED' || status === 'STATIC_REJECTED' || status === 'MALWARE_DETECTED'
    || status === 'STORAGE_INTEGRITY_FAILED' || status === 'FAILED') return 'red'
  if (status === 'REVIEWING' || status === 'TESTING' || status === 'EXTERNAL_REQUIRED'
    || status === 'MALWARE_SCANNER_UNAVAILABLE' || status === 'MALWARE_SCANNER_ERROR') return 'orange'
  if (status === 'RETIRED') return 'gray'
  return 'blue'
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
    await updateCatalogEntry(target.item.id, {
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
    catalogEntries.value = (await listCatalogEntries()).items
    notice.value = '目录元数据已校正'
  } catch (cause) {
    error.value = errorMessage(cause, '目录保存失败')
  } finally {
    pending.value = ''
  }
}

async function setCatalogPublication(item: CatalogAdminItem, publish: boolean): Promise<void> {
  pending.value = `catalog-${item.item.id}`
  error.value = ''
  try {
    if (publish) await publishCatalogEntry(item.item.id, item.version)
    else await retireCatalogEntry(item.item.id, item.version)
    catalogEntries.value = (await listCatalogEntries()).items
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
      <section class="page admin-page">
      <header class="page-head">
        <div>
          <h1 class="page-head__title">模板运营台</h1>
          <p class="page-head__sub">候选资产、权利证据、测试门禁与版本状态</p>
        </div>
        <button class="icon-btn" type="button" title="刷新" :disabled="loading" @click="loadAll">
          <AppIcon name="refresh" :size="18" />
        </button>
      </header>

      <div class="ops-summary" aria-label="模板运营状态">
        <div><strong>{{ families.length }}</strong><span>模板家族</span></div>
        <div><strong>{{ assets.filter((item) => item.status === 'REVIEWING').length }}</strong><span>待审批候选</span></div>
        <div><strong>{{ evidence.length }}</strong><span>不可变证据</span></div>
        <div><strong>{{ families.filter((item) => item.status === 'PUBLISHED').length }}</strong><span>已发布家族</span></div>
        <div>
          <strong :class="malwareScanner?.ready ? 'scanner-ready' : 'scanner-blocked'">
            {{ malwareScanner?.ready ? '正常' : '阻断' }}
          </strong>
          <span>{{ malwareScanner?.engineVersion || malwareScanner?.detailCode || '扫描器不可用' }}</span>
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
        <span v-for="index in 5" :key="index" class="bone" />
      </div>

      <section v-else-if="activeTab === 'imports'" key="imports" class="ops-panel">
        <header class="panel-toolbar">
          <div><h2>固定目录导入</h2><p>仅处理服务器配置的 HICV 根目录；每项必须通过 OOXML、ClamAV 和去重门禁。</p></div>
          <AppButton :pending="pending === 'batch-start'" @click="runBatchAction('start')"><AppIcon name="send" :size="15" />启动批次</AppButton>
        </header>
        <div class="batch-layout">
          <aside class="batch-list">
            <button v-for="batch in importBatches" :key="batch.id" type="button" :class="{ active: batch.id === selectedBatchId }" @click="chooseBatch(batch.id)">
              <span><strong>{{ batch.processedCount }} / {{ batch.totalCount }}</strong><small>{{ formatWhen(batch.createdAt) }}</small></span>
              <AppTag :tone="statusTone(batch.status)">{{ batch.status }}</AppTag>
            </button>
          </aside>
          <div v-if="importBatches.find((item) => item.id === selectedBatchId)" class="batch-detail">
            <template v-for="batch in importBatches.filter((item) => item.id === selectedBatchId)" :key="batch.id">
              <div class="batch-metrics"><span><strong>{{ batch.readyCount }}</strong>就绪</span><span><strong>{{ batch.duplicateCount }}</strong>重复</span><span><strong>{{ batch.rejectedCount }}</strong>拒绝</span><span><strong>{{ batch.quarantinedCount }}</strong>隔离</span><span><strong>{{ batch.unsupportedCount }}</strong>不支持</span></div>
              <div class="row-actions"><AppButton v-if="batch.status === 'QUEUED' || batch.status === 'RUNNING'" variant="ghost" @click="runBatchAction('pause', batch)"><AppIcon name="clock" :size="14" />暂停</AppButton><AppButton v-if="batch.status === 'PAUSED' || batch.status === 'FAILED' || batch.quarantinedCount" variant="ghost" @click="runBatchAction('retry', batch)"><AppIcon name="refresh" :size="14" />重试失败项</AppButton></div>
            </template>
            <div class="table-scroll"><table class="tbl"><thead><tr><th>源文件</th><th>大小</th><th>状态</th><th>原因</th><th>SHA-256</th></tr></thead><tbody><tr v-for="item in importItems" :key="item.id"><td><strong>{{ item.originalFilename }}</strong><small :title="item.sourceRelativePath">{{ item.sourceRelativePath }}</small></td><td>{{ fileSize(item.sizeBytes) }}</td><td><AppTag :tone="statusTone(item.status)">{{ item.status }}</AppTag></td><td>{{ item.detailCode || '—' }}</td><td><code v-if="item.fileHash" :title="item.fileHash">{{ shortHash(item.fileHash) }}</code><span v-else>—</span></td></tr></tbody></table></div>
          </div>
          <AppEmpty v-else text="尚未创建导入批次" icon="archive" />
        </div>
      </section>

      <section v-else-if="activeTab === 'catalog'" key="catalog" class="ops-panel">
        <div class="table-scroll"><table class="tbl"><thead><tr><th>目录项</th><th>能力</th><th>频道</th><th>状态</th><th>下载</th><th>操作</th></tr></thead><tbody><tr v-for="entry in catalogEntries" :key="entry.item.id"><td><strong>{{ entry.item.title }}</strong><small>{{ entry.item.sourceName }} · {{ entry.item.referenceId }}</small></td><td>{{ entry.item.capability }}</td><td>{{ entry.item.assetKind }}</td><td><AppTag :tone="statusTone(entry.publicationStatus)">{{ entry.publicationStatus }}</AppTag></td><td>{{ entry.item.downloadCount }}</td><td><div class="row-actions"><button class="icon-btn" type="button" title="校正元数据" @click="openCatalog(entry)"><AppIcon name="edit" :size="15" /></button><button v-if="entry.publicationStatus !== 'PUBLISHED'" class="icon-btn" type="button" title="发布" @click="setCatalogPublication(entry, true)"><AppIcon name="send" :size="15" /></button><button v-else class="icon-btn" type="button" title="退休" @click="setCatalogPublication(entry, false)"><AppIcon name="archive" :size="15" /></button></div></td></tr></tbody></table></div>
        <AppEmpty v-if="!catalogEntries.length" text="公共目录尚无条目" icon="book" />
      </section>

      <section v-else-if="activeTab === 'evidence'" key="evidence" class="ops-panel">
        <form class="upload-grid" @submit.prevent="submitEvidence">
          <AppField id="evidence-type" label="证据类型">
            <AppSelect id="evidence-type" v-model="evidenceForm.evidenceType" ariaLabel="证据类型">
              <option v-for="[value, label] in EVIDENCE_TYPES" :key="value" :value="value">{{ label }}</option>
            </AppSelect>
          </AppField>
          <AppField id="evidence-description" label="结论说明">
            <input id="evidence-description" v-model="evidenceForm.description" class="input" maxlength="512" required />
          </AppField>
          <AppField id="evidence-file" label="证据文件">
            <input id="evidence-file" ref="evidenceInput" class="input file-input" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.json" required @change="selectFile($event, 'evidence')" />
          </AppField>
          <AppButton type="submit" :pending="pending === 'evidence'" :disabled="!evidenceFile || !evidenceForm.description.trim()">
            <AppIcon name="upload" :size="15" />登记证据
          </AppButton>
        </form>

        <div class="table-scroll">
          <table class="tbl">
            <thead><tr><th>类型</th><th>文件</th><th>说明</th><th>指纹</th><th>登记时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in evidence" :key="item.id">
                <td><AppTag tone="blue">{{ evidenceLabel(item.evidenceType) }}</AppTag></td>
                <td><strong>{{ item.originalFilename }}</strong><small>{{ fileSize(item.sizeBytes) }}</small></td>
                <td class="description-cell">{{ item.description }}</td>
                <td><code :title="item.fileHash">{{ shortHash(item.fileHash) }}</code></td>
                <td>{{ formatWhen(item.createdAt) }}</td>
                <td>
                  <button class="icon-btn" type="button" title="下载证据" :disabled="pending === `download:${item.id}`" @click="downloadEvidence(item)">
                    <AppIcon name="download" :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppEmpty v-if="!evidence.length" text="暂无不可变证据" icon="folder" />
      </section>

      <section v-else-if="activeTab === 'assets'" key="assets" class="ops-panel">
        <form class="asset-upload" @submit.prevent="submitAsset">
          <AppField id="asset-file" label="候选 DOCX">
            <input id="asset-file" ref="assetInput" class="input file-input" type="file" accept=".docx" required @change="selectFile($event, 'asset')" />
          </AppField>
          <AppField id="asset-source" label="来源 URI">
            <input id="asset-source" v-model="assetForm.sourceUri" class="input" maxlength="1024" required />
          </AppField>
          <AppField id="asset-name" label="来源名称">
            <input id="asset-name" v-model="assetForm.sourceName" class="input" maxlength="255" />
          </AppField>
          <AppField id="asset-license" label="权利状态">
            <AppSelect id="asset-license" v-model="assetForm.licenseStatus" ariaLabel="权利状态">
              <option value="UNCONFIRMED">未确认</option>
              <option value="APPROVED">商业授权</option>
              <option value="INDEPENDENT_DESIGN">独立设计</option>
            </AppSelect>
          </AppField>
          <AppField v-if="assetForm.licenseStatus !== 'UNCONFIRMED'" id="asset-license-evidence" label="权利证据">
            <AppSelect id="asset-license-evidence" v-model="assetForm.licenseEvidenceId" ariaLabel="权利证据" required searchable search-placeholder="搜索权利证据">
              <option value="">请选择</option>
              <option v-for="item in evidence.filter((entry) => entry.evidenceType === (assetForm.licenseStatus === 'APPROVED' ? 'LICENSE' : 'INDEPENDENT_DESIGN'))" :key="item.id" :value="item.id">
                {{ item.description }}
              </option>
            </AppSelect>
          </AppField>
          <AppButton type="submit" :pending="pending === 'asset'" :disabled="!assetFile || !assetForm.sourceUri.trim()">
            <AppIcon name="upload" :size="15" />导入候选
          </AppButton>
        </form>

        <div class="table-scroll">
          <table class="tbl">
            <thead><tr><th>候选</th><th>安全扫描</th><th>权利</th><th>状态</th><th>指纹</th><th>时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in assets" :key="item.id">
                <td><strong>{{ item.sourceName }}</strong><small :title="item.sourceUri">{{ item.sourceUri }}</small></td>
                <td><AppTag :tone="statusTone(item.scanStatus)">{{ item.scanStatus }}</AppTag></td>
                <td>{{ item.licenseStatus }}</td>
                <td><AppTag :tone="statusTone(item.status)">{{ item.status }}</AppTag><small v-if="item.rejectionReason">{{ item.rejectionReason }}</small></td>
                <td><code :title="item.fileHash">{{ shortHash(item.fileHash) }}</code></td>
                <td>{{ formatWhen(item.createdAt) }}</td>
                <td>
                  <div v-if="item.status === 'REVIEWING' || canRescan(item)" class="row-actions">
                    <AppButton v-if="canRescan(item)" variant="ghost" :pending="pending === `rescan-${item.id}`" @click="rescanAsset(item)"><AppIcon name="refresh" :size="14" />复扫</AppButton>
                    <AppButton v-if="item.status === 'REVIEWING' && item.scanStatus === 'PASSED'" variant="ghost" @click="openReview(item, 'APPROVED')"><AppIcon name="check" :size="14" />批准</AppButton>
                    <AppButton v-if="item.status === 'REVIEWING'" variant="text" @click="openReview(item, 'REJECTED')"><AppIcon name="x" :size="14" />驳回</AppButton>
                  </div>
                  <span v-else class="fine">已完成</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <AppEmpty v-if="!assets.length" text="暂无候选资产" icon="file-text" />
      </section>

      <section v-else key="families" class="version-layout">
        <aside class="family-list" aria-label="模板家族">
          <button v-for="item in families" :key="item.id" type="button" :class="{ active: item.id === selectedFamilyId }" @click="chooseFamily(item.id)">
            <span><strong>{{ item.displayName }}</strong><small>{{ item.familyName }} · {{ item.recommendedPages }} 页</small></span>
            <AppTag :tone="statusTone(item.status)">{{ item.status }}</AppTag>
          </button>
        </aside>

        <div class="version-workspace">
          <header class="workspace-head">
            <div>
              <h2>{{ selectedFamily?.displayName }}</h2>
              <p class="fine"><code>{{ selectedFamily?.id }}</code> · {{ selectedFamily?.languageCode }} · {{ selectedFamily?.atsCandidateLevel }}</p>
            </div>
            <AppButton :disabled="!canCreateDraft" @click="openDraft()"><AppIcon name="plus" :size="15" />新建修订</AppButton>
          </header>

          <div v-if="versionsLoading" class="ops-loading" aria-busy="true"><span v-for="index in 3" :key="index" class="bone" /></div>
          <div v-else-if="versions.length" class="version-list">
            <article v-for="version in versions" :key="version.id" class="version-row">
              <div class="version-row__main">
                <span class="revision">R{{ version.revisionNo }}</span>
                <div>
                  <strong>{{ version.rendererProtocol }}</strong>
                  <small>{{ provenanceLabel(version) }} · 对象版本 {{ version.version }}</small>
                </div>
                <AppTag :tone="statusTone(version.status)">{{ version.status }}</AppTag>
              </div>
              <div class="gate-strip" aria-label="发布门禁">
                <span v-for="gate in GATES" :key="gate.code" :class="{ passed: gatePassed(version, gate.field) }">
                  <AppIcon :name="gatePassed(version, gate.field) ? 'check-circle' : 'clock'" :size="13" />{{ gate.label }}
                </span>
              </div>
              <div class="version-row__actions">
                <AppButton v-if="version.status === 'DRAFT'" variant="ghost" @click="openDraft(version)"><AppIcon name="edit" :size="14" />编辑</AppButton>
                <AppButton v-if="version.status === 'DRAFT' || version.status === 'TESTING'" variant="ghost" @click="openTest(version)"><AppIcon name="check-circle" :size="14" />记录测试</AppButton>
                <AppButton v-if="version.status === 'TESTING'" @click="confirmAction = { kind: 'publish', version }"><AppIcon name="send" :size="14" />发布</AppButton>
                <AppButton v-if="version.status === 'PUBLISHED'" variant="danger" @click="confirmAction = { kind: 'retire', version }"><AppIcon name="archive" :size="14" />退休</AppButton>
              </div>
            </article>
          </div>
          <AppEmpty v-else text="该家族暂无版本" icon="file-text">
            <AppButton :disabled="!canCreateDraft" @click="openDraft()">新建修订</AppButton>
          </AppEmpty>
        </div>
      </section>
      </Transition>
    </section>

    <AppModal :open="Boolean(catalogTarget)" title="校正目录元数据" :width="760" @close="catalogTarget = null">
      <div class="modal-form">
        <div class="form-grid-2"><AppField id="catalog-title" label="展示标题"><input id="catalog-title" v-model="catalogForm.title" class="input" maxlength="512" required /></AppField><AppField id="catalog-kind" label="素材频道"><AppSelect id="catalog-kind" v-model="catalogForm.assetKind" ariaLabel="素材频道"><option value="RESUME">求职简历</option><option value="COVER">封面</option><option value="SCHOOL_APPLICATION">升学材料</option><option value="COVER_LETTER">自荐信/范文</option></AppSelect></AppField></div>
        <AppField id="catalog-summary" label="摘要"><textarea id="catalog-summary" v-model="catalogForm.summary" class="textarea" maxlength="1024" /></AppField>
        <div class="form-grid-2"><AppField id="catalog-language" label="语言"><AppSelect id="catalog-language" v-model="catalogForm.languageCode" ariaLabel="语言"><option value="zh-CN">中文</option><option value="en">英文</option><option value="mixed">中英文</option></AppSelect></AppField><AppField id="catalog-pages" label="页数"><input id="catalog-pages" v-model="catalogForm.pageCount" class="input" maxlength="16" /></AppField><AppField id="catalog-photo" label="照片"><AppSelect id="catalog-photo" v-model="catalogForm.photoPolicy" ariaLabel="照片"><option value="UNSPECIFIED">未识别</option><option value="DISABLED">无照片</option><option value="OPTIONAL">照片可选</option><option value="REQUIRED">含照片</option></AppSelect></AppField><AppField id="catalog-thumbnail" label="缩略图 URI"><input id="catalog-thumbnail" v-model="catalogForm.thumbnailUri" class="input" maxlength="1024" /></AppField></div>
        <p class="fine">标签每行使用“编码|显示名”。职业大类、岗位、风格和职业阶段可分别维护。</p>
        <div class="form-grid-2"><AppField id="catalog-occupations" label="职业大类"><textarea id="catalog-occupations" v-model="catalogForm.occupations" class="textarea code-editor code-editor--small" /></AppField><AppField id="catalog-jobs" label="岗位标签"><textarea id="catalog-jobs" v-model="catalogForm.jobs" class="textarea code-editor code-editor--small" /></AppField><AppField id="catalog-styles" label="版式风格"><textarea id="catalog-styles" v-model="catalogForm.styles" class="textarea code-editor code-editor--small" /></AppField><AppField id="catalog-stages" label="职业阶段"><textarea id="catalog-stages" v-model="catalogForm.careerStages" class="textarea code-editor code-editor--small" /></AppField></div>
      </div>
      <template #footer><AppButton variant="ghost" @click="catalogTarget = null">取消</AppButton><AppButton :pending="pending === 'catalog-save'" :disabled="!catalogForm.title.trim()" @click="saveCatalog">保存校正</AppButton></template>
    </AppModal>

    <AppModal :open="Boolean(reviewTarget)" :title="reviewForm.decision === 'APPROVED' ? '批准候选资产' : '驳回候选资产'" @close="reviewTarget = null">
      <div class="modal-form">
        <p class="object-line"><strong>{{ reviewTarget?.sourceName }}</strong><code>{{ reviewTarget?.fileHash }}</code></p>
        <template v-if="reviewForm.decision === 'APPROVED'">
          <AppField id="review-license" label="权利结论">
            <AppSelect id="review-license" v-model="reviewForm.licenseStatus" ariaLabel="权利结论">
              <option value="APPROVED">商业授权</option>
              <option value="INDEPENDENT_DESIGN">独立设计</option>
            </AppSelect>
          </AppField>
          <AppField id="review-evidence" label="不可变权利证据">
            <AppSelect id="review-evidence" v-model="reviewForm.licenseEvidenceId" ariaLabel="不可变权利证据" required searchable search-placeholder="搜索权利证据">
              <option value="">请选择</option>
              <option v-for="item in licenseEvidence" :key="item.id" :value="item.id">{{ item.description }}</option>
            </AppSelect>
          </AppField>
        </template>
        <AppField v-else id="review-reason" label="驳回原因">
          <textarea id="review-reason" v-model="reviewForm.reason" class="textarea" maxlength="1024" required />
        </AppField>
      </div>
      <template #footer>
        <AppButton variant="ghost" @click="reviewTarget = null">取消</AppButton>
        <AppButton :variant="reviewForm.decision === 'APPROVED' ? 'primary' : 'danger'" :pending="pending === 'review'" :disabled="!reviewReady" @click="submitReview">
          {{ reviewForm.decision === 'APPROVED' ? '确认批准' : '确认驳回' }}
        </AppButton>
      </template>
    </AppModal>

    <AppModal :open="draftOpen" :title="editingVersion ? `编辑 R${editingVersion.revisionNo}` : '新建模板修订'" :width="760" @close="draftOpen = false">
      <div class="modal-form">
        <div class="form-grid-2">
          <AppField id="draft-protocol" label="渲染协议">
            <input id="draft-protocol" v-model="draftForm.rendererProtocol" class="input" required />
          </AppField>
          <AppField id="draft-thumbnail" label="缩略图 URI">
            <input id="draft-thumbnail" v-model="draftForm.thumbnailUri" class="input" />
          </AppField>
        </div>
        <div class="source-switch" :class="{ 'is-independent': draftSourceMode === 'independent' }" aria-label="来源类型">
          <button type="button" :class="{ active: draftSourceMode === 'asset' }" :disabled="provenanceLocked" @click="draftSourceMode = 'asset'">已批准候选</button>
          <button type="button" :class="{ active: draftSourceMode === 'independent' }" :disabled="provenanceLocked" @click="draftSourceMode = 'independent'">独立设计</button>
        </div>
        <Transition name="source-field" mode="out-in">
        <AppField v-if="draftSourceMode === 'asset'" id="draft-asset" key="asset" label="来源资产">
          <AppSelect id="draft-asset" v-model="draftForm.sourceAssetId" ariaLabel="来源资产" required searchable search-placeholder="搜索来源资产">
            <option value="">请选择</option>
            <option v-for="item in approvedAssets" :key="item.id" :value="item.id">{{ item.sourceName }} · {{ shortHash(item.fileHash) }}</option>
          </AppSelect>
        </AppField>
        <AppField v-else id="draft-independent" key="independent" label="独立设计证据">
          <AppSelect id="draft-independent" v-model="draftForm.independentDesignEvidenceId" ariaLabel="独立设计证据" required searchable search-placeholder="搜索独立设计证据">
            <option value="">请选择</option>
            <option v-for="item in independentEvidence" :key="item.id" :value="item.id">{{ item.description }}</option>
          </AppSelect>
        </AppField>
        </Transition>
        <AppField id="draft-definition" label="受控版式定义">
          <textarea id="draft-definition" v-model="draftForm.definitionJson" class="textarea code-editor" required />
        </AppField>
      </div>
      <template #footer>
        <AppButton variant="ghost" @click="draftOpen = false">取消</AppButton>
        <AppButton :pending="pending === 'draft'" :disabled="!draftReady" @click="submitDraft">保存修订</AppButton>
      </template>
    </AppModal>

    <AppModal :open="Boolean(testTarget)" title="记录发布门禁" :width="620" @close="testTarget = null">
      <div class="modal-form">
        <div class="form-grid-2">
          <AppField id="test-gate" label="门禁">
            <AppSelect id="test-gate" v-model="testForm.gateCode" ariaLabel="门禁">
              <option v-for="gate in GATES" :key="gate.code" :value="gate.code">{{ gate.label }}</option>
            </AppSelect>
          </AppField>
          <AppField id="test-outcome" label="结论">
            <AppSelect id="test-outcome" v-model="testForm.outcome" ariaLabel="结论">
              <option value="PASSED">通过</option>
              <option value="FAILED">失败</option>
            </AppSelect>
          </AppField>
        </div>
        <AppField id="test-evidence" label="不可变证据">
          <AppSelect id="test-evidence" v-model="testForm.evidenceId" ariaLabel="不可变证据" required searchable search-placeholder="搜索证据">
            <option value="">请选择</option>
            <option v-for="item in testEvidenceOptions" :key="item.id" :value="item.id">{{ item.description }}</option>
          </AppSelect>
        </AppField>
        <AppField id="test-summary" label="测试结论摘要">
          <textarea id="test-summary" v-model="testForm.summary" class="textarea" maxlength="2048" required />
        </AppField>
        <AppField id="test-environment" label="环境 JSON">
          <textarea id="test-environment" v-model="testForm.environmentJson" class="textarea code-editor code-editor--small" required />
        </AppField>
      </div>
      <template #footer>
        <AppButton variant="ghost" @click="testTarget = null">取消</AppButton>
        <AppButton :pending="pending === 'test'" :disabled="!testReady" @click="submitTest">追加记录</AppButton>
      </template>
    </AppModal>

    <AppModal :open="Boolean(confirmAction)" :title="confirmAction?.kind === 'publish' ? '发布模板版本' : '退休模板版本'" @close="confirmAction = null">
      <p class="confirm-copy">
        {{ confirmAction?.kind === 'publish' ? '发布后版本内容不可原地修改。' : '退休后禁止新选择，历史冻结快照保持可用。' }}
      </p>
      <template #footer>
        <AppButton variant="ghost" @click="confirmAction = null">取消</AppButton>
        <AppButton :variant="confirmAction?.kind === 'publish' ? 'primary' : 'danger'" :pending="pending === confirmAction?.kind" @click="runConfirmedAction">
          确认{{ confirmAction?.kind === 'publish' ? '发布' : '退休' }}
        </AppButton>
      </template>
    </AppModal>
</template>

<style scoped>
.admin-page{max-width:1540px;margin:0 auto}.ops-summary{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden}.ops-summary>div{min-height:82px;padding:16px 20px;display:grid;align-content:center;border-right:1px solid var(--border)}.ops-summary>div:last-child{border-right:0}.ops-summary strong{font-size:24px;line-height:1.15}.ops-summary span{font-size:12px;color:var(--text-tertiary)}.scanner-ready{color:var(--color-success)}.scanner-blocked{color:var(--color-danger)}.ops-tabs,.source-switch{display:inline-flex;width:max-content;padding:3px;background:var(--surface-3);border-radius:var(--radius)}.ops-tabs button,.source-switch button{height:32px;padding:0 15px;border:0;border-radius:6px;background:transparent;color:var(--text-secondary);font-size:13px}.ops-tabs button.active,.source-switch button.active{background:var(--surface);color:var(--text);box-shadow:var(--shadow-s);font-weight:600}.source-switch button:disabled{cursor:not-allowed;opacity:.7}.ops-loading{display:grid;gap:12px;padding:24px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius)}.ops-panel{display:grid;gap:18px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden}.upload-grid{display:grid;grid-template-columns:180px minmax(220px,1fr) minmax(240px,1fr) auto;gap:12px;align-items:end;padding:18px;border-bottom:1px solid var(--border)}.asset-upload{display:grid;grid-template-columns:repeat(4,minmax(160px,1fr));gap:12px;align-items:end;padding:18px;border-bottom:1px solid var(--border)}.asset-upload>.btn{justify-self:start}.file-input{padding:5px 8px;height:40px}.table-scroll{overflow:auto}.tbl td{max-width:280px}.tbl td strong,.tbl td small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.tbl td small{color:var(--text-tertiary);font-size:12px;margin-top:2px}.description-cell{min-width:220px}.row-actions{display:flex;gap:4px}.row-actions .btn{height:30px;padding:0 9px;font-size:12px}.version-layout{display:grid;grid-template-columns:300px minmax(0,1fr);gap:16px;align-items:start}.family-list{display:grid;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden;max-height:calc(100vh - 260px);overflow-y:auto}.family-list>button{min-height:64px;border:0;border-bottom:1px solid var(--border);background:var(--surface);padding:10px 12px;display:flex;align-items:center;gap:10px;text-align:left;color:var(--text)}.family-list>button:last-child{border-bottom:0}.family-list>button:hover{background:var(--surface-2)}.family-list>button.active{background:var(--color-primary-soft);box-shadow:inset 3px 0 var(--color-primary)}.family-list>button>span:first-child{display:grid;flex:1;min-width:0}.family-list strong,.family-list small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.family-list small{font-size:12px;color:var(--text-tertiary)}.version-workspace{min-width:0;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden}.workspace-head{min-height:76px;padding:14px 18px;border-bottom:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;gap:16px}.workspace-head h2{font-size:17px}.version-list{display:grid}.version-row{display:grid;gap:12px;padding:16px 18px;border-bottom:1px solid var(--border)}.version-row:last-child{border-bottom:0}.version-row__main{display:flex;align-items:center;gap:12px}.version-row__main>div{display:grid;flex:1;min-width:0}.version-row__main small{font-size:12px;color:var(--text-tertiary)}.revision{width:42px;height:32px;border-radius:6px;background:var(--surface-2);display:grid;place-items:center;font-family:var(--mono);font-size:12px;font-weight:600}.gate-strip{display:flex;flex-wrap:wrap;gap:6px}.gate-strip span{display:inline-flex;align-items:center;gap:4px;height:26px;padding:0 8px;border:1px solid var(--border);border-radius:6px;color:var(--text-tertiary);font-size:12px}.gate-strip span.passed{border-color:var(--border-default);background:var(--success-soft);color:var(--color-success)}.version-row__actions{display:flex;flex-wrap:wrap;gap:6px}.version-row__actions .btn{height:30px;padding:0 11px;font-size:12px}.modal-form{display:grid;gap:16px}.form-grid-2{display:grid;grid-template-columns:1fr 1fr;gap:12px}.code-editor{min-height:280px;font-family:var(--mono);font-size:12px;white-space:pre;overflow:auto}.code-editor--small{min-height:100px}.object-line{display:grid;gap:4px;padding:10px 12px;background:var(--surface-2);border:1px solid var(--border);border-radius:var(--radius)}.object-line code{word-break:break-all;color:var(--text-tertiary)}.confirm-copy{color:var(--text-secondary)}@media(max-width:1100px){.ops-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.ops-summary>div{border-right:1px solid var(--border);border-bottom:1px solid var(--border)}.ops-summary>div:nth-child(2n){border-right:0}.ops-summary>div:last-child{grid-column:1/-1;border-right:0;border-bottom:0}.upload-grid,.asset-upload{grid-template-columns:repeat(2,minmax(0,1fr))}.version-layout{grid-template-columns:240px minmax(0,1fr)}}@media(max-width:760px){.admin-page{padding:18px 14px 40px}.upload-grid,.asset-upload,.version-layout,.form-grid-2{grid-template-columns:1fr}.family-list{max-height:300px}.workspace-head{align-items:flex-start;flex-direction:column}.upload-grid>.btn,.asset-upload>.btn{width:100%}.table-scroll{max-width:calc(100vw - 92px)}}
.panel-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px;border-bottom:1px solid var(--border)}.panel-toolbar h2{font-size:16px}.panel-toolbar p{margin-top:3px;color:var(--text-tertiary);font-size:12px}.batch-layout{display:grid;grid-template-columns:250px minmax(0,1fr);min-height:420px}.batch-list{display:grid;align-content:start;border-right:1px solid var(--border)}.batch-list button{min-height:58px;display:flex;align-items:center;gap:8px;padding:10px 12px;border:0;border-bottom:1px solid var(--border);background:var(--surface);color:var(--text);text-align:left}.batch-list button.active{background:var(--color-primary-soft);box-shadow:inset 3px 0 var(--color-primary)}.batch-list button>span{min-width:0;display:grid;flex:1}.batch-list small{color:var(--text-tertiary);font-size:12px}.batch-detail{min-width:0;display:grid;align-content:start;gap:12px;padding:14px}.batch-metrics{display:grid;grid-template-columns:repeat(5,minmax(80px,1fr));border:1px solid var(--border)}.batch-metrics span{display:grid;padding:10px 12px;border-right:1px solid var(--border);color:var(--text-tertiary);font-size:12px}.batch-metrics span:last-child{border-right:0}.batch-metrics strong{color:var(--text);font-size:18px}@media(max-width:900px){.batch-layout{grid-template-columns:1fr}.batch-list{grid-template-columns:repeat(2,minmax(0,1fr));border-right:0;border-bottom:1px solid var(--border)}.batch-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.panel-toolbar{align-items:stretch;flex-direction:column}.batch-list,.batch-metrics{grid-template-columns:1fr}.ops-tabs{max-width:100%;overflow-x:auto}}
@media(max-width:760px){.admin-page{grid-template-columns:minmax(0,1fr)}.admin-page>*{min-width:0}.page-head,.page-head>div,.ops-summary>div{min-width:0}.page-head__sub,.ops-summary span{overflow-wrap:anywhere}}

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
  background: var(--surface);
  box-shadow: var(--shadow-s);
  content: '';
  transform: translateX(calc(var(--ops-tab-index) * 100%));
  transition: transform var(--motion-slow) var(--motion-ease);
}
.ops-tabs button,
.source-switch button {
  position: relative;
  z-index: 1;
  transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease);
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
  transition: opacity var(--motion-base) ease, transform var(--motion-base) var(--motion-ease);
}
.ops-panel-swap-enter-from,
.source-field-enter-from { opacity: 0; transform: translateY(7px); }
.ops-panel-swap-leave-to,
.source-field-leave-to { opacity: 0; transform: translateY(-3px); }
.family-list > button,
.batch-list button {
  transition: background-color var(--motion-base) ease, box-shadow var(--motion-base) ease, color var(--motion-fast) ease;
}
.version-list,
.batch-detail { animation: ops-detail-in var(--motion-base) var(--motion-ease-out) both; }
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
  background: var(--surface);
  box-shadow: var(--shadow-s);
  content: '';
  transition: transform var(--motion-slow) var(--motion-ease);
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
