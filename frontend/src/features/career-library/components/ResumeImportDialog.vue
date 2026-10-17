<script setup lang="ts">
/**
 * Pick entries from one of your resumes and add them as library records. Nothing is imported
 * without a tick: the library stays the user's own confirmed material.
 */
import { computed, ref, shallowRef, watch } from 'vue'
import { Check, FileText } from 'lucide-vue-next'
import UiBadge from '@/shared/ui/UiBadge.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiCheckbox from '@/shared/ui/UiCheckbox.vue'
import UiDialog from '@/shared/ui/UiDialog.vue'
import UiSelect from '@/shared/ui/UiSelect.vue'
import UiSkeleton from '@/shared/ui/UiSkeleton.vue'
import { errorMessage } from '@/shared/api/types'
import { listResumes } from '@/features/resume/services/resumeApi'
import type { ResumeMasterSummary } from '@/features/resume/types'
import { RECORD_TYPES } from '../labels'
import { fetchResumeImportCandidates, importRecordsFromResume } from '../services/careerLibraryApi'
import type { ResumeImportCandidate, ResumeImportResult } from '../types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; imported: [result: ResumeImportResult] }>()

const resumes = shallowRef<ResumeMasterSummary[]>([])
const masterId = ref('')
const candidates = shallowRef<ResumeImportCandidate[]>([])
const selected = ref<Set<string>>(new Set())
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
let seq = 0

const resumeOptions = computed(() => resumes.value.map(item => ({ value: item.id, label: item.title?.trim() || '未命名简历' })))
const importable = computed(() => candidates.value.filter(item => !item.alreadyInLibrary))
const groups = computed(() => RECORD_TYPES
  .map(type => ({ type: type.value, label: type.label, items: candidates.value.filter(item => item.type === type.value) }))
  .filter(group => group.items.length))
const allChecked = computed<boolean | 'indeterminate'>(() => {
  if (!importable.value.length || !selected.value.size) return false
  return selected.value.size === importable.value.length ? true : 'indeterminate'
})

function period(item: ResumeImportCandidate): string {
  const fmt = (value?: string | null) => (value ? value.slice(0, 7).replace('-', '.') : '')
  if (!item.startDate && !item.endDate) return ''
  if (item.type === 'CERTIFICATE' || item.type === 'HONOR') return fmt(item.startDate)
  return `${fmt(item.startDate) || '?'} – ${fmt(item.endDate) || '至今'}`
}

function subtitle(item: ResumeImportCandidate): string {
  return [item.organization !== item.title ? item.organization : '', item.role !== item.title ? item.role : '', period(item)].filter(Boolean).join(' · ')
}

function toggle(item: ResumeImportCandidate, on: boolean): void {
  if (item.alreadyInLibrary) return
  const next = new Set(selected.value)
  if (on) next.add(item.key)
  else next.delete(item.key)
  selected.value = next
}

function toggleAll(on: boolean): void {
  selected.value = on ? new Set(importable.value.map(item => item.key)) : new Set()
}

async function loadCandidates(): Promise<void> {
  const current = ++seq
  candidates.value = []
  selected.value = new Set()
  if (!masterId.value) return
  loading.value = true
  error.value = ''
  try {
    const result = await fetchResumeImportCandidates(masterId.value)
    if (current !== seq) return
    candidates.value = result.items
    // Everything new is ticked by default; the user unticks what they do not want.
    selected.value = new Set(result.items.filter(item => !item.alreadyInLibrary).map(item => item.key))
  } catch (reason) {
    if (current === seq) error.value = errorMessage(reason, '读取简历内容失败')
  } finally {
    if (current === seq) loading.value = false
  }
}

async function start(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    resumes.value = (await listResumes()).filter(item => item.status !== 'ARCHIVED')
    masterId.value = resumes.value[0]?.id ?? ''
    await loadCandidates()
  } catch (reason) {
    error.value = errorMessage(reason, '读取简历列表失败')
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!selected.value.size || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const result = await importRecordsFromResume(masterId.value, [...selected.value])
    emit('imported', result)
  } catch (reason) {
    error.value = errorMessage(reason, '添加到资料库失败')
  } finally {
    submitting.value = false
  }
}

watch(() => props.open, open => { if (open) void start() }, { immediate: true })
watch(masterId, (value, previous) => { if (previous && value !== previous) void loadCandidates() })
</script>

