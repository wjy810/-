import type { DeletionImpactItem, DeletionPreview, DeletionStatus, DeletionView, ExportView } from './types'

const IMPACT_RELATION_GLOSS: Record<string, string> = {
  TARGET: '永久删除',
  CASCADE: '一并删除',
  CHILD: '一并删除',
  GENERATED: '一并删除',
  REFERENCE: '一并删除',
  BOUND: '保留',
  RELATED: '保留',
  BLOCKING: '阻止删除',
}

const BLOCKER_GLOSS: Record<string, string> = {
  CAREER_RECORD_REFERENCED: '这条经历正被简历版本引用，请先在简历中移除引用后再删除',
  RESUME_TASK_IN_PROGRESS: '这份简历还有任务在进行中，请等任务结束或取消后再删除',
}

const TARGET_TYPE_GLOSS: Record<string, string> = {
  CAREER_RECORD: '经历与成果记录',
  CAREER_FILE: '资料文件',
  CAREER_PROFILE: '职业主档',
  RESUME_MASTER: '简历',
  RESUME_VERSION: '简历版本',
}

const RECEIPT_MODULE_GLOSS: Record<string, string> = {
  identity: '账号',
  share: '分享链接',
  storage: '文件存储',
  task: '后台任务',
  audit: '安全审计记录',
  'career-library': '求职资料库',
  job: '岗位',
  matching: '岗位匹配',
  'resume-import': '简历导入',
  resume: '简历',
  'career-planning': '职业规划',
  object: '所选数据',
}

const RECEIPT_STATUS_GLOSS: Record<string, string> = {
  SUCCEEDED: '已处理',
  RESTRICTED: '依法保留',
  FAILED: '处理失败',
  SKIPPED: '暂未处理',
}

const HISTORY_TYPE_GLOSS: Record<string, string> = {
  APPLICATION: '投递记录',
  APPLICATION_EVENT: '投递动态',
  APPLICATION_STAGE: '投递阶段',
  INTERVIEW: '面试记录',
  INTERVIEW_REMINDER: '面试提醒',
  NOTIFICATION: '提醒',
  REVIEW: '复盘记录',
  REVIEW_SUGGESTION: '复盘建议',
  AI_INTERVIEW: 'AI 模拟面试',
  AI_INTERVIEW_TURN: 'AI 模拟面试问答',
}

export function glossBlocker(code: string): string {
  return BLOCKER_GLOSS[code] ?? '还有未解除的关联，暂时不能删除'
}

export function glossTargetType(type?: string | null): string {
  return type ? TARGET_TYPE_GLOSS[type.toUpperCase()] ?? '所选数据' : '所选数据'
}

export function glossReceiptModule(code?: string | null): string {
  return code ? RECEIPT_MODULE_GLOSS[code] ?? '其他数据' : '其他数据'
}

export function glossReceiptStatus(status?: string | null): string {
  return status ? RECEIPT_STATUS_GLOSS[status] ?? '处理中' : '处理中'
}

export function glossHistoryType(type: string): string {
  return HISTORY_TYPE_GLOSS[type] ?? '其他记录'
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
  return relation ? IMPACT_RELATION_GLOSS[relation.toUpperCase()] ?? '相关数据' : '相关数据'
}

export function impactRowTone(relation?: string | null): 'wipe' | 'keep' | 'block' | '' {
  const key = (relation || '').toUpperCase()
  if (['TARGET', 'CASCADE', 'CHILD', 'GENERATED', 'REFERENCE'].includes(key)) {
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
    return '填写的编号和预览中的不一致，请重新预览删除影响。'
  }
  const blockers = (preview.blockers ?? []).map((item) => String(item).trim()).filter(Boolean)
  const blocking = previewHasBlockingImpact(preview)
  if (preview.canProceed === false || blocking || blockers.length > 0) {
    return blockers.length
      ? `暂时无法删除：${blockers.map(glossBlocker).join('；')}。`
      : '所选数据仍有未解除的关联，暂时无法删除。'
  }
  return ''
}

export function deletionProgressNote(view: DeletionView | null): string {
  if (!view) {
    return ''
  }
  const status = view.status as DeletionStatus | string
  if (status === 'PARTIALLY_RESTRICTED') {
    return '部分数据暂不能删除。具体保留范围与原因见下方处理结果。'
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
      return '所选数据的删除申请已处理完成，具体范围见下方处理结果。'
    }
    return '删除申请已处理完成，依法保留的数据见下方处理结果。'
  }
  return ''
}
