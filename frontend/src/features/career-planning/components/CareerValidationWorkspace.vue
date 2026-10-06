<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Check, ChevronRight, CircleAlert, ClipboardCheck, Code2, FileCheck2, FlaskConical,
  LoaderCircle, Play, RefreshCw, Route, ShieldCheck, Sparkles, UploadCloud,
} from 'lucide-vue-next'
import AppSelect from '@/shared/ui/AppSelect.vue'
import { errorMessage } from '@/shared/api/types'
import { fetchTask, retryTask, type TaskView } from '@/shared/api/task'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import {
  confirmCareerAbilityValidation, fetchCareerAbilityValidations,
  startCareerAbilityValidation, startCareerValidationBatch,
} from '../services/careerPlanningApi'
import type { CanvasNode, CareerAbilityValidation, CareerCanvas, CareerLearningPlan } from '../types'

const props = defineProps<{
  sessionId: string
  canvas: CareerCanvas
  plan?: CareerLearningPlan | null
  validations: CareerAbilityValidation[]
  initialNodeIds?: string[]
  initialNodeId?: string
}>()
const emit = defineEmits<{
  updated: [validation: CareerAbilityValidation, refreshSession: boolean]
  batchUpdated: [validations: CareerAbilityValidation[], refreshSession: boolean]
  error: [message: string]
  notice: [message: string]
  canvas: []
}>()

type ValidationDraft = { submission: string; evidenceIds: string[] }
type StoredBatchTask = { taskId: string; requestId: string; nodeIds: string[] }

const selectedNodeIds = ref<string[]>([])
const activeNodeId = ref('')
const method = ref('PROJECT_CHECK')
const drafts = reactive<Record<string, ValidationDraft>>({})
const pending = ref('')
const selectedValidationId = ref('')
const batchTask = ref<TaskView | null>(null)
const batchResults = ref<CareerAbilityValidation[]>([])
const singleRequestId = ref('')
let batchController: AbortController | null = null

const nodes = computed(() => props.canvas.nodes.filter(node => !['CAREER', 'DOMAIN', 'EVIDENCE'].includes(node.type)))
const nodeOptions = computed(() => nodes.value.map(node => ({ value: node.logicalNodeId, label: `${node.title} · ${statusLabel(node.status)}` })))
const selectedNodes = computed(() => selectedNodeIds.value.map(id => nodes.value.find(node => node.logicalNodeId === id)).filter((node): node is CanvasNode => Boolean(node)))
const isBatch = computed(() => selectedNodes.value.length > 1)
const nodeId = computed({
  get: () => activeNodeId.value,
  set: (value: string) => { activeNodeId.value = value; if (!isBatch.value) selectedNodeIds.value = value ? [value] : [] },
})
const selectedNode = computed(() => nodes.value.find(node => node.logicalNodeId === activeNodeId.value) ?? null)
const availableEvidence = computed(() => evidenceFor(activeNodeId.value))
const submission = computed({ get: () => draftFor(activeNodeId.value).submission, set: value => { draftFor(activeNodeId.value).submission = value } })
const evidenceIds = computed(() => draftFor(activeNodeId.value).evidenceIds)
const allValidations = computed(() => {
  const values = [...batchResults.value, ...props.validations]
  return values.filter((value, index) => values.findIndex(item => item.id === value.id) === index)
})
const currentValidation = computed(() => allValidations.value.find(value => value.id === selectedValidationId.value)
  ?? allValidations.value.find(value => value.nodeId === activeNodeId.value && value.status === 'EVALUATED')
  ?? null)
const completedStep = computed(() => currentValidation.value
  ? currentValidation.value.status === 'CONFIRMED' || currentValidation.value.status === 'REJECTED' ? 5 : 4
  : batchTask.value ? batchTask.value.status === 'SUCCEEDED' ? 4 : 3
  : selectedNodes.value.some(node => materialReady(node.logicalNodeId)) ? 3 : method.value ? 2 : 1)
const taskRunning = computed(() => batchTask.value?.status === 'PENDING' || batchTask.value?.status === 'RUNNING')
const taskFailed = computed(() => batchTask.value?.status === 'FAILED' || batchTask.value?.status === 'CANCELLED')
const taskProgress = computed(() => Math.max(0, Math.min(100, batchTask.value?.progressPercent ?? 0)))

watch([() => props.initialNodeIds, () => props.initialNodeId, nodes], () => applyInitialSelection(), { immediate: true, deep: true })
watch(activeNodeId, () => {
  const latest = allValidations.value.find(value => value.nodeId === activeNodeId.value)
  selectedValidationId.value = latest?.id ?? ''
})
watch(() => props.sessionId, () => {
  batchController?.abort()
  batchTask.value = null
  batchResults.value = []
  selectedNodeIds.value = []
  activeNodeId.value = ''
  applyInitialSelection()
  void restoreBatchTask()
})

