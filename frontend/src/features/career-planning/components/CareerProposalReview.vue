<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Check, CircleAlert, GitCompareArrows, LoaderCircle, Sparkles, X } from 'lucide-vue-next'
import AppModal from '@/shared/ui/AppModal.vue'
import { errorMessage } from '@/shared/api/types'
import {
  decideCareerCanvasProposal, discardCareerCanvasProposal, generateCareerCanvasProposal,
} from '../services/careerPlanningApi'
import type { CareerCanvas, CareerCanvasProposal, CareerProposalApplyResult } from '../types'
import { careerSourceRefLabels } from '../utils/presentation'

const props = defineProps<{
  open: boolean
  sessionId: string
  canvas: CareerCanvas
  initialProposal?: CareerCanvasProposal | null
}>()

const emit = defineEmits<{
  close: []
  applied: [result: CareerProposalApplyResult]
  generated: [proposal: CareerCanvasProposal]
  discarded: [proposal: CareerCanvasProposal]
}>()

const instruction = ref('')
const proposal = ref<CareerCanvasProposal | null>(null)
const pending = ref('')
const message = ref('')
const decisions = reactive<Record<string, { decision: 'ACCEPTED' | 'REJECTED' | ''; reason: string }>>({})

watch(() => [props.open, props.initialProposal] as const, ([open, value]) => {
  if (!open) return
  proposal.value = value ?? null
  message.value = ''
  syncDecisions()
}, { immediate: true })

function syncDecisions(): void {
  Object.keys(decisions).forEach(key => delete decisions[key])
  proposal.value?.items.forEach(item => {
    decisions[item.id] = {
      decision: item.decision === 'ACCEPTED' || item.decision === 'REJECTED' ? item.decision : '',
      reason: item.rejectionReason ?? '',
    }
  })
}

const decidedCount = computed(() => proposal.value?.items.filter(item => decisions[item.id]?.decision).length ?? 0)
const allDecided = computed(() => Boolean(proposal.value?.items.length)
  && proposal.value!.items.every(item => {
    const choice = decisions[item.id]
    return choice?.decision === 'ACCEPTED' || (choice?.decision === 'REJECTED' && choice.reason.trim())
  }))

function afterTitle(item: CareerCanvasProposal['items'][number]): string {
  if (item.operation === 'ADD_RELATION') {
    return `${referenceTitle(String(item.after?.fromNodeId ?? ''))} → ${referenceTitle(String(item.after?.toNodeId ?? ''))}`
  }
  const value = item.after?.title
  return typeof value === 'string' && value.trim() ? value : item.operation === 'DELETE' ? '删除节点' : '结构调整'
}

function beforeTitle(item: CareerCanvasProposal['items'][number]): string {
  if (item.operation === 'ADD_RELATION') return '尚未建立前置依赖'
  const value = item.before?.title
  return typeof value === 'string' && value.trim() ? value : '新增节点'
}

function operationLabel(value: CareerCanvasProposal['items'][number]['operation']): string {
  return ({ ADD: '新增', UPDATE: '修改', DELETE: '删除', MOVE: '移动', ADD_RELATION: '关系' } as const)[value]
}

function referenceTitle(value: string): string {
  if (value.startsWith('PROPOSAL:')) {
    const key = value.slice('PROPOSAL:'.length)
    const item = proposal.value?.items.find(candidate => candidate.proposalKey === key)
    return typeof item?.after?.title === 'string' ? item.after.title : '新增节点'
  }
  return props.canvas.nodes.find(node => node.logicalNodeId === value)?.title || '能力节点'
}

async function generate(): Promise<void> {
  if (!instruction.value.trim()) { message.value = '请先说明希望 AI 优化的方向。'; return }
  pending.value = 'generate'; message.value = ''
  try {
    proposal.value = await generateCareerCanvasProposal(props.sessionId, instruction.value.trim(), props.canvas.version)
    syncDecisions()
    emit('generated', proposal.value)
  } catch (reason) { message.value = errorMessage(reason, 'AI 差异建议生成失败') }
  finally { pending.value = '' }
}

function acceptAll(): void {
  proposal.value?.items.forEach(item => { decisions[item.id] = { decision: 'ACCEPTED', reason: '' } })
}

async function apply(): Promise<void> {
  if (!proposal.value || proposal.value.status === 'STALE') { message.value = '该提案基于旧画布，请放弃后重新推演。'; return }
  if (!allDecided.value) { message.value = '每一项建议都需要接受或拒绝；拒绝时请填写原因。'; return }
  pending.value = 'apply'; message.value = ''
  try {
    const result = await decideCareerCanvasProposal(props.sessionId, proposal.value.id,
      proposal.value.items.map(item => ({
        itemId: item.id,
        decision: decisions[item.id].decision as 'ACCEPTED' | 'REJECTED',
        rejectionReason: decisions[item.id].decision === 'REJECTED' ? decisions[item.id].reason.trim() : undefined,
      })), props.canvas.version)
    emit('applied', result)
    emit('close')
  } catch (reason) { message.value = errorMessage(reason, '差异决定保存失败') }
  finally { pending.value = '' }
}

