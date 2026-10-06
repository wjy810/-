<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  Archive, ArchiveRestore, ArrowRight, BarChart3, BriefcaseBusiness, CheckCircle2, Clock3, Copy,
  LayoutList, Lightbulb, MoreVertical, Pencil, Plus, Search, ShieldCheck,
  Sparkles, Tags, Trash2, WandSparkles,
} from 'lucide-vue-next'
import AppDatePicker from '@/shared/ui/AppDatePicker.vue'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import PageState from '@/shared/ui/PageState.vue'
import UiDropdownMenu, { type MenuItem } from '@/shared/ui/UiDropdownMenu.vue'
import { errorMessage } from '@/shared/api/types'
import {
  acceptCareerAiCandidate, archiveCareerRecord, copyCareerRecord, createCareerRecord,
  fetchCareerAiAvailability, fetchCareerAiCandidates, fetchCareerRecords, generateCareerAiCandidate,
  rejectCareerAiCandidate, restoreCareerRecord, updateCareerRecord,
} from '../services/careerLibraryApi'
import { usePagedList } from '../usePagedList'
import type { CareerAiAvailability, CareerAiCandidate, CareerOverview, CareerRecord, CareerRecordWrite } from '../types'

const props = defineProps<{
  overview: CareerOverview
  q: string
  type: string
  status: string
  sort: string
  layout: string
}>()
const emit = defineEmits<{
  query: [value: Record<string, string>]
  changed: []
  /** Open the file picker in the files view with this category preselected. */
  uploadFile: [category: string]
  /** Permanent deletion goes through the data-rights page, which previews the impact first. */
  remove: [target: { type: 'CAREER_RECORD'; id: string }]
  notice: [message: string]
  error: [message: string]
}>()

const {
  items: records, total, error: loadError, loading, loaded, load, hasMore, loadingMore, loadMore,
} = usePagedList((page, size) => fetchCareerRecords({
  keyword: props.q, type: props.type, status: props.status || 'ACTIVE', page, size,
}))
const pending = ref('')
const modalOpen = ref(false)
const selected = ref<CareerRecord | null>(null)
const form = reactive<CareerRecordWrite>({ type: 'EXPERIENCE', title: '' })
const aiAvailability = ref<CareerAiAvailability | null>(null)
const aiCandidate = ref<CareerAiCandidate | null>(null)
const aiRecord = ref<CareerRecord | null>(null)
const aiOpen = ref(false)
const aiPending = ref(false)

const typeOptions = [
  { value: '', label: '全部类型' }, { value: 'EDUCATION', label: '教育经历' },
  { value: 'EXPERIENCE', label: '工作经历' }, { value: 'PROJECT', label: '项目经历' },
  { value: 'ORGANIZATION', label: '组织与社团' }, { value: 'SKILL', label: '专业技能' },
  { value: 'CERTIFICATE', label: '证书' }, { value: 'HONOR', label: '荣誉' },
  { value: 'LANGUAGE', label: '语言能力' }, { value: 'ACHIEVEMENT', label: '成果记录' },
]
const editableTypes = typeOptions.filter((item) => item.value)
const statusOptions = [
  { value: 'ACTIVE', label: '使用中' }, { value: 'ARCHIVED', label: '已归档' }, { value: 'ALL', label: '全部状态' },
]
const sortOptions = [
  { value: 'RECENT', label: '最近更新' }, { value: 'OLDEST', label: '最早更新' },
  { value: 'START_DESC', label: '开始时间倒序' }, { value: 'TITLE', label: '标题名称' },
]
const OUTCOME_TYPES = new Set(['EXPERIENCE', 'PROJECT', 'ORGANIZATION'])
const DATED_TYPES = new Set(['EDUCATION', 'EXPERIENCE', 'PROJECT', 'ORGANIZATION'])

const sortedRecords = computed(() => {
  const list = [...records.value]
  if (props.sort === 'OLDEST') return list.sort((a, b) => a.updatedAt.localeCompare(b.updatedAt))
  if (props.sort === 'START_DESC') return list.sort((a, b) => (b.startDate || '').localeCompare(a.startDate || ''))
  if (props.sort === 'TITLE') return list.sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'))
  return list.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
})
const missingOutcome = computed(() => records.value.filter((record) => OUTCOME_TYPES.has(record.type) && !record.coreOutcome?.trim()))
const missingDates = computed(() => records.value.filter((record) => DATED_TYPES.has(record.type) && !record.startDate))
const incompleteCount = computed(() => new Set([...missingOutcome.value, ...missingDates.value].map((record) => record.id)).size)

