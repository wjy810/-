import type { DeletionImpactItem, DeletionPreview, DeletionStatus, DeletionView, ExportView } from './types'

export const OBJECT_TARGET_TYPE_HINT =
  'CAREER_RECORD（别名 RECORD）、CAREER_FILE（别名 FILE）、CAREER_PROFILE（别名 PROFILE）'

const IMPACT_RELATION_GLOSS: Record<string, string> = {
  TARGET: '将物理删除（目标）',
  CASCADE: '将物理删除（级联）',
  BOUND: '仅展示（绑定，不删）',
  RELATED: '仅展示（关联，不删）',
  BLOCKING: '阻止提交',
}

const DELETION_STATUS_GLOSS: Record<string, string> = {
  SUBMITTED: '已提交',
  PROCESSING: '处理中',
  PARTIALLY_RESTRICTED: '部分受限',
  COMPLETED: '已完成',
  FAILED: '失败',
}

const TASK_STATUS_GLOSS: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '进行中',
  SUCCEEDED: '已成功',
  FAILED: '失败',
  CANCELLED: '已取消',
}

export function glossDeletionStatus(status?: string | null): string {
  if (!status) {
    return '—'
  }
  return DELETION_STATUS_GLOSS[status] ?? status
}

export function glossTaskStatus(status?: string | null): string {
  if (!status) {
    return '—'
  }
  return TASK_STATUS_GLOSS[status] ?? status
}

export function isOpenDeletion(status?: string | null): boolean {
  return status === 'SUBMITTED' || status === 'PROCESSING' || status === 'PARTIALLY_RESTRICTED'
}

export function isPollingDeletion(status?: string | null): boolean {
  return status === 'SUBMITTED' || status === 'PROCESSING'
}

export function deletionLooksFinished(status?: string | null): boolean {
  return status === 'COMPLETED'
}

export function previewIsObjectScoped(preview: DeletionPreview | null): boolean {
  if (!preview) {
    return false
  }
  if (preview.objectScoped === true || preview.scope === 'OBJECT') {
    return true
  }
  const related = [
    preview.relatedApplications,
    preview.relatedResumeVersions,
    preview.relatedInterviews,
    preview.relatedReviews,
  ]
  return related.some((item) => Array.isArray(item) && item.length > 0)
}

export function extraPreviewLines(preview: DeletionPreview | null): Array<{ key: string; value: string }> {
  if (!preview) {
    return []
  }
  const known = new Set([
    'impactSummary',
    'legalExceptionNote',
    'statusHint',
    'scope',
    'targetType',
    'targetId',
    'objectScoped',
    'irreversible',
    'canProceed',
    'impacts',
    'blockers',
    'relatedApplications',
    'relatedResumeVersions',
    'relatedInterviews',
    'relatedReviews',
  ])
  const lines: Array<{ key: string; value: string }> = []
  for (const [key, value] of Object.entries(preview)) {
    if (known.has(key) || value == null || value === '') {
      continue
    }
    lines.push({ key, value: stringifyPreviewValue(value) })
  }
  const relatedKeys: Array<keyof DeletionPreview> = [
    'relatedApplications',
    'relatedResumeVersions',
    'relatedInterviews',
    'relatedReviews',
  ]
  for (const key of relatedKeys) {
    const value = preview[key]
    if (value == null || (Array.isArray(value) && value.length === 0)) {
      continue
    }
    lines.push({ key, value: stringifyPreviewValue(value) })
  }
  return lines
}

function stringifyPreviewValue(value: unknown): string {
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

export function exportDownloadDisabledReason(view: ExportView | null, taskStatus?: string | null): string | null {
  if (!view) {
    return '还没有导出记录。完成导出后即可下载。'
  }
  const status = view.taskStatus ?? taskStatus
  if (status && status !== 'SUCCEEDED') {
    return `导出任务当前为「${glossTaskStatus(status)}」，不能下载。`
  }
  if (view.downloadExpired) {
    return '下载链接已过期，请重新发起导出。'
  }
  if (view.downloadUrl || view.fileId || view.downloadAvailable) {
    return null
  }
  return '导出文件暂不可下载，请刷新进度或重新发起导出。'
}

export function glossImpactRelation(relation?: string | null): string {
  if (!relation) {
    return '—'
  }
  return IMPACT_RELATION_GLOSS[relation] ?? relation
}

export function impactRowTone(relation?: string | null): 'wipe' | 'keep' | 'block' | '' {
  const key = (relation || '').toUpperCase()
  if (key === 'TARGET' || key === 'CASCADE') {
    return 'wipe'
  }
  if (key === 'BOUND' || key === 'RELATED') {
    return 'keep'
  }
  if (key === 'BLOCKING') {
    return 'block'
  }
  return ''
}

export function previewHasBlockingImpact(preview: DeletionPreview | null): boolean {
  return (preview?.impacts ?? []).some((row: DeletionImpactItem) => (row.relation || '').toUpperCase() === 'BLOCKING')
}

export function objectSubmitBlockedReason(preview: DeletionPreview | null, targetType: string, targetId: string): string {
  if (!targetType.trim() || !targetId.trim()) {
    return '请选择数据类型并填写记录编号，然后刷新影响预览。'
  }
  if (!preview) {
    return '请先刷新所选数据的影响预览，再确认删除。'
  }
  if (!previewIsObjectScoped(preview)) {
    return '尚未确认所选数据的删除范围，请刷新影响预览。'
  }
  if (preview.targetId && preview.targetId.trim() !== targetId.trim()) {
    return '表单中的对象 ID 与预览不一致，请先按当前参数刷新预览。'
  }
  const blockers = (preview.blockers ?? []).map((item) => String(item).trim()).filter(Boolean)
  const blocking = previewHasBlockingImpact(preview)
  if (preview.canProceed === false || blocking || blockers.length > 0) {
    return blockers.length
      ? `暂时无法删除：${blockers.join('；')}`
      : '所选数据仍有未解除的关联限制，暂时无法删除。'
  }
  return ''
}

export function deletionProgressNote(view: DeletionView | null): string {
  if (!view) {
    return ''
  }
  const status = view.status as DeletionStatus | string
  if (status === 'PARTIALLY_RESTRICTED') {
    return '部分数据暂不能删除。具体保留范围与原因见处理回执。'
  }
  if (status === 'SUBMITTED') {
    return '申请已提交，等待处理。'
  }
  if (status === 'PROCESSING') {
    return '正在处理删除申请，数据尚未全部删除。'
  }
  if (status === 'FAILED') {
    return '删除失败，请保留申请编号并联系支持。'
  }
  if (status === 'COMPLETED') {
    if (view.scope === 'OBJECT') {
      return '所选数据的删除申请已处理完成，具体范围见处理回执。'
    }
    return '删除申请已处理完成，依法保留的数据见处理回执。'
  }
  return ''
}
