<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppBanner from '@/shared/ui/AppBanner.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppEmpty from '@/shared/ui/AppEmpty.vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import AppTag from '@/shared/ui/AppTag.vue'
import { useToastFeedback } from '@/shared/ui/toast'
import ForbidState from '@/shared/ui/ForbidState.vue'
import { errorMessage, isForbidden, isVersionConflict, versionConflictMessage } from '@/shared/api/types'
import ResumeTemplatePreview from '../components/ResumeTemplatePreview.vue'
import {
  applyResumeTemplate,
  fetchCurrentResumeLayout,
  fetchResume,
  fetchResumeTemplate,
  listResumes,
  previewResumeTemplate,
} from '../services/resumeApi'
import type {
  CurrentResumeLayout,
  ResumeMasterSummary,
  ResumeMasterView,
  ResumeTemplateDetail,
  ResumeTemplatePreview as PreviewResult,
} from '../types'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const previewing = ref(false)
const applying = ref(false)
const error = ref('')
const previewError = ref('')
const notice = ref('')
useToastFeedback(error, 'error', 'resume-template-detail-error')
useToastFeedback(notice, 'success', 'resume-template-detail-notice')
const forbidden = ref<unknown>(null)
const detail = ref<ResumeTemplateDetail | null>(null)
const resumes = ref<ResumeMasterSummary[]>([])
const selectedMasterId = ref('')
const selectedResume = ref<ResumeMasterView | null>(null)
const currentLayout = ref<CurrentResumeLayout | null>(null)
const variantCode = ref('')
const preview = ref<PreviewResult | null>(null)

const templateId = computed(() => String(route.params.templateId ?? ''))
const usableResumes = computed(() => resumes.value.filter((item) => item.status !== 'ARCHIVED'))
const overflowItems = computed(() => preview.value?.overflow.items ?? [])
const canApply = computed(
  () => Boolean(detail.value && selectedResume.value && variantCode.value && preview.value?.variantValid),
)

function variantLabel(value: string): string {
  const labels: Record<string, string> = {
    MONO: '黑白',
    BLUE: '蓝色强调',
  }
  return labels[value] ?? value
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  forbidden.value = null
  try {
    const [template, masterItems] = await Promise.all([
      fetchResumeTemplate(templateId.value),
      listResumes(),
    ])
    detail.value = template
    resumes.value = masterItems
    variantCode.value = template.variants[0] ?? ''
    const requested = typeof route.query.masterId === 'string' ? route.query.masterId : ''
    selectedMasterId.value = usableResumes.value.some((item) => item.id === requested)
      ? requested
      : usableResumes.value[0]?.id ?? ''
    if (selectedMasterId.value) await loadSelectedResume()
  } catch (cause) {
    detail.value = null
    if (isForbidden(cause)) {
      forbidden.value = cause
      return
    }
    error.value = errorMessage(cause, '模板详情读取失败')
  } finally {
    loading.value = false
  }
}

async function loadSelectedResume(): Promise<void> {
  if (!selectedMasterId.value) {
    selectedResume.value = null
    currentLayout.value = null
    preview.value = null
    return
  }
  previewing.value = true
  previewError.value = ''
  try {
    const [resume, layout] = await Promise.all([
      fetchResume(selectedMasterId.value),
      fetchCurrentResumeLayout(selectedMasterId.value),
    ])
    selectedResume.value = resume
    currentLayout.value = layout
    await loadPreview()
  } catch (cause) {
    selectedResume.value = null
    currentLayout.value = null
    preview.value = null
    if (isForbidden(cause)) {
      forbidden.value = cause
      return
    }
    previewError.value = errorMessage(cause, '本人简历内容读取失败')
  } finally {
    previewing.value = false
  }
}

async function loadPreview(): Promise<void> {
  if (!selectedMasterId.value || !variantCode.value) return
  previewing.value = true
  previewError.value = ''
  try {
    preview.value = await previewResumeTemplate(templateId.value, selectedMasterId.value, variantCode.value)
  } catch (cause) {
    preview.value = null
    if (isForbidden(cause)) {
      forbidden.value = cause
      return
    }
    previewError.value = errorMessage(cause, '模板容量预览失败')
  } finally {
    previewing.value = false
  }
}

