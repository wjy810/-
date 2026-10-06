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

  constructor(error: ApiError, status: number) {
    super(error.message)
    this.name = 'ApiClientError'
    this.category = error.category
    this.reason = error.reason
    this.status = status
  }
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
  const detail =
    isApiClientError(error) && error.message
      ? error.message
      : '对象版本冲突，已拒绝覆盖。'
  return `${detail}（409 VERSION_CONFLICT，请刷新后再提交。这不是系统故障。）`
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

function notSystemFailureStamp(detail: string, status: number, reason: string): string {
  return `${detail}（${status} ${reason}。这不是系统故障。）`
}

function correctableCopy(error: ApiClientError, fallback: string, shortChinese: string): string {
  return usableServerMessage(error) || (fallback && !looksLikeSystemFailureCopy(fallback) ? fallback : shortChinese)
}

export function errorMessage(error: unknown, fallback = '系统繁忙，请稍后重试'): string {
  if (isUnauthenticated(error) || isForbidden(error)) {
    return isApiClientError(error) && error.message ? error.message : fallback
  }
  if (isVersionConflict(error)) {
    return versionConflictMessage(error)
  }
  if (isApiClientError(error)) {
    if (error.reason === 'FILE_TOO_LARGE') {
      return notSystemFailureStamp(usableServerMessage(error) || '附件不超过 1MB', error.status || 400, 'FILE_TOO_LARGE')
    }
    if (error.reason === 'FILE_INVALID') {
      return notSystemFailureStamp(usableServerMessage(error) || '文件不能为空', error.status || 400, 'FILE_INVALID')
    }
    if (error.reason === 'FILE_TYPE_NOT_ALLOWED') {
      return notSystemFailureStamp(
        usableServerMessage(error) || '仅允许 PDF、PNG、JPEG、WebP 核验附件，且扩展名须与内容一致',
        error.status || 400,
        'FILE_TYPE_NOT_ALLOWED',
      )
    }
    if (error.reason === 'PASSWORD_TOO_WEAK') {
      return notSystemFailureStamp(
        usableServerMessage(error) || '密码至少 8 位，至多 72 位，不能与邮箱相同',
        error.status || 400,
        'PASSWORD_TOO_WEAK',
      )
    }
    if (error.reason === 'RESET_CODE_INVALID') {
      return notSystemFailureStamp('验证码无效或已过期', error.status || 400, 'RESET_CODE_INVALID')
    }
    if (error.reason === 'VALIDATION_FAILED' || (error.status === 400 && isMisclassifiedSystem4xx(error))) {
      return notSystemFailureStamp(usableServerMessage(error) || '请求参数不正确', 400, 'VALIDATION_FAILED')
    }
    if (error.status === 415 || error.reason === 'UNSUPPORTED_MEDIA_TYPE') {
      return notSystemFailureStamp(
        usableServerMessage(error) || '请求内容类型不受支持，请使用 application/json',
        415,
        'UNSUPPORTED_MEDIA_TYPE',
      )
    }
    if (error.status === 405 || error.reason === 'METHOD_NOT_ALLOWED') {
      return notSystemFailureStamp(
        usableServerMessage(error) || '请求方法不受支持',
        405,
        'METHOD_NOT_ALLOWED',
      )
    }
    if (error.status === 406 || error.reason === 'NOT_ACCEPTABLE') {
      return notSystemFailureStamp(
        usableServerMessage(error) || '响应格式不受支持',
        406,
        'NOT_ACCEPTABLE',
      )
    }
    if (error.status === 413 || error.reason === 'PAYLOAD_TOO_LARGE') {
      return notSystemFailureStamp(usableServerMessage(error) || '请求体过大', 413, 'PAYLOAD_TOO_LARGE')
    }
    if (error.status === 429 || error.reason === 'TOO_MANY_REQUESTS') {
      return notSystemFailureStamp(
        usableServerMessage(error) || '请求过于频繁，请稍后再试',
        429,
        'TOO_MANY_REQUESTS',
      )
    }
    if (error.reason === 'NOT_FOUND' || (error.status === 404 && isMisclassifiedSystem4xx(error))) {
      return notSystemFailureStamp(usableServerMessage(error) || '接口不存在', 404, 'NOT_FOUND')
    }
    if (error.status === 404 || /NOT_FOUND/.test(error.reason)) {
      return correctableCopy(error, fallback, '对象不存在')
    }
    if (isClientHttpError(error)) {
      return correctableCopy(error, fallback, '请求无法处理')
    }
    return error.message || fallback
  }
  if (error instanceof TypeError) {
    return '无法连接后端。请确认已在 backend/ 执行 mvn spring-boot:run，且本页走 Vite 代理。'
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
