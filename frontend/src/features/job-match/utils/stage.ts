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

export function statusTone(status: JobMatchStatus): 'green' | 'orange' | 'red' | 'blue' | 'gray' | 'purple' {
  if (status === 'COMPLETED') return 'green'
  if (status === 'NEEDS_CLARIFICATION') return 'purple'
  if (status === 'ANALYZING') return 'blue'
  if (status.includes('FAILED') || status === 'ANALYSIS_PAUSED') return 'red'
  if (status === 'CANCELLED') return 'gray'
  return 'orange'
}

/** Where a match summary should open, based on its lifecycle status. */
export function summaryRoute(item: { id: string; status: string }): { name: string; params?: { id: string }; query?: { id: string } } {
  if (item.status === 'COMPLETED') return { name: 'job-match-report', params: { id: item.id } }
  if (item.status === 'ANALYZING' || item.status === 'ANALYSIS_PAUSED' || item.status === 'CANCELLED') return { name: 'job-match-analyzing', params: { id: item.id } }
  if (item.status === 'NEEDS_CLARIFICATION') return { name: 'job-match-clarifications', params: { id: item.id } }
  return { name: 'job-match-new', query: { id: item.id } }
}