const methods = [
  { value: 'QUIZ', label: '专项测试', copy: '通过结构化题目检查核心概念', icon: ClipboardCheck },
  { value: 'PROJECT_CHECK', label: '实践任务', copy: '用真实项目成果验证应用能力', icon: FlaskConical },
  { value: 'SCENARIO', label: '场景验证', copy: '在岗位情境中说明判断、行动与结果', icon: Route },
  { value: 'CODE_REVIEW', label: '代码评审', copy: '检查实现质量、边界与可维护性', icon: Code2 },
  { value: 'EVIDENCE_REVIEW', label: '证据复核', copy: '综合已提交证据进行判断', icon: FileCheck2 },
]

function statusLabel(value: string): string {
  return ({ NOT_STARTED: '未开始', PLANNED: '计划中', LEARNING: '学习中', PENDING_VALIDATION: '待验证', MASTERED: '已掌握', PAUSED: '已暂停' } as Record<string,string>)[value] ?? value
}
function resultLabel(value?: string | null): string {
  return ({ PASSED: '评估通过', NEEDS_WORK: '需要补强', INSUFFICIENT: '证据不足' } as Record<string,string>)[value ?? ''] ?? '等待评估'
}
function feedbackList(validation: CareerAbilityValidation | null, key: string): string[] {
  const value = validation?.feedback?.[key]
  return Array.isArray(value) ? value.filter(item => typeof item === 'string') : []
}
function overallScore(validation: CareerAbilityValidation | null): number | null {
  const value = validation?.score?.overall
  return typeof value === 'number' ? value : null
}
function applyInitialSelection(): void {
  const requested = props.initialNodeIds?.length ? props.initialNodeIds : props.initialNodeId ? [props.initialNodeId] : []
  const legal = new Set(nodes.value.map(node => node.logicalNodeId))
  const resolved = Array.from(new Set(requested.filter(id => legal.has(id)))).slice(0, 8)
  if (resolved.length) {
    if (resolved.join(',') !== selectedNodeIds.value.join(',')) selectedNodeIds.value = resolved
    if (!resolved.includes(activeNodeId.value)) activeNodeId.value = resolved[0]
    resolved.forEach(draftFor)
    return
  }
  if (!selectedNodeIds.value.length && nodes.value.length) {
    const fallback = nodes.value.find(item => item.status === 'PENDING_VALIDATION') ?? nodes.value[0]
    selectedNodeIds.value = [fallback.logicalNodeId]
    activeNodeId.value = fallback.logicalNodeId
    draftFor(fallback.logicalNodeId)
  }
}
function draftFor(id: string): ValidationDraft {
  if (!id) return { submission: '', evidenceIds: [] }
  drafts[id] ??= { submission: '', evidenceIds: [] }
  return drafts[id]
}
function evidenceFor(id: string) {
  return props.plan?.evidences.filter(item => item.nodeId === id) ?? []
}
function materialReady(id: string): boolean {
  const draft = draftFor(id)
  return Boolean(draft.submission.trim() || draft.evidenceIds.length)
}
function toggleEvidence(id: string): void {
  const draft = draftFor(activeNodeId.value)
  draft.evidenceIds = draft.evidenceIds.includes(id) ? draft.evidenceIds.filter(value => value !== id) : [...draft.evidenceIds, id]
}
function selectQueueNode(id: string): void {
  activeNodeId.value = id
  const latest = allValidations.value.find(value => value.nodeId === id)
  selectedValidationId.value = latest?.id ?? ''
}
function taskStorageKey(): string {
  return `career-planning:validation-batch:${props.sessionId}`
}
function saveTask(value: StoredBatchTask): void {
  window.sessionStorage.setItem(taskStorageKey(), JSON.stringify(value))
}
function clearTask(): void {
  window.sessionStorage.removeItem(taskStorageKey())
}

async function evaluate(): Promise<void> {
  if (!selectedNodes.value.length) { emit('error', '请先选择需要验证的能力节点。'); return }
  if (isBatch.value) { await evaluateBatch(); return }
  if (!materialReady(activeNodeId.value)) { emit('error', '请填写验证说明或至少关联一项证据。'); return }
  pending.value = 'evaluate'
  try {
    singleRequestId.value ||= crypto.randomUUID()
    const value = await startCareerAbilityValidation(props.sessionId, {
      requestId: singleRequestId.value, nodeId: activeNodeId.value, method: method.value,
      evidenceIds: evidenceIds.value, submission: { summary: submission.value.trim() },
      expectedCanvasVersion: props.canvas.version,
    })
    singleRequestId.value = ''
    selectedValidationId.value = value.id
    emit('updated', value, true)
    emit('notice', 'AI 评估已完成，请核对结果并由你确认最终状态。')
  } catch (reason) { emit('error', errorMessage(reason, '能力验证失败')) }
  finally { pending.value = '' }
}

