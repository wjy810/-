import { api } from '@/shared/api/client'
import { withQuery } from '@/shared/lib/query'
import type { AsyncTaskView, NotificationPage } from '../types'

export function fetchUnreadCount(): Promise<number> {
  return api<{ count: number }>('/api/v1/notifications/unread-count').then((data) => data.count)
}

export function listNotifications(page = 0, size = 20, status?: string): Promise<NotificationPage> {
  return api<NotificationPage>(withQuery('/api/v1/notifications', { page, size, status }))
}

export function fetchTaskDetail(taskId: string): Promise<AsyncTaskView> {
  return api<AsyncTaskView>(`/api/v1/tasks/${encodeURIComponent(taskId)}`)
}

export function markNotificationRead(id: string): Promise<void> {
  return api<void>(`/api/v1/notifications/${id}/read`, {
    method: 'POST',
  })
}

export function markNotificationsReadBatch(ids: string[]): Promise<void> {
  return api<void>('/api/v1/notifications/read-batch', {
    method: 'POST',
    body: JSON.stringify({ ids }),
  })
}
