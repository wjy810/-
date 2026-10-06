<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  AlertTriangle, ArrowDown, ArrowLeft, ArrowRight, ArrowUp, CalendarDays, Check,
  ChevronDown, ChevronRight, CircleAlert,
  ClipboardCheck, Clock3, FilePlus2, LoaderCircle, Pause, Play, RotateCcw, ShieldCheck,
  Sparkles, Target,
} from 'lucide-vue-next'
import AppModal from '@/shared/ui/AppModal.vue'
import { errorMessage } from '@/shared/api/types'
import {
  addCareerLearningEvidence, saveCareerWeeklyReview, updateCareerLearningPlan, updateCareerPlanTask,
} from '../services/careerPlanningApi'
import type { CareerCanvas, CareerLearningPlan, CareerPlanTask, CareerPlanTaskStatus } from '../types'
import { careerEvidenceStatusLabel } from '../utils/presentation'

const props = defineProps<{
  sessionId: string
  plan?: CareerLearningPlan | null
  canvas: CareerCanvas
  canCreate: boolean
}>()
const emit = defineEmits<{
  updated: [plan: CareerLearningPlan, refreshSession: boolean]
  create: []
  validate: [nodeId: string]
  canvas: []
  error: [message: string]
  notice: [message: string]
}>()

const activeWeek = ref(1)
const pending = ref('')
const evidenceTask = ref<CareerPlanTask | null>(null)
const evidenceTitle = ref('')
const evidenceNote = ref('')
const reviewOpen = ref(true)
const review = reactive({ completedSummary: '', blockers: '', adjustment: '', nextWeekFocus: '' })

watch(() => props.plan?.id, () => {
  if (!props.plan) return
  const incomplete = props.plan.tasks.find(task => task.status !== 'DONE' && task.status !== 'SKIPPED')
  activeWeek.value = incomplete?.week ?? Math.min(props.plan.durationWeeks, 1)
  hydrateReview()
}, { immediate: true })
watch(activeWeek, hydrateReview)

function hydrateReview(): void {
  const value = props.plan?.reviews.find(item => item.week === activeWeek.value)
  review.completedSummary = value?.completedSummary ?? ''
  review.blockers = value?.blockers ?? ''
  review.adjustment = value?.adjustment ?? ''
  review.nextWeekFocus = value?.nextWeekFocus ?? ''
}

const tasks = computed(() => props.plan?.tasks.filter(task => task.week === activeWeek.value)
  .sort((a, b) => a.sortOrder - b.sortOrder) ?? [])
const completedCount = computed(() => props.plan?.tasks.filter(task => task.status === 'DONE').length ?? 0)
const validationCount = computed(() => props.canvas.nodes.filter(node => node.status === 'PENDING_VALIDATION').length)
const progress = computed(() => props.plan?.tasks.length ? Math.round(completedCount.value / props.plan.tasks.length * 100) : 0)
const weekCompleted = computed(() => tasks.value.filter(task => task.status === 'DONE').length)
const focusTitle = computed(() => {
  const task = tasks.value.find(item => item.status === 'IN_PROGRESS') ?? tasks.value.find(item => item.status === 'TODO')
  if (!task?.nodeId) return '按计划推进本周任务'
  return nodeTitle(task.nodeId)
})

function nodeTitle(nodeId?: string | null): string {
  return props.canvas.nodes.find(node => node.logicalNodeId === nodeId)?.title ?? '计划复盘'
}
function nodeNeedsValidation(nodeId?: string | null): boolean {
  return props.canvas.nodes.find(node => node.logicalNodeId === nodeId)?.status === 'PENDING_VALIDATION'
}
function statusLabel(value: CareerPlanTaskStatus): string {
  return ({ TODO: '计划中', IN_PROGRESS: '进行中', BLOCKED: '受阻', DONE: '已完成', SKIPPED: '已顺延' } as const)[value]
}
function minutesLabel(value: number): string {
  return value >= 60 ? `${Math.floor(value / 60)} 小时${value % 60 ? ` ${value % 60} 分` : ''}` : `${value} 分钟`
}
function taskEvidence(task: CareerPlanTask) {
  return props.plan?.evidences.filter(value => value.taskId === task.id) ?? []
}

