<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, FileText, Upload } from 'lucide-vue-next'
import { errorMessage, isNotFound, isVersionConflict } from '@/shared/api/types'
import { formatRelative } from '@/shared/lib/datetime'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import AppIcon from '@/shared/ui/AppIcon.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiIconButton from '@/shared/ui/UiIconButton.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiSegmented from '@/shared/ui/UiSegmented.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import { toast } from '@/shared/ui/toast'
import { normalizeResumeTitle, resumeTitleIssue } from '@/features/resume/utils/resumeTitle'
import { fetchCareerFiles, uploadCareerFile } from '@/features/career-library/services/careerLibraryApi'
import type { CareerFile } from '@/features/career-library/types'
import {
  confirmResumeImport,
  createAiResume,
  ensureAiResume,
  fetchResumeImport,
  startResumeFileImport,
  startResumeTextImport,
} from '../services/aiResumeApi'
import type { AiIdentity, AiResumeConversation, ResumeImportSession } from '../types'
import { applyTemplateChoice, choiceFromQuery } from '../utils/templateChoice'
import {
  IMPORT_TEXT_MAX,
  IMPORT_TEXT_MIN,
  fileTaskFailureText,
  importContactFields,
  importDraftSummary,
  importFailureText,
  importFileIssue,
  importTextIssue,
  isImportableResumeFile,
  meaningfulLength,
} from '../utils/textImport'

type ResumeStart = 'NEW' | 'EXISTING'
type ImportSource = 'FILE' | 'TEXT'
type ImportStage = 'input' | 'uploading' | 'parsing' | 'review' | 'confirming'

/** Pasted text survives a refresh in this tab only; it is removed once the resume is created. */
const PASTE_KEY = 'jobproof:resume-import:text'

const router = useRouter()
const route = useRoute()
/** Started from the template center: apply that layout right after creating the resume. */
const requestedTemplate = computed(() => {
  const value = route.query.template
  return typeof value === 'string' && /^[a-z0-9-]{3,80}$/.test(value) ? value : ''
})
const start = ref<ResumeStart | ''>('')
const identity = ref<AiIdentity | ''>('')
const pending = ref(false)
const error = ref('')
const flow = ref<HTMLElement | null>(null)

const importSource = ref<ImportSource>('FILE')
const importStage = ref<ImportStage>('input')
const pasteText = ref(readPaste())
const pasteAttempted = ref(false)
const selectedFile = ref<File | null>(null)
const fileAttempted = ref(false)
const libraryFiles = ref<CareerFile[]>([])
const libraryState = ref<'idle' | 'loading' | 'loaded' | 'error'>('idle')
const libraryFileId = ref('')
const importError = ref('')
/** An import still running on the server when this page stopped waiting; it can be checked again. */
const unfinishedImportId = ref('')
const importSession = ref<ResumeImportSession | null>(null)
const importTitle = ref('')
let importAbort: AbortController | null = null

const identityOptions: Array<{ id: AiIdentity; label: string; detail: string; icon: 'book' | 'award' | 'briefcase' }> = [
  { id: 'STUDENT', label: '学生', detail: '实习、课程、社团与项目经历', icon: 'book' },
  { id: 'GRADUATE', label: '应届生', detail: '校招与第一份全职工作', icon: 'award' },
  { id: 'PROFESSIONAL', label: '职场人士', detail: '工作成果与岗位定制', icon: 'briefcase' },
]
const SOURCE_ITEMS = [{ value: 'FILE', label: '上传 PDF / Word' }, { value: 'TEXT', label: '粘贴文字' }]

const selectedStartText = computed(() => start.value === 'EXISTING'
  ? '我已经有一份简历，想导入后继续完善'
  : '我想从零新建一份简历')