async function onApply(): Promise<void> {
  if (!canApply.value || !selectedResume.value) return
  applying.value = true
  error.value = ''
  notice.value = ''
  try {
    const layout = await applyResumeTemplate(
      templateId.value,
      selectedResume.value.id,
      variantCode.value,
      currentLayout.value?.layout?.version,
    )
    notice.value = layout.status === 'OVERFLOW'
      ? '版式已应用，但存在内容溢出。冻结和导出会保持阻断。'
      : '版式已应用，简历内容未被改写。'
    await router.push({
      name: 'resume-editor',
      params: { id: selectedResume.value.id },
      query: { layout: layout.id },
    })
  } catch (cause) {
    if (isForbidden(cause)) {
      forbidden.value = cause
    } else if (isVersionConflict(cause)) {
      error.value = versionConflictMessage(cause)
      await loadSelectedResume()
    } else {
      error.value = errorMessage(cause, '应用模板失败')
    }
  } finally {
    applying.value = false
  }
}

watch(variantCode, (next, previous) => {
  if (next && next !== previous && selectedMasterId.value) void loadPreview()
})

onMounted(() => {
  void load()
})
</script>

<template>
      <ForbidState v-if="forbidden" :error="forbidden" />
    <section v-else class="page detail-page">
      <header class="page-head detail-head">
        <div>
          <RouterLink class="text-link" to="/resume-templates">
            <AppIcon name="chevron-left" :size="14" />
            模板中心
          </RouterLink>
          <div class="detail-head__title">
            <h1 class="page-head__title">{{ detail?.template.displayName || '模板详情' }}</h1>
            <AppTag v-if="detail" :tone="detail.template.status === 'DEMO' ? 'orange' : 'green'">
              {{ detail.template.status === 'DEMO' ? '开发演示' : '已发布' }}
            </AppTag>
          </div>
          <p v-if="detail" class="page-head__sub">
            {{ detail.template.familyName }} · {{ detail.template.languageCode }} · 建议 {{ detail.template.recommendedPages }} 页
          </p>
        </div>
      </header>

      <div v-if="loading && !detail" class="detail-grid" aria-busy="true">
        <div class="bone detail-bone" />
        <div class="bone detail-bone" />
      </div>

      <div v-else-if="detail" class="detail-grid">
        <div class="preview-pane">
          <ResumeTemplatePreview
            :resume="selectedResume"
            :variant-code="variantCode"
            :renderer-protocol="detail.rendererProtocol"
            :layout-definition-json="detail.layoutDefinitionJson"
          />
        </div>

        <aside class="control-pane">
          <section class="control-section">
            <h2>应用到简历</h2>
            <label class="control-label" for="template-master">选择本人简历</label>
            <AppSelect
              id="template-master"
              v-model="selectedMasterId"
              ariaLabel="选择本人简历"
              :disabled="previewing || applying"
              @change="loadSelectedResume"
            >
              <option value="">请选择简历</option>
              <option v-for="item in usableResumes" :key="item.id" :value="item.id">
                {{ item.title || '未命名简历' }} · {{ item.statusLabel || item.status }}
              </option>
            </AppSelect>
            <AppEmpty
              v-if="!usableResumes.length"
              text="还没有可用简历"
              hint="先创建并填写简历内容，再回来预览版式。"
              icon="file-text"
            >
              <RouterLink class="btn btn--primary" to="/resumes">前往简历工作台</RouterLink>
            </AppEmpty>
          </section>

          <section class="control-section">
            <h2>颜色变体</h2>
            <div class="variant-control" role="radiogroup" aria-label="模板颜色变体">
              <button
                v-for="variant in detail.variants"
                :key="variant"
                type="button"
                role="radio"
                class="variant-control__item"
                :class="{ 'is-on': variantCode === variant }"
                :aria-checked="variantCode === variant"
                :disabled="applying"
                @click="variantCode = variant"
              >
                <i :class="variant.includes('BLUE') ? 'swatch swatch--blue' : 'swatch swatch--mono'" />
                {{ variantLabel(variant) }}
              </button>
            </div>
          </section>

          <section class="control-section">
            <div class="control-section__head">
              <h2>容量检查</h2>
              <AppTag v-if="preview" :tone="preview.overflow.valid ? 'green' : 'red'">
                {{ preview.overflow.valid ? '无溢出' : `${overflowItems.length} 处溢出` }}
              </AppTag>
            </div>
            <AppBanner v-if="previewError" tone="bad">{{ previewError }}</AppBanner>
            <p v-else-if="previewing" class="fine">正在按本人内容计算页面容量…</p>
            <template v-else-if="preview">
              <p class="fine">已使用 {{ preview.overflow.consumedUnits }} 个确定性容量单位。</p>
              <div v-if="overflowItems.length" class="overflow-list">
                <div v-for="item in overflowItems" :key="`${item.page}-${item.slotKey}`" class="overflow-item">
                  <AppIcon name="alert-circle" :size="16" />
                  <span>第 {{ item.page }} 页 · {{ item.slotKey }} · 超出 {{ item.excessUnits }}</span>
                </div>
              </div>
            </template>
          </section>

          <section class="control-section">
            <h2>格式与验证</h2>
            <dl class="template-facts">
              <div><dt>PDF</dt><dd>冻结后可导出</dd></div>
              <div><dt>DOCX</dt><dd>{{ detail.docxAvailable ? '已通过门禁，可导出' : '暂未开放' }}</dd></div>
              <div><dt>ATS 等级</dt><dd>候选，未实测</dd></div>
              <div><dt>版本</dt><dd>r{{ detail.revisionNo }}</dd></div>
            </dl>
            <AppBanner :tone="detail.docxAvailable ? 'ok' : 'warn'">
              {{
                detail.docxAvailable
                  ? '授权、安全、渲染、Word、WPS 与 ATS 六项证据均已通过。'
                  : detail.docxUnavailableReason || 'DOCX 尚未通过全部发布门禁。'
              }}
            </AppBanner>
          </section>

          <div class="control-actions">
            <AppButton :disabled="!canApply" :pending="applying" @click="onApply">
              <AppIcon name="check" :size="15" />
              {{ overflowItems.length ? '应用并处理溢出' : '应用到这份简历' }}
            </AppButton>
            <p class="fine">应用只更新版式绑定，不会改写、删除或裁剪简历事实。</p>
          </div>
        </aside>
      </div>
    </section>
