import type { KeyOutcome, ResumeDraft, ResumeFieldKey, ResumeMasterView } from './types'

export const MASTER_STATUS_GLOSS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_CONFIRMATION: '待确认',
  READY_TO_EXPORT: '可导出',
  ARCHIVED: '已归档',
}

export const VERSION_STATUS_GLOSS: Record<string, string> = {
  GENERATING: '生成中',
  PENDING_USER_CONFIRMATION: '待用户确认',
  FROZEN: '已冻结',
  ARCHIVED: '已归档',
}

export const TEMPLATE_GLOSS: Record<string, string> = {
  SOFTWARE_DEV: '软件开发',
  QA: '测试',
  DATA_ANALYSIS: '数据分析',
  PRODUCT: '产品',
}

export const FIELD_GLOSS: Record<ResumeFieldKey | string, string> = {
  TITLE: '标题',
  EDUCATION: '教育',
  EXPERIENCE: '经历',
  PROJECTS: '项目',
  SKILLS: '技能',
  CERTIFICATES: '证书',
  SELF_INTRO: '自我介绍',
  KEY_OUTCOMES: '关键成果',
}

export const CANDIDATE_STATUS_GLOSS: Record<string, string> = {
  PENDING: '待处理',
  CONFIRMED: '已确认为正式事实',
  REJECTED: '已拒绝 · 非正式事实',
  CORRECTED: '已更正并写入正式事实',
}

export const AI_ACTION_GLOSS: Record<string, string> = {
  REFINE: '精炼',
  POLISH: '润色',
}

export const FIELD_KEYS: ResumeFieldKey[] = [
  'TITLE',
  'EDUCATION',
  'EXPERIENCE',
  'PROJECTS',
  'SKILLS',
  'CERTIFICATES',
  'SELF_INTRO',
  'KEY_OUTCOMES',
]

export function glossMasterStatus(status?: string | null): string {
  if (!status) {
    return '—'
  }
  return MASTER_STATUS_GLOSS[status] ?? status
}

export function glossVersionStatus(status?: string | null): string {
  if (!status) {
    return '—'
  }
  return VERSION_STATUS_GLOSS[status] ?? status
}

export function fieldLabel(key: string): string {
  return FIELD_GLOSS[key] ?? key
}

export function prettyValue(value: unknown): string {
  if (value == null) {
    return '—'
  }
  if (typeof value === 'string') {
    return value.trim() ? value : '—'
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

export function hydrateDraft(master?: ResumeMasterView | null): ResumeDraft {
  return {
    title: master?.title ?? '',
    education: master?.education ?? '',
    experience: master?.experience ?? '',
    projects: master?.projects ?? '',
    skills: master?.skills ?? '',
    certificates: master?.certificates ?? '',
    selfIntro: master?.selfIntro ?? '',
    outcomes: (master?.keyOutcomes ?? []).map((item) => ({
      id: item.id,
      text: item.text ?? '',
      evidenceId: item.evidenceId ?? '',
      waiveNoEvidence: Boolean(item.waiveNoEvidence),
    })),
  }
}

export function outcomeResolved(item: KeyOutcome): boolean {
  return Boolean(item.evidenceId && item.evidenceId.trim()) || Boolean(item.waiveNoEvidence)
}

export function unresolvedOutcomes(items: KeyOutcome[]): KeyOutcome[] {
  return items.filter((item) => (item.text ?? '').trim() && !outcomeResolved(item))
}

export function isBindableVersion(status?: string | null): boolean {
  return status === 'FROZEN'
}

export function isPdfExportable(status?: string | null): boolean {
  return status === 'FROZEN'
}

export function pdfDownloadDisabledReason(task: {
  status?: string | null
  fileId?: string | null
  downloadAvailable?: boolean | null
  downloadUrl?: string | null
  downloadExpired?: boolean | null
} | null): string | null {
  if (!task) {
    return '还没有导出任务。仅已冻结版本可导出；成功后才会出现限时下载。'
  }
  if (task.status !== 'SUCCEEDED') {
    return `导出任务当前为「${task.status}」，不能下载。`
  }
  if (task.downloadExpired) {
    return '下载已过期。请对同一已冻结版本重新导出。本页不会用过期链假装还能下载。'
  }
  if (!task.fileId?.trim()) {
    return '任务已成功，但未返回 fileId。下载按钮已禁用，本页不会伪造下载文件。'
  }
  return null
}

export function docxDownloadDisabledReason(task: {
  status?: string | null
  fileId?: string | null
  downloadExpired?: boolean | null
} | null): string | null {
  if (!task) {
    return '还没有 DOCX 导出任务。仅已冻结版本可导出。'
  }
  if (task.status !== 'SUCCEEDED') {
    return `DOCX 导出任务当前为「${task.status}」，不能下载。`
  }
  if (task.downloadExpired) {
    return 'DOCX 下载已过期，请对同一冻结版本重新导出。'
  }
  if (!task.fileId?.trim()) {
    return '任务已成功，但未返回 fileId。下载按钮已禁用。'
  }
  return null
}

export const COMPARE_KEYS = [
  { key: 'title', label: '标题' },
  { key: 'education', label: '教育' },
  { key: 'experience', label: '经历' },
  { key: 'projects', label: '项目' },
  { key: 'skills', label: '技能' },
  { key: 'certificates', label: '证书' },
  { key: 'selfIntro', label: '自我介绍' },
  { key: 'keyOutcomes', label: '关键成果' },
] as const
