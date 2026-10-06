<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  BookOpen, Bot, BriefcaseBusiness, ChevronDown,
  ChevronRight, ChevronsDown, ChevronsUp, Code2, FileCheck2, GitBranch,
  GitMerge, Layers3, ListChecks, ListTree, LoaderCircle, LockKeyhole, LockOpen,
  Hand, Maximize2, MoreHorizontal, Network, Plus, Redo2, ShieldCheck, Sparkles,
  SquareDashedMousePointer,
  Target, Trash2, Undo2, X,
} from 'lucide-vue-next'
import { Handle, MarkerType, Position, SelectionMode, VueFlow, useVueFlow } from '@vue-flow/core'
import type { Edge, Node, NodeChange, NodeDragEvent, NodeMouseEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'
import '@vue-flow/controls/dist/style.css'
import AppSelect from '@/shared/ui/AppSelect.vue'
import { errorMessage } from '@/shared/api/types'
import { fetchTask, isOpenTask, type TaskView } from '@/shared/api/task'
import { isAbortError, pollTask } from '@/shared/lib/pollTask'
import {
  addCareerCanvasRelation, createCareerCanvasNode, deleteCareerCanvasNode,
  fetchCareerCanvasProposal, fetchCareerPlanningSession, mergeCareerCanvasNodes, splitCareerCanvasNode,
  restoreCareerCanvasVersion, startCareerCanvasGeneration, startCareerNodeInference, updateCareerCanvasNode,
  updateCareerCanvasNodes,
} from '../services/careerPlanningApi'
import type {
  CanvasNode, CanvasNodeStatus, CanvasNodeType, CareerCanvas, CareerCanvasGenerationScale,
  CareerCanvasProposal, CareerInferenceDepth, CareerInferenceDirection,
} from '../types'
import {
  buildCanvasTree, canvasStatusCounts, layoutCanvas, parentMap, virtualCanvasRange,
  visibleCanvasNodeIds,
} from '../utils/canvas'
import { branchGrowth, confirmedMastery } from '../utils/canvasMotion'

const props = defineProps<{
  sessionId: string
  canvas: CareerCanvas
  goalTitle: string
  aiConsent: boolean
  pendingProposal?: CareerCanvasProposal | null
}>()

const emit = defineEmits<{
  updated: [canvas: CareerCanvas]
  error: [message: string]
  notice: [message: string]
  proposalGenerated: [proposal: CareerCanvasProposal]
  reviewProposal: [proposal: CareerCanvasProposal]
  validate: [nodeIds: string[]]
}>()

type CanvasFlowData = { item: CanvasNode }
type MobileRow = { node: CanvasNode; level: number; childCount: number; expanded: boolean }
type CanvasHistoryState = { physicalVersion: number; undo: number[]; redo: number[] }

const currentCanvas = ref(props.canvas)
const query = ref('')
const statusFilter = ref<CanvasNodeStatus | 'ALL'>('ALL')
const focusNodeId = ref('')
const selectedNodeId = ref('')
const selectedNodeIds = ref<Set<string>>(new Set())
const pending = ref('')
const editorMode = ref<'VIEW' | 'EDIT' | 'ADD' | 'SPLIT' | 'MERGE' | 'INFERENCE'>('VIEW')
const deleteArmed = ref(false)
const prerequisiteId = ref('')
const expandedIds = ref<Set<string>>(new Set())
const collapsedIds = ref<Set<string>>(new Set())
const undoVersions = ref<number[]>([])
const redoVersions = ref<number[]>([])
const generationTask = ref<TaskView | null>(null)
const generationPolling = ref(false)
const generationScale = ref<CareerCanvasGenerationScale>('STANDARD')
const inferenceDirection = ref<CareerInferenceDirection>('DOWNWARD')
const inferenceDepth = ref<CareerInferenceDepth>('ONE_LEVEL')
const inferenceInstruction = ref('')
const inferenceTask = ref<TaskView | null>(null)
const inferencePolling = ref(false)
let splitSequence = 2
const splitItems = ref<Array<{ id: number; type: Exclude<CanvasNodeType, 'CAREER'>; title: string; summary: string }>>([])
const mergeNodeIds = ref<string[]>([])
const mergeTitle = ref('')
const mergeSummary = ref('')
const batchStatus = ref<CanvasNodeStatus | ''>('')
const mobileSelectionMode = ref(false)
const canvasTool = ref<'PAN' | 'MARQUEE'>('PAN')
const mobileScrollTop = ref(0)
const mobileViewportHeight = ref(560)
const mobileTreeViewport = ref<HTMLElement | null>(null)
let generationController: AbortController | null = null
let inferenceController: AbortController | null = null
let marqueeSelecting = false
let fitFrame = 0
const { fitView } = useVueFlow()
const canvasStage = ref<HTMLElement | null>(null)
let motionMedia: MediaQueryList | null = null
let activeAnimations: Animation[] = []
let animationRevision = 0

function cancelCanvasMotion(): void {
  animationRevision++
  activeAnimations.forEach(animation => animation.cancel())
  activeAnimations = []
}

async function animateCanvasChange(previous: ReadonlySet<string>, completed = new Set<string>()): Promise<void> {
  cancelCanvasMotion()
  if (motionMedia?.matches || !matchMedia('(min-width: 901px)').matches) return
  const revision = animationRevision
  const growth = branchGrowth(previous, desktopVisibleIds.value, displayedCanvas.value.relations)
  await nextTick()
  if (revision !== animationRevision || !canvasStage.value) return
  const animate = (element: Element | null, keyframes: Keyframe[], delay = 0, duration = 240) => {
    if (!element || typeof element.animate !== 'function') return
    activeAnimations.push(element.animate(keyframes, { duration, delay, easing: 'cubic-bezier(.2,.7,.3,1)', fill: 'backwards' }))
  }
  for (const element of canvasStage.value.querySelectorAll<HTMLElement>('.vue-flow__node')) {
    const id = element.dataset.id ?? ''
    const delay = growth.nodes.get(id)
    if (delay != null) animate(element.querySelector('.flow-card'), [{ opacity: 0, transform: 'translateX(6px)' }, { opacity: 1, transform: 'translateX(0)' }], delay + 40, 240)
    if (completed.has(id)) animate(element.querySelector('.flow-card'), [{ boxShadow: '0 0 0 0 #15966600' }, { boxShadow: '0 0 0 4px #15966638', offset: .45 }, { boxShadow: '0 0 0 0 #15966600' }], 0, 480)
  }
  for (const relation of displayedCanvas.value.relations) {
    const group = Array.from(canvasStage.value.querySelectorAll<SVGElement>('.vue-flow__edge')).find(element => element.dataset.id === relation.id)
    const path = group?.querySelector<SVGPathElement>('.vue-flow__edge-path')
    const delay = growth.edges.get(relation.id)
    if (path && delay != null) {
      const length = path.getTotalLength()
      // Existing SVG runs child -> parent: negative dash offset reveals from the parent end.
      animate(path, [{ strokeDasharray: `${length} ${length}`, strokeDashoffset: -length }, { strokeDasharray: `${length} ${length}`, strokeDashoffset: 0 }], delay, 240)
    }
    if (relation.type === 'TREE_PARENT' && completed.has(relation.fromNodeId)) animate(path ?? null, [{ stroke: '#8eaae9', strokeWidth: 1.7 }, { stroke: '#159666', strokeWidth: 3, offset: .45 }, { stroke: '#8eaae9', strokeWidth: 1.7 }], 0, 480)
  }
}

const form = reactive({
  type: 'SKILL' as Exclude<CanvasNodeType, 'CAREER'>,
  title: '',
  status: 'NOT_STARTED' as Exclude<CanvasNodeStatus, 'MASTERED'>,
  parentNodeId: '',
  summary: '',
  importance: 'MEDIUM',
  estimatedHours: '',
  learningContents: '',
  masteryCriteria: '',
  notes: '',
  locked: false,
})

const statusOptions: Array<{ value: CanvasNodeStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: '全部状态' },
  { value: 'NOT_STARTED', label: '未开始' },
  { value: 'PLANNED', label: '计划中' },
  { value: 'LEARNING', label: '学习中' },
  { value: 'PENDING_VALIDATION', label: '待验证' },
  { value: 'MASTERED', label: '已掌握' },
  { value: 'PAUSED', label: '已暂停' },
]

const editableStatusOptions = statusOptions.filter(option => option.value !== 'ALL' && option.value !== 'MASTERED')
const nodeTypeOptions = [
  { value: 'DOMAIN', label: '能力域' }, { value: 'SKILL', label: '技能' },
  { value: 'KNOWLEDGE', label: '知识点' }, { value: 'TASK', label: '行动任务' },
  { value: 'EVIDENCE', label: '能力证据' },
] as const
const generationScales: Array<{
  value: CareerCanvasGenerationScale; label: string; range: string; copy: string
}> = [
  { value: 'COMPACT', label: '精简', range: '14–28 节点', copy: '快速覆盖关键能力与行动任务' },
  { value: 'STANDARD', label: '标准', range: '22–44 节点', copy: '平衡能力域、任务与验证证据' },
  { value: 'DEEP', label: '深入', range: '34–60 节点', copy: '展开完整分支和更多前置能力' },
]
const inferenceDirections: Array<{
  value: CareerInferenceDirection; label: string; copy: string
}> = [
  { value: 'DOWNWARD', label: '向下扩展', copy: '继续拆解当前节点的后续能力与任务' },
  { value: 'PREREQUISITES', label: '补齐前置', copy: '补充掌握当前能力前需要的知识或技能' },
  { value: 'SIBLINGS', label: '横向扩展', copy: '在同一能力域中补充同级节点' },
  { value: 'TARGET_GAP', label: '岗位缺口', copy: '围绕已确认目标职业检查遗漏能力' },
]

watch(() => props.canvas, value => {
  const previous = currentCanvas.value
  if (previous.version !== value.version && previous.graphHash !== value.graphHash) {
    pushHistoryVersion(undoVersions.value, previous.version)
    redoVersions.value = []
  }
  currentCanvas.value = value
  const validIds = new Set(value.nodes.map(node => node.logicalNodeId))
  selectedNodeIds.value = new Set(Array.from(selectedNodeIds.value).filter(id => validIds.has(id)))
  syncExpanded(value)
  persistCanvasHistory(value.version)
}, { deep: true })

watch(() => props.sessionId, () => {
  cancelCanvasMotion()
  generationController?.abort()
  inferenceController?.abort()
  generationTask.value = null
  inferenceTask.value = null
  currentCanvas.value = props.canvas
  selectedNodeId.value = ''
  selectedNodeIds.value = new Set()
  mobileSelectionMode.value = false
  initializeExpanded(props.canvas)
  collapsedIds.value = new Set()
  restoreCanvasHistory()
  void restoreGenerationTask()
  void restoreInferenceTask()
  void restoreMobileScroll()
})

onMounted(() => {
  motionMedia = matchMedia('(prefers-reduced-motion: reduce)')
  motionMedia.addEventListener('change', cancelCanvasMotion)
  window.addEventListener('keydown', onCanvasShortcut)
  void restoreGenerationTask()
  void restoreInferenceTask()
  void restoreMobileScroll()
})
onBeforeUnmount(() => {
  cancelCanvasMotion()
  motionMedia?.removeEventListener('change', cancelCanvasMotion)
  generationController?.abort()
  inferenceController?.abort()
  window.cancelAnimationFrame(fitFrame)
  window.removeEventListener('keydown', onCanvasShortcut)
})

function initializeExpanded(canvas: CareerCanvas): void {
  const next = new Set<string>()
  canvas.nodes.filter(node => node.type === 'CAREER' || node.type === 'DOMAIN').forEach(node => next.add(node.logicalNodeId))
  expandedIds.value = next
}
initializeExpanded(props.canvas)
restoreCanvasHistory()

function canvasHistoryStorageKey(): string {
  return `career-planning:canvas-history:${props.sessionId}`
}

function pushHistoryVersion(stack: number[], version: number): void {
  if (version < 1 || stack.at(-1) === version) return
  stack.push(version)
  if (stack.length > 60) stack.splice(0, stack.length - 60)
}

function persistCanvasHistory(physicalVersion = currentCanvas.value.version): void {
  if (typeof window === 'undefined') return
  const state: CanvasHistoryState = {
    physicalVersion,
    undo: [...undoVersions.value],
    redo: [...redoVersions.value],
  }
  window.sessionStorage.setItem(canvasHistoryStorageKey(), JSON.stringify(state))
}

function restoreCanvasHistory(): void {
  undoVersions.value = []
  redoVersions.value = []
  if (typeof window === 'undefined') return
  try {
    const raw = window.sessionStorage.getItem(canvasHistoryStorageKey())
    if (!raw) return
    const state = JSON.parse(raw) as Partial<CanvasHistoryState>
    if (state.physicalVersion !== currentCanvas.value.version) {
      window.sessionStorage.removeItem(canvasHistoryStorageKey())
      return
    }
    undoVersions.value = Array.isArray(state.undo) ? state.undo.filter(Number.isInteger).slice(-60) : []
    redoVersions.value = Array.isArray(state.redo) ? state.redo.filter(Number.isInteger).slice(-60) : []
  } catch {
    window.sessionStorage.removeItem(canvasHistoryStorageKey())
  }
}

function recordCanvasMutation(before: CareerCanvas, after: CareerCanvas): void {
  if (before.graphHash !== after.graphHash) {
    pushHistoryVersion(undoVersions.value, before.version)
    redoVersions.value = []
  }
  persistCanvasHistory(after.version)
}

function onCanvasShortcut(event: KeyboardEvent): void {
  const target = event.target
  if (target instanceof Element && target.matches('input, textarea, select, [contenteditable="true"]')) return
  if (event.key === 'Escape') {
    clearNodeSelection()
    return
  }
  if (!(event.ctrlKey || event.metaKey) && !event.altKey) {
    if (event.key.toLowerCase() === 'h') {
      event.preventDefault()
      canvasTool.value = 'PAN'
    } else if (event.key.toLowerCase() === 'v') {
      event.preventDefault()
      canvasTool.value = 'MARQUEE'
    }
    return
  }
  if (!(event.ctrlKey || event.metaKey) || event.altKey) return
  if (event.key.toLowerCase() === 'z') {
    event.preventDefault()
    void (event.shiftKey ? redoCanvas() : undoCanvas())
  } else if (event.key.toLowerCase() === 'y') {
    event.preventDefault()
    void redoCanvas()
  }
}

