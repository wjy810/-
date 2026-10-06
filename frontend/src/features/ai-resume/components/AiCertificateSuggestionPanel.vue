<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { errorMessage, isApiClientError } from '@/shared/api/types'
import AppIcon from '@/shared/ui/AppIcon.vue'
import {
  cancelAiResumeResponse,
  generateAiResumeCertificateSuggestion,
  generateAiResumeCredentialRecommendations,
  generateAiResumeHonorSuggestion,
} from '../services/aiResumeApi'
import {
  credentialDescriptionCharacters,
  hasCredentialFacts,
  isCredentialFactsInsufficientReason,
  mergeCredentialNameCandidates,
  normalizeCredentialName,
  type CredentialKind,
} from '../utils/certificateSuggestions'
import type { AiCertificateItemSuggestion, AiCertificateNameCandidate } from '../types'

type PanelStatus = 'idle' | 'loading-names' | 'loading-recommendations' | 'names' | 'loading-details' | 'details' | 'error'

const props = withDefaults(defineProps<{
  conversationId: string
  cardId: string
  currentItems: Record<string, unknown>[]
  kind?: CredentialKind
  careerLibraryEvidenceEnabled?: boolean
  disabledReason?: string
}>(), { kind: 'CERTIFICATE', careerLibraryEvidenceEnabled: false, disabledReason: '' })

const emit = defineEmits<{
  apply: [certificates: AiCertificateItemSuggestion[]]
  quota: [remaining: number]
}>()

const open = ref(false)
const status = ref<PanelStatus>('idle')
const requestId = ref('')
const candidates = ref<AiCertificateNameCandidate[]>([])
const certificates = ref<AiCertificateItemSuggestion[]>([])
const selectedNames = ref<string[]>([])
const recommendationsConfirmed = ref(false)
const detailsConfirmed = ref(false)
const panelError = ref('')
let controller: AbortController | null = null

const noun = computed(() => props.kind === 'CERTIFICATE' ? '证书' : '荣誉')
const sectionLabel = computed(() => props.kind === 'CERTIFICATE' ? '证书与资质' : '荣誉奖项')
const pending = computed(() => ['loading-names', 'loading-recommendations', 'loading-details'].includes(status.value))
const ownedCandidates = computed(() => candidates.value.filter((candidate) => candidate.candidateType === 'OWNED'))
const recommendedCandidates = computed(() => candidates.value.filter((candidate) => candidate.candidateType === 'RECOMMENDED'))
const selectedCandidates = computed(() => candidates.value.filter((candidate) => selectedNames.value.includes(candidate.name)))
const selectedRecommended = computed(() => selectedCandidates.value.filter((candidate) => candidate.candidateType === 'RECOMMENDED'))
const namesBlockedReason = computed(() => !hasCredentialFacts(props.currentItems) && !props.careerLibraryEvidenceEnabled
  ? `请先填写至少一个${noun.value}名称，或开启“使用求职资料库作为证据”。`
  : '')

function toggleCandidate(name: string): void {
  if (selectedNames.value.includes(name)) {
    selectedNames.value = selectedNames.value.filter((item) => item !== name)
  } else if (selectedNames.value.length < 12) {
    selectedNames.value = [...selectedNames.value, name]
  } else {
    panelError.value = `一次最多选择 12 个${noun.value}。`
  }
  recommendationsConfirmed.value = false
  detailsConfirmed.value = false
}

function selectAll(): void {
  selectedNames.value = candidates.value.slice(0, 12).map((item) => item.name)
  recommendationsConfirmed.value = false
  panelError.value = candidates.value.length > 12 ? `已选择前 12 个${noun.value}；可取消后改选其他名称。` : ''
}

async function requestSuggestions(
  phase: 'NAMES' | 'DETAILS',
  selected: string[],
  confirmed: string[],
  signal: AbortSignal,
) {
  if (props.kind === 'HONOR') {
    const response = await generateAiResumeHonorSuggestion(
      props.conversationId, props.cardId, phase, props.currentItems, selected, confirmed,
      requestId.value, signal,
    )
    return { ...response, items: response.honors }
  }
  const response = await generateAiResumeCertificateSuggestion(
    props.conversationId, props.cardId, phase, props.currentItems, selected, confirmed, requestId.value, signal,
  )
  return { ...response, items: response.certificates }
}