async function setTaskStatus(task: CareerPlanTask, status: CareerPlanTaskStatus): Promise<void> {
  if (!props.plan) return
  if (status === 'DONE' && task.evidenceRequired && !taskEvidence(task).length) {
    openEvidence(task)
    emit('notice', '该任务需要先关联证据，再标记完成。')
    return
  }
  pending.value = `task:${task.id}`
  try {
    const value = await updateCareerPlanTask(props.sessionId, props.plan.id, task.id, { status, expectedVersion: task.version })
    emit('updated', value, Boolean(task.nodeId))
    emit('notice', status === 'DONE' ? '任务已完成，对应能力进入待验证状态。' : '任务状态已更新。')
  } catch (reason) { emit('error', errorMessage(reason, '任务状态更新失败')) }
  finally { pending.value = '' }
}

async function repositionTask(task: CareerPlanTask, targetWeek: number, sortOrder?: number): Promise<void> {
  if (!props.plan || targetWeek < 1 || targetWeek > props.plan.durationWeeks) return
  pending.value = `task:${task.id}`
  try {
    const value = await updateCareerPlanTask(props.sessionId, props.plan.id, task.id, {
      targetWeek, ...(sortOrder == null ? {} : { sortOrder }), expectedVersion: task.version,
    })
    emit('updated', value, false)
    activeWeek.value = targetWeek
    emit('notice', targetWeek === task.week ? '任务顺序已更新。' : `任务已移到第 ${targetWeek} 周。`)
  } catch (reason) { emit('error', errorMessage(reason, '任务位置更新失败')) }
  finally { pending.value = '' }
}

function moveWithinWeek(task: CareerPlanTask, direction: -1 | 1): void {
  const index = tasks.value.findIndex(item => item.id === task.id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= tasks.value.length) return
  void repositionTask(task, activeWeek.value, target)
}

function openEvidence(task: CareerPlanTask): void {
  evidenceTask.value = task
  evidenceTitle.value = `${task.title} - 学习证据`
  evidenceNote.value = ''
}

async function addEvidence(): Promise<void> {
  if (!props.plan || !evidenceTask.value?.nodeId || !evidenceTitle.value.trim()) return
  pending.value = 'evidence'
  try {
    const value = await addCareerLearningEvidence(props.sessionId, props.plan.id, {
      taskId: evidenceTask.value.id, nodeId: evidenceTask.value.nodeId, sourceType: 'USER_NOTE',
      title: evidenceTitle.value.trim(), note: evidenceNote.value.trim(), expectedTaskVersion: evidenceTask.value.version,
    })
    emit('updated', value, false)
    evidenceTask.value = null
    emit('notice', '能力证据已关联，可以继续完成任务或发起验证。')
  } catch (reason) { emit('error', errorMessage(reason, '证据关联失败')) }
  finally { pending.value = '' }
}

async function saveReview(): Promise<void> {
  if (!props.plan) return
  pending.value = 'review'
  try {
    const value = await saveCareerWeeklyReview(props.sessionId, props.plan.id, activeWeek.value, { ...review })
    emit('updated', value, false)
    emit('notice', `第 ${activeWeek.value} 周复盘已保存。`)
  } catch (reason) { emit('error', errorMessage(reason, '周复盘保存失败')) }
  finally { pending.value = '' }
}

async function setPlanStatus(status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED'): Promise<void> {
  if (!props.plan) return
  pending.value = `plan:${status}`
  try {
    const value = await updateCareerLearningPlan(props.sessionId, props.plan.id, status, props.plan.version)
    emit('updated', value, false)
    emit('notice', status === 'PAUSED' ? '计划已暂停。' : status === 'ACTIVE' ? '计划已恢复。' : '计划状态已更新。')
  } catch (reason) { emit('error', errorMessage(reason, '计划状态更新失败')) }
  finally { pending.value = '' }
}
</script>

