<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Archive, ArrowRight, Download, Eye, File, FileImage, FileText,
  Folder, FolderPlus, Grid2X2, HardDrive, Info, List, LoaderCircle, MoreVertical,
  Pencil, RefreshCcw, Search, ShieldCheck, UploadCloud, X,
} from 'lucide-vue-next'
import AppModal from '@/shared/ui/AppModal.vue'
import AppSelect from '@/shared/ui/AppSelect.vue'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import { errorMessage, isApiClientError } from '@/shared/api/types'
import {
  archiveCareerFile, archiveCareerFolder, createCareerFolder, downloadCareerFile,
  fetchCareerFiles, fetchCareerFolders, fetchCareerStorage, restoreCareerFile,
  retryCareerFile, updateCareerFile, uploadCareerFile,
} from '../services/careerLibraryApi'
import type { CareerFile, CareerFileFolder, CareerOverview, CareerStorage } from '../types'

const props = defineProps<{
  overview: CareerOverview
  q: string
  type: string
  status: string
  sort: string
  layout: string
  folderId: string
}>()
const emit = defineEmits<{
  query: [value: Record<string, string>]
  changed: []
  notice: [message: string]
  error: [message: string]
}>()

const files = ref<CareerFile[]>([])
const folders = ref<CareerFileFolder[]>([])
const storage = ref<CareerStorage>({ usedBytes: 0, quotaBytes: 20 * 1024 * 1024, availableBytes: 20 * 1024 * 1024, categoryCounts: {} })
const loading = ref(true)
const pending = ref('')
const isDragging = ref(false)
const selectedUpload = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploadForm = reactive({ category: 'RESUME', displayName: '', folderId: '' })
const previewFile = ref<CareerFile | null>(null)
const editFile = ref<CareerFile | null>(null)
const editForm = reactive({ displayName: '', category: 'OTHER', folderId: '' })
const folderModal = ref(false)
const folderName = ref('')
const taskControllers = new Map<string, AbortController>()

const categoryOptions = [
  { value: '', label: '全部分类' }, { value: 'RESUME', label: '简历' },
  { value: 'PORTFOLIO', label: '作品集' }, { value: 'WORK_SAMPLE', label: '工作样本' },
  { value: 'CERTIFICATE', label: '证书' }, { value: 'TRANSCRIPT', label: '成绩单' },
  { value: 'PROOF', label: '证明材料' }, { value: 'OTHER', label: '其他' },
]
const uploadCategories = categoryOptions.filter((item) => item.value)
const statusOptions = [
  { value: 'ALL', label: '全部状态' }, { value: 'ACTIVE', label: '使用中' },
  { value: 'ARCHIVED', label: '已归档' }, { value: 'QUARANTINED', label: '处理失败' },
]
const sortOptions = [
  { value: 'RECENT', label: '最近更新' }, { value: 'OLDEST', label: '最早更新' },
  { value: 'NAME', label: '文件名称' }, { value: 'SIZE', label: '文件大小' },
]
const allowedExtensions = new Set(['docx', 'pdf', 'png', 'jpg', 'jpeg', 'webp'])
const maxUploadBytes = 10 * 1024 * 1024

const folderOptions = computed(() => [
  { value: '', label: '未放入文件夹' },
  ...folders.value.map((folder) => ({ value: folder.id, label: folder.name, count: folder.fileCount })),
])
const previewPages = computed(() => Array.from({ length: previewFile.value?.previewPageCount || 0 }, (_, index) => index + 1))
const usagePercent = computed(() => Math.min(100, Math.round(storage.value.usedBytes * 100 / Math.max(1, storage.value.quotaBytes))))

async function load(): Promise<void> {
  loading.value = true
  try {
    const [filePage, folderList, storageView] = await Promise.all([
      fetchCareerFiles({
        keyword: props.q, category: props.type, status: props.status || 'ALL', folderId: props.folderId,
        sort: props.sort || 'RECENT', size: 100,
      }),
      fetchCareerFolders(), fetchCareerStorage(),
    ])
    files.value = filePage.items
    folders.value = folderList
    storage.value = storageView
  } catch (reason) { emit('error', errorMessage(reason, '文件资料读取失败')) }
  finally { loading.value = false }
}

function chooseFile(): void { fileInput.value?.click() }