async function discard(): Promise<void> {
  if (!proposal.value || !['DRAFT', 'STALE'].includes(proposal.value.status)) return
  pending.value = 'discard'; message.value = ''
  try {
    proposal.value = await discardCareerCanvasProposal(props.sessionId, proposal.value.id)
    emit('discarded', proposal.value)
    emit('close')
  } catch (reason) { message.value = errorMessage(reason, '提案放弃失败') }
  finally { pending.value = '' }
}
</script>

<template>
  <AppModal :open="open" title="AI 画布差异提案" :width="920" mobile-sheet @close="emit('close')">
    <div class="proposal-review">
      <section v-if="!proposal" class="proposal-prompt">
        <span><Sparkles :size="28" /></span>
        <div><h2>先描述你希望补强的方向</h2><p>AI 只会形成可审阅的新增、修改、移动或删除建议，不会直接覆盖画布。</p></div>
        <textarea v-model="instruction" rows="5" maxlength="1000" placeholder="例如：补强 Spring Boot 云原生工程能力，并增加可验证的项目任务"></textarea>
        <footer><small>{{ instruction.length }} / 1000</small><button type="button" :disabled="pending === 'generate'" @click="generate"><LoaderCircle v-if="pending === 'generate'" class="spin" :size="17" /><Sparkles v-else :size="17" />生成差异提案</button></footer>
      </section>

      <template v-else>
        <header class="proposal-head"><div><span><GitCompareArrows :size="21" /></span><p><strong>{{ proposal.proposalType === 'NODE_INFERENCE' ? '节点推演提案' : '全局优化提案' }} · 基于画布 v{{ proposal.baseVersion }}</strong><small>{{ proposal.instruction }}</small></p></div><em>{{ decidedCount }} / {{ proposal.items.length }} 已决定</em></header>
        <p v-if="proposal.status === 'STALE'" class="proposal-error" role="alert"><CircleAlert :size="16" />画布已产生新版本，这份提案已过期。请放弃后重新推演。</p>
        <p class="proposal-boundary"><CircleAlert :size="16" />锁定节点、用户备注和未被事实支持的内容不会被 AI 直接改写。</p>
        <div class="proposal-items">
          <article v-for="item in proposal.items" :key="item.id" :class="`is-${decisions[item.id]?.decision.toLowerCase() || 'pending'}`">
            <header><span>{{ operationLabel(item.operation) }}</span><strong>{{ afterTitle(item) }}</strong><small>{{ item.reason }}</small></header>
            <div class="proposal-diff"><p><em>调整前</em>{{ beforeTitle(item) }}</p><span>→</span><p><em>调整后</em>{{ afterTitle(item) }}</p></div>
            <div v-if="item.sourceRefs.length" class="proposal-refs"><span v-for="source in careerSourceRefLabels(item.sourceRefs)" :key="source">{{ source }}</span></div>
            <footer><button type="button" :class="{ active: decisions[item.id]?.decision === 'ACCEPTED' }" @click="decisions[item.id] = { decision: 'ACCEPTED', reason: '' }"><Check :size="15" />接受</button><button type="button" :class="{ active: decisions[item.id]?.decision === 'REJECTED' }" @click="decisions[item.id] = { decision: 'REJECTED', reason: decisions[item.id]?.reason || '' }"><X :size="15" />拒绝</button></footer>
            <textarea v-if="decisions[item.id]?.decision === 'REJECTED'" v-model="decisions[item.id].reason" rows="2" maxlength="1000" placeholder="填写拒绝原因，便于保留审计记录"></textarea>
          </article>
        </div>
      </template>
      <p v-if="message" class="proposal-error" role="alert"><CircleAlert :size="16" />{{ message }}</p>
    </div>
    <template #footer>
      <button class="proposal-cancel" type="button" @click="emit('close')">取消</button>
      <button v-if="proposal && ['DRAFT','STALE'].includes(proposal.status)" class="proposal-cancel proposal-discard" type="button" :disabled="pending === 'discard'" @click="discard">放弃提案</button>
      <button v-if="proposal && proposal.status === 'DRAFT'" class="proposal-secondary" type="button" @click="acceptAll">全部接受</button>
      <button v-if="proposal && proposal.status === 'DRAFT'" class="proposal-apply" type="button" :disabled="pending === 'apply' || !allDecided" @click="apply"><LoaderCircle v-if="pending === 'apply'" class="spin" :size="17" /><Check v-else :size="17" />应用已确认差异</button>
    </template>
  </AppModal>
