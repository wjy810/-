import type { JobMatchStatus } from '../types'

export const ANALYSIS_CHECKPOINTS = [
  ['PARSE_JD', '解析岗位要求'],
  ['VALIDATE_RESUME', '校验简历结构'],
  ['LINK_EVIDENCE', '关联资料库证据'],
  ['RULE_GATE', '校验硬性门槛'],
  ['AI_SEMANTIC_ANALYSIS', 'AI 语义分析'],
  ['FACT_VALIDATION', '事实与质量校验'],
  ['REPORT_READY', '生成行动报告'],
] as const

export type AnalysisCheckpointState = 'done' | 'active' | 'paused' | 'pending'

const CHECKPOINT_DISPLAY_CEILINGS = [24, 39, 51, 67, 84, 94, 98] as const

type DisplayedProgressInput = {
  current: number
  authoritative: number
  checkpoint: string | null | undefined
  status: JobMatchStatus
  deltaMs: number
}

function finiteProgress(value: number, maximum = 100): number {
  if (!Number.isFinite(value)) return 0
  return Math.min(maximum, Math.max(0, value))
}

export function analysisCheckpointIndex(
  checkpoint: string | null | undefined,
  status: JobMatchStatus,
  progress: number,
): number {
  const exact = ANALYSIS_CHECKPOINTS.findIndex((item) => item[0] === checkpoint)
  if (exact >= 0) return exact
  if (status === 'ANALYSIS_PAUSED' || status === 'ANALYZING') {
    if (progress >= 90) return 6
    if (progress >= 78) return 5
    if (progress >= 68) return 4
    if (progress >= 52) return 3
    if (progress >= 40) return 2
    if (progress >= 25) return 1
  }
  return 0
}

export function analysisCheckpointState(
  index: number,
  currentIndex: number,
  status: JobMatchStatus,
): AnalysisCheckpointState {
  if (status === 'COMPLETED' || index < currentIndex) return 'done'
  if (index !== currentIndex) return 'pending'
  if (status === 'ANALYZING') return 'active'
  if (status === 'ANALYSIS_PAUSED') return 'paused'
  return 'pending'
}

/**
 * Smooths discrete server updates without claiming that an unfinished task is complete.
 * The server remains authoritative; checkpoint ceilings only animate the waiting time
 * between durable updates.
 */
export function nextDisplayedProgress({
  current,
  authoritative,
  checkpoint,
  status,
  deltaMs,
}: DisplayedProgressInput): number {
  if (status === 'COMPLETED') return 100

  const displayed = finiteProgress(current, 98)
  const durable = finiteProgress(authoritative, 98)
  if (status !== 'ANALYZING') return Math.max(displayed, durable)

  const checkpointIndex = analysisCheckpointIndex(checkpoint, status, durable)
  const checkpointCeiling = CHECKPOINT_DISPLAY_CEILINGS[checkpointIndex] ?? 98
  const target = Math.min(98, Math.max(durable, checkpointCeiling))
  if (displayed >= target) return displayed

  const tickScale = Math.min(4, Math.max(0, deltaMs) / 400)
  if (tickScale === 0) return displayed

  const isCatchingUp = displayed < durable
  const distance = (isCatchingUp ? durable : target) - displayed
  const baseStep = isCatchingUp
    ? Math.max(0.2, distance * 0.12)
    : Math.max(0.025, distance * 0.018)
  const next = displayed + baseStep * tickScale
  return Math.min(isCatchingUp ? durable : target, next)
}

export function analysisEtaLabel(
  checkpoint: string | null | undefined,
  status: JobMatchStatus,
  idleMs: number,
): string {
  if (status === 'COMPLETED') return '分析已完成'
  if (status === 'ANALYSIS_PAUSED') return '进度已保存，可随时恢复'
  if (status === 'CANCELLED') return '分析已取消'
  if (status !== 'ANALYZING') return '正在准备分析'

  const checkpointIndex = checkpoint === 'ANALYSIS_PAUSED'
    ? 4
    : Math.max(0, ANALYSIS_CHECKPOINTS.findIndex(item => item[0] === checkpoint))
  if (idleMs >= 60_000) {
    return checkpointIndex >= 4
      ? '正在等待 AI 返回，通常还需 1–2 分钟'
      : '任务仍在后台运行，可能还需约 1 分钟'
  }

  const estimatedSeconds = [90, 75, 60, 45, 40, 22, 10][checkpointIndex] ?? 60
  if (estimatedSeconds <= 15) return '预计还需约 10–15 秒'
  if (estimatedSeconds <= 30) return '预计还需约 20–30 秒'
  if (estimatedSeconds <= 50) return '预计还需约 30–45 秒'
  if (estimatedSeconds <= 75) return '预计还需约 1 分钟'
  return '预计还需 1–2 分钟'
}
