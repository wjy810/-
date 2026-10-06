const FALLBACK = '/dashboard'
const AUTH_ONLY = new Set(['/', '/login', '/register', '/reset'])
const ORIGIN = 'https://jobproof.invalid'

function decodeOnce(raw: string): string | null {
  try {
    return decodeURIComponent(raw)
  } catch {
    return null
  }
}

/** Keep post-login `next` on-origin. Reject protocol-relative, `..` 跳出和登录循环. */
export function safeNextPath(raw: unknown): string {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > 512) {
    return FALLBACK
  }
  let value = decodeOnce(raw)
  if (value == null) {
    return FALLBACK
  }
  const again = decodeOnce(value)
  if (again != null && again !== value) {
    value = again
  }
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\') || /[\u0000-\u001F\s]/.test(value)) {
    return FALLBACK
  }
  if (value.includes('://')) {
    return FALLBACK
  }
  let url: URL
  try {
    url = new URL(value, ORIGIN)
  } catch {
    return FALLBACK
  }
  if (url.origin !== ORIGIN || url.username || url.password) {
    return FALLBACK
  }
  const path = url.pathname
  if (!path.startsWith('/') || path.startsWith('//') || AUTH_ONLY.has(path)) {
    return FALLBACK
  }
  return `${path}${url.search}${url.hash}`
}
