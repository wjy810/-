import { ApiClientError, type ApiError, type ApiResponse } from './types'

type UnauthenticatedHandler = () => void

let onUnauthenticated: UnauthenticatedHandler | null = null

export function setUnauthenticatedHandler(handler: UnauthenticatedHandler | null): void {
  onUnauthenticated = handler
}

const DEFAULT_ERROR: ApiError = {
  category: 'SYSTEM_FAILURE',
  reason: 'BAD_RESPONSE',
  message: '服务响应无法解析',
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

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const formData = typeof FormData !== 'undefined' && init.body instanceof FormData
  if (init.body !== undefined && !formData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('Accept', 'application/json')

  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers,
      credentials: 'include',
    })
  } catch (error) {
    throw error instanceof TypeError ? error : new TypeError('网络请求失败')
  }

  const payload = (await parseJson(response)) as ApiResponse<T> | null
  if (!payload || typeof payload.ok !== 'boolean') {
    if (response.status === 401) {
      onUnauthenticated?.()
    }
    throw new ApiClientError(unparsedHttpError(response.status), response.status)
  }

  if (!payload.ok) {
    const error = errorFromPayload(payload.error, response.status)
    if (response.status === 401 || error.category === 'UNAUTHENTICATED') {
      onUnauthenticated?.()
    }
    throw new ApiClientError(error, response.status)
  }

  return payload.data as T
}

export type SseEvent = { id?: string; type: string; data: unknown }

export async function apiSse(
  path: string,
  init: RequestInit,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  headers.set('Accept', 'text/event-stream')
  let response: Response
  try {
    response = await fetch(path, { ...init, headers, credentials: 'include' })
  } catch (error) {
    throw error instanceof TypeError ? error : new TypeError('网络请求失败')
  }
  if (!response.ok) {
    const payload = (await parseJson(response)) as ApiResponse<unknown> | null
    const error = payload && typeof payload.ok === 'boolean'
      ? errorFromPayload(payload.error, response.status)
      : unparsedHttpError(response.status)
    if (response.status === 401 || error.category === 'UNAUTHENTICATED') onUnauthenticated?.()
    throw new ApiClientError(error, response.status)
  }
  if (!response.body) throw new ApiClientError(DEFAULT_ERROR, response.status)

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
      const lines = block.split('\n')
      let id: string | undefined
      let type = 'message'
      const data: string[] = []
      for (const line of lines) {
        if (line.startsWith(':')) continue
        const separator = line.indexOf(':')
        const field = separator < 0 ? line : line.slice(0, separator)
        let fieldValue = separator < 0 ? '' : line.slice(separator + 1)
        if (fieldValue.startsWith(' ')) fieldValue = fieldValue.slice(1)
        if (field === 'id') id = fieldValue
        else if (field === 'event') type = fieldValue || 'message'
        else if (field === 'data') data.push(fieldValue)
      }
      if (data.length) {
        const text = data.join('\n')
        let parsed: unknown = text
        try { parsed = JSON.parse(text) as unknown } catch { /* Plain SSE data is valid. */ }
        onEvent({ id, type, data: parsed })
      }
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }
  if (buffer.trim()) throw new ApiClientError(DEFAULT_ERROR, response.status)
}

export async function apiDownload(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const headers = new Headers()
  headers.set('Accept', 'application/json, application/octet-stream, application/pdf, */*')

  let response: Response
  try {
    response = await fetch(path, {
      method: 'GET',
      headers,
      credentials: 'include',
      redirect: 'manual',
    })
  } catch (error) {
    throw error instanceof TypeError ? error : new TypeError('网络请求失败')
  }

  if (response.type === 'opaqueredirect' || (response.status >= 300 && response.status < 400)) {
    throw new ApiClientError(
      {
        category: 'SYSTEM_FAILURE',
        reason: 'BAD_RESPONSE',
        message: '下载被重定向。本页不会跟随跳转，以免把网页当成文件。',
      },
      response.status || 0,
    )
  }

  if (!response.ok) {
    const payload = (await parseJson(response)) as ApiResponse<unknown> | null
    if (response.status === 401 || payload?.error?.category === 'UNAUTHENTICATED') {
      onUnauthenticated?.()
    }
    if (!payload || typeof payload.ok !== 'boolean') {
      throw new ApiClientError(unparsedHttpError(response.status), response.status)
    }
    throw new ApiClientError(errorFromPayload(payload.error, response.status), response.status)
  }

  const contentType = (response.headers.get('Content-Type') || '').toLowerCase()
  if (contentType.includes('text/html')) {
    throw new ApiClientError(
      {
        category: 'SYSTEM_FAILURE',
        reason: 'BAD_RESPONSE',
        message: '服务返回了网页而不是文件。未保存伪造下载。',
      },
      response.status,
    )
  }

  const blob = await response.blob()
  if (blob.size === 0) {
    throw new ApiClientError(
      {
        category: 'SYSTEM_FAILURE',
        reason: 'BAD_RESPONSE',
        message: '下载内容为空。未保存空文件。',
      },
      response.status,
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
