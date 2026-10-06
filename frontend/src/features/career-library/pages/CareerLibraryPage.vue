<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  FolderPlus, Import, LockKeyhole, Plus, Search, ShieldCheck, UploadCloud,
} from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { useToastFeedback } from '@/shared/ui/toast'
import CareerLibraryTabs, { type CareerLibraryView } from '../components/CareerLibraryTabs.vue'
import CareerProfileTab from '../components/CareerProfileTab.vue'
import CareerRecordsTab from '../components/CareerRecordsTab.vue'
import CareerFilesTab from '../components/CareerFilesTab.vue'
import {
  fetchCareerOverview, fetchCareerProfile, saveCareerProfile, searchCareerLibrary,
} from '../services/careerLibraryApi'
import type { CareerOverview, CareerProfile, CareerSearchResult } from '../types'
import '../career-library.css'

const route = useRoute()
const router = useRouter()
const profile = ref<CareerProfile | null>(null)
const overview = ref<CareerOverview | null>(null)
const loading = ref(true)
const profilePending = ref(false)
const pageError = ref('')
const notice = ref('')
useToastFeedback(pageError, 'error', 'career-library-page-error')
useToastFeedback(notice, 'success', 'career-library-page-notice')
const view = ref<CareerLibraryView>(readView(route.query.view))
const q = ref(textQuery('q'))
const type = ref(textQuery('type'))
const status = ref(textQuery('status') || (view.value === 'files' ? 'ALL' : 'ACTIVE'))
const sort = ref(textQuery('sort') || 'RECENT')
const layout = ref(textQuery('layout') || (view.value === 'files' ? 'grid' : 'list'))
const folderId = ref(textQuery('folderId'))
const globalSearch = ref('')
const searchResults = ref<CareerSearchResult[]>([])
const searchOpen = ref(false)
const searchPending = ref(false)
const recordsTab = ref<InstanceType<typeof CareerRecordsTab> | null>(null)
const filesTab = ref<InstanceType<typeof CareerFilesTab> | null>(null)
let searchTimer = 0

