<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import type { CSSProperties } from 'vue'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { listJobTaxonomy } from '../services/aiResumeApi'
import type { JobTaxonomyNode, JobTaxonomySelection } from '../types'

const props = withDefaults(defineProps<{
  selectedName?: string
  selectedNodeId?: string
  disabled?: boolean
}>(), {
  selectedName: '',
  selectedNodeId: '',
  disabled: false,
})

const emit = defineEmits<{
  select: [selection: JobTaxonomySelection]
}>()

const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const open = ref(false)
const pending = ref(false)
const searchPending = ref(false)
const error = ref('')
const categories = ref<JobTaxonomyNode[]>([])
const groups = ref<JobTaxonomyNode[]>([])
const jobs = ref<JobTaxonomyNode[]>([])
const activeCategoryId = ref('')
const activeGroupId = ref('')
const keyword = ref('')
const searchResults = ref<JobTaxonomyNode[]>([])
const panelStyle = ref<CSSProperties>({})
const compactPanel = ref(false)
let browseRequest = 0
let searchRequest = 0
let searchTimer = 0
let positionFrame = 0

const activeCategory = computed(() => categories.value.find((item) => item.id === activeCategoryId.value) ?? null)
const activeGroup = computed(() => groups.value.find((item) => item.id === activeGroupId.value) ?? null)

async function toggle(): Promise<void> {
  if (props.disabled) return
  if (open.value) {
    close()
    return
  }
  open.value = true
  await nextTick()
  updatePanelPosition()
  if (categories.value.length === 0) void loadCategories()
}

function close(): void {
  open.value = false
}

async function loadCategories(): Promise<void> {
  const request = ++browseRequest
  pending.value = true
  error.value = ''
  try {
    const result = await listJobTaxonomy()
    if (request !== browseRequest) return
    categories.value = result.filter((item) => item.level === 'CATEGORY')
    const initial = categories.value[0]
    if (initial) await chooseCategory(initial)
  } catch {
    if (request === browseRequest) error.value = '岗位分类读取失败，请重试。'
  } finally {
    if (request === browseRequest) pending.value = false
  }
}

async function chooseCategory(category: JobTaxonomyNode): Promise<void> {
  const request = ++browseRequest
  activeCategoryId.value = category.id
  activeGroupId.value = ''
  groups.value = []
  jobs.value = []
  pending.value = true
  error.value = ''
  try {
    const result = await listJobTaxonomy(category.id)
    if (request !== browseRequest) return
    groups.value = result.filter((item) => item.level === 'GROUP')
    const first = groups.value[0]
    if (!first) return
    activeGroupId.value = first.id
    const roles = await listJobTaxonomy(first.id)
    if (request === browseRequest) jobs.value = roles.filter((item) => item.level === 'JOB')
  } catch {
    if (request === browseRequest) error.value = '该分类暂时无法读取，请重试。'
  } finally {
    if (request === browseRequest) pending.value = false
  }
}

async function chooseGroup(group: JobTaxonomyNode): Promise<void> {
  const request = ++browseRequest
  activeGroupId.value = group.id
  jobs.value = []
  pending.value = true
  error.value = ''
  try {
    const result = await listJobTaxonomy(group.id)
    if (request === browseRequest) jobs.value = result.filter((item) => item.level === 'JOB')
  } catch {
    if (request === browseRequest) error.value = '该岗位组暂时无法读取，请重试。'
  } finally {
    if (request === browseRequest) pending.value = false
  }
}

function scheduleSearch(): void {
  window.clearTimeout(searchTimer)
  const value = keyword.value.trim()
  if (!value) {
    searchResults.value = []
    searchPending.value = false
    return
  }
  searchTimer = window.setTimeout(() => void runSearch(value), 260)
}

async function runSearch(value: string): Promise<void> {
  const request = ++searchRequest
  searchPending.value = true
  error.value = ''
  try {
    const result = await listJobTaxonomy(undefined, value)
    if (request === searchRequest) searchResults.value = result.filter((item) => item.level === 'JOB')
  } catch {
    if (request === searchRequest) error.value = '岗位搜索失败，请稍后重试。'
  } finally {
    if (request === searchRequest) searchPending.value = false
  }
}

function clearSearch(): void {
  keyword.value = ''
  searchResults.value = []
  searchPending.value = false
  searchRequest += 1
}

function chooseJob(job: JobTaxonomyNode): void {
  const groupId = job.groupId || job.parentId || activeGroupId.value
  const categoryId = job.categoryId || activeCategoryId.value
  if (!groupId || !categoryId) {
    error.value = '岗位层级信息不完整，请重新选择。'
    return
  }
  emit('select', { job, categoryId, groupId })
  close()
}