const pasteLength = computed(() => meaningfulLength(pasteText.value))
const pasteIssue = computed(() => importTextIssue(pasteText.value))
const pasteCounter = computed(() => `${pasteLength.value} 字${pasteLength.value < IMPORT_TEXT_MIN ? `（至少 ${IMPORT_TEXT_MIN}）` : ''}`)
const fileIssue = computed(() => (libraryFileId.value && !selectedFile.value ? '' : importFileIssue(selectedFile.value)))
const working = computed(() => importStage.value === 'uploading' || importStage.value === 'parsing')
const confirming = computed(() => importStage.value === 'confirming')
const busy = computed(() => pending.value || working.value || confirming.value)
const reviewing = computed(() => Boolean(importSession.value) && (importStage.value === 'review' || confirming.value))
const workingText = computed(() => importStage.value === 'uploading' ? '正在上传并做安全检查…' : '正在解析简历…')
const importSummary = computed(() => importDraftSummary(importSession.value?.structuredDraft))
const importContacts = computed(() => importContactFields(importSession.value?.structuredDraft))
const importTitleIssue = computed(() => normalizeResumeTitle(importTitle.value) ? resumeTitleIssue(importTitle.value) : '')
const reviewSourceText = computed(() => importSession.value?.sourceType === 'CAREER_FILE'
  ? `这是我的简历文件${importSession.value.sourceFilename ? `「${importSession.value.sourceFilename}」` : ''}`
  : `这是我的简历（${pasteLength.value} 字）`)

function readPaste(): string {
  try { return window.sessionStorage.getItem(PASTE_KEY) ?? '' } catch { return '' }
}

function writePaste(value: string | null): void {
  try {
    if (value) window.sessionStorage.setItem(PASTE_KEY, value)
    else window.sessionStorage.removeItem(PASTE_KEY)
  } catch {
    // Without session storage a refresh loses the pasted text; the import itself is unaffected.
  }
}

function chooseStart(value: ResumeStart): void {
  if (busy.value || start.value === value) return
  start.value = value
  identity.value = ''
  error.value = ''
  if (value === 'EXISTING') void loadLibraryFiles()
  void nextTick(scrollLatest)
}

/**
 * The resume already exists at this point, so a failure here never blocks opening it: the workbench
 * starts on the default layout and says so.
 */
async function applyRequestedTemplate(conversation: AiResumeConversation): Promise<void> {
  const templateId = requestedTemplate.value
  if (!templateId) return
  try {
    const result = await applyTemplateChoice(conversation, templateId, choiceFromQuery(templateId, route.query))
    if (!result.designSaved) toast.warning('已换成所选模板，但在模板页选的配色、字体等没有保存成功；可以在工作台「设计」里重新选择。')
  } catch {
    toast.warning('所选模板没有应用成功，先用默认模板打开；可以在工作台「模板」里重新选择。')
  }
}

async function chooseIdentity(value: AiIdentity): Promise<void> {
  if (start.value !== 'NEW' || pending.value) return
  identity.value = value
  pending.value = true
  error.value = ''
  await nextTick(scrollLatest)
  try {
    const conversation = await createAiResume(value)
    await applyRequestedTemplate(conversation)
    await router.replace({ path: `/ai-resume/${conversation.id}`, query: { source: 'new' } })
  } catch (reason) {
    error.value = errorMessage(reason, '创建 AI 简历失败')
    pending.value = false
  }
}

/** Resume files already in the career library that passed the safety checks. */
async function loadLibraryFiles(): Promise<void> {
  if (libraryState.value === 'loading') return
  libraryState.value = 'loading'
  try {
    const page = await fetchCareerFiles({ category: 'RESUME', status: 'ACTIVE', processingStatus: 'READY', sort: 'RECENT', size: 20 })
    libraryFiles.value = page.items.filter(item => isImportableResumeFile(item.contentType)).slice(0, 5)
    libraryState.value = 'loaded'
  } catch {
    libraryState.value = 'error'
  }
}

function chooseFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  if (!file) return
  selectedFile.value = file
  libraryFileId.value = ''
  fileAttempted.value = true
  importError.value = ''
}

function chooseLibraryFile(id: string): void {
  if (working.value) return
  libraryFileId.value = libraryFileId.value === id ? '' : id
  selectedFile.value = null
  fileAttempted.value = false
  importError.value = ''
}