function textQuery(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function readView(value: unknown): CareerLibraryView {
  return value === 'records' || value === 'files' ? value : 'profile'
}

async function loadShell(): Promise<void> {
  loading.value = true
  try {
    const [profileValue, overviewValue] = await Promise.all([fetchCareerProfile(), fetchCareerOverview()])
    profile.value = profileValue
    overview.value = overviewValue
  } catch (reason) { pageError.value = errorMessage(reason, '求职资料库读取失败') }
  finally { loading.value = false }
}

async function refreshOverview(): Promise<void> {
  try { overview.value = await fetchCareerOverview() }
  catch { /* 当前页业务成功时不因右侧统计失败覆盖成功结果 */ }
}

async function changeView(next: CareerLibraryView): Promise<void> {
  view.value = next
  q.value = ''
  type.value = ''
  status.value = next === 'files' ? 'ALL' : 'ACTIVE'
  sort.value = 'RECENT'
  layout.value = next === 'files' ? 'grid' : 'list'
  folderId.value = ''
  await syncUrl()
}

async function patchQuery(patch: Record<string, string>): Promise<void> {
  if (patch.q !== undefined) q.value = patch.q
  if (patch.type !== undefined) type.value = patch.type
  if (patch.status !== undefined) status.value = patch.status
  if (patch.sort !== undefined) sort.value = patch.sort
  if (patch.layout !== undefined) layout.value = patch.layout
  if (patch.folderId !== undefined) folderId.value = patch.folderId
  await syncUrl()
}

async function syncUrl(): Promise<void> {
  const query: Record<string, string> = { view: view.value }
  if (q.value) query.q = q.value
  if (type.value) query.type = type.value
  if (status.value) query.status = status.value
  if (sort.value) query.sort = sort.value
  if (layout.value) query.layout = layout.value
  if (folderId.value) query.folderId = folderId.value
  await router.replace({ query })
}

async function saveProfile(values: { basics: Record<string, unknown>; intentions: Record<string, unknown>; preferences: Record<string, unknown>; summary: string }): Promise<void> {
  if (!profile.value) return
  profilePending.value = true
  pageError.value = ''
  try {
    profile.value = await saveCareerProfile({ ...profile.value, ...values })
    await refreshOverview()
    notice.value = '个人信息已保存，AI 简历可在你授权后使用新的资料快照。'
  } catch (reason) { pageError.value = errorMessage(reason, '个人信息保存失败') }
  finally { profilePending.value = false }
}

function setNotice(message: string): void { notice.value = message; pageError.value = '' }
function setError(message: string): void { pageError.value = message; notice.value = '' }

function handleGlobalSearch(value: string): void {
  globalSearch.value = value
  window.clearTimeout(searchTimer)
  const term = value.trim()
  if (!term) { searchResults.value = []; searchOpen.value = false; return }
  searchPending.value = true
  searchOpen.value = true
  searchTimer = window.setTimeout(async () => {
    try { searchResults.value = await searchCareerLibrary(term) }
    catch (reason) { setError(errorMessage(reason, '全局搜索失败')); searchResults.value = [] }
    finally { searchPending.value = false }
  }, 220)
}

async function selectSearchResult(result: CareerSearchResult): Promise<void> {
  searchOpen.value = false
  globalSearch.value = result.title
  view.value = result.view
  q.value = result.type === 'RECORD' || result.type === 'FILE' ? result.title : ''
  type.value = ''
  status.value = result.view === 'files' ? 'ALL' : 'ACTIVE'
  folderId.value = result.type === 'FOLDER' ? result.id : ''
  sort.value = 'RECENT'
  layout.value = result.view === 'files' ? 'grid' : 'list'
  await syncUrl()
  await nextTick()
  window.setTimeout(() => document.getElementById(result.targetId)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 500)
}

async function importResume(): Promise<void> {
  await changeView('files')
  await patchQuery({ type: 'RESUME' })
  await nextTick()
  filesTab.value?.chooseFile()
}

watch(() => route.query.view, (value) => { view.value = readView(value) })
onMounted(loadShell)
onBeforeUnmount(() => window.clearTimeout(searchTimer))
</script>

<template>

    <div class="career-library-page">
      <header class="career-page-head">
        <div><h1>求职资料库</h1><p>维护长期职业档案、结构化经历和私有文件，供简历与经你授权的 AI 使用。</p><small v-if="view === 'profile'"><ShieldCheck :size="14" />仅在 AI 对话中经你授权后使用；不采集年龄、性别、婚育和民族等敏感字段。</small></div>
        <div class="career-head-actions">
          <template v-if="view === 'profile'">
            <button class="career-btn ghost privacy-button" type="button"><LockKeyhole :size="16" />隐私设置</button>
            <button class="career-btn primary" type="submit" form="career-profile-form" :disabled="profilePending">{{ profilePending ? '保存中' : '保存修改' }}</button>
          </template>
          <template v-else-if="view === 'records'">
            <button class="career-btn ghost" type="button" @click="importResume"><Import :size="17" />导入简历</button>
            <button class="career-btn primary" type="button" @click="recordsTab?.openCreate()"><Plus :size="17" />添加记录</button>
          </template>
          <template v-else>
            <button class="career-btn ghost" type="button" @click="filesTab?.openFolder()"><FolderPlus :size="17" />新建文件夹</button>
            <button class="career-btn primary" type="button" @click="filesTab?.chooseFile()"><UploadCloud :size="17" />上传文件</button>
          </template>
        </div>
      </header>

      <div class="career-toolbar">
        <CareerLibraryTabs :model-value="view" @update:model-value="changeView" />
        <div class="library-global-search">
          <Search :size="18" />
          <input :value="globalSearch" placeholder="搜索资料库中的经历、文件或文件夹…" aria-label="搜索求职资料库" @focus="globalSearch && (searchOpen = true)" @input="handleGlobalSearch(($event.target as HTMLInputElement).value)" />
          <div v-if="searchOpen" class="global-search-results">
            <p v-if="searchPending">正在搜索...</p>
            <button v-for="result in searchResults" v-else :key="`${result.type}-${result.id}`" type="button" @click="selectSearchResult(result)"><Search :size="15" /><span><strong>{{ result.title }}</strong><small>{{ result.subtitle }}</small></span></button>
            <p v-if="!searchPending && !searchResults.length">没有匹配的资料或操作</p>
          </div>
        </div>
      </div>

      <Transition name="career-view" mode="out-in">
        <div :key="loading || !profile || !overview ? 'loading' : view" class="career-view-panel">
          <div v-if="loading || !profile || !overview" class="career-shell-loading"><span class="bone" /><span class="bone" /></div>
          <CareerProfileTab v-else-if="view === 'profile'" :profile="profile" :overview="overview" :pending="profilePending" @save="saveProfile" @profile-updated="profile = $event; refreshOverview()" @notice="setNotice" @error="setError" />
          <CareerRecordsTab v-else-if="view === 'records'" ref="recordsTab" :overview="overview" :q="q" :type="type" :status="status" :sort="sort" :layout="layout" @query="patchQuery" @changed="refreshOverview" @import-resume="importResume" @notice="setNotice" @error="setError" />
          <CareerFilesTab v-else ref="filesTab" :overview="overview" :q="q" :type="type" :status="status" :sort="sort" :layout="layout" :folder-id="folderId" @query="patchQuery" @changed="refreshOverview" @notice="setNotice" @error="setError" />
        </div>
      </Transition>
    </div>
</template>
