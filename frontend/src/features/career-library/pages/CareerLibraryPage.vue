<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  FileUp, FolderPlus, LockKeyhole, Plus, Search, ShieldCheck, UploadCloud,
} from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import { useLoadState } from '@/shared/lib/useLoadState'
import PageState from '@/shared/ui/PageState.vue'
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
const shell = useLoadState(() => Promise.all([fetchCareerProfile(), fetchCareerOverview()]))
watch(shell.data, (value) => {
  if (value) [profile.value, overview.value] = value
})
const { loading, error: shellError, loaded, load: loadShell } = shell
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
/** Category to preselect once the files tab has mounted and its picker can open. */
const pendingPick = ref<string | null>(null)
let searchTimer = 0

function textQuery(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function readView(value: unknown): CareerLibraryView {
  return value === 'records' || value === 'files' ? value : 'profile'
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
    notice.value = '个人信息已保存；之后经你授权的 AI 简历会使用最新资料。'
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

/** Switches to the files view filtered to `category` and opens the file picker there. */
async function pickFile(category: string): Promise<void> {
  pendingPick.value = category
  if (view.value !== 'files') await changeView('files')
  await patchQuery({ type: category })
  if (filesTab.value) openPendingPick()
}

function openPendingPick(): void {
  if (pendingPick.value === null || !filesTab.value) return
  pendingPick.value = null
  filesTab.value.chooseFile()
}

/** Permanent deletion happens on the data-rights page, which shows what will be removed first. */
function removeItem(target: { type: string; id: string }): void {
  void router.push({ path: '/account/data-rights', query: { targetType: target.type, targetId: target.id } })
}

watch(filesTab, openPendingPick)
watch(() => route.query.view, (value) => { view.value = readView(value) })
onMounted(loadShell)
onBeforeUnmount(() => window.clearTimeout(searchTimer))
</script>

<template>
  <div class="career-library-page">
    <header class="career-page-head">
      <div>
        <h1>求职资料库</h1>
        <p>维护长期职业档案、结构化经历和私有文件，供简历与经你授权的 AI 使用。</p>
        <small v-if="view === 'profile'"><ShieldCheck :size="14" />仅在 AI 对话中经你授权后使用；不采集年龄、性别、婚育和民族等敏感字段。</small>
        <small v-else-if="view === 'records'"><FileUp :size="14" />上传的简历文件保存在“文件资料”中，可预览和下载，不会自动拆分成经历记录。</small>
      </div>
      <div v-if="loaded" class="career-head-actions">
        <template v-if="view === 'profile'">
          <RouterLink class="career-btn ghost privacy-button" to="/account/data-rights"><LockKeyhole :size="16" />数据与隐私</RouterLink>
          <button class="career-btn primary" type="submit" form="career-profile-form" :disabled="profilePending">{{ profilePending ? '保存中' : '保存修改' }}</button>
        </template>
        <template v-else-if="view === 'records'">
          <button class="career-btn ghost" type="button" title="文件保存到“文件资料”，不会自动生成经历记录" @click="pickFile('RESUME')"><FileUp :size="17" />上传简历文件</button>
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

    <PageState :loading="loading" :error="shellError" :loaded="loaded" error-title="求职资料库读取失败" @retry="loadShell">
      <template #skeleton><div class="career-shell-loading"><span class="bone" /><span class="bone" /></div></template>
      <Transition name="career-view" mode="out-in">
        <div v-if="profile && overview" :key="view" class="career-view-panel">
          <CareerProfileTab v-if="view === 'profile'" :profile="profile" :overview="overview" :pending="profilePending" @save="saveProfile" @profile-updated="profile = $event; refreshOverview()" @notice="setNotice" @error="setError" />
          <CareerRecordsTab v-else-if="view === 'records'" ref="recordsTab" :overview="overview" :q="q" :type="type" :status="status" :sort="sort" :layout="layout" @query="patchQuery" @changed="refreshOverview" @upload-file="pickFile" @remove="removeItem" @notice="setNotice" @error="setError" />
          <CareerFilesTab v-else ref="filesTab" :overview="overview" :q="q" :type="type" :status="status" :sort="sort" :layout="layout" :folder-id="folderId" @query="patchQuery" @changed="refreshOverview" @remove="removeItem" @notice="setNotice" @error="setError" />
        </div>
      </Transition>
    </PageState>
  </div>
</template>
