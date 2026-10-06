<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage, isVersionConflict } from '@/shared/api/types'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import AppIcon from '@/shared/ui/AppIcon.vue'
import UiButton from '@/shared/ui/UiButton.vue'
import UiField from '@/shared/ui/UiField.vue'
import UiInput from '@/shared/ui/UiInput.vue'
import UiTextarea from '@/shared/ui/UiTextarea.vue'
import { normalizeResumeTitle, resumeTitleIssue } from '@/features/resume/utils/resumeTitle'
import {
  confirmResumeImport,
  createAiResume,
  ensureAiResume,
  fetchResumeImport,
  selectAiResumeTemplate,
  startResumeTextImport,
} from '../services/aiResumeApi'
import type { AiIdentity, AiResumeConversation, ResumeImportSession } from '../types'
import {
  IMPORT_TEXT_MAX,
  IMPORT_TEXT_MIN,
  importContactFields,
  importDraftSummary,
  importFailureText,
  importTextIssue,
  meaningfulLength,
} from '../utils/textImport'

type ResumeStart = 'NEW' | 'EXISTING'
type ImportStage = 'paste' | 'parsing' | 'review' | 'confirming'

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

const importStage = ref<ImportStage>('paste')
const pasteText = ref('')
const pasteAttempted = ref(false)
const importError = ref('')
const importSession = ref<ResumeImportSession | null>(null)
const importTitle = ref('')
let importAbort: AbortController | null = null

const identityOptions: Array<{ id: AiIdentity; label: string; detail: string; icon: 'book' | 'award' | 'briefcase' }> = [
  { id: 'STUDENT', label: '学生', detail: '实习、课程、社团与项目经历', icon: 'book' },
  { id: 'GRADUATE', label: '应届生', detail: '校招与第一份全职工作', icon: 'award' },
  { id: 'PROFESSIONAL', label: '职场人士', detail: '工作成果与岗位定制', icon: 'briefcase' },
]

const selectedStartText = computed(() => start.value === 'EXISTING'
  ? '我已经有一份简历，想导入后继续完善'
  : '我想从零新建一份简历')
const pasteLength = computed(() => meaningfulLength(pasteText.value))
const pasteIssue = computed(() => importTextIssue(pasteText.value))
const pasteCounter = computed(() => `${pasteLength.value} 字${pasteLength.value < IMPORT_TEXT_MIN ? `（至少 ${IMPORT_TEXT_MIN}）` : ''}`)
const parsing = computed(() => importStage.value === 'parsing')
const confirming = computed(() => importStage.value === 'confirming')
const reviewing = computed(() => Boolean(importSession.value) && (importStage.value === 'review' || confirming.value))
const importSummary = computed(() => importDraftSummary(importSession.value?.structuredDraft))
const importContacts = computed(() => importContactFields(importSession.value?.structuredDraft))
const importTitleIssue = computed(() => normalizeResumeTitle(importTitle.value) ? resumeTitleIssue(importTitle.value) : '')

function chooseStart(value: ResumeStart): void {
  if (pending.value) return
  start.value = value
  identity.value = ''
  error.value = ''
  void nextTick(scrollLatest)
}

