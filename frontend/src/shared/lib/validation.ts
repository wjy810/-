const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function normalizeEmail(raw: string): { value: string; error: string | null } {
  const value = raw.trim().toLowerCase()
  if (!value) {
    return { value, error: '邮箱不能为空' }
  }
  if (value.length > 320 || !EMAIL.test(value)) {
    return { value, error: '邮箱格式不正确' }
  }
  return { value, error: null }
}

export const PASSWORD_RULE_HINT = '至少 8 位，至多 72 位，不能与邮箱相同。'

export function validatePassword(password: string, email?: string): string | null {
  if (!password) {
    return '密码不能为空'
  }
  if (password.length < 8) {
    return '密码至少 8 位'
  }
  if (password.length > 72) {
    return '密码过长'
  }
  if (email && password.toLowerCase() === email.toLowerCase()) {
    return '密码不能与邮箱相同'
  }
  return null
}

export function validateCode(code: string): string | null {
  const trimmed = code.trim()
  if (!trimmed) {
    return '验证码不能为空'
  }
  if (!/^\d{6}$/.test(trimmed)) {
    return '验证码须为 6 位数字'
  }
  return null
}

/** A mainland China mobile number as +86XXXXXXXXXXX, from the ways people type it. */
export function normalizePhone(raw: string): { value: string; error: string | null } {
  let value = raw.trim().replace(/[\s()-]/g, '')
  if (value.startsWith('0086')) value = value.slice(4)
  else if (value.startsWith('+86')) value = value.slice(3)
  else if (value.startsWith('86') && value.length === 13) value = value.slice(2)
  if (!/^1[3-9]\d{9}$/.test(value)) return { value, error: '请输入正确的中国大陆手机号' }
  return { value: `+86${value}`, error: null }
}
