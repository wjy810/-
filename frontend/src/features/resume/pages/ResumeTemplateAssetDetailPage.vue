<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session } from '@/features/identity/session'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppChrome from '@/shared/ui/AppChrome.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { errorMessage, isUnauthenticated } from '@/shared/api/types'
import { downloadTemplateCatalogDocx, fetchTemplateCatalogItem } from '../services/resumeApi'
import type { TemplateCatalogItem } from '../types'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const downloading = ref(false)
const error = ref('')
const downloadError = ref('')
const failedPreviewPages = ref(new Set<number>())
const item = ref<TemplateCatalogItem | null>(null)
let previewPoll: ReturnType<typeof setTimeout> | null = null

const catalogId = computed(() => String(route.params.catalogId ?? ''))
const jobLabels = computed(() =>
  item.value?.facets.filter((facet) => facet.type === 'JOB').map((facet) => facet.label) ?? [],
)
const occupationLabels = computed(() =>
  item.value?.facets.filter((facet) => facet.type === 'OCCUPATION').map((facet) => facet.label) ?? [],
)
const styleLabels = computed(() =>
  item.value?.facets.filter((facet) => facet.type === 'STYLE').map((facet) => facet.label) ?? [],
)

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

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchTemplateCatalogItem(catalogId.value)
    if (result.capability !== 'DOCX_DOWNLOAD') {
      await router.replace({
        name: 'resume-template-detail',
        params: { templateId: result.referenceId },
      })
      return
    }
    item.value = result
    failedPreviewPages.value = new Set()
    schedulePreviewPoll(result.previewStatus)
  } catch (cause) {
    item.value = null
    error.value = errorMessage(cause, '模板详情读取失败')
  } finally {
    loading.value = false
  }
}

function schedulePreviewPoll(status: string): void {
  if (previewPoll) clearTimeout(previewPoll)
  previewPoll = null
  if (status !== 'PENDING' && status !== 'PROCESSING') return
  previewPoll = setTimeout(() => {
    void refreshPreview()
  }, 3000)
}

async function refreshPreview(): Promise<void> {
  try {
    const result = await fetchTemplateCatalogItem(catalogId.value)
    item.value = result
    schedulePreviewPoll(result.previewStatus)
  } catch {
    schedulePreviewPoll(item.value?.previewStatus ?? '')
  }
}

function markPreviewPageFailed(pageNumber: number): void {
  const next = new Set(failedPreviewPages.value)
  next.add(pageNumber)
  failedPreviewPages.value = next
}

async function download(): Promise<void> {
  downloadError.value = ''
  if (!session.signedIn.value) {
    await router.push({
      name: 'home',
      query: { auth: 'login', next: route.fullPath, reason: 'unauthenticated' },
    })
    return
  }
  downloading.value = true
  try {
    const file = await downloadTemplateCatalogDocx(catalogId.value)
    const url = URL.createObjectURL(file.blob)
    const link = document.createElement('a')
    try {
      link.href = url
      link.download = file.filename || 'HICV-resume-template.docx'
      document.body.appendChild(link)
      link.click()
    } finally {
      link.remove()
      URL.revokeObjectURL(url)
    }
  } catch (cause) {
    if (isUnauthenticated(cause)) {
      await router.push({
        name: 'home',
        query: { auth: 'login', next: route.fullPath, reason: 'unauthenticated' },
      })
      return
    }
    downloadError.value = errorMessage(cause, '模板下载失败')
  } finally {
    downloading.value = false
  }
}

onMounted(() => {
  void load()
})

onUnmounted(() => {
  if (previewPoll) clearTimeout(previewPoll)
})
</script>