function acceptFile(file?: File): void {
  if (!file) return
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowedExtensions.has(extension)) { emit('error', '仅支持 DOCX、PDF、PNG、JPEG 和 WebP 文件。'); return }
  if (file.size > maxUploadBytes) { emit('error', '单个文件不能超过 10 MiB。'); return }
  if (storage.value.usedBytes + file.size > storage.value.quotaBytes) { emit('error', '资料库剩余容量不足 20 MiB 配额，无法上传该文件。'); return }
  selectedUpload.value = file
  uploadForm.displayName = file.name.replace(/\.[^.]+$/, '')
  uploadForm.category = props.type && categoryOptions.some((item) => item.value === props.type) ? props.type : 'RESUME'
  uploadForm.folderId = props.folderId || ''
}

function onInput(event: Event): void { acceptFile((event.target as HTMLInputElement).files?.[0]) }
function onDrop(event: DragEvent): void { isDragging.value = false; acceptFile(event.dataTransfer?.files?.[0]) }
function clearSelection(): void {
  selectedUpload.value = null; uploadForm.displayName = ''
  if (fileInput.value) fileInput.value.value = ''
}

async function startUpload(): Promise<void> {
  if (!selectedUpload.value) { chooseFile(); return }
  pending.value = 'upload'
  try {
    const result = await uploadCareerFile(selectedUpload.value, uploadForm.category, uploadForm.displayName, uploadForm.folderId)
    files.value = [result.file, ...files.value.filter((file) => file.id !== result.file.id)]
    clearSelection()
    emit('notice', '文件已进入私有隔离区，正在进行安全扫描。')
    void watchTask(result.file.id, result.task.id)
    await refreshStorage()
  } catch (reason) { emit('error', uploadError(reason)) }
  finally { pending.value = '' }
}

async function watchTask(fileId: string, taskId: string): Promise<void> {
  taskControllers.get(fileId)?.abort()
  const controller = new AbortController()
  taskControllers.set(fileId, controller)
  try {
    const task = await pollTask(taskId, () => {
      const file = files.value.find((item) => item.id === fileId)
      if (file && file.processingStatus === 'SCANNING') file.processingStatus = 'PREVIEWING'
    }, controller.signal, 900, 240_000)
    await load()
    if (task.status === 'SUCCEEDED') emit('notice', '文件已通过安全扫描并生成逐页预览。')
    else emit('error', taskFailure(task.failureReason))
  } catch (reason) {
    if (!isAbortError(reason)) { await load(); emit('error', errorMessage(reason, '文件处理状态读取失败')) }
  } finally { taskControllers.delete(fileId) }
}

async function refreshStorage(): Promise<void> {
  try { storage.value = await fetchCareerStorage() } catch { /* 文件主流程不因统计失败中断 */ }
}

function uploadError(reason: unknown): string {
  if (reason instanceof TypeError) return '网络连接中断，文件尚未提交。请确认后端服务可用后重试。'
  if (isApiClientError(reason)) {
    if (reason.reason === 'PAYLOAD_TOO_LARGE' || /TOO_LARGE/.test(reason.reason)) return '文件超过 10 MiB，未上传。'
    if (/QUOTA/.test(reason.reason)) return '资料库容量不足，20 MiB 配额已用完。'
    if (/TYPE|FORMAT|PDF_|DOCX_|IMAGE_/.test(reason.reason)) return reason.message || '文件格式或安全结构不符合要求。'
    if (/SCAN/.test(reason.reason)) return reason.message || '安全扫描失败，文件保持隔离。'
    if (reason.reason === 'BAD_RESPONSE') return '后端服务未返回有效响应，请确认 8080 服务正在运行。'
  }
  return errorMessage(reason, '文件上传失败，未写入资料库')
}

function taskFailure(reason?: string | null): string {
  if (/SCAN_UNAVAILABLE|SCAN_ERROR/.test(reason || '')) return '安全扫描服务不可用，文件保持隔离；可稍后重试。'
  if (/PREVIEW/.test(reason || '')) return '文件通过扫描，但逐页预览生成失败；可稍后重试。'
  if (/MALWARE/.test(reason || '')) return '检测到恶意内容，原始文件字节已删除。'
  return '文件处理失败，已保持隔离，不能预览或下载。'
}

