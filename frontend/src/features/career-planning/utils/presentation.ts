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

export function careerPlanStatusLabel(value: string): string {
  return ({ ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', ARCHIVED: '已归档' } as Record<string, string>)[value] ?? ''
}

export function careerProfileSectionLabel(value: string): string {
  return ({
    BASICS: '基础信息',
    SKILLS: '技能',
    EDUCATION: '教育经历',
    EXPERIENCE: '经历',
    PROJECT: '项目',
    PROJECTS: '项目',
    ORGANIZATION: '组织经历',
    CERTIFICATES: '证书',
    PREFERENCES: '个人倾向',
    CONSTRAINTS: '限制条件',
    CLARIFICATION: '访谈补充',
  } as Record<string, string>)[value] ?? '其他信息'
}

export function careerEvidenceStrengthLabel(value: string | null | undefined): string {
  return ({ WEAK: '证据较少', MEDIUM: '证据一般', STRONG: '证据充分', VERIFIED: '已核验' } as Record<string, string>)[value ?? ''] ?? ''
}

export function careerEvidenceStatusLabel(value: string | null | undefined): string {
  return ({ PENDING: '待验证', CONFIRMED: '已确认', REJECTED: '未通过' } as Record<string, string>)[value ?? ''] ?? '待核对'
}

/** Progress of a batch ability validation; unknown codes have no label rather than a raw code. */
export function careerValidationCheckpointLabel(value: string | null | undefined): string {
  return ({
    QUEUED: '正在排队',
    STARTING: '正在启动评估',
    RESUMING: '正在恢复评估',
    RETRY_QUEUED: '正在等待重试',
    VALIDATING_BATCH_INPUT: '正在核对提交材料',
    VALIDATING_RESPONSE: '正在校验评估结果',
    REPAIRING_RESPONSE: '正在整理评估结果格式',
    VALIDATING_REPAIRED_RESPONSE: '正在复核评估结果',
    FINALIZING_RESPONSE: '正在汇总评估结果',
    PERSISTING_VALIDATIONS: '正在保存评估结果',
  } as Record<string, string>)[value ?? ''] ?? ''
}

const sourceTypeLabels: Record<string, string> = {
  PROFILE_ITEM: '画像条目',
  PROFILE: '职业画像',
  CAREER_RECORD: '资料库记录',
  GOAL: '目标职业',
  INTERVIEW: 'AI 访谈回答',
}

/**
 * Readable labels for source references such as `PROFILE_ITEM:<id>`. Ids are never shown: a
 * reference with a known title shows the title, the rest are counted per type.
 */
export function careerSourceRefLabels(refs: readonly string[], titles: ReadonlyMap<string, string> = new Map()): string[] {
  const named: string[] = []
  const counts = new Map<string, number>()
  for (const ref of refs) {
    const title = titles.get(ref)?.trim()
    const type = ref.includes(':') ? ref.slice(0, ref.indexOf(':')).toUpperCase() : ''
    const label = sourceTypeLabels[type] ?? '其他来源'
    if (title) {
      const value = `${label} · ${title}`
      if (!named.includes(value)) named.push(value)
    } else counts.set(label, (counts.get(label) ?? 0) + 1)
  }
  return [...named, ...[...counts].map(([label, count]) => count > 1 ? `${label} ${count} 项` : label)]
}
