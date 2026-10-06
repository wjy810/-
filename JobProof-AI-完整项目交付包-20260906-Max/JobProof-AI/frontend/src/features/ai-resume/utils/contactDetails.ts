export type ContactDetails = Record<string, unknown>

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const phonePattern = /^\+?[0-9\s().-]+$/

export function validContactEmail(value: unknown): boolean {
  const email = String(value ?? '').trim()
  return email.length <= 254 && emailPattern.test(email)
}

export function validContactPhone(value: unknown): boolean {
  const phone = String(value ?? '').trim()
  if (!phone || phone.length > 30 || !phonePattern.test(phone)) return false
  const digits = phone.replace(/\D/g, '')
  return digits.length >= 7 && digits.length <= 15
}

export function contactSubmitIssue(payload: ContactDetails): string {
  const name = String(payload.name ?? '').trim()
  const email = String(payload.email ?? '').trim()
  const phone = String(payload.phone ?? '').trim()

  if (!name) return '请填写姓名。'
  if (!email && !phone) return '邮箱和手机号请至少填写一项。'
  if (email && !validContactEmail(email)) return '请填写有效的邮箱地址。'
  if (phone && !validContactPhone(phone)) return '请填写有效的手机号，可包含国家或地区代码。'
  return ''
}
