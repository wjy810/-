<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  LayoutDashboard, LoaderCircle, MessageCircle, RefreshCw, Sparkles,
} from 'lucide-vue-next'
import { errorMessage } from '@/shared/api/types'
import JobProofIcon from '@/shared/ui/JobProofIcon.vue'
import { jobProofIconIds } from '@/shared/ui/jobProofIcons'
import CareerAbilityCanvas from './CareerAbilityCanvas.vue'
import CareerLearningPlanView from './CareerLearningPlan.vue'
import CareerPlanSetupDialog from './CareerPlanSetupDialog.vue'
import CareerProposalReview from './CareerProposalReview.vue'
import CareerValidationWorkspace from './CareerValidationWorkspace.vue'
import CareerVersionHistory from './CareerVersionHistory.vue'
import { fetchCareerExecution, fetchCareerPlanningSession } from '../services/careerPlanningApi'
import { careerPlanStatusLabel, hasActionablePlanNodes, hasCanvasContent, initialCareerWorkbenchView } from '../utils/presentation'
import {
  CAREER_WORKBENCH_VIEWS, careerWorkbenchDirection, careerWorkbenchQueryValue,
  careerWorkbenchViewFromQuery, resolveCareerWorkbenchKey,
} from '../utils/workbenchTransition'
import type { CareerWorkbenchView, CareerWorkbenchDirection } from '../utils/workbenchTransition'
import type {
  CareerAbilityValidation, CareerCanvas, CareerCanvasProposal, CareerExecutionOverview,
  CareerLearningPlan, CareerPlanningSession, CareerProposalApplyResult,
} from '../types'

const props = defineProps<{ session: CareerPlanningSession }>()
const emit = defineEmits<{
  sessionUpdated: [session: CareerPlanningSession]
  error: [message: string]
  notice: [message: string]
}>()
const route = useRoute()
const router = useRouter()
const execution = ref<CareerExecutionOverview | null>(null)
const loadingExecution = ref(true)
const activeView = ref<CareerWorkbenchView>('CANVAS')
const transitionDirection = ref<CareerWorkbenchDirection>('none')
const transitionName = ref('career-view-fade')
const viewHost = ref<HTMLElement | null>(null)
const scrollPositions = reactive<Record<CareerWorkbenchView, number>>({
  CANVAS: 0,
  PLAN: 0,
  VALIDATION: 0,
  HISTORY: 0,
})
const proposalOpen = ref(false)
const planSetupOpen = ref(false)
const validationNodeIds = ref<string[]>([])

const canvas = computed(() => props.session.canvas!)
const plan = computed(() => execution.value?.activePlan ?? null)
const pendingProposal = computed(() => execution.value?.pendingProposal ?? null)
const validations = computed(() => execution.value?.validations ?? [])
const masteredCount = computed(() => canvas.value.nodes.filter(node => node.status === 'MASTERED').length)
const learningCount = computed(() => canvas.value.nodes.filter(node => node.status === 'LEARNING').length)
const validationCount = computed(() => canvas.value.nodes.filter(node => node.status === 'PENDING_VALIDATION').length)
const canOptimizeCanvas = computed(() => props.session.aiConsent && hasCanvasContent(canvas.value))
const canCreatePlan = computed(() => hasActionablePlanNodes(canvas.value))
const suggestedHours = computed(() => {
  const value = props.session.profile.basics.weeklyLearningHours
  if (typeof value !== 'string') return 10
  const numbers = value.match(/\d+/g)?.map(Number) ?? []
  return numbers.length > 1 ? Math.round((numbers[0] + numbers[1]) / 2) : numbers[0] || 10
})

function openPlanSetup(): void {
  if (!canCreatePlan.value) {
    emit('notice', '请先生成包含技能、知识或任务节点的完整能力树。')
    return
  }
  planSetupOpen.value = true
}

