import type { CareerCanvas } from '../types'

export type CareerWorkbenchView = 'CANVAS' | 'PLAN' | 'VALIDATION'

export function isCareerExecutionPhase(value: string): boolean {
  return value === 'CANVAS' || value === 'PLAN' || value === 'VALIDATION'
}

export function initialCareerWorkbenchView(value: string): CareerWorkbenchView {
  if (value === 'PLAN' || value === 'VALIDATION') return value
  return 'CANVAS'
}

export function hasCanvasContent(canvas: CareerCanvas): boolean {
  return canvas.nodes.some(node => node.type !== 'CAREER')
}

export function hasActionablePlanNodes(canvas: CareerCanvas): boolean {
  return canvas.nodes.some(node =>
    ['SKILL', 'KNOWLEDGE', 'TASK'].includes(node.type)
      && !['MASTERED', 'PAUSED'].includes(node.status),
  )
}

const versionReasonLabels: Record<string, string> = {
  GOAL_CONFIRMED: '目标确认',
  INITIAL_GOAL: '目标确认',
  AI_CANVAS_GENERATED: 'AI 生成能力树',
  USER_NODE_ADDED: '新增节点',
  USER_NODE_CREATED: '新增节点',
  USER_NODE_UPDATED: '编辑节点',
  USER_NODE_DELETED: '删除节点',
  USER_RELATION_UPDATED: '调整依赖关系',
  AI_PROPOSAL_APPLIED: '应用 AI 差异',
  USER_VERSION_RESTORED: '恢复历史版本',
  GOAL_CHANGED: '更换目标',
}

export function careerVersionReasonLabel(value: string): string {
  return versionReasonLabels[value] ?? '版本更新'
}

export function careerVersionAuthorLabel(value: string): string {
  return ({ USER: '用户操作', AI: 'AI 助手', SYSTEM: '系统' } as Record<string, string>)[value] ?? '系统'
}

export function careerCanvasNodeStatusLabel(value: string): string {
  return ({
    NOT_STARTED: '未开始',
    PLANNED: '计划中',
    LEARNING: '学习中',
    PENDING_VALIDATION: '待验证',
    MASTERED: '已掌握',
    PAUSED: '已暂停',
  } as Record<string, string>)[value] ?? '状态未知'
}

export function careerCanvasNodeTypeLabel(value: string): string {
  return ({
    CAREER: '目标职业',
    DOMAIN: '能力域',
    SKILL: '技能',
    KNOWLEDGE: '知识点',
    TASK: '行动任务',
    EVIDENCE: '成果证据',
  } as Record<string, string>)[value] ?? '能力节点'
}