function onPointerDown(event: PointerEvent): void {
  if (!open.value) return
  const target = event.target as Node
  if (!root.value?.contains(target) && !panel.value?.contains(target)) close()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && open.value) close()
}

function updatePanelPosition(): void {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const viewportGap = 12
  const panelGap = 6
  const width = Math.max(280, Math.min(680, viewportWidth - viewportGap * 2))
  const desiredLeft = rect.left + rect.width / 2 - width / 2
  const left = Math.min(
    Math.max(desiredLeft, viewportGap),
    Math.max(viewportGap, viewportWidth - width - viewportGap),
  )
  const roomBelow = viewportHeight - rect.bottom - panelGap - viewportGap
  const roomAbove = rect.top - panelGap - viewportGap
  const placeAbove = roomBelow < 360 && roomAbove > roomBelow
  const availableHeight = Math.max(160, placeAbove ? roomAbove : roomBelow)
  compactPanel.value = width < 560
  const bodyHeight = Math.max(116, Math.min(compactPanel.value ? 460 : 390, availableHeight - 44))

  panelStyle.value = {
    left: `${Math.round(left)}px`,
    width: `${Math.round(width)}px`,
    top: placeAbove ? 'auto' : `${Math.round(rect.bottom + panelGap)}px`,
    bottom: placeAbove ? `${Math.round(viewportHeight - rect.top + panelGap)}px` : 'auto',
    '--taxonomy-body-height': `${Math.floor(bodyHeight)}px`,
  } as CSSProperties
}

function schedulePanelPosition(): void {
  if (!open.value || positionFrame) return
  positionFrame = window.requestAnimationFrame(() => {
    positionFrame = 0
    updatePanelPosition()
  })
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', schedulePanelPosition)
  window.addEventListener('scroll', schedulePanelPosition, true)
})

onBeforeUnmount(() => {
  window.clearTimeout(searchTimer)
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', schedulePanelPosition)
  window.removeEventListener('scroll', schedulePanelPosition, true)
  if (positionFrame) window.cancelAnimationFrame(positionFrame)
})
</script>

<template>
  <div ref="root" class="job-taxonomy-picker">
    <button
      ref="trigger"
      type="button"
      class="job-taxonomy-trigger"
      :class="{ selected: selectedNodeId }"
      :disabled="disabled"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="toggle"
    >
      <span>
        <small>{{ selectedNodeId ? '已选择标准岗位' : '请选择岗位类型' }}</small>
        <strong>{{ selectedName || '从岗位库中选择目标岗位' }}</strong>
      </span>
      <AppIcon :name="open ? 'chevron-up' : 'chevron-down'" :size="16" />
    </button>

    <p v-if="selectedName && !selectedNodeId" class="job-taxonomy-legacy">
      该岗位尚未关联标准岗位 ID，请从岗位库重新选择。
    </p>

    <Teleport to="body">
    <Transition name="taxonomy-panel">
    <section
      v-if="open"
      ref="panel"
      class="job-taxonomy-panel"
      :class="{ 'job-taxonomy-panel--compact': compactPanel }"
      :style="panelStyle"
      role="dialog"
      aria-label="选择目标岗位"
    >
      <header class="job-taxonomy-search">
        <AppIcon name="search" :size="15" />
        <input v-model="keyword" type="search" placeholder="搜索岗位名称、英文缩写或别名" aria-label="搜索岗位" @input="scheduleSearch" />
        <button v-if="keyword" type="button" title="清空搜索" aria-label="清空岗位搜索" @click="clearSearch"><AppIcon name="x" :size="14" /></button>
      </header>

      <p v-if="error" class="job-taxonomy-error" role="alert">{{ error }}</p>

      <div v-if="keyword.trim()" class="job-taxonomy-results">
        <p v-if="searchPending"><AppIcon name="loader" :size="15" />正在搜索岗位库…</p>
        <p v-else-if="!searchResults.length">没有匹配的标准岗位，请尝试岗位简称或英文缩写。</p>
        <template v-else>
          <button
            v-for="job in searchResults"
            :key="job.id"
            type="button"
            :class="{ active: job.id === selectedNodeId }"
            @click="chooseJob(job)"
          >
            <span><strong>{{ job.displayName }}</strong><small>{{ job.catalogOccupationCode }}</small></span>
            <AppIcon v-if="job.id === selectedNodeId" name="check" :size="14" />
          </button>
        </template>
      </div>

      <div v-else class="job-taxonomy-browser">
        <nav aria-label="职业大类">
          <span>职业大类</span>
          <button
            v-for="category in categories"
            :key="category.id"
            type="button"
            :class="{ active: category.id === activeCategoryId }"
            @click="chooseCategory(category)"
          >
            {{ category.displayName }}<AppIcon name="chevron-right" :size="13" />
          </button>
        </nav>

        <nav aria-label="岗位分组">
          <span>岗位分组</span>
          <button
            v-for="group in groups"
            :key="group.id"
            type="button"
            :class="{ active: group.id === activeGroupId }"
            @click="chooseGroup(group)"
          >
            {{ group.displayName }}
          </button>
        </nav>

        <section class="job-taxonomy-roles" aria-live="polite">
          <header><div><span>{{ activeCategory?.displayName || '目标岗位' }}</span><strong>{{ activeGroup?.displayName || '请选择岗位分组' }}</strong></div><small>{{ jobs.length }} 个岗位</small></header>
          <p v-if="pending"><AppIcon name="loader" :size="15" />正在读取岗位…</p>
          <p v-else-if="!jobs.length">当前分组暂无可选岗位。</p>
          <div v-else role="listbox" aria-label="标准岗位">
            <button
              v-for="job in jobs"
              :key="job.id"
              type="button"
              role="option"
              :aria-selected="job.id === selectedNodeId"
              :class="{ active: job.id === selectedNodeId }"
              @click="chooseJob(job)"
            >
              {{ job.displayName }}
            </button>
          </div>
        </section>
      </div>
    </section>
    </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.job-taxonomy-picker { min-width: 0; display: grid; gap: 6px; }
