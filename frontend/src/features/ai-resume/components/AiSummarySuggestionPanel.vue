<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { errorMessage } from '@/shared/api/types'
import AppIcon from '@/shared/ui/AppIcon.vue'
import { cancelAiResumeResponse, generateAiResumeSummarySuggestions } from '../services/aiResumeApi'
import type { AiSummaryCandidate } from '../types'
import { chooseSummaryDraft, summaryVisibleCharacters } from '../utils/summarySuggestions'

type PanelStatus = 'idle' | 'loading' | 'ready' | 'error'

const props = defineProps<{
  conversationId: string
  cardId: string
  currentSummary: string
  disabledReason?: string
}>()

const emit = defineEmits<{
  apply: [text: string]
  quota: [remaining: number]
  busy: [pending: boolean]
}>()

const open = ref(false)
const status = ref<PanelStatus>('idle')
const candidates = ref<AiSummaryCandidate[]>([])
const selectedStyle = ref('')
const requestId = ref('')
const panelError = ref('')
let controller: AbortController | null = null
let generatedFrom = ''

async function generate(): Promise<void> {
  if (props.disabledReason) {
    open.value = true
    status.value = 'error'
    panelError.value = props.disabledReason
    return
  }
  controller?.abort()
  controller = new AbortController()
  requestId.value = crypto.randomUUID()
  generatedFrom = props.currentSummary
  open.value = true
  status.value = 'loading'
  panelError.value = ''
  emit('busy', true)
  try {
    const response = await generateAiResumeSummarySuggestions(
      props.conversationId, props.cardId, generatedFrom, requestId.value, controller.signal,
    )
    if (props.currentSummary !== generatedFrom) {
      status.value = 'error'
      panelError.value = '生成期间个人简介草稿已变化，请按最新内容重新生成。'
      return
    }
    candidates.value = response.candidates
    selectedStyle.value = response.candidates[0]?.style ?? ''
    status.value = 'ready'
    emit('quota', response.remainingQuota)
  } catch (reason) {
    if (controller?.signal.aborted) return
    status.value = 'error'
    panelError.value = errorMessage(reason, 'AI 个人简介生成失败，当前草稿没有变化')
  } finally {
    controller = null
    emit('busy', false)
  }
}

async function cancelGeneration(): Promise<void> {
  const activeRequest = requestId.value
  controller?.abort()
  controller = null
  emit('busy', false)
  if (activeRequest) {
    try { await cancelAiResumeResponse(props.conversationId, activeRequest) } catch { /* Local abort still wins. */ }
  }
  status.value = candidates.value.length ? 'ready' : 'idle'
  panelError.value = ''
}

function applyCandidate(candidate: AiSummaryCandidate): void {
  selectedStyle.value = candidate.style
  const choice = chooseSummaryDraft(props.currentSummary, candidate.text)
  if (!choice.next) return
  emit('apply', choice.next)
}

function sourceTitle(candidate: AiSummaryCandidate): string {
  return candidate.sourceRefs.map((source) => `${source.label}：${source.excerpt}`).join('\n')
}

watch(() => props.cardId, () => {
  controller?.abort()
  emit('busy', false)
  open.value = false
  status.value = 'idle'
  candidates.value = []
  selectedStyle.value = ''
  panelError.value = ''
})

onBeforeUnmount(() => {
  controller?.abort()
  emit('busy', false)
})
</script>