async function generateNames(): Promise<void> {
  if (namesBlockedReason.value) { panelError.value = namesBlockedReason.value; return }
  controller?.abort()
  controller = new AbortController()
  requestId.value = crypto.randomUUID()
  status.value = 'loading-names'
  panelError.value = ''
  certificates.value = []
  recommendationsConfirmed.value = false
  detailsConfirmed.value = false
  try {
    const response = await requestSuggestions('NAMES', [], [], controller.signal)
    candidates.value = mergeCredentialNameCandidates(candidates.value, response.candidates, props.kind)
    selectedNames.value = selectedNames.value.filter((name) =>
      candidates.value.some((candidate) => candidate.name === name))
    status.value = 'names'
    emit('quota', response.remainingQuota)
  } catch (reason) {
    if (controller?.signal.aborted) return
    if (isApiClientError(reason) && isCredentialFactsInsufficientReason(reason.reason)) {
      controller = null
      if (!props.disabledReason) {
        await generateRecommendations()
        return
      }
      status.value = 'idle'
      panelError.value = `没有识别到已拥有的${noun.value}；${props.disabledReason}`
      return
    }
    status.value = 'error'
    panelError.value = errorMessage(reason, `AI ${noun.value}名称提取失败，当前内容没有变化`)
  } finally {
    controller = null
  }
}

async function generateRecommendations(): Promise<void> {
  if (props.disabledReason) { panelError.value = props.disabledReason; return }
  controller?.abort()
  controller = new AbortController()
  requestId.value = crypto.randomUUID()
  status.value = 'loading-recommendations'
  panelError.value = ''
  certificates.value = []
  recommendationsConfirmed.value = false
  detailsConfirmed.value = false
  try {
    const response = await generateAiResumeCredentialRecommendations(
      props.conversationId, props.cardId, requestId.value, controller.signal,
    )
    candidates.value = mergeCredentialNameCandidates(candidates.value, response.candidates, props.kind)
    selectedNames.value = selectedNames.value.filter((name) =>
      candidates.value.some((candidate) => candidate.name === name))
    status.value = 'names'
    emit('quota', response.remainingQuota)
  } catch (reason) {
    if (controller?.signal.aborted) return
    status.value = 'error'
    panelError.value = errorMessage(reason, `AI ${noun.value}推荐失败，当前内容没有变化`)
  } finally {
    controller = null
  }
}

