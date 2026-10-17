<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { Archive, Briefcase, Check, FileText, IdCard, Search, Trash2 } from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSegmented, { type SegmentItem } from '@/shared/ui/UiSegmented.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { confirm } from '@/shared/ui/confirm'
import { toast } from '@/shared/ui/toast'
import { errorMessage, isForbidden } from '@/shared/api/types'
import { formatRelative } from '@/shared/lib/datetime'
import { fetchCareerFiles, fetchCareerRecords } from '@/features/career-library/services/careerLibraryApi'
import { fileCategoryLabel, fileSize, recordTypeLabel } from '@/features/career-library/labels'
import { glossBlocker, glossImpactRelation, glossTargetType, impactRowTone, objectSubmitBlockedReason } from '../labels'
import { fetchDeletionPreview } from '../services/dataRightsApi'
import type { DeletionRequests } from '../composables/useDeletionRequests'
import type { DeletionPreview } from '../types'

type Kind = 'CAREER_RECORD' | 'CAREER_FILE' | 'CAREER_PROFILE'
type Candidate = { id: string; title: string; meta: string[]; note?: string; archived?: boolean }

const props = defineProps<{
  requests: DeletionRequests
  accountId: string
  busyReason?: string
  initialType?: string
  initialId?: string
}>()
const emit = defineEmits<{ forbidden: [error: unknown] }>()

const KINDS: Kind[] = ['CAREER_RECORD', 'CAREER_FILE', 'CAREER_PROFILE']
const kind = ref<Kind>('CAREER_RECORD')
const keyword = ref('')
const candidates = shallowRef<Candidate[]>([])
const totals = ref<Record<Kind, number | null>>({ CAREER_RECORD: null, CAREER_FILE: null, CAREER_PROFILE: null })
const listLoading = ref(false)
const listError = ref('')
const selectedId = ref('')
const preview = shallowRef<DeletionPreview | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const submitting = ref(false)
let listSeq = 0
let previewSeq = 0
let searchTimer: number | undefined

const segments = computed<SegmentItem<Kind>[]>(() => [
  { value: 'CAREER_RECORD', label: '经历与成果', icon: Briefcase, count: totals.value.CAREER_RECORD },
  { value: 'CAREER_FILE', label: '资料文件', icon: FileText, count: totals.value.CAREER_FILE },
  { value: 'CAREER_PROFILE', label: '职业主档', icon: IdCard },
])
const searchable = computed(() => kind.value !== 'CAREER_PROFILE')
const selected = computed(() => candidates.value.find(item => item.id === selectedId.value) ?? null)
/** An item sent here from the library may not be in the first page of results; it still gets a row. */
const pinned = computed<Candidate | null>(() => {
  if (!selectedId.value || selected.value) return null
  const label = preview.value?.impacts?.[0]?.label
  return { id: selectedId.value, title: label || `所选${glossTargetType(kind.value)}`, meta: ['从求职资料库带入'] }
})
const rows = computed(() => (pinned.value ? [pinned.value, ...candidates.value] : candidates.value))
const blockers = computed(() => (preview.value?.blockers ?? []).map(item => String(item).trim()).filter(Boolean).map(glossBlocker))
const blockedReason = computed(() => objectSubmitBlockedReason(preview.value, kind.value, selectedId.value))
const canDelete = computed(() => !props.busyReason && !blockedReason.value && !previewLoading.value && !submitting.value)

function recordPeriod(start?: string | null, end?: string | null): string {
  if (!start && !end) return ''
  return `${start?.slice(0, 7).replace('-', '.') || '?'} – ${end ? end.slice(0, 7).replace('-', '.') : '至今'}`
}

async function loadCandidates(): Promise<void> {
  const seq = ++listSeq
  const current = kind.value
  listError.value = ''
  if (current === 'CAREER_PROFILE') {
    candidates.value = [{ id: props.accountId, title: '我的职业主档', meta: ['基本信息与求职意向', '全部经历与成果记录会一并删除'] }]
    return
  }
  listLoading.value = true
  try {
    const search = keyword.value.trim() || undefined
    if (current === 'CAREER_RECORD') {
      const page = await fetchCareerRecords({ status: 'ALL', keyword: search, size: 50 })
      if (seq !== listSeq) return
      totals.value.CAREER_RECORD = search ? totals.value.CAREER_RECORD ?? page.total : page.total
      candidates.value = page.items.map(record => ({
        id: record.id,
        title: record.title || '未命名经历',
        meta: [recordTypeLabel(record.type), record.organization ?? '', recordPeriod(record.startDate, record.endDate)].filter(Boolean),
        note: record.resumeReferenceCount > 0 ? `被 ${record.resumeReferenceCount} 个简历版本引用` : '',
        archived: record.status === 'ARCHIVED',
      }))
    } else {
      const page = await fetchCareerFiles({ status: 'ALL', keyword: search, size: 50, sort: 'RECENT' })
      if (seq !== listSeq) return
      totals.value.CAREER_FILE = search ? totals.value.CAREER_FILE ?? page.total : page.total
      candidates.value = page.items.map(file => ({
        id: file.id,
        title: file.displayName || file.originalFilename,
        meta: [fileCategoryLabel(file.category), fileSize(file.sizeBytes), formatRelative(file.updatedAt)],
        archived: file.status === 'ARCHIVED',
      }))
    }
  } catch (reason) {
    if (seq !== listSeq) return
    candidates.value = []
    if (isForbidden(reason)) emit('forbidden', reason)
    else listError.value = errorMessage(reason, '读取可删除的内容失败')
  } finally {
    if (seq === listSeq) listLoading.value = false
  }
}