async function evaluateBatch(): Promise<void> {
  if (selectedNodes.value.length < 2 || selectedNodes.value.length > 8) {
    emit('error', '批量验证需要选择 2–8 个技能、知识或任务节点。')
    return
  }
  const missing = selectedNodes.value.filter(node => !materialReady(node.logicalNodeId))
  if (missing.length) {
    activeNodeId.value = missing[0].logicalNodeId
    emit('error', `请先为“${missing[0].title}”填写验证说明或选择该节点的证据。`)
    return
  }
  pending.value = 'evaluate-batch'
  batchResults.value = []
  try {
    const stored: StoredBatchTask = {
      taskId: '', requestId: crypto.randomUUID(), nodeIds: selectedNodes.value.map(node => node.logicalNodeId),
    }
    const task = await startCareerValidationBatch(props.sessionId, {
      requestId: stored.requestId,
      expectedCanvasVersion: props.canvas.version,
      method: method.value,
      items: selectedNodes.value.map(node => ({
        nodeId: node.logicalNodeId,
        evidenceIds: [...draftFor(node.logicalNodeId).evidenceIds],
        submission: { summary: draftFor(node.logicalNodeId).submission.trim() },
      })),
    })
    stored.taskId = task.id
    batchTask.value = task
    saveTask(stored)
    await trackBatchTask(task.id)
  } catch (reason) {
    if (!isAbortError(reason)) emit('error', errorMessage(reason, '批量能力验证启动失败'))
  } finally { pending.value = '' }
}

async function trackBatchTask(taskId: string): Promise<void> {
  batchController?.abort()
  batchController = new AbortController()
  try {
    const result = await pollTask(taskId, value => { batchTask.value = value }, batchController.signal)
    batchTask.value = result
    if (result.status === 'SUCCEEDED') {
      if (!result.resultVersion) throw new Error('批量验证已完成，但未返回批次标识。')
      const values = await fetchCareerAbilityValidations(props.sessionId, result.resultVersion)
      batchResults.value = values
      clearTask()
      if (values[0]) {
        activeNodeId.value = values[0].nodeId
        selectedValidationId.value = values[0].id
      }
      emit('batchUpdated', values, true)
      emit('notice', `AI 已完成 ${values.length} 个节点的辅助评估，请逐条确认或拒绝。`)
    }
  } catch (reason) {
    if (!isAbortError(reason)) emit('error', errorMessage(reason, '批量能力验证状态读取失败'))
  }
}

async function restoreBatchTask(): Promise<void> {
  let stored: StoredBatchTask | null = null
  try { stored = JSON.parse(window.sessionStorage.getItem(taskStorageKey()) ?? 'null') as StoredBatchTask | null }
  catch { clearTask() }
  if (!stored?.taskId || !stored.nodeIds?.length) return
  const legal = new Set(nodes.value.map(node => node.logicalNodeId))
  const restoredIds = stored.nodeIds.filter(id => legal.has(id)).slice(0, 8)
  if (restoredIds.length) {
    selectedNodeIds.value = restoredIds
    activeNodeId.value = restoredIds[0]
    restoredIds.forEach(draftFor)
  }
  try {
    const task = await fetchTask(stored.taskId)
    batchTask.value = task
    if (task.status === 'SUCCEEDED') {
      if (!task.resultVersion) throw new Error('批量验证结果缺少批次标识。')
      const values = await fetchCareerAbilityValidations(props.sessionId, task.resultVersion)
      batchResults.value = values
      clearTask()
      emit('batchUpdated', values, true)
    } else if (task.status === 'PENDING' || task.status === 'RUNNING') {
      await trackBatchTask(task.id)
    }
  } catch (reason) {
    if (!isAbortError(reason)) emit('error', errorMessage(reason, '恢复批量验证任务失败'))
  }
}

async function retryBatchTask(): Promise<void> {
  if (!batchTask.value) return
  pending.value = 'retry-batch'
  try {
    const value = await retryTask(batchTask.value.id)
    batchTask.value = value
    let stored: StoredBatchTask | null = null
    try { stored = JSON.parse(window.sessionStorage.getItem(taskStorageKey()) ?? 'null') as StoredBatchTask | null }
    catch { stored = null }
    saveTask({
      taskId: value.id,
      requestId: stored?.requestId ?? crypto.randomUUID(),
      nodeIds: stored?.nodeIds?.length ? stored.nodeIds : selectedNodes.value.map(node => node.logicalNodeId),
    })
    await trackBatchTask(value.id)
  } catch (reason) { emit('error', errorMessage(reason, '批量验证任务重试失败')) }
  finally { pending.value = '' }
}