/** Keeps the import in the address bar so a refresh (or a slow parse) can pick it up again. */
async function rememberImport(id: string): Promise<void> {
  unfinishedImportId.value = ''
  await router.replace({ query: { ...route.query, import: id } })
}

async function forgetImport(): Promise<void> {
  if (!route.query.import) return
  const query = { ...route.query }
  delete query.import
  await router.replace({ query })
}

function beginWork(stage: ImportStage): AbortSignal {
  importError.value = ''
  importStage.value = stage
  importAbort?.abort()
  importAbort = new AbortController()
  return importAbort.signal
}

/** Waits for the parse, then shows what was found or why nothing was. */
async function awaitParse(importId: string, taskId: string | null | undefined, signal: AbortSignal): Promise<void> {
  if (taskId) await pollTask(taskId, () => undefined, signal)
  const session = await fetchResumeImport(importId)
  if (session.status === 'PENDING' || session.status === 'PARSING') {
    unfinishedImportId.value = importId
    importError.value = '解析还在进行。可以稍后点“查看解析结果”，或刷新页面接着查看。'
    importStage.value = 'input'
    return
  }
  if (session.status !== 'READY_FOR_CONFIRMATION') {
    importError.value = importFailureText(session.errorCode, session.sourceType)
    importStage.value = 'input'
    await forgetImport()
    return
  }
  importSession.value = session
  importStage.value = 'review'
  await nextTick(scrollLatest)
}

function failWork(reason: unknown, importId: string, fallback: string): void {
  if (isAbortError(reason)) return
  importStage.value = 'input'
  if (importId && !isNotFound(reason)) {
    // Polling gave up or the network dropped; the import itself may still finish on the server.
    unfinishedImportId.value = importId
    importError.value = `${errorMessage(reason, fallback)} 解析结果会保留，可以点“查看解析结果”接着看。`
  } else {
    importError.value = errorMessage(reason, fallback)
  }
}

async function parseImport(): Promise<void> {
  if (busy.value) return
  pasteAttempted.value = true
  importError.value = ''
  if (pasteIssue.value) return
  const signal = beginWork('parsing')
  let importId = ''
  try {
    writePaste(pasteText.value)
    const started = await startResumeTextImport(pasteText.value)
    importId = started.importSession.id
    await rememberImport(importId)
    await awaitParse(importId, started.task.id, signal)
  } catch (reason) {
    failWork(reason, importId, '简历解析没有成功，请稍后重试。')
  }
}

async function parseFile(): Promise<void> {
  if (busy.value) return
  fileAttempted.value = true
  importError.value = ''
  if (fileIssue.value) return
  const signal = beginWork(selectedFile.value ? 'uploading' : 'parsing')
  let importId = ''
  try {
    let fileId = libraryFileId.value
    const file = selectedFile.value
    if (file) {
      const upload = await uploadCareerFile(file, 'RESUME')
      const checked = await pollTask(upload.task.id, () => undefined, signal)
      if (checked.status !== 'SUCCEEDED') {
        importError.value = fileTaskFailureText(checked.failureReason)
        importStage.value = 'input'
        return
      }
      fileId = upload.file.id
      // The file now lives in the library: offer it from there if this import has to be redone.
      selectedFile.value = null
      libraryFileId.value = fileId
      void loadLibraryFiles()
      importStage.value = 'parsing'
    }
    const started = await startResumeFileImport(fileId)
    importId = started.importSession.id
    await rememberImport(importId)
    await awaitParse(importId, started.task.id, signal)
  } catch (reason) {
    failWork(reason, importId, '简历文件没有导入成功，请稍后重试。')
  }
}

