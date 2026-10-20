import type { BadgeTone } from '@/shared/ui/UiBadge.vue'
import type { JobMatch, JobMatchStatus } from '../types'

export type MatchRouteStage = 'jd' | 'resume' | 'evidence' | 'confirm' | 'analyzing' | 'clarifications' | 'report'

const RANK: Record<JobMatchStatus, number> = {
  DRAFT: 0,
  JD_PARSE_FAILED: 0,
  JD_PARSED: 1,
  RESUME_UPLOAD_FAILED: 1,
  RESUME_CONFIRMED: 2,
  EVIDENCE_AUTHORIZED: 3,
  ANALYZING: 4,
  ANALYSIS_PAUSED: 4,
  CANCELLED: 4,
  NEEDS_CLARIFICATION: 5,
  COMPLETED: 6,
}

export function serverStage(match: Pick<JobMatch, 'status' | 'checkpoint'>): MatchRouteStage {
  if (match.status === 'COMPLETED') return 'report'
  if (match.status === 'NEEDS_CLARIFICATION') return 'clarifications'
  if (match.status === 'ANALYZING' || match.status === 'ANALYSIS_PAUSED' || match.status === 'CANCELLED') return 'analyzing'
  if (match.status === 'EVIDENCE_AUTHORIZED') return 'confirm'
  if (match.status === 'RESUME_CONFIRMED') return 'evidence'
  if (match.status === 'JD_PARSED' || match.status === 'RESUME_UPLOAD_FAILED') {
    return match.checkpoint === 'JD_CONFIRMED' ? 'resume' : 'jd'
  }
  return 'jd'
}

export function stepNumber(stage: MatchRouteStage): number {
  return ({ jd: 1, resume: 2, evidence: 3, confirm: 4, analyzing: 4, clarifications: 4, report: 4 })[stage]
}

export function canReach(status: JobMatchStatus, requested: MatchRouteStage): boolean {
  const required = ({ jd: 0, resume: 1, evidence: 2, confirm: 3, analyzing: 4, clarifications: 5, report: 6 })[requested]
  return RANK[status] >= required
}

export function statusLabel(status: JobMatchStatus): string {
  return ({
    DRAFT: '待确认岗位', JD_PARSED: '待选择简历', RESUME_CONFIRMED: '待补充证据',
    EVIDENCE_AUTHORIZED: '待开始分析', ANALYZING: '分析中', NEEDS_CLARIFICATION: '需要确认',
    COMPLETED: '分析完成', JD_PARSE_FAILED: 'JD 解析失败', RESUME_UPLOAD_FAILED: '简历处理失败',
    ANALYSIS_PAUSED: '分析已暂停', CANCELLED: '已取消',
  })[status]
}

/** Badge colour for a match's lifecycle status. */
export function statusTone(status: JobMatchStatus): BadgeTone {
  if (status === 'COMPLETED') return 'success'
  if (status === 'NEEDS_CLARIFICATION') return 'ai'
  if (status === 'ANALYZING') return 'primary'
  if (status.includes('FAILED') || status === 'ANALYSIS_PAUSED') return 'danger'
  if (status === 'CANCELLED') return 'neutral'
  return 'accent'
}

/**
 * Where a match summary should open, based on its lifecycle status. A paused or cancelled re-analysis
 * opens the last finished report, which says what happened to the new run and offers its next step.
 */
export function summaryRoute(item: { id: string; status: string; reportVersions?: number | null }): { name: string; params?: { id: string }; query?: { id: string } } {
  if (item.status === 'COMPLETED') return { name: 'job-match-report', params: { id: item.id } }
  if ((item.status === 'ANALYSIS_PAUSED' || item.status === 'CANCELLED') && (item.reportVersions ?? 0) > 0) {
    return { name: 'job-match-report', params: { id: item.id } }
  }
  if (item.status === 'ANALYZING' || item.status === 'ANALYSIS_PAUSED' || item.status === 'CANCELLED') return { name: 'job-match-analyzing', params: { id: item.id } }
  if (item.status === 'NEEDS_CLARIFICATION') return { name: 'job-match-clarifications', params: { id: item.id } }
  return { name: 'job-match-new', query: { id: item.id } }
}

export type RunAction = 'RETRY' | 'CANCEL' | 'PROGRESS' | 'CLARIFY'
export type RunNotice = { tone: 'info' | 'warning' | 'ai'; text: string; actions: RunAction[] }

/**
 * What the report page says about the match's latest run when it is not the report being shown:
 * a re-analysis still running, waiting for answers, paused or cancelled. Null once it completed.
 */
export function latestRunNotice(status: JobMatchStatus, reason: string, preview: boolean): RunNotice | null {
  const shown = preview ? '下面是规则预估。' : '下面仍是上一版报告。'
  if (status === 'ANALYZING') return { tone: 'info', text: `正在重新分析，完成后会生成新版本；${shown}`, actions: ['PROGRESS'] }
  if (status === 'NEEDS_CLARIFICATION') return { tone: 'ai', text: `重新分析需要你确认几个问题，确认后才会生成新版本；${shown}`, actions: ['CLARIFY'] }
  if (status === 'ANALYSIS_PAUSED') return { tone: 'warning', text: `最近一次分析没有完成：${reason}${shown}可以重试，或取消这次分析。`, actions: ['RETRY', 'CANCEL'] }
  if (status === 'CANCELLED') return { tone: 'info', text: `最近一次分析已取消，${shown}`, actions: [] }
  return null
}