async function openMobileConversation(): Promise<void> {
  if (!canOptimizeCanvas.value) {
    await setView('CANVAS')
    emit('notice', '完整能力树生成后，可以在“对话”中让 AI 提出逐项差异建议。')
    return
  }
  proposalOpen.value = true
}

const tabs = [
  { value: 'CANVAS' as const, label: '能力画布', icon: jobProofIconIds.abilityCanvas },
  { value: 'PLAN' as const, label: '学习计划', icon: jobProofIconIds.learningPlan },
  { value: 'VALIDATION' as const, label: '能力验证', icon: jobProofIconIds.abilityValidation },
  { value: 'HISTORY' as const, label: '版本记录', icon: jobProofIconIds.versionHistory },
]
const activeTabIndex = computed(() => CAREER_WORKBENCH_VIEWS.indexOf(activeView.value))
const panelId = computed(() => `career-workbench-panel-${activeView.value.toLowerCase()}`)

watch(() => props.session.id, () => {
  CAREER_WORKBENCH_VIEWS.forEach(view => { scrollPositions[view] = 0 })
  void loadExecution()
})
watch(() => route.query.view, value => {
  const view = careerWorkbenchViewFromQuery(value)
  if (view && view !== activeView.value) void setView(view, false)
})
watch(() => [route.query.nodeId, route.query.nodeIds], () => {
  validationNodeIds.value = validationNodesFromQuery()
})
onMounted(() => {
  const routeView = careerWorkbenchViewFromQuery(route.query.view)
  activeView.value = routeView ?? initialCareerWorkbenchView(props.session.phase)
  validationNodeIds.value = validationNodesFromQuery()
  if (route.query.view !== careerWorkbenchQueryValue(activeView.value)) {
    void router.replace({ query: { ...route.query, view: careerWorkbenchQueryValue(activeView.value) } })
  }
  void restoreViewScroll(activeView.value)
  void loadExecution()
})
onBeforeUnmount(() => saveViewScroll(activeView.value))

async function loadExecution(): Promise<void> {
  loadingExecution.value = true
  try { execution.value = await fetchCareerExecution(props.session.id) }
  catch (reason) { emit('error', errorMessage(reason, '规划执行状态读取失败')) }
  finally { loadingExecution.value = false }
}

function saveViewScroll(view: CareerWorkbenchView): void {
  const host = viewHost.value
  if (!host) return
  const hostTop = host.getBoundingClientRect().top + window.scrollY
  scrollPositions[view] = Math.max(0, Math.round(window.scrollY + workbenchHeaderOffset(host) - hostTop))
}

function workbenchHeaderOffset(host: HTMLElement): number {
  const header = host.parentElement?.querySelector<HTMLElement>('.workbench-shell-header')
  if (!header) return 0
  const rect = header.getBoundingClientRect()
  const stickyTop = Number.parseFloat(window.getComputedStyle(header).top)
  return Math.max(0, (Number.isFinite(stickyTop) ? stickyTop : Math.max(0, rect.top)) + rect.height)
}

async function restoreViewScroll(view: CareerWorkbenchView = activeView.value): Promise<void> {
  await nextTick()
  if (view !== activeView.value) return
  const host = viewHost.value
  if (!host) return
  const hostTop = host.getBoundingClientRect().top + window.scrollY
  window.scrollTo({
    top: Math.max(0, hostTop + scrollPositions[view] - workbenchHeaderOffset(host)),
    behavior: 'auto',
  })
  if (view === 'CANVAS') {
    window.requestAnimationFrame(() => window.dispatchEvent(new Event('resize')))
  }
}

