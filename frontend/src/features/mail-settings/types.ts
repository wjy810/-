/** 后台邮件设置 (docs/phase2/05). Shapes mirror MailSettingsService. */
export type MailSecurity = 'SSL' | 'STARTTLS' | 'NONE'
export type MailSource = 'ADMIN' | 'ENVIRONMENT' | 'NONE'

export type MailAdminSettings = {
  host: string
  port: number
  security: MailSecurity
  username: string
  fromAddress: string
  fromName: string
  notifications: boolean
  version: number
  updatedAt: string
  /** False when the saved authorization code cannot be decrypted any more. */
  usable: boolean
}

export type MailEnvironment = { configured: boolean; host?: string | null; username?: string | null; fromAddress?: string | null; notifications: boolean }
export type MailTestResult = { at: string; ok: boolean; message: string }
export type RecentMailEmail = {
  id: string
  type: string
  to: string
  subject: string
  status: 'PENDING' | 'SENT' | 'FAILED' | string
  attempts: number
  lastError?: string | null
  createdAt: string
  sentAt?: string | null
}

export type MailSettings = {
  source: MailSource
  notifications: boolean
  canStoreSecrets: boolean
  admin?: MailAdminSettings | null
  environment: MailEnvironment
  lastTest?: MailTestResult | null
  delivery: { sent24h: number; pending: number; failed7d: number; recent: RecentMailEmail[] }
}

export type MailSettingsWrite = {
  host: string
  port: number
  security: MailSecurity
  username: string
  password: string
  fromAddress: string
  fromName: string
  notifications: boolean
  expectedVersion: number | null
}
