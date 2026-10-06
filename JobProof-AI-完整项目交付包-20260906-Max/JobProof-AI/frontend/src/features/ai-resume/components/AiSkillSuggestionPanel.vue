<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { errorMessage } from '@/shared/api/types'
import AppIcon from '@/shared/ui/AppIcon.vue'
import {
  cancelAiResumeResponse,
  generateAiResumeSkillSuggestion,
} from '../services/aiResumeApi'
import { canonicalSkillCategory } from '../utils/skillSuggestions'
import type {
  AiSkillGroupSuggestion,
  AiSkillNameCandidate,
  AiSkillSuggestionMode,
} from '../types'

type PanelStatus = 'idle' | 'loading-names' | 'names' | 'loading-details' | 'details' | 'error'

const props = withDefaults(defineProps<{
  conversationId: string
  cardId: string
  currentItems: Record<string, unknown>[]
  disabledReason?: string
}>(), { disabledReason: '' })

const emit = defineEmits<{
  apply: [groups: AiSkillGroupSuggestion[]]
  quota: [remaining: number]
}>()

const open = ref(false)
const mode = ref<AiSkillSuggestionMode>('EXTRACT')
const status = ref<PanelStatus>('idle')
const requestId = ref('')
const candidates = ref<AiSkillNameCandidate[]>([])
const groups = ref<AiSkillGroupSuggestion[]>([])
const selectedNames = ref<string[]>([])
const ownershipConfirmed = ref(false)
const detailsConfirmed = ref(false)
const panelError = ref('')
let controller: AbortController | null = null

const modes: Array<{ value: AiSkillSuggestionMode; label: string }> = [
  { value: 'EXTRACT', label: '从经历提取' },
  { value: 'EXPAND', label: '补充同类技能' },
  { value: 'JOB', label: '按岗位推荐' },
]

const pending = computed(() => status.value === 'loading-names' || status.value === 'loading-details')
const selectedCandidates = computed(() => candidates.value.filter((item) => selectedNames.value.includes(item.name)))
const selectedUnsupported = computed(() => selectedCandidates.value.filter((item) => item.evidenceStatus !== 'SUPPORTED'))

function chooseMode(value: AiSkillSuggestionMode): void {
  if (pending.value || mode.value === value) return
  mode.value = value
  status.value = 'idle'
  candidates.value = []
  groups.value = []
  selectedNames.value = []
  ownershipConfirmed.value = false
  detailsConfirmed.value = false
  panelError.value = ''
}

function toggleCandidate(name: string): void {
  selectedNames.value = selectedNames.value.includes(name)
    ? selectedNames.value.filter((item) => item !== name)
    : [...selectedNames.value, name]
  ownershipConfirmed.value = false
  detailsConfirmed.value = false
}

function selectSupported(): void {
  selectedNames.value = candidates.value.filter((item) => item.evidenceStatus === 'SUPPORTED').map((item) => item.name)
  ownershipConfirmed.value = false
}

async function generateNames(): Promise<void> {
  if (props.disabledReason) { panelError.value = props.disabledReason; return }
  controller?.abort()
  controller = new AbortController()
  requestId.value = crypto.randomUUID()
  status.value = 'loading-names'
  panelError.value = ''
  groups.value = []
  detailsConfirmed.value = false
  try {
    const response = await generateAiResumeSkillSuggestion(
      props.conversationId, props.cardId, 'NAMES', mode.value, props.currentItems,
      [], [], requestId.value, controller.signal,
    )
    candidates.value = response.candidates
    selectedNames.value = []
    ownershipConfirmed.value = false
    status.value = 'names'
    emit('quota', response.remainingQuota)
  } catch (reason) {
    if (controller?.signal.aborted) return
    status.value = 'error'
    panelError.value = errorMessage(reason, 'AI 技能名称生成失败，当前内容没有变化')
  } finally {
    controller = null
  }
}

