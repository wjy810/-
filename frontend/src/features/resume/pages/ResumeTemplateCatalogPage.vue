<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/shared/ui/AppButton.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import { errorMessage } from '@/shared/api/types'
import { listTemplateCatalog, listTemplateCatalogFacets } from '../services/resumeApi'
import SmartTemplateGallery from '../components/SmartTemplateGallery.vue'
import type {
  TemplateCatalogAssetKind,
  TemplateCatalogCapability,
  TemplateCatalogFacetGroup,
  TemplateCatalogItem,
} from '../types'

const route = useRoute()
const router = useRouter()
const TAB_QUERY: Record<string, TemplateCatalogCapability | ''> = { smart: 'SMART_EDITABLE', word: 'DOCX_DOWNLOAD', all: '' }
const initialTab = TAB_QUERY[String(route.query.tab ?? 'smart')] ?? 'SMART_EDITABLE'
const loading = ref(true)
const facetsLoading = ref(true)
const error = ref('')
useToastFeedback(error, 'error', 'resume-template-catalog-error')
const items = ref<TemplateCatalogItem[]>([])
const facetGroups = ref<TemplateCatalogFacetGroup[]>([])
const total = ref(0)
const page = ref(0)
const size = 24
const failedThumbnails = ref<Set<string>>(new Set())

const filters = reactive({
  keyword: '',
  capability: initialTab as TemplateCatalogCapability | '',
  assetKind: 'RESUME' as TemplateCatalogAssetKind,
  occupation: '',
  jobTag: '',
  style: '',
  language: '',
  pages: '',
  photoPolicy: '',
  careerStage: '',
})

const capabilityTabs: Array<{ value: TemplateCatalogCapability | ''; label: string; query: string }> = [
  { value: 'SMART_EDITABLE', label: '智能模板', query: 'smart' },
  { value: 'DOCX_DOWNLOAD', label: 'Word 模板', query: 'word' },
  { value: '', label: '全部目录', query: 'all' },
]

const assetChannels: Array<{ value: TemplateCatalogAssetKind; label: string }> = [
  { value: 'RESUME', label: '求职简历' },
  { value: 'COVER', label: '简历封面' },
  { value: 'SCHOOL_APPLICATION', label: '升学材料' },
  { value: 'COVER_LETTER', label: '自荐信与范文' },
]

const channelOptions = assetChannels.map((channel) => ({ ...channel }))

const occupations = [
  ['TECHNOLOGY', '技术研发'],
  ['PRODUCT_OPERATIONS', '产品运营'],
  ['SALES_SERVICE', '销售客服'],
  ['MARKETING_MEDIA', '市场传媒'],
  ['DESIGN_CREATIVE', '设计创意'],
  ['FINANCE', '财务金融'],
  ['HR_ADMIN', '人力行政'],
  ['EDUCATION_RESEARCH', '教育科研'],
  ['HEALTHCARE', '医疗护理'],
  ['CONSTRUCTION_ENGINEERING', '建筑工程'],
  ['LOGISTICS_TRANSPORT', '物流交通'],
  ['GENERAL', '通用其他'],
] as const

const occupationOptions = [
  { value: '', label: '全部职业大类' },
  ...occupations.map(([value, label]) => ({ value, label })),
]
const languageOptions = [
  { value: '', label: '全部语言' },
  { value: 'zh-CN', label: '中文' },
  { value: 'en', label: '英文' },
  { value: 'mixed', label: '中英文' },
]
const pageOptions = [
  { value: '', label: '全部页数' },
  { value: '1', label: '1 页' },
  { value: '2', label: '2 页' },
  { value: '3+', label: '3 页及以上' },
]
const photoOptions = [
  { value: '', label: '全部照片类型' },
  { value: 'DISABLED', label: '无照片' },
  { value: 'OPTIONAL', label: '照片可选' },
  { value: 'REQUIRED', label: '含照片' },
]

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)))
const capabilityIndex = computed(() => Math.max(0, capabilityTabs.findIndex((tab) => tab.value === filters.capability)))
/** The smart tab lists the live layout templates; the catalog filters below only apply to Word assets. */
const smartTab = computed(() => filters.capability === 'SMART_EDITABLE')
const activeFilterCount = computed(() =>
  [
    filters.occupation,
    filters.jobTag,
    filters.style,
    filters.language,
    filters.pages,
    filters.photoPolicy,
    filters.careerStage,
  ].filter(Boolean).length,
)

