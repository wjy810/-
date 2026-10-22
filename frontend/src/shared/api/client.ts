/**
 * HTTP client for the JobProof API (docs/03 §4.5).
 *
 * - Unwraps the `{ ok, data, error }` envelope and throws ApiClientError.
 * - Adds `X-Request-Id` to every request and surfaces it on errors.
 * - Applies a timeout (default 20s) and honours caller AbortSignals.
 * - Routes 401 responses to the registered session-expiry handler.
 * - Routes plan-limit refusals (docs/phase2/02) to the registered upgrade handler.
 */
import { ApiClientError, isAiRequestInFlight, type ApiError, type ApiResponse } from './types'

type UnauthenticatedHandler = () => void

let onUnauthenticated: UnauthenticatedHandler | null = null

type PreflightResult = { headers?: Record<string, string>; complete?: (receivedResponse: boolean) => void }
type RequestPreflight = (path: string, init: ApiInit) => Promise<PreflightResult | undefined>
let requestPreflight: RequestPreflight | null = null
export function setRequestPreflight(hook: RequestPreflight | null): void { requestPreflight = hook }

export function setUnauthenticatedHandler(handler: UnauthenticatedHandler | null): void {
  onUnauthenticated = handler
}

/** Refusals that mean "the current plan allows no more", which the app can answer with an upgrade path. */
export const PLAN_LIMIT_REASONS: ReadonlySet<string> = new Set(['AI_QUOTA_INSUFFICIENT', 'RESUME_LINK_LIMIT'])

type PlanLimitHandler = (error: ApiError) => void

let onPlanLimit: PlanLimitHandler | null = null

export function setPlanLimitHandler(handler: PlanLimitHandler | null): void {
  onPlanLimit = handler
}

function notifyPlanLimit(error: ApiError): void {
  if (PLAN_LIMIT_REASONS.has(error.reason)) onPlanLimit?.(error)
}

/** Reads should be quick; a stuck GET surfaces as a retryable timeout. */
export const DEFAULT_TIMEOUT_MS = 20_000
export const LONG_TIMEOUT_MS = 60_000
/** Writes may wait on AI generation, file scanning or rendering on the server. */
export const WRITE_TIMEOUT_MS = 120_000

export type ApiInit = RequestInit & {
  /** Abort the request after this many milliseconds; 0 disables the timeout. */
  timeoutMs?: number
}

const DEFAULT_ERROR: ApiError = {
  category: 'SYSTEM_FAILURE',
  reason: 'BAD_RESPONSE',
  message: '服务响应无法解析',
}

const TIMEOUT_ERROR: ApiError = {
  category: 'DEPENDENCY_FAILED',
  reason: 'TIMEOUT',
  message: '请求超时，请检查网络后重试',
}

function unparsedHttpError(status: number): ApiError {
  if (status === 400) {
    return { category: 'USER_CORRECTABLE', reason: 'VALIDATION_FAILED', message: '请求参数不正确' }
  }
  if (status === 401) {
    return { category: 'UNAUTHENTICATED', reason: 'UNAUTHENTICATED', message: '尚未登录，或会话已失效' }
  }
  if (status === 403) {
    return { category: 'FORBIDDEN', reason: 'FORBIDDEN', message: '没有权限执行此操作。' }
  }
  if (status === 404) {
    return { category: 'USER_CORRECTABLE', reason: 'NOT_FOUND', message: '接口不存在' }
  }
  if (status === 405) {
    return { category: 'USER_CORRECTABLE', reason: 'METHOD_NOT_ALLOWED', message: '请求方法不受支持' }
  }
  if (status === 406) {
    return { category: 'USER_CORRECTABLE', reason: 'NOT_ACCEPTABLE', message: '响应格式不受支持' }
  }
  if (status === 409) {
    return { category: 'CONFLICT', reason: 'CONFLICT', message: '请求冲突，已拒绝覆盖' }
  }
  if (status === 413) {
    return { category: 'USER_CORRECTABLE', reason: 'PAYLOAD_TOO_LARGE', message: '请求体过大' }
  }
  if (status === 415) {
    return {
      category: 'USER_CORRECTABLE',
      reason: 'UNSUPPORTED_MEDIA_TYPE',
      message: '请求内容类型不受支持，请使用 application/json',
    }
  }
  if (status === 422) {
    return { category: 'REQUIRES_HUMAN', reason: 'REQUIRES_HUMAN', message: '需要人工确认后再继续' }
  }
  if (status === 429) {
    return { category: 'USER_CORRECTABLE', reason: 'TOO_MANY_REQUESTS', message: '请求过于频繁，请稍后再试' }
  }
  if (status === 502 || status === 503 || status === 504) {
    return { category: 'DEPENDENCY_FAILED', reason: 'SERVICE_UNAVAILABLE', message: '服务暂时不可用，请稍后重试' }
  }
  if (status >= 400 && status < 500) {
    return { category: 'USER_CORRECTABLE', reason: 'CLIENT_ERROR', message: '请求无法处理' }
  }
  return DEFAULT_ERROR
}