</template>

<style scoped>
.proposal-review{display:grid;gap:16px;color:var(--text-primary)}.proposal-prompt{display:grid;grid-template-columns:48px 1fr;gap:12px;padding:4px}.proposal-prompt>span{display:grid;place-items:center;width:44px;height:44px;border-radius:12px;color:var(--color-primary);background:var(--surface-2)}.proposal-prompt h2{margin:0 0 5px;font-size:19px}.proposal-prompt p{margin:0;color:var(--text-secondary);line-height:1.6}.proposal-prompt textarea{grid-column:1/-1;min-height:126px;padding:14px;border:1px solid var(--border-subtle);border-radius:10px;outline:0;resize:vertical}.proposal-prompt textarea:focus{border-color:var(--color-primary);box-shadow:0 0 0 3px color-mix(in srgb, var(--color-primary) 7%, transparent)}.proposal-prompt footer{grid-column:1/-1;display:flex;align-items:center;justify-content:flex-end;gap:14px}.proposal-prompt footer small{color:var(--text-tertiary)}.proposal-prompt footer button,.proposal-apply{display:flex;align-items:center;justify-content:center;gap:7px;min-height:42px;padding:0 18px;border:0;border-radius:8px;color:var(--text-on-primary);background:var(--color-primary)}.proposal-head,.proposal-head>div{display:flex;align-items:center;justify-content:space-between}.proposal-head>div{gap:10px}.proposal-head span{display:grid;place-items:center;width:40px;height:40px;border-radius:10px;color:var(--color-primary);background:var(--surface-2)}.proposal-head p{display:grid;margin:0}.proposal-head small{margin-top:3px;color:var(--text-secondary)}.proposal-head em{padding:5px 9px;border-radius:6px;color:var(--color-primary);background:var(--surface-2);font-style:normal;font-size:12px}.proposal-boundary,.proposal-error{display:flex;align-items:center;gap:7px;margin:0;padding:10px 12px;border-radius:8px;color:var(--color-warning-text);background:var(--surface-2);font-size:13px}.proposal-error{color:var(--color-danger-text);background:var(--surface-2)}.proposal-items{display:grid;gap:10px;max-height:52vh;overflow:auto}.proposal-items article{display:grid;gap:11px;padding:14px;border:1px solid var(--border-subtle);border-radius:10px;background:var(--surface-1)}.proposal-items article.is-accepted{border-color:color-mix(in srgb, var(--color-success) 35%, var(--border-subtle));background:var(--surface-1)}.proposal-items article.is-rejected{border-color:var(--border-default);background:var(--surface-1)}.proposal-items article>header{display:grid;grid-template-columns:auto 1fr;gap:3px 8px}.proposal-items article>header span{grid-row:1/3;padding:4px 7px;border-radius:5px;color:var(--color-primary);background:var(--surface-2);font-size:12px}.proposal-items article>header small{color:var(--text-secondary)}.proposal-diff{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:9px}.proposal-diff p{display:grid;gap:4px;margin:0;padding:10px;border-radius:7px;background:var(--surface-2)}.proposal-diff em{color:var(--text-secondary);font-size:12px;font-style:normal}.proposal-diff>span{color:var(--text-tertiary)}.proposal-refs{display:flex;flex-wrap:wrap;gap:5px}.proposal-refs span{padding:3px 6px;border-radius:4px;color:var(--color-primary-text);background:var(--surface-2);font-size:12px}.proposal-items article>footer{display:flex;gap:8px}.proposal-items article>footer button,.proposal-cancel,.proposal-secondary{display:flex;align-items:center;gap:5px;min-height:36px;padding:0 12px;border:1px solid var(--border-subtle);border-radius:7px;color:var(--text-secondary);background:var(--surface-1)}.proposal-items article>footer button.active:first-child{color:var(--color-success-text);border-color:var(--color-success);background:var(--surface-2)}.proposal-items article>footer button.active:last-child{color:var(--color-danger-text);border-color:color-mix(in srgb, var(--color-danger) 35%, var(--border-subtle));background:var(--surface-2)}.proposal-items article>textarea{padding:10px;border:1px solid var(--border-default);border-radius:7px;resize:vertical}.proposal-apply:disabled,.proposal-prompt button:disabled{opacity:.5}.proposal-secondary{color:var(--color-primary);border-color:var(--color-primary-border)}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:700px){.proposal-head{align-items:flex-start;gap:8px}.proposal-head em{white-space:nowrap}.proposal-diff{grid-template-columns:1fr}.proposal-diff>span{display:none}.proposal-items{max-height:58vh}}
</style>
