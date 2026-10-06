export type ErrorCategory =
  | 'USER_CORRECTABLE'
  | 'FORBIDDEN'
  | 'CONFLICT'
  | 'DEPENDENCY_FAILED'
  | 'SYSTEM_FAILURE'
  | 'REQUIRES_HUMAN'
  | 'UNAUTHENTICATED'

export type ApiError = {
  category: string
  reason: string
  message: string
  /** Server-side correlation id (X-Request-Id); shown to users for support. */
  requestId?: string
  /** Field-level validation messages keyed by request field name. */
  fields?: Record<string, string>
}

export type ApiResponse<T> = {
  ok: boolean
  data?: T | null
  error?: ApiError | null
}

export class ApiClientError extends Error {
  readonly category: string
  readonly reason: string
  readonly status: number
  readonly requestId: string
  readonly fields: Record<string, string>

  constructor(error: ApiError, status: number, requestId?: string) {
    super(error.message)
    this.name = 'ApiClientError'
    this.category = error.category
    this.reason = error.reason
    this.status = status
    this.requestId = error.requestId ?? requestId ?? ''
    this.fields = error.fields ?? {}
  }
}

export function isTimeout(error: unknown): boolean {
  return isApiClientError(error) && error.reason === 'TIMEOUT'
}

export function fieldErrors(error: unknown): Record<string, string> {
  return isApiClientError(error) ? error.fields : {}
}

export function isApiClientError(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError
}

export function isUnauthenticated(error: unknown): boolean {
  return isApiClientError(error) && (error.status === 401 || error.category === 'UNAUTHENTICATED')
}

export function isForbidden(error: unknown): boolean {
  return isApiClientError(error) && (error.status === 403 || error.category === 'FORBIDDEN')
}

export function isConflict(error: unknown): error is ApiClientError {
  return isApiClientError(error) && (error.status === 409 || error.category === 'CONFLICT')
}

export function isVersionConflict(error: unknown): error is ApiClientError {
  return isApiClientError(error) && error.reason === 'VERSION_CONFLICT'
}

export function versionConflictMessage(error: unknown): string {
  const detail = isApiClientError(error) && error.message?.trim() ? error.message.trim() : '内容已在其他页面更新'
  return `${detail}，请刷新后再提交。`
}

export function isValidationFailed(error: unknown): error is ApiClientError {
  return isApiClientError(error) && error.reason === 'VALIDATION_FAILED'
}

export function isFileTooLarge(error: unknown): error is ApiClientError {
  return isApiClientError(error) && error.reason === 'FILE_TOO_LARGE'
}

export function isFileInvalid(error: unknown): error is ApiClientError {
  return isApiClientError(error) && error.reason === 'FILE_INVALID'
}

export function isFileTypeNotAllowed(error: unknown): error is ApiClientError {
  return isApiClientError(error) && error.reason === 'FILE_TYPE_NOT_ALLOWED'
}

export function isUnsupportedMediaType(error: unknown): error is ApiClientError {
  return isApiClientError(error) && (error.status === 415 || error.reason === 'UNSUPPORTED_MEDIA_TYPE')
}

export function isMethodNotAllowed(error: unknown): error is ApiClientError {
  return isApiClientError(error) && (error.status === 405 || error.reason === 'METHOD_NOT_ALLOWED')
}

export function isNotAcceptable(error: unknown): error is ApiClientError {
  return isApiClientError(error) && (error.status === 406 || error.reason === 'NOT_ACCEPTABLE')
}

export function isNotFound(error: unknown): error is ApiClientError {
  return isApiClientError(error) && (error.status === 404 || /NOT_FOUND/.test(error.reason))
}

export function isUnprocessable(error: unknown): error is ApiClientError {
  return isApiClientError(error) && (error.status === 422 || error.category === 'REQUIRES_HUMAN')
}

function isClientHttpError(error: ApiClientError): boolean {
  return error.status >= 400 && error.status < 500
}

function isMisclassifiedSystem4xx(error: ApiClientError): boolean {
  return isClientHttpError(error) && (error.category === 'SYSTEM_FAILURE' || error.reason === 'BAD_RESPONSE')
}

function looksLikeSystemFailureCopy(text: string): boolean {
  return /系统故障|系统繁忙|\b500\b/.test(text)
}

function usableServerMessage(error: ApiClientError): string {
  const text = error.message?.trim() ?? ''
  if (!text || text === '服务响应无法解析' || looksLikeSystemFailureCopy(text)) {
    return ''
  }
  return text
}

function correctableCopy(error: ApiClientError, fallback: string, shortChinese: string): string {
  return usableServerMessage(error) || (fallback && !looksLikeSystemFailureCopy(fallback) ? fallback : shortChinese)
}

/** Plain-language fallbacks by reason; the code itself stays on the error for logs and support. */
const REASON_COPY: Record<string, string> = {
  FILE_TOO_LARGE: '文件太大，请压缩后重试',
  FILE_INVALID: '文件为空或已损坏',
  FILE_TYPE_NOT_ALLOWED: '不支持这种文件类型，请检查扩展名与文件内容是否一致',
  PASSWORD_TOO_WEAK: '密码需要 8 到 72 位，且不能与邮箱相同',
  RESET_CODE_INVALID: '验证码无效或已过期',
  VALIDATION_FAILED: '填写的内容不符合要求，请检查后重试',
  UNSUPPORTED_MEDIA_TYPE: '请求格式不受支持，请刷新页面后重试',
  METHOD_NOT_ALLOWED: '这个操作暂不支持，请刷新页面后重试',
  NOT_ACCEPTABLE: '请求格式不受支持，请刷新页面后重试',
  PAYLOAD_TOO_LARGE: '提交的内容太大，请精简后重试',
  TOO_MANY_REQUESTS: '操作太频繁，请稍后再试',
  NOT_FOUND: '要找的内容不存在或已被删除',
}

const STATUS_REASON: Record<number, string> = {
  405: 'METHOD_NOT_ALLOWED', 406: 'NOT_ACCEPTABLE', 413: 'PAYLOAD_TOO_LARGE', 415: 'UNSUPPORTED_MEDIA_TYPE', 429: 'TOO_MANY_REQUESTS',
}

/**
 * The message to show a user for any error. Server messages are already user-facing Chinese and
 * win; status codes, reason codes and developer hints never appear in the text.
 */
export function errorMessage(error: unknown, fallback = '系统繁忙，请稍后重试'): string {
  if (isUnauthenticated(error) || isForbidden(error)) {
    return isApiClientError(error) && error.message ? error.message : fallback
  }
  if (isVersionConflict(error)) {
    return versionConflictMessage(error)
  }
  if (isApiClientError(error)) {
    const server = usableServerMessage(error)
    if (server) return server
    const reason = REASON_COPY[error.reason] ? error.reason : STATUS_REASON[error.status]
    if (reason) return REASON_COPY[reason]
    if (error.status === 404 || /NOT_FOUND/.test(error.reason)) return correctableCopy(error, fallback, REASON_COPY.NOT_FOUND)
    if (error.status === 400 && isMisclassifiedSystem4xx(error)) return REASON_COPY.VALIDATION_FAILED
    if (isClientHttpError(error)) return correctableCopy(error, fallback, '请求无法处理，请检查后重试')
    return error.message || fallback
  }
  if (error instanceof TypeError) {
    return '网络连接失败，请检查网络后重试。'
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