<template>
  <UiDialog :open="open" title="从简历添加经历" description="勾选要放进资料库的条目，添加后可以继续编辑、补充成果。" width="lg" :dismissible="!submitting" @close="emit('close')">
    <div class="rid">
      <div v-if="resumes.length > 1" class="rid__pick">
        <span>来源简历</span>
        <UiSelect v-model="masterId" :options="resumeOptions" size="sm" aria-label="来源简历" />
      </div>

      <div v-if="loading" class="rid__loading"><UiSkeleton v-for="n in 4" :key="n" height="44px" radius="var(--radius-md)" /></div>
      <p v-else-if="error" class="rid__error" role="alert">{{ error }}</p>
      <div v-else-if="!resumes.length" class="rid__empty">
        <FileText :size="22" />
        <p>还没有可以读取的简历。先在简历工作台导入或新建一份简历。</p>
      </div>
      <div v-else-if="!candidates.length" class="rid__empty">
        <FileText :size="22" />
        <p>这份简历里还没有可以添加的经历、技能或荣誉。</p>
      </div>
      <template v-else>
        <div class="rid__bar">
          <UiCheckbox :model-value="allChecked" :disabled="!importable.length" :label="`全选（${importable.length} 项可添加）`" @update:model-value="toggleAll" />
          <span v-if="candidates.length > importable.length">{{ candidates.length - importable.length }} 项已在资料库</span>
        </div>
        <div class="rid__groups">
          <section v-for="group in groups" :key="group.type" class="rid__group">
            <h4>{{ group.label }}<small>{{ group.items.length }}</small></h4>
            <ul>
              <li
                v-for="item in group.items"
                :key="item.key"
                class="rid__item"
                :class="{ 'is-on': selected.has(item.key), 'is-done': item.alreadyInLibrary }"
              >
                <div class="rid__row" @click="toggle(item, !selected.has(item.key))">
                  <span @click.stop>
                    <UiCheckbox :model-value="item.alreadyInLibrary || selected.has(item.key)" :disabled="item.alreadyInLibrary || submitting" :aria-label="item.title" @update:model-value="toggle(item, $event)" />
                  </span>
                  <span class="rid__copy">
                    <strong>{{ item.title }}</strong>
                    <small v-if="subtitle(item)">{{ subtitle(item) }}</small>
                    <em v-if="item.description">{{ item.description }}</em>
                  </span>
                  <UiBadge v-if="item.alreadyInLibrary" tone="success" size="sm"><Check :size="11" />已在资料库</UiBadge>
                </div>
              </li>
            </ul>
          </section>
        </div>
      </template>
    </div>
    <template #footer>
      <UiButton variant="secondary" :disabled="submitting" @click="emit('close')">取消</UiButton>
      <UiButton :disabled="!selected.size || loading" :pending="submitting" @click="submit">
        {{ selected.size ? `添加 ${selected.size} 项到资料库` : '选择要添加的条目' }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.rid {
  display: grid;
  gap: var(--space-4);
}

.rid__pick {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}

.rid__pick > :last-child {
  flex: 1;
  max-width: 320px;
}

.rid__loading {
  display: grid;
  gap: var(--space-2);
}

.rid__error {
  color: var(--color-danger-text);
  font-size: var(--fs-sm);
}

.rid__empty {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-4);
  color: var(--text-tertiary);
  font-size: var(--fs-sm);
  text-align: center;
}

.rid__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.rid__groups {
  display: grid;
  gap: var(--space-4);
  max-height: min(52vh, 460px);
  overflow-y: auto;
  padding-right: 4px;
  overscroll-behavior: contain;
}

.rid__group h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-weight: 650;
  letter-spacing: 0.02em;
}

.rid__group h4 small {
  padding: 0 6px;
  border-radius: var(--radius-full);
  background: var(--surface-3);
  color: var(--text-tertiary);
  font-size: 11px;
}

.rid__group ul {
  list-style: none;
  display: grid;
  gap: 6px;
}

.rid__row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: 10px 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
}

.rid__item.is-on .rid__row {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
}

.rid__item.is-done .rid__row {
  cursor: default;
  opacity: 0.72;
}

.rid__copy {
  flex: 1;
  display: grid;
  gap: 2px;
  min-width: 0;
}

.rid__copy strong {
  font-size: var(--fs-sm);
  font-weight: 600;
}

.rid__copy small {
  color: var(--text-tertiary);
  font-size: var(--fs-xs);
}

.rid__copy em {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-style: normal;
  line-height: 1.6;
  white-space: pre-line;
}
</style>
