import type { MailSecurity } from './types'

export type MailPreset = { key: string; label: string; host: string; port: number; security: MailSecurity; hint: string }

/** Common providers in China first. Values are the providers' documented SMTP endpoints. */
export const MAIL_PRESETS: readonly MailPreset[] = [
  { key: 'qq', label: 'QQ 邮箱', host: 'smtp.qq.com', port: 465, security: 'SSL', hint: 'QQ 邮箱 › 设置 › 账号与安全 › 安全设置 中开启 SMTP 服务并生成授权码；发件地址必须与账号相同。' },
  { key: 'exmail', label: '腾讯企业邮', host: 'smtp.exmail.qq.com', port: 465, security: 'SSL', hint: '使用企业邮箱地址作为账号，客户端专用密码作为授权码；发件地址必须与账号相同。' },
  { key: '163', label: '网易 163', host: 'smtp.163.com', port: 465, security: 'SSL', hint: '163 邮箱 › 设置 › POP3/SMTP/IMAP 中开启服务并获取授权码。' },
  { key: 'aliyun', label: '阿里企业邮', host: 'smtp.qiye.aliyun.com', port: 465, security: 'SSL', hint: '账号为完整的企业邮箱地址，密码为邮箱密码或三方客户端安全密码。' },
  { key: 'office365', label: 'Outlook / 365', host: 'smtp.office365.com', port: 587, security: 'STARTTLS', hint: '需要在 Microsoft 365 管理中心为该邮箱开启 SMTP 认证。' },
]

export function presetFor(host: string): MailPreset | undefined {
  const value = host.trim().toLowerCase()
  return MAIL_PRESETS.find(preset => preset.host === value)
}

/** Which form field a refusal from the server belongs to; anything else is shown as a page message. */
export const FIELD_OF_REASON: Record<string, 'host' | 'port' | 'security' | 'username' | 'password' | 'fromAddress' | 'fromName'> = {
  MAIL_HOST_INVALID: 'host',
  MAIL_PORT_INVALID: 'port',
  MAIL_SECURITY_INVALID: 'security',
  MAIL_USERNAME_INVALID: 'username',
  MAIL_PASSWORD_REQUIRED: 'password',
  MAIL_PASSWORD_INVALID: 'password',
  MAIL_FROM_INVALID: 'fromAddress',
  MAIL_FROM_MISMATCH: 'fromAddress',
  MAIL_FROM_NAME_INVALID: 'fromName',
}