<template>
  <AppChrome>
    <section class="page asset-page">
      <nav class="asset-breadcrumb" aria-label="面包屑">
        <RouterLink to="/resume-templates">模板中心</RouterLink>
        <AppIcon name="chevron-right" :size="14" />
        <span>Word 模板详情</span>
      </nav>

      <div v-if="loading" class="asset-loading" aria-busy="true">
        <div class="bone asset-loading__preview" />
        <div>
          <div class="bone" />
          <div class="bone bone--short" />
        </div>
      </div>

      <AppBanner v-else-if="error" tone="bad">{{ error }}</AppBanner>

      <div v-else-if="item" class="asset-layout">
        <section class="asset-preview" aria-label="模板全部页面预览">
          <header class="asset-preview__head">
            <h2>文档预览</h2>
            <span v-if="item.previewStatus === 'READY'">{{ item.previewPageCount }} 页</span>
          </header>

          <div v-if="item.previewStatus === 'READY'" class="preview-pages">
            <figure v-for="page in item.previewPages" :key="page.pageNumber" class="preview-page">
              <img
                v-if="!failedPreviewPages.has(page.pageNumber)"
                :src="page.imageUri"
                :alt="`${item.title} 第 ${page.pageNumber} 页`"
                :loading="page.pageNumber === 1 ? 'eager' : 'lazy'"
                decoding="async"
                @error="markPreviewPageFailed(page.pageNumber)"
              />
              <AppBanner v-else tone="bad">第 {{ page.pageNumber }} 页读取失败</AppBanner>
              <figcaption>第 {{ page.pageNumber }} 页</figcaption>
            </figure>
          </div>

          <div v-else-if="item.previewStatus === 'PENDING' || item.previewStatus === 'PROCESSING'" class="preview-state" aria-live="polite">
            <div class="bone preview-state__paper" />
            <strong>{{ item.previewStatus === 'PROCESSING' ? '正在生成分页预览' : '等待生成分页预览' }}</strong>
            <span>完成后将自动显示全部页面</span>
          </div>

          <AppBanner v-else-if="item.previewStatus === 'FAILED'" tone="bad">
            Word 分页预览生成失败，管理员可在模板管理中重试。
          </AppBanner>
        </section>

        <aside class="asset-info">
          <div class="asset-info__head">
            <AppTag tone="green">
              <AppIcon name="download" :size="12" />
              原始 Word
            </AppTag>
            <h1>{{ item.title }}</h1>
            <p>{{ item.summary || 'HICV 公共 Word 简历模板，可下载原始 DOCX 文件。' }}</p>
          </div>

          <dl class="asset-facts">
            <div><dt>语言</dt><dd>{{ languageLabel(item.languageCode) }}</dd></div>
            <div><dt>页数</dt><dd>{{ item.pageCount || '未识别' }}</dd></div>
            <div><dt>照片</dt><dd>{{ photoLabel(item.photoPolicy) }}</dd></div>
            <div><dt>下载</dt><dd>{{ item.downloadCount }} 次</dd></div>
            <div><dt>职业大类</dt><dd>{{ occupationLabels.join('、') || '通用其他' }}</dd></div>
            <div><dt>版式风格</dt><dd>{{ styleLabels.join('、') || '标准' }}</dd></div>
          </dl>

          <div v-if="jobLabels.length" class="asset-tags" aria-label="岗位标签">
            <span v-for="label in jobLabels" :key="label">{{ label }}</span>
          </div>

          <AppBanner v-if="downloadError" tone="bad">{{ downloadError }}</AppBanner>

          <div class="asset-actions">
            <AppButton :pending="downloading" @click="download">
              <AppIcon :name="session.signedIn.value ? 'download' : 'lock'" :size="15" />
              {{ session.signedIn.value ? '下载 DOCX' : '登录后下载 DOCX' }}
            </AppButton>
            <p>下载时服务端会再次核对病毒扫描结果与 SHA-256，文件名保留 HICV 来源前缀。</p>
          </div>

          <section class="source-block">
            <h2>来源与许可</h2>
            <dl>
              <div><dt>来源</dt><dd>{{ item.sourceName }}</dd></div>
              <div><dt>署名</dt><dd>{{ item.attribution }}</dd></div>
            </dl>
            <a :href="item.sourceUri" target="_blank" rel="noreferrer">
              <AppIcon name="link" :size="14" />
              查看 HICV GitHub 项目与许可证
            </a>
            <p>本平台按永久非商业免费模式提供该资产；若产品商业化，这批资产将自动停止公开。</p>
          </section>
        </aside>
      </div>

      <AppEmpty v-else text="模板不存在或已退休" icon="archive">
        <RouterLink class="btn btn--ghost" to="/resume-templates">返回模板中心</RouterLink>
      </AppEmpty>
    </section>
  </AppChrome>