function facetOptions(type: string) {
  return facetGroups.value.find((group) => group.type === type)?.options ?? []
}

function facetSelectOptions(type: string, defaultLabel: string) {
  return [
    { value: '', label: defaultLabel },
    ...facetOptions(type).map((option) => ({
      value: option.code,
      label: option.label,
      count: option.count,
    })),
  ]
}

function capabilityLabel(value: string): string {
  return value === 'SMART_EDITABLE' ? '在线编辑 + AI' : '原始 Word'
}

function languageLabel(value: string): string {
  if (value === 'zh-CN') return '中文'
  if (value === 'en') return '英文'
  if (value === 'mixed') return '中英文'
  return value
}

function photoLabel(value: string): string {
  if (value === 'DISABLED') return '无照片'
  if (value === 'OPTIONAL') return '照片可选'
  if (value === 'REQUIRED') return '含照片'
  return '照片未识别'
}

function facetLabels(item: TemplateCatalogItem, type: string): string[] {
  return item.facets.filter((facet) => facet.type === type).map((facet) => facet.label)
}

function itemLink(item: TemplateCatalogItem) {
  if (item.capability === 'SMART_EDITABLE') {
    return { name: 'resume-template-detail', params: { templateId: item.referenceId } }
  }
  return { name: 'template-asset-detail', params: { catalogId: item.id } }
}

function markThumbnailFailed(id: string): void {
  failedThumbnails.value = new Set(failedThumbnails.value).add(id)
}

async function loadFacets(): Promise<void> {
  facetsLoading.value = true
  try {
    facetGroups.value = await listTemplateCatalogFacets(filters.assetKind)
  } catch {
    facetGroups.value = []
  } finally {
    facetsLoading.value = false
  }
}

async function load(nextPage = page.value): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listTemplateCatalog({
      ...filters,
      page: nextPage,
      size,
    })
    items.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (cause) {
    items.value = []
    total.value = 0
    error.value = errorMessage(cause, '模板目录读取失败')
  } finally {
    loading.value = false
  }
}

function selectCapability(value: TemplateCatalogCapability | ''): void {
  if (filters.capability === value) return
  filters.capability = value
  const tab = capabilityTabs.find(item => item.value === value)?.query ?? 'smart'
  void router.replace({ query: { ...route.query, tab } })
  if (value === 'SMART_EDITABLE') {
    error.value = ''
    loading.value = false
    return
  }
  void load(0)
}

function search(): void {
  void load(0)
}

function changeChannel(): void {
  filters.occupation = ''
  filters.jobTag = ''
  filters.style = ''
  filters.careerStage = ''
  void Promise.all([loadFacets(), load(0)])
}

function resetDetails(): void {
  filters.occupation = ''
  filters.jobTag = ''
  filters.style = ''
  filters.language = ''
  filters.pages = ''
  filters.photoPolicy = ''
  filters.careerStage = ''
  void load(0)
}

onMounted(() => {
  void loadFacets()
  if (smartTab.value) loading.value = false
  else void load(0)
})
</script>