/** Picks an import up again after a refresh or a poll that gave up. */
async function resumeImport(importId: string): Promise<void> {
  if (busy.value) return
  start.value = 'EXISTING'
  const signal = beginWork('parsing')
  unfinishedImportId.value = ''
  try {
    const session = await fetchResumeImport(importId)
    importSource.value = session.sourceType === 'CAREER_FILE' ? 'FILE' : 'TEXT'
    if (session.status === 'CONFIRMED' && session.resultMasterId) {
      await openConfirmed(session.resultMasterId)
      return
    }
    await awaitParse(importId, session.status === 'PENDING' || session.status === 'PARSING' ? session.task?.id : null, signal)
  } catch (reason) {
    if (isNotFound(reason)) await forgetImport()
    failWork(reason, isNotFound(reason) ? '' : importId, '没有找到这次导入，请重新上传或粘贴。')
  }
}

function editAgain(): void {
  if (confirming.value) return
  importSession.value = null
  importError.value = ''
  importStage.value = 'input'
  void forgetImport()
}

async function openConfirmed(masterId: string): Promise<void> {
  writePaste(null)
  try {
    const conversation = await ensureAiResume(masterId)
    await applyRequestedTemplate(conversation)
    await router.replace({ path: `/ai-resume/${conversation.id}`, query: { source: 'import' } })
  } catch {
    // The resume exists; its own page opens (or explains) the workbench.
    await router.replace(`/resumes/${encodeURIComponent(masterId)}`)
  }
}

async function confirmImport(): Promise<void> {
  const session = importSession.value
  if (!session || confirming.value || importTitleIssue.value) return
  importStage.value = 'confirming'
  importError.value = ''
  try {
    const title = normalizeResumeTitle(importTitle.value) || undefined
    const confirmed = await confirmResumeImport(session.id, title, session.version)
    if (!confirmed.resultMasterId) throw new Error('简历没有创建成功，请重新导入。')
    await openConfirmed(confirmed.resultMasterId)
  } catch (reason) {
    if (isVersionConflict(reason)) {
      importSession.value = await fetchResumeImport(session.id).catch(() => session)
      importError.value = '解析结果刚刚更新过，请确认后再导入一次。'
    } else {
      importError.value = errorMessage(reason, '导入没有成功，请稍后重试。')
    }
    importStage.value = 'review'
  }
}

function identityLabel(value: AiIdentity): string {
  return identityOptions.find((option) => option.id === value)?.label ?? value
}

function scrollLatest(): void {
  flow.value?.scrollTo({ top: flow.value.scrollHeight, behavior: 'smooth' })
}

function chooseSource(value: string): void {
  if (working.value || (value !== 'FILE' && value !== 'TEXT')) return
  importSource.value = value
  importError.value = ''
}

onMounted(() => {
  const importId = route.query.import
  if (typeof importId === 'string' && /^[A-Za-z0-9-]{8,64}$/.test(importId)) {
    void loadLibraryFiles()
    void resumeImport(importId)
  } else if (pasteText.value) {
    // Text pasted before a refresh: reopen the paste box instead of starting over.
    start.value = 'EXISTING'
    importSource.value = 'TEXT'
    void loadLibraryFiles()
  }
})
onBeforeUnmount(() => importAbort?.abort())
</script>