async function setView(view: CareerWorkbenchView, updateRoute = true): Promise<void> {
  let changed = false
  if (view !== activeView.value) {
    saveViewScroll(activeView.value)
    transitionDirection.value = careerWorkbenchDirection(activeView.value, view)
    transitionName.value = activeView.value === 'CANVAS' || view === 'CANVAS'
      ? 'career-view-fade'
      : `career-view-${transitionDirection.value}`
    activeView.value = view
    changed = true
  }
  if (changed) await restoreViewScroll(view)
  if (updateRoute && route.query.view !== careerWorkbenchQueryValue(view)) {
    await router.replace({ query: {
      ...route.query,
      view: careerWorkbenchQueryValue(view),
      nodeId: view === 'VALIDATION' ? route.query.nodeId : undefined,
      nodeIds: view === 'VALIDATION' ? route.query.nodeIds : undefined,
    } })
  }
}

async function onTabKeydown(event: KeyboardEvent): Promise<void> {
  const view = resolveCareerWorkbenchKey(activeView.value, event.key)
  if (!view) return
  event.preventDefault()
  await setView(view)
  await nextTick()
  document.getElementById(`career-workbench-tab-${view.toLowerCase()}`)?.focus()
}

async function refreshSession(): Promise<CareerPlanningSession | null> {
  try {
    const value = await fetchCareerPlanningSession(props.session.id)
    emit('sessionUpdated', value)
    return value
  } catch (reason) { emit('error', errorMessage(reason, '职业规划状态刷新失败')); return null }
}

function updateCanvas(value: CareerCanvas): void {
  emit('sessionUpdated', { ...props.session, canvas: value })
}

function proposalGenerated(value: CareerCanvasProposal): void {
  execution.value = { pendingProposal: value, activePlan: plan.value, validations: validations.value }
}

function reviewProposal(value: CareerCanvasProposal): void {
  proposalGenerated(value)
  proposalOpen.value = true
}

function proposalDiscarded(): void {
  if (execution.value) execution.value = { ...execution.value, pendingProposal: null }
  emit('notice', '待审提案已放弃，画布版本没有变化。')
}

function proposalApplied(result: CareerProposalApplyResult): void {
  const createdVersion = result.canvas.version !== canvas.value.version
  updateCanvas(result.canvas)
  if (execution.value) execution.value = { ...execution.value, pendingProposal: null }
  emit('notice', createdVersion ? 'AI 差异决定已保存，并创建新的画布版本。' : '提案已完成审阅，未创建空版本。')
}

async function planCreated(value: CareerLearningPlan): Promise<void> {
  execution.value = { pendingProposal: pendingProposal.value, activePlan: value, validations: validations.value }
  await refreshSession()
  await setView('PLAN')
  emit('notice', `${value.durationWeeks} 周学习计划已生成。`)
}

async function planUpdated(value: CareerLearningPlan, shouldRefresh: boolean): Promise<void> {
  if (execution.value) execution.value = { ...execution.value, activePlan: value }
  if (shouldRefresh) await refreshSession()
}

async function validationUpdated(value: CareerAbilityValidation, shouldRefresh: boolean): Promise<void> {
  const next = validations.value.filter(item => item.id !== value.id)
  if (execution.value) execution.value = { ...execution.value, validations: [value, ...next] }
  if (shouldRefresh) await refreshSession()
}

async function validationsUpdated(values: CareerAbilityValidation[], shouldRefresh: boolean): Promise<void> {
  const ids = new Set(values.map(value => value.id))
  const next = validations.value.filter(item => !ids.has(item.id))
  if (execution.value) execution.value = { ...execution.value, validations: [...values, ...next] }
  if (shouldRefresh) await refreshSession()
}

function validationNodesFromQuery(): string[] {
  const batch = Array.isArray(route.query.nodeIds) ? route.query.nodeIds.join(',') : route.query.nodeIds
  const single = Array.isArray(route.query.nodeId) ? route.query.nodeId[0] : route.query.nodeId
  const raw = typeof batch === 'string' && batch.trim() ? batch : typeof single === 'string' ? single : ''
  return Array.from(new Set(raw.split(',').map(value => value.trim()).filter(Boolean))).slice(0, 8)
}