<template>
  <section class="summary-ai" :class="{ 'is-open': open }">
    <header class="summary-ai__header">
      <div>
        <span class="summary-ai__icon"><AppIcon name="sparkles" :size="15" /></span>
        <span><strong>AI 个人简介帮写</strong><small>基于已确认事实生成 3 个版本</small></span>
      </div>
      <button
        type="button" class="summary-ai__trigger" :disabled="status === 'loading'"
        :title="disabledReason || '生成三个个人简介方案'"
        @click="status === 'ready' ? (open = !open) : generate()"
      >
        <AppIcon :name="status === 'loading' ? 'loader' : open && status === 'ready' ? 'chevron-up' : 'sparkles'" :size="13" />
        {{ status === 'loading' ? '生成中' : open && status === 'ready' ? '收起' : candidates.length ? '查看方案' : 'AI 帮写' }}
      </button>
    </header>

    <div v-if="open" class="summary-ai__body">
      <div v-if="status === 'idle'" class="summary-ai__empty">
        <p>将使用已确认的目标岗位、教育、经历、项目、组织和技能，不读取姓名或联系方式。</p>
        <button type="button" :disabled="Boolean(disabledReason)" :title="disabledReason" @click="generate">
          <AppIcon name="sparkles" :size="13" />生成三个版本
        </button>
      </div>

      <div v-else-if="status === 'loading'" class="summary-ai__loading" role="status">
        <span aria-hidden="true"><i /><i /><i /></span>
        <p>正在核对事实来源并组织三个简介版本</p>
        <button type="button" @click="cancelGeneration">取消</button>
      </div>

      <div v-else-if="status === 'ready'" class="summary-ai__candidates">
        <article
          v-for="candidate in candidates" :key="candidate.style" class="summary-candidate"
          :class="{ 'is-selected': selectedStyle === candidate.style }"
          @click="selectedStyle = candidate.style"
        >
          <header>
            <strong>{{ candidate.style }}</strong>
            <span>{{ summaryVisibleCharacters(candidate.text) }} 字</span>
          </header>
          <p>{{ candidate.text }}</p>
          <small>{{ candidate.reason }}</small>
          <div class="summary-candidate__sources">
            <span v-for="source in candidate.sourceRefs" :key="source.key" :title="sourceTitle(candidate)">
              <AppIcon name="link" :size="10" />{{ source.label }}
            </span>
          </div>
          <button type="button" @click.stop="applyCandidate(candidate)">
            <AppIcon name="check" :size="13" />使用此版本
          </button>
        </article>
        <footer>
          <span>使用后仅写入草稿，仍需确认此模块。</span>
          <button type="button" :disabled="Boolean(disabledReason)" :title="disabledReason" @click="generate">
            <AppIcon name="refresh" :size="12" />重新生成
          </button>
        </footer>
      </div>

      <div v-else class="summary-ai__error" role="alert">
        <AppIcon name="alert-circle" :size="14" />
        <span>{{ panelError }}</span>
        <button type="button" :disabled="Boolean(disabledReason)" @click="generate">重试</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.summary-ai { min-width: 0; border: 1px solid var(--border-default); border-radius: 6px; background: var(--surface-1); overflow: hidden; }