async function generateDetails(): Promise<void> {
  if (!selectedNames.value.length) { panelError.value = '请至少选择一个技能名称。'; return }
  if (selectedUnsupported.value.length && !ownershipConfirmed.value) {
    panelError.value = '请先确认所选的待核实技能确实掌握。'
    return
  }
  controller?.abort()
  controller = new AbortController()
  requestId.value = crypto.randomUUID()
  status.value = 'loading-details'
  panelError.value = ''
  try {
    const response = await generateAiResumeSkillSuggestion(
      props.conversationId, props.cardId, 'DETAILS', mode.value, props.currentItems,
      selectedNames.value,
      ownershipConfirmed.value ? selectedUnsupported.value.map((item) => item.name) : [],
      requestId.value, controller.signal,
    )
    groups.value = response.groups
    detailsConfirmed.value = false
    status.value = 'details'
    emit('quota', response.remainingQuota)
  } catch (reason) {
    if (controller?.signal.aborted) return
    status.value = 'error'
    panelError.value = errorMessage(reason, 'AI 技能条目生成失败，当前内容没有变化')
  } finally {
    controller = null
  }
}

async function cancelGeneration(): Promise<void> {
  const activeRequest = requestId.value
  controller?.abort()
  controller = null
  if (activeRequest) {
    try { await cancelAiResumeResponse(props.conversationId, activeRequest) } catch { /* Local abort still wins. */ }
  }
  status.value = candidates.value.length ? 'names' : 'idle'
  panelError.value = ''
}

function applyGroups(): void {
  if (!detailsConfirmed.value || !groups.value.length) return
  emit('apply', groups.value)
  open.value = false
  status.value = 'idle'
  candidates.value = []
  groups.value = []
  selectedNames.value = []
  ownershipConfirmed.value = false
  detailsConfirmed.value = false
  panelError.value = ''
}

function sourceTitle(candidate: AiSkillNameCandidate): string {
  return candidate.sourceRefs.map((source) => `${source.label}：${source.excerpt}`).join('\n')
}

function evidenceLabel(candidate: AiSkillNameCandidate): string {
  if (candidate.evidenceStatus === 'SUPPORTED') return '事实支持'
  if (candidate.evidenceStatus === 'GAP') return '岗位缺口'
  return '需要确认'
}

function descriptionCharacters(value: string): number {
  return Array.from(value).filter((character) => !/\s/.test(character)).length
}

function mergeImpact(group: AiSkillGroupSuggestion): string {
  const category = canonicalSkillCategory(group.category)
  const existing = props.currentItems.find((item) => canonicalSkillCategory(String(item.category ?? '')) === category)
  if (!existing) return `将新增「${category}」类别`
  const existingNames = new Set(
    (Array.isArray(existing.items) ? existing.items : []).map((item) => String(item).trim().toLowerCase()),
  )
  const added = group.items.filter((item) => !existingNames.has(item.trim().toLowerCase())).length
  return added
    ? `合并到已有「${category}」：保留原技能与说明，新增 ${added} 项`
    : `补充已有「${category}」的说明，不删除原内容`
}

