import type { NotificationView } from './types'

export const NOTIFICATION_STATUS_GLOSS: Record<string, string> = {
  PENDING: '待发送',
  DELIVERED: '已送达',
  READ: '已读',
  SEND_FAILED: '发送失败',
  EXPIRED: '已过期',
  ARCHIVED: '已归档',
}

export const NOTIFICATION_TYPE_GLOSS: Record<string, string> = {
  TASK_COMPLETED: '任务完成',
  TASK_FAILED: '任务失败',
  DATA_EXPORT_PROGRESS: '导出进度',
  DATA_DELETION_PROGRESS: '删除进度',
  PRODUCT_UPDATE: '产品更新',
  SECURITY: '安全通知',
  DATA_RIGHTS: '数据权利',
}

export function glossNotificationStatus(status?: string | null): string {
  if (!status) {
    return '—'
  }
  return NOTIFICATION_STATUS_GLOSS[status] ?? status
}

export function glossNotificationType(type?: string | null): string {
  if (!type) {
    return '—'
  }
  return NOTIFICATION_TYPE_GLOSS[type] ?? type
}

export function canMarkRead(item: NotificationView): boolean {
  return item.status === 'DELIVERED'
}

export function markReadBlockedReason(item: NotificationView): string {
  if (item.status === 'SEND_FAILED') {
    return '这条通知发送失败，无需标记已读；相关业务结果以对应页面为准。'
  }
  if (item.status === 'PENDING') {
    return '这条通知还在发送中，送达后才能标记已读。'
  }
  if (item.status === 'READ') {
    return '已经是已读。'
  }
  if (item.status === 'EXPIRED') {
    return '这条提醒已过期，无需处理。'
  }
  if (item.status === 'ARCHIVED') {
    return '已归档。'
  }
  return ''
}

const TASK_TYPE_GLOSS: Record<string, string> = {
  ACCOUNT_EXPORT: '导出个人数据',
  ACCOUNT_DELETION: '账号删除',
  RESUME_PDF_EXPORT: '导出简历 PDF',
  RESUME_DOCX_EXPORT: '导出简历 Word',
  CAREER_FILE_PROCESS: '资料文件安全检查与预览',
  RESUME_IMPORT_PARSE: '简历导入',
  JD_PARSE: '岗位描述解析',
  JOB_MATCH: '岗位匹配分析',
  MATCH_AI_ADVICE: '岗位匹配建议',
  JOB_MATCH_EXPORT: '导出匹配报告',
  CAREER_PLANNING_CANVAS: '职业规划生成',
  CAREER_PLANNING_CANVAS_PROPOSAL: '职业规划调整建议',
  CAREER_PLANNING_INTERVIEW: '职业规划访谈',
  CAREER_PLANNING_RECOMMENDATIONS: '职业方向推荐',
  CAREER_PLANNING_VALIDATION_BATCH: '职业规划校验',
}

export function glossTaskType(type?: string | null): string {
  if (!type) {
    return '后台任务'
  }
  return TASK_TYPE_GLOSS[type] ?? '后台任务'
}

export function dataRightsPathForNotice(item: NotificationView): string | null {
  if (item.actionPath?.startsWith('/') && !item.actionPath.startsWith('//')) {
    return item.actionPath
  }
  if (item.type === 'DATA_EXPORT_PROGRESS') {
    const id = item.eventId?.trim()
    return id ? `/account/data-rights?exportId=${encodeURIComponent(id)}` : '/account/data-rights'
  }
  if (item.type === 'DATA_DELETION_PROGRESS') {
    const id = item.eventId?.trim()
    return id ? `/account/data-rights?deletionId=${encodeURIComponent(id)}` : '/account/data-rights'
  }
  if (item.type === 'DATA_RIGHTS') {
    return '/account/data-rights'
  }
  return null
}
