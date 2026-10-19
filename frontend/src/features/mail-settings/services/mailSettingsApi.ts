import { api } from '@/shared/api/client'
import type { MailSettings, MailSettingsWrite, MailTestResult } from '../types'

const ROOT = '/api/v1/admin/mail-settings'

export const fetchMailSettings = () => api<MailSettings>(ROOT)
export const saveMailSettings = (write: MailSettingsWrite) => api<MailSettings>(ROOT, { method: 'PUT', body: JSON.stringify(write) })
export const clearMailSettings = () => api<MailSettings>(ROOT, { method: 'DELETE' })
export const sendTestMail = (to: string) => api<MailTestResult>(`${ROOT}/test`, { method: 'POST', body: JSON.stringify({ to }) })