watch(() => props.cardId, () => {
  controller?.abort()
  open.value = false
  status.value = 'idle'
  candidates.value = []
  groups.value = []
  selectedNames.value = []
})
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="skill-ai" :class="{ 'is-open': open }">
    <header class="skill-ai__header">
      <div><AppIcon name="sparkles" :size="15" /><span><strong>AI 技能提取与建议</strong><small>确认后才写入简历</small></span></div>
      <button
        type="button"
        class="skill-ai__toggle"
        :disabled="Boolean(disabledReason)"
        :title="disabledReason || (open ? '收起技能建议' : '生成技能名称备选')"
        @click="open = !open"
      ><AppIcon :name="open ? 'chevron-up' : 'sparkles'" :size="13" />{{ open ? '收起' : 'AI 填充' }}</button>
    </header>

    <Transition name="skill-panel">
      <div v-if="open" class="skill-ai__body">
      <div class="skill-ai__modes" role="tablist" aria-label="技能生成方式">
        <button
          v-for="item in modes" :key="item.value" type="button" role="tab"
          :aria-selected="mode === item.value" :class="{ 'is-active': mode === item.value }"
          :disabled="pending" @click="chooseMode(item.value)"
        >{{ item.label }}</button>
      </div>

      <div v-if="status === 'idle'" class="skill-ai__start">
        <button type="button" class="skill-ai__primary" @click="generateNames"><AppIcon name="sparkles" :size="13" />生成名称备选</button>
      </div>

      <div v-else-if="pending" class="skill-ai__loading" role="status">
        <span aria-hidden="true"><i /><i /><i /></span>
        <p>{{ status === 'loading-names' ? '正在识别技能名称与事实来源' : '正在整理技能分组和补充描述' }}</p>
        <button type="button" @click="cancelGeneration">取消</button>
      </div>

      <div v-else-if="status === 'names'" class="skill-ai__names">
        <div class="skill-ai__names-head"><span>{{ candidates.length }} 个名称备选</span><button type="button" @click="selectSupported">只选事实支持项</button></div>
        <div class="skill-candidate-list">
          <label v-for="candidate in candidates" :key="candidate.name" class="skill-candidate" :class="[`is-${candidate.evidenceStatus.toLowerCase()}`, { 'is-selected': selectedNames.includes(candidate.name) }]">
            <input type="checkbox" :checked="selectedNames.includes(candidate.name)" @change="toggleCandidate(candidate.name)" />
            <span class="skill-candidate__main">
              <strong>{{ candidate.name }}</strong><small>{{ candidate.category }}</small>
              <em>{{ candidate.reason }}</em>
            </span>
            <span class="skill-candidate__meta" :title="sourceTitle(candidate)">
              <b>{{ evidenceLabel(candidate) }}</b>
              <small>{{ candidate.sourceRefs[0]?.label || '待用户确认' }}</small>
            </span>
          </label>
        </div>
        <label v-if="selectedUnsupported.length" class="skill-ai__confirmation">
          <input v-model="ownershipConfirmed" type="checkbox" />
          <span>我确认所选的 {{ selectedUnsupported.map((item) => item.name).join('、') }} 确实掌握，不是仅为匹配岗位添加</span>
        </label>
        <footer class="skill-ai__actions">
          <span>已选 {{ selectedNames.length }} 项</span>
          <button type="button" @click="generateNames"><AppIcon name="refresh" :size="12" />换一批</button>
          <button type="button" class="skill-ai__primary" :disabled="!selectedNames.length || (selectedUnsupported.length > 0 && !ownershipConfirmed)" @click="generateDetails">生成技能条目</button>
        </footer>
      </div>

      <div v-else-if="status === 'details'" class="skill-ai__details">
        <article v-for="group in groups" :key="group.category">
          <header><strong>{{ group.category }}</strong><span>{{ group.items.join(' · ') }} · {{ descriptionCharacters(group.description) }} 字</span></header>
          <small class="skill-ai__impact"><AppIcon name="link" :size="11" />{{ mergeImpact(group) }}</small>
          <p>{{ group.description }}</p>
          <div class="skill-ai__sources"><span v-for="source in group.sourceRefs" :key="source.key" :title="source.excerpt">{{ source.label }}</span></div>
          <ul><li v-for="item in group.verificationItems" :key="item">{{ item }}</li></ul>
        </article>
        <label class="skill-ai__confirmation">
          <input v-model="detailsConfirmed" type="checkbox" />
          <span>我确认以上技能名称和补充描述符合真实情况；加入后只合并和追加，不删除已有技能</span>
        </label>
        <footer class="skill-ai__actions">
          <button type="button" @click="status = 'names'">返回名称备选</button>
          <button type="button" class="skill-ai__primary" :disabled="!detailsConfirmed" @click="applyGroups"><AppIcon name="check" :size="13" />加入技能草稿</button>
        </footer>
      </div>

      <div v-if="status === 'error' || panelError" class="skill-ai__error" role="alert">
        <AppIcon name="alert-circle" :size="14" /><span>{{ panelError }}</span>
        <button type="button" @click="status = candidates.length ? 'names' : 'idle'; panelError = ''">关闭</button>
      </div>
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.skill-ai { min-width: 0; margin: 4px 0 10px; border: 1px solid #d6dfeb; border-radius: 6px; background: #fbfcfe; overflow: hidden; }
.skill-ai__header { min-height: 44px; padding: 7px 9px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.skill-ai__header > div { min-width: 0; display: flex; align-items: center; gap: 7px; color: #2c64ae; }
.skill-ai__header > div > span { min-width: 0; display: flex; align-items: baseline; gap: 7px; }
.skill-ai__header strong { color: var(--text); font-size: 11px; }
.skill-ai__header small { color: var(--text-3); font-size: 9px; font-weight: 500; }
.skill-ai__toggle, .skill-ai__primary { min-height: 28px; padding: 0 8px; display: inline-flex; align-items: center; justify-content: center; gap: 5px; border: 1px solid #9ab7e4; border-radius: 4px; background: #eef5ff; color: #245fae; font-size: 10px; font-weight: 700; }
.skill-ai__toggle:hover:not(:disabled), .skill-ai__primary:hover:not(:disabled) { background: #e2edff; border-color: #7299d4; }
.skill-ai button:disabled { cursor: not-allowed; opacity: .55; }
.skill-ai__body { padding: 10px; display: grid; gap: 10px; border-top: 1px solid #e1e7ef; background: #fff; }
.skill-panel-enter-active, .skill-panel-leave-active { overflow: hidden; transition: opacity var(--motion-base) ease, transform var(--motion-base) var(--motion-ease), max-height var(--motion-slow) var(--motion-ease); }
.skill-panel-enter-from, .skill-panel-leave-to { max-height: 0; opacity: 0; transform: translateY(-4px); }
.skill-panel-enter-to, .skill-panel-leave-from { max-height: 680px; }
.skill-ai__modes { position: relative; isolation: isolate; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); padding: 2px; border: 1px solid #d7dee8; border-radius: 5px; background: #f2f4f7; }
.skill-ai__modes::before { position: absolute; z-index: 0; inset: 2px auto 2px 2px; width: calc((100% - 4px) / 3); border-radius: 3px; background: #fff; box-shadow: 0 1px 3px rgba(23, 43, 77, .12); content: ''; pointer-events: none; transition: transform var(--motion-base) var(--motion-ease); }
.skill-ai__modes:has(button:nth-child(2).is-active)::before { transform: translateX(100%); }
.skill-ai__modes:has(button:nth-child(3).is-active)::before { transform: translateX(200%); }
.skill-ai__modes button { position: relative; z-index: 1; min-height: 28px; padding: 0 5px; border: 0; border-radius: 3px; background: transparent; color: var(--text-3); font-size: 10px; font-weight: 600; transition: color var(--motion-base) ease, transform var(--motion-fast) var(--motion-ease); }
.skill-ai__modes button.is-active { background: transparent; color: #245fae; box-shadow: none; }
.skill-ai__modes button:active { transform: scale(.97); }
.skill-ai__start { display: flex; justify-content: flex-end; }
.skill-ai__loading { min-height: 42px; padding: 8px 10px; display: flex; align-items: center; gap: 8px; background: #f5f8fc; color: var(--text-2); font-size: 10px; }
.skill-ai__loading > span { width: 24px; display: flex; gap: 3px; }
.skill-ai__loading i { width: 4px; height: 4px; border-radius: 50%; background: #4e7fc8; animation: skill-dot 1.1s ease-in-out infinite; }
.skill-ai__loading i:nth-child(2) { animation-delay: .13s; }.skill-ai__loading i:nth-child(3) { animation-delay: .26s; }
.skill-ai__loading p { flex: 1; }.skill-ai__loading button { border: 0; background: transparent; color: var(--primary); font-size: 10px; }
.skill-ai__names, .skill-ai__details { min-width: 0; display: grid; gap: 8px; }
.skill-ai__names-head { display: flex; align-items: center; justify-content: space-between; color: var(--text-3); font-size: 9px; }
.skill-ai__names-head button { border: 0; background: transparent; color: var(--primary); font-size: 9px; }
.skill-candidate-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
.skill-candidate { min-width: 0; min-height: 68px; padding: 8px; display: grid; grid-template-columns: 15px minmax(0, 1fr) auto; align-items: start; gap: 7px; border: 1px solid #d8dfe8; border-radius: 5px; background: #fff; cursor: pointer; }
.skill-candidate:hover { border-color: #9db7de; }.skill-candidate.is-selected { border-color: #5c8ed5; background: #f6f9ff; }
.skill-candidate > input { width: 14px; height: 14px; margin: 2px 0 0; accent-color: var(--primary); }
.skill-candidate__main { min-width: 0; display: grid; gap: 2px; }
.skill-candidate__main strong { color: var(--text); font-size: 11px; overflow-wrap: anywhere; }
.skill-candidate__main small { color: var(--text-3); font-size: 9px; }
.skill-candidate__main em { color: var(--text-2); font-size: 9px; font-style: normal; line-height: 1.45; }
.skill-candidate__meta { max-width: 76px; display: grid; justify-items: end; gap: 3px; text-align: right; }
.skill-candidate__meta b { padding: 2px 4px; border-radius: 3px; color: #2c6a4d; background: #e9f6ef; font-size: 8px; }
.skill-candidate.is-needs_confirmation .skill-candidate__meta b { color: #875a16; background: #fff5df; }
.skill-candidate.is-gap .skill-candidate__meta b { color: #8c3940; background: #fff0f1; }
.skill-candidate__meta small { color: var(--text-3); font-size: 8px; line-height: 1.3; }
.skill-ai__confirmation { padding: 7px 8px; display: flex; align-items: flex-start; gap: 7px; border-left: 2px solid #ce9131; background: #fffaf0; color: #624716; font-size: 9px; line-height: 1.5; cursor: pointer; }
.skill-ai__confirmation input { width: 14px; height: 14px; flex: none; margin: 0; accent-color: var(--primary); }
.skill-ai__actions { display: flex; align-items: center; justify-content: flex-end; gap: 7px; }
.skill-ai__actions > span { margin-right: auto; color: var(--text-3); font-size: 9px; }
.skill-ai__actions > button:not(.skill-ai__primary) { min-height: 28px; padding: 0 7px; border: 1px solid #d3dae4; border-radius: 4px; background: #fff; color: var(--text-2); font-size: 9px; }
.skill-ai__details article { padding: 9px 10px; display: grid; gap: 7px; border-left: 2px solid #5f8fd3; background: #f7faff; }
.skill-ai__details article header { display: flex; align-items: baseline; gap: 8px; }.skill-ai__details article header strong { color: var(--text); font-size: 11px; }.skill-ai__details article header span { color: #376cae; font-size: 9px; }
.skill-ai__details article p { color: var(--text-2); font-size: 10px; line-height: 1.65; white-space: pre-line; }
.skill-ai__impact { display: inline-flex; align-items: center; gap: 4px; color: #376cae; font-size: 8px; font-weight: 600; }
.skill-ai__details article ul { margin: 0; padding-left: 16px; color: #75551e; font-size: 8px; line-height: 1.5; }
.skill-ai__sources { display: flex; flex-wrap: wrap; gap: 4px; }.skill-ai__sources span { padding: 2px 4px; border-radius: 3px; background: #e9f1ff; color: #3d659a; font-size: 8px; }
.skill-ai__error { min-height: 34px; padding: 7px 8px; display: flex; align-items: center; gap: 7px; border-left: 2px solid #d36a6a; background: #fff5f5; color: #9a3030; font-size: 9px; }
.skill-ai__error span { flex: 1; }.skill-ai__error button { border: 0; background: transparent; color: #9a3030; font-size: 9px; }
@keyframes skill-dot { 0%, 70%, 100% { opacity: .3; transform: translateY(0); } 35% { opacity: 1; transform: translateY(-2px); } }
@keyframes skill-panel-enter { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 620px) {
  .skill-ai__header > div > span { align-items: flex-start; flex-direction: column; gap: 1px; }
  .skill-candidate-list { grid-template-columns: 1fr; }
  .skill-ai__actions { flex-wrap: wrap; }.skill-ai__actions > span { width: 100%; }
  .skill-ai__primary { flex: 1; }
}
@media (prefers-reduced-motion: reduce) { .skill-panel-enter-active, .skill-panel-leave-active, .skill-ai__modes::before, .skill-ai__modes button { transition: none; } .skill-ai__modes button:active { transform: none; } .skill-ai__body, .skill-ai__loading i { animation: none; } }
</style>
