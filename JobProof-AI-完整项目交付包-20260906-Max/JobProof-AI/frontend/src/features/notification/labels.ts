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
    return '发送失败，不能标成已读来假装已送达。通知失败不回滚业务。'
  }
  if (item.status === 'PENDING') {
    return '仍为待发送，不能假装已送达。'
  }
  if (item.status === 'READ') {
    return '已经是已读。'
  }
  if (item.status === 'EXPIRED') {
    return '已过期提醒不再标已读。'
  }
  if (item.status === 'ARCHIVED') {
    return '已归档。'
  }
  return ''
}

export function isProtectedNotice(item: NotificationView): boolean {
  return item.type === 'SECURITY' || item.type === 'DATA_RIGHTS'
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
