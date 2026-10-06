export type NotificationStatus = 'PENDING' | 'DELIVERED' | 'READ' | 'SEND_FAILED' | 'EXPIRED' | 'ARCHIVED'

export type NotificationType =
  | 'TASK_COMPLETED'
  | 'TASK_FAILED'
  | 'DATA_EXPORT_PROGRESS'
  | 'DATA_DELETION_PROGRESS'
  | 'PRODUCT_UPDATE'
  | 'SECURITY'
  | 'DATA_RIGHTS'

export type NotificationView = {
  id: string
  type: NotificationType | string
  status: NotificationStatus | string
  eventId?: string | null
  title: string
  body?: string | null
  createdAt?: string | null
  readAt?: string | null
  actionPath?: string | null
}

export type AsyncTaskView = {
  id: string
  taskType: string
  status: string
  inputVersion?: string | null
  resultVersion?: string | null
  failureReason?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  downloadAvailable?: boolean
  downloadUrl?: string | null
}

export type NotificationPage = {
  items: NotificationView[]
  total: number
  page: number
  size: number
}