async function retry(file: CareerFile): Promise<void> {
  pending.value = file.id
  try {
    const result = await retryCareerFile(file)
    files.value = files.value.map((item) => item.id === file.id ? result.file : item)
    emit('notice', '已重新提交处理任务。')
    void watchTask(file.id, result.task.id)
  } catch (reason) { emit('error', errorMessage(reason, '重试失败')) }
  finally { pending.value = '' }
}

async function download(file: CareerFile): Promise<void> {
  pending.value = file.id
  try { await downloadCareerFile(file) }
  catch (reason) { emit('error', errorMessage(reason, '文件下载失败')) }
  finally { pending.value = '' }
}

function openEdit(file: CareerFile): void {
  editFile.value = file
  Object.assign(editForm, { displayName: file.displayName, category: file.category, folderId: file.folderId || '' })
}

async function saveEdit(): Promise<void> {
  if (!editFile.value) return
  pending.value = editFile.value.id
  try {
    await updateCareerFile(editFile.value, editForm)
    editFile.value = null
    await load(); emit('changed'); emit('notice', '文件名称和分类已更新。')
  } catch (reason) { emit('error', errorMessage(reason, '文件资料更新失败')) }
  finally { pending.value = '' }
}

async function toggleArchive(file: CareerFile): Promise<void> {
  pending.value = file.id
  try {
    if (file.status === 'ARCHIVED') await restoreCareerFile(file)
    else await archiveCareerFile(file)
    await load(); emit('changed'); emit('notice', file.status === 'ARCHIVED' ? '文件已恢复。' : '文件已归档。')
  } catch (reason) { emit('error', errorMessage(reason, '文件状态更新失败')) }
  finally { pending.value = '' }
}

function openFolder(): void { folderName.value = ''; folderModal.value = true }
async function createFolder(): Promise<void> {
  if (!folderName.value.trim()) return
  pending.value = 'folder'
  try { await createCareerFolder(folderName.value); folderModal.value = false; await load(); emit('notice', '文件夹已创建。') }
  catch (reason) { emit('error', errorMessage(reason, '文件夹创建失败')) }
  finally { pending.value = '' }
}

async function removeFolder(folder: CareerFileFolder): Promise<void> {
  try { await archiveCareerFolder(folder); await load(); emit('notice', '文件夹已归档，原文件已移至未分类。') }
  catch (reason) { emit('error', errorMessage(reason, '文件夹归档失败')) }
}