async function generateDetails(): Promise<void> {
  if (!selectedNames.value.length) { panelError.value = `请至少选择一个${noun.value}名称。`; return }
  if (selectedRecommended.value.length && !recommendationsConfirmed.value) {
    panelError.value = `请先确认所选 AI 推荐${noun.value}确实已经取得。`
    return
  }
  if (props.disabledReason) {
    panelError.value = props.disabledReason
    return
  }
  controller?.abort()
  const aiCandidates = selectedCandidates.value
  controller = new AbortController()
  requestId.value = crypto.randomUUID()
  status.value = 'loading-details'
  panelError.value = ''
  try {
    const response = await requestSuggestions(
      'DETAILS', aiCandidates.map((candidate) => candidate.name),
      selectedRecommended.value.map((candidate) => candidate.name), controller.signal,
    )
    certificates.value = response.items
    emit('quota', response.remainingQuota)
    detailsConfirmed.value = false
    status.value = 'details'
  } catch (reason) {
    if (controller?.signal.aborted) return
    status.value = 'error'
    panelError.value = errorMessage(reason, `AI ${noun.value}补充说明生成失败，当前内容没有变化`)
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

function applyCredentials(): void {
  if (!detailsConfirmed.value || !certificates.value.length) return
  const incomplete = certificates.value.some((certificate) => !certificate.issuer.trim()
    || credentialDescriptionCharacters(certificate.description) < 60)
  if (incomplete) {
    panelError.value = `${sectionLabel.value}必须包含机构和至少 60 个有效字符的补充说明。`
    return
  }
  emit('apply', certificates.value)
  resetPanel()
  open.value = false
}

function resetPanel(): void {
  controller?.abort()
  status.value = 'idle'
  candidates.value = []
  certificates.value = []
  selectedNames.value = []
  recommendationsConfirmed.value = false
  detailsConfirmed.value = false
  panelError.value = ''
}

function sourceTitle(candidate: AiCertificateNameCandidate): string {
  return candidate.sourceRefs.map((source) => `${source.label}：${source.excerpt}`).join('\n')
}

function mergeImpact(certificate: AiCertificateItemSuggestion): string {
  const existing = props.currentItems.find((item) =>
    normalizeCredentialName(String(item.name ?? ''), props.kind) === normalizeCredentialName(certificate.name, props.kind))
  if (!existing) return `将新增为一条${noun.value}记录`
  const filled = ['issuer', 'date'].filter((key) => !String(existing[key] ?? '').trim() && String(certificate[key as 'issuer' | 'date'] ?? '').trim())
  return filled.length
    ? `合并同名${noun.value}，仅补充缺失的${filled.map((key) => key === 'issuer' ? '机构' : '时间').join('和')}`
    : `合并同名${noun.value}，保留原有机构、时间和描述`
}

watch(() => props.cardId, () => {
  resetPanel()
  open.value = false
})
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section class="certificate-ai" :class="{ 'is-open': open }">
    <header class="certificate-ai__header">
      <div>
        <AppIcon :name="kind === 'CERTIFICATE' ? 'award' : 'star'" :size="15" />
        <span><strong>AI {{ sectionLabel }}填充</strong><small>提取已拥有，也可推荐 5–8 个相关选项</small></span>
      </div>
      <button
        type="button" class="certificate-ai__toggle"
        :title="open ? `收起${noun}填充` : `提取或推荐${noun}`"
        @click="open = !open"
      ><AppIcon :name="open ? 'chevron-up' : 'sparkles'" :size="13" />{{ open ? '收起' : 'AI 填充' }}</button>
    </header>

    <div v-if="open" class="certificate-ai__body">
      <div class="certificate-ai__scope">
        <AppIcon :name="careerLibraryEvidenceEnabled ? 'check-circle' : 'folder'" :size="13" />
        <span v-if="careerLibraryEvidenceEnabled">已包含求职资料库中已确认、未归档的{{ noun }}记录</span>
        <span v-else>当前仅提取简历{{ noun }}；开启“使用求职资料库作为证据”后可包含资料库记录</span>
      </div>

      <div v-if="status === 'idle'" class="certificate-ai__start">
        <p>可以梳理已经填写或资料库中的名称，也可以根据目标岗位、教育、经历、项目和技能推荐多个选项。</p>
        <div class="certificate-ai__start-actions">
          <button type="button" :disabled="Boolean(namesBlockedReason)" :title="namesBlockedReason" @click="generateNames"><AppIcon name="search" :size="13" />提取已拥有</button>
          <button type="button" class="certificate-ai__primary" :disabled="Boolean(disabledReason)" :title="disabledReason" @click="generateRecommendations"><AppIcon name="sparkles" :size="13" />AI 推荐多个</button>
        </div>
        <small v-if="namesBlockedReason">{{ namesBlockedReason }}仍可使用 AI 推荐。</small>
      </div>

      <div v-else-if="pending" class="certificate-ai__loading" role="status">
        <span aria-hidden="true"><i /><i /><i /></span>
        <p>{{ status === 'loading-names'
          ? `正在核对${noun}名称和事实来源`
          : status === 'loading-recommendations'
            ? `正在根据前面的简历内容推荐多个${noun}选项`
            : `正在生成所选${noun}的补充说明` }}</p>
        <button type="button" @click="cancelGeneration">取消</button>
      </div>

      <div v-else-if="status === 'names'" class="certificate-ai__names">
        <div class="certificate-ai__names-head">
          <span>共 {{ candidates.length }} 个选项，可多选</span>
          <button type="button" @click="selectAll">选择{{ candidates.length > 12 ? '前 12 个' : '全部' }}</button>
        </div>
        <div class="certificate-candidate-scroll">
          <section v-if="ownedCandidates.length" class="certificate-candidate-group">
            <header><strong>已拥有</strong><span>有当前草稿或资料库事实来源</span></header>
            <div class="certificate-candidate-list">
              <label
                v-for="candidate in ownedCandidates" :key="candidate.name" class="certificate-candidate"
                :class="{ 'is-selected': selectedNames.includes(candidate.name) }"
              >
                <input type="checkbox" :checked="selectedNames.includes(candidate.name)" @change="toggleCandidate(candidate.name)" />
                <span class="certificate-candidate__main"><strong>{{ candidate.name }}</strong><em>{{ candidate.reason }}</em></span>
                <span class="certificate-candidate__source" :title="sourceTitle(candidate)">
                  <AppIcon name="link" :size="10" />{{ candidate.sourceRefs[0]?.label || `当前${noun}草稿` }}
                </span>
              </label>
            </div>
          </section>
          <section v-if="recommendedCandidates.length" class="certificate-candidate-group is-recommended">
            <header><strong>AI 推荐 · 需确认已取得</strong><span>只推荐相关名称，不代表你已经拥有</span></header>
            <div class="certificate-candidate-list">
              <label
                v-for="candidate in recommendedCandidates" :key="candidate.name" class="certificate-candidate"
                :class="{ 'is-selected': selectedNames.includes(candidate.name) }"
              >
                <input type="checkbox" :checked="selectedNames.includes(candidate.name)" @change="toggleCandidate(candidate.name)" />
                <span class="certificate-candidate__main"><strong>{{ candidate.name }}</strong><em>{{ candidate.reason }}</em></span>
                <span class="certificate-candidate__source" :title="sourceTitle(candidate)">
                  <AppIcon name="sparkles" :size="10" />依据：{{ candidate.sourceRefs.map((source) => source.label).join('、') }}
                </span>
              </label>
            </div>
          </section>
        </div>
        <label v-if="selectedRecommended.length" class="certificate-ai__recommendation-confirmation">
          <input v-model="recommendationsConfirmed" type="checkbox" />
          <span>我确认所选 {{ selectedRecommended.length }} 个 AI 推荐{{ noun }}确实已经取得；推荐本身不是取得证明</span>
        </label>
        <footer class="certificate-ai__actions">
          <span>已选 {{ selectedNames.length }} / 12</span>
          <button type="button" :disabled="Boolean(namesBlockedReason)" :title="namesBlockedReason" @click="generateNames"><AppIcon name="search" :size="12" />提取已拥有</button>
          <button type="button" :disabled="Boolean(disabledReason)" :title="disabledReason" @click="generateRecommendations"><AppIcon name="refresh" :size="12" />重新推荐</button>
          <button
            type="button" class="certificate-ai__primary"
            :disabled="!selectedNames.length || (selectedRecommended.length > 0 && !recommendationsConfirmed) || Boolean(disabledReason)"
            :title="selectedRecommended.length && !recommendationsConfirmed ? `请先确认推荐${noun}确实已经取得` : disabledReason"
            @click="generateDetails"
          >AI 完善详情</button>
        </footer>
        <p v-if="disabledReason && selectedNames.length" class="certificate-ai__details-unavailable"><AppIcon name="alert-circle" :size="12" />{{ disabledReason }}</p>
      </div>

      <div v-else-if="status === 'details'" class="certificate-ai__details">
        <article v-for="certificate in certificates" :key="certificate.name">
          <header><strong>{{ certificate.name }}</strong><span>{{ certificate.date || '时间待补充' }}</span></header>
          <p class="certificate-ai__issuer">{{ certificate.issuer || '发证机构待补充' }}</p>
          <small><AppIcon name="link" :size="11" />{{ mergeImpact(certificate) }}</small>
          <p v-if="certificate.description" class="certificate-ai__description">{{ certificate.description }}</p>
          <p v-if="certificate.description" class="certificate-ai__count" :class="{ 'is-valid': credentialDescriptionCharacters(certificate.description) >= 80 }">{{ credentialDescriptionCharacters(certificate.description) }} 个有效字符 · AI 要求 80–160 字</p>
          <p v-else class="certificate-ai__count">详情不完整，不能加入草稿</p>
          <div class="certificate-ai__sources"><span v-for="source in certificate.sourceRefs" :key="source.key" :title="source.excerpt">{{ source.label }}</span></div>
          <ul><li v-for="item in certificate.verificationItems" :key="item">{{ item }}</li></ul>
        </article>
        <label class="certificate-ai__confirmation">
          <input v-model="detailsConfirmed" type="checkbox" />
          <span>我确认所选{{ noun }}均已取得；机构和描述与事实一致，日期未知时可以留空</span>
        </label>
        <footer class="certificate-ai__actions">
          <button type="button" @click="status = 'names'">返回名称选择</button>
          <button type="button" class="certificate-ai__primary" :disabled="!detailsConfirmed" @click="applyCredentials"><AppIcon name="check" :size="13" />加入{{ noun }}草稿</button>
        </footer>
      </div>

      <div v-if="status === 'error' || panelError" class="certificate-ai__error" role="alert">
        <AppIcon name="alert-circle" :size="14" /><span>{{ panelError }}</span>
        <button type="button" @click="status = candidates.length ? 'names' : 'idle'; panelError = ''">关闭</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.certificate-ai { min-width:0; margin:4px 0 10px; overflow:hidden; border:1px solid #d6dfeb; border-radius:6px; background:#fbfcfe; }
.certificate-ai__header { min-height:44px; padding:7px 9px; display:flex; align-items:center; justify-content:space-between; gap:12px; }
.certificate-ai__header > div { min-width:0; display:flex; align-items:center; gap:7px; color:#2c64ae; }
.certificate-ai__header > div > span { min-width:0; display:flex; align-items:baseline; gap:7px; }
.certificate-ai__header strong { color:var(--text); font-size:12px; }.certificate-ai__header small { color:var(--text-3); font-size:11px; font-weight:500; }
.certificate-ai__toggle,.certificate-ai__primary { min-height:30px; padding:0 9px; display:inline-flex; align-items:center; justify-content:center; gap:5px; border:1px solid #9ab7e4; border-radius:4px; background:#eef5ff; color:#245fae; font-size:11px; font-weight:700; }
.certificate-ai__toggle:hover:not(:disabled),.certificate-ai__primary:hover:not(:disabled) { background:#e2edff; border-color:#7299d4; }.certificate-ai button:disabled { cursor:not-allowed; opacity:.55; }
.certificate-ai__body { padding:10px; display:grid; gap:10px; border-top:1px solid #e1e7ef; background:#fff; animation:certificate-panel-enter .22s ease-out both; }
.certificate-ai__scope { padding:7px 8px; display:flex; align-items:flex-start; gap:6px; background:#f5f8fc; color:var(--text-2); font-size:11px; line-height:1.45; }
.certificate-ai__start { display:grid; grid-template-columns:minmax(0,1fr) auto; align-items:center; gap:8px 10px; }.certificate-ai__start p { color:var(--text-3); font-size:11px; line-height:1.5; }.certificate-ai__start>small{grid-column:1/-1;color:#8a5e22;font-size:10px}.certificate-ai__start-actions{display:flex;align-items:center;gap:6px}.certificate-ai__start-actions>button{min-height:30px;padding:0 9px;display:inline-flex;align-items:center;justify-content:center;gap:5px;border:1px solid #d3dae4;border-radius:4px;background:#fff;color:var(--text-2);font-size:11px;font-weight:700}
.certificate-ai__loading { min-height:42px; padding:8px 10px; display:flex; align-items:center; gap:8px; background:#f5f8fc; color:var(--text-2); font-size:11px; }
.certificate-ai__loading > span { width:24px; display:flex; gap:3px; }.certificate-ai__loading i { width:4px; height:4px; border-radius:50%; background:#4e7fc8; animation:certificate-dot 1.1s ease-in-out infinite; }
.certificate-ai__loading i:nth-child(2){animation-delay:.13s}.certificate-ai__loading i:nth-child(3){animation-delay:.26s}.certificate-ai__loading p{flex:1}.certificate-ai__loading button{border:0;background:transparent;color:var(--primary);font-size:11px}
.certificate-ai__names,.certificate-ai__details { min-width:0; display:grid; gap:8px; }.certificate-ai__names-head { display:flex; align-items:center; justify-content:space-between; color:var(--text-3); font-size:11px; }.certificate-ai__names-head button { border:0; background:transparent; color:var(--primary); font-size:11px; }
.certificate-candidate-scroll{max-height:360px;display:grid;gap:10px;overflow:auto;overscroll-behavior:contain;scrollbar-width:none}.certificate-candidate-scroll::-webkit-scrollbar{display:none}.certificate-candidate-group{display:grid;gap:6px}.certificate-candidate-group>header{display:flex;align-items:baseline;justify-content:space-between;gap:8px}.certificate-candidate-group>header strong{color:#285f3f;font-size:11px}.certificate-candidate-group>header span{color:var(--text-3);font-size:10px;text-align:right}.certificate-candidate-group.is-recommended>header strong{color:#7a5520}.certificate-candidate-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:6px; }
.certificate-candidate { min-width:0; min-height:62px; padding:8px; display:grid; grid-template-columns:15px minmax(0,1fr); align-items:start; gap:7px; border:1px solid #d8dfe8; border-radius:5px; background:#fff; cursor:pointer; }.certificate-candidate:hover{border-color:#9db7de}.certificate-candidate.is-selected{border-color:#5c8ed5;background:#f6f9ff}.certificate-candidate>input{width:14px;height:14px;margin:2px 0 0;accent-color:var(--primary)}
.certificate-candidate__main { min-width:0; display:grid; gap:3px; }.certificate-candidate__main strong { color:var(--text); font-size:12px; overflow-wrap:anywhere; }.certificate-candidate__main em { color:var(--text-2); font-size:11px; font-style:normal; line-height:1.4; }
.certificate-candidate__source { grid-column:2; min-width:0; display:flex; align-items:center; gap:3px; color:#3d659a; font-size:10px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.certificate-ai__actions { display:flex; align-items:center; justify-content:flex-end; gap:7px; }.certificate-ai__actions>span{margin-right:auto;color:var(--text-3);font-size:11px}.certificate-ai__actions>button:not(.certificate-ai__primary){min-height:30px;padding:0 8px;border:1px solid #d3dae4;border-radius:4px;background:#fff;color:var(--text-2);font-size:11px}
.certificate-ai__details article { padding:9px 10px; display:grid; gap:6px; border-left:2px solid #5f8fd3; background:#f7faff; }.certificate-ai__details article header{display:flex;align-items:baseline;justify-content:space-between;gap:8px}.certificate-ai__details article header strong{color:var(--text);font-size:12px}.certificate-ai__details article header span{color:#376cae;font-size:11px}.certificate-ai__issuer{color:var(--text-2);font-size:11px}.certificate-ai__details article>small{display:inline-flex;align-items:center;gap:4px;color:#376cae;font-size:10px;font-weight:600}.certificate-ai__description{color:var(--text-2);font-size:11px;line-height:1.6;white-space:pre-line}
.certificate-ai__count{margin:0;color:#9a5b24;font-size:10px}.certificate-ai__count.is-valid{color:#2f7750}
.certificate-ai__sources{display:flex;flex-wrap:wrap;gap:4px}.certificate-ai__sources span{padding:2px 4px;border-radius:3px;background:#e9f1ff;color:#3d659a;font-size:10px}.certificate-ai__details article ul{margin:0;padding-left:16px;color:#75551e;font-size:10px;line-height:1.5}
.certificate-ai__confirmation{padding:7px 8px;display:flex;align-items:flex-start;gap:7px;border-left:2px solid #ce9131;background:#fffaf0;color:#624716;font-size:11px;line-height:1.5;cursor:pointer}.certificate-ai__confirmation input{width:14px;height:14px;flex:none;margin:0;accent-color:var(--primary)}
.certificate-ai__recommendation-confirmation{padding:7px 8px;display:flex;align-items:flex-start;gap:7px;border-left:2px solid #ce9131;background:#fffaf0;color:#624716;font-size:11px;line-height:1.5;cursor:pointer}.certificate-ai__recommendation-confirmation input{width:14px;height:14px;flex:none;margin:1px 0 0;accent-color:var(--primary)}
.certificate-ai__details-unavailable{margin:0;padding:6px 8px;display:flex;align-items:center;gap:5px;background:#fff8eb;color:#79551a;font-size:11px}
.certificate-ai__error{min-height:34px;padding:7px 8px;display:flex;align-items:center;gap:7px;border-left:2px solid #d36a6a;background:#fff5f5;color:#9a3030;font-size:11px}.certificate-ai__error span{flex:1}.certificate-ai__error button{border:0;background:transparent;color:#9a3030;font-size:11px}
@keyframes certificate-dot{0%,70%,100%{opacity:.3;transform:translateY(0)}35%{opacity:1;transform:translateY(-2px)}}@keyframes certificate-panel-enter{from{opacity:0;transform:translateY(-4px)}to{opacity:1;transform:translateY(0)}}
@media(max-width:620px){.certificate-ai__header>div>span{align-items:flex-start;flex-direction:column;gap:1px}.certificate-ai__start{grid-template-columns:1fr}.certificate-ai__start-actions{width:100%}.certificate-ai__start-actions>button{flex:1}.certificate-candidate-list{grid-template-columns:1fr}.certificate-ai__actions{flex-wrap:wrap}.certificate-ai__actions>span{width:100%}.certificate-ai__primary{flex:1}}
@media(prefers-reduced-motion:reduce){.certificate-ai__body,.certificate-ai__loading i{animation:none}}
</style>
