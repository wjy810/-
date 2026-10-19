export type NotificationStatus = 'PENDING' | 'DELIVERED' | 'READ' | 'SEND_FAILED' | 'EXPIRED' | 'ARCHIVED'

export type NotificationType =
  | 'TASK_COMPLETED'
  | 'TASK_FAILED'
  | 'DATA_EXPORT_PROGRESS'
  | 'DATA_DELETION_PROGRESS'
  | 'PRODUCT_UPDATE'
  | 'SECURITY'
  | 'DATA_RIGHTS'
  | 'APPLICATION_ACTIVITY'
  | 'REMINDER'
  | 'MEMBERSHIP'
  | 'WEEKLY_DIGEST'

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

/** 通知偏好 (docs/phase2/03). */
export type NotificationPreferenceType = 'APPLICATION_ACTIVITY' | 'REMINDER' | 'MEMBERSHIP' | 'WEEKLY_DIGEST' | 'PRODUCT_UPDATE'
export type NotificationPreferenceItem = { type: NotificationPreferenceType; inApp: boolean; email: boolean }
export type NotificationPreferences = {
  items: NotificationPreferenceItem[]
  /** False when the deployment has no email sender. */
  emailAvailable: boolean
  /** The verified address, masked; null when the account has none. */
  emailAddress?: string | null
}