async function confirm(validation: CareerAbilityValidation, accepted: boolean): Promise<void> {
  pending.value = `${accepted ? 'confirm' : 'reject'}:${validation.id}`
  try {
    const value = await confirmCareerAbilityValidation(props.sessionId, validation.id, accepted, props.canvas.version)
    batchResults.value = batchResults.value.map(item => item.id === value.id ? value : item)
    selectedValidationId.value = value.id
    emit('updated', value, true)
    emit('notice', accepted && value.result === 'PASSED' ? '你已确认验证结果，能力节点已标记为掌握。' : '验证结果已记录，节点不会被自动标记为掌握。')
  } catch (reason) { emit('error', errorMessage(reason, '验证结果确认失败')) }
  finally { pending.value = '' }
}

onMounted(() => { void restoreBatchTask() })
onBeforeUnmount(() => batchController?.abort())
</script>

<template>
  <section class="validation-workspace">
    <header class="validation-title"><div><button type="button" @click="emit('canvas')">能力画布</button><ChevronRight :size="15" /><span>能力验证</span><ChevronRight :size="15" /><strong>{{ isBatch ? `${selectedNodes.length} 个节点` : selectedNode?.title || '选择能力节点' }}</strong></div><h1>{{ isBatch ? `批量验证 ${selectedNodes.length} 个能力节点` : `验证 ${selectedNode?.title || '能力掌握情况'}` }} <em v-if="!isBatch && selectedNode">{{ statusLabel(selectedNode.status) }}</em></h1></header>
    <div class="validation-step-scroll" tabindex="0" aria-label="能力验证进度"><ol class="validation-steps"><i class="validation-step-progress" aria-hidden="true" :style="{ width: `${Math.max(0, completedStep - 1) * 25}%` }" /><li v-for="(label,index) in ['选择验证方式','完成验证','提交证据','AI 辅助评估','用户确认']" :key="label" :class="{ active: completedStep >= index + 1, current: completedStep === index + 1 }"><span>{{ completedStep > index + 1 ? '✓' : index + 1 }}</span><strong>{{ label }}</strong></li></ol></div>

    <div class="validation-layout">
      <main>
        <section v-if="!isBatch" class="node-select"><label>验证节点<AppSelect v-model="nodeId" :options="nodeOptions" aria-label="验证能力节点" /></label><p><CircleAlert :size="15" />只能验证技能、知识或任务节点；职业根节点和能力域不能直接标记为掌握。</p></section>
        <section v-else class="batch-queue"><header><div><h2>批量材料队列</h2><p>统一验证方式，每个节点分别填写说明和选择证据。</p></div><strong>{{ selectedNodes.filter(node => materialReady(node.logicalNodeId)).length }} / {{ selectedNodes.length }} 已就绪</strong></header><div><button v-for="(node,index) in selectedNodes" :key="node.logicalNodeId" type="button" :class="{ active: activeNodeId === node.logicalNodeId, ready: materialReady(node.logicalNodeId) }" @click="selectQueueNode(node.logicalNodeId)"><span>{{ materialReady(node.logicalNodeId) ? '✓' : index + 1 }}</span><strong>{{ node.title }}</strong><small>{{ materialReady(node.logicalNodeId) ? '材料已就绪' : '待填写材料' }}</small></button></div></section>
        <section><h2>选择验证方式</h2><div class="method-grid"><button v-for="item in methods" :key="item.value" type="button" :aria-pressed="method === item.value" :class="{ active: method === item.value }" @click="method = item.value"><component :is="item.icon" :size="21" /><p><strong>{{ item.label }}</strong><small>{{ item.copy }}</small></p><Transition name="validation-check"><Check v-if="method === item.value" :size="16" /></Transition></button></div></section>
        <section><h2>{{ isBatch ? '当前节点材料' : '任务详情' }}</h2><div class="validation-task"><span><Play :size="20" /></span><div><strong>{{ selectedNode?.title || '尚未选择节点' }}</strong><p>{{ selectedNode?.detail?.summary || '根据节点掌握标准提交可复核的实践说明。' }}</p></div></div><label class="submission-field">验证说明<textarea v-model="submission" rows="5" maxlength="4000" placeholder="说明你完成了什么、如何验证，以及有哪些结果可以复核"></textarea><small>{{ submission.length }} / 4000</small></label></section>
        <p class="important-rule"><ShieldCheck :size="22" /><span><strong>重要规则</strong>{{ isBatch ? '整批只消耗 1 次 AI 额度，但结果仍需逐条确认。' : 'AI 只给出辅助评估；最终状态需要证据与用户确认。' }} 任何 AI 结果都不会自动把节点标记为已掌握。</span></p>
      </main>

      <aside>
        <section class="criteria"><h2>验收标准</h2><ol><li v-for="(value,index) in (Array.isArray(selectedNode?.detail?.masteryCriteria) ? selectedNode?.detail?.masteryCriteria : ['能够独立说明核心概念','能够完成对应实践任务','结果可以通过证据复核'])" :key="String(value)"><span>{{ index + 1 }}</span>{{ value }}</li></ol></section>
        <section class="evidence-check"><h2>证据清单</h2><button v-for="item in availableEvidence" :key="item.id" type="button" :class="{ active: evidenceIds.includes(item.id) }" @click="toggleEvidence(item.id)"><UploadCloud :size="18" /><span><strong>{{ item.title }}</strong><small>{{ item.verificationStatus }}</small></span><Check v-if="evidenceIds.includes(item.id)" :size="16" /></button><p v-if="!availableEvidence.length">当前节点还没有计划证据，可先回到学习计划添加说明或文件引用。</p></section>
        <section v-if="batchTask && (taskRunning || taskFailed)" class="batch-task-state" :class="{ failed: taskFailed }"><header><LoaderCircle v-if="taskRunning" class="spin" :size="20" /><CircleAlert v-else :size="20" /><div><strong>{{ taskRunning ? 'AI 正在批量评估' : '批量评估未完成' }}</strong><small>{{ batchTask.checkpointCode || '等待任务检查点' }}</small></div><em>{{ taskProgress }}%</em></header><div class="batch-task-progress"><i :style="{ width: `${taskProgress}%` }" /></div><template v-if="taskFailed"><p>{{ batchTask.failureReason || '任务执行失败，画布没有被自动修改。' }}</p><dl><div><dt>错误码</dt><dd>{{ batchTask.errorCode || 'CAREER_VALIDATION_BATCH_FAILED' }}</dd></div><div><dt>失败阶段</dt><dd>{{ batchTask.checkpointCode || 'UNKNOWN' }}</dd></div></dl><button type="button" :disabled="pending === 'retry-batch'" @click="retryBatchTask"><RefreshCw :class="{ spin: pending === 'retry-batch' }" :size="16" />重试此任务</button></template></section>
        <Transition name="validation-result" mode="out-in"><section v-if="currentValidation && !isBatch" :key="currentValidation.id" class="evaluation-result" :class="`result-${currentValidation.result?.toLowerCase()}`"><header><span><Sparkles :size="20" /></span><div><strong>{{ resultLabel(currentValidation.result) }}</strong><small>AI 辅助评估 · {{ currentValidation.model || 'SYSTEM' }}</small></div><em v-if="overallScore(currentValidation) !== null">{{ overallScore(currentValidation) }}</em></header><p>{{ currentValidation.feedback?.summary || '评估已完成，请核对后确认。' }}</p><ul><li v-for="item in feedbackList(currentValidation,'strengths')" :key="item">{{ item }}</li><li v-for="item in feedbackList(currentValidation,'gaps')" :key="item">{{ item }}</li></ul><footer v-if="currentValidation.status === 'EVALUATED'"><button type="button" :disabled="pending === `reject:${currentValidation.id}`" @click="confirm(currentValidation,false)">拒绝结果</button><button type="button" :disabled="pending === `confirm:${currentValidation.id}`" @click="confirm(currentValidation,true)"><LoaderCircle v-if="pending === `confirm:${currentValidation.id}`" class="spin" :size="16" /><Check v-else :size="16" />确认评估结果</button></footer><p v-else class="confirmed"><Check :size="15" />用户决定已记录</p></section><button v-else-if="!isBatch || !batchResults.length" key="evaluate" class="evaluate-button" type="button" :disabled="pending === 'evaluate' || pending === 'evaluate-batch' || taskRunning" @click="evaluate"><LoaderCircle v-if="pending === 'evaluate' || pending === 'evaluate-batch' || taskRunning" class="spin" :size="17" /><Sparkles v-else :size="17" />{{ isBatch ? `提交 ${selectedNodes.length} 个节点并评估 · 消耗 1 次` : '提交证据并评估' }}</button></Transition>
      </aside>
    </div>

    <section v-if="isBatch && batchResults.length" class="batch-results"><header><div><h2>批量评估结果</h2><p>AI 结果不会自动修改掌握状态，请逐条作出决定。</p></div><strong>{{ batchResults.filter(item => item.status !== 'EVALUATED').length }} / {{ batchResults.length }} 已决定</strong></header><div><article v-for="item in batchResults" :key="item.id" class="evaluation-result" :class="`result-${item.result?.toLowerCase()}`"><header><span><Sparkles :size="20" /></span><div><strong>{{ canvas.nodes.find(node => node.logicalNodeId === item.nodeId)?.title || '能力节点' }}</strong><small>{{ resultLabel(item.result) }} · {{ item.model || 'SYSTEM' }}</small></div><em v-if="overallScore(item) !== null">{{ overallScore(item) }}</em></header><p>{{ item.feedback?.summary || '评估已完成，请核对后确认。' }}</p><section><h3>优势</h3><ul><li v-for="value in feedbackList(item,'strengths')" :key="value">{{ value }}</li></ul><h3>缺口与行动</h3><ul><li v-for="value in [...feedbackList(item,'gaps'),...feedbackList(item,'nextActions')]" :key="value">{{ value }}</li></ul></section><footer v-if="item.status === 'EVALUATED'"><button type="button" :disabled="Boolean(pending)" @click="confirm(item,false)">拒绝</button><button type="button" :disabled="Boolean(pending)" @click="confirm(item,true)"><LoaderCircle v-if="pending === `confirm:${item.id}`" class="spin" :size="16" /><Check v-else :size="16" />确认</button></footer><p v-else class="confirmed"><Check :size="15" />{{ item.status === 'CONFIRMED' ? '已确认' : '已拒绝' }}</p></article></div></section>

    <section v-if="allValidations.length" class="validation-history"><h2>验证记录</h2><button v-for="item in allValidations" :key="item.id" type="button" :class="{ active: selectedValidationId === item.id }" @click="selectQueueNode(item.nodeId); selectedValidationId = item.id"><span>{{ canvas.nodes.find(node => node.logicalNodeId === item.nodeId)?.title || '能力节点' }}</span><em>{{ resultLabel(item.result) }}</em><small>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</small></button></section>
  </section>