function fileExtension(file: CareerFile): string { return file.originalFilename.split('.').pop()?.toUpperCase() || 'FILE' }
function fileSize(bytes: number): string { return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KiB` : `${(bytes / 1024 / 1024).toFixed(1)} MiB` }
function categoryLabel(category: string): string { return categoryOptions.find((item) => item.value === category)?.label || category }
function stateLabel(file: CareerFile): string {
  if (file.processingStatus === 'SCANNING') return '安全扫描中'
  if (file.processingStatus === 'PREVIEWING') return '生成预览中'
  if (file.processingStatus === 'SCAN_FAILED') return '扫描失败'
  if (file.processingStatus === 'PREVIEW_FAILED') return '预览失败'
  if (file.processingStatus === 'INFECTED') return '已阻断'
  return file.status === 'ARCHIVED' ? '已归档' : '使用中'
}
function processing(file: CareerFile): boolean { return file.processingStatus === 'SCANNING' || file.processingStatus === 'PREVIEWING' }
function setQuery(key: string, value: string | number): void { emit('query', { [key]: String(value) }) }

watch(() => [props.q, props.type, props.status, props.sort, props.folderId], load)
onMounted(load)
onBeforeUnmount(() => taskControllers.forEach((controller) => controller.abort()))
defineExpose({ chooseFile, openFolder })
</script>

<template>
  <div class="career-two-column files-tab">
    <main class="career-main-column career-stack">
      <input ref="fileInput" class="sr-only" type="file" accept=".docx,.pdf,.png,.jpg,.jpeg,.webp" @change="onInput" />
      <section
        id="file-upload"
        class="upload-dropzone"
        :class="{ dragging: isDragging, selected: selectedUpload }"
        @dragenter.prevent="isDragging = true"
        @dragover.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        @drop.prevent="onDrop"
      >
        <div v-if="!selectedUpload" class="dropzone-empty" @click="chooseFile">
          <UploadCloud :size="34" /><strong>拖拽文件到此处，或<button type="button">点击选择</button></strong>
          <p>支持 DOCX、PDF、PNG、JPEG、WebP，单文件不超过 10 MiB</p><small>首版仅支持预览和下载，不解析字段。</small>
        </div>
        <div v-else class="upload-ready">
          <span class="file-hero-icon docx"><FileText :size="27" /></span>
          <div><strong>{{ selectedUpload.name }}</strong><small>{{ fileSize(selectedUpload.size) }} · 等待提交</small></div>
          <label><span>分类</span><AppSelect v-model="uploadForm.category" :options="uploadCategories" aria-label="上传文件分类" /></label>
          <label><span>文件夹</span><AppSelect v-model="uploadForm.folderId" :options="folderOptions" aria-label="上传文件夹" /></label>
          <button class="career-btn primary" type="button" :disabled="pending === 'upload'" @click="startUpload"><UploadCloud :size="16" />{{ pending === 'upload' ? '提交中' : '开始上传' }}</button>
          <button class="icon-action" type="button" title="取消" @click="clearSelection"><X :size="18" /></button>
        </div>
      </section>

      <section class="file-toolbar">
        <label class="career-search"><Search :size="18" /><input :value="q" placeholder="搜索文件名或显示名称" @input="setQuery('q', ($event.target as HTMLInputElement).value)" /></label>
        <AppSelect :model-value="type" :options="categoryOptions" aria-label="文件分类" @update:model-value="setQuery('type', $event)" />
        <AppSelect :model-value="status" :options="statusOptions" aria-label="文件状态" @update:model-value="setQuery('status', $event)" />
        <AppSelect :model-value="sort" :options="sortOptions" aria-label="文件排序" @update:model-value="setQuery('sort', $event)" />
        <div class="layout-toggle"><button type="button" :class="{ active: layout !== 'list' }" title="网格视图" @click="setQuery('layout', 'grid')"><Grid2X2 :size="18" /></button><button type="button" :class="{ active: layout === 'list' }" title="列表视图" @click="setQuery('layout', 'list')"><List :size="18" /></button></div>
      </section>

      <section class="file-grid" :class="{ list: layout === 'list' }" aria-live="polite">
        <div v-if="loading" class="files-loading"><span v-for="n in 6" :key="n" class="career-card bone-card" /></div>
        <div v-else-if="!files.length" class="career-card career-empty"><Folder :size="30" /><strong>还没有符合条件的文件</strong><p>上传后会先进入隔离区，通过安全扫描和预览门禁后才可使用。</p><button type="button" @click="chooseFile"><UploadCloud :size="16" />上传文件</button></div>
        <article v-for="file in files" v-else :id="file.id" :key="file.id" class="career-card file-card" :class="{ processing: processing(file), failed: /FAILED|INFECTED/.test(file.processingStatus) }">
          <button class="file-more" type="button" title="更多操作"><MoreVertical :size="18" /></button>
          <div class="file-card-main">
            <span class="file-hero-icon" :class="fileExtension(file).toLowerCase()">
              <FileImage v-if="/PNG|JPG|JPEG|WEBP/.test(fileExtension(file))" :size="27" />
              <FileText v-else :size="27" /><small>{{ fileExtension(file) }}</small>
            </span>
            <div class="file-card-copy"><strong :title="file.displayName">{{ file.displayName }}</strong><div><span>{{ categoryLabel(file.category) }}</span><em :class="{ bad: /FAILED|INFECTED/.test(file.processingStatus) }">{{ stateLabel(file) }}</em></div><p>{{ fileSize(file.sizeBytes) }}<b>·</b>{{ new Date(file.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</p></div>
          </div>
          <div v-if="processing(file)" class="file-processing"><LoaderCircle :size="16" /><span><strong>{{ stateLabel(file) }}</strong><small>可以离开页面，任务会继续</small></span><i /></div>
          <div v-else-if="/FAILED|INFECTED/.test(file.processingStatus)" class="file-processing failed"><Info :size="16" /><span><strong>{{ stateLabel(file) }}</strong><small>{{ file.previewError || '文件保持隔离，不能预览或下载' }}</small></span><button v-if="file.processingStatus !== 'INFECTED'" type="button" @click="retry(file)"><RefreshCcw :size="14" />重试</button></div>
          <footer>
            <button type="button" :disabled="file.processingStatus !== 'READY'" @click="previewFile = file"><Eye :size="17" />预览</button>
            <button type="button" :disabled="file.processingStatus !== 'READY'" @click="download(file)"><Download :size="17" />下载</button>
            <button type="button" @click="openEdit(file)"><Pencil :size="17" />重命名</button>
            <button v-if="layout === 'list'" type="button" @click="toggleArchive(file)"><Archive :size="17" />{{ file.status === 'ARCHIVED' ? '恢复' : '归档' }}</button>
          </footer>
        </article>
      </section>
    </main>

    <aside class="career-aside-column">
      <section class="career-card aside-card storage-card"><header><div><HardDrive :size="19" /><h3>存储空间</h3></div></header><strong>{{ fileSize(storage.usedBytes) }} <small>/ {{ fileSize(storage.quotaBytes) }}</small></strong><span>{{ usagePercent }}%</span><div class="storage-progress"><i :style="{ width: `${usagePercent}%` }" /></div><p><span>已使用 {{ fileSize(storage.usedBytes) }}</span><span>可用 {{ fileSize(storage.availableBytes) }}</span></p></section>
      <section class="career-card aside-card category-card"><header><div><Folder :size="19" /><h3>分类概览</h3></div></header><div class="category-overview"><button v-for="category in uploadCategories" :key="category.value" type="button" @click="setQuery('type', category.value)"><span><File :size="16" />{{ category.label }}</span><strong>{{ storage.categoryCounts[category.value] || 0 }}</strong></button></div><button class="aside-link" type="button" @click="setQuery('type', '')">查看全部文件<ArrowRight :size="15" /></button></section>
      <section v-if="folders.length" class="career-card aside-card category-card"><header><div><FolderPlus :size="19" /><h3>我的文件夹</h3></div></header><div class="category-overview"><button v-for="folder in folders" :key="folder.id" type="button" @click="setQuery('folderId', folder.id)"><span><Folder :size="16" />{{ folder.name }}</span><strong>{{ folder.fileCount }}</strong><i title="归档文件夹" @click.stop="removeFolder(folder)"><Archive :size="14" /></i></button></div></section>
      <section class="career-card aside-card privacy-file-card"><ShieldCheck :size="21" /><div><h3>隐私说明</h3><p>文件仅用于你授权的 AI 对话，不向招聘方自动公开。</p><button type="button">了解更多隐私政策<ArrowRight :size="15" /></button></div></section>
    </aside>

    <AppModal :open="Boolean(previewFile)" :title="previewFile?.displayName || '文件预览'" :width="960" @close="previewFile = null"><div class="career-preview-pages"><img v-for="page in previewPages" :key="page" :src="`/api/v1/career-library/files/${previewFile?.id}/preview-pages/${page}`" :alt="`第 ${page} 页`" /></div></AppModal>

    <AppModal :open="Boolean(editFile)" title="更新文件资料" :width="520" @close="editFile = null"><form id="career-file-edit" class="file-edit-form" @submit.prevent="saveEdit"><label><span>显示名称</span><input v-model.trim="editForm.displayName" class="career-input" maxlength="160" /></label><label><span>分类</span><AppSelect v-model="editForm.category" :options="uploadCategories" aria-label="文件分类" /></label><label><span>文件夹</span><AppSelect v-model="editForm.folderId" :options="folderOptions" aria-label="文件夹" /></label></form><template #footer><button class="career-btn ghost" type="button" @click="editFile = null">取消</button><button class="career-btn primary" type="submit" form="career-file-edit">保存修改</button></template></AppModal>

    <AppModal :open="folderModal" title="新建文件夹" :width="460" @close="folderModal = false"><form id="career-folder-form" class="file-edit-form" @submit.prevent="createFolder"><label><span>文件夹名称</span><input v-model.trim="folderName" class="career-input" maxlength="80" autofocus placeholder="例如：证书与证明" /></label><p class="folder-hint">当前仅支持一级文件夹，文件可随时移动。</p></form><template #footer><button class="career-btn ghost" type="button" @click="folderModal = false">取消</button><button class="career-btn primary" type="submit" form="career-folder-form">创建文件夹</button></template></AppModal>
  </div>
</template>