<template>
  <main class="onboarding-workbench">
    <header class="onboarding-bar">
      <UiIconButton label="返回简历列表" :icon="ChevronLeft" to="/resumes" tooltip-side="right" />
      <strong>AI 创建简历</strong>
      <span><AppIcon name="file-text" :size="15" />简历预览</span>
    </header>

    <div class="onboarding-grid">
      <section ref="flow" class="onboarding-flow" aria-label="AI 简历建档对话">
        <article class="flow-copy">
          <span>AI 简历助手</span>
          <p>你好，我会一步一步梳理你的经历、明确求职目标，并生成一份适合投递的简历。</p>
          <p>如果你已经有简历，可以粘贴进来导入后继续完善；如果还没有，我们就从基础信息开始创建。</p>
        </article>

        <section class="choice-card">
          <div><strong>你现在已经有一份简历了吗？</strong><span>选择后我会按对应路径继续，不会覆盖已有内容。</span></div>
          <button type="button" :class="{ 'is-selected': start === 'EXISTING' }" :aria-pressed="start === 'EXISTING'" :disabled="busy" @click="chooseStart('EXISTING')">有，我已经有简历了<AppIcon name="chevron-right" :size="16" /></button>
          <button type="button" :class="{ 'is-selected': start === 'NEW' }" :aria-pressed="start === 'NEW'" :disabled="busy" @click="chooseStart('NEW')">没有，我想新建一份<AppIcon name="chevron-right" :size="16" /></button>
        </section>

        <article v-if="start" class="user-answer"><p>{{ selectedStartText }}</p></article>

        <template v-if="start === 'NEW'">
          <article class="flow-copy flow-copy--followup">
            <span>AI 简历助手</span>
            <p>很好。先确认你当前所处的求职阶段，我会据此安排后续提问顺序。</p>
          </article>
          <section class="choice-card identity-card">
            <div><strong>选择你的身份</strong><span>身份只影响引导顺序，简历内容结构保持一致。</span></div>
            <button v-for="option in identityOptions" :key="option.id" type="button" :disabled="pending" @click="chooseIdentity(option.id)">
              <i><AppIcon :name="option.icon" :size="17" /></i><span><strong>{{ option.label }}</strong><small>{{ option.detail }}</small></span><AppIcon name="chevron-right" :size="16" />
            </button>
          </section>
          <article v-if="identity" class="user-answer"><p>我目前是{{ identityLabel(identity) }}</p></article>
          <article v-if="pending" class="flow-copy flow-copy--loading"><span>AI 简历助手</span><p><i /><i /><i /></p></article>
          <p v-if="error" class="flow-error" role="alert">{{ error }}</p>
        </template>

        <template v-if="start === 'EXISTING'">
          <article class="flow-copy flow-copy--followup">
            <span>AI 简历助手</span>
            <p>好的。上传现有的 PDF 或 Word 简历，或者把文字粘贴进来，我会按“教育经历”“工作经历”等板块拆成结构化内容。导入后可以在工作台里逐项核对和修改。</p>
          </article>

          <section v-if="!reviewing" class="import-card" aria-label="导入已有简历">
            <div class="import-card__head">
              <strong>导入已有简历</strong>
              <UiSegmented :model-value="importSource" :items="SOURCE_ITEMS" aria-label="导入方式" size="sm" @update:model-value="chooseSource" />
            </div>

            <template v-if="importSource === 'FILE'">
              <label class="import-drop" :class="{ 'is-disabled': working, 'is-invalid': fileAttempted && fileIssue }">
                <input class="sr-only" type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" :disabled="working" data-testid="import-file" @change="chooseFile">
                <span class="import-drop__icon"><Upload :size="22" aria-hidden="true" /></span>
                <strong>{{ selectedFile?.name || '选择简历文件' }}</strong>
                <small>PDF 或 Word（.docx），最大 10 MB。扫描件和图片里的文字无法识别。</small>
              </label>
              <div v-if="libraryFiles.length" class="import-library">
                <span>或者用资料库里已有的简历</span>
                <button
                  v-for="item in libraryFiles"
                  :key="item.id"
                  type="button"
                  class="import-library__item"
                  :class="{ 'is-selected': libraryFileId === item.id }"
                  :aria-pressed="libraryFileId === item.id"
                  :disabled="working"
                  data-testid="import-library-file"
                  @click="chooseLibraryFile(item.id)"
                >
                  <FileText :size="16" aria-hidden="true" />
                  <span>{{ item.displayName || item.originalFilename }}</span>
                  <small>{{ formatRelative(item.updatedAt) }}</small>
                </button>
              </div>
              <p v-else-if="libraryState === 'error'" class="import-card__note">
                资料库里的简历文件没有读取成功。
                <UiButton size="sm" variant="link" @click="loadLibraryFiles">重试</UiButton>
              </p>
              <p v-if="fileAttempted && fileIssue" class="flow-error" role="alert">{{ fileIssue }}</p>
              <p class="import-card__note">上传的文件会保存到求职资料库的“简历”分类，经过安全检查后再解析，之后也可以直接选用。</p>
            </template>

            <UiField
              v-else
              v-slot="{ id, describedBy, invalid }"
              label="简历文字"
              :error="pasteAttempted ? pasteIssue : ''"
              :counter="pasteCounter"
            >
              <UiTextarea
                :id="id"
                v-model="pasteText"
                :aria-describedby="describedBy"
                :invalid="invalid"
                :disabled="working"
                :maxlength="IMPORT_TEXT_MAX"
                :min-rows="8"
                :max-rows="16"
                autosize
                placeholder="例如：&#10;张三&#10;教育经历&#10;浙江大学 计算机科学与技术 2019.09 - 2023.06&#10;工作经历&#10;……"
                data-testid="import-text"
              />
            </UiField>

            <p v-if="working" class="import-card__progress" role="status"><i aria-hidden="true" />{{ workingText }}</p>
            <p v-if="importError" class="flow-error" role="alert">{{ importError }}</p>
            <div class="import-card__actions">
              <UiButton variant="ghost" :disabled="working" data-testid="import-switch-new" @click="chooseStart('NEW')">改为从零新建</UiButton>
              <UiButton v-if="unfinishedImportId && !working" variant="secondary" data-testid="import-resume" @click="resumeImport(unfinishedImportId)">查看解析结果</UiButton>
              <UiButton v-if="importSource === 'FILE'" :pending="working" data-testid="import-parse-file" @click="parseFile">
                {{ working ? workingText : '导入简历文件' }}
              </UiButton>
              <UiButton v-else :pending="working" data-testid="import-parse" @click="parseImport">
                {{ working ? '正在解析…' : '解析简历' }}
              </UiButton>
            </div>
          </section>

          <template v-else>
            <article class="user-answer"><p>{{ reviewSourceText }}</p></article>
            <article class="flow-copy flow-copy--followup">
              <span>AI 简历助手</span>
              <p>解析完成。确认导入后会在工作台中打开，请逐个模块核对：时间、职位等细节可能需要你补全。</p>
            </article>
            <section class="import-card" aria-label="解析结果" data-testid="import-review">
              <div class="import-card__head">
                <strong>识别到的内容</strong>
                <span>只按原文拆分，不会补写或改写你的内容。</span>
              </div>
              <ul class="import-summary">
                <li v-for="item in importSummary" :key="item.key">
                  <span>{{ item.label }}</span><strong>{{ item.count }} {{ item.unit }}</strong>
                </li>
              </ul>
              <p class="import-card__note">
                {{ importContacts.length ? `联系方式：识别到${importContacts.join('、')}。` : '没有识别到联系方式，可以在工作台里补充。' }}
              </p>
              <UiField v-slot="{ id, describedBy, invalid }" label="简历名称" optional :error="importTitleIssue">
                <UiInput
                  :id="id"
                  v-model="importTitle"
                  :aria-describedby="describedBy"
                  :invalid="invalid"
                  :disabled="confirming"
                  :placeholder="importSession?.sourceFilename ? importSession.sourceFilename.replace(/\.(pdf|docx)$/i, '') + '（导入）' : '导入简历'"
                  autocomplete="off"
                />
              </UiField>
              <p v-if="importError" class="flow-error" role="alert">{{ importError }}</p>
              <div class="import-card__actions">
                <UiButton variant="secondary" :disabled="confirming" @click="editAgain">{{ importSession?.sourceType === 'CAREER_FILE' ? '换一个文件' : '重新粘贴' }}</UiButton>
                <UiButton :pending="confirming" :disabled="Boolean(importTitleIssue)" data-testid="import-confirm" @click="confirmImport">
                  {{ confirming ? '正在导入…' : '导入并打开工作台' }}
                </UiButton>
              </div>
            </section>
          </template>
        </template>
      </section>

      <aside class="onboarding-preview">
        <div><span><AppIcon name="file-text" :size="28" /></span><strong>简历预览将在这里生成</strong><p>新建或导入后会生成统一的结构化内容，并实时映射到所选模板。</p></div>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.onboarding-workbench { height: 100vh; display: grid; grid-template-rows: 56px 1fr; overflow: hidden; background: var(--surface-2); }