</template>

<style scoped>
.asset-page {
  max-width: 1260px;
  margin: 0 auto;
}

.asset-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 18px;
  color: var(--text-3);
  font-size: 12px;
}

.asset-breadcrumb a {
  color: var(--primary);
}

.asset-layout,
.asset-loading {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 420px;
  gap: 30px;
  align-items: start;
}

.asset-preview {
  min-height: 660px;
  padding: 24px;
  border: 1px solid var(--border);
  background: #e8ebef;
}

.asset-preview__head {
  min-height: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.asset-preview__head h2 {
  font-size: 14px;
}

.asset-preview__head span,
.preview-page figcaption,
.preview-state span {
  color: var(--text-3);
  font-size: 11px;
}

.preview-pages {
  display: grid;
  gap: 22px;
}

.preview-page {
  min-width: 0;
  display: grid;
  justify-items: center;
  gap: 8px;
  margin: 0;
}

.preview-page img {
  width: 100%;
  height: auto;
  display: block;
  border: 1px solid #d5dbe4;
  background: #fff;
  box-shadow: 0 8px 24px rgba(24, 35, 54, 0.14);
}

.preview-state {
  min-height: 580px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  text-align: center;
}

.preview-state__paper {
  width: min(76%, 440px);
  aspect-ratio: 210 / 297;
  margin-bottom: 6px;
}

.asset-info {
  min-width: 0;
  display: grid;
  gap: 20px;
}

.asset-info__head {
  display: grid;
  gap: 10px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border);
}

.asset-info__head .tag {
  width: max-content;
}

.asset-info__head h1 {
  overflow-wrap: anywhere;
  font-size: 24px;
}

.asset-info__head p,
.asset-actions p,
.source-block p {
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.65;
}

.asset-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin: 0;
  border-top: 1px solid var(--border);
}

.asset-facts div {
  min-width: 0;
  display: grid;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}

.asset-facts div:nth-child(odd) {
  padding-right: 16px;
}

.asset-facts dt,
.source-block dt {
  color: var(--text-3);
  font-size: 11px;
}

.asset-facts dd,
.source-block dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.asset-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.asset-tags span {
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 11px;
}

.asset-actions {
  display: grid;
  gap: 8px;
}

.asset-actions .btn {
  width: 100%;
  min-height: 44px;
}

.source-block {
  display: grid;
  gap: 10px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
}

.source-block h2 {
  font-size: 14px;
}

.source-block dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

.source-block dl > div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
}

.source-block a {
  width: max-content;
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--primary);
  font-size: 12px;
}

.asset-loading__preview {
  min-height: 660px;
}

.asset-loading > div:last-child {
  display: grid;
  gap: 12px;
}

@media (max-width: 960px) {
  .asset-layout,
  .asset-loading {
    grid-template-columns: minmax(0, 1fr);
  }

  .asset-preview {
    min-height: 520px;
  }
}

@media (max-width: 640px) {
  .asset-page {
    padding: 18px 14px 36px;
  }

  .asset-preview {
    min-height: 420px;
    padding: 12px;
  }

  .asset-info {
    order: -1;
  }

  .preview-state {
    min-height: 360px;
  }

  .asset-info__head h1 {
    font-size: 20px;
  }
}
</style>