function generationStorageKey(): string {
  return `career-planning:canvas-task:${props.sessionId}`
}

function inferenceStorageKey(): string {
  return `career-planning:canvas-inference-task:${props.sessionId}`
}

function checkpointLabel(value?: string | null): string {
  return ({
    QUEUED: '正在排队',
    STARTING: '正在启动任务',
    RESUMING: '正在恢复任务',
    VALIDATING_INPUT: '正在校验职业画像',
    GENERATING_CANVAS: 'AI 正在生成能力节点',
    VALIDATING_RESPONSE: '正在校验能力树结构',
    REPAIRING_RESPONSE: 'AI 正在修复结构化输出',
    VALIDATING_REPAIRED_RESPONSE: '正在复核修复后的能力树',
    FINALIZING_RESPONSE: '正在整理能力树结果',
    PERSISTING_RESULT: '正在保存画布版本',
    RETRY_QUEUED: '正在等待重试',
    COMPLETED: '能力树已生成',
    FAILED: '生成失败',
    CANCELLED: '任务已取消',
  } as Record<string, string>)[value || ''] ?? '正在处理能力树'
}

function inferenceCheckpointLabel(value?: string | null): string {
  return ({
    QUEUED: '推演任务正在排队', STARTING: '正在启动推演', RESUMING: '正在恢复推演',
    VALIDATING_NODE: '正在核对节点范围', VALIDATING_RESPONSE: '正在校验候选结构',
    REPAIRING_RESPONSE: '正在修复候选结构', VALIDATING_REPAIRED_RESPONSE: '正在复核候选',
    FINALIZING_RESPONSE: '正在整理差异候选', PERSISTING_PROPOSAL: '正在保存待审提案',
    RETRY_QUEUED: '正在等待重试', COMPLETED: '推演提案已就绪', FAILED: '推演失败',
    CANCELLED: '推演已取消',
  } as Record<string, string>)[value || ''] ?? 'AI 正在推演此节点'
}

const displayedCanvas = computed(() => currentCanvas.value)
const selectedNode = computed(() => displayedCanvas.value.nodes.find(node => node.logicalNodeId === selectedNodeId.value) ?? null)
const selectedCanvasNodes = computed(() => displayedCanvas.value.nodes.filter(node =>
  selectedNodeIds.value.has(node.logicalNodeId) && node.type !== 'CAREER'))
const selectedValidationNodes = computed(() => displayedCanvas.value.nodes.filter(node =>
  selectedNodeIds.value.has(node.logicalNodeId) && ['SKILL', 'KNOWLEDGE', 'TASK'].includes(node.type)))
const unsupportedValidationCount = computed(() => displayedCanvas.value.nodes.filter(node =>
  selectedNodeIds.value.has(node.logicalNodeId) && !['SKILL', 'KNOWLEDGE', 'TASK'].includes(node.type)).length)
const parents = computed(() => parentMap(displayedCanvas.value))
const visibleIds = computed(() => visibleCanvasNodeIds(
  displayedCanvas.value, query.value, statusFilter.value, focusNodeId.value || undefined,
))
const treeChildren = computed(() => {
  const result = new Map<string, string[]>()
  displayedCanvas.value.relations.filter(relation => relation.type === 'TREE_PARENT').forEach(relation => {
    const children = result.get(relation.toNodeId) ?? []
    children.push(relation.fromNodeId)
    result.set(relation.toNodeId, children)
  })
  return result
})
const desktopVisibleIds = computed(() => {
  if (query.value.trim()) return visibleIds.value
  const hidden = new Set<string>()
  const hideDescendants = (id: string): void => {
    for (const childId of treeChildren.value.get(id) ?? []) {
      hidden.add(childId)
      hideDescendants(childId)
    }
  }
  collapsedIds.value.forEach(hideDescendants)
  return new Set(Array.from(visibleIds.value).filter(id => !hidden.has(id)))
})
const statusCounts = computed(() => canvasStatusCounts(displayedCanvas.value))
const domainCount = computed(() => displayedCanvas.value.nodes.filter(node => node.type === 'DOMAIN').length)
const generationActive = computed(() => isOpenTask(generationTask.value?.status))
const generationProgress = computed(() => Math.max(0, Math.min(100, generationTask.value?.progressPercent ?? 0)))
const generationStatus = computed(() => checkpointLabel(generationTask.value?.checkpointCode))
const inferenceActive = computed(() => isOpenTask(inferenceTask.value?.status))
const inferenceProgress = computed(() => Math.max(0, Math.min(100, inferenceTask.value?.progressPercent ?? 0)))
const inferenceStatus = computed(() => inferenceCheckpointLabel(inferenceTask.value?.checkpointCode))
const canValidateSelected = computed(() => Boolean(selectedNode.value
  && ['SKILL', 'KNOWLEDGE', 'TASK'].includes(selectedNode.value.type)))
const selectedPendingProposal = computed(() => props.pendingProposal?.proposalType === 'NODE_INFERENCE'
  && props.pendingProposal.targetNodeId === selectedNode.value?.logicalNodeId ? props.pendingProposal : null)
const availableInferenceDirections = computed(() => inferenceDirections.map(item => ({
  ...item,
  disabled: (item.value === 'PREREQUISITES' && !['SKILL', 'KNOWLEDGE', 'TASK'].includes(selectedNode.value?.type ?? ''))
    || (item.value === 'SIBLINGS' && selectedNode.value?.type === 'CAREER'),
})))
const prerequisiteRelations = computed(() => displayedCanvas.value.relations.filter(relation => relation.type === 'PREREQUISITE'))
const selectedPrerequisites = computed(() => selectedNode.value
  ? prerequisiteRelations.value
      .filter(relation => relation.toNodeId === selectedNode.value!.logicalNodeId)
      .map(relation => displayedCanvas.value.nodes.find(node => node.logicalNodeId === relation.fromNodeId))
      .filter((node): node is CanvasNode => Boolean(node))
  : [])
const parentOptions = computed(() => currentCanvas.value.nodes.filter(node => node.type !== 'EVIDENCE'
  && node.logicalNodeId !== selectedNodeId.value))
const prerequisiteOptions = computed(() => currentCanvas.value.nodes.filter(node => node.type !== 'CAREER'
  && node.logicalNodeId !== selectedNodeId.value
  && !selectedPrerequisites.value.some(item => item.logicalNodeId === node.logicalNodeId)))
const mergeCandidates = computed(() => {
  const node = selectedNode.value
  const parentId = node ? parents.value.get(node.logicalNodeId) : undefined
  if (!node || !parentId) return []
  return currentCanvas.value.nodes.filter(item => item.logicalNodeId !== node.logicalNodeId
    && item.type === node.type
    && parents.value.get(item.logicalNodeId) === parentId
    && !item.locked
    && item.status !== 'MASTERED'
    && item.status !== 'PENDING_VALIDATION')
})
const editorTitle = computed(() => ({
  ADD: '新增能力节点', EDIT: selectedNode.value?.title || '编辑能力节点',
  SPLIT: '拆分能力节点', MERGE: '合并同级节点',
  INFERENCE: `AI 推演 · ${selectedNode.value?.title || '能力节点'}`, VIEW: selectedNode.value?.title || '',
} as const)[editorMode.value])

const highlightedPath = computed(() => {
  const result = new Set<string>()
  for (const selected of selectedNodeIds.value) {
    let id: string | undefined = selected
    while (id && !result.has(id)) { result.add(id); id = parents.value.get(id) }
  }
  return result
})

const flowNodes = computed<Node<CanvasFlowData>[]>(() => layoutCanvas(displayedCanvas.value)
  .filter(item => desktopVisibleIds.value.has(item.node.logicalNodeId))
  .map(item => ({
    id: item.node.logicalNodeId,
    type: item.node.type === 'CAREER' ? 'career' : 'ability',
    position: { x: item.x, y: item.y },
    data: { item: item.node },
    draggable: !item.node.locked,
    selectable: true,
    selected: selectedNodeIds.value.has(item.node.logicalNodeId),
    focusable: true,
    class: `cp-flow-node cp-flow-node--${item.node.status.toLowerCase()}${highlightedPath.value.has(item.node.logicalNodeId) ? ' cp-flow-node--path' : highlightedPath.value.size ? ' cp-flow-node--muted' : ''}`,
    ariaLabel: `${nodeTypeLabel(item.node.type)}：${item.node.title}，${statusLabel(item.node.status)}`,
  })))

const flowEdges = computed<Edge[]>(() => displayedCanvas.value.relations
  .filter(relation => desktopVisibleIds.value.has(relation.fromNodeId) && desktopVisibleIds.value.has(relation.toNodeId))
  .map(relation => ({
    id: relation.id,
    source: relation.fromNodeId,
    target: relation.toNodeId,
    type: 'smoothstep',
    animated: false,
    markerEnd: relation.type === 'PREREQUISITE' ? MarkerType.ArrowClosed : undefined,
    class: relation.type === 'PREREQUISITE' ? 'cp-flow-edge--prerequisite' : 'cp-flow-edge--tree',
    style: {
      ...(relation.type === 'PREREQUISITE'
        ? { stroke: '#667aa6', strokeWidth: 1.7, strokeDasharray: '7 5' }
        : { stroke: '#8eaae9', strokeWidth: 1.7 }),
      ...(highlightedPath.value.size && highlightedPath.value.has(relation.fromNodeId) && highlightedPath.value.has(relation.toNodeId)
        ? { stroke: '#2563eb', strokeWidth: 2.25 }
        : highlightedPath.value.size ? { opacity: .46 } : {}),
    },
  })))

watch(() => ({ sessionId: props.sessionId, nodes: currentCanvas.value.nodes.map(node => ({ logicalNodeId: node.logicalNodeId, status: node.status })) }), (value, previous) => {
  if (value.sessionId !== previous.sessionId) return
  const completed = confirmedMastery(previous.nodes, value.nodes)
  if (completed.size) void animateCanvasChange(desktopVisibleIds.value, completed)
})
watch([query, statusFilter, focusNodeId], cancelCanvasMotion)

const mobileRows = computed<MobileRow[]>(() => {
  const rows: MobileRow[] = []
  const visit = (items: ReturnType<typeof buildCanvasTree>, level: number): void => {
    for (const item of items) {
      if (!visibleIds.value.has(item.node.logicalNodeId)) continue
      const expanded = expandedIds.value.has(item.node.logicalNodeId)
      rows.push({ node: item.node, level, childCount: item.children.length, expanded })
      if (expanded) visit(item.children, level + 1)
    }
  }
  visit(buildCanvasTree(displayedCanvas.value), 0)
  return rows
})
const mobileVirtualRange = computed(() => virtualCanvasRange(
  mobileRows.value.length, mobileScrollTop.value, mobileViewportHeight.value,
))
const virtualMobileRows = computed(() => mobileRows.value.slice(
  mobileVirtualRange.value.start, mobileVirtualRange.value.end,
))

function mobileScrollStorageKey(): string {
  return `career-planning:mobile-canvas-scroll:${props.sessionId}`
}

async function restoreMobileScroll(): Promise<void> {
  await nextTick()
  const viewport = mobileTreeViewport.value
  if (!viewport) return
  const stored = Number(window.sessionStorage.getItem(mobileScrollStorageKey()) ?? 0)
  viewport.scrollTop = Number.isFinite(stored) ? Math.max(0, stored) : 0
  mobileScrollTop.value = viewport.scrollTop
  mobileViewportHeight.value = viewport.clientHeight || 560
}

function onMobileTreeScroll(event: Event): void {
  const viewport = event.currentTarget as HTMLElement
  mobileScrollTop.value = viewport.scrollTop
  mobileViewportHeight.value = viewport.clientHeight || 560
  window.sessionStorage.setItem(mobileScrollStorageKey(), String(Math.round(viewport.scrollTop)))
}

function detailString(node: CanvasNode, key: string): string {
  const value = node.detail[key]
  return typeof value === 'string' ? value : ''
}

function detailLines(node: CanvasNode, key: string): string {
  const value = node.detail[key]
  return Array.isArray(value) ? value.filter(item => typeof item === 'string').join('\n') : detailString(node, key)
}

function nodeTypeLabel(type: CanvasNodeType): string {
  return ({ CAREER: '目标职业', DOMAIN: '能力域', SKILL: '技能', KNOWLEDGE: '知识点', TASK: '行动任务', EVIDENCE: '能力证据' } as const)[type]
}

function statusLabel(status: CanvasNodeStatus): string {
  return ({ NOT_STARTED: '未开始', PLANNED: '计划中', LEARNING: '学习中', PENDING_VALIDATION: '待验证', MASTERED: '已掌握', PAUSED: '已暂停' } as const)[status]
}

function nodeIcon(type: CanvasNodeType) {
  return ({ CAREER: BriefcaseBusiness, DOMAIN: Layers3, SKILL: Code2, KNOWLEDGE: BookOpen, TASK: Target, EVIDENCE: FileCheck2 } as const)[type]
}

function hasTreeChildren(id: string): boolean {
  return Boolean(treeChildren.value.get(id)?.length)
}

function selectNode(node: CanvasNode): void {
  selectedNodeId.value = node.logicalNodeId
  selectedNodeIds.value = new Set([node.logicalNodeId])
  editorMode.value = 'VIEW'
  deleteArmed.value = false
  prerequisiteId.value = ''
}

function openInference(node = selectedNode.value): void {
  if (!node) return
  if (!props.aiConsent) {
    emit('error', '节点推演前需要开启 AI 授权。')
    return
  }
  selectNode(node)
  if (selectedPendingProposal.value) {
    emit('reviewProposal', selectedPendingProposal.value)
    return
  }
  inferenceDirection.value = node.type === 'CAREER' ? 'TARGET_GAP' : 'DOWNWARD'
  inferenceDepth.value = 'ONE_LEVEL'
  inferenceInstruction.value = ''
  editorMode.value = 'INFERENCE'
}