async function applyRequestedTemplate(conversation: AiResumeConversation): Promise<void> {
  if (!requestedTemplate.value || requestedTemplate.value === conversation.layout?.templateId) return
  // Best effort: the default layout still works, and the template can be switched in the workbench.
  await selectAiResumeTemplate(conversation.id, requestedTemplate.value, conversation.layout?.version).catch(() => undefined)
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

async function parseImport(): Promise<void> {
  if (parsing.value) return
  pasteAttempted.value = true
  importError.value = ''
  if (pasteIssue.value) return
  importStage.value = 'parsing'
  importAbort?.abort()
  const abort = new AbortController()
  importAbort = abort
  try {
    const started = await startResumeTextImport(pasteText.value)
    await pollTask(started.task.id, () => undefined, abort.signal)
    const session = await fetchResumeImport(started.importSession.id)
    if (session.status !== 'READY_FOR_CONFIRMATION') {
      importError.value = importFailureText(session.errorCode)
      importStage.value = 'paste'
      return
    }
    importSession.value = session
    importStage.value = 'review'
    await nextTick(scrollLatest)
  } catch (reason) {
    if (isAbortError(reason)) return
    importError.value = errorMessage(reason, '简历解析没有成功，请稍后重试。')
    importStage.value = 'paste'
  }
}

function editAgain(): void {
  if (confirming.value) return
  importSession.value = null
  importError.value = ''
  importStage.value = 'paste'
}

async function confirmImport(): Promise<void> {
  const session = importSession.value
  if (!session || confirming.value || importTitleIssue.value) return
  importStage.value = 'confirming'
  importError.value = ''
  let masterId = ''
  try {
    const title = normalizeResumeTitle(importTitle.value) || undefined
    const confirmed = await confirmResumeImport(session.id, title, session.version)
    masterId = confirmed.resultMasterId ?? ''
    if (!masterId) throw new Error('简历没有创建成功，请重新导入。')
    const conversation = await ensureAiResume(masterId)
    await applyRequestedTemplate(conversation)
    await router.replace({ path: `/ai-resume/${conversation.id}`, query: { source: 'import' } })
  } catch (reason) {
    if (masterId) {
      // The resume exists; its own page opens (or explains) the workbench.
      await router.replace(`/resumes/${encodeURIComponent(masterId)}`)
      return
    }
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

onBeforeUnmount(() => importAbort?.abort())
</script>

<template>
  <main class="onboarding-workbench">
    <header class="onboarding-bar">
      <RouterLink class="icon-btn" to="/resumes" title="返回简历列表"><AppIcon name="chevron-left" :size="18" /></RouterLink>
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
          <button type="button" :disabled="Boolean(start)" @click="chooseStart('EXISTING')">有，我已经有简历了<AppIcon name="chevron-right" :size="16" /></button>
          <button type="button" :disabled="Boolean(start)" @click="chooseStart('NEW')">没有，我想新建一份<AppIcon name="chevron-right" :size="16" /></button>
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
            <p>好的。把现有简历的文字复制粘贴到下面，我会按“教育经历”“工作经历”等板块标题拆成结构化内容。导入后可以在工作台里逐项核对和修改。</p>
          </article>

          <section v-if="!reviewing" class="import-card" aria-label="粘贴已有简历文本">
            <div class="import-card__head">
              <strong>粘贴已有简历文本</strong>
              <span>可以从 Word、PDF 或网页复制文字；图片里的文字无法识别。</span>
            </div>
            <UiField
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
                :disabled="parsing"
                :maxlength="IMPORT_TEXT_MAX"
                :min-rows="8"
                :max-rows="16"
                autosize
                placeholder="例如：&#10;张三&#10;教育经历&#10;浙江大学 计算机科学与技术 2019.09 - 2023.06&#10;工作经历&#10;……"
                data-testid="import-text"
              />
            </UiField>
            <p v-if="importError" class="flow-error" role="alert">{{ importError }}</p>
            <div class="import-card__actions">
              <UiButton :pending="parsing" data-testid="import-parse" @click="parseImport">
                {{ parsing ? '正在解析…' : '解析简历' }}
              </UiButton>
            </div>
          </section>

          <template v-else>
            <article class="user-answer"><p>这是我的简历（{{ pasteLength }} 字）</p></article>
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
                  placeholder="导入简历"
                  autocomplete="off"
                />
              </UiField>
              <p v-if="importError" class="flow-error" role="alert">{{ importError }}</p>
              <div class="import-card__actions">
                <UiButton variant="secondary" :disabled="confirming" @click="editAgain">重新粘贴</UiButton>
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
.onboarding-bar { display: grid; grid-template-columns: 40px 1fr 1fr; align-items: center; padding: 0 16px; background: var(--surface-1); border-bottom: 1px solid var(--border); }
.onboarding-bar > strong { text-align: center; font-size: 14px; }
.onboarding-bar > span { height: 100%; padding-left: 18px; display: flex; align-items: center; gap: 7px; color: var(--color-primary); border-left: 1px solid var(--border); font-size: 12px; font-weight: 600; }
.onboarding-grid { min-height: 0; display: grid; grid-template-columns: minmax(430px, 46%) minmax(0, 54%); }
.onboarding-flow { min-height: 0; padding: 28px max(28px, 7vw) 60px; display: flex; flex-direction: column; gap: 18px; overflow-y: auto; border-right: 1px solid var(--border); scroll-behavior: smooth; }
.flow-copy { max-width: 620px; display: grid; gap: 7px; color: var(--text-primary); }
.flow-copy > span { color: var(--text-tertiary); font-size: 12px; }
.flow-copy p { line-height: 1.7; }
.choice-card { width: min(620px, 100%); padding: 20px; display: grid; gap: 10px; background: var(--surface-1); border: 1px solid var(--border-subtle); border-radius: 8px; box-shadow: 0 8px 24px rgba(15, 23, 42, .05); }
.choice-card > div { margin-bottom: 4px; display: grid; gap: 5px; }
.choice-card > div strong { font-size: 15px; }
.choice-card > div span { color: var(--text-tertiary); font-size: 12px; }
.choice-card > button { width: fit-content; min-width: 190px; min-height: 44px; padding: 0 14px; display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--text); background: var(--surface-1); border: 1px solid var(--border-strong); border-radius: 7px; font-weight: 600; }
.choice-card > button:hover:not(:disabled) { color: var(--color-primary); border-color: var(--color-primary-border); background: var(--surface-2); }
.choice-card > button:disabled { opacity: .55; }
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
.import-card__actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.import-summary { margin: 0; padding: 0; list-style: none; display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 8px; }
.import-summary li { padding: 10px 12px; display: flex; align-items: center; justify-content: space-between; gap: 8px; border: 1px solid var(--border-subtle); border-radius: 6px; background: var(--surface-2); font-size: 13px; }
.import-summary li span { color: var(--text-secondary); }
.flow-error { width: min(620px, 100%); padding: 10px 12px; color: var(--color-danger-text); background: var(--surface-2); border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--border-subtle)); border-radius: 6px; }
.onboarding-preview { min-width: 0; display: grid; place-items: center; padding: 24px; }
.onboarding-preview > div { max-width: 320px; display: grid; justify-items: center; gap: 9px; text-align: center; color: var(--text-secondary); }
.onboarding-preview span { width: 52px; height: 52px; display: grid; place-items: center; color: var(--color-primary); background: var(--surface-2); border-radius: 8px; }
.onboarding-preview p { color: var(--text-tertiary); font-size: 12px; line-height: 1.6; }
@keyframes enter { from { opacity: 0; transform: translateY(7px); } to { opacity: 1; transform: translateY(0); } }
@keyframes dot { 0%, 60%, 100% { opacity: .35; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-3px); } }
@media (max-width: 800px) { .onboarding-bar { grid-template-columns: 40px 1fr auto; }.onboarding-bar > span { border-left: 0; font-size: 0; }.onboarding-grid { grid-template-columns: 1fr; }.onboarding-flow { padding: 20px 16px 44px; border-right: 0; }.onboarding-preview { display: none; }.choice-card { padding: 16px; }.choice-card > button { width: 100%; }.user-answer { max-width: 88%; } }
@media (prefers-reduced-motion: reduce) { .import-card, .identity-card, .flow-copy--followup, .user-answer, .flow-copy--loading i { animation: none; } }
</style>