<template>
      <section class="page catalog-page">
      <header class="page-head catalog-head">
        <div>
          <h1 class="page-head__title">简历模板中心</h1>
          <p class="page-head__sub">16 套智能模板可在线编辑、与 AI 协作并一键换版，导出的 PDF 与预览一致；另有开源 Word 模板可检索、预览和下载。</p>
        </div>
        <RouterLink class="btn btn--ghost" to="/resumes">
          <JobProofIcon name="resume-all-resumes" :size="15" />
          我的简历
        </RouterLink>
      </header>

      <div class="catalog-toolbar" :class="{ 'catalog-toolbar--pending': smartTab }">
        <div class="capability-tabs" role="tablist" aria-label="模板能力" :style="{ '--capability-index': capabilityIndex }">
          <button
            v-for="tab in capabilityTabs"
            :key="tab.value || 'ALL'"
            :id="`capability-tab-${tab.value || 'ALL'}`"
            type="button"
            role="tab"
            :aria-selected="filters.capability === tab.value"
            :aria-controls="tab.value === 'SMART_EDITABLE' ? 'capability-panel-SMART_EDITABLE' : undefined"
            :class="{ active: filters.capability === tab.value }"
            @click="selectCapability(tab.value)"
          >
            <span>{{ tab.label }}</span>
          </button>
        </div>

        <form v-if="!smartTab" class="catalog-search" role="search" @submit.prevent="search">
          <AppIcon name="search" :size="17" />
          <input
            v-model.trim="filters.keyword"
            type="search"
            placeholder="搜索职位、行业、模板名或原文件名"
            aria-label="关键词"
          />
          <AppButton type="submit" :pending="loading">搜索</AppButton>
        </form>
      </div>

      <div v-if="!smartTab" class="channel-row">
        <div class="channel-control">
          <span>素材频道</span>
          <AppSelect
            v-model="filters.assetKind"
            ariaLabel="素材频道"
            :options="channelOptions"
            @change="changeChannel"
          />
        </div>
        <p>当前共 {{ total.toLocaleString('zh-CN') }} 项</p>
      </div>

      <form v-if="!smartTab" class="filter-band" @submit.prevent="search">
        <AppSelect v-model="filters.occupation" ariaLabel="职业大类" :options="occupationOptions" />
        <AppSelect
          v-model="filters.jobTag"
          ariaLabel="岗位标签"
          :disabled="facetsLoading"
          :options="facetSelectOptions('JOB', '全部岗位')"
          searchable
          search-placeholder="搜索岗位"
        />
        <AppSelect
          v-model="filters.style"
          ariaLabel="版式风格"
          :disabled="facetsLoading"
          :options="facetSelectOptions('STYLE', '全部版式')"
        />
        <AppSelect v-model="filters.language" ariaLabel="语言" :options="languageOptions" />
        <AppSelect v-model="filters.pages" ariaLabel="页数" :options="pageOptions" />
        <AppSelect
          v-model="filters.photoPolicy"
          ariaLabel="照片"
          :options="photoOptions"
          menu-align="end"
        />
        <AppSelect
          v-model="filters.careerStage"
          ariaLabel="职业阶段"
          :disabled="facetsLoading"
          :options="facetSelectOptions('CAREER_STAGE', '全部职业阶段')"
          menu-align="end"
        />
        <div class="filter-actions">
          <AppButton type="submit" :pending="loading">
            <AppIcon name="filter" :size="14" />
            筛选
          </AppButton>
          <AppButton v-if="activeFilterCount" variant="text" type="button" @click="resetDetails">
            清除 {{ activeFilterCount }}
          </AppButton>
        </div>
      </form>

      <SmartTemplateGallery v-if="smartTab" />

      <div v-else-if="loading && !items.length" class="catalog-grid" aria-busy="true">
        <div v-for="index in 8" :key="index" class="catalog-card catalog-card--loading">
          <div class="bone catalog-card__preview" />
          <div class="bone" />
          <div class="bone bone--short" />
        </div>
      </div>

      <div v-else-if="items.length" class="catalog-grid">
        <RouterLink
          v-for="item in items"
          :key="item.id"
          :to="itemLink(item)"
          class="catalog-card"
          :aria-label="'查看' + item.title"
        >
          <div class="catalog-card__preview">
            <img
              v-if="item.thumbnailUri && !failedThumbnails.has(item.id)"
              :src="item.thumbnailUri"
              :alt="item.title + ' 缩略图'"
              loading="lazy"
              @error="markThumbnailFailed(item.id)"
            />
            <div v-else class="paper-preview" aria-hidden="true">
              <span class="paper-preview__name" />
              <span class="paper-preview__meta" />
              <span class="paper-preview__rule" />
              <span v-for="line in 7" :key="line" class="paper-preview__line" :class="'line-' + line" />
            </div>
            <AppTag
              class="catalog-card__capability"
              :tone="item.capability === 'SMART_EDITABLE' ? 'blue' : 'green'"
            >
              <AppIcon :name="item.capability === 'SMART_EDITABLE' ? 'sparkles' : 'download'" :size="12" />
              {{ capabilityLabel(item.capability) }}
            </AppTag>
          </div>

          <div class="catalog-card__body">
            <div class="catalog-card__title">
              <h2>{{ item.title }}</h2>
              <AppIcon name="chevron-right" :size="16" />
            </div>
            <p>{{ item.summary || 'HICV 公共 Word 简历模板' }}</p>
            <div class="catalog-card__facts">
              <span>{{ languageLabel(item.languageCode) }}</span>
              <span v-if="item.pageCount">{{ item.pageCount }} 页</span>
              <span>{{ photoLabel(item.photoPolicy) }}</span>
            </div>
            <div v-if="facetLabels(item, 'JOB').length" class="catalog-card__tags">
              <span v-for="label in facetLabels(item, 'JOB').slice(0, 3)" :key="label">{{ label }}</span>
            </div>
            <small>{{ item.sourceName }} · {{ item.downloadCount }} 次下载</small>
          </div>
        </RouterLink>
      </div>

      <AppEmpty
        v-else
        text="没有找到匹配模板"
        hint="换一个职位关键词，或减少筛选条件。"
        icon="search"
      >
        <AppButton variant="ghost" @click="resetDetails">清除筛选</AppButton>
      </AppEmpty>

      <nav v-if="!smartTab && total > size" class="pager catalog-pager" aria-label="模板分页">
        <button class="pager__page" :disabled="page <= 0 || loading" @click="load(page - 1)">
          <AppIcon name="chevron-left" :size="15" />
        </button>
        <span>第 {{ page + 1 }} / {{ pageCount }} 页</span>
        <button class="pager__page" :disabled="page + 1 >= pageCount || loading" @click="load(page + 1)">
          <AppIcon name="chevron-right" :size="15" />
        </button>
      </nav>

      <footer v-if="!smartTab" class="catalog-license">
        <AppIcon name="shield" :size="17" />
        <p>
          HICV 公共资产仅用于本平台永久非商业免费服务，下载文件保留来源前缀。
          <a href="https://github.com/HICV-CN/hicv-word-resume-templates" target="_blank" rel="noreferrer">查看 GitHub 来源与许可</a>
        </p>
      </footer>
    </section>