async function showMore(): Promise<void> {
  try { await loadMore() }
  catch (reason) { emit('error', errorMessage(reason, '更多经历读取失败，已显示的内容不受影响')) }
}

function openCreate(type = 'EXPERIENCE'): void {
  selected.value = null
  Object.assign(form, {
    type, title: '', organization: '', role: '', startDate: '', endDate: '', location: '',
    description: '', coreOutcome: '', url: '', payload: {}, strength: 'MEDIUM', sortOrder: records.value.length,
    expectedVersion: undefined,
  })
  modalOpen.value = true
}

/** Opens the record's edit form and moves focus to the field the user came to fill. */
function openEdit(record: CareerRecord, focusId = ''): void {
  selected.value = record
  Object.assign(form, {
    type: record.type, title: record.title, organization: record.organization || '', role: record.role || '',
    startDate: record.startDate || '', endDate: record.endDate || '', location: record.location || '',
    description: record.description || '', coreOutcome: record.coreOutcome || '', url: record.url || '',
    payload: record.payload, strength: record.strength || 'MEDIUM', sortOrder: record.sortOrder,
    expectedVersion: record.version,
  })
  modalOpen.value = true
  if (focusId) void nextTick(() => window.setTimeout(() => document.getElementById(focusId)?.focus(), 240))
}

function recordMenu(record: CareerRecord): MenuItem[] {
  const archived = record.status === 'ARCHIVED'
  return [
    { label: '编辑', icon: Pencil, onSelect: () => openEdit(record) },
    { label: '复制为新记录', icon: Copy, disabled: pending.value === record.id, onSelect: () => void copyRecord(record) },
    { label: archived ? '恢复使用' : '归档', icon: archived ? ArchiveRestore : Archive, disabled: pending.value === record.id, onSelect: () => void toggleArchive(record) },
    { type: 'separator' },
    { label: '永久删除…', icon: Trash2, danger: true, onSelect: () => emit('remove', { type: 'CAREER_RECORD', id: record.id }) },
  ]
}

async function save(): Promise<void> {
  if (!form.title.trim()) { emit('error', '请填写经历标题。'); return }
  pending.value = 'save'
  try {
    if (selected.value) await updateCareerRecord(selected.value.id, form)
    else await createCareerRecord(form)
    modalOpen.value = false
    await load()
    emit('changed')
    emit('notice', selected.value ? '经历资料已更新。' : '经历资料已添加。')
  } catch (reason) { emit('error', errorMessage(reason, '经历资料保存失败')) }
  finally { pending.value = '' }
}

async function copyRecord(record: CareerRecord): Promise<void> {
  pending.value = record.id
  try { await copyCareerRecord(record); await load(); emit('changed'); emit('notice', '已创建一份可独立编辑的副本。') }
  catch (reason) { emit('error', errorMessage(reason, '复制失败')) }
  finally { pending.value = '' }
}

async function toggleArchive(record: CareerRecord): Promise<void> {
  pending.value = record.id
  try {
    if (record.status === 'ARCHIVED') await restoreCareerRecord(record)
    else await archiveCareerRecord(record)
    await load(); emit('changed')
    emit('notice', record.status === 'ARCHIVED' ? '记录已恢复使用。' : '记录已归档。')
  } catch (reason) { emit('error', errorMessage(reason, '记录状态更新失败')) }
  finally { pending.value = '' }
}

async function loadAiAvailability(): Promise<void> {
  try { aiAvailability.value = await fetchCareerAiAvailability() }
  catch { aiAvailability.value = { available: false, model: '', reason: 'AI_CHANNEL_UNAVAILABLE' } }
}

async function openAiOptimize(record: CareerRecord): Promise<void> {
  if (!aiAvailability.value?.available) {
    emit('error', 'AI 服务暂不可用，请稍后再试。')
    return
  }
  aiPending.value = true
  aiRecord.value = record
  try {
    const pendingCandidates = await fetchCareerAiCandidates(record.id)
    aiCandidate.value = pendingCandidates[0] || await generateCareerAiCandidate(record.id)
    aiOpen.value = true
  } catch (reason) { emit('error', errorMessage(reason, 'AI 优化建议生成失败，原经历没有变化')) }
  finally { aiPending.value = false }
}