.summary-ai__header { min-height: 50px; padding: 9px 10px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.summary-ai__header > div { min-width: 0; display: flex; align-items: center; gap: 8px; }
.summary-ai__header > div > span:last-child { min-width: 0; display: grid; gap: 2px; }
.summary-ai__header strong { color: var(--text); font-size: 12px; }
.summary-ai__header small { color: var(--text-tertiary); font-size: 11px; }
.summary-ai__icon { width: 28px; height: 28px; flex: none; display: grid; place-items: center; color: var(--color-primary-text); background: var(--surface-2); border-radius: 50%; }
.summary-ai__trigger { min-height: 28px; padding: 0 8px; flex: none; display: inline-flex; align-items: center; gap: 5px; color: var(--color-primary-text); background: var(--surface-1); border: 1px solid var(--color-primary-border); border-radius: 5px; font-size: 11.5px; font-weight: 700; }
.summary-ai__trigger:hover:not(:disabled) { background: var(--surface-2); border-color: var(--color-primary); }
.summary-ai__trigger:disabled { color: var(--color-primary); background: var(--surface-2); border-color: var(--border-default); cursor: not-allowed; }
.summary-ai__trigger .app-icon[name="loader"] { animation: summary-spin 1s linear infinite; }
.summary-ai__body { padding: 0 10px 10px; animation: summary-enter .24s ease-out both; }
.summary-ai__empty { min-height: 48px; padding: 9px; display: flex; align-items: center; justify-content: space-between; gap: 10px; background: var(--surface-2); border-left: 2px solid var(--color-primary); }
.summary-ai__empty p { color: var(--text-secondary); font-size: 11.5px; line-height: 1.55; }
.summary-ai__empty button, .summary-ai__loading button, .summary-ai__error button, .summary-ai__candidates > footer button { min-height: 27px; padding: 0 7px; flex: none; display: inline-flex; align-items: center; gap: 4px; color: var(--color-primary-text); background: var(--surface-1); border: 1px solid var(--color-primary-border); border-radius: 4px; font-size: 11px; font-weight: 700; }
.summary-ai__loading { min-height: 48px; padding: 9px; display: flex; align-items: center; gap: 8px; background: var(--surface-2); }
.summary-ai__loading > span { width: 26px; display: inline-flex; gap: 3px; }
.summary-ai__loading i { width: 5px; height: 5px; border-radius: 50%; background: var(--color-primary); animation: summary-dot 1.1s ease-in-out infinite; }
.summary-ai__loading i:nth-child(2) { animation-delay: .13s; }
.summary-ai__loading i:nth-child(3) { animation-delay: .26s; }
.summary-ai__loading p { min-width: 0; flex: 1; color: var(--text-secondary); font-size: 11.5px; }
.summary-ai__candidates { display: grid; gap: 7px; }
.summary-candidate { padding: 10px; display: grid; gap: 7px; background: var(--surface-1); border: 1px solid var(--border-subtle); border-radius: 5px; cursor: pointer; transition: border-color .16s ease, box-shadow .16s ease; }
.summary-candidate:hover, .summary-candidate.is-selected { border-color: var(--color-primary); box-shadow: 0 0 0 1px rgba(69, 112, 177, .08); }
.summary-candidate > header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.summary-candidate > header strong { color: var(--color-primary-text); font-size: 11.5px; }
.summary-candidate > header span { color: var(--text-tertiary); font-size: 11px; }
.summary-candidate > p { color: var(--text); font-size: 12px; line-height: 1.72; overflow-wrap: anywhere; }
.summary-candidate > small { color: var(--text-tertiary); font-size: 11px; line-height: 1.45; }
.summary-candidate__sources { display: flex; flex-wrap: wrap; gap: 4px; }
.summary-candidate__sources span { max-width: 100%; padding: 2px 5px; display: inline-flex; align-items: center; gap: 3px; color: var(--color-primary-text); background: var(--surface-2); border-radius: 3px; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-candidate > button { min-height: 28px; justify-self: end; padding: 0 8px; display: inline-flex; align-items: center; gap: 4px; color: var(--text-on-primary); background: var(--color-primary); border: 1px solid var(--color-primary); border-radius: 4px; font-size: 11px; font-weight: 700; }
.summary-ai__candidates > footer { padding-top: 2px; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.summary-ai__candidates > footer span { color: var(--text-tertiary); font-size: 11px; }
.summary-ai__error { min-height: 42px; padding: 8px 9px; display: flex; align-items: center; gap: 7px; color: var(--color-danger-text); background: var(--surface-2); border-left: 2px solid var(--color-danger); }
.summary-ai__error span { min-width: 0; flex: 1; font-size: 11.5px; line-height: 1.45; }
@keyframes summary-spin { to { transform: rotate(360deg); } }
@keyframes summary-dot { 0%, 70%, 100% { opacity: .35; transform: translateY(0); } 35% { opacity: 1; transform: translateY(-2px); } }
@keyframes summary-enter { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 520px) {
  .summary-ai__header { align-items: flex-start; }
  .summary-ai__empty { align-items: stretch; flex-direction: column; }
  .summary-ai__empty button { justify-content: center; }
  .summary-ai__candidates > footer { align-items: stretch; flex-direction: column; }
  .summary-ai__candidates > footer button, .summary-candidate > button { width: 100%; justify-content: center; }
}
@media (prefers-reduced-motion: reduce) {
  .summary-ai__trigger .app-icon, .summary-ai__body, .summary-ai__loading i, .summary-candidate { animation: none; transition: none; }
}
</style>