async function loadPreview(): Promise<void> {
  const seq = ++previewSeq
  preview.value = null
  previewError.value = ''
  if (!selectedId.value) return
  previewLoading.value = true
  try {
    const result = await fetchDeletionPreview({ scope: 'OBJECT', targetType: kind.value, targetId: selectedId.value })
    if (seq === previewSeq) preview.value = result
  } catch (reason) {
    if (seq !== previewSeq) return
    if (isForbidden(reason)) emit('forbidden', reason)
    else previewError.value = errorMessage(reason, '删除影响读取失败')
  } finally {
    if (seq === previewSeq) previewLoading.value = false
  }
}

function choose(id: string): void {
  if (selectedId.value === id) return
  selectedId.value = id
  void loadPreview()
}

function switchKind(next: Kind): void {
  if (kind.value === next) return
  kind.value = next
  keyword.value = ''
  selectedId.value = ''
  preview.value = null
  previewError.value = ''
  void loadCandidates()
  if (next === 'CAREER_PROFILE') choose(props.accountId)
}

async function onDelete(): Promise<void> {
  if (!canDelete.value) return
  const target = selected.value ?? pinned.value
  const ok = await confirm({
    title: `永久删除${glossTargetType(kind.value)}？`,
    message: `「${target?.title ?? '所选数据'}」及上方列出的关联数据会被永久删除，无法恢复。`,
    confirmText: '永久删除',
    tone: 'danger',
    requireText: kind.value === 'CAREER_PROFILE' ? '删除职业主档' : undefined,
  })
  if (!ok) return
  submitting.value = true
  try {
    await props.requests.submit({ scope: 'OBJECT', targetType: kind.value, targetId: selectedId.value, confirmationAck: true })
    toast.success('删除申请已提交', { description: '处理结果会显示在下方“删除申请”，也会通过通知告诉你。' })
    selectedId.value = ''
    preview.value = null
    void loadCandidates()
  } catch (reason) {
    if (isForbidden(reason)) emit('forbidden', reason)
    else previewError.value = errorMessage(reason, '提交删除申请失败')
  } finally {
    submitting.value = false
  }
}

watch(keyword, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => void loadCandidates(), 260)
})

function applyInitial(): void {
  const type = props.initialType?.toUpperCase() as Kind | undefined
  if (type && KINDS.includes(type)) kind.value = type
  void loadCandidates()
  if (props.initialId && type && KINDS.includes(type)) {
    selectedId.value = props.initialId
    void loadPreview()
  } else if (kind.value === 'CAREER_PROFILE') {
    choose(props.accountId)
  }
}

watch(() => [props.initialType, props.initialId], applyInitial)
onMounted(applyInitial)
</script>