</template>

<style scoped>
.catalog-page {
  max-width: 1560px;
  margin: 0 auto;
}

.catalog-head {
  align-items: flex-start;
}

.catalog-toolbar {
  display: grid;
  grid-template-columns: auto minmax(360px, 640px);
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

.catalog-toolbar--pending {
  grid-template-columns: auto;
}

.capability-tabs {
  position: relative;
  isolation: isolate;
  display: inline-grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  width: max-content;
  padding: 3px;
  border-radius: var(--radius);
  background: var(--surface-3);
}

.capability-tabs::before {
  position: absolute;
  inset: 3px auto 3px 3px;
  z-index: -1;
  width: calc((100% - 6px) / 3);
  border-radius: 6px;
  background: var(--surface);
  box-shadow: var(--shadow-s);
  content: '';
  transform: translateX(calc(var(--capability-index) * 100%));
  transition: transform 280ms cubic-bezier(0.2, 0.8, 0.2, 1), box-shadow 220ms ease;
}

.capability-tabs button {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  transition: color 220ms ease, transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.capability-tabs__status {
  padding: 1px 4px;
  border: 1px solid var(--color-warning);
  border-radius: 3px;
  background: var(--surface-2);
  color: var(--color-warning-text);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}

.capability-tabs button:hover:not(.active) {
  color: var(--color-primary);
  transform: none;
}

.capability-tabs button.active {
  background: transparent;
  color: var(--text);
  font-weight: 600;
  box-shadow: none;
}

.capability-tabs button:active {
  transform: none;
}

.capability-tabs button:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--color-primary) 35%, transparent);
  outline-offset: -3px;
}

.catalog-search {
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding-left: 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
}

.catalog-search:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.catalog-search input {
  min-width: 0;
  height: 40px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 13px;
}

.catalog-search .btn {
  margin: 3px;
  height: 34px;
}

.channel-row {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.channel-control {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.channel-control .app-select {
  width: 180px;
}

.channel-row p {
  color: var(--text-tertiary);
  font-size: 12px;
}

.filter-band {
  display: grid;
  grid-template-columns: repeat(7, minmax(128px, 1fr)) auto;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}


.catalog-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  padding: 20px 0;
}

.catalog-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
  transition: border-color 150ms ease, box-shadow 150ms ease, transform 150ms ease;
}

.catalog-card:hover {
  border-color: var(--border-strong);
  box-shadow: 0 8px 24px rgba(20, 32, 55, 0.1);
  transform: translateY(-2px);
}