<template>
  <div class="career-learning-plan-view">
  <section v-if="!plan" class="plan-empty">
    <span><CalendarDays :size="34" /></span><h2>还没有行动计划</h2><p>{{ canCreate ? '从当前能力画布生成 4、8、12 周或自定义周期的任务安排。' : '先生成包含技能、知识或任务节点的完整能力树，再制定学习计划。' }}</p><button type="button" :disabled="!canCreate" :title="canCreate ? '生成学习计划' : '请先生成完整能力树'" @click="emit('create')"><Sparkles :size="17" />{{ canCreate ? '生成学习计划' : '先生成完整能力树' }}</button>
  </section>

  <section v-else class="learning-plan">
    <header class="plan-summary">
      <div><span><CalendarDays :size="22" /></span><p><strong>第 {{ activeWeek }} 周</strong><small>/ 共 {{ plan.durationWeeks }} 周</small></p></div>
      <dl><div><dt><ClipboardCheck :size="18" /></dt><dd><small>已完成</small><strong>{{ completedCount }} / {{ plan.tasks.length }}</strong></dd></div><div><dt class="validation"><ShieldCheck :size="18" /></dt><dd><small>待验证</small><strong>{{ validationCount }}</strong></dd></div><div><dt><Target :size="18" /></dt><dd><small>当前重点</small><strong>{{ focusTitle }}</strong></dd></div></dl>
      <div class="plan-actions"><button v-if="plan.status === 'ACTIVE'" type="button" :disabled="pending === 'plan:PAUSED'" @click="setPlanStatus('PAUSED')"><Pause :size="15" />暂停</button><button v-else-if="plan.status === 'PAUSED'" type="button" @click="setPlanStatus('ACTIVE')"><Play :size="15" />恢复</button><button type="button" @click="emit('create')"><RotateCcw :size="15" />重新规划</button></div>
    </header>

    <div class="plan-progress"><span :style="{ width: `${progress}%` }"></span></div>
    <nav class="week-tabs" aria-label="计划周次"><i class="week-tab-indicator" aria-hidden="true" :style="{ transform: `translateX(${(activeWeek - 1) * 96}px)` }" /><button v-for="week in plan.durationWeeks" :key="week" type="button" :aria-current="activeWeek === week ? 'step' : undefined" :class="{ active: activeWeek === week }" @click="activeWeek = week">第 {{ week }} 周<small>{{ plan.tasks.filter(task => task.week === week && task.status === 'DONE').length }}/{{ plan.tasks.filter(task => task.week === week).length }}</small></button></nav>

    <div class="plan-content">
      <main>
        <header class="week-goal"><div><h2>本周目标</h2><p>围绕 {{ focusTitle }} 完成任务并沉淀可复核证据。</p></div><em>完成 {{ weekCompleted }} / {{ tasks.length }}</em></header>
        <TransitionGroup name="plan-task" tag="div" class="task-list">
          <article v-for="(task,index) in tasks" :key="task.id" :class="`status-${task.status.toLowerCase()}`">
            <button class="task-check" type="button" :disabled="pending === `task:${task.id}`" :aria-label="task.status === 'DONE' ? '恢复任务' : '完成任务'" @click="setTaskStatus(task, task.status === 'DONE' ? 'TODO' : 'DONE')"><LoaderCircle v-if="pending === `task:${task.id}`" class="spin" :size="17" /><Check v-else-if="task.status === 'DONE'" :size="17" /><span v-else>{{ index + 1 }}</span></button>
            <div class="task-copy"><header><strong>{{ task.title }}</strong><em :class="task.status.toLowerCase()">{{ statusLabel(task.status) }}</em></header><p>{{ task.description || '按节点掌握标准完成学习并沉淀证据。' }}</p><footer><span>{{ nodeTitle(task.nodeId) }}</span><span><Clock3 :size="13" />{{ minutesLabel(task.estimatedMinutes) }}</span><span><CalendarDays :size="13" />{{ task.dueDate }}</span></footer></div>
            <div class="task-evidence">
              <div class="task-order" aria-label="调整任务位置">
                <button type="button" aria-label="移到上一周" title="移到上一周" :disabled="activeWeek <= 1 || Boolean(pending)" @click="repositionTask(task, activeWeek - 1)"><ArrowLeft :size="14" /></button>
                <button type="button" aria-label="上移任务" title="上移任务" :disabled="index === 0 || Boolean(pending)" @click="moveWithinWeek(task, -1)"><ArrowUp :size="14" /></button>
                <button type="button" aria-label="下移任务" title="下移任务" :disabled="index === tasks.length - 1 || Boolean(pending)" @click="moveWithinWeek(task, 1)"><ArrowDown :size="14" /></button>
                <button type="button" aria-label="移到下一周" title="移到下一周" :disabled="activeWeek >= plan.durationWeeks || Boolean(pending)" @click="repositionTask(task, activeWeek + 1)"><ArrowRight :size="14" /></button>
              </div>
              <button type="button" @click="openEvidence(task)"><FilePlus2 :size="15" />{{ taskEvidence(task).length ? `${taskEvidence(task).length} 项证据` : '添加证据' }}</button>
              <button v-if="task.nodeId && nodeNeedsValidation(task.nodeId)" type="button" @click="emit('validate', task.nodeId)">去验证<ChevronRight :size="14" /></button><button v-else-if="task.status === 'TODO'" type="button" @click="setTaskStatus(task, 'IN_PROGRESS')">开始</button>
            </div>
          </article>
          <p v-if="!tasks.length" key="empty" class="week-empty">本周暂无任务，可切换其他周次查看。</p>
        </TransitionGroup>
        <button class="next-week" type="button" :disabled="activeWeek >= plan.durationWeeks" @click="activeWeek++">下周预览（第 {{ Math.min(activeWeek + 1, plan.durationWeeks) }} 周）<ChevronDown :size="16" /></button>
      </main>

      <aside>
        <section class="evidence-summary"><h3>本周证据</h3><button v-for="item in plan.evidences.filter(value => tasks.some(task => task.id === value.taskId))" :key="item.id" type="button"><FilePlus2 :size="17" /><span><strong>{{ item.title }}</strong><small>{{ careerEvidenceStatusLabel(item.verificationStatus) }}</small></span><ChevronRight :size="15" /></button><p v-if="!plan.evidences.some(value => tasks.some(task => task.id === value.taskId))">完成任务前添加代码、报告、作品或真实说明。</p></section>
        <section class="weekly-review"><header><h3>本周复盘</h3><button type="button" :aria-expanded="reviewOpen" aria-controls="career-weekly-review" @click="reviewOpen = !reviewOpen"><ChevronDown :size="16" :class="{ rotated: !reviewOpen }" /></button></header><Transition name="review-expand"><div v-if="reviewOpen" id="career-weekly-review"><label>本周收获<textarea v-model="review.completedSummary" rows="3" maxlength="2000" placeholder="完成了什么，学会了什么"></textarea></label><label>阻塞问题<textarea v-model="review.blockers" rows="2" maxlength="2000" placeholder="遇到的问题与原因"></textarea></label><label>调整与下周重点<textarea v-model="review.adjustment" rows="2" maxlength="2000" placeholder="如何调整后续节奏"></textarea></label><input v-model="review.nextWeekFocus" maxlength="1000" placeholder="下周最重要的一件事"><button type="button" :disabled="pending === 'review'" @click="saveReview"><LoaderCircle v-if="pending === 'review'" class="spin" :size="16" />提交本周复盘</button></div></Transition></section>
        <p class="validation-rule"><AlertTriangle :size="17" /><span><strong>验证规则提醒</strong>任务完成只会进入待验证，AI 评估通过且由你确认后，节点才标记为已掌握。</span></p>
      </aside>
    </div>
  </section>

  <AppModal :open="Boolean(evidenceTask)" title="添加能力证据" :width="560" mobile-sheet @close="evidenceTask = null">
    <div class="evidence-dialog"><p><CircleAlert :size="16" />首版保存结构化证据说明，不会把未选择的资料文件发送给 AI。</p><label>证据名称<input v-model="evidenceTitle" maxlength="255" placeholder="例如：订单接口测试报告"></label><label>补充说明<textarea v-model="evidenceNote" rows="5" maxlength="2000" placeholder="写明你完成的工作、验证方式和可复核结果"></textarea></label></div>
    <template #footer><button class="dialog-cancel" type="button" @click="evidenceTask = null">取消</button><button class="dialog-primary" type="button" :disabled="pending === 'evidence' || !evidenceTitle.trim()" @click="addEvidence"><LoaderCircle v-if="pending === 'evidence'" class="spin" :size="16" /><FilePlus2 v-else :size="16" />保存并关联</button></template>
  </AppModal>
  </div>