.job-taxonomy-trigger { width: 100%; min-height: 52px; padding: 9px 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--text); text-align: left; background: #fff; border: 1px solid #cfd8e5; border-radius: 7px; transition: border-color .18s ease, box-shadow .18s ease, background-color .18s ease; }
.job-taxonomy-trigger:hover { border-color: #9ab3d5; background: #fbfdff; }
.job-taxonomy-trigger[aria-expanded="true"] { border-color: #6f98dd; box-shadow: 0 0 0 3px rgba(37, 99, 235, .09); }
.job-taxonomy-trigger.selected { border-color: #91afe0; background: #f8fbff; box-shadow: inset 3px 0 #4f7fc8; }
.job-taxonomy-trigger > span { min-width: 0; display: grid; gap: 2px; }
.job-taxonomy-trigger small { color: var(--text-3); font-size: 10px; font-weight: 500; }
.job-taxonomy-trigger strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.job-taxonomy-legacy { color: #9a3412; font-size: 10px; }
.job-taxonomy-panel, .job-taxonomy-panel * { box-sizing: border-box; }
.job-taxonomy-panel { position: fixed; z-index: 270; min-width: 0; display: grid; overflow: hidden; color: var(--text); border: 1px solid #c7d4e5; border-radius: 9px; background: #fff; box-shadow: 0 18px 42px rgba(15, 23, 42, .16), 0 4px 12px rgba(15, 23, 42, .08); transform-origin: top center; }
.job-taxonomy-panel button, .job-taxonomy-panel input { font: inherit; letter-spacing: 0; }
.taxonomy-panel-enter-active { transition: opacity .28s ease, transform .38s cubic-bezier(.16, 1, .3, 1); }
.taxonomy-panel-leave-active { transition: opacity .18s ease, transform .18s ease; }
.taxonomy-panel-enter-from, .taxonomy-panel-leave-to { opacity: 0; transform: translateY(-6px) scale(.99); }
.job-taxonomy-search { min-height: 42px; padding: 0 10px; display: grid; grid-template-columns: 18px minmax(0, 1fr) 28px; align-items: center; gap: 5px; border-bottom: 1px solid var(--border); }
.job-taxonomy-search:focus-within { color: #2563eb; box-shadow: inset 0 -2px #8eb1ea; }
.job-taxonomy-search input { min-width: 0; height: 40px; padding: 0; color: var(--text); background: transparent; border: 0; outline: 0; font-size: 12px; }
.job-taxonomy-search button { width: 28px; height: 28px; display: grid; place-items: center; color: var(--text-3); background: transparent; border: 0; border-radius: 5px; }
.job-taxonomy-error { padding: 8px 10px; color: var(--danger); background: #fff6f6; border-bottom: 1px solid #fecaca; font-size: 10px; }
.job-taxonomy-browser { height: var(--taxonomy-body-height, 390px); min-height: 0; display: grid; grid-template-columns: 130px 180px minmax(0, 1fr); }
.job-taxonomy-browser > nav { min-width: 0; padding: 8px; display: grid; align-content: start; gap: 3px; overflow-y: auto; border-right: 1px solid var(--border); }
.job-taxonomy-browser > nav, .job-taxonomy-roles, .job-taxonomy-results { scrollbar-width: thin; scrollbar-color: #c1c9d4 transparent; }
.job-taxonomy-browser > nav::-webkit-scrollbar, .job-taxonomy-roles::-webkit-scrollbar, .job-taxonomy-results::-webkit-scrollbar { width: 6px; height: 6px; }
.job-taxonomy-browser > nav::-webkit-scrollbar-track, .job-taxonomy-roles::-webkit-scrollbar-track, .job-taxonomy-results::-webkit-scrollbar-track { background: transparent; }
.job-taxonomy-browser > nav::-webkit-scrollbar-thumb, .job-taxonomy-roles::-webkit-scrollbar-thumb, .job-taxonomy-results::-webkit-scrollbar-thumb { background: #c1c9d4; background-clip: padding-box; border: 1px solid transparent; border-radius: 999px; }
.job-taxonomy-browser > nav::-webkit-scrollbar-thumb:hover, .job-taxonomy-roles::-webkit-scrollbar-thumb:hover, .job-taxonomy-results::-webkit-scrollbar-thumb:hover { background: #8f9cad; background-clip: padding-box; }
.job-taxonomy-browser > nav > span { padding: 5px 7px; color: var(--text-3); font-size: 9px; font-weight: 600; }
.job-taxonomy-browser > nav button { min-width: 0; min-height: 34px; padding: 7px; display: flex; justify-content: space-between; align-items: center; gap: 5px; color: var(--text-2); text-align: left; background: transparent; border: 0; border-radius: 5px; font-size: 10px; transition: color .16s ease, background-color .16s ease; }
.job-taxonomy-browser > nav button:hover { background: #f4f7fb; }
.job-taxonomy-browser > nav button.active { color: #175cd3; background: #eaf2ff; font-weight: 700; }
.job-taxonomy-roles { min-width: 0; padding: 12px; display: grid; align-content: start; gap: 10px; overflow-y: auto; }
.job-taxonomy-roles > header { display: flex; align-items: end; justify-content: space-between; gap: 10px; }
.job-taxonomy-roles > header > div { min-width: 0; display: grid; gap: 2px; }
.job-taxonomy-roles > header span, .job-taxonomy-roles > header small { color: var(--text-3); font-size: 9px; }
.job-taxonomy-roles > header strong { font-size: 11px; }
.job-taxonomy-roles > p, .job-taxonomy-results > p { min-height: 70px; display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--text-3); text-align: center; font-size: 10px; }
.job-taxonomy-roles > div { display: grid; grid-template-columns: repeat(2, minmax(100px, 1fr)); gap: 4px 6px; }
.job-taxonomy-roles > div button { min-width: 0; min-height: 30px; padding: 5px 7px; overflow-wrap: anywhere; color: var(--text-2); text-align: left; background: transparent; border: 1px solid transparent; border-radius: 5px; font-size: 10px; transition: color .16s ease, border-color .16s ease, background-color .16s ease; }
.job-taxonomy-roles > div button:hover { color: #175cd3; background: #f5f8ff; border-color: #d5e3fb; }
.job-taxonomy-roles > div button.active { color: #174ea6; background: #eaf2ff; border-color: #9cbcec; font-weight: 700; }
.job-taxonomy-results { height: var(--taxonomy-body-height, 390px); padding: 7px; display: grid; align-content: start; gap: 3px; overflow-y: auto; }
.job-taxonomy-results > button { min-height: 42px; padding: 7px 9px; display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--text); text-align: left; background: #fff; border: 1px solid transparent; border-radius: 5px; }
.job-taxonomy-results > button:hover, .job-taxonomy-results > button.active { color: #175cd3; background: #f5f8ff; border-color: #d5e3fb; }
.job-taxonomy-results > button span { min-width: 0; display: grid; gap: 2px; }
.job-taxonomy-results > button strong { font-size: 11px; }
.job-taxonomy-results > button small { color: var(--text-3); font-size: 9px; }
.job-taxonomy-panel--compact .job-taxonomy-browser { grid-template-columns: 1fr; grid-template-rows: auto auto minmax(0, 1fr); }
.job-taxonomy-panel--compact .job-taxonomy-browser > nav { padding: 6px; display: flex; overflow-x: auto; overflow-y: hidden; border-right: 0; border-bottom: 1px solid var(--border); }
.job-taxonomy-panel--compact .job-taxonomy-browser > nav > span { display: none; }
.job-taxonomy-panel--compact .job-taxonomy-browser > nav button { min-width: max-content; min-height: 32px; }
.job-taxonomy-panel--compact .job-taxonomy-browser > nav button .app-icon { display: none; }
.job-taxonomy-panel--compact .job-taxonomy-roles { min-height: 0; max-height: none; }
@media (prefers-reduced-motion: reduce) {
  .taxonomy-panel-enter-active, .taxonomy-panel-leave-active { transition: none; }
}
</style>
