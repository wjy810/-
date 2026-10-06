<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import constructionIllustration from '@/assets/template-smart-editing-construction.png'
import { errorMessage } from '@/shared/api/types'
import { listTemplateCatalog, listTemplateCatalogFacets } from '../services/resumeApi'
import type {
  TemplateCatalogAssetKind,
  TemplateCatalogCapability,
  TemplateCatalogFacetGroup,
  TemplateCatalogItem,
} from '../types'

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
  capability: '' as TemplateCatalogCapability | '',
  assetKind: 'RESUME' as TemplateCatalogAssetKind,
  occupation: '',
  jobTag: '',
  style: '',
  language: '',
  pages: '',
  photoPolicy: '',
  careerStage: '',
})

const capabilityTabs: Array<{ value: TemplateCatalogCapability | ''; label: string }> = [
  { value: '', label: '全部' },
  { value: 'SMART_EDITABLE', label: '智能编辑' },
  { value: 'DOCX_DOWNLOAD', label: 'Word 下载' },
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
const smartEditingPending = computed(() => filters.capability === 'SMART_EDITABLE')
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
  if (value === 'SMART_EDITABLE') {
    items.value = []
    total.value = 0
    page.value = 0
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
  void Promise.all([loadFacets(), load(0)])
})
</script>