function openValidation(node = selectedNode.value): void {
  if (!node || !['SKILL', 'KNOWLEDGE', 'TASK'].includes(node.type)) {
    emit('notice', '只有技能、知识或任务节点可以进入能力验证。')
    return
  }
  emit('validate', [node.logicalNodeId])
}

function openSelectedValidation(): void {
  const nodes = selectedValidationNodes.value
  if (!nodes.length) {
    emit('notice', '当前选择中没有可验证的技能、知识或任务节点。')
    return
  }
  if (nodes.length > 8) {
    emit('error', `当前有 ${nodes.length} 个可验证节点，请减少到 8 个以内。`)
    return
  }
  emit('validate', nodes.map(node => node.logicalNodeId))
}

function onFlowCardClick(event: MouseEvent, node: CanvasNode): void {
  if (event.ctrlKey || event.metaKey || event.shiftKey) toggleNodeSelection(node.logicalNodeId)
  else selectNode(node)
}

function onFlowCardPointerDown(event: PointerEvent): void {
  // Vue Flow also handles modified pointer-down events. Let the card own those
  // events so one user action cannot select and immediately deselect a node.
  if (event.ctrlKey || event.metaKey || event.shiftKey) event.stopPropagation()
}

function onNodeClick(event: NodeMouseEvent): void {
  const node = displayedCanvas.value.nodes.find(item => item.logicalNodeId === event.node.id)
  if (!node) return
  const mouse = event.event instanceof MouseEvent ? event.event : null
  if (mouse?.ctrlKey || mouse?.metaKey || mouse?.shiftKey) {
    toggleNodeSelection(node.logicalNodeId)
    return
  }
  selectNode(node)
}

function onNodesChange(changes: NodeChange[]): void {
  const selections = changes.filter((change): change is Extract<NodeChange, { type: 'select' }> => change.type === 'select')
  if (!marqueeSelecting || !selections.length) return
  const next = new Set(selectedNodeIds.value)
  selections.forEach(change => change.selected ? next.add(change.id) : next.delete(change.id))
  selectedNodeIds.value = next
  selectedNodeId.value = next.size === 1 ? Array.from(next)[0] : ''
}

function onSelectionStart(event: MouseEvent): void {
  const target = event.target
  marqueeSelecting = !(target instanceof Element && target.closest('.vue-flow__node'))
}

function onSelectionEnd(): void {
  marqueeSelecting = false
}

function toggleNodeSelection(nodeId: string): void {
  const node = displayedCanvas.value.nodes.find(item => item.logicalNodeId === nodeId)
  if (!node || node.type === 'CAREER') return
  const next = new Set(selectedNodeIds.value)
  next.has(nodeId) ? next.delete(nodeId) : next.add(nodeId)
  selectedNodeIds.value = next
  selectedNodeId.value = mobileSelectionMode.value ? '' : next.size === 1 ? Array.from(next)[0] : ''
  editorMode.value = 'VIEW'
}

function clearNodeSelection(): void {
  selectedNodeIds.value = new Set()
  selectedNodeId.value = ''
  batchStatus.value = ''
  mobileSelectionMode.value = false
}

function handleMobileNode(node: CanvasNode): void {
  if (mobileSelectionMode.value) toggleNodeSelection(node.logicalNodeId)
  else selectNode(node)
}

async function applyBatchChange(change: 'LOCK' | 'UNLOCK' | 'STATUS'): Promise<void> {
  const nodes = selectedCanvasNodes.value
  if (nodes.length < 2) {
    emit('error', '请至少选择两个非职业根节点。')
    return
  }
  if (change === 'STATUS' && !batchStatus.value) {
    emit('error', '请选择批量状态。')
    return
  }
  const command = {
    nodeIds: nodes.map(node => node.logicalNodeId),
    expectedVersion: currentCanvas.value.version,
    ...(change === 'STATUS' ? { status: batchStatus.value } : { locked: change === 'LOCK' }),
  }
  const saved = await mutate('batch-nodes', () => updateCareerCanvasNodes(props.sessionId, command),
    `已批量更新 ${nodes.length} 个节点，并生成一个新版本。`)
  if (saved) clearNodeSelection()
}

async function onNodeDragStop(event: NodeDragEvent): Promise<void> {
  const node = currentCanvas.value.nodes.find(item => item.logicalNodeId === event.node.id)
  if (!node || node.locked) return
  await mutate(`position:${node.logicalNodeId}`, () => updateCareerCanvasNode(props.sessionId, node.logicalNodeId, {
    x: Math.round(event.node.position.x), y: Math.round(event.node.position.y), expectedVersion: currentCanvas.value.version,
  }), '节点位置已保存。')
}

function startEdit(node: CanvasNode): void {
  selectNode(node)
  editorMode.value = 'EDIT'
  form.type = node.type === 'CAREER' ? 'DOMAIN' : node.type
  form.title = node.title
  form.status = node.status === 'MASTERED' ? 'PENDING_VALIDATION' : node.status
  form.parentNodeId = parents.value.get(node.logicalNodeId) ?? ''
  form.summary = detailString(node, 'summary')
  form.importance = detailString(node, 'importance') || 'MEDIUM'
  form.estimatedHours = String(node.detail.estimatedHours ?? '')
  form.learningContents = detailLines(node, 'learningContents')
  form.masteryCriteria = detailLines(node, 'masteryCriteria')
  form.notes = detailString(node, 'notes')
  form.locked = node.locked
}

function startAdd(parent: CanvasNode): void {
  selectedNodeId.value = parent.logicalNodeId
  editorMode.value = 'ADD'
  form.type = parent.type === 'CAREER' ? 'DOMAIN' : parent.type === 'DOMAIN' ? 'SKILL' : 'KNOWLEDGE'
  form.title = ''
  form.status = 'NOT_STARTED'
  form.parentNodeId = parent.logicalNodeId
  form.summary = ''
  form.importance = 'MEDIUM'
  form.estimatedHours = ''
  form.learningContents = ''
  form.masteryCriteria = ''
  form.notes = ''
  form.locked = false
}

function childTypeFor(node: CanvasNode): Exclude<CanvasNodeType, 'CAREER'> {
  return node.type === 'CAREER' ? 'DOMAIN'
    : node.type === 'DOMAIN' ? 'SKILL'
      : node.type === 'SKILL' ? 'KNOWLEDGE'
        : node.type === 'KNOWLEDGE' ? 'TASK' : 'EVIDENCE'
}

function newSplitItem(type: Exclude<CanvasNodeType, 'CAREER'>) {
  return { id: splitSequence++, type, title: '', summary: '' }
}

function startSplit(node: CanvasNode): void {
  if (node.type === 'CAREER' || node.type === 'EVIDENCE' || node.locked) return
  selectNode(node)
  editorMode.value = 'SPLIT'
  const type = childTypeFor(node)
  splitItems.value = [newSplitItem(type), newSplitItem(type)]
}

function addSplitItem(): void {
  if (!selectedNode.value || splitItems.value.length >= 8) return
  splitItems.value.push(newSplitItem(childTypeFor(selectedNode.value)))
}

function removeSplitItem(id: number): void {
  if (splitItems.value.length <= 2) return
  splitItems.value = splitItems.value.filter(item => item.id !== id)
}

function startMerge(node: CanvasNode): void {
  selectNode(node)
  if (node.type === 'CAREER' || node.locked || node.status === 'MASTERED' || node.status === 'PENDING_VALIDATION') return
  if (!mergeCandidates.value.length) {
    emit('error', '当前上级下没有可与该节点合并的同类型节点。')
    return
  }
  editorMode.value = 'MERGE'
  mergeNodeIds.value = [node.logicalNodeId]
  mergeTitle.value = node.title
  mergeSummary.value = detailString(node, 'summary')
}

function toggleMergeNode(nodeId: string): void {
  const next = new Set(mergeNodeIds.value)
  next.has(nodeId) ? next.delete(nodeId) : next.add(nodeId)
  mergeNodeIds.value = [selectedNode.value!.logicalNodeId, ...Array.from(next)
    .filter(id => id !== selectedNode.value!.logicalNodeId)]
}

function detailPayload(): Record<string, unknown> {
  const lines = (value: string) => value.split('\n').map(item => item.trim()).filter(Boolean)
  return {
    summary: form.summary.trim(), importance: form.importance,
    estimatedHours: form.estimatedHours ? Number(form.estimatedHours) : null,
    learningContents: lines(form.learningContents), masteryCriteria: lines(form.masteryCriteria),
    notes: form.notes.trim(),
  }
}

async function saveNode(): Promise<void> {
  if (!form.title.trim() || !form.parentNodeId) {
    emit('error', '请填写节点名称并选择上级节点。')
    return
  }
  if (editorMode.value === 'ADD') {
    const saved = await mutate('save-node', () => createCareerCanvasNode(props.sessionId, {
      type: form.type, title: form.title.trim(), status: form.status,
      parentNodeId: form.parentNodeId, detail: detailPayload(), sourceRefs: [],
      locked: form.locked, expectedVersion: currentCanvas.value.version,
    }), '新能力节点已加入，并生成新版本。')
    if (saved) editorMode.value = 'VIEW'
  } else if (selectedNode.value) {
    const nodeId = selectedNode.value.logicalNodeId
    const saved = await mutate('save-node', () => updateCareerCanvasNode(props.sessionId, nodeId, {
      title: form.title.trim(), status: form.status, parentNodeId: form.parentNodeId,
      detail: detailPayload(), sourceRefs: selectedNode.value?.sourceRefs ?? [],
      locked: form.locked, expectedVersion: currentCanvas.value.version,
    }), '节点修改已保存为新版本。')
    if (saved) editorMode.value = 'VIEW'
  }
}

async function confirmSplit(): Promise<void> {
  const node = selectedNode.value
  if (!node) return
  const titles = splitItems.value.map(item => item.title.trim())
  if (titles.some(title => !title)) {
    emit('error', '请填写每个拆分节点的名称。')
    return
  }
  if (new Set(titles.map(title => title.toLocaleLowerCase())).size !== titles.length) {
    emit('error', '拆分节点名称不能重复。')
    return
  }
  const saved = await mutate('split-node', () => splitCareerCanvasNode(props.sessionId, node.logicalNodeId,
    splitItems.value.map(item => ({
      type: item.type, title: item.title.trim(), status: 'NOT_STARTED',
      detail: { summary: item.summary.trim() }, sourceRefs: node.sourceRefs, locked: false,
    })), currentCanvas.value.version), `“${node.title}”已拆分为 ${splitItems.value.length} 个结构化下级，并生成新版本。`)
  if (saved) editorMode.value = 'VIEW'
}

async function confirmMerge(): Promise<void> {
  const node = selectedNode.value
  if (!node || mergeNodeIds.value.length < 2) {
    emit('error', '请至少再选择一个同级节点。')
    return
  }
  if (!mergeTitle.value.trim()) {
    emit('error', '请填写合并后的节点名称。')
    return
  }
  const saved = await mutate('merge-nodes', () => mergeCareerCanvasNodes(props.sessionId,
    mergeNodeIds.value, mergeTitle.value.trim(), { ...node.detail, summary: mergeSummary.value.trim() },
    currentCanvas.value.version), `${mergeNodeIds.value.length} 个同级节点已合并，原有下级和依赖关系已迁移。`)
  if (saved) editorMode.value = 'VIEW'
}

async function generate(): Promise<void> {
  if (!props.aiConsent) {
    emit('error', '生成能力树前需要开启 AI 授权。')
    return
  }
  if (generationActive.value || generationPolling.value) return
  pending.value = 'generate'
  try {
    const task = await startCareerCanvasGeneration(
      props.sessionId, currentCanvas.value.version, generationScale.value,
    )
    window.localStorage.setItem(generationStorageKey(), task.id)
    await followGenerationTask(task)
  } catch (reason) {
    emit('error', errorMessage(reason, '能力树任务启动失败，原画布没有变化'))
  } finally {
    pending.value = ''
  }
}

async function restoreGenerationTask(): Promise<void> {
  const taskId = window.localStorage.getItem(generationStorageKey())
  if (!taskId || generationPolling.value) return
  try {
    const task = await fetchTask(taskId)
    generationTask.value = task
    if (isOpenTask(task.status)) {
      pending.value = 'generate'
      await followGenerationTask(task)
    } else if (task.status === 'SUCCEEDED' && currentCanvas.value.nodes.length === 1) {
      await applyGeneratedCanvas()
    } else {
      window.localStorage.removeItem(generationStorageKey())
    }
  } catch (reason) {
    window.localStorage.removeItem(generationStorageKey())
    if (!isAbortError(reason)) emit('error', errorMessage(reason, '能力树任务恢复失败'))
  } finally {
    pending.value = ''
  }
}

async function followGenerationTask(initial: TaskView): Promise<void> {
  if (generationPolling.value) return
  generationPolling.value = true
  generationTask.value = initial
  generationController?.abort()
  generationController = new AbortController()
  try {
    const terminal = isOpenTask(initial.status)
      ? await pollTask(initial.id, task => { generationTask.value = task }, generationController.signal, 1000, 360_000)
      : initial
    generationTask.value = terminal
    if (terminal.status === 'SUCCEEDED') {
      await applyGeneratedCanvas()
      emit('notice', '完整能力树已生成，并保存为新的画布版本。')
    } else if (terminal.status === 'FAILED') {
      emit('error', terminal.failureReason || `能力树生成失败（${terminal.errorCode || 'CAREER_CANVAS_GENERATION_FAILED'}）`)
    }
    if (!isOpenTask(terminal.status)) window.localStorage.removeItem(generationStorageKey())
  } catch (reason) {
    if (!isAbortError(reason)) emit('error', errorMessage(reason, '任务仍在后台执行，可刷新页面继续查看'))
  } finally {
    generationPolling.value = false
  }
}

async function applyGeneratedCanvas(): Promise<void> {
  const before = currentCanvas.value
  const session = await fetchCareerPlanningSession(props.sessionId)
  if (!session.canvas) throw new Error('能力树任务完成，但正式画布尚未就绪')
  currentCanvas.value = session.canvas
  initializeExpanded(session.canvas)
  recordCanvasMutation(before, session.canvas)
  emit('updated', session.canvas)
  await nextTick(() => fitCurrent())
}