async function openValidation(value: string | string[] = ''): Promise<void> {
  const nodeIds = Array.from(new Set((Array.isArray(value) ? value : value ? [value] : []).filter(Boolean))).slice(0, 8)
  validationNodeIds.value = nodeIds
  await setView('VALIDATION', false)
  await router.replace({ query: {
    ...route.query,
    view: careerWorkbenchQueryValue('VALIDATION'),
    nodeId: nodeIds.length === 1 ? nodeIds[0] : undefined,
    nodeIds: nodeIds.length > 1 ? nodeIds.join(',') : undefined,
  } })
}

async function restored(value: CareerCanvas): Promise<void> {
  updateCanvas(value)
  await loadExecution()
}

</script>

<template>
  <section class="planning-workbench" :class="{ 'is-canvas-view': activeView === 'CANVAS' }">
    <div class="workbench-shell-header">
      <header class="workbench-header">
        <nav
          class="workbench-tabs"
          role="tablist"
          aria-label="职业规划工作台视图"
          :style="{ '--active-tab': activeTabIndex }"
          @keydown="onTabKeydown"
        >
          <button
            v-for="tab in tabs"
            :id="`career-workbench-tab-${tab.value.toLowerCase()}`"
            :key="tab.value"
            type="button"
            role="tab"
            :aria-selected="activeView === tab.value"
            :aria-controls="`career-workbench-panel-${tab.value.toLowerCase()}`"
            :tabindex="activeView === tab.value ? 0 : -1"
            :class="{ active: activeView === tab.value }"
            @click="setView(tab.value)"
          >
            <JobProofIcon :name="tab.icon" :size="17" />
            <span>{{ tab.label }}</span>
            <em v-if="tab.value === 'PLAN' && plan && careerPlanStatusLabel(plan.status)">{{ careerPlanStatusLabel(plan.status) }}</em>
            <em v-if="tab.value === 'VALIDATION' && validationCount">{{ validationCount }}</em>
          </button>
          <i class="workbench-tab-indicator" aria-hidden="true" />
        </nav>
        <div class="workbench-actions"><button type="button" aria-label="AI 优化" :disabled="!canOptimizeCanvas" :title="canOptimizeCanvas ? '生成能力画布差异建议' : '请先生成完整能力树'" @click="proposalOpen = true"><Sparkles :size="16" /><span>AI 优化</span><em v-if="pendingProposal">待确认</em></button><button type="button" :aria-label="plan ? '调整计划' : '生成计划'" :disabled="!canCreatePlan" :title="canCreatePlan ? '生成学习计划' : '请先生成包含技能或任务的能力树'" @click="openPlanSetup"><JobProofIcon :name="jobProofIconIds.learningPlan" :size="16" /><span>{{ plan ? '调整计划' : '生成计划' }}</span></button><button type="button" aria-label="画布总览" @click="router.push({ name: 'career-planning' })"><LayoutDashboard :size="16" /><span>画布总览</span></button><button class="icon-only" type="button" aria-label="刷新执行状态" title="刷新执行状态" @click="loadExecution"><RefreshCw :size="16" /></button></div>
      </header>
      <section class="workbench-status" aria-label="职业规划执行摘要"><span class="status-goal"><strong>{{ session.activeGoal?.title }}</strong><small>目标职业</small></span><span><strong>{{ canvas.nodes.length }}</strong><small>能力节点</small></span><span><strong>{{ masteredCount }}</strong><small>已掌握</small></span><span><strong>{{ learningCount }}</strong><small>学习中</small></span><span><strong>{{ validationCount }}</strong><small>待验证</small></span><span class="status-version"><strong>v{{ canvas.version }}</strong><small>画布版本</small></span></section>
    </div>

    <div v-if="loadingExecution" class="execution-loading"><LoaderCircle class="spin" :size="19" />正在同步执行状态</div>
    <div ref="viewHost" class="workbench-view-host" :data-view="activeView" :data-direction="transitionDirection">
      <Transition :name="transitionName" mode="out-in" @after-enter="restoreViewScroll()">
        <KeepAlive>
          <CareerAbilityCanvas v-if="activeView === 'CANVAS'" :id="panelId" :key="'CANVAS'" role="tabpanel" aria-labelledby="career-workbench-tab-canvas" :session-id="session.id" :canvas="canvas" :goal-title="session.activeGoal?.title || '目标职业'" :ai-consent="session.aiConsent" :pending-proposal="pendingProposal" @updated="updateCanvas" @proposal-generated="proposalGenerated" @review-proposal="reviewProposal" @validate="openValidation" @error="emit('error',$event)" @notice="emit('notice',$event)" />
          <CareerLearningPlanView v-else-if="activeView === 'PLAN'" :id="panelId" :key="'PLAN'" role="tabpanel" aria-labelledby="career-workbench-tab-plan" :session-id="session.id" :plan="plan" :canvas="canvas" :can-create="canCreatePlan" @updated="planUpdated" @create="openPlanSetup" @validate="openValidation" @canvas="setView('CANVAS')" @error="emit('error',$event)" @notice="emit('notice',$event)" />
          <CareerValidationWorkspace v-else-if="activeView === 'VALIDATION'" :id="panelId" :key="'VALIDATION'" role="tabpanel" aria-labelledby="career-workbench-tab-validation" :session-id="session.id" :canvas="canvas" :plan="plan" :validations="validations" :initial-node-ids="validationNodeIds" @updated="validationUpdated" @batch-updated="validationsUpdated" @canvas="setView('CANVAS')" @error="emit('error',$event)" @notice="emit('notice',$event)" />
          <CareerVersionHistory v-else :id="panelId" :key="'HISTORY'" role="tabpanel" aria-labelledby="career-workbench-tab-history" :session-id="session.id" :canvas="canvas" @restored="restored" @error="emit('error',$event)" @notice="emit('notice',$event)" />
        </KeepAlive>
      </Transition>
    </div>

    <nav class="workbench-mobile-nav" aria-label="职业规划移动端工作台导航">
      <button type="button" :class="{ active: proposalOpen }" @click="openMobileConversation"><MessageCircle :size="20" /><span>对话</span><em v-if="pendingProposal">1</em></button>
      <button type="button" :class="{ active: activeView === 'CANVAS' && !proposalOpen }" @click="setView('CANVAS')"><JobProofIcon :name="jobProofIconIds.abilityCanvas" :size="20" /><span>画布</span></button>
      <button type="button" :class="{ active: activeView === 'PLAN' && !proposalOpen }" @click="setView('PLAN')"><JobProofIcon :name="jobProofIconIds.learningPlan" :size="20" /><span>计划</span></button>
      <button type="button" :class="{ active: activeView === 'VALIDATION' && !proposalOpen }" @click="setView('VALIDATION')"><JobProofIcon :name="jobProofIconIds.abilityValidation" :size="20" /><span>验证</span><em v-if="validationCount">{{ validationCount }}</em></button>
      <button type="button" :class="{ active: activeView === 'HISTORY' && !proposalOpen }" @click="setView('HISTORY')"><JobProofIcon :name="jobProofIconIds.versionHistory" :size="20" /><span>版本</span></button>
    </nav>

    <CareerProposalReview :open="proposalOpen" :session-id="session.id" :canvas="canvas" :initial-proposal="pendingProposal" @close="proposalOpen = false" @generated="proposalGenerated" @applied="proposalApplied" @discarded="proposalDiscarded" />
    <CareerPlanSetupDialog :open="planSetupOpen" :session-id="session.id" :canvas-version="canvas.version" :suggested-hours="suggestedHours" @close="planSetupOpen = false" @created="planCreated" />
  </section>