function errorFromPayload(error: ApiError | null | undefined, status: number): ApiError {
  if (error?.reason) {
    return error
  }
  return unparsedHttpError(status)
}

export function newRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

type PreparedSignal = { signal?: AbortSignal; timedOut: () => boolean; dispose: () => void }

/** Combines the caller's signal with a timeout without relying on AbortSignal.any. */
function prepareSignal(external: AbortSignal | null | undefined, timeoutMs: number): PreparedSignal {
  if (!timeoutMs && !external) return { signal: undefined, timedOut: () => false, dispose: () => undefined }
  const controller = new AbortController()
  let timedOut = false
  const abortFromExternal = () => controller.abort(external?.reason)
  if (external) {
    if (external.aborted) controller.abort(external.reason)
    else external.addEventListener('abort', abortFromExternal, { once: true })
  }
  const timer = timeoutMs
    ? setTimeout(() => {
        timedOut = true
        controller.abort(new DOMException('Timeout', 'TimeoutError'))
      }, timeoutMs)
    : undefined
  return {
    signal: controller.signal,
    timedOut: () => timedOut,
    dispose: () => {
      if (timer) clearTimeout(timer)
      external?.removeEventListener('abort', abortFromExternal)
    },
  }
}

async function send(path: string, init: ApiInit, accept: string, defaultTimeout: number): Promise<{ response: Response; requestId: string; complete: (completed: boolean) => void }> {
  const method = (init.method ?? 'GET').toUpperCase()
  const fallbackTimeout = defaultTimeout && method !== 'GET' && method !== 'HEAD' ? WRITE_TIMEOUT_MS : defaultTimeout
  const { timeoutMs = fallbackTimeout, ...rest } = init
  const headers = new Headers(rest.headers)
  const formData = typeof FormData !== 'undefined' && rest.body instanceof FormData
  if (rest.body !== undefined && !formData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('Accept', accept)
  const requestId = headers.get('X-Request-Id') ?? newRequestId()
  headers.set('X-Request-Id', requestId)

  const preflight = await requestPreflight?.(path, init)
  for (const [name, value] of Object.entries(preflight?.headers ?? {})) headers.set(name, value)
  if (rest.signal?.aborted) throw new DOMException('用户取消', 'AbortError')

  const prepared = prepareSignal(rest.signal, timeoutMs)
  try {
    const response = await fetch(path, { ...rest, headers, credentials: 'include', signal: prepared.signal })
    return { response, requestId: response.headers.get('X-Request-Id') ?? requestId, complete: completed => preflight?.complete?.(completed) }
  } catch (error) {
    preflight?.complete?.(false)
    if (prepared.timedOut()) throw new ApiClientError(TIMEOUT_ERROR, 0, requestId)
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw error instanceof TypeError ? error : new TypeError('网络请求失败')
  } finally {
    prepared.dispose()
  }
}

export async function api<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { response, requestId, complete } = await send(path, init, 'application/json', DEFAULT_TIMEOUT_MS)
  const payload = (await parseJson(response)) as ApiResponse<T> | null
  if (!payload || typeof payload.ok !== 'boolean') {
    if (response.status === 401) {
      onUnauthenticated?.()
    }
    if (response.ok && response.status === 204) { complete(true); return null as T }
    throw new ApiClientError(unparsedHttpError(response.status), response.status, requestId)
  }

  complete(payload.ok || !isAiRequestInFlight(payload.error?.reason))
  if (!payload.ok) {
    const error = errorFromPayload(payload.error, response.status)
    if (response.status === 401 || error.category === 'UNAUTHENTICATED') {
      onUnauthenticated?.()
    }
    notifyPlanLimit(error)
    throw new ApiClientError(error, response.status, requestId, true)
  }

  return payload.data as T
}

export type SseEvent = { id?: string; type: string; data: unknown }