async function startInference(): Promise<void> {
  const node = selectedNode.value
  if (!node || inferenceActive.value || inferencePolling.value) return
  const option = availableInferenceDirections.value.find(item => item.value === inferenceDirection.value)
  if (!option || option.disabled) {
    emit('error', '当前节点不支持所选推演方向。')
    return
  }
  pending.value = 'inference'
  try {
    const task = await startCareerNodeInference(props.sessionId, {
      targetNodeId: node.logicalNodeId,
      direction: inferenceDirection.value,
      depth: inferenceDepth.value,
      instruction: inferenceInstruction.value.trim() || undefined,
      expectedVersion: currentCanvas.value.version,
    })
    window.localStorage.setItem(inferenceStorageKey(), task.id)
    await followInferenceTask(task)
  } catch (reason) {
    emit('error', errorMessage(reason, '节点推演任务启动失败，原画布没有变化'))
  } finally {
    pending.value = ''
  }
}

async function restoreInferenceTask(): Promise<void> {
  const taskId = window.localStorage.getItem(inferenceStorageKey())
  if (!taskId || inferencePolling.value) return
  try {
    const task = await fetchTask(taskId)
    inferenceTask.value = task
    if (isOpenTask(task.status)) await followInferenceTask(task)
    else if (task.status === 'SUCCEEDED') {
      await revealInferenceProposal(task)
      window.localStorage.removeItem(inferenceStorageKey())
    }
    else window.localStorage.removeItem(inferenceStorageKey())
  } catch (reason) {
    window.localStorage.removeItem(inferenceStorageKey())
    if (!isAbortError(reason)) emit('error', errorMessage(reason, '节点推演任务恢复失败'))
  }
}

async function followInferenceTask(initial: TaskView): Promise<void> {
  if (inferencePolling.value) return
  inferencePolling.value = true
  inferenceTask.value = initial
  inferenceController?.abort()
  inferenceController = new AbortController()
  try {
    const terminal = isOpenTask(initial.status)
      ? await pollTask(initial.id, task => { inferenceTask.value = task }, inferenceController.signal, 1000, 360_000)
      : initial
    inferenceTask.value = terminal
    if (terminal.status === 'SUCCEEDED') {
      await revealInferenceProposal(terminal)
      emit('notice', '节点推演已完成，请逐项确认候选差异。')
    } else if (terminal.status === 'FAILED') {
      emit('error', terminal.failureReason || `节点推演失败（${terminal.errorCode || 'CAREER_INFERENCE_GENERATION_FAILED'}）`)
    }
    if (!isOpenTask(terminal.status)) window.localStorage.removeItem(inferenceStorageKey())
  } catch (reason) {
    if (!isAbortError(reason)) emit('error', errorMessage(reason, '推演仍在后台执行，可刷新页面继续查看'))
  } finally {
    inferencePolling.value = false
  }
}

async function revealInferenceProposal(task: TaskView): Promise<void> {
  if (!task.resultVersion) throw new Error('推演任务完成，但待审提案尚未就绪')
  const proposal = await fetchCareerCanvasProposal(props.sessionId, task.resultVersion)
  emit('proposalGenerated', proposal)
  emit('reviewProposal', proposal)
  editorMode.value = 'VIEW'
}

async function removeNode(): Promise<void> {
  const node = selectedNode.value
  if (!node || node.type === 'CAREER') return
  if (!deleteArmed.value) {
    deleteArmed.value = true
    return
  }
  const removed = await mutate('delete-node', () => deleteCareerCanvasNode(
    props.sessionId, node.logicalNodeId, true, currentCanvas.value.version,
  ), `“${node.title}”及其下级节点已删除，并生成新版本。`)
  if (removed) {
    selectedNodeId.value = ''
    deleteArmed.value = false
  }
}

async function addPrerequisite(): Promise<void> {
  if (!selectedNode.value || !prerequisiteId.value) return
  await mutate('prerequisite', () => addCareerCanvasRelation(
    props.sessionId, prerequisiteId.value, selectedNode.value!.logicalNodeId,
    'PREREQUISITE', currentCanvas.value.version,
  ), '前置依赖已保存为新版本。')
  prerequisiteId.value = ''
}

async function mutate(key: string, operation: () => Promise<CareerCanvas>, success: string): Promise<boolean> {
  if (pending.value) return false
  pending.value = key
  try {
    const before = currentCanvas.value
    const value = await operation()
    currentCanvas.value = value
    recordCanvasMutation(before, value)
    emit('updated', value)
    emit('notice', success)
    return true
  } catch (reason) {
    emit('error', errorMessage(reason, '能力画布更新失败'))
    return false
  } finally {
    pending.value = ''
  }
}

async function restoreHistoryVersion(direction: 'undo' | 'redo'): Promise<void> {
  if (pending.value) return
  const source = direction === 'undo' ? undoVersions.value : redoVersions.value
  const opposite = direction === 'undo' ? redoVersions.value : undoVersions.value
  const targetVersion = source.at(-1)
  if (!targetVersion) return
  const before = currentCanvas.value
  pending.value = direction
  try {
    const value = await restoreCareerCanvasVersion(props.sessionId, targetVersion, before.version)
    source.pop()
    pushHistoryVersion(opposite, before.version)
    currentCanvas.value = value
    persistCanvasHistory(value.version)
    emit('updated', value)
    emit('notice', `${direction === 'undo' ? '已撤销' : '已重做'}到画布 v${targetVersion} 的内容，并创建新的 v${value.version}。`)
    await nextTick(() => fitCurrent())
  } catch (reason) {
    emit('error', errorMessage(reason, direction === 'undo' ? '画布撤销失败' : '画布重做失败'))
  } finally {
    pending.value = ''
  }
}

async function undoCanvas(): Promise<void> {
  await restoreHistoryVersion('undo')
}

async function redoCanvas(): Promise<void> {
  await restoreHistoryVersion('redo')
}

function toggleFocus(): void {
  focusNodeId.value = focusNodeId.value ? '' : selectedNodeId.value
  void nextTick(() => fitCurrent())
}