<template>
      <section class="page catalog-page">
      <header class="page-head catalog-head">
        <div>
          <h1 class="page-head__title">简历模板中心</h1>
          <p class="page-head__sub">公共模板可检索、预览和下载；智能编辑功能正在建设中。</p>
        </div>
        <RouterLink class="btn btn--ghost" to="/resumes">
          <JobProofIcon name="resume-all-resumes" :size="15" />
          我的简历
        </RouterLink>
      </header>

      <div class="catalog-toolbar" :class="{ 'catalog-toolbar--pending': smartEditingPending }">
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
            <small v-if="tab.value === 'SMART_EDITABLE'" class="capability-tabs__status">建设中</small>
          </button>
        </div>

        <form v-if="!smartEditingPending" class="catalog-search" role="search" @submit.prevent="search">
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

      <div v-if="!smartEditingPending" class="channel-row">
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

      <form v-if="!smartEditingPending" class="filter-band" @submit.prevent="search">
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

      <section
        v-if="smartEditingPending"
        id="capability-panel-SMART_EDITABLE"
        class="construction-state"
        role="tabpanel"
        aria-labelledby="capability-tab-SMART_EDITABLE construction-title"
        tabindex="0"
      >
        <div class="construction-state__copy">
          <span class="construction-state__eyebrow">
            <JobProofIcon name="template-construction" :size="15" />
            建设中
          </span>
          <h2 id="construction-title">智能编辑正在施工</h2>
          <p>
            智能编辑暂未开放。你可以先比较模板样式、查看预览，并下载可用的 Word 模板。
          </p>
        </div>

        <figure class="construction-visual" aria-hidden="true">
          <img
            :src="constructionIllustration"
            alt=""
            width="1420"
            height="793"
            decoding="async"
          />
        </figure>

        <div class="construction-state__actions">
          <AppButton type="button" @click="selectCapability('DOCX_DOWNLOAD')">
            <AppIcon name="download" :size="15" />
            浏览 Word 模板
          </AppButton>
          <AppButton variant="ghost" type="button" @click="selectCapability('')">查看全部模板</AppButton>
        </div>

        <div class="construction-state__progress" aria-label="智能编辑建设进度">
          <span><AppIcon name="check" :size="14" />结构化内容模型</span>
          <span><AppIcon name="loader" :size="14" />在线编辑体验</span>
          <span><AppIcon name="clock" :size="14" />开放时间待定</span>
        </div>

      </section>

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

      <nav v-if="!smartEditingPending && total > size" class="pager catalog-pager" aria-label="模板分页">
        <button class="pager__page" :disabled="page <= 0 || loading" @click="load(page - 1)">
          <AppIcon name="chevron-left" :size="15" />
        </button>
        <span>第 {{ page + 1 }} / {{ pageCount }} 页</span>
        <button class="pager__page" :disabled="page + 1 >= pageCount || loading" @click="load(page + 1)">
          <AppIcon name="chevron-right" :size="15" />
        </button>
      </nav>

      <footer v-if="!smartEditingPending" class="catalog-license">
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
  background: #e9edf3;
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
  color: var(--text-2);
  font-size: 13px;
  transition: color 220ms ease, transform 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.capability-tabs__status {
  padding: 1px 4px;
  border: 1px solid #d9a43b;
  border-radius: 3px;
  background: #fff8e8;
  color: #8a5a08;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}

.capability-tabs button:hover:not(.active) {
  color: var(--primary);
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
  outline: 2px solid rgba(37, 99, 235, 0.35);
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
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
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
  color: var(--text-3);
  font-size: 12px;
}

.channel-control .app-select {
  width: 180px;
}

.channel-row p {
  color: var(--text-3);
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

.construction-state {
  min-height: 500px;
  display: grid;
  grid-template-columns: minmax(0, .9fr) minmax(480px, 1.1fr);
  grid-template-areas:
    "copy visual"
    "actions visual"
    "progress visual";
  grid-template-rows: auto auto;
  align-items: start;
  align-content: center;
  column-gap: clamp(28px, 4.5vw, 72px);
  row-gap: 22px;
  margin-top: 18px;
  padding: clamp(38px, 5vw, 68px);
  overflow: hidden;
  border-top: 1px solid #dfe6ef;
  border-bottom: 1px solid #dfe6ef;
  border-left: 0;
  border-right: 0;
  border-radius: 0;
  outline: 0;
  background: #f5f8fc;
  animation: construction-content-enter 220ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.construction-state:focus-visible {
  outline: 2px solid rgba(37, 99, 235, 0.38);
  outline-offset: -2px;
}

.construction-state__copy {
  grid-area: copy;
  max-width: 540px;
}

.construction-state__eyebrow {
  width: max-content;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 9px 5px 8px;
  border-left: 3px solid #e4a11b;
  background: #fff8e8;
  color: #76500b;
  font-size: 12px;
  font-weight: 650;
}

.construction-state h2 {
  margin-top: 17px;
  color: #142238;
  font-size: clamp(32px, 2.7vw, 36px);
  line-height: 1.25;
  letter-spacing: 0;
}

.construction-state__copy > p {
  max-width: 520px;
  margin-top: 13px;
  color: #57677d;
  font-size: 14px;
  line-height: 1.8;
}

.construction-state__actions {
  grid-area: actions;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.construction-state__progress {
  grid-area: progress;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  padding-top: 17px;
  border-top: 1px solid #dfe6ef;
}

.construction-state__progress span {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 13px;
  color: #738198;
  font-size: 11px;
  line-height: 1.45;
}

.construction-state__progress span + span {
  padding-left: 13px;
  border-left: 1px solid #dfe6ef;
}

.construction-state__progress span:first-child { color: #16805c; }
.construction-state__progress span:nth-child(2) { color: #2563eb; }
.construction-state__progress span:nth-child(2) :deep(.app-icon) { animation: progress-spin 2.4s linear infinite; }

.construction-state__actions .btn {
  min-height: 44px;
}

.construction-visual {
  grid-area: visual;
  min-width: 0;
  margin: 0;
  display: grid;
  place-items: center;
  align-self: center;
  animation: construction-visual-enter 240ms 40ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.construction-visual img {
  display: block;
  width: 100%;
  height: auto;
  width: min(100%, 680px);
  max-height: 420px;
  object-fit: contain;
}

@keyframes construction-content-enter {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}

@keyframes construction-visual-enter {
  from { opacity: 0; transform: translateX(6px); }
  to { opacity: 1; transform: none; }
}

@keyframes progress-spin { to { transform: rotate(360deg); } }


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
  border-color: #aebdd2;
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
  background: #e8ebef;
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
  background: #fff;
  border: 1px solid #d6dbe3;
  box-shadow: 0 4px 12px rgba(28, 39, 57, 0.12);
}

.paper-preview span {
  display: block;
  background: #cfd6df;
}

.paper-preview__name {
  width: 42%;
  height: 8px;
  background: #253247 !important;
}

.paper-preview__meta {
  width: 62%;
  height: 3px;
}

.paper-preview__rule {
  width: 100%;
  height: 2px;
  margin: 4px 0;
  background: #3b82f6 !important;
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
  color: var(--text-2);
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
  background: #eef1f5;
  color: var(--text-2);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.catalog-card__tags span {
  background: var(--primary-soft);
  color: var(--primary);
}

.catalog-card small {
  margin-top: auto;
  overflow: hidden;
  color: var(--text-3);
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
  color: var(--text-3);
  font-size: 12px;
  line-height: 1.6;
}

.catalog-license a {
  color: var(--primary);
}

@media (prefers-reduced-motion: reduce) {
  .capability-tabs::before,
  .capability-tabs button {
    transition: none;
  }

  .construction-state,
  .construction-visual {
    animation: none !important;
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
  .construction-state {
    grid-template-columns: minmax(0, 1fr) 180px;
    column-gap: 24px;
  }

  .construction-state__copy {
    max-width: 680px;
  }

  .construction-visual img {
    max-height: 140px;
  }

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

  .construction-state {
    min-height: 0;
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: 'copy' 'visual' 'actions' 'progress';
    gap: 30px;
    margin-top: 16px;
    padding: 34px 18px 24px;
  }

  .construction-state h2 {
    margin-top: 16px;
    font-size: 29px;
  }

  .construction-state__copy > p {
    font-size: 14px;
    line-height: 1.75;
  }

  .construction-state__actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .construction-state__actions .btn {
    width: 100%;
  }

  .construction-visual { display: grid; }
  .construction-visual img { max-height: 240px; }
  .construction-state__progress { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .construction-state__progress span { min-height: 28px; padding: 0; }
  .construction-state__progress span + span { padding-left: 0; border-left: 0; }

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
.capability-tabs__status { font-size: 9px; }
.capability-tabs button:hover:not(.active) { transform: translateY(-1px); }
.capability-tabs button:active { transform: scale(.98); }
.catalog-card small { font-size: 10px; }
@media (prefers-reduced-motion: reduce) {
  .construction-state,
  .construction-visual,
  .capability-tabs button,
  .catalog-card { animation: none !important; transition: none !important; }
}

</style>