export async function apiSse(path: string, init: RequestInit, onEvent: (event: SseEvent) => void): Promise<void> {
  const { response, requestId, complete } = await send(path, { ...init, timeoutMs: 0 }, 'text/event-stream', 0)
  if (!response.ok) {
    const payload = (await parseJson(response)) as ApiResponse<unknown> | null
    const error = payload && typeof payload.ok === 'boolean'
      ? errorFromPayload(payload.error, response.status)
      : unparsedHttpError(response.status)
    if (payload && typeof payload.ok === 'boolean') complete(payload.ok || !isAiRequestInFlight(payload.error?.reason))
    if (response.status === 401 || error.category === 'UNAUTHENTICATED') onUnauthenticated?.()
    notifyPlanLimit(error)
    throw new ApiClientError(error, response.status, requestId, Boolean(payload && typeof payload.ok === 'boolean'))
  }
  if (!response.body) throw new ApiClientError(DEFAULT_ERROR, response.status, requestId)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const event = parseSseBlock(block)
      if (event) {
        onEvent(event)
        if (!(event.type === 'request.failed' && isAiRequestInFlight((event.data as { errorCode?: string })?.errorCode)) && ['done', 'completed', 'complete', 'error', 'response.completed', 'generation.completed', 'generation.failed', 'assistant.completed', 'assistant.cancelled', 'assistant.failed', 'request.failed'].includes(event.type)) complete(true)
      }
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }
  if (buffer.trim()) throw new ApiClientError(DEFAULT_ERROR, response.status, requestId)
}

export function parseSseBlock(block: string): SseEvent | null {
  let id: string | undefined
  let type = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith(':')) continue
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    let fieldValue = separator < 0 ? '' : line.slice(separator + 1)
    if (fieldValue.startsWith(' ')) fieldValue = fieldValue.slice(1)
    if (field === 'id') id = fieldValue
    else if (field === 'event') type = fieldValue || 'message'
    else if (field === 'data') data.push(fieldValue)
  }
  if (!data.length) return null
  const text = data.join('\n')
  let parsed: unknown = text
  try {
    parsed = JSON.parse(text) as unknown
  } catch {
    /* Plain SSE data is valid. */
  }
  return { id, type, data: parsed }
}

export async function apiDownload(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const { response, requestId } = await send(
    path,
    { method: 'GET', redirect: 'manual', timeoutMs: LONG_TIMEOUT_MS },
    'application/json, application/octet-stream, application/pdf, */*',
    LONG_TIMEOUT_MS,
  )

  if (response.type === 'opaqueredirect' || (response.status >= 300 && response.status < 400)) {
    throw new ApiClientError(
      {
        category: 'SYSTEM_FAILURE',
        reason: 'BAD_RESPONSE',
        message: '下载被重定向。本页不会跟随跳转，以免把网页当成文件。',
      },
      response.status || 0,
      requestId,
    )
  }

  if (!response.ok) {
    const payload = (await parseJson(response)) as ApiResponse<unknown> | null
    if (response.status === 401 || payload?.error?.category === 'UNAUTHENTICATED') {
      onUnauthenticated?.()
    }
    if (!payload || typeof payload.ok !== 'boolean') {
      throw new ApiClientError(unparsedHttpError(response.status), response.status, requestId)
    }
    throw new ApiClientError(errorFromPayload(payload.error, response.status), response.status, requestId)
  }

  const contentType = (response.headers.get('Content-Type') || '').toLowerCase()
  if (contentType.includes('text/html')) {
    throw new ApiClientError(
      { category: 'SYSTEM_FAILURE', reason: 'BAD_RESPONSE', message: '下载没有成功：服务返回的不是文件，请稍后重试。' },
      response.status,
      requestId,
    )
  }

  const blob = await response.blob()
  if (blob.size === 0) {
    throw new ApiClientError(
      { category: 'SYSTEM_FAILURE', reason: 'BAD_RESPONSE', message: '下载内容为空。未保存空文件。' },
      response.status,
      requestId,
    )
  }
  return { blob, filename: filenameFromDisposition(response.headers.get('Content-Disposition')) }
}

function filenameFromDisposition(header: string | null): string | null {
  if (!header) {
    return null
  }
  const star = /filename\*=(?:UTF-8''|utf-8'')([^;]+)/i.exec(header)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"(.*)"$/, '$1'))
    } catch {
      return star[1]
    }
  }
  const basic = /filename="([^"]+)"|filename=([^;]+)/i.exec(header)
  const raw = (basic?.[1] ?? basic?.[2] ?? '').trim()
  return raw || null
}

async function parseJson(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}