function toggleExpanded(id: string): void {
  cancelCanvasMotion()
  const next = new Set(expandedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  expandedIds.value = next
}

function syncExpanded(canvas: CareerCanvas): void {
  const valid = new Set(canvas.nodes.map(node => node.logicalNodeId))
  const next = new Set(Array.from(expandedIds.value).filter(id => valid.has(id)))
  canvas.nodes.filter(node => node.type === 'CAREER' || node.type === 'DOMAIN').forEach(node => next.add(node.logicalNodeId))
  expandedIds.value = next
  collapsedIds.value = new Set(Array.from(collapsedIds.value).filter(id => valid.has(id)))
}

function toggleDesktopCollapsed(id: string): void {
  const previous = new Set(desktopVisibleIds.value)
  const next = new Set(collapsedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  collapsedIds.value = next
  void animateCanvasChange(previous)
  void nextTick(() => fitCurrent())
}

function collapseToDomains(): void {
  cancelCanvasMotion()
  collapsedIds.value = new Set(displayedCanvas.value.nodes
    .filter(node => node.type === 'DOMAIN' && hasTreeChildren(node.logicalNodeId))
    .map(node => node.logicalNodeId))
  const roots = displayedCanvas.value.nodes.filter(node => node.type === 'CAREER').map(node => node.logicalNodeId)
  expandedIds.value = new Set(roots)
  void nextTick(() => fitCurrent())
}

function expandAllNodes(): void {
  const previous = new Set(desktopVisibleIds.value)
  collapsedIds.value = new Set()
  expandedIds.value = new Set(displayedCanvas.value.nodes
    .filter(node => hasTreeChildren(node.logicalNodeId))
    .map(node => node.logicalNodeId))
  void animateCanvasChange(previous)
  void nextTick(() => fitCurrent())
}

function fitCurrent(): void {
  if (!matchMedia('(min-width: 901px)').matches) return
  window.cancelAnimationFrame(fitFrame)
  fitFrame = window.requestAnimationFrame(() => {
    fitFrame = window.requestAnimationFrame(() => {
      fitFrame = 0
      void fitView({ padding: 0.32, maxZoom: 0.95, duration: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 420 })
    })
  })
}
</script>

<template>
  <section class="career-canvas-shell">
    <aside class="career-canvas-assistant">
      <header><span><Bot :size="20" /></span><div><strong>JobProof AI</strong><small>能力规划助手</small></div></header>
      <template v-if="currentCanvas.nodes.length === 1">
        <p>目标已经确认。下一步会基于已确认画像和授权资料生成完整能力树。</p>
        <div class="generation-scale" role="radiogroup" aria-label="能力画布初始化规模">
          <button v-for="item in generationScales" :key="item.value" type="button" role="radio" :aria-checked="generationScale === item.value" :class="{ active: generationScale === item.value }" @click="generationScale = item.value"><strong>{{ item.label }}</strong><small>{{ item.range }}</small></button>
        </div>
        <Transition name="assistant-copy" mode="out-in"><div :key="generationScale" class="assistant-summary scale-summary"><strong>{{ generationScales.find(item => item.value === generationScale)?.copy }}</strong><span>{{ generationScale === 'COMPACT' ? '3–4' : generationScale === 'DEEP' ? '8–10' : '5–7' }} 个能力域</span><span>包含任务、证据与完整能力链路</span></div></Transition>
        <button class="canvas-primary" type="button" :disabled="generationActive || generationPolling || Boolean(pending)" :aria-busy="generationActive || generationPolling" @click="generate">
          <LoaderCircle v-if="generationActive || generationPolling" class="cp-spin" :size="17" />
          <Sparkles v-else :size="17" />{{ generationActive || generationPolling ? generationStatus : '生成完整能力树' }}
        </button>
        <div v-if="generationTask" class="canvas-generation-status" role="status">
          <span><i :style="{ width: `${generationProgress}%` }" /></span>
          <p><strong>{{ generationStatus }}</strong><em>{{ generationProgress }}%</em></p>
          <small>任务在后台执行，刷新页面后仍会继续恢复；正式画布只在校验成功后更新。</small>
        </div>
      </template>
      <template v-else>
        <p>完整能力树已经生成。你可以查看路径、编辑节点，或从任务与证据继续制定学习计划。</p>
        <div class="assistant-summary">
          <strong>画布状态：已生成</strong>
          <span>节点总数 {{ currentCanvas.nodes.length }}</span>
          <span>能力域 {{ domainCount }}</span>
          <span>版本 v{{ currentCanvas.version }}</span>
        </div>
        <div class="assistant-actions">
          <button type="button" :disabled="!selectedNodeId" @click="toggleFocus"><Network :size="16" />{{ focusNodeId ? '查看完整画布' : '聚焦当前路径' }}</button>
          <Transition name="assistant-copy" mode="out-in"><div v-if="selectedNode" :key="selectedNode.logicalNodeId" class="assistant-node"><small>当前节点 · {{ nodeTypeLabel(selectedNode.type) }}</small><strong>{{ selectedNode.title }}</strong><span>{{ detailString(selectedNode, 'summary') || '可继续推演发展分支，或进入证据验证。' }}</span></div></Transition>
          <button type="button" :disabled="!selectedNode || selectedNode.type === 'EVIDENCE' || inferenceActive" :title="selectedNode?.type === 'EVIDENCE' ? '证据节点是推演终点' : '从当前节点继续推演'" @click="openInference()"><LoaderCircle v-if="inferenceActive" class="cp-spin" :size="16" /><Sparkles v-else :size="16" />{{ selectedPendingProposal ? '继续审阅推演' : inferenceActive ? inferenceStatus : 'AI 推演此节点' }}</button>
          <button type="button" :disabled="!canValidateSelected" :title="canValidateSelected ? '使用现有证据与确认流程验证此节点' : '只有技能、知识或任务节点可以验证'" @click="openValidation()"><ShieldCheck :size="16" />AI 验证此节点</button>
        </div>
      </template>
      <footer><ShieldCheck :size="15" />AI 不能直接把节点标记为已掌握</footer>
    </aside>

    <section class="career-canvas-workspace">
      <header class="canvas-meta">
        <div><small>目标职业</small><strong>{{ goalTitle }}</strong></div>
        <div><small>能力节点</small><strong>{{ displayedCanvas.nodes.length }}</strong></div>
        <div><small>画布版本</small><strong>v{{ displayedCanvas.version }}</strong></div>
      </header>

      <div class="canvas-toolbar">
        <div class="canvas-tool-toggle" role="group" aria-label="画布操作工具">
          <button type="button" :class="{ active: canvasTool === 'PAN' }" :aria-pressed="canvasTool === 'PAN'" title="手掌移动 (H)" @click="canvasTool = 'PAN'"><Hand :size="16" /><span>手掌移动</span></button>
          <button type="button" :class="{ active: canvasTool === 'MARQUEE' }" :aria-pressed="canvasTool === 'MARQUEE'" title="框选节点 (V)" @click="canvasTool = 'MARQUEE'"><SquareDashedMousePointer :size="16" /><span>框选节点</span></button>
        </div>
        <AppSelect v-model="statusFilter" aria-label="筛选节点状态">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </AppSelect>
        <button class="icon-command" type="button" aria-label="聚焦当前路径" :title="focusNodeId ? '查看完整画布' : '聚焦当前路径'" :class="{ active: Boolean(focusNodeId) }" :disabled="!selectedNodeId" @click="toggleFocus"><GitBranch :size="16" /></button>
        <button class="icon-command" type="button" aria-label="适应屏幕" title="适应屏幕" @click="fitCurrent"><Maximize2 :size="16" /></button>
        <button class="icon-command" type="button" aria-label="撤销画布变更" title="撤销画布变更 (Ctrl+Z)" :disabled="!undoVersions.length || Boolean(pending)" @click="undoCanvas"><Undo2 :size="17" /></button>
        <button class="icon-command" type="button" aria-label="重做画布变更" title="重做画布变更 (Ctrl+Shift+Z)" :disabled="!redoVersions.length || Boolean(pending)" @click="redoCanvas"><Redo2 :size="17" /></button>
        <button class="icon-command" type="button" aria-label="折叠到能力域" title="折叠到能力域" @click="collapseToDomains"><ChevronsDown :size="17" /></button>
        <button class="icon-command" type="button" aria-label="展开全部节点" title="展开全部节点" @click="expandAllNodes"><ChevronsUp :size="17" /></button>
        <button v-if="currentCanvas.nodes.length > 1" class="icon-command" type="button" title="在目标下新增能力域" @click="startAdd(currentCanvas.nodes.find(item => item.type === 'CAREER')!)"><Plus :size="18" /></button>
      </div>

      <section v-if="selectedNodeIds.size > 1 || mobileSelectionMode" class="canvas-batch-bar" aria-label="画布批量操作">
        <div><ListChecks :size="18" /><strong>可验证 {{ selectedValidationNodes.length }} 个<span v-if="unsupportedValidationCount"> · 不支持 {{ unsupportedValidationCount }} 个</span></strong><small>框选完整包围节点，Ctrl/Command 点击增减</small></div>
        <AppSelect v-model="batchStatus" aria-label="批量设置节点状态">
          <option value="">选择状态</option>
          <option v-for="option in editableStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </AppSelect>
        <button type="button" :disabled="selectedCanvasNodes.length < 2 || !batchStatus || Boolean(pending)" @click="applyBatchChange('STATUS')"><ShieldCheck :size="16" />应用状态</button>
        <button type="button" :disabled="selectedCanvasNodes.length < 2 || Boolean(pending)" @click="applyBatchChange('LOCK')"><LockKeyhole :size="16" />锁定</button>
        <button type="button" :disabled="selectedCanvasNodes.length < 2 || Boolean(pending)" @click="applyBatchChange('UNLOCK')"><LockOpen :size="16" />解锁</button>
        <button class="batch-validate" type="button" :class="{ 'over-limit': selectedValidationNodes.length > 8 }" :disabled="!selectedValidationNodes.length || Boolean(pending)" @click="openSelectedValidation"><Sparkles :size="16" />AI 验证</button>
        <button class="batch-clear" type="button" @click="clearNodeSelection"><X :size="16" /><span>取消选择</span></button>
      </section>

      <div class="canvas-status-legend" aria-label="节点状态说明">
        <span class="mastered">已掌握 {{ statusCounts.MASTERED }}</span>
        <span class="learning">学习中 {{ statusCounts.LEARNING }}</span>
        <span class="planned">计划中 {{ statusCounts.PLANNED }}</span>
        <span class="validation">待验证 {{ statusCounts.PENDING_VALIDATION }}</span>
      </div>

      <div ref="canvasStage" class="canvas-desktop-stage">
        <VueFlow
          v-if="currentCanvas.nodes.length > 1"
          :nodes="flowNodes"
          :edges="flowEdges"
          :min-zoom="0.45"
          :max-zoom="1.8"
          :nodes-connectable="false"
          :delete-key-code="null"
          :selection-key-code="canvasTool === 'MARQUEE' ? true : null"
          :multi-selection-key-code="['Control', 'Meta']"
          :selection-mode="SelectionMode.Full"
          :pan-on-drag="canvasTool === 'PAN' ? true : [1, 2]"
          :select-nodes-on-drag="true"
          :only-render-visible-elements="true"
          class="career-vue-flow"
          @node-click="onNodeClick"
          @nodes-change="onNodesChange"
          @selection-start="onSelectionStart"
          @selection-end="onSelectionEnd"
          @node-drag-stop="onNodeDragStop"
          @init="fitCurrent"
        >
          <Background pattern-color="#dbe5f4" :gap="18" :size="1.2" />
          <MiniMap pannable zoomable :node-color="node => node.data?.item?.type === 'CAREER' ? '#1769ff' : node.data?.item?.type === 'DOMAIN' ? '#12a67a' : '#8aa7e8'" />
          <Controls position="bottom-right" :show-interactive="false" />
          <template #node-career="{ data }">
            <Handle type="target" :position="Position.Left" />
            <button class="flow-card flow-card--career" type="button" @click.stop="onFlowCardClick($event, data.item)">
              <BriefcaseBusiness :size="20" /><span><strong>{{ data.item.title }}</strong><small>{{ statusLabel(data.item.status) }}</small></span>
            </button>
            <button v-if="hasTreeChildren(data.item.logicalNodeId)" class="flow-collapse-toggle" type="button" :aria-label="collapsedIds.has(data.item.logicalNodeId) ? `展开 ${data.item.title}` : `折叠 ${data.item.title}`" @click.stop="toggleDesktopCollapsed(data.item.logicalNodeId)"><ChevronRight v-if="collapsedIds.has(data.item.logicalNodeId)" :size="14" /><ChevronDown v-else :size="14" /></button>
          </template>
          <template #node-ability="{ data }">
            <Handle type="target" :position="Position.Left" />
            <button class="flow-card" type="button" :class="[`flow-card--${data.item.type.toLowerCase()}`, { selected: selectedNodeIds.has(data.item.logicalNodeId) }]" @pointerdown="onFlowCardPointerDown" @click.stop="onFlowCardClick($event, data.item)">
              <span class="flow-card__status" :class="data.item.status.toLowerCase()" />
              <component :is="nodeIcon(data.item.type)" :size="17" />
              <span><strong>{{ data.item.title }}</strong><small>{{ statusLabel(data.item.status) }}</small></span>
              <LockKeyhole v-if="data.item.locked" :size="13" />
            </button>
            <button v-if="hasTreeChildren(data.item.logicalNodeId)" class="flow-collapse-toggle" type="button" :aria-label="collapsedIds.has(data.item.logicalNodeId) ? `展开 ${data.item.title}` : `折叠 ${data.item.title}`" @click.stop="toggleDesktopCollapsed(data.item.logicalNodeId)"><ChevronRight v-if="collapsedIds.has(data.item.logicalNodeId)" :size="14" /><ChevronDown v-else :size="14" /></button>
            <Handle type="source" :position="Position.Right" />
          </template>
        </VueFlow>
        <div v-if="currentCanvas.nodes.length === 1" class="canvas-empty-callout">
          <Layers3 :size="28" /><strong>能力树尚未生成</strong><p>点击左侧按钮生成真实节点、关系和版本记录。</p>
        </div>
      </div>

      <div class="canvas-mobile-list">
        <header><div class="mobile-progress-ring"><strong>{{ displayedCanvas.nodes.length }}</strong><small>节点</small></div><dl><div><dt class="mastered" /> <dd>已掌握<strong>{{ statusCounts.MASTERED }}</strong></dd></div><div><dt class="learning" /> <dd>学习中<strong>{{ statusCounts.LEARNING }}</strong></dd></div><div><dt class="validation" /> <dd>待验证<strong>{{ statusCounts.PENDING_VALIDATION }}</strong></dd></div></dl></header>
        <section v-if="currentCanvas.nodes.length === 1" class="mobile-generation-card">
          <span><Sparkles :size="20" /></span>
          <div><strong>{{ generationActive || generationPolling ? generationStatus : '生成完整能力树' }}</strong><p>基于已确认画像生成能力域、技能、任务和证据分支。</p></div>
          <button
            type="button"
            :disabled="generationActive || generationPolling || Boolean(pending)"
            :aria-label="generationActive || generationPolling ? generationStatus : '生成完整能力树'"
            @click="generate"
          ><LoaderCircle v-if="generationActive || generationPolling" class="cp-spin" :size="17" /><ChevronRight v-else :size="18" /></button>
          <div v-if="generationTask" class="mobile-generation-progress"><i :style="{ width: `${generationProgress}%` }" /><em>{{ generationProgress }}%</em></div>
          <div class="mobile-generation-scales" role="radiogroup" aria-label="能力画布初始化规模"><button v-for="item in generationScales" :key="item.value" type="button" role="radio" :aria-checked="generationScale === item.value" :class="{ active: generationScale === item.value }" @click="generationScale = item.value">{{ item.label }}<small>{{ item.range }}</small></button></div>
        </section>
        <div class="mobile-canvas-filters">
          <div><button type="button" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">全部</button><button type="button" :class="{ active: statusFilter === 'LEARNING' }" @click="statusFilter = 'LEARNING'">学习中</button><button type="button" :class="{ active: statusFilter === 'PENDING_VALIDATION' }" @click="statusFilter = 'PENDING_VALIDATION'">待验证</button><button type="button" :class="{ active: mobileSelectionMode }" @click="mobileSelectionMode = !mobileSelectionMode"><ListChecks :size="15" />批量</button><button type="button" aria-label="折叠到能力域" @click="collapseToDomains"><ChevronsDown :size="15" />折叠</button><button type="button" aria-label="展开全部节点" @click="expandAllNodes"><ChevronsUp :size="15" />展开</button></div>
        </div>
        <div ref="mobileTreeViewport" class="mobile-tree-viewport" tabindex="0" aria-label="能力节点分层列表" @scroll="onMobileTreeScroll">
          <div class="mobile-tree-spacer" :style="{ height: `${mobileVirtualRange.totalHeight}px` }">
            <div class="mobile-tree" :style="{ transform: `translateY(${mobileVirtualRange.offsetTop}px)` }">
              <div
                v-for="row in virtualMobileRows"
                :key="row.node.logicalNodeId"
                class="mobile-tree-row"
                :class="{ selected: selectedNodeIds.has(row.node.logicalNodeId), domain: row.node.type === 'DOMAIN' || row.node.type === 'CAREER', selectable: mobileSelectionMode }"
                :style="{ '--tree-level': row.level }"
              >
                <button
                  class="mobile-tree-row__main"
                  type="button"
                  :aria-label="mobileSelectionMode ? `选择${row.node.title}` : `查看${row.node.title}详情，${nodeTypeLabel(row.node.type)}，${statusLabel(row.node.status)}`"
                  @click="handleMobileNode(row.node)"
                >
                  <i v-if="mobileSelectionMode && row.node.type !== 'CAREER'" class="mobile-selection-mark" aria-hidden="true">{{ selectedNodeIds.has(row.node.logicalNodeId) ? '✓' : '' }}</i>
                  <component v-else :is="nodeIcon(row.node.type)" :size="19" />
                  <span><strong>{{ row.node.title }}</strong><small>{{ nodeTypeLabel(row.node.type) }}</small></span>
                  <em :class="row.node.status.toLowerCase()">{{ statusLabel(row.node.status) }}</em>
                </button>
                <button
                  v-if="row.childCount"
                  class="mobile-tree-row__toggle"
                  type="button"
                  :aria-label="`${row.expanded ? '收起' : '展开'}${row.node.title}下级节点`"
                  @click="toggleExpanded(row.node.logicalNodeId)"
                ><component :is="row.expanded ? ChevronDown : ChevronRight" :size="18" /></button>
                <ChevronRight v-else class="mobile-tree-row__leaf" :size="18" aria-hidden="true" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <Transition name="drawer">
      <aside v-if="selectedNode" class="node-drawer" aria-label="能力节点详情">
        <header><div><small>{{ nodeTypeLabel(selectedNode.type) }}</small><h2>{{ editorTitle }}</h2></div><button type="button" aria-label="关闭节点详情" @click="selectedNodeId = ''"><X :size="20" /></button></header>
        <template v-if="editorMode === 'VIEW'">
          <div class="node-status-head"><span :class="selectedNode.status.toLowerCase()">{{ statusLabel(selectedNode.status) }}</span><em>v{{ displayedCanvas.version }}</em></div>
          <section><h3>能力说明</h3><p>{{ detailString(selectedNode, 'summary') || '暂无补充说明。' }}</p></section>
          <section v-if="detailLines(selectedNode, 'learningContents')"><h3>学习内容</h3><ul><li v-for="line in detailLines(selectedNode, 'learningContents').split('\n')" :key="line">{{ line }}</li></ul></section>
          <section v-if="detailLines(selectedNode, 'masteryCriteria')"><h3>掌握标准</h3><ul><li v-for="line in detailLines(selectedNode, 'masteryCriteria').split('\n')" :key="line">{{ line }}</li></ul></section>
          <section v-if="detailString(selectedNode, 'notes')"><h3>用户备注</h3><p>{{ detailString(selectedNode, 'notes') }}</p></section>
          <section><h3>上级节点</h3><p>{{ displayedCanvas.nodes.find(item => item.logicalNodeId === parents.get(selectedNode!.logicalNodeId))?.title || '无' }}</p></section>
          <section><h3>前置节点</h3><div v-if="selectedPrerequisites.length" class="node-chips"><button v-for="item in selectedPrerequisites" :key="item.logicalNodeId" type="button" @click="selectNode(item)">{{ item.title }}</button></div><p v-else>暂无前置依赖。</p></section>
          <div v-if="selectedNode.type !== 'CAREER'" class="prerequisite-control">
            <AppSelect v-model="prerequisiteId" aria-label="选择前置节点"><option value="">添加前置节点</option><option v-for="item in prerequisiteOptions" :key="item.logicalNodeId" :value="item.logicalNodeId">{{ item.title }}</option></AppSelect>
            <button type="button" :disabled="!prerequisiteId || Boolean(pending)" @click="addPrerequisite"><Plus :size="16" /></button>
          </div>
          <footer class="node-command-grid">
            <button v-if="selectedNode.type !== 'EVIDENCE'" type="button" :disabled="inferenceActive" @click="openInference(selectedNode)"><Sparkles :size="16" />{{ selectedPendingProposal ? '继续审阅推演' : 'AI 推演' }}</button>
            <button type="button" :disabled="!canValidateSelected" :title="canValidateSelected ? '验证当前能力节点' : '只有技能、知识或任务节点可以验证'" @click="openValidation(selectedNode)"><ShieldCheck :size="16" />AI 验证</button>
            <button v-if="selectedNode.type !== 'EVIDENCE'" type="button" @click="startAdd(selectedNode)"><Plus :size="16" />新增下级</button>
            <button v-if="selectedNode.type !== 'CAREER' && selectedNode.type !== 'EVIDENCE'" type="button" :disabled="selectedNode.locked" @click="startSplit(selectedNode)"><ListTree :size="16" />拆分</button>
            <button v-if="selectedNode.type !== 'CAREER'" type="button" :disabled="selectedNode.locked || !mergeCandidates.length" @click="startMerge(selectedNode)"><GitMerge :size="16" />合并</button>
            <button v-if="selectedNode.type !== 'CAREER'" type="button" @click="startEdit(selectedNode)"><MoreHorizontal :size="16" />编辑</button>
            <button v-if="selectedNode.type !== 'CAREER'" class="danger" type="button" @click="removeNode"><Trash2 :size="16" />{{ deleteArmed ? '确认级联删除' : '删除' }}</button>
          </footer>
        </template>

        <form v-else-if="editorMode === 'INFERENCE'" class="node-editor inference-editor" @submit.prevent="startInference">
          <p class="inference-guidance"><Sparkles :size="19" /><span><strong>只形成可审阅候选</strong>不会修改、移动或删除现有节点，也不会把任何能力直接标记为已掌握。</span></p>
          <section class="inference-target"><small>推演起点</small><strong>{{ selectedNode.title }}</strong><span>{{ nodeTypeLabel(selectedNode.type) }} · 画布 v{{ currentCanvas.version }}</span></section>
          <fieldset class="inference-options"><legend>推演方向</legend><button v-for="item in availableInferenceDirections" :key="item.value" type="button" :disabled="item.disabled" :aria-pressed="inferenceDirection === item.value" :class="{ active: inferenceDirection === item.value }" @click="inferenceDirection = item.value"><GitBranch :size="17" /><span><strong>{{ item.label }}</strong><small>{{ item.copy }}</small></span></button></fieldset>
          <fieldset class="inference-depth"><legend>展开深度</legend><button type="button" :class="{ active: inferenceDepth === 'ONE_LEVEL' }" @click="inferenceDepth = 'ONE_LEVEL'"><strong>单层</strong><small>最多 6 个新增节点</small></button><button type="button" :class="{ active: inferenceDepth === 'FULL_BRANCH' }" @click="inferenceDepth = 'FULL_BRANCH'"><strong>完整分支</strong><small>最多 14 个新增节点</small></button></fieldset>
          <label>补充要求（可选）<textarea v-model="inferenceInstruction" rows="3" maxlength="1000" placeholder="例如：更偏重可验证的项目实践，不要扩展管理能力"></textarea><small>{{ inferenceInstruction.length }} / 1000</small></label>
          <Transition name="assistant-copy"><div v-if="inferenceTask" class="canvas-generation-status inference-task-status" role="status"><span><i :style="{ width: `${inferenceProgress}%` }" /></span><p><strong>{{ inferenceStatus }}</strong><em>{{ inferenceProgress }}%</em></p><small>任务可在后台继续，刷新页面后会自动恢复。</small></div></Transition>
          <footer><button type="button" @click="editorMode = 'VIEW'">返回详情</button><button class="save" type="submit" :disabled="inferenceActive || inferencePolling || pending === 'inference'"><LoaderCircle v-if="inferenceActive || inferencePolling" class="cp-spin" :size="16" /><Sparkles v-else :size="16" />{{ inferenceActive || inferencePolling ? inferenceStatus : '开始 AI 推演' }}</button></footer>
        </form>

        <form v-else-if="editorMode === 'SPLIT'" class="node-editor node-structure-editor" @submit.prevent="confirmSplit">
          <p class="structure-guidance"><ListTree :size="18" /><span><strong>保留当前节点作为上级</strong>新增 2–8 个可继续编辑的结构化下级，不删除原内容和来源。</span></p>
          <div class="split-items">
            <fieldset v-for="(item, index) in splitItems" :key="item.id">
              <legend>下级节点 {{ index + 1 }}</legend>
              <button type="button" aria-label="移除该拆分节点" :disabled="splitItems.length <= 2" @click="removeSplitItem(item.id)"><X :size="15" /></button>
              <label>节点类型<AppSelect v-model="item.type" :aria-label="`拆分节点 ${index + 1} 类型`"><option v-for="option in nodeTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></AppSelect></label>
              <label>节点名称<input v-model="item.title" maxlength="120" :placeholder="`填写第 ${index + 1} 个具体能力`"></label>
              <label>能力说明<textarea v-model="item.summary" rows="2" maxlength="800" placeholder="说明学习边界或可验证产出"></textarea></label>
            </fieldset>
          </div>
          <button class="add-structure-item" type="button" :disabled="splitItems.length >= 8" @click="addSplitItem"><Plus :size="16" />继续添加下级</button>
          <section class="structure-impact"><strong>变更影响</strong><span>当前节点保留</span><span>新增 {{ splitItems.length }} 个节点</span><span>生成画布 v{{ currentCanvas.version + 1 }}</span></section>
          <footer><button type="button" @click="editorMode = 'VIEW'">取消</button><button class="save" type="submit" :disabled="pending === 'split-node'"><LoaderCircle v-if="pending === 'split-node'" class="cp-spin" :size="16" /><ListTree v-else :size="16" />确认拆分</button></footer>
        </form>

        <form v-else-if="editorMode === 'MERGE'" class="node-editor node-structure-editor" @submit.prevent="confirmMerge">
          <p class="structure-guidance"><GitMerge :size="18" /><span><strong>保留“{{ selectedNode.title }}”的节点身份</strong>其他节点的下级、依赖和来源会迁移到该节点，旧版本不受影响。</span></p>
          <label>合并后的名称<input v-model="mergeTitle" maxlength="120" placeholder="填写统一后的能力名称"></label>
          <label>合并后的说明<textarea v-model="mergeSummary" rows="3" maxlength="800" placeholder="概括合并后能力的边界"></textarea></label>
          <section class="merge-candidates">
            <header><strong>选择同级节点</strong><em>已选 {{ mergeNodeIds.length }} / 8</em></header>
            <label class="selected-primary"><input type="checkbox" checked disabled><span><strong>{{ selectedNode.title }}</strong><small>保留此节点 ID 与当前状态</small></span></label>
            <label v-for="item in mergeCandidates" :key="item.logicalNodeId">
              <input type="checkbox" :checked="mergeNodeIds.includes(item.logicalNodeId)" :disabled="!mergeNodeIds.includes(item.logicalNodeId) && mergeNodeIds.length >= 8" @change="toggleMergeNode(item.logicalNodeId)">
              <span><strong>{{ item.title }}</strong><small>{{ statusLabel(item.status) }} · {{ item.sourceRefs.length }} 项来源</small></span>
            </label>
          </section>
          <section class="structure-impact"><strong>变更影响</strong><span>保留 1 个主节点</span><span>移除 {{ Math.max(0, mergeNodeIds.length - 1) }} 个当前节点</span><span>下级与依赖自动迁移</span></section>
          <footer><button type="button" @click="editorMode = 'VIEW'">取消</button><button class="save" type="submit" :disabled="pending === 'merge-nodes' || mergeNodeIds.length < 2"><LoaderCircle v-if="pending === 'merge-nodes'" class="cp-spin" :size="16" /><GitMerge v-else :size="16" />确认合并</button></footer>
        </form>

        <form v-else class="node-editor" @submit.prevent="saveNode">
          <label v-if="editorMode === 'ADD'">节点类型<AppSelect v-model="form.type" aria-label="节点类型"><option v-for="option in nodeTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option></AppSelect></label>
          <label>节点名称<input v-model="form.title" maxlength="120" placeholder="例如 Spring Boot"></label>
          <label>当前状态<AppSelect v-model="form.status" aria-label="节点状态"><option v-for="option in editableStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option></AppSelect></label>
          <label>上级节点<AppSelect v-model="form.parentNodeId" aria-label="上级节点"><option value="">请选择上级节点</option><option v-for="item in parentOptions" :key="item.logicalNodeId" :value="item.logicalNodeId">{{ item.title }}</option></AppSelect></label>
          <label>能力说明<textarea v-model="form.summary" rows="3" maxlength="800" placeholder="说明这项能力的用途和边界" /></label>
          <div class="node-editor-grid"><label>重要程度<AppSelect v-model="form.importance" aria-label="重要程度"><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></AppSelect></label><label>预计小时<input v-model="form.estimatedHours" type="number" min="0" max="1000"></label></div>
          <label>学习内容<textarea v-model="form.learningContents" rows="4" placeholder="每行一项"></textarea></label>
          <label>掌握标准<textarea v-model="form.masteryCriteria" rows="4" placeholder="每行一项；通过验证后才能标记为已掌握"></textarea></label>
          <label>用户备注<textarea v-model="form.notes" rows="3" maxlength="1000"></textarea></label>
          <label class="node-lock"><input v-model="form.locked" type="checkbox"><span><LockKeyhole :size="16" />锁定节点，AI 提案不得覆盖</span></label>
          <footer><button type="button" @click="editorMode = 'VIEW'">取消</button><button class="save" type="submit" :disabled="pending === 'save-node'"><LoaderCircle v-if="pending === 'save-node'" class="cp-spin" :size="16" />保存为新版本</button></footer>
        </form>
      </aside>
    </Transition>

  </section>
</template>

<style scoped>
.career-vue-flow :deep(.cp-flow-node--path .flow-card){border-color:#81a7ea;background:#f5f9ff}
.career-vue-flow :deep(.cp-flow-node--muted .flow-card){background:#fbfcfd;box-shadow:none}
.career-canvas-shell{position:relative;display:grid;grid-template-columns:292px minmax(0,1fr);height:calc(100vh - 76px);min-height:680px;background:#fff;overflow:hidden;color:#14213f}.career-canvas-assistant{display:flex;flex-direction:column;gap:18px;padding:24px 20px;border-right:1px solid #e3e9f3;background:#fbfdff}.career-canvas-assistant>header{display:flex;align-items:center;gap:11px}.career-canvas-assistant>header>span{display:grid;place-items:center;width:38px;height:38px;border-radius:10px;color:var(--primary);background:#eaf2ff}.career-canvas-assistant header div{display:grid}.career-canvas-assistant header small{margin-top:2px;color:var(--text-3)}.career-canvas-assistant p{margin:0;color:#50607b;line-height:1.75}.assistant-summary{display:grid;gap:10px;padding:16px;border:1px solid #e0e9f7;border-radius:8px;background:#f4f8ff}.assistant-summary span{font-size:13px;color:#6e7e98}.canvas-primary{display:flex;align-items:center;justify-content:center;gap:8px;min-height:42px;border:0;border-radius:7px;color:white;background:var(--primary);cursor:pointer}.canvas-primary:disabled{opacity:.55;cursor:wait}.assistant-actions{display:grid;gap:8px}.assistant-actions button,.career-canvas-assistant footer{display:flex;align-items:center;gap:8px}.assistant-actions button{min-height:40px;padding:0 12px;border:1px solid #dce5f3;border-radius:7px;color:#315079;background:#fff;cursor:pointer}.assistant-actions button:disabled{opacity:.45}.career-canvas-assistant footer{margin-top:auto;color:#7887a0;font-size:12px}.career-canvas-workspace{position:relative;display:flex;flex-direction:column;min-width:0;min-height:0;overflow:hidden}.canvas-meta{display:flex;align-items:center;gap:36px;min-height:64px;padding:0 24px;border-bottom:1px solid #e5ebf4;background:#fff}.canvas-meta>div{display:grid;gap:2px}.canvas-meta small{color:var(--text-3);font-size:12px}.canvas-meta strong{font-size:14px}.canvas-meta button{display:flex;align-items:center;gap:7px;margin-left:auto;border:0;color:#47628d;background:transparent;cursor:pointer}.history-preview-bar{display:flex;align-items:center;gap:8px;padding:8px 20px;color:#785616;background:#fff7df;border-bottom:1px solid #f2dfa6;font-size:13px}.history-preview-bar button{display:flex;align-items:center;gap:5px;margin-left:auto;border:0;color:var(--primary);background:transparent;cursor:pointer}.canvas-toolbar{position:relative;z-index:6;display:flex;align-items:center;gap:10px;min-height:62px;flex:0 0 auto;padding:10px 20px;border-bottom:1px solid #e8edf5;background:#fff}.canvas-search{display:flex;align-items:center;gap:8px;width:min(320px,30vw);height:38px;padding:0 11px;border:1px solid #d7e0ec;border-radius:8px;color:var(--text-3);background:#fff}.canvas-search:focus-within{border-color:var(--primary);box-shadow:0 0 0 3px #2563eb16}.canvas-search input{min-width:0;width:100%;border:0;outline:0;background:transparent}.canvas-toolbar :deep(.app-select){width:150px}.canvas-toolbar>button{display:flex;align-items:center;gap:6px;height:38px;padding:0 12px;border:1px solid #dbe3ef;border-radius:8px;color:#49617f;background:#fff;cursor:pointer}.canvas-toolbar>button:hover,.canvas-toolbar>button.active{color:var(--primary);border-color:#a9c7ff;background:#f4f8ff}.canvas-toolbar>button:disabled{opacity:.45;cursor:not-allowed}.canvas-toolbar .icon-command{width:38px;padding:0;justify-content:center}.canvas-status-legend{position:absolute;z-index:4;left:18px;bottom:18px;display:flex;gap:8px;padding:8px;border:1px solid #dfe7f2;border-radius:8px;background:#ffffffeb;box-shadow:0 8px 24px #284e7e12}.canvas-status-legend span{display:flex;align-items:center;gap:5px;color:#65748c;font-size:12px}.canvas-status-legend span:before{content:'';width:7px;height:7px;border-radius:50%;background:#aab5c6}.canvas-status-legend .mastered:before,.mobile-progress-ring:before,.mobile-canvas-list .mastered{background:#19b56b}.canvas-status-legend .learning:before,.mobile-canvas-list .learning{background:var(--primary)}.canvas-status-legend .planned:before{background:#aab5c6}.canvas-status-legend .validation:before,.mobile-canvas-list .validation{background:#ff962e}.canvas-desktop-stage{position:relative;min-height:0;flex:1;overflow:hidden;isolation:isolate;background:#fbfdff}.career-vue-flow{width:100%;height:100%;min-height:420px;background:#fbfdff}.flow-card{display:flex;align-items:center;gap:9px;width:224px;min-height:56px;padding:9px 12px;border:1px solid #d9e2ee;border-radius:8px;text-align:left;color:#1d2d4c;background:#fff;box-shadow:var(--shadow-s);cursor:pointer}.flow-card:hover,.flow-card.selected{border-color:var(--primary);box-shadow:0 8px 22px #2563eb20}.flow-card>span:nth-of-type(2){display:grid;min-width:0;flex:1}.flow-card strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px}.flow-card small{margin-top:3px;color:var(--text-3);font-size:12px}.flow-card--career{width:236px;min-height:72px;padding:13px 16px;border:0;color:#fff;background:var(--primary);box-shadow:0 12px 30px #2563eb35}.flow-card--career small{color:#dbe8ff}.flow-card__status{width:8px;height:8px;border-radius:50%;background:#aab5c6}.flow-card__status.learning{background:var(--primary)}.flow-card__status.mastered{background:#20b970}.flow-card__status.pending_validation{background:#ff982e}.flow-card__status.paused{background:#8465d5}.flow-card--domain{min-height:62px;border-left:3px solid #13a97f}.flow-card--task{border-left:3px solid #ff982e}.flow-card--evidence{border-left:3px solid #8c61d5}.career-vue-flow :deep(.vue-flow__handle){width:7px;height:7px;border:1px solid #8eaae9;background:#fff;opacity:0}.career-vue-flow :deep(.vue-flow__node:hover .vue-flow__handle){opacity:1}.career-vue-flow :deep(.vue-flow__minimap){right:14px;bottom:58px;width:160px;height:100px;border:1px solid #dce5f2;border-radius:8px;background:#fff;box-shadow:0 8px 24px #274c7715}.career-vue-flow :deep(.vue-flow__controls){border:1px solid #dce5f2;border-radius:8px;overflow:hidden;box-shadow:none}.canvas-empty-callout{position:absolute;left:50%;top:50%;display:grid;place-items:center;gap:7px;width:320px;padding:24px;transform:translate(-50%,-50%);border:1px dashed #bfd0e5;border-radius:8px;text-align:center;color:#6f7f98;background:#ffffffdc}.canvas-empty-callout strong{color:#29415f}.canvas-empty-callout p{margin:0;font-size:12px;line-height:1.6}.canvas-mobile-list{display:none}.node-drawer,.history-drawer{position:absolute;z-index:20;right:0;top:0;width:min(420px,100%);height:100%;padding:0 22px;background:#fff;border-left:1px solid #dfe7f2;box-shadow:-18px 0 46px #29496e1c;overflow:auto}.node-drawer>header,.history-drawer>header{position:sticky;z-index:2;top:0;display:flex;justify-content:space-between;align-items:center;min-height:74px;border-bottom:1px solid #e8edf4;background:#fff}.node-drawer h2,.history-drawer h2{margin:3px 0 0;font-size:20px}.node-drawer header small,.history-drawer header small{color:#7787a0}.node-drawer header button,.history-drawer header button{display:grid;place-items:center;width:36px;height:36px;border:0;border-radius:50%;background:#f5f7fa;cursor:pointer}.node-status-head{display:flex;justify-content:space-between;align-items:center;padding:18px 0}.node-status-head span{padding:5px 9px;border-radius:5px;color:var(--primary);background:#edf4ff;font-size:12px}.node-status-head span.mastered{color:#0d9154;background:#eaf9f1}.node-status-head span.pending_validation{color:#c26b0d;background:#fff3e3}.node-status-head em{font-style:normal;color:#8b97aa}.node-drawer section{padding:14px 0;border-bottom:1px solid #edf1f6}.node-drawer section h3{margin:0 0 8px;font-size:14px}.node-drawer section p,.node-drawer section li{color:#5d6d85;font-size:13px;line-height:1.7}.node-drawer section p{margin:0}.node-drawer section ul{margin:0;padding-left:18px}.node-chips{display:flex;flex-wrap:wrap;gap:7px}.node-chips button{padding:5px 9px;border:1px solid #dbe5f3;border-radius:5px;color:#456187;background:#f7faff;cursor:pointer}.prerequisite-control{display:grid;grid-template-columns:1fr 38px;gap:8px;margin:14px 0}.prerequisite-control>button{border:0;border-radius:7px;color:#fff;background:var(--primary)}.node-drawer>footer,.node-editor>footer{position:sticky;bottom:0;display:flex;gap:8px;padding:14px 0;background:#fff}.node-drawer>footer button,.node-editor>footer button{display:flex;align-items:center;justify-content:center;gap:6px;min-height:38px;padding:0 12px;border:1px solid #dce5f1;border-radius:7px;color:#49617f;background:#fff;cursor:pointer}.node-drawer>footer button.danger{margin-left:auto;color:#d83d4c;border-color:#f0c9ce}.node-editor{display:grid;gap:14px;padding:18px 0}.node-editor>label,.node-editor-grid label{display:grid;gap:7px;color:#475873;font-size:13px}.node-editor input:not([type=checkbox]),.node-editor textarea{width:100%;padding:10px 11px;border:1px solid #d6dfec;border-radius:8px;outline:0;resize:vertical}.node-editor input:focus,.node-editor textarea:focus{border-color:var(--primary);box-shadow:0 0 0 3px #2563eb12}.node-editor-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.node-lock{display:flex!important;align-items:center;gap:8px!important;padding:12px;border-radius:8px;background:#f6f8fc}.node-lock span{display:flex;align-items:center;gap:7px}.node-editor>footer .save{flex:1;color:#fff;border-color:var(--primary);background:var(--primary)}.history-drawer ol{list-style:none;margin:0;padding:18px 0}.history-drawer li{position:relative;padding:0 0 18px 26px}.history-drawer li:before{content:'';position:absolute;left:5px;top:10px;bottom:-4px;width:1px;background:#dce5f2}.history-drawer li:last-child:before{display:none}.history-drawer li>span{position:absolute;left:0;top:7px;width:11px;height:11px;border:2px solid var(--primary);border-radius:50%;background:#fff}.history-drawer li.current>span{background:#19b56b;border-color:#19b56b}.history-drawer article{padding:14px;border:1px solid #e0e7f1;border-radius:8px}.history-drawer article header{display:flex;justify-content:space-between}.history-drawer article em{padding:3px 7px;border-radius:4px;color:var(--primary);background:#edf4ff;font-size:12px;font-style:normal}.history-drawer article p{margin:9px 0;color:#586982;line-height:1.55}.history-drawer article time{display:block;color:var(--text-3);font-size:12px}.history-drawer article button{margin-top:10px;padding:6px 10px;border:1px solid #b8cff8;border-radius:6px;color:var(--primary);background:#fff;cursor:pointer}.history-drawer>footer{display:flex;gap:8px;padding:14px;color:#7d6740;background:#fff9e9;font-size:12px;line-height:1.5}.drawer-loading{display:flex;align-items:center;justify-content:center;gap:8px;height:160px;color:#718098}.drawer-enter-active,.drawer-leave-active{transition:transform .28s ease,opacity .22s ease}.drawer-enter-from,.drawer-leave-to{transform:translateX(24px);opacity:0}
@media(max-width:900px){.career-canvas-shell{display:block;height:auto;min-height:calc(100vh - 112px);overflow:visible}.career-canvas-assistant{display:none}.career-canvas-workspace{display:block;overflow:visible}.canvas-meta{min-height:62px;padding:0 12px;gap:22px}.canvas-meta>div:nth-child(2){display:none}.canvas-meta button{font-size:0}.canvas-toolbar{padding:8px 12px}.canvas-toolbar>*{display:none!important}.canvas-toolbar .canvas-search{display:flex!important;width:100%;height:42px}.canvas-status-legend,.canvas-desktop-stage{display:none}.canvas-mobile-list{display:block;padding:0 12px 100px;background:var(--bg)}.canvas-mobile-list>header{display:grid;grid-template-columns:96px 1fr;align-items:center;gap:14px;padding:16px 10px;margin:0 -12px 12px;background:#fff;border-bottom:1px solid #e7edf5}.mobile-progress-ring{display:grid;place-items:center;align-content:center;width:82px;height:82px;border-radius:50%;background:radial-gradient(circle at center,#fff 58%,transparent 60%),conic-gradient(var(--primary) 0 62%,#18b86d 62% 78%,#ff982e 78% 88%,#e6ecf5 88%)}.mobile-progress-ring strong{font-size:22px}.mobile-progress-ring small{color:#77869c}.canvas-mobile-list dl{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:0}.canvas-mobile-list dl div{display:grid;grid-template-columns:8px 1fr;gap:6px}.canvas-mobile-list dt{width:8px;height:8px;margin-top:4px;border-radius:50%}.canvas-mobile-list dd{display:grid;margin:0;color:#71809a;font-size:12px}.canvas-mobile-list dd strong{margin-top:3px;color:#192c4b;font-size:18px}.mobile-canvas-filters{display:grid;gap:10px}.mobile-canvas-filters>label{display:flex;align-items:center;gap:8px;height:46px;padding:0 12px;border:1px solid #d8e1ed;border-radius:8px;color:#8190a7;background:#fff}.mobile-canvas-filters input{min-width:0;flex:1;border:0;outline:0}.mobile-canvas-filters>div{display:flex;gap:8px;overflow-x:auto;overflow-y:hidden;overscroll-behavior-inline:contain;scrollbar-width:none;-ms-overflow-style:none}.mobile-canvas-filters>div::-webkit-scrollbar{display:none}.mobile-canvas-filters button{flex:0 0 auto;min-height:36px;padding:0 15px;border:1px solid #dce4ef;border-radius:18px;color:#61728b;background:#fff}.mobile-canvas-filters button.active{color:var(--primary);border-color:#9ebeff;background:#edf4ff}.mobile-tree{display:grid;gap:7px;margin-top:12px}.mobile-tree-row{display:flex;align-items:center;gap:9px;min-height:54px;margin-left:calc(var(--tree-level) * 15px);padding:8px 10px;border:1px solid #dfe6f0;border-radius:8px;text-align:left;color:#304361;background:#fff}.mobile-tree-row.domain{margin-top:4px;border-color:#cbd9ed;background:#fbfdff}.mobile-tree-row.selected{border-color:var(--primary);box-shadow:0 0 0 2px #2563eb12}.mobile-tree-row>span{display:grid;min-width:0;flex:1}.mobile-tree-row strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mobile-tree-row small{margin-top:2px;color:#8a97a9;font-size:12px}.mobile-tree-row em{padding:4px 6px;border-radius:4px;color:#7b889c;background:#f0f3f7;font-size:12px;font-style:normal}.mobile-tree-row em.learning{color:var(--primary);background:#edf4ff}.mobile-tree-row em.mastered{color:#0d9154;background:#eaf9f1}.mobile-tree-row em.pending_validation{color:#c66a08;background:#fff3e3}.node-drawer,.history-drawer{position:fixed;top:auto;bottom:0;width:100%;height:min(72vh,680px);padding:0 18px;border-left:0;border-top:1px solid #dbe4f0;border-radius:16px 16px 0 0;box-shadow:0 -18px 50px #233d6425}.node-drawer:before,.history-drawer:before{content:'';display:block;width:44px;height:4px;margin:8px auto 0;border-radius:2px;background:#c9d3e2}.node-drawer>header,.history-drawer>header{min-height:62px}.drawer-enter-from,.drawer-leave-to{transform:translateY(24px)}.node-editor-grid{grid-template-columns:1fr}}
@media(prefers-reduced-motion:reduce){.drawer-enter-active,.drawer-leave-active{transition:none}}
.canvas-generation-status{display:grid;gap:8px;padding:12px;border:1px solid #cfe0fb;border-radius:8px;background:#f7faff}.canvas-generation-status>span{height:5px;overflow:hidden;border-radius:3px;background:#dfe8f5}.canvas-generation-status>span i{display:block;height:100%;border-radius:inherit;background:var(--primary);transition:width .35s ease}.canvas-generation-status p{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:0;color:#365272;font-size:12px}.canvas-generation-status p em{color:var(--primary);font-style:normal}.canvas-generation-status small{color:#71829a;font-size:12px;line-height:1.55}.mobile-generation-card,.mobile-generation-progress{display:none}
@media(max-width:900px){.mobile-generation-card{display:grid;grid-template-columns:38px minmax(0,1fr) 36px;align-items:center;gap:10px;margin:0 0 12px;padding:13px;border:1px solid #cbdcf6;border-radius:10px;color:#274566;background:#fff;box-shadow:0 8px 24px #2c5d9a0d}.mobile-generation-card>span{display:grid;place-items:center;width:38px;height:38px;border-radius:9px;color:var(--primary);background:#edf4ff}.mobile-generation-card>div{min-width:0}.mobile-generation-card strong{display:block;color:#1e385c;font-size:14px}.mobile-generation-card p{margin:4px 0 0;color:#708099;font-size:12px;line-height:1.5}.mobile-generation-card>button{display:grid;place-items:center;width:36px;height:36px;border:1px solid #b9d0f7;border-radius:50%;color:var(--primary);background:#f7faff}.mobile-generation-card>button:disabled{opacity:.55}.mobile-generation-progress{grid-column:1/-1;display:grid;grid-template-columns:1fr auto;align-items:center;gap:8px;height:16px;margin-top:1px}.mobile-generation-progress:before{content:'';grid-column:1;grid-row:1;height:5px;border-radius:3px;background:#e3eaf4}.mobile-generation-progress i{z-index:1;grid-column:1;grid-row:1;display:block;height:5px;border-radius:3px;background:var(--primary);transition:width .35s ease}.mobile-generation-progress em{grid-column:2;grid-row:1;color:var(--primary);font-size:12px;font-style:normal}}
@media(prefers-reduced-motion:reduce){.canvas-generation-status>span i,.mobile-generation-progress i{transition:none}}
.node-command-grid{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr));align-items:stretch}.node-command-grid button{width:100%;padding-inline:8px!important}.node-command-grid button:disabled{opacity:.42;cursor:not-allowed}.node-command-grid .danger{grid-column:1/-1;margin-left:0!important}.node-structure-editor{padding-bottom:88px}.structure-guidance{display:flex;gap:10px;margin:0;padding:12px;border:1px solid #cfe0f8;border-radius:9px;color:#48617f;background:#f5f9ff;line-height:1.55}.structure-guidance>svg{flex:0 0 auto;margin-top:2px;color:var(--primary)}.structure-guidance span{display:grid;gap:2px}.split-items{display:grid;gap:10px}.split-items fieldset{position:relative;display:grid;gap:10px;margin:0;padding:13px;border:1px solid #dce5f1;border-radius:9px}.split-items legend{padding:0 5px;color:#2d4669;font-size:12px;font-weight:700}.split-items fieldset>button{position:absolute;right:8px;top:8px;display:grid;place-items:center;width:28px;height:28px;border:0;border-radius:50%;color:#7f8da2;background:#f3f6fa}.split-items fieldset>button:disabled{opacity:.25}.split-items label{display:grid;gap:6px;color:#52637c;font-size:12px}.add-structure-item{display:flex;align-items:center;justify-content:center;gap:6px;min-height:38px;border:1px dashed #a9c4ec;border-radius:8px;color:var(--primary);background:#f8fbff}.add-structure-item:disabled{opacity:.45}.structure-impact{display:grid!important;grid-template-columns:1fr 1fr;gap:7px;padding:12px!important;border:1px solid #e1e8f2!important;border-radius:8px;background:#fafcff}.structure-impact strong{grid-column:1/-1}.structure-impact span{color:#647792;font-size:12px}.structure-impact span:before{display:inline-block;width:6px;height:6px;margin-right:6px;border-radius:50%;background:var(--primary);content:''}.merge-candidates{display:grid!important;gap:7px;padding:0!important;border-bottom:0!important}.merge-candidates>header{display:flex;align-items:center;justify-content:space-between}.merge-candidates header em{color:var(--primary);font-size:12px;font-style:normal}.merge-candidates label{display:flex!important;align-items:center;gap:9px;padding:10px;border:1px solid #dfe6f0;border-radius:8px;color:#3f5575;background:#fff}.merge-candidates label:has(input:checked){border-color:#9bbcf2;background:#f7faff}.merge-candidates input{width:17px;height:17px;accent-color:var(--primary)}.merge-candidates label span{display:grid;gap:3px}.merge-candidates label small{color:#7d8ba0}.merge-candidates .selected-primary{border-color:#80adef;background:#edf5ff}
@media(max-width:900px){.node-command-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.node-structure-editor{padding-bottom:104px}.structure-impact{grid-template-columns:1fr}}
.canvas-toolbar{gap:7px;padding-inline:14px}.canvas-search{min-width:160px;max-width:320px;flex:1;width:auto}.canvas-toolbar :deep(.app-select){width:142px}.canvas-toolbar .icon-command{flex:0 0 38px}.flow-collapse-toggle{position:absolute;z-index:3;right:-13px;top:50%;display:grid;place-items:center;width:26px;height:26px;padding:0;transform:translateY(-50%);border:1px solid #cbd9ec;border-radius:50%;color:#55739c;background:#fff;box-shadow:0 4px 12px #28496f18;cursor:pointer}.flow-collapse-toggle:hover{color:var(--primary);border-color:#8fb4f2}
.canvas-tool-toggle{display:grid;grid-template-columns:repeat(2,38px);height:38px;padding:2px;border:1px solid #dbe3ef;border-radius:8px;background:#f5f7fb}.canvas-tool-toggle button{display:grid;place-items:center;width:34px;height:32px;padding:0;border:0;border-radius:6px;color:#6d7d94;background:transparent;cursor:pointer}.canvas-tool-toggle button span{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}.canvas-tool-toggle button:hover,.canvas-tool-toggle button.active{color:var(--primary);background:#fff;box-shadow:0 1px 5px rgba(38,62,96,.12)}.canvas-tool-toggle button:focus-visible{outline:2px solid rgba(37,99,235,.42);outline-offset:2px}
@media(max-width:900px){.canvas-toolbar .canvas-search{max-width:none}.mobile-canvas-filters button{display:flex;align-items:center;gap:5px}}
.canvas-batch-bar{display:flex;align-items:center;gap:8px;min-height:54px;padding:8px 14px;border-bottom:1px solid #cfe0f8;background:#f5f9ff}.canvas-batch-bar>div{display:grid;grid-template-columns:auto auto;align-items:center;gap:1px 7px;margin-right:auto;color:#29496e}.canvas-batch-bar>div>svg{grid-row:1/3;color:var(--primary)}.canvas-batch-bar>div small{color:#71829a;font-size:12px}.canvas-batch-bar :deep(.app-select){width:132px}.canvas-batch-bar>button{display:flex;align-items:center;justify-content:center;gap:5px;height:36px;padding:0 10px;border:1px solid #bed0e8;border-radius:7px;color:#365879;background:#fff}.canvas-batch-bar>button:disabled{opacity:.42}.canvas-batch-bar .batch-clear{width:36px;padding:0;border-color:transparent;background:transparent}.canvas-batch-bar .batch-clear span{display:none}.career-vue-flow :deep(.vue-flow__node.selected .flow-card){border-color:var(--primary);box-shadow:0 0 0 3px #2563eb22,0 8px 22px #2563eb18}.career-vue-flow :deep(.vue-flow__selection){border:1px solid var(--primary);background:#2563eb12}.mobile-tree-viewport,.mobile-tree-spacer{display:none}
@media(max-width:900px){.canvas-batch-bar{position:sticky;z-index:8;top:0;display:grid;grid-template-columns:1fr repeat(3,44px);gap:6px;padding:8px 12px;border-top:1px solid #d9e6f6}.canvas-batch-bar>div{grid-column:1/-1}.canvas-batch-bar>div small{display:none}.canvas-batch-bar :deep(.app-select){grid-column:1;width:100%}.canvas-batch-bar>button{width:44px;min-height:44px;padding:0}.canvas-batch-bar>button:not(.batch-clear){font-size:0}.canvas-batch-bar .batch-clear{display:flex;min-width:44px}.mobile-tree-viewport{display:block;height:min(52vh,560px);min-height:340px;margin-top:12px;overflow:auto;overscroll-behavior:contain;scrollbar-width:thin;scrollbar-color:#c4d2e6 transparent}.mobile-tree-viewport:focus-visible{outline:2px solid var(--primary);outline-offset:2px}.mobile-tree-spacer{position:relative;display:block}.mobile-tree{position:absolute;left:0;right:0;top:0;display:block;margin-top:0}.mobile-tree-row{width:calc(100% - var(--tree-level) * 15px);height:56px;min-height:56px;margin:0 0 7px calc(var(--tree-level) * 15px)}.mobile-tree-row.domain{margin-top:0}.mobile-tree-row.selectable{cursor:default}.mobile-selection-mark{display:grid;place-items:center;width:19px;height:19px;flex:0 0 19px;border:1px solid #9fb2ce;border-radius:5px;color:#fff;background:#fff;font-size:12px;font-style:normal}.mobile-tree-row.selected .mobile-selection-mark{border-color:var(--primary);background:var(--primary)}}
@media(max-width:900px){.career-canvas-shell{display:flex;height:100%;min-height:0;overflow:hidden}.career-canvas-workspace{display:flex;min-height:0;overflow:hidden}.canvas-meta,.canvas-toolbar{flex:0 0 auto}.canvas-mobile-list{display:flex;min-height:0;flex:1;flex-direction:column;padding-bottom:0;overflow:hidden}.canvas-mobile-list>header,.mobile-canvas-filters{flex:0 0 auto}.mobile-tree-viewport{height:auto;min-height:0;flex:1;scrollbar-width:none;-ms-overflow-style:none}.mobile-tree-viewport::-webkit-scrollbar{display:none}.mobile-tree-viewport:focus-visible{outline-offset:-2px}}
@media(max-width:900px){.mobile-canvas-filters button{min-height:44px;border-radius:22px}.mobile-generation-card{grid-template-columns:44px minmax(0,1fr) 44px}.mobile-generation-card>span,.mobile-generation-card>button{width:44px;height:44px}.node-drawer header button,.history-drawer header button{width:44px;height:44px}.node-drawer>footer button,.node-editor>footer button,.add-structure-item{min-height:44px}.canvas-batch-bar{grid-template-columns:1fr repeat(3,44px) 44px}.canvas-batch-bar>button,.canvas-batch-bar .batch-clear{width:44px;height:44px}.canvas-batch-bar :deep(.app-select){min-height:44px}}
@media(max-width:900px){.mobile-canvas-filters>div{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;overflow:visible}.mobile-canvas-filters button{justify-content:center;width:100%;min-width:0;padding:0 8px;border-radius:9px}}
@media(max-width:900px){.mobile-tree-row{display:grid;grid-template-columns:minmax(0,1fr) 40px;align-items:stretch;padding:0;gap:0;overflow:hidden}.mobile-tree-row__main{display:flex;align-items:center;gap:9px;min-width:0;padding:8px 4px 8px 10px;border:0;text-align:left;color:inherit;background:transparent}.mobile-tree-row__main>span{display:grid;min-width:0;flex:1}.mobile-tree-row__toggle,.mobile-tree-row__leaf{align-self:stretch;width:40px}.mobile-tree-row__toggle{display:grid;place-items:center;border:0;color:#61728b;background:transparent}.mobile-tree-row__leaf{margin:auto;color:#8794a7}.mobile-tree-row__toggle:focus-visible,.mobile-tree-row__main:focus-visible{position:relative;z-index:1;outline:2px solid var(--primary);outline-offset:-2px}}
@media(max-width:900px){.node-drawer,.history-drawer{z-index:60;bottom:calc(68px + env(safe-area-inset-bottom));height:min(72vh,calc(100dvh - 68px - env(safe-area-inset-bottom)))}}
.career-canvas-shell{height:calc(100vh - 180px);min-height:620px}.canvas-toolbar button,.mobile-canvas-filters button,.mobile-tree-row,.flow-card,.assistant-actions button{transition:border-color .18s ease,background-color .18s ease,color .18s ease,box-shadow .18s ease,transform .18s ease}.canvas-toolbar button:focus-visible,.mobile-canvas-filters button:focus-visible,.mobile-tree-row:focus-visible,.flow-card:focus-visible,.assistant-actions button:focus-visible,.node-drawer button:focus-visible,.node-drawer input:focus-visible,.node-drawer textarea:focus-visible{outline:2px solid rgba(37,99,235,.42);outline-offset:2px}.mobile-tree-row:hover,.flow-card:hover{background:#fbfdff}
@media(max-width:900px){.career-canvas-shell{height:100%;min-height:0}.canvas-meta,.canvas-toolbar{display:none}.canvas-mobile-list{padding-top:10px}.mobile-canvas-filters{padding:0 12px 10px}.mobile-canvas-filters>label{box-shadow:0 4px 14px rgba(38,65,103,.04)}.canvas-batch-bar{grid-template-columns:1fr repeat(4,44px) 44px}}
@media(prefers-reduced-motion:reduce){.canvas-toolbar button,.mobile-canvas-filters button,.mobile-tree-row,.flow-card,.assistant-actions button{transition:none}.mobile-tree-row:hover,.flow-card:hover{transform:none}}
.generation-scale{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px}.generation-scale button{display:grid;gap:3px;min-width:0;min-height:58px;padding:8px 4px;border:1px solid #dce5f2;border-radius:8px;color:#4f6380;background:#fff;transition:border-color .18s ease,background-color .18s ease,box-shadow .18s ease,transform .18s ease}.generation-scale button:hover{border-color:#a9c4ef}.generation-scale button.active{color:var(--primary);border-color:#7da9f3;background:#edf4ff;box-shadow:0 0 0 2px #2563eb0d}.generation-scale small{font-size:12px;color:var(--text-3)}.scale-summary{min-height:88px;align-content:center}.assistant-node{display:grid;gap:4px;padding:12px;border:1px solid #d7e4f6;border-radius:8px;background:#f6f9ff}.assistant-node small{color:#6f81a0}.assistant-node strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#203b60}.assistant-node span{display:-webkit-box;overflow:hidden;color:#718099;font-size:12px;line-height:1.55;-webkit-line-clamp:2;-webkit-box-orient:vertical}.assistant-copy-enter-active,.assistant-copy-leave-active{transition:opacity .18s ease,transform .22s cubic-bezier(.2,.8,.2,1)}.assistant-copy-enter-from,.assistant-copy-leave-to{opacity:0;transform:translateY(6px)}
.inference-editor{padding-bottom:96px}.inference-guidance{display:flex;gap:9px;margin:0;padding:12px;border:1px solid #cfe0f8;border-radius:8px;color:#49617e;background:#f5f9ff;line-height:1.55}.inference-guidance>svg{flex:0 0 auto;margin-top:2px;color:var(--primary)}.inference-guidance span{display:grid;gap:2px}.inference-target{display:grid!important;gap:4px;padding:13px!important;border:1px solid #e0e7f1!important;border-radius:8px;background:#fbfcff}.inference-target small,.inference-target span{color:#7b899d;font-size:12px}.inference-options,.inference-depth{display:grid;gap:8px;margin:0;padding:0;border:0}.inference-options legend,.inference-depth legend{margin-bottom:2px;color:#475873;font-size:13px}.inference-options button{display:grid;grid-template-columns:24px minmax(0,1fr);gap:8px;align-items:center;min-height:58px;padding:9px 11px;border:1px solid #dce5f0;border-radius:8px;text-align:left;color:#66809f;background:#fff}.inference-options button span{display:grid;gap:3px;color:#334b6d}.inference-options button small{color:#7b899e;line-height:1.4}.inference-options button.active{color:var(--primary);border-color:#8eb3f4;background:#f3f7ff;box-shadow:0 0 0 2px #2563eb0c}.inference-options button:disabled{opacity:.4}.inference-depth{grid-template-columns:1fr 1fr}.inference-depth legend{grid-column:1/-1}.inference-depth button{display:grid;gap:4px;min-height:62px;padding:9px;border:1px solid #dce5f0;border-radius:8px;color:#526681;background:#fff}.inference-depth button small{font-size:12px;color:var(--text-3)}.inference-depth button.active{color:var(--primary);border-color:#8eb3f4;background:#f3f7ff}.inference-task-status{margin-top:0}.mobile-generation-scales{grid-column:1/-1!important;display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px}.mobile-generation-scales button{display:grid!important;width:auto!important;height:50px!important;border-radius:8px!important;font-size:12px}.mobile-generation-scales button.active{color:var(--primary);border-color:#8eb3f4;background:#edf4ff}.mobile-generation-scales small{font-size:9px;color:var(--text-3)}
@media(prefers-reduced-motion:reduce){.generation-scale button,.assistant-copy-enter-active,.assistant-copy-leave-active{transition:none}.generation-scale button:hover,.assistant-copy-enter-from,.assistant-copy-leave-to{transform:none}}
</style>