.catalog-card__preview {
  position: relative;
  aspect-ratio: 210 / 270;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-bottom: 1px solid var(--border);
  background: var(--surface-3);
}

.catalog-card__preview > img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
}

.catalog-card__capability {
  position: absolute;
  top: 9px;
  left: 9px;
  max-width: calc(100% - 18px);
  box-shadow: 0 2px 7px rgba(16, 24, 40, 0.12);
}

.paper-preview {
  width: 72%;
  aspect-ratio: 210 / 297;
  display: grid;
  grid-template-columns: 1fr;
  align-content: start;
  gap: 5px;
  padding: 15% 12%;
  background: var(--surface-1);
  border: 1px solid var(--border-default);
  box-shadow: 0 4px 12px rgba(28, 39, 57, 0.12);
}

.paper-preview span {
  display: block;
  background: var(--border-strong);
}

.paper-preview__name {
  width: 42%;
  height: 8px;
  background: var(--text-primary) !important;
}

.paper-preview__meta {
  width: 62%;
  height: 3px;
}

.paper-preview__rule {
  width: 100%;
  height: 2px;
  margin: 4px 0;
  background: var(--color-primary) !important;
}

.paper-preview__line {
  width: 100%;
  height: 3px;
}

.paper-preview .line-2,
.paper-preview .line-5 {
  width: 78%;
}

.paper-preview .line-4 {
  width: 90%;
}

.catalog-card__body {
  min-height: 162px;
  display: grid;
  align-content: start;
  gap: 7px;
  padding: 13px;
}

.catalog-card__title {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.catalog-card h2 {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.catalog-card__body > p {
  min-height: 36px;
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.catalog-card__facts,
.catalog-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.catalog-card__facts span,
.catalog-card__tags span {
  min-width: 0;
  padding: 2px 6px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--surface-2);
  color: var(--text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.catalog-card__tags span {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.catalog-card small {
  margin-top: auto;
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.catalog-card--loading {
  padding: 0;
}

.catalog-card--loading > .bone:not(.catalog-card__preview) {
  margin: 13px;
}

.catalog-pager {
  padding: 4px 0 22px;
}

.catalog-license {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px 0;
  border-top: 1px solid var(--border);
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.catalog-license a {
  color: var(--color-primary);
}

@media (prefers-reduced-motion: reduce) {
  .capability-tabs::before,
  .capability-tabs button {
    transition: none;
  }
}

@media (max-width: 1400px) {
  .catalog-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }

  .filter-band {
    grid-template-columns: repeat(4, minmax(138px, 1fr));
  }
}

@media (max-width: 1120px) {

  .catalog-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 880px) {
  .catalog-toolbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .capability-tabs {
    width: 100%;
  }

  .catalog-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .filter-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .catalog-page {
    padding: 18px 14px 36px;
  }

  .catalog-head {
    gap: 14px;
  }

  .catalog-head .btn {
    width: 100%;
  }

  .capability-tabs {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .capability-tabs button {
    min-height: 44px;
    padding: 6px;
    flex-wrap: wrap;
    gap: 2px 6px;
  }

  .capability-tabs__status {
    padding-inline: 3px;
    font-size: 12px;
  }

  .catalog-search {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .catalog-search .btn {
    grid-column: 1 / -1;
    width: calc(100% - 6px);
    margin: 0 3px 3px;
  }

  .channel-row {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
    padding: 12px 0;
  }

  .channel-control {
    display: grid;
  }

  .channel-control .app-select {
    width: 100%;
  }

  .filter-band {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-actions .btn {
    flex: 1;
  }

  .catalog-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .catalog-card__body {
    min-height: 150px;
    padding: 10px;
  }

  .catalog-card__body > p,
  .catalog-card__tags {
    display: none;
  }

  .catalog-card__facts {
    align-content: start;
    min-height: 42px;
  }

  .catalog-card h2 {
    font-size: 13px;
  }
}

/* Keep the published interaction details without changing catalog behaviour. */
.capability-tabs__status { font-size: 11px; }
.capability-tabs button:hover:not(.active) { transform: translateY(-1px); }
.capability-tabs button:active { transform: scale(.98); }
.catalog-card small { font-size: 11.5px; }

</style>
