import type { JobMatchStatus } from '../types'

/**
 * Stages of one analysis run, in the order the server writes their checkpoints
 * (JobMatchService.analyze and JobMatchAnalysisService.process). Nothing here advances on a
 * timer: the page shows the checkpoint and percentage the server last reported.
 */
export const ANALYSIS_STAGES = [
  { code: 'FREEZE_INPUTS', label: '冻结分析输入' },
  { code: 'RULE_GATE', label: '核对硬性门槛' },
  { code: 'AI_SEMANTIC_ANALYSIS', label: 'AI 语义分析' },
  { code: 'FACT_VALIDATION', label: '核对事实与建议' },
  { code: 'REPORT_READY', label: '生成报告' },
] as const

export type AnalysisStageState = 'done' | 'active' | 'pending'

const STAGE_OF_CHECKPOINT: Record<string, number> = {
  FREEZE_INPUTS: 0,
  // Queued again after clarifications or a resume; the run restarts from the rule gate.
  CLARIFICATION_CONFIRMED: 0,
  ANALYSIS_PAUSED: 0,
  RULE_GATE: 1,
  RULE_REPORT_READY: 2,
  AI_SEMANTIC_ANALYSIS: 2,
  FACT_VALIDATION: 3,
  REPORT_READY: 4,
}

const CHECKPOINT_LABELS: Record<string, string> = {
  FREEZE_INPUTS: '已冻结输入，等待后台开始',
  CLARIFICATION_CONFIRMED: '已收到你的确认，等待后台重新分析',
  ANALYSIS_PAUSED: '已提交恢复，等待后台开始',
  RULE_GATE: '正在核对硬性门槛',
  RULE_REPORT_READY: '规则结果已生成，即将进入 AI 分析',
  AI_SEMANTIC_ANALYSIS: 'AI 正在分析匹配优势与缺口',
  FACT_VALIDATION: '正在核对事实来源与建议',
  REPORT_READY: '正在生成报告',
}

/** Index of the stage the server's checkpoint belongs to, or -1 when it names none. */
export function analysisStageIndex(checkpoint: string | null | undefined): number {
  return STAGE_OF_CHECKPOINT[checkpoint ?? ''] ?? -1
}

export function analysisStageState(index: number, current: number, status: JobMatchStatus): AnalysisStageState {
  if (status === 'COMPLETED') return 'done'
  if (current < 0) return 'pending'
  if (index < current) return 'done'
  if (index === current && status === 'ANALYZING') return 'active'
  return 'pending'
}

/** The server-reported percentage, or null when there is none to show. */
export function serverProgressPercent(progress: unknown): number | null {
  if (typeof progress !== 'number' || !Number.isFinite(progress) || progress <= 0) return null
  return Math.min(100, Math.round(progress))
}

export function analysisStatusText(status: JobMatchStatus, checkpoint: string | null | undefined): string {
  if (status === 'COMPLETED') return '分析已完成'
  if (status === 'ANALYSIS_PAUSED') return '分析已暂停'
  if (status === 'CANCELLED') return '分析已取消'
  if (status === 'NEEDS_CLARIFICATION') return '等待你确认事实'
  if (status !== 'ANALYZING') return '尚未开始分析'
  return CHECKPOINT_LABELS[checkpoint ?? ''] ?? '分析进行中'
}

/** Plain-language reason for a paused run; the raw code stays in the technical details. */
export function pauseReasonText(errorCode: string | null | undefined): string {
  const code = errorCode ?? ''
  if (/SCHEMA|SOURCE_REF|VALIDATION/.test(code)) return 'AI 返回的内容没有通过结构与事实来源校验。'
  if (/QUOTA/.test(code)) return 'AI 分析额度不足。'
  if (/TIMEOUT/.test(code)) return 'AI 服务响应超时。'
  return 'AI 服务这次没有完成分析。'
}