<template>
  <section id="object" class="dr-card object-panel" aria-labelledby="object-title">
    <header class="dr-card__head">
      <span class="dr-card__icon is-warn"><Trash2 :size="18" /></span>
      <div class="dr-card__heading">
        <h3 id="object-title">删除单项数据</h3>
        <p>选择一条经历、一个资料文件或整个职业主档，先看清影响范围，再决定是否永久删除。</p>
      </div>
    </header>

    <div class="object-body">
      <div class="object-picker">
        <UiSegmented :model-value="kind" :items="segments" aria-label="要删除的数据类型" block size="sm" @update:model-value="switchKind" />
        <UiInput v-if="searchable" v-model="keyword" :icon="Search" size="sm" clearable :placeholder="kind === 'CAREER_FILE' ? '搜索文件名' : '搜索经历标题、单位'" aria-label="搜索要删除的内容" />
        <div class="object-list" role="radiogroup" :aria-label="`选择要删除的${glossTargetType(kind)}`" :aria-busy="listLoading">
          <template v-if="listLoading && !rows.length">
            <div v-for="n in 3" :key="n" class="object-row is-skeleton"><UiSkeleton height="14px" width="60%" /><UiSkeleton height="11px" width="40%" /></div>
          </template>
          <p v-else-if="listError" class="dr-note dr-note--bad">{{ listError }}</p>
          <p v-else-if="!rows.length" class="object-empty">{{ keyword ? '没有找到匹配的内容。' : kind === 'CAREER_FILE' ? '资料库里还没有文件。' : '资料库里还没有经历记录。' }}</p>
          <button
            v-for="row in rows"
            :key="row.id"
            type="button"
            role="radio"
            class="object-row"
            :class="{ 'is-selected': row.id === selectedId }"
            :aria-checked="row.id === selectedId"
            :disabled="submitting"
            @click="choose(row.id)"
          >
            <span class="object-row__radio"><Check v-if="row.id === selectedId" :size="12" :stroke-width="3" /></span>
            <span class="object-row__copy">
              <strong>{{ row.title }}<UiBadge v-if="row.archived" size="sm"><Archive :size="11" />已归档</UiBadge></strong>
              <small>{{ row.meta.join(' · ') }}</small>
              <em v-if="row.note">{{ row.note }}</em>
            </span>
          </button>
        </div>
      </div>

      <div class="object-impact" aria-live="polite">
        <Transition name="rise" mode="out-in">
          <div v-if="!selectedId" key="idle" class="object-impact__idle">
            <span><Trash2 :size="20" /></span>
            <p>在左侧选择一项内容，这里会列出删除后受影响的数据。</p>
          </div>
          <div v-else-if="previewLoading" key="loading" class="object-impact__loading"><UiSkeleton :lines="4" /></div>
          <div v-else key="preview" class="object-impact__body">
            <p v-if="previewError" class="dr-note dr-note--bad" role="alert">{{ previewError }}</p>
            <template v-if="preview">
              <div class="object-impact__head">
                <strong>{{ glossTargetType(preview.targetType) }}</strong>
                <UiBadge :tone="preview.canProceed === true && !blockers.length ? 'danger' : 'warning'" size="sm">
                  {{ preview.canProceed === true && !blockers.length ? '可以永久删除' : '暂时不能删除' }}
                </UiBadge>
              </div>
              <p class="object-impact__summary">{{ preview.impactSummary || '暂未取得删除影响说明，请重新选择。' }}</p>
              <ul v-if="preview.impacts?.length" class="dr-ledger">
                <li v-for="(row, index) in preview.impacts" :key="`${row.kind}-${row.id}-${index}`" :class="`is-${impactRowTone(row.relation) || 'keep'}`">
                  <span>{{ row.label || glossTargetType(row.kind) }}</span>
                  <em>{{ glossImpactRelation(row.relation) }}</em>
                </li>
              </ul>
              <p v-if="blockers.length" class="dr-note dr-note--warn">暂时不能删除：{{ blockers.join('；') }}</p>
            </template>
          </div>
        </Transition>
        <div class="object-impact__foot">
          <p v-if="busyReason" class="dr-note dr-note--warn">{{ busyReason }}</p>
          <UiButton variant="danger" :icon="Trash2" :disabled="!canDelete" :pending="submitting" @click="onDelete">永久删除</UiButton>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.object-body {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: var(--space-5);
}

.object-picker {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  min-width: 0;
}

.object-list {
  display: grid;
  gap: 4px;
  max-height: 300px;
  overflow-y: auto;
  padding: 4px;
  margin: -4px;
  overscroll-behavior: contain;
}

.object-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-1);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.object-row:hover:not(:disabled) {
  border-color: var(--border-strong);
  background: var(--surface-2);
}

.object-row:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.object-row.is-selected {
  border-color: var(--color-danger);
  background: var(--color-danger-soft);
  box-shadow: 0 0 0 1px var(--color-danger);
}

.object-row.is-skeleton {
  display: grid;
  gap: 6px;
  cursor: default;
}

.object-row__radio {
  width: 18px;
  height: 18px;
  flex: none;
  margin-top: 1px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--border-strong);
  border-radius: var(--radius-full);
  color: var(--text-inverse);
  transition: background-color var(--dur-fast) var(--ease-out), border-color var(--dur-fast) var(--ease-out);
}

.is-selected .object-row__radio {
  border-color: var(--color-danger);
  background: var(--color-danger);
}

.object-row__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.object-row__copy strong {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  font-size: var(--fs-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.object-row__copy small {
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.object-row__copy em {
  color: var(--color-warning-text);
  font-size: var(--fs-xs);
  font-style: normal;
}

.object-empty {
  padding: var(--space-6) var(--space-3);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  text-align: center;
}

.object-impact {
  display: grid;
  grid-template-rows: 1fr auto;
  gap: var(--space-4);
  min-width: 0;
  min-height: 220px;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface-2);
}

.object-impact__idle {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: var(--space-2);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  text-align: center;
}

.object-impact__idle span {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-full);
  background: var(--surface-1);
  box-shadow: var(--shadow-xs);
}

.object-impact__body {
  display: grid;
  align-content: start;
  gap: var(--space-3);
}

.object-impact__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.object-impact__summary {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.7;
  white-space: pre-wrap;
}

.object-impact__foot {
  display: grid;
  justify-items: end;
  gap: var(--space-2);
}

@media (max-width: 860px) {
  .object-body {
    grid-template-columns: 1fr;
  }

  .object-impact {
    min-height: 0;
  }

  .object-impact__foot {
    justify-items: stretch;
  }
}
</style>