.onboarding-bar { display: grid; grid-template-columns: 40px 1fr 1fr; align-items: center; padding: 0 16px; background: var(--surface-1); border-bottom: 1px solid var(--border-default); }
.onboarding-bar > strong { text-align: center; font-size: 14px; }
.onboarding-bar > span { height: 100%; padding-left: 18px; display: flex; align-items: center; gap: 7px; color: var(--color-primary); border-left: 1px solid var(--border-default); font-size: 12px; font-weight: 600; }
.onboarding-grid { min-height: 0; display: grid; grid-template-columns: minmax(430px, 46%) minmax(0, 54%); }
.onboarding-flow { min-height: 0; padding: 28px max(28px, 7vw) 60px; display: flex; flex-direction: column; gap: 18px; overflow-y: auto; border-right: 1px solid var(--border-default); scroll-behavior: smooth; }
.flow-copy { max-width: 620px; display: grid; gap: 7px; color: var(--text-primary); }
.flow-copy > span { color: var(--text-tertiary); font-size: 12px; }
.flow-copy p { line-height: 1.7; }
.choice-card { width: min(620px, 100%); padding: 20px; display: grid; gap: 10px; background: var(--surface-1); border: 1px solid var(--border-subtle); border-radius: 8px; box-shadow: 0 8px 24px rgba(15, 23, 42, .05); }
.choice-card > div { margin-bottom: 4px; display: grid; gap: 5px; }
.choice-card > div strong { font-size: 15px; }
.choice-card > div span { color: var(--text-tertiary); font-size: 12px; }
.choice-card > button { width: fit-content; min-width: 190px; min-height: 44px; padding: 0 14px; display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--text-primary); background: var(--surface-1); border: 1px solid var(--border-strong); border-radius: 7px; font-weight: 600; }
.choice-card > button:hover:not(:disabled) { color: var(--color-primary); border-color: var(--color-primary-border); background: var(--surface-2); }
.choice-card > button.is-selected { color: var(--color-primary-text); border-color: var(--color-primary-border); background: var(--color-primary-soft); }
.choice-card > button:disabled:not(.is-selected) { opacity: .55; }
.user-answer { max-width: 74%; align-self: flex-end; animation: enter .18s ease-out; }
.user-answer p { padding: 12px 16px; color: var(--text-on-primary); background: var(--color-primary); border-radius: 8px; box-shadow: 0 7px 18px color-mix(in srgb, var(--color-primary) 16%, transparent); }
.flow-copy--followup { animation: enter .2s ease-out; }
.identity-card { animation: enter .22s ease-out; }
.identity-card > button { width: min(360px, 100%); display: grid; grid-template-columns: 32px 1fr 16px; text-align: left; }
.identity-card > button i { width: 30px; height: 30px; display: grid; place-items: center; color: var(--color-primary); background: var(--color-primary-soft); border-radius: 6px; }
.identity-card > button span { display: grid; }
.identity-card > button small { color: var(--text-tertiary); font-size: 11.5px; font-weight: 400; }
.flow-copy--loading p { width: 52px; height: 38px; display: flex; align-items: center; justify-content: center; gap: 5px; background: var(--surface-1); border-radius: 7px; }
.flow-copy--loading i { width: 6px; height: 6px; border-radius: 50%; background: var(--text-primary); animation: dot 1s ease-in-out infinite; }
.flow-copy--loading i:nth-child(2) { animation-delay: .14s; }.flow-copy--loading i:nth-child(3) { animation-delay: .28s; }
.import-card { width: min(620px, 100%); padding: 20px; display: grid; gap: 14px; background: var(--surface-1); border: 1px solid var(--border-subtle); border-radius: 8px; box-shadow: var(--shadow-sm); animation: enter .22s ease-out; }
.import-card__head { display: grid; gap: 5px; }
.import-card__head strong { font-size: 15px; }
.import-card__head span, .import-card__note { color: var(--text-tertiary); font-size: 12px; line-height: 1.6; }
.import-card__head { grid-template-columns: minmax(0, 1fr) auto; align-items: center; }
.import-card__actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.import-card__actions > :first-child { margin-right: auto; }
.import-drop { min-height: 132px; padding: 18px; display: grid; justify-items: center; align-content: center; gap: 6px; text-align: center; border: 1px dashed var(--border-strong); border-radius: 8px; background: var(--surface-2); cursor: pointer; transition: border-color var(--dur-fast, 120ms) ease, background-color var(--dur-fast, 120ms) ease; }
.import-drop:hover:not(.is-disabled), .import-drop:focus-within { border-color: var(--color-primary-border); background: var(--color-primary-soft); }
.import-drop.is-invalid { border-color: color-mix(in srgb, var(--color-danger) 45%, var(--border-subtle)); }
.import-drop.is-disabled { cursor: default; opacity: .7; }
.import-drop__icon { width: 40px; height: 40px; display: grid; place-items: center; color: var(--color-primary); background: var(--surface-1); border-radius: 8px; }
.import-drop strong { max-width: 100%; overflow-wrap: anywhere; font-size: 14px; }
.import-drop small { color: var(--text-tertiary); font-size: 12px; line-height: 1.6; }
.import-library { display: grid; gap: 6px; }
.import-library > span { color: var(--text-tertiary); font-size: 12px; }
.import-library__item { min-height: 40px; padding: 0 12px; display: grid; grid-template-columns: 16px minmax(0, 1fr) auto; align-items: center; gap: 8px; text-align: left; color: var(--text-primary); background: var(--surface-1); border: 1px solid var(--border-subtle); border-radius: 6px; font-size: 13px; }
.import-library__item span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.import-library__item small { color: var(--text-tertiary); font-size: 12px; }
.import-library__item:hover:not(:disabled) { border-color: var(--color-primary-border); }
.import-library__item.is-selected { color: var(--color-primary-text); border-color: var(--color-primary-border); background: var(--color-primary-soft); }
.import-card__progress { display: flex; align-items: center; gap: 8px; color: var(--text-secondary); font-size: 13px; }
.import-card__progress i { width: 14px; height: 14px; border: 2px solid var(--color-primary-border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin .8s linear infinite; }
.import-summary { margin: 0; padding: 0; list-style: none; display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 8px; }
.import-summary li { padding: 10px 12px; display: flex; align-items: center; justify-content: space-between; gap: 8px; border: 1px solid var(--border-subtle); border-radius: 6px; background: var(--surface-2); font-size: 13px; }
.import-summary li span { color: var(--text-secondary); }
.flow-error { width: min(620px, 100%); padding: 10px 12px; color: var(--color-danger-text); background: var(--surface-2); border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--border-subtle)); border-radius: 6px; }
.onboarding-preview { min-width: 0; display: grid; place-items: center; padding: 24px; }
.onboarding-preview > div { max-width: 320px; display: grid; justify-items: center; gap: 9px; text-align: center; color: var(--text-secondary); }
.onboarding-preview span { width: 52px; height: 52px; display: grid; place-items: center; color: var(--color-primary); background: var(--surface-2); border-radius: 8px; }
.onboarding-preview p { color: var(--text-tertiary); font-size: 12px; line-height: 1.6; }
@keyframes enter { from { opacity: 0; transform: translateY(7px); } to { opacity: 1; transform: translateY(0); } }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes dot { 0%, 60%, 100% { opacity: .35; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-3px); } }
@media (max-width: 800px) { .onboarding-bar { grid-template-columns: 40px 1fr auto; }.onboarding-bar > span { border-left: 0; font-size: 0; }.onboarding-grid { grid-template-columns: 1fr; }.onboarding-flow { padding: 20px 16px 44px; border-right: 0; }.onboarding-preview { display: none; }.choice-card { padding: 16px; }.choice-card > button { width: 100%; }.user-answer { max-width: 88%; } }
@media (prefers-reduced-motion: reduce) { .import-card, .identity-card, .flow-copy--followup, .user-answer, .flow-copy--loading i, .import-card__progress i { animation: none; } }
</style>