</template>

<style scoped>
.planning-workbench{--cp-motion-fast:180ms;--cp-motion-view:220ms;--cp-motion-ease:cubic-bezier(.2,.8,.2,1);--cp-motion-shift:8px;position:relative;min-width:0;color:var(--text-primary);background:var(--bg)}.workbench-shell-header{position:sticky;z-index:28;top:64px;background:var(--surface-1);box-shadow:0 8px 22px rgba(31,54,89,.045)}.workbench-header{display:flex;align-items:center;justify-content:space-between;min-height:58px;padding:0 18px;border-bottom:1px solid var(--border-subtle);background:var(--surface-1)}.workbench-tabs,.workbench-actions{display:flex;align-items:center;gap:4px}.workbench-tabs{position:relative;display:grid;grid-template-columns:repeat(4,minmax(112px,1fr));align-self:stretch;gap:0}.workbench-header button{position:relative;display:flex;align-items:center;justify-content:center;gap:6px;min-height:40px;padding:0 11px;border:0;border-radius:7px;color:var(--text-secondary);background:transparent;transition:color var(--cp-motion-fast) var(--cp-motion-ease),background-color var(--cp-motion-fast) var(--cp-motion-ease)}.workbench-tabs button{min-width:0;border-radius:0}.workbench-tabs button.active{color:var(--color-primary);background:var(--surface-2)}.workbench-tab-indicator{position:absolute;left:0;bottom:0;width:25%;height:3px;border-radius:3px 3px 0 0;background:var(--color-primary);pointer-events:none;transform:translateX(calc(var(--active-tab) * 100%));transition:transform var(--cp-motion-view) var(--cp-motion-ease)}.workbench-header button em{display:grid;place-items:center;min-width:18px;height:18px;padding:0 4px;border-radius:9px;color:var(--text-on-primary);background:var(--color-warning);font-size:11px;font-style:normal}.workbench-actions button{border:1px solid var(--border-subtle);color:var(--color-primary-text);background:var(--surface-1)}.workbench-actions button:first-child{color:var(--color-primary)}.workbench-header .icon-only{width:38px;padding:0;justify-content:center}.workbench-header button:focus-visible,.workbench-mobile-nav button:focus-visible{z-index:2;outline:2px solid color-mix(in srgb, var(--color-primary) 42%, transparent);outline-offset:2px}.workbench-status{display:grid;grid-template-columns:minmax(180px,1fr) repeat(5,minmax(82px,auto));gap:1px;padding:8px 18px;border-bottom:1px solid var(--border-subtle);background:var(--surface-1)}.workbench-status span{display:grid;min-width:0;padding:3px 14px;border-left:1px solid var(--border-subtle);color:var(--text-secondary);font-size:12px}.workbench-status span:first-child{padding-left:0;border-left:0}.workbench-status strong{overflow:hidden;color:var(--text-primary);font-size:13px;text-overflow:ellipsis;white-space:nowrap}.workbench-status small{margin-top:2px;font-size:12px}.workbench-view-host{position:relative;min-height:calc(100vh - 170px);overflow:clip}.execution-loading{position:fixed;z-index:36;right:24px;top:136px;display:flex;align-items:center;gap:6px;padding:8px 10px;border:1px solid var(--border-subtle);border-radius:7px;color:var(--text-secondary);background:color-mix(in srgb, var(--surface-1) 95%, transparent);box-shadow:0 8px 24px color-mix(in srgb, var(--border-strong) 7%, transparent);font-size:12px}.career-view-forward-enter-active,.career-view-forward-leave-active,.career-view-backward-enter-active,.career-view-backward-leave-active,.career-view-fade-enter-active,.career-view-fade-leave-active{transition:opacity var(--cp-motion-view) var(--cp-motion-ease),transform var(--cp-motion-view) var(--cp-motion-ease)}.career-view-forward-enter-from,.career-view-backward-leave-to{opacity:0;transform:translateX(var(--cp-motion-shift))}.career-view-forward-leave-to,.career-view-backward-enter-from{opacity:0;transform:translateX(calc(var(--cp-motion-shift) * -1))}.career-view-fade-enter-from,.career-view-fade-leave-to{opacity:0}.workbench-mobile-nav{display:none}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:1180px) and (min-width:901px){.workbench-tabs{grid-template-columns:repeat(4,minmax(94px,1fr))}.workbench-header{padding-inline:12px}.workbench-header button{padding-inline:8px}.workbench-actions button span{display:none}.workbench-status{padding-inline:12px}}
  @media(max-width:900px){.workbench-shell-header{top:64px}.workbench-header{min-height:62px;padding:6px 10px}.workbench-tabs{display:none}.workbench-actions{display:grid;width:100%;grid-template-columns:repeat(4,minmax(0,1fr));gap:5px;overflow:visible}.workbench-actions button{display:grid;place-items:center;align-content:center;gap:2px;min-width:0;height:48px;padding:3px 2px;font-size:12px}.workbench-actions button span{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.workbench-actions .icon-only{width:auto;margin:0}.workbench-header button em{position:absolute;right:1px;top:1px}.workbench-status{grid-template-columns:repeat(4,minmax(0,1fr));gap:6px;padding:8px 10px;overflow:visible}.workbench-status span,.workbench-status span:first-child{padding:7px 8px;border:0;border-radius:7px;background:var(--surface-2)}.workbench-status .status-goal{grid-column:span 3}.workbench-status .status-version{grid-column:span 1}.workbench-mobile-nav{position:fixed;z-index:50;left:0;right:0;bottom:0;display:grid;grid-template-columns:repeat(5,1fr);height:calc(64px + env(safe-area-inset-bottom));padding-bottom:env(safe-area-inset-bottom);border-top:1px solid var(--border-subtle);background:color-mix(in srgb, var(--surface-1) 97%, transparent);box-shadow:0 -8px 24px rgba(37,55,84,.07);backdrop-filter:blur(12px)}.workbench-mobile-nav button{position:relative;display:grid;place-items:center;align-content:center;gap:3px;min-width:0;border:0;color:var(--text-secondary);background:transparent;font-size:12px}.workbench-mobile-nav button.active{color:var(--color-primary)}.workbench-mobile-nav button.active:after{position:absolute;top:0;width:24px;height:2px;border-radius:0 0 3px 3px;background:var(--color-primary);content:''}.workbench-mobile-nav button em{position:absolute;top:5px;right:calc(50% - 19px);display:grid;place-items:center;width:16px;height:16px;border-radius:50%;color:var(--text-on-primary);background:var(--color-warning);font-size:11px;font-style:normal}.execution-loading{right:10px;top:206px}.workbench-view-host{min-height:calc(100dvh - 222px);padding-bottom:calc(64px + env(safe-area-inset-bottom))}.planning-workbench.is-canvas-view{display:flex;height:100%;min-height:0;flex-direction:column;overflow:hidden}.planning-workbench.is-canvas-view>.workbench-shell-header{top:0;flex:0 0 auto}.planning-workbench.is-canvas-view>.workbench-view-host{display:flex;min-height:0;flex:1;padding-bottom:0}.planning-workbench.is-canvas-view>.workbench-view-host>:deep(*){min-height:0;flex:1}.planning-workbench.is-canvas-view:deep(.career-canvas-shell){min-height:0;flex:1}}
@media(prefers-reduced-motion:reduce){.workbench-header button,.workbench-tab-indicator,.career-view-forward-enter-active,.career-view-forward-leave-active,.career-view-backward-enter-active,.career-view-backward-leave-active,.career-view-fade-enter-active,.career-view-fade-leave-active{transition:none}.career-view-forward-enter-from,.career-view-backward-leave-to,.career-view-forward-leave-to,.career-view-backward-enter-from{transform:none}.spin{animation:none}}
</style>