</template>

<style scoped>
.detail-page {
  max-width: 1380px;
  margin: 0 auto;
}

.detail-head {
  align-items: flex-start;
}

.detail-head__title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 390px;
  gap: 28px;
  align-items: start;
  min-width: 0;
}

.preview-pane {
  min-width: 0;
  padding: 28px;
  background: var(--surface-2);
}

.control-pane {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-width: 0;
  border-top: 1px solid var(--border);
}

.control-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
  padding: 18px 0;
  border-bottom: 1px solid var(--border);
  overflow-wrap: anywhere;
}

.control-section h2 {
  font-size: 14px;
}

.control-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.control-label {
  color: var(--text-secondary);
  font-size: 12px;
}

.variant-control {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.variant-control__item {
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text-secondary);
  font-size: 13px;
}

.variant-control__item.is-on {
  border-color: var(--color-primary);
  color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 10%, transparent);
}

.swatch {
  width: 15px;
  height: 15px;
  border: 1px solid var(--border-default);
  border-radius: 50%;
}

.swatch--blue {
  background: var(--color-primary);
}

.swatch--mono {
  background: var(--text-primary);
}

.overflow-list {
  display: grid;
  gap: 6px;
}

.overflow-item {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: var(--color-danger);
  font-size: 12px;
}

.template-facts {
  display: grid;
  gap: 7px;
  margin: 0;
}

.template-facts div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.template-facts dt {
  color: var(--text-tertiary);
}

.template-facts dd {
  margin: 0;
  text-align: right;
}

.control-actions {
  display: grid;
  gap: 8px;
  padding-top: 18px;
}

.control-actions .btn {
  width: 100%;
}

.detail-bone {
  height: 640px;
}

@media (max-width: 1050px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .control-pane {
    max-width: none;
  }
}

@media (max-width: 640px) {
  .preview-pane {
    padding: 12px;
  }

  .variant-control {
    grid-template-columns: 1fr;
  }
}
</style>