async function acceptAi(): Promise<void> {
  if (!aiCandidate.value || !aiRecord.value) return
  aiPending.value = true
  try {
    await acceptCareerAiCandidate(aiCandidate.value, aiRecord.value)
    aiOpen.value = false
    aiCandidate.value = null
    await load()
    emit('changed')
    emit('notice', '已采纳 AI 优化建议，经历内容已更新。')
  } catch (reason) { emit('error', errorMessage(reason, '采纳建议失败')) }
  finally { aiPending.value = false }
}

async function rejectAi(): Promise<void> {
  if (!aiCandidate.value) return
  aiPending.value = true
  try {
    await rejectCareerAiCandidate(aiCandidate.value)
    aiOpen.value = false
    aiCandidate.value = null
    emit('notice', '已放弃这条 AI 优化建议，原经历保持不变。')
  } catch (reason) { emit('error', errorMessage(reason, '放弃建议失败')) }
  finally { aiPending.value = false }
}

function lines(record: CareerRecord): string[] {
  return (record.description || '').split(/\n+/).map((line) => line.replace(/^[•·*-]\s*/, '').trim()).filter(Boolean)
}

function skills(record: CareerRecord): string[] {
  const value = record.payload.skills || record.payload.tags
  return Array.isArray(value) ? value.map(String).slice(0, 6) : []
}

function labelFor(type: string): string {
  return typeOptions.find((item) => item.value === type)?.label || type
}

function period(record: CareerRecord): string {
  const format = (value?: string | null): string => value?.match(/^\d{4}-\d{2}/)?.[0]?.replace('-', '.') || ''
  return `${format(record.startDate) || '时间待补'} - ${format(record.endDate) || '至今'}`
}

function setQuery(key: string, value: string | number): void {
  emit('query', { [key]: String(value) })
}

watch(() => [props.q, props.type, props.status], load)
onMounted(() => { void load(); void loadAiAvailability() })
defineExpose({ openCreate })
</script>