</template>

<style scoped>
.career-learning-plan-view{min-width:0}
.plan-empty{display:grid;place-items:center;align-content:center;gap:10px;min-height:560px;text-align:center;color:var(--text-secondary);background:var(--surface-1)}.plan-empty>span{display:grid;place-items:center;width:64px;height:64px;border-radius:18px;color:var(--color-primary);background:var(--surface-2)}.plan-empty h2,.plan-empty p{margin:0}.plan-empty h2{color:var(--text-primary)}.plan-empty button,.dialog-primary{display:flex;align-items:center;gap:7px;min-height:42px;margin-top:5px;padding:0 18px;border:0;border-radius:8px;color:var(--text-on-primary);background:var(--color-primary)}.learning-plan{display:grid;grid-template-rows:auto 3px auto 1fr;min-height:calc(100vh - 144px);background:var(--bg);color:var(--text-primary)}.plan-summary{display:flex;align-items:center;gap:24px;padding:16px 20px;background:var(--surface-1);border-bottom:1px solid var(--border-subtle)}.plan-summary>div:first-child{display:flex;align-items:center;gap:9px}.plan-summary>div:first-child>span{display:grid;place-items:center;width:40px;height:40px;border-radius:10px;color:var(--color-primary);background:var(--surface-2)}.plan-summary p{display:grid;margin:0}.plan-summary p small{color:var(--text-secondary)}.plan-summary dl{display:flex;align-items:center;gap:30px;flex:1;margin:0}.plan-summary dl div{display:flex;align-items:center;gap:8px}.plan-summary dt{display:grid;place-items:center;width:34px;height:34px;border-radius:50%;color:var(--color-success-text);background:var(--surface-2)}.plan-summary dt.validation{color:var(--color-warning-text);background:var(--surface-2)}.plan-summary dd{display:grid;margin:0}.plan-summary dd small{color:var(--text-secondary)}.plan-summary dd strong{max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.plan-actions{display:flex;gap:7px}.plan-actions button{display:flex;align-items:center;gap:5px;height:36px;padding:0 10px;border:1px solid var(--border-subtle);border-radius:7px;color:var(--text-secondary);background:var(--surface-1)}.plan-progress{height:3px;background:var(--surface-3)}.plan-progress span{display:block;height:100%;background:var(--color-primary);transition:width .25s ease}.week-tabs{display:flex;gap:4px;padding:9px 16px;overflow:auto;background:var(--surface-1);border-bottom:1px solid var(--border-subtle)}.week-tabs button{display:flex;align-items:center;gap:6px;flex:0 0 auto;height:34px;padding:0 11px;border:0;border-radius:7px;color:var(--text-secondary);background:transparent}.week-tabs button.active{color:var(--color-primary);background:var(--surface-2)}.week-tabs small{font-size:12px}.plan-content{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:14px;padding:14px 18px;min-height:0}.plan-content>main,.plan-content>aside>section,.validation-rule{border:1px solid var(--border-subtle);border-radius:10px;background:var(--surface-1)}.week-goal{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-bottom:1px solid var(--border-subtle)}.week-goal h2,.week-goal p{margin:0}.week-goal h2{font-size:18px}.week-goal p{margin-top:4px;color:var(--text-secondary)}.week-goal em{padding:5px 8px;border-radius:6px;color:var(--color-primary);background:var(--surface-2);font-size:12px;font-style:normal}.task-list{padding:0 16px}.task-list article{display:grid;grid-template-columns:38px minmax(0,1fr) auto;gap:10px;align-items:center;padding:15px 0;border-bottom:1px solid var(--border-subtle)}.task-list article:last-child{border-bottom:0}.task-list article.status-in_progress{margin:0 -8px;padding:15px 8px;border:1px solid var(--color-primary-border);border-radius:8px;background:var(--surface-1)}.task-check{display:grid;place-items:center;width:28px;height:28px;border:1px solid var(--border-default);border-radius:7px;color:var(--text-secondary);background:var(--surface-1)}.status-done .task-check{color:var(--text-on-primary);border-color:var(--color-success);background:var(--color-success)}.task-copy{min-width:0}.task-copy header{display:flex;align-items:center;gap:8px}.task-copy header em{padding:3px 6px;border-radius:5px;color:var(--text-secondary);background:var(--surface-2);font-size:12px;font-style:normal}.task-copy header em.done{color:var(--color-success-text);background:var(--surface-2)}.task-copy header em.in_progress{color:var(--color-primary);background:var(--surface-2)}.task-copy header em.blocked{color:var(--color-warning-text);background:var(--surface-2)}.task-copy p{margin:5px 0;color:var(--text-secondary);font-size:13px}.task-copy footer{display:flex;flex-wrap:wrap;gap:8px}.task-copy footer span{display:flex;align-items:center;gap:4px;padding:3px 6px;border-radius:5px;color:var(--text-secondary);background:var(--surface-2);font-size:12px}.task-evidence{display:grid;gap:6px;justify-items:end}.task-evidence button{display:flex;align-items:center;gap:5px;height:32px;padding:0 9px;border:1px solid var(--border-subtle);border-radius:6px;color:var(--color-primary);background:var(--surface-1);font-size:12px}.week-empty{padding:32px;text-align:center;color:var(--text-secondary)}.next-week{display:flex;align-items:center;justify-content:space-between;width:calc(100% - 32px);height:42px;margin:10px 16px 16px;padding:0 12px;border:1px solid var(--border-subtle);border-radius:7px;color:var(--text-secondary);background:var(--surface-1)}.plan-content>aside{display:grid;align-content:start;gap:12px}.plan-content>aside section{padding:14px}.plan-content h3{margin:0 0 10px;font-size:15px}.evidence-summary>button{display:grid;grid-template-columns:28px 1fr auto;align-items:center;gap:7px;width:100%;min-height:48px;margin-top:7px;padding:7px;border:1px solid var(--border-subtle);border-radius:7px;text-align:left;color:var(--color-primary);background:var(--surface-1)}.evidence-summary>button span{display:grid;color:var(--text-primary)}.evidence-summary>button small{color:var(--text-secondary)}.evidence-summary>p{margin:0;color:var(--text-secondary);font-size:12px;line-height:1.6}.weekly-review>header{display:flex;align-items:center;justify-content:space-between}.weekly-review>header button{border:0;background:transparent}.weekly-review>div,.weekly-review label{display:grid;gap:7px}.weekly-review>div{gap:9px}.weekly-review label{color:var(--text-secondary);font-size:12px}.weekly-review textarea,.weekly-review input,.evidence-dialog input,.evidence-dialog textarea{width:100%;padding:9px;border:1px solid var(--border-subtle);border-radius:7px;outline:0;resize:vertical}.weekly-review>div>button{display:flex;align-items:center;justify-content:center;gap:6px;height:38px;border:0;border-radius:7px;color:var(--text-on-primary);background:var(--color-primary)}.validation-rule{display:flex;gap:8px;margin:0;padding:12px;color:var(--color-warning-text);background:var(--surface-2)!important;font-size:12px;line-height:1.55}.validation-rule span{display:grid}.rotated{transform:rotate(-90deg)}.evidence-dialog{display:grid;gap:14px}.evidence-dialog p{display:flex;gap:7px;margin:0;padding:10px;border-radius:7px;color:var(--color-warning-text);background:var(--surface-2)}.evidence-dialog label{display:grid;gap:7px;color:var(--text-secondary)}.dialog-cancel,.dialog-primary{min-height:40px;padding:0 16px;border:1px solid var(--border-subtle);border-radius:7px;background:var(--surface-1)}.dialog-primary{margin:0;border-color:var(--color-primary)}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:900px){.learning-plan{min-height:calc(100vh - 152px)}.plan-summary{display:grid;grid-template-columns:1fr auto;gap:10px;padding:14px 12px}.plan-summary dl{grid-column:1/-1;display:grid;grid-template-columns:1fr 1fr 1.4fr;gap:8px}.plan-summary dl div{padding:8px;border-radius:8px;background:var(--bg)}.plan-summary dt{width:28px;height:28px}.plan-summary dd strong{font-size:13px}.plan-actions{grid-column:2}.plan-actions button{font-size:0;width:36px;padding:0;justify-content:center}.plan-content{grid-template-columns:1fr;padding:10px 10px 96px}.task-list{padding:0 10px}.task-list article{grid-template-columns:32px minmax(0,1fr)}.task-evidence{grid-column:2;display:flex;flex-wrap:wrap;justify-self:start;justify-items:start}.week-goal{align-items:flex-start}.week-tabs{padding-left:10px}.plan-content>aside{grid-row:auto}.weekly-review textarea{min-height:70px}}
.task-order{display:flex;gap:3px}.task-evidence .task-order button{width:28px;padding:0;justify-content:center;color:var(--color-primary-text);background:var(--surface-1)}.task-evidence .task-order button:disabled{opacity:.32;cursor:not-allowed}
.plan-empty button:disabled{color:var(--text-secondary);background:var(--surface-3);cursor:not-allowed}
.plan-empty>span,.plan-summary>div:first-child>span{border-radius:8px}.plan-content>main,.plan-content>aside>section,.validation-rule{border-radius:8px}.week-tabs{position:relative;overflow-x:auto;overflow-y:hidden;scrollbar-width:none}.week-tabs::-webkit-scrollbar{display:none}.week-tabs button{position:relative;z-index:1;justify-content:center;flex-basis:92px;width:92px;padding-inline:7px;background:transparent;transition:color .18s ease}.week-tabs button.active{background:transparent}.week-tab-indicator{position:absolute;left:16px;top:9px;width:92px;height:34px;border-radius:7px;background:var(--surface-2);transition:transform .22s cubic-bezier(.2,.8,.2,1)}.weekly-review>header button{display:grid;place-items:center;width:34px;height:34px;border-radius:7px;color:var(--text-secondary)}.weekly-review>header button:hover{background:var(--surface-2)}.weekly-review>div{overflow:hidden}.weekly-review svg{transition:transform .18s ease}.plan-task-move,.plan-task-enter-active,.plan-task-leave-active{transition:transform .22s cubic-bezier(.2,.8,.2,1),opacity .18s ease}.plan-task-enter-from,.plan-task-leave-to{opacity:0;transform:translateY(8px)}.plan-task-leave-active{position:absolute}.review-expand-enter-active,.review-expand-leave-active{transition:opacity .18s ease,transform .22s cubic-bezier(.2,.8,.2,1)}.review-expand-enter-from,.review-expand-leave-to{opacity:0;transform:translateY(-6px)}button:focus-visible,textarea:focus-visible,input:focus-visible{outline:2px solid color-mix(in srgb, var(--color-primary) 42%, transparent);outline-offset:2px}
@media(max-width:900px){.week-tab-indicator{left:10px}}
@media(prefers-reduced-motion:reduce){.plan-progress span,.week-tab-indicator,.week-tabs button,.weekly-review svg,.plan-task-move,.plan-task-enter-active,.plan-task-leave-active,.review-expand-enter-active,.review-expand-leave-active{transition:none}.plan-task-enter-from,.plan-task-leave-to,.review-expand-enter-from,.review-expand-leave-to{transform:none}.spin{animation:none}}
</style>
