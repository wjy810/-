import type { KeyOutcome, ResumeFieldKey, ResumeVersionView } from './types'

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
  return MASTER_STATUS_GLOSS[status] ?? '未知状态'
}

export function glossVersionStatus(status?: string | null): string {
  if (!status) {
    return '—'
  }
  return VERSION_STATUS_GLOSS[status] ?? '未知状态'
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

const TASK_STATE: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '正在生成',
  FAILED: '没有成功',
  CANCELLED: '已取消',
}

export function pdfDownloadDisabledReason(task: {
  status?: string | null
  fileId?: string | null
  downloadAvailable?: boolean | null
  downloadUrl?: string | null
  downloadExpired?: boolean | null
} | null): string | null {
  if (!task) {
    return '还没有导出。只有已冻结的版本可以导出 PDF。'
  }
  if (task.status !== 'SUCCEEDED') {
    return `PDF ${TASK_STATE[task.status ?? ''] ?? '还没有生成'}，暂时不能下载。`
  }
  if (task.downloadExpired) {
    return '下载已过期，请重新导出这个版本。'
  }
  if (!task.fileId?.trim()) {
    return '导出完成了，但没有拿到文件，请重新导出。'
  }
  return null
}

export function docxDownloadDisabledReason(task: {
  status?: string | null
  fileId?: string | null
  downloadExpired?: boolean | null
} | null): string | null {
  if (!task) {
    return '还没有导出。只有已冻结的版本可以导出 Word。'
  }
  if (task.status !== 'SUCCEEDED') {
    return `Word 文件${TASK_STATE[task.status ?? ''] ?? '还没有生成'}，暂时不能下载。`
  }
  if (task.downloadExpired) {
    return '下载已过期，请重新导出这个版本。'
  }
  if (!task.fileId?.trim()) {
    return '导出完成了，但没有拿到文件，请重新导出。'
  }
  return null
}

const VERSION_SOURCE_GLOSS: Record<string, string> = {
  USER_FREEZE: '手动冻结',
  AI_WORKBENCH_PDF: '工作台导出 PDF',
  AI_WORKBENCH_PDF_ANONYMOUS: '工作台导出匿名 PDF',
}

export function versionSourceLabel(source?: string | null): string {
  return (source && VERSION_SOURCE_GLOSS[source]) || '冻结版本'
}

const MASTER_SOURCE_GLOSS: Record<string, string> = {
  BLANK: '新建',
  TEMPLATE: '模板创建',
  IMPORT: '导入',
  COPY: '复制',
}

export function masterSourceLabel(source?: string | null): string {
  return (source && MASTER_SOURCE_GLOSS[source]) || '—'
}

/**
 * Versions numbered 1, 2, 3… in the order they were created. `version` on a version is its
 * optimistic-lock counter (0 or 1 for almost every snapshot), so it cannot name a version.
 */
export function versionOrdinals(versions: readonly Pick<ResumeVersionView, 'id' | 'createdAt' | 'frozenAt'>[]): Map<string, number> {
  const sorted = [...versions].sort((left, right) => {
    const byTime = (left.createdAt || left.frozenAt || '').localeCompare(right.createdAt || right.frozenAt || '')
    return byTime || left.id.localeCompare(right.id)
  })
  return new Map(sorted.map((item, index) => [item.id, index + 1]))
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