<template>
  <div class="career-two-column records-tab">
    <main class="career-main-column career-stack">
      <section class="records-stats">
        <article class="career-card"><span class="stat-icon blue"><BriefcaseBusiness :size="21" /></span><div><small>全部记录</small><strong>{{ overview.totalRecords }}</strong></div></article>
        <article class="career-card"><span class="stat-icon green"><CheckCircle2 :size="21" /></span><div><small>已用于简历</small><strong>{{ overview.inUseRecords }}</strong></div></article>
        <article class="career-card"><span class="stat-icon purple"><Tags :size="21" /></span><div><small>技能与证书</small><strong>{{ overview.skillKeywords }}</strong></div></article>
        <article class="career-card"><span class="stat-icon cyan"><BarChart3 :size="21" /></span><div><small>资料健康度</small><strong>{{ overview.healthScore }}%</strong></div></article>
      </section>

      <section class="record-toolbar">
        <label class="career-search"><Search :size="18" /><input :value="q" placeholder="搜索标题、组织、岗位或内容" @input="setQuery('q', ($event.target as HTMLInputElement).value)" /></label>
        <AppSelect :model-value="type" :options="typeOptions" aria-label="经历类型" @update:model-value="setQuery('type', $event)" />
        <AppSelect :model-value="status" :options="statusOptions" aria-label="经历状态" @update:model-value="setQuery('status', $event)" />
        <AppSelect :model-value="sort" :options="sortOptions" aria-label="经历排序" @update:model-value="setQuery('sort', $event)" />
        <div class="layout-toggle" aria-label="布局">
          <button type="button" :class="{ active: layout !== 'compact' }" title="列表视图" @click="setQuery('layout', 'list')"><LayoutList :size="18" /></button>
          <button type="button" :class="{ active: layout === 'compact' }" title="紧凑视图" @click="setQuery('layout', 'compact')"><MoreVertical :size="18" /></button>
        </div>
      </section>

      <PageState class="career-list-state" :loading="loading" :error="loadError" :loaded="loaded" :empty="!sortedRecords.length" error-title="经历资料读取失败" compact @retry="load">
        <template #skeleton><div class="records-loading"><span v-for="n in 3" :key="n" class="career-card bone-card" /></div></template>
        <template #empty>
          <div class="career-card career-empty"><BriefcaseBusiness :size="30" /><strong>还没有符合条件的经历</strong><p>添加真实经历后可用于简历和经授权的 AI 辅助。</p><button type="button" @click="openCreate()"><Plus :size="16" />添加记录</button></div>
        </template>
        <section class="record-timeline" :class="{ compact: layout === 'compact' }" aria-live="polite">
          <article v-for="(record, index) in sortedRecords" :id="record.id" :key="record.id" class="career-card timeline-record">
            <span class="timeline-dot" :class="`dot-${index % 3}`" />
            <div class="record-core">
              <header>
                <div><h3>{{ record.title }}</h3><span>{{ record.organization || record.role || '' }}</span></div>
                <em v-if="record.resumeReferenceCount"><CheckCircle2 :size="14" />已用于 {{ record.resumeReferenceCount }} 份简历</em>
                <em v-else-if="record.status === 'ARCHIVED'">已归档</em>
              </header>
              <div class="record-period"><Clock3 :size="14" />{{ period(record) }}<span>{{ labelFor(record.type) }}</span></div>
              <ul v-if="lines(record).length"><li v-for="line in lines(record).slice(0, layout === 'compact' ? 1 : 4)" :key="line">{{ line }}</li></ul>
              <p v-else class="record-placeholder">尚未填写详细描述，可编辑后补充职责、行动和成果。</p>
              <div v-if="skills(record).length" class="record-tags"><span v-for="skill in skills(record)" :key="skill">{{ skill }}</span></div>
            </div>
            <div class="record-actions">
              <div>
                <button type="button" title="编辑" @click="openEdit(record)"><Pencil :size="17" /></button>
                <button type="button" title="复制" :disabled="pending === record.id" @click="copyRecord(record)"><Copy :size="17" /></button>
                <button type="button" :title="record.status === 'ARCHIVED' ? '恢复' : '归档'" :disabled="pending === record.id" @click="toggleArchive(record)"><Archive :size="17" /></button>
                <UiDropdownMenu :items="recordMenu(record)"><button type="button" title="更多操作" aria-label="更多操作"><MoreVertical :size="17" /></button></UiDropdownMenu>
              </div>
              <button class="ai-optimize" type="button" :disabled="!aiAvailability?.available || aiPending" :title="aiAvailability?.available ? '生成优化建议，确认后才会修改' : 'AI 服务暂不可用'" @click="openAiOptimize(record)"><WandSparkles :size="16" />{{ aiPending && aiRecord?.id === record.id ? '生成中' : 'AI 优化' }}</button>
            </div>
          </article>
        </section>
        <div v-if="hasMore" class="career-load-more">
          <span>已显示 {{ records.length }} / {{ total }} 条</span>
          <button class="career-btn ghost" type="button" :disabled="loadingMore" @click="showMore">{{ loadingMore ? '加载中…' : '加载更多' }}</button>
        </div>
      </PageState>
    </main>

    <aside class="career-aside-column">
      <section class="career-card aside-card health-card"><h3>资料健康度</h3><div class="career-ring" :style="{ '--ring-value': `${overview.healthScore * 3.6}deg` }"><strong>{{ overview.healthScore }}%</strong><small>{{ overview.healthScore >= 80 ? '优秀' : '待完善' }}</small></div><p>持续完善资料，让 AI 更懂你，生成更准确的简历建议。</p></section>
      <section class="career-card aside-card">
        <header><h3>待补充项</h3><span v-if="loaded">{{ incompleteCount }}</span></header>
        <div class="record-tips">
          <button type="button" :disabled="!missingOutcome.length" @click="missingOutcome[0] && openEdit(missingOutcome[0], 'record-core-outcome')">
            <span class="red"><BarChart3 :size="17" /></span>
            <div><strong>补充可量化的成果</strong><small>{{ missingOutcome.length ? `${missingOutcome.length} 条经历还没有填写核心成果` : records.length ? '列表中的经历都已填写核心成果' : '添加经历后会在这里提示' }}</small></div><ArrowRight :size="15" />
          </button>
          <button type="button" @click="emit('uploadFile', 'CERTIFICATE')">
            <span class="orange"><ShieldCheck :size="17" /></span>
            <div><strong>上传证明材料</strong><small>证书、成绩单或工作样本保存到“文件资料”</small></div><ArrowRight :size="15" />
          </button>
          <button type="button" @click="openCreate('SKILL')">
            <span class="blue"><Tags :size="17" /></span>
            <div><strong>添加专业技能</strong><small>{{ overview.skillKeywords ? `已记录 ${overview.skillKeywords} 项技能与证书` : '还没有记录技能或证书' }}</small></div><ArrowRight :size="15" />
          </button>
          <button type="button" :disabled="!missingDates.length" @click="missingDates[0] && openEdit(missingDates[0], 'record-start-date')">
            <span class="purple"><Clock3 :size="17" /></span>
            <div><strong>补全经历时间</strong><small>{{ missingDates.length ? `${missingDates.length} 条经历还没有填写开始时间` : records.length ? '列表中的经历都已填写开始时间' : '添加经历后会在这里提示' }}</small></div><ArrowRight :size="15" />
          </button>
        </div>
      </section>
      <section class="career-card aside-card usage-tip"><h3>使用建议</h3><div><Lightbulb :size="20" /><p>被简历引用的经历会标记“已用于简历”；暂时不用的经历可以归档，之后随时恢复。</p></div></section>
    </aside>

    <AppModal :open="modalOpen" :title="selected ? '编辑经历资料' : '添加经历资料'" :width="720" @close="modalOpen = false">
      <form id="career-record-form" class="record-form-grid" @submit.prevent="save">
        <label><span>资料类型</span><AppSelect v-model="form.type" :options="editableTypes" aria-label="资料类型" /></label>
        <label><span>标题</span><input v-model.trim="form.title" class="career-input" placeholder="如：数据分析实习生" /></label>
        <label><span>组织或公司</span><input v-model.trim="form.organization" class="career-input" placeholder="填写真实组织" /></label>
        <label><span>岗位或角色</span><input v-model.trim="form.role" class="career-input" placeholder="填写真实岗位" /></label>
        <label><span>开始时间</span><AppDatePicker id="record-start-date" :model-value="form.startDate?.slice(0, 7)" mode="month" @update:model-value="form.startDate = $event" /></label>
        <label><span>结束时间</span><AppDatePicker :model-value="form.endDate?.slice(0, 7)" mode="month" @update:model-value="form.endDate = $event" /></label>
        <label class="span-2"><span>地点</span><input v-model.trim="form.location" class="career-input" placeholder="城市或远程" /></label>
        <label class="span-2"><span>经历要点</span><textarea v-model="form.description" class="career-input" rows="5" placeholder="每行填写一条真实职责、行动或成果" /></label>
        <label class="span-2"><span>核心成果</span><textarea id="record-core-outcome" v-model="form.coreOutcome" class="career-input" rows="2" placeholder="填写可核验的结果，不确定的数字请勿填写" /></label>
      </form>
      <template #footer><button class="career-btn ghost" type="button" @click="modalOpen = false">取消</button><button class="career-btn primary" type="submit" form="career-record-form" :disabled="pending === 'save'"><Sparkles :size="16" />{{ pending === 'save' ? '保存中' : '保存记录' }}</button></template>
    </AppModal>

    <AppModal :open="aiOpen" title="AI 优化建议" :width="760" @close="aiOpen = false">
      <div v-if="aiCandidate" class="career-ai-review">
        <div class="career-ai-review__summary"><Sparkles :size="19" /><div><strong>{{ aiRecord?.title }}</strong><p>{{ aiCandidate.proposed.reason || 'AI 仅基于当前经历中的确认事实进行重组。' }}</p></div><span>{{ aiCandidate.modelName }}</span></div>
        <section v-if="aiCandidate.diff.description?.changed"><header><strong>经历要点</strong><span>待确认</span></header><div class="career-ai-diff"><article><small>原内容</small><p>{{ aiCandidate.diff.description.before || '未填写' }}</p></article><article><small>优化建议</small><p>{{ aiCandidate.diff.description.after }}</p></article></div></section>
        <section v-if="aiCandidate.diff.coreOutcome?.changed"><header><strong>核心成果</strong><span>待确认</span></header><div class="career-ai-diff"><article><small>原内容</small><p>{{ aiCandidate.diff.coreOutcome.before || '未填写' }}</p></article><article><small>优化建议</small><p>{{ aiCandidate.diff.coreOutcome.after }}</p></article></div></section>
        <section class="career-ai-sources"><header><strong>依据的原文</strong><span>{{ aiCandidate.sourceRefs.length }} 项</span></header><blockquote v-for="source in aiCandidate.sourceRefs" :key="source.quote">{{ source.quote }}</blockquote></section>
      </div>
      <template #footer><button class="career-btn ghost" type="button" :disabled="aiPending" @click="rejectAi">不采纳</button><button class="career-btn primary" type="button" :disabled="aiPending" @click="acceptAi"><CheckCircle2 :size="16" />{{ aiPending ? '处理中' : '采纳并更新经历' }}</button></template>
    </AppModal>
  </div>
</template>