</template>

<style scoped>
.validation-workspace{padding:18px 20px 96px;color:#172643;background:var(--bg);min-height:calc(100vh - 144px)}.validation-title>div{display:flex;align-items:center;gap:7px;color:#79879b;font-size:12px}.validation-title button{border:0;color:var(--primary);background:transparent}.validation-title h1{display:flex;align-items:center;gap:10px;margin:14px 0 0;font-size:26px}.validation-title h1 em{padding:4px 8px;border-radius:6px;color:#c87513;background:#fff3e3;font-size:12px;font-style:normal}.validation-steps{display:grid;grid-template-columns:repeat(5,1fr);gap:0;margin:18px 0;padding:0;list-style:none}.validation-steps li{position:relative;display:flex;align-items:center;gap:7px;color:#7b899e;font-size:12px}.validation-steps li:after{content:'';height:1px;flex:1;background:#cfd8e6}.validation-steps li:last-child:after{display:none}.validation-steps span{display:grid;place-items:center;width:29px;height:29px;border:1px solid #cbd6e6;border-radius:50%;color:#63728a;background:#fff}.validation-steps li.active{color:#31547e}.validation-steps li.active span{color:var(--primary);border-color:#93b8fa}.validation-steps li.current span{color:#fff;border-color:var(--primary);background:var(--primary)}.validation-layout{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:14px}.validation-layout main,.validation-layout aside{display:grid;align-content:start;gap:14px}.validation-layout main>section,.validation-layout aside>section,.important-rule,.evaluate-button{padding:16px;border:1px solid #e0e7f1;border-radius:10px;background:#fff}.validation-layout h2{margin:0 0 12px;font-size:18px}.node-select{display:grid;grid-template-columns:minmax(260px,420px) 1fr;gap:18px;align-items:end}.node-select label{display:grid;gap:7px;color:#53657e;font-size:13px}.node-select p{display:flex;align-items:center;gap:6px;margin:0;color:#75601f;font-size:12px}.method-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:9px}.method-grid button{position:relative;display:flex;align-items:flex-start;gap:8px;min-height:96px;padding:12px;border:1px solid #dfe6f1;border-radius:9px;text-align:left;color:var(--primary);background:#fff}.method-grid button.active{border-color:var(--primary);background:#f4f8ff;box-shadow:0 0 0 2px #2563eb0f}.method-grid button p{display:grid;gap:5px;margin:0;color:#263b5a}.method-grid button small{color:#8190a5;line-height:1.45}.method-grid button>svg:last-child{position:absolute;right:8px;top:8px}.validation-task{display:flex;gap:10px;padding:12px;border-radius:8px;background:#f6f9fd}.validation-task>span{display:grid;place-items:center;width:38px;height:38px;border-radius:9px;color:#fff;background:#18ac6c}.validation-task p{margin:5px 0 0;color:#6e7e96;line-height:1.55}.submission-field{position:relative;display:grid;gap:7px;margin-top:12px;color:#53657e;font-size:13px}.submission-field textarea{padding:11px;border:1px solid #dce4ef;border-radius:8px;resize:vertical}.submission-field small{position:absolute;right:8px;bottom:7px;color:#96a1b1}.important-rule{display:flex;gap:10px;margin:0;color:#775716!important;background:#fff8e8!important;font-size:12px;line-height:1.6}.important-rule span{display:grid}.criteria ol{display:grid;gap:8px;margin:0;padding:0;list-style:none}.criteria li{display:flex;align-items:center;gap:8px;min-height:42px;padding:8px;border:1px solid #cce8da;border-radius:7px;color:#3b6555;background:#f7fdf9}.criteria li span{display:grid;place-items:center;width:21px;height:21px;border-radius:50%;color:#fff;background:#22af70;font-size:12px}.evidence-check>button{display:grid;grid-template-columns:30px 1fr auto;gap:8px;align-items:center;width:100%;min-height:52px;margin-top:7px;padding:8px;border:1px solid #dfe7f1;border-radius:8px;text-align:left;color:var(--primary);background:#fff}.evidence-check>button.active{border-color:#9abcf7;background:#f5f9ff}.evidence-check>button span{display:grid;color:#2d405e}.evidence-check>button small{color:var(--text-3)}.evidence-check>p{color:#77869b;font-size:12px;line-height:1.6}.evaluation-result header{display:flex;align-items:center;gap:8px}.evaluation-result header>span{display:grid;place-items:center;width:38px;height:38px;border-radius:10px;color:var(--primary);background:#edf4ff}.evaluation-result header div{display:grid;flex:1}.evaluation-result header small{color:var(--text-3)}.evaluation-result header em{display:grid;place-items:center;width:44px;height:44px;border-radius:50%;color:#0c9a59;background:#e8f9f0;font-style:normal;font-weight:700}.evaluation-result>p,.evaluation-result li{color:#63738a;line-height:1.55}.evaluation-result footer{display:flex;gap:7px}.evaluation-result footer button,.evaluate-button{display:flex;align-items:center;justify-content:center;gap:6px;min-height:39px;padding:0 12px;border:1px solid #dce4ef;border-radius:7px;background:#fff}.evaluation-result footer button:last-child,.evaluate-button{color:#fff;border-color:var(--primary);background:var(--primary)}.evaluation-result .confirmed{display:flex;align-items:center;gap:6px;color:#0b9456}.evaluate-button{width:100%;min-height:44px}.validation-history{display:flex;align-items:center;gap:8px;margin-top:14px;padding:12px;overflow:auto;border:1px solid #e0e7f1;border-radius:10px;background:#fff}.validation-history h2{flex:0 0 auto;margin:0 6px 0 0;font-size:14px}.validation-history button{display:grid;gap:2px;flex:0 0 210px;padding:8px;border:1px solid #e2e8f1;border-radius:7px;text-align:left;background:#fff}.validation-history button.active{border-color:var(--primary)}.validation-history em{color:var(--primary);font-size:12px;font-style:normal}.validation-history small{color:#8a96a8;font-size:12px}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:1180px) and (min-width:901px){.validation-layout{grid-template-columns:1fr}.validation-layout aside{grid-row:auto}}
@media(max-width:900px){.validation-workspace{padding:12px 10px 100px}.validation-title h1{font-size:20px}.validation-steps{overflow:auto}.validation-steps li{flex:0 0 120px}.validation-layout{grid-template-columns:1fr}.node-select{grid-template-columns:1fr}.method-grid{grid-template-columns:1fr 1fr}.method-grid button{min-height:90px}.validation-layout aside{grid-row:auto}.validation-history{margin-bottom:0}}
.validation-step-scroll{overflow-x:auto;overflow-y:hidden;overscroll-behavior-inline:contain;scrollbar-width:none}.validation-step-scroll::-webkit-scrollbar{display:none}.validation-step-scroll:focus-visible{outline:2px solid rgba(37,99,235,.42);outline-offset:2px}.validation-steps{position:relative;min-width:680px}.validation-steps:before{position:absolute;left:14px;right:14px;top:14px;height:2px;background:#d9e2ef;content:''}.validation-step-progress{position:absolute;z-index:0;left:14px;top:14px;max-width:calc(100% - 29px);height:2px;background:var(--primary);transition:width .22s cubic-bezier(.2,.8,.2,1)}.validation-steps li{z-index:1;display:grid;grid-template-columns:29px minmax(0,1fr);align-items:center}.validation-steps li:after{display:none}.validation-steps li strong{position:relative;width:max-content;max-width:100%;padding:0 6px;font-size:12px;font-weight:500;background:var(--bg)}.validation-steps li.active span{background:#fff}.validation-steps li.current span{background:var(--primary)}.validation-layout main>section,.validation-layout aside>section,.important-rule,.evaluate-button,.validation-history{border-radius:8px}.method-grid button{border-radius:8px;transition:border-color .18s ease,background-color .18s ease,box-shadow .18s ease,transform .18s ease}.method-grid button:hover{transform:none;border-color:#b4c9e9}.method-grid button:focus-visible,.validation-history button:focus-visible,.evaluation-result button:focus-visible,.evaluate-button:focus-visible,textarea:focus-visible{outline:2px solid rgba(37,99,235,.42);outline-offset:2px}.validation-check-enter-active,.validation-check-leave-active,.validation-result-enter-active,.validation-result-leave-active{transition:opacity .18s ease,transform .22s cubic-bezier(.2,.8,.2,1)}.validation-check-enter-from,.validation-check-leave-to{opacity:0;transform:scale(.8)}.validation-result-enter-from,.validation-result-leave-to{opacity:0;transform:translateY(8px)}
@media(max-width:900px){.validation-steps{overflow:visible;margin:14px 0;min-width:620px}.validation-steps li{flex:initial}.validation-step-scroll{margin:0 -10px;padding:0 10px}.validation-history{scrollbar-width:none}.validation-history::-webkit-scrollbar{display:none}}
@media(prefers-reduced-motion:reduce){.validation-step-progress,.method-grid button,.validation-check-enter-active,.validation-check-leave-active,.validation-result-enter-active,.validation-result-leave-active{transition:none}.method-grid button:hover,.validation-check-enter-from,.validation-check-leave-to,.validation-result-enter-from,.validation-result-leave-to{transform:none}.spin{animation:none}}
.batch-queue header,.batch-results>header,.batch-task-state header{display:flex;align-items:center;justify-content:space-between;gap:12px}.batch-queue header h2,.batch-results>header h2{margin:0 0 4px}.batch-queue header p,.batch-results>header p{margin:0;color:#78879c;font-size:12px}.batch-queue header>strong,.batch-results>header>strong{flex:0 0 auto;color:var(--primary);font-size:12px}.batch-queue>div{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:12px}.batch-queue>div button{display:grid;grid-template-columns:28px minmax(0,1fr);gap:2px 8px;align-items:center;min-height:58px;padding:8px 10px;border:1px solid #dfe6f0;border-radius:7px;text-align:left;color:#263b5a;background:#fff}.batch-queue>div button>span{grid-row:1/3;display:grid;place-items:center;width:25px;height:25px;border:1px solid #c8d4e5;border-radius:50%;color:#77869b;font-size:12px}.batch-queue>div button strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.batch-queue>div button small{color:#8995a7}.batch-queue>div button.ready>span{color:#fff;border-color:#18a968;background:#18a968}.batch-queue>div button.active{border-color:var(--primary);background:#f5f9ff;box-shadow:0 0 0 2px rgba(37,99,235,.08)}.batch-queue>div button:focus-visible,.batch-task-state button:focus-visible{outline:2px solid rgba(37,99,235,.42);outline-offset:2px}.batch-task-state{display:grid;gap:10px}.batch-task-state header>div{display:grid;min-width:0;flex:1}.batch-task-state header small{overflow:hidden;color:#7e8da1;text-overflow:ellipsis;white-space:nowrap}.batch-task-state header em{color:var(--primary);font-style:normal;font-weight:700}.batch-task-progress{height:6px;overflow:hidden;border-radius:3px;background:#e4eaf3}.batch-task-progress i{display:block;height:100%;border-radius:inherit;background:var(--primary);transition:width .3s ease}.batch-task-state.failed{border-color:#f0c8cd!important;background:#fffafb!important}.batch-task-state.failed header>svg,.batch-task-state.failed header em{color:#d74755}.batch-task-state.failed p{margin:0;color:#784a51;line-height:1.55}.batch-task-state dl{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin:0}.batch-task-state dl div{display:grid;gap:3px;padding:8px;border:1px solid #f0d9dc;border-radius:6px;background:#fff}.batch-task-state dt{color:#8d6f73;font-size:12px}.batch-task-state dd{margin:0;overflow-wrap:anywhere;color:#604046;font-size:12px}.batch-task-state button{display:flex;align-items:center;justify-content:center;gap:6px;min-height:38px;border:1px solid #df9fa7;border-radius:7px;color:#b52e3e;background:#fff}.batch-results{margin-top:14px;padding:16px;border:1px solid #e0e7f1;border-radius:8px;background:#fff}.batch-results>div{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:14px}.batch-results article{padding:14px;border:1px solid #dde5f0;border-radius:8px;background:#fff}.batch-results article>section{margin-top:10px;padding-top:10px;border-top:1px solid #edf1f5}.batch-results article h3{margin:8px 0 4px;color:#53657e;font-size:12px}.batch-results article ul{margin:0;padding-left:18px}.batch-results article footer{margin-top:12px}.batch-results article.result-passed{border-color:#bfe4d1}.batch-results article.result-needs_work{border-color:#f1d4a9}.batch-results article.result-insufficient{border-color:#d7dce7}
@media(max-width:900px){.batch-queue>div,.batch-results>div{grid-template-columns:1fr}.batch-queue header,.batch-results>header{align-items:flex-start}.batch-task-state dl{grid-template-columns:1fr}.batch-results{padding:12px}}
@media(prefers-reduced-motion:reduce){.batch-task-progress i{transition:none}}
</style>
